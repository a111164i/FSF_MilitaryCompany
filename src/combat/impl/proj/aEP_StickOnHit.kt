package combat.impl.proj

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import combat.impl.VEs.aEP_MovingSprite
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Tool
import data.scripts.util.MagicRender
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

open class aEP_StickOnHit(duration: Float, target: CombatEntityAPI, hitPoint: Vector2f, spriteId: String, outSpriteId: String, spriteOnHitAngle: Float, hitShield: Boolean) : aEP_BaseCombatEffect() {

  var hitPoint: Vector2f
  var sprite: SpriteAPI
  var outSpriteId : String
  var target: CombatEntityAPI
  var shouldDetach = false
  var hitShield: Boolean
  var onHitAngle = 0f
  var relativeLocData: Vector2f? = null
  var renderLoc: Vector2f? = null
  var renderAngle = 0f

  init {
    this.lifeTime = duration
    this.hitPoint = hitPoint
    this.target = target
    var id = spriteId.split("\\.".toRegex()).toTypedArray()
    sprite = Global.getSettings().getSprite(id[0], id[1])
    this.outSpriteId = outSpriteId
    relativeLocData = if (hitShield) {
      aEP_Tool.getRelativeLocationData(hitPoint, target, true)
    } else {
      aEP_Tool.getRelativeLocationData(hitPoint, target, false)
    }
    this.hitShield = hitShield
    if (hitShield) {
      this.onHitAngle = aEP_Tool.angleAdd(spriteOnHitAngle, -target.getShield().getFacing())
    } else {
      this.onHitAngle = aEP_Tool.angleAdd(spriteOnHitAngle, -target.getFacing())
    }

  }

  override fun advance(amount:Float){
    //Global.getCombatEngine().addFloatingText(Global.getCombatEngine().getPlayerShip().getMouseTarget(),amount+"",20f,new Color(100,100,100,100),Global.getCombatEngine().getPlayerShip(),1f,5f);
    //render angle decide which angle sprite itself is facing
    super.advance(amount)
    if (hitShield) {
      renderAngle = aEP_Tool.angleAdd(onHitAngle, target.shield.facing - 90f)
      renderLoc = aEP_Tool.getAbsoluteLocation(relativeLocData!!.x, relativeLocData!!.y, target, true)
    } else {
      renderAngle = aEP_Tool.angleAdd(onHitAngle, target.facing - 90f)
      renderLoc = aEP_Tool.getAbsoluteLocation(relativeLocData!!.x, relativeLocData!!.y, target, false)
    }
    advanceImpl(amount)

    //渲染原图
    MagicRender.singleframe(sprite,renderLoc,
      Vector2f(sprite.width,sprite.height),
      renderAngle,
      Color(255,255,255),
      false)


    //detach check
    if (hitShield && target.shield.isOff) {
      cleanup()
    }
    if (shouldDetach) {
      cleanup()
    }
  }

  /**
   * 继承这个
   * */
  override fun advanceImpl(amount: Float) {

  }

  override fun readyToEnd() {
    val id: Array<String> = outSpriteId.split("\\.".toRegex()).toTypedArray()
    val outSprite = Global.getSettings().getSprite(id[0], id[1])
    val ms = aEP_MovingSprite(outSprite, renderLoc)
    ms.setInitVel(aEP_Tool.speed2Velocity(renderAngle-90f + MathUtils.getRandomNumberInRange(-20f,20f), 100f + Math.random().toFloat() * 50f))
    ms.angleSpeed = MathUtils.getRandomNumberInRange(120f,240f)
    ms.size = Vector2f(outSprite.width,outSprite.height)
    ms.color = outSprite.color
    ms.lifeTime = 1f
    ms.fadeIn = 0f
    ms.fadeOut = 0.5f
    aEP_CombatEffectPlugin.addEffect(ms)
  }
}
