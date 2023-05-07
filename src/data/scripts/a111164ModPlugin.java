package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import combat.util.aEP_Tool;
import data.missions.aEP_MissionUtils;
import data.scripts.ai.*;
import data.scripts.campaign.a111164CampaignPlugin;
import data.scripts.world.aEP_gen;


public class a111164ModPlugin extends BaseModPlugin
{


  //shipAI plugin pick
  @Override
  public PluginPick<ShipAIPlugin> pickShipAI(FleetMemberAPI member, ShipAPI ship) {

    switch (ship.getHullSpec().getHullId()){
      case "aEP_ftr_ut_repair":
        return new PluginPick<ShipAIPlugin>(new aEP_DroneRepairShipAI(member, ship), CampaignPlugin.PickPriority.MOD_SPECIFIC);
      case "aEP_ftr_ut_supply":
        return new PluginPick<ShipAIPlugin>(new aEP_DroneSupplyShipAI(member, ship), CampaignPlugin.PickPriority.MOD_SPECIFIC);
      case "aEP_ftr_ut_decompose":
        return new PluginPick<ShipAIPlugin>(new aEP_DroneDecomposeAI(member, ship), CampaignPlugin.PickPriority.MOD_SPECIFIC);
      case "aEP_ftr_sup_shield":
        return new PluginPick<ShipAIPlugin>(new aEP_DroneShieldShipAI(member, ship), CampaignPlugin.PickPriority.MOD_SPECIFIC);
      case "aEP_ftr_ut_maodian":
        return new PluginPick<ShipAIPlugin>(new aEP_MaoDianDroneAI(ship), CampaignPlugin.PickPriority.MOD_SPECIFIC);
      case "aEP_CruiseMissile":
      case "aEP_CruiseMissile2":
        return new PluginPick<ShipAIPlugin>(new aEP_CruiseMissileAI(ship,null), CampaignPlugin.PickPriority.MOD_SPECIFIC);

    }
    return null;

  }

  @Override
  public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
    switch (missile.getProjectileSpecId()) {
      case "aEP_m_l_harpoon_shot":
      case "aEP_m_s_harpoon_shot":
        return new PluginPick<MissileAIPlugin>(new aEP_MissileAI(missile,launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);

    }
    return null;
  }

  //weaponAI plugin pick
  @Override
  public PluginPick<AutofireAIPlugin> pickWeaponAutofireAI(WeaponAPI weapon) {

    switch (weapon.getId()) {
      case "aEP_cap_biaobing_radar":
        //return new PluginPick<AutofireAIPlugin>(new aEP_BbRadarAI(weapon), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        return null;
      case "aEP_ftr_ut_decompose_beam":
        return new PluginPick<AutofireAIPlugin>(new aEP_TearingBeamAI(weapon), CampaignPlugin.PickPriority.MOD_SPECIFIC);
      case "aEP_ftr_ut_maodian_ads":
        return new PluginPick<AutofireAIPlugin>(new aEP_MaoDianDroneAutoFire(weapon), CampaignPlugin.PickPriority.MOD_SPECIFIC);
    }
    return null;

  }

  @Override
  public void onApplicationLoad() throws Exception {
    try{
      aEP_MissionUtils.loadDefaultWeapon(Global.getSettings().getAllWeaponSpecs());
      aEP_MissionUtils.loadDefaultWing(Global.getSettings().getAllFighterWingSpecs());
      aEP_MissionUtils.loadDefaultHullMod(Global.getSettings().getAllHullModSpecs());
    } catch (Exception e1){
      aEP_Tool.Util.addDebugLog(this.getClass().getSimpleName()+" onApplicationLoad, mission filter initiation error");
    }

  }

  @Override
  public void onGameLoad(boolean newGame) {
    aEP_MissionUtils.restore();
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
