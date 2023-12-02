package data.scripts.hullmods

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI

class aEP_FocusOnShield : aEP_BaseHullMod(){

  companion object{
    const val ID = "aEP_FocusOnShield"
    const val SHIELD_DAMAGE_TAKEN_REDUCE_MULT = 0.5f
    const val WEAPON_FLUX_USE_PERCENT_BONUS = 100f
    const val MAX_SHIELD_ANGLE = 200f
  }

  init {
    notCompatibleList.add(aEP_ShieldFloating.ID)
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if(ship.shield?.isOn == true){
      val arc = ship.shield.activeArc
      if(arc > MAX_SHIELD_ANGLE){
        ship.shield.activeArc = MAX_SHIELD_ANGLE
      }
    }
  }

  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize, stats: MutableShipStatsAPI, id: String) {
    stats.shieldDamageTakenMult.modifyMult(ID, 1f - SHIELD_DAMAGE_TAKEN_REDUCE_MULT)

    stats.ballisticWeaponFluxCostMod.modifyPercent(ID, WEAPON_FLUX_USE_PERCENT_BONUS)
    stats.energyWeaponFluxCostMod.modifyPercent(ID, WEAPON_FLUX_USE_PERCENT_BONUS)
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize, ship: ShipAPI?): String {
    if (index == 0) return String.format("-%.0f", SHIELD_DAMAGE_TAKEN_REDUCE_MULT * 100f) +"%"
    if (index == 1) return String.format("+%.0f", WEAPON_FLUX_USE_PERCENT_BONUS) +"%"
    if (index == 2) return String.format("%.0f", MAX_SHIELD_ANGLE)
    return ""
  }

}