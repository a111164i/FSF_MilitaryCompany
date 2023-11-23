package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_SwitchPowerMode
import data.scripts.weapons.aEP_fga_xiliu_main
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f

class aEP_SwitchPowerModeAI: aEP_BaseSystemAI() {

  override fun initImpl() {
    thinkTracker.setInterval(0.1f,0.3f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {

    val checkRange = computeMaxWeaponRange()
    val currMode = (ship.customData[aEP_SwitchPowerMode.ID]?:0) as Int

    shouldActive = false
    val target = ship.shipTarget
    if(target != null){
      val distToTarget = MathUtils.getDistance(ship, target)
      if(distToTarget < checkRange - 50f){
        shouldActive = true
      }
    }else{
      val nearestShip = aEP_Tool.getNearestEnemyCombatShip(ship)?:return
      val distToTarget = MathUtils.getDistance(ship, nearestShip)
      //如果最近的敌人舰船目标处于自己的射程外
      if(distToTarget > checkRange){
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

    //上面得出的shouldActive是是否需要切换的模式1，这里根据目前的0/1模式翻译成实际的shouldActive
    //需要变1，当前是0
    if(shouldActive && currMode == 0){
      shouldActive = true
    //需要变1，当前是1
    }else if(shouldActive && currMode == 1){
      shouldActive = false
    //需要变0，当前是0
    }else if(!shouldActive && currMode == 0){
      shouldActive = false
    //需要变0，当前是1
    }else if(!shouldActive && currMode == 1){
      shouldActive = true
    }

  }

  fun computeMaxWeaponRange(): Float{
    for(weapon in ship.allWeapons){
      if(!aEP_Tool.isNormalWeaponSlotType(weapon.slot,false)) continue
      if(weapon.spec.weaponId.equals(aEP_fga_xiliu_main.WEAPON_ID+"2")){
        return weapon.range
        break
      }
    }
    return 450f
  }
}