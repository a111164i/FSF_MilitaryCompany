package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.Misc
import combat.impl.VEs.aEP_MovingSmoke
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Blinker
import combat.util.aEP_DataTool
import combat.util.aEP_ID
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.speed2Velocity
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color

class aEP_Skimmer: BaseShipSystemScript(){

  companion object{
    const val ID = "aEP_Skimmer"
    var BLINK_RANGE =  Global.getSettings().getShipSystemSpec(ID).getRange(null)

  }
  var didBlink = false
  var didEngineBurst = false
  var ship:ShipAPI? = null
  val originalLoc = Vector2f(0f,0f)
  var sprite: SpriteAPI? = null

  val blinker = aEP_Blinker(3f,0f)
  val markerSprite = Global.getSettings().getSprite("aEP_FX","forward")

  override fun apply(stats: MutableShipStatsAPI?, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    val ship = (stats?.entity?: return) as ShipAPI
    this.ship = ship
    val amount = aEP_Tool.getAmount(ship)

    if(aEP_Tool.isDead(ship)) return

    val blinkDist = aEP_Tool.getSystemRange(ship, BLINK_RANGE)

    if(state == ShipSystemStatsScript.State.IDLE){
      didBlink = false
      didEngineBurst = false

      //如果玩家在手操，显示闪现的预计落点
      if(Global.getCombatEngine().playerShip == ship && ship.ai == null){
        if(ship.system.ammo >= 1){
          if(sprite == null){
            sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
          }else {
            val sprite = sprite as SpriteAPI
            val ans = aEP_Tool.velocity2Speed(ship.velocity)
            //如果初速度为0，方向为正面
            if (ans.y == 0f) ans.x = ship.facing
            val newPos = aEP_Tool.getExtendedLocationFromPoint(ship.location, ans.x, blinkDist)
            var newFacing = VectorUtils.getAngle(ship.location, ship.mouseTarget)
            if(ship.shipTarget != null && ship.shipTarget != ship){
              newFacing = VectorUtils.getAngle(newPos, ship.shipTarget.location)

            }

            MagicRender.singleframe(
              sprite, newPos,
              Vector2f(sprite.width, sprite.height), newFacing - 90f,
              aEP_Tool.getColorWithAlpha(Color.white,0.1f+0.1f*(blinker.blinkLevel)), true,
              CombatEngineLayers.BELOW_SHIPS_LAYER)

            blinker.advance(amount)
            val xDiff = newPos.x - ship.location.x
            val yDiff = newPos.y - ship.location.y
            val point1 = Vector2f(ship.location.x + xDiff * 0.35f, ship.location.y + yDiff * 0.35f)
            val point2 = Vector2f(ship.location.x + xDiff * 0.65f, ship.location.y + yDiff * 0.65f)
            MagicRender.singleframe(
              markerSprite, point1,
              Vector2f(40f, 40f), ans.x - 90f,
              aEP_Tool.getColorWithAlpha(Color.green,
                0.25f*(blinker.blinkLevel)), true,
              CombatEngineLayers.BELOW_SHIPS_LAYER)
            MagicRender.singleframe(
              markerSprite, point2,
              Vector2f(40f, 40f), ans.x - 90f,
              aEP_Tool.getColorWithAlpha(Color.green,
                0.25f*(blinker.blinkLevel)), true,
              CombatEngineLayers.BELOW_SHIPS_LAYER)

          }
        }
      }
    }

    if(state == ShipSystemStatsScript.State.IN && effectLevel < 1f){
      originalLoc.set(ship.location)
      if(didEngineBurst){
        didEngineBurst = true
      }
    }

    if(state == ShipSystemStatsScript.State.OUT && effectLevel < 1f && !didBlink) {
      didBlink = true
      val blinkDist = MathUtils.getDistance(originalLoc, ship.location)
      val origin2newAngle = VectorUtils.getAngle(originalLoc, ship.location)

      //闪光
      val glowSize = ship.collisionRadius + 400f
      Global.getCombatEngine().addHitParticle(
        originalLoc, aEP_ID.VECTOR2F_ZERO,
        ship.collisionRadius + 400f,
        1f,0f,0.3f,Color(205,165,25))

      //在老位置加烟
      val num = 20
      val size = ship.collisionRadius * 0.1f + 40f
      for (i in 0 until num) {
        Global.getCombatEngine().addNebulaSmokeParticle(
          MathUtils.getRandomPointInCircle(originalLoc, ship.collisionRadius/2f),
          speed2Velocity(origin2newAngle, MathUtils.getRandomNumberInRange(blinkDist*0.1f, blinkDist*0.75f)),
          MathUtils.getRandomNumberInRange(size, size*2f),
          2f,
          0.1f,
          0.5f, MathUtils.getRandomNumberInRange(0.5f,1.25f),
          Color(255, 250, 250, 105)
        )
      }

      //在新位置加烟
      val num2 = 16
      val size2 = ship.collisionRadius * 0.1f + 40f
      for (i in 0 until num2) {
        Global.getCombatEngine().addNebulaSmokeParticle(
          MathUtils.getRandomPointInCircle(ship.location, ship.collisionRadius),
          speed2Velocity(origin2newAngle, MathUtils.getRandomNumberInRange(150f,250f)),
          MathUtils.getRandomNumberInRange(size2, size2*2f),
          3f,
          0.1f,
          0.5f, MathUtils.getRandomNumberInRange(0.5f,1.25f),
          Color(255, 250, 250, 75)
        )
      }

      //后坐力抵消， 引擎反推
      if(ship.engineController?.shipEngines?.isEmpty() == false){
        createEngineBlowback(ship)
      }

      //残影
      val numImages = (blinkDist/96f).toInt()
      createAfterMerge(ship, originalLoc, numImages)

    }

  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    val ship = (stats?.entity?: return) as ShipAPI
    this.ship = ship

    originalLoc.set(ship.location)
    didBlink = false
    didEngineBurst = false
  }

