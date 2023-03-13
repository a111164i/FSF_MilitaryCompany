package data.scripts.campaign.econ.environment

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin
import com.fs.starfarer.api.impl.campaign.ids.Stats

class aEP_UndergroundStorage : BaseMarketConditionPlugin() {
  override fun apply(id: String) {}

  //势力争霸里必须每帧
  override fun advance(amount: Float) {
    if (market.stabilityValue < 1) {
      market.stability.modifyFlat(modId, 1 - market.stabilityValue)
    }
    if (Global.getSettings().modManager.isModEnabled("nexerelin") && market.faction?.id == "aEP_FSF" &&
      market.faction?.id != Global.getSector().playerFaction?.id
    ) {
      val sizeCompensate = (800f - market.size * 50f).coerceAtLeast(100f)
      market.stats.dynamic.getMod(Stats.GROUND_DEFENSES_MOD).modifyFlat(modId, sizeCompensate, name)
      market.stats.dynamic.getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat(modId, 0.5f, name)
      market.stats.dynamic.getMod(Stats.PATROL_NUM_MEDIUM_MOD).modifyFlat(modId, 1f, name)
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
}