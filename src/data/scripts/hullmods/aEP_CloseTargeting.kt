package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_ID
import java.awt.Color
import java.util.HashMap

class aEP_CloseTargeting : aEP_BaseHullMod() {

  companion object{
    const val ID = "aEP_CloseTargeting"

    const val DAMAGE_MISSILE_PERCENT_BONUS = 50f
    const val DAMAGE_FTR_PERCENT_BONUS = 50f

    const val TURRET_DAMAGE_REDUCE_MULT = 0.5f

    private val WEAPON_RANGE_FLAT_BONUS: MutableMap<ShipAPI.HullSize, Float> = HashMap()
    init {
      WEAPON_RANGE_FLAT_BONUS[ShipAPI.HullSize.FIGHTER] = 0f
      WEAPON_RANGE_FLAT_BONUS[ShipAPI.HullSize.FRIGATE] = 50f
      WEAPON_RANGE_FLAT_BONUS[ShipAPI.HullSize.DESTROYER] = 75f
      WEAPON_RANGE_FLAT_BONUS[ShipAPI.HullSize.CRUISER] = 150f
      WEAPON_RANGE_FLAT_BONUS[ShipAPI.HullSize.CAPITAL_SHIP] = 200f
    }
  }

  init {
    haveToBeWithMod.add(aEP_MarkerDissipation.ID)
    notCompatibleList.add(HullMods.INTEGRATED_TARGETING_UNIT)
    notCompatibleList.add(HullMods.DEDICATED_TARGETING_CORE)
    notCompatibleList.add(HullMods.SAFETYOVERRIDES)
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    ship.mutableStats.weaponDamageTakenMult.modifyMult(ID, 1f - TURRET_DAMAGE_REDUCE_MULT)

    ship.mutableStats.damageToMissiles.modifyPercent(ID, DAMAGE_MISSILE_PERCENT_BONUS)
    ship.mutableStats.damageToFighters.modifyPercent(ID, DAMAGE_FTR_PERCENT_BONUS)

    ship.mutableStats.ballisticWeaponRangeBonus.modifyFlat(ID, WEAPON_RANGE_FLAT_BONUS[ship.hullSize]?:50f)
    ship.mutableStats.energyWeaponRangeBonus.modifyFlat(ID, WEAPON_RANGE_FLAT_BONUS[ship.hullSize]?:50f)

  }

  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_CloseTargeting01"), 5f, arrayOf(Color.green, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", WEAPON_RANGE_FLAT_BONUS[hullSize]))
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_CloseTargeting02"), 5f, arrayOf(Color.green, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", DAMAGE_FTR_PERCENT_BONUS) +"%")
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_CloseTargeting03"), 5f, arrayOf(Color.green, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", TURRET_DAMAGE_REDUCE_MULT*100f) +"%")
    //显示不兼容插件
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("not_compatible") +"{%s}", 5f, arrayOf(Color.red, highLight),
      aEP_ID.HULLMOD_POINT,
      showModName(notCompatibleList))
  }

}