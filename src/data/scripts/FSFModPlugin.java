package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.loading.specs.FighterWingSpec;
import data.scripts.utils.aEP_Tool;
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
      case "aEP_ftr_ut_shuishi":
        return new PluginPick<ShipAIPlugin>(new aEP_DroneShieldShipAI(member,ship), CampaignPlugin.PickPriority.MOD_SPECIFIC);
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

    //以中文模式启动时，自动翻译装配的名称，反正一共就standard attack defense等等几个
    if(!Global.getSettings().getBoolean("aEP_UseEnString")) {
      updateVariantNames();
      updateWingRoleName();
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

  public static void updateVariantNames(){
    //更新装配的名称
    for(String variantId : Global.getSettings().getAllVariantIds()){
      if(!variantId.startsWith("aEP_")) continue; //跳过非本mod的装配
      ShipVariantAPI variant = Global.getSettings().getVariant(variantId);
      String baseName = variant.getDisplayName().toLowerCase();
      //舰船名
      if(!variant.isFighter()) {
        if(baseName.contains("standard")){
          variant.setVariantDisplayName("标准");
        } else if(baseName.contains("attack")){
          variant.setVariantDisplayName("进攻");
        } else if(baseName.contains("defense")){
          variant.setVariantDisplayName("防御");
        } else if(baseName.contains("assault")){
          variant.setVariantDisplayName("突击");
        } else if(baseName.contains("range")){
          variant.setVariantDisplayName("远距离");
        } else if(baseName.contains("super")){
          variant.setVariantDisplayName("超装");
        } else if(baseName.contains("elite")){
          variant.setVariantDisplayName("精英");
        } else if(baseName.contains("support")){
          variant.setVariantDisplayName("支援");
        } else if(baseName.contains("repair")){
          variant.setVariantDisplayName("维修");
        } else if(baseName.contains("siege")){
          variant.setVariantDisplayName("攻城");
        } else if(baseName.contains("bomb")){
          variant.setVariantDisplayName("轰炸");
        } else if(baseName.contains("strike")){
          variant.setVariantDisplayName("重击");
        } else if(baseName.contains("beam")){
          variant.setVariantDisplayName("光束");
        } else if(baseName.contains("mixed")){
          variant.setVariantDisplayName("混合");
        } else if(baseName.contains("burst")){
          variant.setVariantDisplayName("爆发");
        } else if(baseName.contains("boardside")){
          variant.setVariantDisplayName("侧弦");
        } else if(baseName.contains("front")){
          variant.setVariantDisplayName("前线");
        }
      }
      //战机名
      else {
        String postfix = baseName.endsWith("drone") ? "无人机" : "机";
        String prefix = baseName.contains("light") ? "轻型" :
                        baseName.contains("heavy") ? "重型" : "";
        if(baseName.contains("support fighter")){
          variant.setVariantDisplayName(prefix+"支援战斗"+postfix);
        } else if(baseName.contains("assault fighter")){
          variant.setVariantDisplayName(prefix+"攻击"+postfix);
        } else if(baseName.contains("assault bomber")){
          variant.setVariantDisplayName(prefix+"攻击轰炸"+postfix);
        } else if(baseName.contains("interceptor")){
          variant.setVariantDisplayName(prefix+"拦截"+postfix);
        } else if(baseName.contains("bomber")){
          variant.setVariantDisplayName(prefix+"轰炸"+postfix);
        }else if(baseName.contains("support")){
          variant.setVariantDisplayName(prefix+"支援"+postfix);
        } else if(baseName.contains("utility")){
          variant.setVariantDisplayName(prefix+"功能"+postfix);
        } else if(baseName.contains("repair")){
          variant.setVariantDisplayName(prefix+"维修"+postfix);
        } else if(baseName.contains("fighter")){
          variant.setVariantDisplayName(prefix+"战斗"+postfix);
        } else if(baseName.contains("shield")){
          variant.setVariantDisplayName(prefix+"组盾"+postfix);
        }
      }


    }
  }

  public static void updateWingRoleName(){
    for(FighterWingSpecAPI wingSpec : Global.getSettings().getAllFighterWingSpecs()){
      if(!wingSpec.getId().startsWith("aEP_")) continue; //跳过非本mod的战机编队
      String baseName = wingSpec.getRoleDesc().toLowerCase();
      String postfix = baseName.endsWith("drone") ? "无人机" : "机";
      String prefix = baseName.contains("light") ? "轻型" :
              baseName.contains("heavy") ? "重型" : "";
      if(baseName.contains("fighter")){
        wingSpec.setRoleDesc(prefix+"战斗"+postfix);
      } else if(baseName.contains("interceptor")){
        wingSpec.setRoleDesc(prefix+"拦截"+postfix);
      } else if(baseName.contains("bomber")){
        wingSpec.setRoleDesc(prefix+"轰炸"+postfix);
      } else if(baseName.contains("support")){
        wingSpec.setRoleDesc(prefix+"支援"+postfix);
      } else if(baseName.contains("utility")) {
        wingSpec.setRoleDesc(prefix + "功能" + postfix);
      }
    }

  }

}