  fun createAfterMerge(ship:ShipAPI, originalLoc:Vector2f, num:Int){
    //生成10个残影
    val num = num.coerceAtLeast(2)
    for(i in 1 until num ){
      var relativeLoc = Vector2f(originalLoc.x-ship.location.x,  originalLoc.y - ship.location.y)
      relativeLoc.scale(i*1f/num)
      val dur = 0.5f
      val out = 0.5f
      var velocity = Vector2f(-relativeLoc.x, -relativeLoc.y)
      velocity.scale(1f/(dur+out))
      ship.addAfterimage(
        Color(255, 155, 155, 255),
        relativeLoc.x, relativeLoc.y,
        velocity.x, velocity.y, 5f, 0f,
        dur,
        out,
        true,
        false,
        false)
    }

  }

  fun createEngineBlowback(ship:ShipAPI){
    //后坐力抵消， 引擎反推
    for(e in ship.engineController.shipEngines){
      if(e.isSystemActivated) continue
      if(e.engineSlot.width <= 1f) continue
      val loc = e.engineSlot.computePosition(ship.location, ship.facing)
      val vel = aEP_Tool.speed2Velocity(e.engineSlot.computeMidArcAngle(ship.facing), 40f)
      Global.getCombatEngine().addSmoothParticle(
        loc, Misc.ZERO, 300f,  //size
        1f,  //brightness
        0.35f,
        0.3f, Color(255, 120, 120, 255))
      val ms = aEP_MovingSmoke(loc)
      ms.setInitVel(vel)
      ms.stopSpeed = 1f
      ms.lifeTime = 1f
      ms.fadeIn = 0.15f
      ms.fadeOut = 0.45f
      ms.size = 40f
      ms.sizeChangeSpeed = 10f
      ms.color = Color(255, 120, 120, 155)
      aEP_CombatEffectPlugin.addEffect(ms)

      Global.getCombatEngine().spawnExplosion(loc,vel,Color(255, 255, 255, 120),80f,0.8f)
    }

  }

}