package data.scripts.ai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.WeightedRandomPicker
import com.fs.starfarer.combat.ai.AI
import com.fs.starfarer.combat.entities.DamagingExplosion
import com.fs.starfarer.combat.entities.MovingRay
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.projTimeToHitShip
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lazywizard.lazylib.combat.WeaponUtils
import org.lwjgl.util.vector.Vector2f
import java.util.*
import kotlin.math.absoluteValue

open class aEP_BaseAutoFireAI(val w: WeaponAPI) : AutofireAIPlugin {

  var targetPoint : Vector2f? = null
  var aimEntity: CombatEntityAPI? = null

  var shouldFire = false


  override fun advance(amount: Float) {
    search(amount)
    track(amount)

    var angleDist = 999f
    if(targetPoint != null){
      angleDist = MathUtils.getShortestRotation(weapon.currAngle, VectorUtils.getAngle(weapon.location, targetPoint))
    }
    angleDist = angleDist.absoluteValue

    shouldFire = checkShouldFire(angleDist)

  }

  open fun search(amount: Float){
  }

  open fun track(amount: Float){

  }


  open fun checkShouldFire(angleDist: Float):Boolean{
    return false
  }

  override fun shouldFire(): Boolean {
    return shouldFire
  }

  override fun forceOff() {
    aimEntity = null
    targetPoint = null
  }

  override fun getTarget(): Vector2f {
    return targetPoint?: getExtendedLocationFromPoint(weapon.location, weapon.slot.computeMidArcAngle(weapon.ship), 100f)
  }

  override fun getTargetShip(): ShipAPI? {
    if(aimEntity is ShipAPI) return aimEntity as ShipAPI
    return null
  }


  override fun getTargetMissile(): MissileAPI? {
    if(aimEntity is MissileAPI) return aimEntity as MissileAPI
    return null
  }

  override fun getWeapon(): WeaponAPI {
    return w
  }
}

fun getDamagingProjectileInArc(weapon: WeaponAPI, extraArc:Float, extraRange:Float): List<DamagingProjectileAPI>{
  var list = LinkedList<DamagingProjectileAPI>()
  val maxRange: Float = weapon.range * weapon.range
  var distanceSquared: Float

  for (tmp in Global.getCombatEngine().projectiles) {
    if (tmp.owner == weapon.ship.owner) continue
    if(weapon.distanceFromArc(tmp.location) - extraArc > 0f) continue

    distanceSquared = MathUtils.getDistanceSquared(tmp.location, weapon.location)
    if (distanceSquared - extraRange > maxRange) continue
    if(tmp is DamagingExplosion) continue
    list.add(tmp)

  }
  return list

}

class aEP_MaoDianDroneAutoFire(weapon: WeaponAPI) : aEP_BaseAutoFireAI(weapon){


  override fun search(amount: Float) {

    //已有目标，且目标有效时，不需要search
    if (aimEntity != null && Global.getCombatEngine().isEntityInPlay(aimEntity)) {
      return
    }
    //下面是找新目标的过程


    val picker = WeightedRandomPicker<Array<Any>>()
    var newTarget: Array<Any>? = null
    for(it in getDamagingProjectileInArc(weapon, 30f, 250f)) {
      //先计算一下有没有拦截的可能
      val targetEndPoint = AIUtils.getBestInterceptPoint(weapon.location, weapon.projectileSpeed, it.location, it.velocity)
      targetEndPoint?: continue


      //拦截点不能在武器射界外面
      if(weapon.distanceFromArc(targetEndPoint)?:1f > 0f) continue
      if(MathUtils.getDistance(targetEndPoint, weapon.location) > w.range) continue

      //弹丸本身，还有拦截点都不能处于队友的碰撞圈内（都已经打到队友了还拦啥）
      //弹丸必须指向某个友军
      //弹丸和拦截点画线不能碰到队友的碰撞圈
      val dist = 800f
      var cant = false
      var closestDist = 9999999f
      for(ally in AIUtils.getNearbyEnemies(it, dist)){
        if(ally.isFighter) continue
        if(ally.isDrone) continue
        val angleToAlly = VectorUtils.getAngle(it.location, ally.location)
        val angleDist = MathUtils.getShortestRotation(it.facing, angleToAlly).absoluteValue
          if(angleDist > 15f) {cant = true; break}
        if(MathUtils.getDistance(ally, it) <= 1f) {cant = true; break}
        if(MathUtils.getDistance(ally, targetEndPoint) <= 1f) {cant = true; break}
        if(CollisionUtils.getCollides(it.location, targetEndPoint, ally.location, ally.collisionRadius)) {cant = true; break}
        val d = MathUtils.getDistance(ally, targetEndPoint)
        if(d < closestDist) closestDist = d
      }
      if(cant) continue



      var weight = it.damage?.baseDamage?: 0f
      weight *= MathUtils.clamp(closestDist,50f,500f)/500f
      weight *= weight
      weight *= weight
      picker.add(arrayOf(it,targetEndPoint), weight)
    }

    newTarget = picker.pick()
    if (newTarget != null) {
      aimEntity = newTarget[0] as DamagingProjectileAPI
      targetPoint = (newTarget[1] as Vector2f)
    }else{
      aimEntity = null
      targetPoint = null
    }

  }

  override fun track(amount: Float) {

  }

  override fun checkShouldFire(angleDist: Float): Boolean {
    if(angleDist < 2.5f && aimEntity != null && Global.getCombatEngine().isEntityInPlay(aimEntity)) return true
    return false
  }

}