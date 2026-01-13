package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_DataTool.txt
import data.scripts.utils.aEP_ID
import java.awt.Color

class aEP_TargetSystem : aEP_BaseHullMod() {
  companion object {

    const val ID = "aEP_TargetSystem"
    const val RANGE_PERCENT_BONUS = 50f
    const val RANGE_PD_PERCENT_BONUS = 35f
    const val RANGE_THRESHOLD = 1450f
    const val PASS_PUNISH_MULT= 0.5f

    fun shouldModRange(w:WeaponAPI):Boolean{
      if(w.type != WeaponAPI.WeaponType.BALLISTIC && w.type == WeaponAPI.WeaponType.ENERGY) return false
      if(w.spec.maxRange * (100f + RANGE_PERCENT_BONUS)/100f <= RANGE_THRESHOLD) return false
      return true
    }
  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.INTEGRATED_TARGETING_UNIT)
    notCompatibleList.add(HullMods.DEDICATED_TARGETING_CORE)
    notCompatibleList.add(HullMods.ADVANCED_TARGETING_CORE)
  }


  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    ship.mutableStats.ballisticWeaponRangeBonus.modifyPercent(ID, RANGE_PERCENT_BONUS)
    ship.mutableStats.energyWeaponRangeBonus.modifyPercent(ID, RANGE_PERCENT_BONUS)

    ship.mutableStats.nonBeamPDWeaponRangeBonus.modifyPercent(ID, RANGE_PD_PERCENT_BONUS - RANGE_PERCENT_BONUS)
    ship.mutableStats.beamPDWeaponRangeBonus.modifyPercent(ID, RANGE_PD_PERCENT_BONUS - RANGE_PERCENT_BONUS)

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

    //表格显示所有受到射程惩罚的武器
    //用表格显示总装填率的最大值，回复速度，最大消耗速度
    val affectedWeaponList = ArrayList<WeaponSpecAPI>()
    ship?.run {
      for(w in ship.allWeapons){
        if(!shouldModRange(w)) continue
        if(!affectedWeaponList.contains(w.spec)) affectedWeaponList.add(w.spec)
      }
    }
    if(!affectedWeaponList.isEmpty()){
      val col2W0 = 120f
      //第一列显示的名称，尽可能可能的长
      val col1W0 = (width - col2W0 - PARAGRAPH_PADDING_BIG)
      tooltip.beginTable(
        factionColor, factionDarkColor, factionBrightColor,
        TEXT_HEIGHT_SMALL, true, true,
        *arrayOf<Any>(
          txt("weapon_spec"), col1W0,
          txt("punish"), col2W0)
      )
      for(spec in affectedWeaponList){
        val punish = (spec.maxRange * (100f+RANGE_PERCENT_BONUS)/100f - RANGE_THRESHOLD) * PASS_PUNISH_MULT
        tooltip.addRow(
          Alignment.MID, highlight, spec.weaponName,
          Alignment.MID, negativeHighlight, String.format("-%.0f", punish),
        )
      }
      tooltip.addTable("", 0, PARAGRAPH_PADDING_SMALL)
    }


    showIncompatible(tooltip)
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize?, ship: ShipAPI?): String? {
    if (index == 0) return String.format("+%.0f", RANGE_PERCENT_BONUS) +"%"
    if (index == 1) return String.format("+%.0f", RANGE_PD_PERCENT_BONUS) +"%"
    if (index == 2) return String.format("%.0f", RANGE_THRESHOLD)
    if (index == 3) return String.format("%.0f", (1f-PASS_PUNISH_MULT)*100f) +"%"
    else return null
  }

}