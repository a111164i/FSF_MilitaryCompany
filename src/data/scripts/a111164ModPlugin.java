package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import data.scripts.ai.*;
import data.scripts.campaign.a111164CampaignPlugin;
import data.scripts.world.aEP_gen;


public class a111164ModPlugin extends BaseModPlugin
{
  public static final String RepairDrone_ID = "aEP_ftr_ut_repair";
  public static final String DecomposeDrone_ID = "aEP_ftr_ut_decompose";
  public static final String DefenseDrone_ID = "aEP_ftr_sup_shield";
  public static final String BB_Radar_ID = "aEP_BB_radar";
  public static final String TearingBeamFighter_ID = "aEP_ftr_ut_decompose_beam";
  public static final String MaoDianDrone_ID = "aEP_ftr_ut_maodian";
  public static final String CruiseMissile_ID = "aEP_CruiseMissile";
  public static final String CruiseMissile2_ID = "aEP_CruiseMissile2";


  //shipAI plugin pick
  @Override
  public PluginPick<ShipAIPlugin> pickShipAI(FleetMemberAPI member, ShipAPI ship) {
    if (ship.getHullSpec().getHullId().equals(RepairDrone_ID)) {
      return new PluginPick<ShipAIPlugin>(new aEP_RepairingDroneAI(member, ship), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
    else if (ship.getHullSpec().getHullId().equals(DefenseDrone_ID)) {
      return new PluginPick<ShipAIPlugin>(new aEP_DefenseDroneAI(member, ship), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
    else if (ship.getHullSpec().getHullId().equals(DecomposeDrone_ID)) {
      return new PluginPick<ShipAIPlugin>(new aEP_DecomposeDroneAI(member, ship), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
    else if (ship.getHullSpec().getHullId().equals(CruiseMissile_ID)) {
      member.setCaptain(null);
      return new PluginPick<ShipAIPlugin>(new aEP_CruiseMissileAI(ship,null), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    } else if (ship.getHullSpec().getHullId().equals(CruiseMissile2_ID)) {
      member.setCaptain(null);
      return new PluginPick<ShipAIPlugin>(new aEP_CruiseMissileAI(ship,null), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
    else if (ship.getHullSpec().getHullId().equals(MaoDianDrone_ID)) {
      member.setCaptain(null);
      return new PluginPick<ShipAIPlugin>(new aEP_MaoDianDroneAI(ship), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
    return null;
  }

  @Override
  public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
    switch (missile.getProjectileSpecId()) {
      case "aEP_harpoon_missile":
        return new PluginPick<MissileAIPlugin>(new aEP_MissileAI(missile,launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
    return null;
  }

  //weaponAI plugin pick
  @Override
  public PluginPick<AutofireAIPlugin> pickWeaponAutofireAI(WeaponAPI weapon) {

    if (weapon.getId().equals(TearingBeamFighter_ID)) {
      return new PluginPick<AutofireAIPlugin>(new aEP_TearingBeamAI(weapon), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
    if (weapon.getId().equals(BB_Radar_ID)) {
      //return new PluginPick<AutofireAIPlugin>(new aEP_BbRadarAI(weapon), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }

    return null;
  }

  //create a sector
  @Override
  public void onNewGame() {

    new aEP_gen().generate(Global.getSector());
    SharedData.getData().getPersonBountyEventData().addParticipatingFaction("aEP_FSF");
    SectorAPI sector = Global.getSector();
    if (!sector.hasScript(a111164CampaignPlugin.class)) {
      sector.addScript(new a111164CampaignPlugin());
    }
  }


}
