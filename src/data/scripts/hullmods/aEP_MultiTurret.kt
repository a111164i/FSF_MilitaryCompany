package data.scripts.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.impl.campaign.ids.Stats

class aEP_MultiTurret : BaseHullMod() {
  companion object {
    const val SMALL_WEAPON_MOD = 1
    const val MEDIUM_WEAPON_MOD = 3
    const val LARGE_WEAPON_MOD = 8
  }

  override fun applyEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
    stats.dynamic.getMod(Stats.SMALL_BALLISTIC_MOD).modifyFlat(id, -SMALL_WEAPON_MOD.toFloat())
    stats.dynamic.getMod(Stats.MEDIUM_BALLISTIC_MOD).modifyFlat(id, -MEDIUM_WEAPON_MOD.toFloat())
    stats.dynamic.getMod(Stats.LARGE_BALLISTIC_MOD).modifyFlat(id, -LARGE_WEAPON_MOD.toFloat())
    stats.dynamic.getMod(Stats.SMALL_ENERGY_MOD).modifyFlat(id, -SMALL_WEAPON_MOD.toFloat())
    stats.dynamic.getMod(Stats.MEDIUM_ENERGY_MOD).modifyFlat(id, -MEDIUM_WEAPON_MOD.toFloat())
    stats.dynamic.getMod(Stats.LARGE_ENERGY_MOD).modifyFlat(id, -LARGE_WEAPON_MOD.toFloat())
  }


  override fun getDescriptionParam(index: Int, hullSize: HullSize): String? {
    if (index == 0) return "" + SMALL_WEAPON_MOD
    if (index == 1) return "" + MEDIUM_WEAPON_MOD
    return if (index == 2) "" + LARGE_WEAPON_MOD else null
  }

  override fun affectsOPCosts(): Boolean {
    return true
  }


}