package data.scripts.ai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.util.Misc.getInterceptPoint
import com.fs.starfarer.api.util.WeightedRandomPicker
import com.fs.starfarer.combat.ai.AI
import com.fs.starfarer.combat.entities.DamagingExplosion
import com.fs.starfarer.combat.entities.MovingRay
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.addDebugPoint
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.isDead
import combat.util.aEP_Tool.Util.isEnemy
import combat.util.aEP_Tool.Util.isShipTargetable
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
import kotlin.math.pow

open class aEP_BaseAutoFireAI(val w: WeaponAPI) : AutofireAIPlugin {

  val targetPoint =  getExtendedLocationFromPoint(w.location, w.slot.computeMidArcAngle(w.ship), 100f)
  //interceptPoint可以为null
  var interceptPoint : Vector2f? = null
  var aimEntity: CombatEntityAPI? = null

  var shouldFire = false


  override fun advance(amount: Float) {
    //默认不开火
    shouldFire = false

    //没有目标/目标已经死亡/不再是武器的目标对象，就重新索敌
    if(aimEntity == null
      || isDead(aimEntity!!)
      || !checkIsValid(aimEntity!!) ){
      interceptPoint = null
      search(amount)
    }

    //索敌失败
    aimEntity?:return
    //如果索敌成功，interceptPoint一定不为空

    //上一步索敌成功，开始track，此时保证aimEntity一定不为null
    if(aimEntity is CombatEntityAPI){
      //计算targetPoint
      track(amount, aimEntity as CombatEntityAPI)
      //如果track()完毕发现丢失目标，不进行开火判断
      aimEntity?:return
      shouldFire = checkShouldFire(aimEntity as CombatEntityAPI)

      //addDebugPoint(targetPoint)

    }else{

    }

  }

  /**
   *  获取aimEntity和interceptPoint的地方
   *  不要动targetPoint，那是track()的内容
   * */
  open fun search(amount: Float){
  }

  /**
   * 只有锁到了有效的target才会调用，只用考虑追踪的问题
   * 在这里计算targetPoint
   * 既然能被放入有效目标，一开始肯定是在射界内的，但是后续track中有可能脱离射界，此时需要调用forceOff()重新索敌
   * */
  open fun track(amount: Float, target: CombatEntityAPI){
    if(w.isBeam){
      //当前追踪的目标脱离了射程，调用forceOff()
      if(!isPointWithinRange(target.location, 0f,0f)){
        forceOff()
        return
      }
      targetPoint.set(target.location)
      return
    }

    if(!w.isBeam){
      interceptPoint = AIUtils.getBestInterceptPoint(weapon.location, weapon.projectileSpeed, target.location, target.velocity)
      //当前追踪的目标脱离了射程，调用forceOff()
      if(interceptPoint == null || !isPointWithinRange(interceptPoint!!, 0f,0f)){
        forceOff()
        return
      }
      targetPoint.set(interceptPoint?:target.location)
      return
    }
  }

  /**
   * 本武器会自动瞄准哪些目标
   * 不需要检测目标是否被摧毁，在advance部分会自动检测
   * 这里默认的检测包括是否还处于射界中，是否是敌人的弹丸，如果有特殊的ai需求，override本方法，也可以在search()中使用
   * */
  open fun checkIsValid(target: CombatEntityAPI) : Boolean{
    val isEnemy = isEntityEnemy(target)
    val isInRange = isPointWithinRange(target.location,0f,0f)
    if(isEnemy && isInRange) return true
    return false
  }

  /**
   * 默认在指向角度和目标角度差距少于1时开火
   * */
  open fun checkShouldFire(target: CombatEntityAPI):Boolean{
    val angleDistAbs = checkAngleDistAbs()
    if(angleDistAbs < 1f) return true
    return false
  }

  override fun shouldFire(): Boolean {
    return shouldFire
  }

  override fun forceOff() {
    shouldFire = false
    aimEntity = null
    interceptPoint = null
  }

