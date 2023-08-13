package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.GuidedMissileAI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.util.WeightedRandomPicker
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_BBLockOn
import data.scripts.shipsystems.aEP_FlareStrikeSS
import data.scripts.shipsystems.aEP_MaodianDroneLaunch
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f

class aEP_FlareStrikeAI:aEP_BaseSystemAI() {

  companion object{
    const val ID = "aEP_FlareStrikeAI"
  }

  override fun initImpl() {
    thinkTracker.setInterval(0.5f,1.5f)
  }



  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false
    var willing = 0f;

    val targetWeightPicker = WeightedRandomPicker<ShipAPI>()
    //遍历导弹，找到被导弹锁定的舰船
    for(m in engine.missiles){
      if(!m.isGuided) continue
      val ai = m.ai
      if(ai !is GuidedMissileAI) continue
      if(ai.target !is ShipAPI) continue
      val target = ai.target as ShipAPI
      //不是战舰，不考虑
      if(target.isFighter) continue
      if(target.isDrone) continue

      //目标不在系统范围内，不考虑
      val distToShip = MathUtils.getDistance(target, ship)
      val sysRange = aEP_Tool.getSystemRange(ship,aEP_FlareStrikeSS.RANGE)
      if(distToShip > sysRange) continue

      var damageAmount = m.damage.baseDamage
      when(m.damage.type){
        DamageType.ENERGY-> damageAmount  *= 1f
        DamageType.KINETIC-> damageAmount *= 1f
        DamageType.FRAGMENTATION-> damageAmount *= 0.5f
        else -> {}
      }
      //毛毛雨伤害的蜂群式导弹不需要考虑
      if(damageAmount < 100) continue
      //导弹里目标太远和太近都不需要考虑
      val distToTarget = MathUtils.getDistance(target,m)
      if(distToTarget > m.maxSpeed * 5f || distToTarget < m.maxSpeed * 2f) continue
      targetWeightPicker.add(target, damageAmount * damageAmount)
    }
    val target = targetWeightPicker.pick()
    val weight = targetWeightPicker.getWeight(target)

    willing += weight/2500f

    //根据当前剩余使用次数，越少越不想用
    if(system.ammo <= 1){
      willing *= 0.15f
    }else if (system.ammo <= 2){
      willing *= 0.35f
    } else if (system.ammo <= 3){
      willing *= 0.65f
    }

    willing *= MathUtils.getRandomNumberInRange(0.8f,1.2f)
    if(willing < 100f) return

    if(target != null){
      if(target.owner == ship.owner){
        ship.setCustomData(ID, findTargetLocFriendly( target))
        shouldActive = true
        return
      }else

      if(target.owner != ship.owner){
        ship.setCustomData(ID, findTargetLocEnemy(target))
        shouldActive = true
        return
      }
    }
  }


  fun findTargetLocFriendly(target: ShipAPI): Vector2f {
    //如果有盾，刷在盾前方200处
    if(target.shield != null && target.shieldTarget != null){
      val facingTo = VectorUtils.getAngle(target.location, target.shieldTarget)
      val point = aEP_Tool.getExtendedLocationFromPoint(target.location, facingTo, target.collisionRadius + 800f)
      return point
    }

    //如果有目标有锁定的敌人，刷在指向锁定方向的200处
    if(target.shipTarget != null ){
      val facingTo= VectorUtils.getAngle(target.location, target.shipTarget.location)
      return aEP_Tool.getExtendedLocationFromPoint(target.location, facingTo, target.collisionRadius + 800f)
    }


    return aEP_Tool.getExtendedLocationFromPoint(target.location, target.facing, target.collisionRadius + 800f)
  }

  fun findTargetLocEnemy(target: ShipAPI): Vector2f {
    //如果有盾，刷在盾背后的100处
    if(target.shield != null && target.shieldTarget != null){
      val facingTo = VectorUtils.getAngle(target.location, target.shieldTarget)
      return aEP_Tool.getExtendedLocationFromPoint(target.location, facingTo - 180f, target.collisionRadius + 100f)
    }

    //如果有目标有锁定的敌人，刷在指向锁定方向背后的100处
    if(target.shipTarget != null ){
      val facingTo= VectorUtils.getAngle(target.location, target.shipTarget.location)
      return aEP_Tool.getExtendedLocationFromPoint(target.location, facingTo - 180f, target.collisionRadius + 100f)
    }

    return target.location
  }

}