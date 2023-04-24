package data.scripts.shipsystems

import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import combat.util.aEP_Tool.Util.isNormalWeaponType
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.combat.ShipAPI
import data.scripts.shipsystems.aEP_NCReloadScript.RefresherOrb
import com.fs.starfarer.api.combat.WeaponAPI
import data.scripts.hullmods.aEP_MissilePlatform

class aEP_RequanReload : BaseShipSystemScript() {

  companion object {
    private const val COOLDOWN_REDUCE = 8f
  }

  var didVisual = false

  override fun apply(stats: MutableShipStatsAPI?, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI

    //使用的一瞬间启用特效
    if (!didVisual) {
      addEffect(RefresherOrb(ship))
      didVisual = true
    }

    //effectLevel == 1f 时运行一次
    if (effectLevel < 1f) return
    
    //回满总装填率
    if(ship.customData.containsKey(aEP_MissilePlatform.ID)){
      val loadingClass = ship.customData[aEP_MissilePlatform.ID] as aEP_MissilePlatform.LoadingMap
      loadingClass.currRate = loadingClass.maxRate
    }

    for (w in ship.allWeapons) {
      //少量减少内置导弹cd
      if (w.slot.weaponType != WeaponAPI.WeaponType.BUILT_IN) {
        if (isNormalWeaponType(w, true) && w.spec.type == WeaponAPI.WeaponType.MISSILE) {
          w.setRemainingCooldownTo(w.cooldownRemaining - Math.min(COOLDOWN_REDUCE, w.cooldownRemaining))
        }
      } else { //回复系统导弹
        w.beginSelectionFlash()
        w.ammoTracker.ammo = Math.min(w.ammo + w.spec.burstSize, w.maxAmmo)
      }
    }
  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    didVisual = false
  }

}