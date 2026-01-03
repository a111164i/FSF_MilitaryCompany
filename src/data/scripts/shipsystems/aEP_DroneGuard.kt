package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import data.scripts.utils.aEP_ID
import data.scripts.utils.aEP_Tool
import data.scripts.utils.aEP_Tool.Util.speed2Velocity
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_DroneGuard: BaseShipSystemScript(){

  companion object{
    const val ID = "aEP_DroneGuard"
    const val MAX_DIST = 800f
  }
  var didBlink = false
  var didOverload = false
  var ship:ShipAPI? = null
  var wingL: aEP_DecoAnimation? = null
  var wingR: aEP_DecoAnimation? = null

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    val ship = (stats?.entity?: return) as ShipAPI
    this.ship = ship
    val originalLoc = Vector2f(ship.location)
    if(effectLevel >= 1f && !didBlink) {
    Global.getSector().playerFaction.createRandomPerson()
      //闪光
      Global.getCombatEngine().addSmoothParticle(
        originalLoc,
        aEP_ID.VECTOR2F_ZERO,
        150f,1f,0.1f,0.3f,Color.yellow)

      //在原位置加烟
      val num = 12
      for (i in 0 until num) {
        Global.getCombatEngine().addNebulaSmokeParticle(
          MathUtils.getRandomPointInCircle(ship.location, ship.collisionRadius),
          speed2Velocity(ship.facing - 180f, MathUtils.getRandomNumberInRange(50, 100).toFloat()),
          MathUtils.getRandomNumberInRange(20, 60).toFloat(),
          2f,
          0.25f,
          0.5f, 1.25f,
          Color(255, 250, 250, 125)
        )
      }


      //瞬移
      val angle = VectorUtils.getAngle(ship.location, ship.mouseTarget)
      val dist = MathUtils.clamp(MathUtils.getDistance(ship.location, ship.mouseTarget), 0f,aEP_Tool.getSystemRange(ship, MAX_DIST))
      ship.location.set(aEP_Tool.getExtendedLocationFromPoint(ship.location, angle, dist))
      //找到瞬移后最近的友军，决定朝向
      var parent = aEP_Tool.getNearestFriendCombatShip(ship) ?: ship.wing?.sourceShip ?: null
      if (parent != null) ship.facing = VectorUtils.getAngle(parent.location, ship.location)

      ship.angularVelocity = 0f
      ship.velocity.scale(0.01f)

      //闪光
      Global.getCombatEngine().addSmoothParticle(
        ship.location,
        aEP_ID.VECTOR2F_ZERO,
        100f,1f,0.1f,0.3f,Color.white)

      //残影
      createAfterMerge(ship, originalLoc)

      didBlink = true
    }

    //展开护盾
    if(effectLevel > 0f){
      ship.setShield(ShieldAPI.ShieldType.FRONT,0f,1f,210f)
      val shield = ship.shield
      shield.toggleOn()
      shield.activeArc = 90f + 120f * effectLevel
    }

    //禁止机动
    if(effectLevel > 0f){
      stats.maxTurnRate.modifyMult(ID,0f)
      stats.turnAcceleration.modifyMult(ID,0f)
      stats.maxSpeed.modifyMult(ID,0f)
      stats.acceleration.modifyMult(ID,0f)
      //给光束一点点减伤，爆发性光束可以连盾带人一起击破
      //刚好吞弹护盾也有减伤
      stats.beamShieldDamageTakenMult.modifyMult(ID, 0.5f)

    }

    //控制装饰武器
    if(wingL == null || wingR == null){
      for(w in ship.allWeapons){
        if(!w.isDecorative) continue
        if(w.slot.id.equals("WING_L")) wingL = w.effectPlugin as aEP_DecoAnimation
        if(w.slot.id.equals("WING_R")) wingR = w.effectPlugin as aEP_DecoAnimation
      }
    }else{
      moveDeco(ship,effectLevel)
    }


  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    val ship = (stats?.entity?: return) as ShipAPI
    this.ship = ship

    val spec = Global.getSettings().getHullSpec(ship.hullSpec.baseHullId)
    if(spec.shieldSpec != null ){
      ship.setShield(spec.shieldSpec.type, spec.shieldSpec.upkeepCost,spec.shieldSpec.fluxPerDamageAbsorbed,spec.shieldSpec.arc)
    }

    stats.maxTurnRate.unmodify(ID)
    stats.turnAcceleration.unmodify(ID)
    stats.maxSpeed.unmodify(ID)
    stats.acceleration.unmodify(ID)
    stats.beamShieldDamageTakenMult.unmodify(ID)

    //控制装饰武器
    if(wingL == null || wingR == null){
      for(w in ship.allWeapons){
        if(!w.isDecorative) continue
        if(w.slot.id.equals("WING_L")) wingL = w.effectPlugin as aEP_DecoAnimation
        if(w.slot.id.equals("WING_R")) wingR = w.effectPlugin as aEP_DecoAnimation
      }
    }else{
      moveDeco(ship,effectLevel = 0f)
    }

    didBlink = false
    didOverload = false
  }

  fun createAfterMerge(ship:ShipAPI, originalLoc:Vector2f){
    //生成10个残影
    val num = 10
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
        true)
    }

  }

  fun moveDeco(ship: ShipAPI, effectLevel: Float){
    val l = (wingL?: return) as aEP_DecoAnimation
    val r = (wingR?: return) as aEP_DecoAnimation
    l.setMoveToSideLevel(effectLevel)
    l.setMoveToLevel(effectLevel)
    l.setRevoToLevel(effectLevel)

    r.setMoveToSideLevel(effectLevel)
    r.setMoveToLevel(effectLevel)
    r.setRevoToLevel(effectLevel)
  }
}