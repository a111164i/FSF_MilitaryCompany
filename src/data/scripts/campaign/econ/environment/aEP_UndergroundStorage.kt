package data.scripts.campaign.econ.environment

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import java.awt.Color

class aEP_UndergroundStorage : BaseMarketConditionPlugin() {
  companion object{
    const val GROUND_DEFENSE_BONUS = 2000f
    const val MINIMUM_STABILITY = 5f;
    const val MINIMUM_STABILITY_MAX_BONUS = 10f;
  }

  override fun apply(id: String) {
    if(market.faction?.id != aEP_ID.FACTION_ID_FSF) return

    //势力争霸特供
    if (Global.getSettings().modManager.isModEnabled("nexerelin")) {
      market.stats.dynamic.getMod(Stats.GROUND_DEFENSES_MOD).modifyFlat(modId, GROUND_DEFENSE_BONUS, name)
      market.stats.dynamic.getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat(modId, 2f, name)
      market.stats.dynamic.getMod(Stats.PATROL_NUM_HEAVY_MOD).modifyFlat(modId, 2f, name)
    }

  }

  //势力争霸里必须每帧
  override fun advance(amount: Float) {
    if(market.faction?.id != aEP_ID.FACTION_ID_FSF) return

    if (market.stabilityValue < MINIMUM_STABILITY) {
      val bonus = (MINIMUM_STABILITY - (market.stabilityValue)).coerceAtMost(MINIMUM_STABILITY_MAX_BONUS)
      market.stability.modifyFlat(modId, bonus, name)
    }

  }

  override fun runWhilePaused(): Boolean {
    return true
  }

  override fun unapply(id: String) {
    market.stability.unmodify(modId)
    market.stats.dynamic.getMod(Stats.GROUND_DEFENSES_MOD).unmodify(modId)
    market.stats.dynamic.getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(modId)
    market.stats.dynamic.getMod(Stats.PATROL_NUM_MEDIUM_MOD).unmodify(modId)
  }

  /**
   * 覆盖掉description里面的描述
   * */
  override fun hasCustomTooltip(): Boolean {
    return true
  }

  /**
   * 和上面配套使用
   * */
  override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean) {
    super.createTooltip(tooltip, expanded)
  }

  /**
   * 如果不覆盖base类，会在createTooltip的最后调用
   * */
  override fun createTooltipAfterDescription(tooltip: TooltipMakerAPI, expanded: Boolean) {

    if(market.faction?.id != aEP_ID.FACTION_ID_FSF) return
    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f)

    tooltip.addPara("{%s}"+txt("aEP_UndergroundStorage01"), 5f, arrayOf(Color.green),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", MINIMUM_STABILITY),
      String.format("%.0f", MINIMUM_STABILITY_MAX_BONUS),)

    if (Global.getSettings().modManager.isModEnabled("nexerelin")) {
      tooltip.addPara("{%s}"+txt("aEP_UndergroundStorage02"), 5f, arrayOf(Color.green),
        aEP_ID.HULLMOD_POINT)
      tooltip.addPara("{%s}"+txt("aEP_UndergroundStorage03"), 5f, arrayOf(Color.green),
        aEP_ID.HULLMOD_POINT)
      tooltip.addPara("{%s}"+txt("aEP_UndergroundStorage04"), 5f, arrayOf(Color.green),
        aEP_ID.HULLMOD_POINT)
    }
  }

  override fun isTooltipExpandable(): Boolean {
    return false
  }
}