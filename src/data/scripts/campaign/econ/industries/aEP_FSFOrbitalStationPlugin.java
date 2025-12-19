package data.scripts.campaign.econ.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;

import static combat.util.aEP_DataTool.txt;


public class aEP_FSFOrbitalStationPlugin extends OrbitalStation
{


  @Override
  public boolean isAvailableToBuild() {
    if(getUnavailableReason().equals("")) return true;
    return false;
  }

  @Override
  public String getUnavailableReason() {
    if(Global.getSector().getPlayerFaction().getRelationshipLevel("aEP_FSF").isAtBest(RepLevel.FRIENDLY)){
      return txt("aEP_FSFOrbitalStationPluginBuildReason");
    }

    return "";
  }

  @Override
  public boolean showWhenUnavailable() {
    return false;
  }
}