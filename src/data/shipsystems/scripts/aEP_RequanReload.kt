package data.shipsystems.scripts

import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import combat.util.aEP_Tool.Util.isNormalWeaponType
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.combat.ShipAPI
import combat.plugin.aEP_CombatEffectPlugin
import data.shipsystems.scripts.aEP_NCReloadScript.RefresherOrb
import com.fs.starfarer.api.combat.WeaponAPI
import combat.util.aEP_Tool
import data.shipsystems.scripts.aEP_RequanReload

class aEP_RequanReload : BaseShipSystemScript() {
  var didVisual = false
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    val ship = stats.entity as ShipAPI ?: return

    //使用的一瞬间启用特效
    if (!didVisual) {
      addEffect(RefresherOrb(ship))
      didVisual = true
    }

    //effectLevel == 1f 时运行一次
    if (effectLevel < 1f) return
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

  companion object {
    private const val COOLDOWN_REDUCE = 5f
  }
}