package data.scripts.campaign.econ.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;


public class aEP_FSFOrbitalStationPlugin extends OrbitalStation
{


  @Override
  public boolean isAvailableToBuild() {
    return true;
  }

  @Override
  public String getUnavailableReason() {
    return "";
  }

  @Override
  public boolean showWhenUnavailable() {
    return false;
  }
}