  override fun getTarget(): Vector2f {
    return targetPoint
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


  //往下是工具方法，一般不需要重载

  open fun checkAngleDistAbs(): Float{
    return MathUtils.getShortestRotation(weapon.currAngle, VectorUtils.getAngle(weapon.location, targetPoint)).absoluteValue
  }

  open fun checkAngleDist(): Float{
    return MathUtils.getShortestRotation(weapon.currAngle, VectorUtils.getAngle(weapon.location, targetPoint))
  }

  //用在checkIsValid当中
  open fun isEntityEnemy(tmp: CombatEntityAPI) : Boolean{

    if(tmp is ShipAPI){
      //不瞄准无敌/相位/无碰撞/fx无人机
      if(!aEP_Tool.isShipTargetable(tmp,
          false,
          true,
          true,
          false,
          true)) return false
      //不瞄准队友
      if(!isEnemy(w.ship?:return false, tmp)) return false
      //不瞄准残骸
      if(isDead(tmp)) return false
      return true
    }

    if(tmp is DamagingProjectileAPI){
      //无伤害弹丸不需要拦截
      if (tmp.damage.baseDamage <= 0f) return false
      //无碰撞弹丸不需要拦截
      if(tmp.collisionClass == CollisionClass.NONE) return false
      //同阵营舰船射出的NO_FF弹丸不需要拦截
      if((tmp.source?.owner ?: return false) == (weapon.ship?.owner ?: return false) &&
        (tmp.collisionClass == CollisionClass.MISSILE_NO_FF || tmp.collisionClass == CollisionClass.PROJECTILE_NO_FF)) return false
      //自己射出的弹丸不需要拦截
      if (tmp.owner == (weapon.ship?.owner?:return false)) return false
      //爆炸也是弹丸的一种，直接跳过
      if(tmp is DamagingExplosion) return false
      return true
    }
    return false
  }

  //用在checkIsValid当中
  open fun isPointWithinRange(point: Vector2f, extraRange: Float, extraArc: Float) : Boolean{
    //超出射界的不需要拦截
    if(weapon.distanceFromArc(point) - extraArc > 0f) return false
    //超出射程的不需要拦截
    val maxRangeSq: Float = (weapon.range + extraRange).coerceAtLeast(0f).pow(2)
    val distanceSq = MathUtils.getDistanceSquared(point, weapon.location)
    if (distanceSq > maxRangeSq) return false
    return true
  }

  open fun isIgnoreFlare():Boolean{
    return ((w.hasAIHint(WeaponAPI.AIHints.IGNORES_FLARES)
        || (w.ship?.mutableStats?.dynamic?.getMod(Stats.PD_IGNORES_FLARES)?.computeEffective(0f) ?: 0f) >= 1f) )

  }

}

fun getDamagingProjectileInArc(weapon: WeaponAPI, extraArc:Float, extraRange:Float): List<DamagingProjectileAPI>{
  var list = LinkedList<DamagingProjectileAPI>()
  val maxRange: Float = weapon.range * weapon.range
  var distanceSquared: Float

  for (tmp in Global.getCombatEngine().projectiles) {
    //同阵营舰船射出的NO_FF弹丸不需要拦截
    if((tmp.source?.owner ?: continue) == (weapon.ship?.owner ?: continue) &&
      (tmp.collisionClass == CollisionClass.MISSILE_NO_FF || tmp.collisionClass == CollisionClass.PROJECTILE_NO_FF)) continue
    //自己射出的弹丸不需要拦截
    if (tmp.owner == (weapon.ship?.owner?:continue)) continue
    //超出射界的不需要拦截
    if(weapon.distanceFromArc(tmp.location) - extraArc > 0f) continue
    //超出射程的不需要拦截
    distanceSquared = MathUtils.getDistanceSquared(tmp.location, weapon.location)
    if (distanceSquared - extraRange > maxRange) continue
    if(tmp is DamagingExplosion) continue
    list.add(tmp)

  }
  return list

}

class aEP_MaoDianDroneAutoFire(weapon: WeaponAPI) : aEP_BaseAutoFireAI(weapon){

  override fun search(amount: Float) {

    //下面是找新目标的过程
    val picker = WeightedRandomPicker<Array<Any>>()
    var newTarget: Array<Any>? = null
    for(it in Global.getCombatEngine().projectiles) {
      //排除掉射界外的，队友的弹丸
      if(!checkIsValid(it)) continue

      //热诱弹
      if(it is MissileAPI && it.isFlare){
        if(isIgnoreFlare()) continue
      }

      //先计算一下有没有拦截的可能
      interceptPoint = AIUtils.getBestInterceptPoint(weapon.location, weapon.projectileSpeed, it.location, it.velocity)
      interceptPoint?: continue

      //伤害太低了不拦截
      if(it.damage.type == DamageType.FRAGMENTATION && it.damage.baseDamage < 100) continue
      if(it.damage.type == DamageType.KINETIC && it.damage.baseDamage < 25) continue
      if(it.damage.type == DamageType.HIGH_EXPLOSIVE && it.damage.baseDamage < 50) continue

      //拦截点不能在武器射界外面
      if(!isPointWithinRange(interceptPoint!!, -50f,0f)) continue

      //弹丸本身，还有拦截点都不能处于队友的碰撞圈内（都已经打到队友了还拦啥）
      //弹丸必须指向某个友军
      //弹丸和拦截点画线不能碰到队友的碰撞圈
      val dist = 800f
      var cant = false
      var closestDistSq = 9999999f
      for(ally in AIUtils.getNearbyEnemies(it, dist)){
        //不保护非常规队友
        if(!aEP_Tool.isShipTargetable(ally,
            false,
            true,
            true,
            false,
            true)) continue

        if(MathUtils.getDistanceSquared(ally, it) <= 0f) {cant = true; break}
        if(MathUtils.getDistanceSquared(ally, interceptPoint) <= 0f) {cant = true; break}
        val dSq = MathUtils.getDistanceSquared(ally, interceptPoint)
        if(dSq < closestDistSq) closestDistSq = dSq
      }

      if(cant) continue

      //把这个有效目标加入picker
      var weight = it.damage?.baseDamage?: 0f
      weight *= MathUtils.clamp(closestDistSq,10000f,250000f)/500f
      weight *= weight
      weight *= weight
      picker.add(arrayOf(it,interceptPoint!!), weight)
    }

    newTarget = picker.pick()
    if (newTarget != null) {
      aimEntity = newTarget[0] as DamagingProjectileAPI
      interceptPoint = newTarget[1] as Vector2f
    }else{
      aimEntity = null
      interceptPoint = null
    }

  }

  override fun checkIsValid(target: CombatEntityAPI): Boolean {
    val isEnemy = isEntityEnemy(target)
    val isInRange = isPointWithinRange(target.location,100f,30f)
    if(isEnemy && isInRange) return true
    return false
  }

}