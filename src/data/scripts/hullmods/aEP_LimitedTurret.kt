package data.scripts.hullmods

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.TooltipMakerAPI

class aEP_LimitedTurret: aEP_BaseHullMod() {
  companion object{
    private val WEAPON_HEALTH_PERCENT_BONHS = 25f
    private val WEAPON_REPAIR_TIME_REDUCE_MULT = 0.25f
    private val WEAPON_RANGE_BONS: MutableMap<String, Float> = HashMap()

    init {

      WEAPON_RANGE_BONS["aEP_cap_shangshengliu_mk3"] = 30f


    }
  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.INTEGRATED_TARGETING_UNIT)
    notCompatibleList.add(HullMods.DEDICATED_TARGETING_CORE)
    notCompatibleList.add(HullMods.ADVANCED_TARGETING_CORE)
  }


  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?, ship: ShipAPI?): String {

    if (index == 0) return String.format("%.0f", WEAPON_RANGE_BONS[ship?.hullSpec?.baseHullId?:""]?:25f) +"%"
    if (index == 1) return String.format("%.0f", WEAPON_HEALTH_PERCENT_BONHS ) +"%"
    if (index == 2) return String.format("%.0f", WEAPON_REPAIR_TIME_REDUCE_MULT * 100f) +"%"
    else return ""

  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    val bonus = WEAPON_RANGE_BONS[ship.hullSpec.baseHullId]?: 25f
    ship.mutableStats.ballisticWeaponRangeBonus.modifyPercent(id, bonus)
    ship.mutableStats.energyWeaponRangeBonus.modifyPercent(id, bonus)

    ship.mutableStats.weaponHealthBonus.modifyPercent(id, WEAPON_HEALTH_PERCENT_BONHS)
    ship.mutableStats.combatWeaponRepairTimeMult.modifyMult(id,1f-WEAPON_REPAIR_TIME_REDUCE_MULT)
  }
}