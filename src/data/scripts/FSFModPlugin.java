package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import combat.util.aEP_Tool;
import data.missions.aEP_MissionUtils;
import data.scripts.ai.*;
import data.scripts.campaign.FSFCampaignPlugin;
import data.scripts.world.aEP_gen;
import exerelin.campaign.SectorManager;
import org.dark.shaders.light.LightData;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;


public class FSFModPlugin extends BaseModPlugin {

  public static boolean isLunalibEnabled = false;
  public static boolean isNexerelinEnabled = false;
  boolean isCorvusMode = true;

  //shipAI plugin pick
  @Override
  public PluginPick<ShipAIPlugin> pickShipAI(FleetMemberAPI member, ShipAPI ship) {

    switch (ship.getHullSpec().getBaseHullId()){
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
      case "aEP_cru_maodian_ads":
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

    //以英文模式启动时，替换名称
    if(Global.getSettings().getBoolean("aEP_UseEnString")) {
      //updateLanguage();
    }


    boolean hasGraphicsLib = Global.getSettings ().getModManager ().isModEnabled ( "shaderLib" );
    if ( hasGraphicsLib ) {
      ShaderLib.init();
      TextureData.readTextureDataCSV("data/config/lights/FSF_texture_data_normal_ships.csv");
      TextureData.readTextureDataCSV("data/config/lights/FSF_texture_data_material_ships.csv");
      TextureData.readTextureDataCSV("data/config/lights/FSF_texture_data_surface_ships.csv");

      TextureData.readTextureDataCSV("data/config/lights/FSF_texture_data_normal_weapons.csv");
      TextureData.readTextureDataCSV("data/config/lights/FSF_texture_data_material_weapons.csv");
      TextureData.readTextureDataCSV("data/config/lights/FSF_texture_data_surface_weapons.csv");

      loadLightData();
    }
  }

  @Override
  public void onGameLoad(boolean newGame) {
    aEP_MissionUtils.restore();
    isNexerelinEnabled = Global.getSettings().getModManager().isModEnabled("nexerelin");
    isLunalibEnabled = Global.getSettings().getModManager().isModEnabled("lunalib");

  }

  //create a sector
  @Override
  public void onNewGame() {
    isNexerelinEnabled = Global.getSettings().getModManager().isModEnabled("nexerelin");
    isLunalibEnabled = Global.getSettings().getModManager().isModEnabled("lunalib");

    //random sector
    //corvusMode关闭代表启用随机星域
    if (isNexerelinEnabled && !SectorManager.getManager().isCorvusMode()) {
      isCorvusMode = false;
      randomSpawn();
      return;
    }

    //defualt spawn
    defaultSpawn();

  }

  public void defaultSpawn(){
    new aEP_gen().generate(Global.getSector());
    SharedData.getData().getPersonBountyEventData().addParticipatingFaction("aEP_FSF");
    SectorAPI sector = Global.getSector();
    if (!sector.hasScript(FSFCampaignPlugin.class)) {
      sector.addScript(new FSFCampaignPlugin());
    }

  }

  public void randomSpawn(){
    new aEP_gen().randomGenerate(Global.getSector());
    SharedData.getData().getPersonBountyEventData().addParticipatingFaction("aEP_FSF");
    SectorAPI sector = Global.getSector();
    if (!sector.hasScript(FSFCampaignPlugin.class)) {
      sector.addScript(new FSFCampaignPlugin());
    }

  }
  public static void loadLightData(){
    LightData.readLightDataCSV("data/config/lights/FSF_light_data.csv");

  }
}
