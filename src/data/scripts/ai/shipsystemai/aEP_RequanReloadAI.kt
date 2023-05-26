package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import combat.impl.VEs.aEP_MovingSmoke
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Tool
import data.scripts.hullmods.aEP_MissilePlatform
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_RequanReloadAI: aEP_BaseSystemAI() {

  var loadingMap : aEP_MissilePlatform.LoadingMap? = null

  override fun initImpl() {
    thinkTracker.setInterval(1f,1f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    //录入 loadingMap
    loadingMap?: run {
      if(ship.customData.containsKey(aEP_MissilePlatform.ID))
        loadingMap= ship.customData[aEP_MissilePlatform.ID] as aEP_MissilePlatform.LoadingMap
    }

    //用不起就不需要思考了
    if(ship.fluxTracker.maxFlux - ship.fluxTracker.currFlux < ship.system.fluxPerSecond) {
      shouldActive = false
      return
    }


    var willing = 0f
    var reloadLevelTotal = 0f
    var totalOp = 0f
    val maxRate = loadingMap?.maxRate?:1f
    val currRate = loadingMap?.currRate?:1f

    var needReloadRqMissile = false
    for(w in ship.allWeapons){
      //对于不需要弹药的导弹，跳过
      if(w.type != WeaponAPI.WeaponType.MISSILE) continue
      if(!w.usesAmmo() || w.ammo == Int.MAX_VALUE) continue

      //计算所有非内置武器目前的冷却时间，看看按一下能装多少导弹
      if( w.slot.weaponType == WeaponAPI.WeaponType.MISSILE || w.slot.weaponType == WeaponAPI.WeaponType.COMPOSITE) {
        val op = w.spec.getOrdnancePointCost(null) + 0.01f
        //小心有些武器的只有delay，没用cooldown
        val coolDown = w.cooldown + 0.01f
        val coolDownRemaining = w.cooldownRemaining
        reloadLevelTotal += (op * (coolDownRemaining / coolDown))
        totalOp += op

      //计算内置的热泉导弹的情况
      }else if(w.spec.weaponId.contains("requan_missile")){
        if(w.ammo <= 1 ){
          needReloadRqMissile = true
        }
      }
    }

    //0f-1f，冷却百分比
    reloadLevelTotal /= totalOp
    //当热泉导弹需要填装时，挤占普通导弹冷却率的权衡权重，把一部导弹视为全部需要装填
    if(needReloadRqMissile){
      val weight = 0.4f
      reloadLevelTotal = reloadLevelTotal * (1f-weight) + weight
    }

    //0f-0.5f，总装填率目前下降了多少
    val emptyRate = (maxRate - currRate)/maxRate

    //因为导弹冷却率是0-1，总装填率是0-0.5，平衡一下两者
    willing += (reloadLevelTotal * 0.8f)
    willing += (emptyRate * 1.5f)

    //保证幅能小于0.25时，光是用完内置导弹就会使用f
    val highThreshold = 1f
    val lowThreshold = 0.25f
    val lowFluxBonus = (willing * 0.8f + 0.25f)
    if(ship.fluxLevel < lowThreshold){
      willing += lowFluxBonus
    }else if(ship.fluxLevel > lowThreshold && ship.fluxLevel < highThreshold){
      willing += lowFluxBonus * ((highThreshold - ship.fluxLevel)/(highThreshold-lowThreshold) )
    }

    willing *= MathUtils.getRandomNumberInRange(0.75f,1.25f)
    aEP_Tool.addDebugLog(willing.toString())
    shouldActive = false
    if(willing >= 1f){
      shouldActive = true
    }
  }
}