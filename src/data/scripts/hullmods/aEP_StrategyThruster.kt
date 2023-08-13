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

class aEP_StrategyThruster:aEP_BaseHullMod() {
  companion object{
    const val ID = "aEP_StrategyThruster"
    const val ALLOW_LEVEL_BONUS = 8f

    const val SHIELD_DAMAGE_TAKEN_BONUS_IN_ZERO = 50f

    val ZERO_FLUX_SPEED_BONUS = LinkedHashMap<ShipAPI.HullSize, Float>()
    const val DEFAULT_BOOST_BONUS = 36f
    init {
      ZERO_FLUX_SPEED_BONUS[ShipAPI.HullSize.CAPITAL_SHIP] = 30f
      ZERO_FLUX_SPEED_BONUS[ShipAPI.HullSize.CRUISER] = 30f
      ZERO_FLUX_SPEED_BONUS[ShipAPI.HullSize.DESTROYER] = 40f
      ZERO_FLUX_SPEED_BONUS[ShipAPI.HullSize.FRIGATE] = 50f
    }

  }

  init {
    haveToBeWithMod.add("aEP_MarkerDissipation")
    notCompatibleList.add(HullMods.UNSTABLE_INJECTOR)
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship.mutableStats.zeroFluxMinimumFluxLevel.modifyFlat(ID, ALLOW_LEVEL_BONUS/100f)
    ship.mutableStats.zeroFluxSpeedBoost.modifyFlat(ID, ZERO_FLUX_SPEED_BONUS[ship.hullSpec.hullSize]?: DEFAULT_BOOST_BONUS)
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if(ship.isEngineBoostActive ){
      ship.mutableStats.shieldAbsorptionMult.modifyPercent(ID, SHIELD_DAMAGE_TAKEN_BONUS_IN_ZERO)
    }

    if(!ship.isEngineBoostActive ){
      ship.mutableStats.shieldAbsorptionMult.modifyPercent(ID, 0f)
    }
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

    //主效果
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    val bonus = ZERO_FLUX_SPEED_BONUS[hullSize]?: DEFAULT_BOOST_BONUS
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_StrategyThruster01"), 5f, arrayOf(Color.green,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", bonus),
      String.format("%.0f", ship?.mutableStats?.zeroFluxSpeedBoost?.modifiedValue?:bonus))
    val level = ALLOW_LEVEL_BONUS
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_StrategyThruster02"), 5f, arrayOf(Color.green,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", level)+"%",
      String.format("%.0f", ship?.mutableStats?.zeroFluxMinimumFluxLevel?.modifiedValue?.times(100f)?:level)+"%")


    //负面
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_StrategyThruster03"), 5f, arrayOf(Color.red,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", SHIELD_DAMAGE_TAKEN_BONUS_IN_ZERO)+"%")

    //显示不兼容插件
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("not_compatible") +"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))

    //预热完全后额外效果
    //tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor, Alignment.MID, 5f)

    //灰字额外说明
    //tooltip.addPara(aEP_DataTool.txt("MD_des04"), grayColor, 5f)

  }


}