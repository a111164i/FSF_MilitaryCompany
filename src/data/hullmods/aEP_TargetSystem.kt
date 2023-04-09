package data.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import java.util.HashMap
import data.hullmods.aEP_TargetSystem
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import org.lazywizard.lazylib.MathUtils
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import data.scripts.world.aEP_systems.aEP_BaseEveryFrame
import org.lazywizard.lazylib.combat.AIUtils
import java.awt.Color

class aEP_TargetSystem : aEP_BaseHullMod() {
  companion object {

    const val ID = "aEP_TargetSystem"
    const val RANGE_PERCENT_BONUS = 60f
    const val RANGE_PD_PERCENT_BONUS = 40f
    const val RANGE_THRESHOLD = 1600f
    const val THRESHOLD_PUNISH = 0.5f
  }

  init {
    haveToBeWithMod.add(aEP_MarkerDissipation.ID)
    notCompatibleList.add(HullMods.INTEGRATED_TARGETING_UNIT)
    notCompatibleList.add(HullMods.DEDICATED_TARGETING_CORE)
    notCompatibleList.add(HullMods.SAFETYOVERRIDES)
  }


  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    ship.mutableStats.ballisticWeaponRangeBonus.modifyPercent(ID, RANGE_PERCENT_BONUS)
    ship.mutableStats.energyWeaponRangeBonus.modifyPercent(ID, RANGE_PERCENT_BONUS)

    ship.mutableStats.weaponRangeThreshold.modifyFlat(ID, RANGE_THRESHOLD)
    ship.mutableStats.weaponRangeMultPastThreshold.modifyMult(ID,THRESHOLD_PUNISH)

    ship.mutableStats.nonBeamPDWeaponRangeBonus.modifyPercent(ID, RANGE_PD_PERCENT_BONUS - RANGE_PERCENT_BONUS)
    ship.mutableStats.beamPDWeaponRangeBonus.modifyPercent(ID, RANGE_PD_PERCENT_BONUS - RANGE_PERCENT_BONUS)

  }


  override fun getDescriptionParam(index: Int, hullSize: HullSize?, ship: ShipAPI?): String? {
    if (index == 0) return String.format("%.0f", RANGE_PERCENT_BONUS) +"%"
    if (index == 1) return String.format("%.0f", RANGE_PD_PERCENT_BONUS) +"%"
    if (index == 2) return String.format("%.0f", RANGE_THRESHOLD)
    if (index == 3) return String.format("%.0f", THRESHOLD_PUNISH*100f) +"%"
    else return null
  }

}