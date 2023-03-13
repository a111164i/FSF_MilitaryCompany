package data.shipsystems.scripts

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import java.awt.Color

class aEP_DroneTimeAlter: BaseShipSystemScript() {
  companion object{
    const val TIME_ALTER_BONUS_MULT = 2f
    val JITTER_COLOR = Color(90, 165, 255, 55)
    val JITTER_UNDER_COLOR = Color(90, 165, 255, 155)

  }

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    val stats = (stats?:return) as MutableShipStatsAPI
    val ship = (stats?.entity?: return) as ShipAPI
    ship.setJitter(id, JITTER_COLOR, effectLevel, 3, 0f, effectLevel*5f)
    ship.setJitterUnder(id,JITTER_UNDER_COLOR, effectLevel, 25, 0f, 5f + effectLevel*5f)
    ship.isJitterShields = false
    stats.timeMult.modifyMult(id,1f+ TIME_ALTER_BONUS_MULT*effectLevel)
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    val stats = (stats?:return) as MutableShipStatsAPI
    stats.timeMult.unmodify(id)
  }
}