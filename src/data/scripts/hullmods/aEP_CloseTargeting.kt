package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_DataTool.txt
import data.scripts.utils.aEP_ID
import java.awt.Color

class aEP_CloseTargeting : aEP_BaseHullMod() {

  companion object{
    const val ID = "aEP_CloseTargeting"

    val SPREAD_REDUCE_MULT = 0.5f

    val PD_WEAPON_RANGE_BONUS = HashMap<HullSize, Float>()
    init {
      PD_WEAPON_RANGE_BONUS[HullSize.CAPITAL_SHIP] = 60f
      PD_WEAPON_RANGE_BONUS[HullSize.CRUISER] = 60f
      PD_WEAPON_RANGE_BONUS[HullSize.DESTROYER] = 60f
      PD_WEAPON_RANGE_BONUS[HullSize.FRIGATE] = 60f
    }

    val TO_FIGHTER_BONUS = HashMap<HullSize, Float>()
    init {
      TO_FIGHTER_BONUS[HullSize.CAPITAL_SHIP] = 10f
      TO_FIGHTER_BONUS[HullSize.CRUISER] = 20f
      TO_FIGHTER_BONUS[HullSize.DESTROYER] = 35f
      TO_FIGHTER_BONUS[HullSize.FRIGATE] = 50f
    }

  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.INTEGRATED_TARGETING_UNIT)
    notCompatibleList.add(HullMods.DEDICATED_TARGETING_CORE)
    notCompatibleList.add(HullMods.ADVANCED_TARGETING_CORE)

  }


  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize, stats: MutableShipStatsAPI, id: String) {
    stats.beamPDWeaponRangeBonus.modifyPercent(ID, PD_WEAPON_RANGE_BONUS[hullSize]?:60f)
    stats.nonBeamPDWeaponRangeBonus.modifyPercent(ID, PD_WEAPON_RANGE_BONUS[hullSize]?:60f)

    stats.recoilPerShotMult.modifyMult(ID,1f - SPREAD_REDUCE_MULT)
    stats.maxRecoilMult.modifyMult(ID,1f - SPREAD_REDUCE_MULT)

    // 反战机追加效果
    stats.damageToFighters.modifyPercent(ID, TO_FIGHTER_BONUS[hullSize]?:50f)

  }



  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {

    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highlight = Misc.getHighlightColor()
    val negativeHighlight = Misc.getNegativeHighlightColor()

    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()

    val titleTextColor: Color = faction.color
    val factionColor: Color = faction.baseUIColor
    val factionDarkColor = faction.darkUIColor
    val factionBrightColor = faction.brightUIColor

    // 反战机追加效果
    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f)
    addPositivePara(tooltip, "aEP_CloseTargeting01", arrayOf("+${(TO_FIGHTER_BONUS[hullSize]?:50f).toInt()}%"))

  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize): String? {
    if (index == 0) return String.format("+%.0f", PD_WEAPON_RANGE_BONUS[hullSize]?: 60f) +"%"
    if (index == 1) return String.format("-%.0f", SPREAD_REDUCE_MULT * 100f) +"%"
    return null
  }

}
