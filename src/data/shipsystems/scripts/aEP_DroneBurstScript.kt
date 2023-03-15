package data.shipsystems.scripts

import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript

class aEP_DroneBurstScript : BaseShipSystemScript() {
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    if (state == ShipSystemStatsScript.State.ACTIVE) {
      stats.maxSpeed.modifyPercent(id, 200f)
      stats.maxTurnRate.modifyPercent(id, -60f)
      stats.zeroFluxSpeedBoost.modifyFlat(id, -50f)
    } else if (state == ShipSystemStatsScript.State.IDLE) {
      stats.maxSpeed.unmodify(id)
      stats.maxTurnRate.unmodify(id)
      stats.zeroFluxSpeedBoost.unmodify(id)
    }
  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    stats.maxSpeed.unmodify(id)
    stats.maxTurnRate.unmodify(id)
    stats.zeroFluxSpeedBoost.unmodify(id)
  }
}