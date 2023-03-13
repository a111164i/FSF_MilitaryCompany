package data.scripts.campaign.econ.entity;

import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;


public class aEP_FSFOrbitalStationPlugin extends OrbitalStation
{

  @Override
  protected void matchStationAndCommanderToCurrentIndustry() {
    super.matchStationAndCommanderToCurrentIndustry();
  }

  @Override
  public boolean isAvailableToBuild() {
    return false;
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