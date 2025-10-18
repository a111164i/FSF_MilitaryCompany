package data.scripts.hullmods

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI

class aEP_AcceleratedGunnery: aEP_BaseHullMod() {
  companion object{
    const val ID = "aEP_AcceleratedGunnery"
    const val ROF_BONUS = 0.25f
    const val FLUX_WASTE_BONUS = 0.05f

  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    ship.mutableStats.ballisticRoFMult.modifyFlat(ID, ROF_BONUS)
    ship.mutableStats.energyRoFMult.modifyFlat(ID, ROF_BONUS)

    ship.mutableStats.ballisticAmmoRegenMult.modifyFlat(ID, ROF_BONUS)
    ship.mutableStats.energyAmmoRegenMult.modifyFlat(ID, ROF_BONUS)

    ship.mutableStats.ballisticWeaponFluxCostMod.modifyMult(ID,1f+FLUX_WASTE_BONUS)
    ship.mutableStats.energyWeaponFluxCostMod.modifyMult(ID,1f+FLUX_WASTE_BONUS)
  }


  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?, ship: ShipAPI?): String? {
    if (index == 0) return String.format("+%.0f", ROF_BONUS*100f) +"%"
    if (index == 1) return String.format("+%.0f", FLUX_WASTE_BONUS*100f) +"%"
    return null
  }
}