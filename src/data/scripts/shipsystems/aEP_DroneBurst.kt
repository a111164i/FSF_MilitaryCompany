package data.scripts.shipsystems

import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript

class aEP_DroneBurst : BaseShipSystemScript() {
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    stats.maxSpeed.modifyPercent(id, 100f * effectLevel)
    stats.maxTurnRate.modifyPercent(id, -50f * effectLevel)
  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    stats.maxSpeed.unmodify(id)
    stats.maxTurnRate.unmodify(id)
  }
}