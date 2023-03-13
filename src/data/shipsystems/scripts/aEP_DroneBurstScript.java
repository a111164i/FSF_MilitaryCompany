package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class aEP_DroneBurstScript extends BaseShipSystemScript
{

  @Override
  public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    if (state == State.ACTIVE) {
      stats.getMaxSpeed().modifyPercent(id, 200f);
      stats.getMaxTurnRate().modifyPercent(id, -60f);
      stats.getZeroFluxSpeedBoost().modifyFlat(id, -50f);
    }
    else if (state == State.IDLE) {
      stats.getMaxSpeed().unmodify(id);
      stats.getMaxTurnRate().unmodify(id);
      stats.getZeroFluxSpeedBoost().unmodify(id);
    }
  }

  @Override
  public void unapply(MutableShipStatsAPI stats, String id) {
    stats.getMaxSpeed().unmodify(id);
    stats.getMaxTurnRate().unmodify(id);
    stats.getZeroFluxSpeedBoost().unmodify(id);
  }
}