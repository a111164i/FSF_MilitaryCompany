package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.AutofireAIPlugin
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_MultiTracker
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.lang.Math.max

class aEP_MultiTrackerAI: aEP_BaseSystemAI() {
  override fun initImpl() {

    thinkTracker.setInterval(0.1f,0.4f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false

    val checkRange = computeMaxWeaponRange()

    if(ship.shipTarget != null){
      val target = ship.shipTarget as ShipAPI
      val distToTarget = MathUtils.getDistance(ship, target)
      if(distToTarget < checkRange - 50f){
        shouldActive = true
      }
    }else{
      val nearestShip = aEP_Tool.getNearestEnemyCombatShip(ship)?:return
      val distToTarget = MathUtils.getDistance(ship, nearestShip)
      //如果最近的敌人舰船目标处于自己的射程外
      if(distToTarget > checkRange/(1f - aEP_MultiTracker.WEAPON_RANGE_REDUCE_MULT)){
        //有武器组正在自动射击处于系统距离内的导弹或是战机
        for(group in ship.weaponGroupsCopy){
          for(ai in group.aiPlugins){
            ai?: continue
            ai.target ?: continue
            //如果目标超过了系统距离，跳过
            val dist = MathUtils.getDistance(ship.location, ai.target)
            if(dist > checkRange) continue
            //如果目标处于系统距离，使用系统
            if(ai.targetShip != null){
              if(ai.targetShip.isFighter ){
                shouldActive = true
                break
              }
            }
            if(ai.targetMissile != null){
              shouldActive = true
              break
            }
          }
          if(shouldActive) break
        }

      }

    }
  }

  fun computeMaxWeaponRange(): Float{
    var baseRange = 300f
    val flatBonus = 0f
    for(weapon in ship.allWeapons){
      if(!aEP_Tool.isNormalWeaponSlotType(weapon.slot,false)) continue
      if(weapon.hasAIHint(WeaponAPI.AIHints.PD)){
        baseRange = (weapon.range + flatBonus).coerceAtLeast(baseRange)
      }else{
        baseRange = ((weapon.range + flatBonus) * (1f - aEP_MultiTracker.WEAPON_RANGE_REDUCE_MULT)).coerceAtLeast(baseRange)
      }
    }

    return baseRange
  }
}