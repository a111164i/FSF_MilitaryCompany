package data.hullmods

import com.fs.starfarer.api.combat.ShipAPI

class aEP_BBAdjustRange : aEP_BaseHullMod() {
  init {
    notCompatibleList.add("dedicated_targeting_core")
    notCompatibleList.add("targetingunit")
  }

  /**
   * 使用这个
   */
  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship?.mutableStats?.beamPDWeaponRangeBonus?.modifyPercent(id,60f)
    ship?.mutableStats?.nonBeamPDWeaponRangeBonus?.modifyPercent(id,60f)
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String {
    if(index == 0) return "60"
    return ""
  }
}