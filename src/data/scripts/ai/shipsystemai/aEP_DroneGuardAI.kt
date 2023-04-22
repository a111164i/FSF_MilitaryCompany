package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_DroneGuard.Companion.MAX_DIST
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lwjgl.util.vector.Vector2f

class aEP_DroneGuardAI: aEP_BaseSystemAI() {
  companion object{
    const val ALERT_TIME = 0.9f
    const val ID = "aEP_DroneGuardAI"
    const val THRESHOLD = 749f
    const val BLINK_TIME_BEFORE_HIT = 0.4f
  }

  var currProj:DamagingProjectileAPI? = null

  override fun initImpl() {
    thinkTracker.setInterval(0.05f,0.05f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false
    val distTimes2 = aEP_Tool.getSystemRange(ship,MAX_DIST*2f)
    val nearShips = AIUtils.getNearbyAllies(ship,distTimes2)
    val nearEntities = CombatUtils.getEntitiesWithinRange(ship.location,distTimes2)
    for(proj in nearEntities){
      //这里的得到的是全部entity，先过滤一道只剩下弹丸
      if(proj !is DamagingProjectileAPI) continue
      val proj = proj as DamagingProjectileAPI
      //排除无效的拦截对象，比如伤害太低的弹丸，或者已经是友军拦截对象的弹丸
      if(proj.customData.containsKey(ID)) continue
      //modifier基础值是1
      var damageAmount = proj.damage.modifier.modifiedValue * proj.damage.damage
      when(proj.damage.type){
        DamageType.ENERGY-> damageAmount  /= 1.2f
        DamageType.KINETIC-> damageAmount /= 1.5f
        DamageType.FRAGMENTATION-> damageAmount /= 2f
      }
      if(damageAmount < THRESHOLD) continue

      val hitPoint = aEP_Tool.getExtendedLocationFromPoint(proj.location, proj.facing, proj.moveSpeed * ALERT_TIME)
      for(s in nearShips){

        //排除无效的保护对象，比如不要保护飞机，排除太近了已经无法拦截的舰船，友军射出的弹丸
        if(s.isFighter) continue
        if(s.owner == proj.owner) continue
        val distProj2Target = MathUtils.getDistance(proj.location,s.location)
        if(distProj2Target < s.collisionRadius) continue

        //如果是一个有效目标，并且将会进入保护对象的碰撞圈，启动系统
        val willHit = CollisionUtils.getCollides(proj.location,hitPoint,s.location,s.collisionRadius)
        if(willHit){
          if(MathUtils.getDistance(proj.location,ship.location) < aEP_Tool.getSystemRange(ship, MAX_DIST)){
            ship.mouseTarget.set(aEP_Tool.getExtendedLocationFromPoint(proj.location,proj.facing, BLINK_TIME_BEFORE_HIT *proj.moveSpeed+ship.collisionRadius))
            proj.setCustomData(ID,1f)
            currProj = proj
            shouldActive = true
            return
          }
        }
      }
    }

    //拦截结束以后解除弹丸已经被锁定的状态，可以被其他ai选中
    if(system.state == ShipSystemAPI.SystemState.IDLE && currProj != null){
      currProj?.removeCustomData(ID)
      currProj = null
    }

  }
}