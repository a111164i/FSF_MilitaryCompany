package data.shipsystems.scripts.ai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAIScript
import com.fs.starfarer.api.combat.WeaponAPI
import combat.util.aEP_Tool
import data.hullmods.aEP_MissilePlatform
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f

class aEP_RequanReloadAI: aEP_BaseSystemAI() {

  var loadingMap : aEP_MissilePlatform.LoadingMap? = null

  override fun initImpl() {
    thinkTracker.setInterval(0.5f,0.5f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    //录入 loadingMap
    loadingMap?: run {
      if(ship.customData.containsKey(aEP_MissilePlatform.ID)) loadingMap= ship.customData[aEP_MissilePlatform.ID] as aEP_MissilePlatform.LoadingMap
    }

    var willing = 0f
    var reloadLevelTotal = 0f
    var totalOp = 0f

    for(w in ship.allWeapons){
      //对于不需要弹药的导弹，跳过
      if(w.type != WeaponAPI.WeaponType.MISSILE) continue
      if(!w.usesAmmo() && w.ammo < 1) continue

      //计算所有非内置武器目前的冷却时间，看看按一下能装多少导弹
      if( w.slot.weaponType == WeaponAPI.WeaponType.MISSILE) {
        val op = w.spec.getOrdnancePointCost(null) + 0.01f
        //小心有些武器的只有delay，没用cooldown
        val coolDown = w.cooldown + 0.01f
        val coolDownRemaining = w.cooldownRemaining
        reloadLevelTotal += (op * (coolDownRemaining / coolDown))
        totalOp += op
      //计算内置的热泉导弹的情况
      }else if(w.slot.weaponType == WeaponAPI.WeaponType.BUILT_IN ){
        if(w.ammo <= 1){
          willing += 0.35f
        }
      }
    }
    //0f-1f，冷却百分比
    reloadLevelTotal /= totalOp
    willing += reloadLevelTotal

    //保证幅能小于0.25时，光是用完内置导弹就会使用f
    val highThreshold = 0.7f
    val lowThreshold = 0.2f
    if(ship.fluxLevel < lowThreshold){
      willing += 0.65f
    }else if(ship.fluxLevel > 0.2f && ship.fluxLevel < highThreshold){
      willing +=0.8f* ((highThreshold - ship.fluxLevel)/(1f-highThreshold) )
    }

    willing *= MathUtils.getRandomNumberInRange(0.75f,1.25f)
    shouldActive = false
    if(willing >= 1f){
      shouldActive = true
    }
  }
}