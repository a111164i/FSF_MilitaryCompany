package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketSpecAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import combat.util.aEP_DataTool;
import combat.util.aEP_ID;
import combat.util.aEP_Tool;
import data.missions.aEP_MissionUtils;
import data.scripts.ai.*;
import data.scripts.campaign.FSFCampaignPlugin;
import data.scripts.world.aEP_gen;
import exerelin.campaign.SectorManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Writer;
import java.util.ArrayList;


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
      updateLanguage();
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

    //如果开了跳过任务，加入memKey



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

  private void updateLanguage() {
    try {

      // faction name
      FactionSpecAPI factionSpec = Global.getSettings().getFactionSpec(aEP_ID.FACTION_ID_FSF);
      factionSpec.setDisplayName(aEP_ID.FACTION_NAME_EN);
      factionSpec.setDisplayNameWithArticle(aEP_ID.FACTION_NAME_EN);
      factionSpec.setDisplayNameLong(aEP_ID.FACTION_NAME_EN);
      factionSpec.setDisplayNameLongWithArticle(aEP_ID.FACTION_NAME_EN);
      FactionSpecAPI factionSpec2 = Global.getSettings().getFactionSpec(aEP_ID.FACTION_ID_FSF_ADV);
      factionSpec2.setDisplayName(aEP_ID.FACTION_NAME_EN);
      factionSpec2.setDisplayNameWithArticle(aEP_ID.FACTION_NAME_EN);
      factionSpec2.setDisplayNameLong(aEP_ID.FACTION_NAME_EN);
      factionSpec2.setDisplayNameLongWithArticle(aEP_ID.FACTION_NAME_EN);

      // commodities
      JSONArray allCommoditiesString = Global.getSettings().loadCSV("data/campaign/commodities_EN.csv", "FSF_MilitaryCorporation");
      ArrayList<aEP_DataTool.RowData> commoditiesData = aEP_DataTool.jsonToList(allCommoditiesString);
      if (!commoditiesData.isEmpty()) {
        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
          String id = spec.getId();
          String engName = aEP_DataTool.getValueById(commoditiesData, id, "name");
          if (!engName.isEmpty()) {
            spec.setName(engName);
          }
        }
      }

      // colony conditions
      JSONArray allConditionString = Global.getSettings().loadCSV("data/campaign/market_conditions_EN.csv", "FSF_MilitaryCorporation");
      ArrayList<aEP_DataTool.RowData> allConditionsData = aEP_DataTool.jsonToList(allConditionString);
      if (!allConditionsData.isEmpty()) {
        for (MarketConditionSpecAPI spec : Global.getSettings().getAllMarketConditionSpecs()) {
          String id = spec.getId();
          String engName = aEP_DataTool.getValueById(allConditionsData, id, "name");
          String engDesc = aEP_DataTool.getValueById(allConditionsData, id, "desc");
          if (!engName.isEmpty()) {
            spec.setName(engName);
            spec.setDesc(engDesc);
          }
        }
      }

      // skills
      JSONArray allSkillsString = Global.getSettings().loadCSV("data/characters/skills/skill_data_EN.csv", "FSF_MilitaryCorporation");
      ArrayList<aEP_DataTool.RowData> allSkillsData = aEP_DataTool.jsonToList(allSkillsString);
      if (!allSkillsData.isEmpty()) {
        for (String id : Global.getSettings().getSkillIds()) {
          String engName = aEP_DataTool.getValueById(allSkillsData, id, "name");
          String engDesc = aEP_DataTool.getValueById(allSkillsData, id, "description");
          String engAuthor = aEP_DataTool.getValueById(allSkillsData, id, "author");
          if (!engName.isEmpty()) {
            SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
            spec.setName(engName);
            spec.setDescription(engDesc);
            spec.setAuthor(engAuthor);
          }
        }
      }

      // hullmods
      JSONArray allHullmodsString = Global.getSettings().loadCSV("data/hullmods/hull_mods_EN.csv", "FSF_MilitaryCorporation");
      ArrayList<aEP_DataTool.RowData> allHullmodsData = aEP_DataTool.jsonToList(allHullmodsString);
      if (!allHullmodsData.isEmpty()) {
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
          String id = spec.getId();
          String engName = aEP_DataTool.getValueById(allHullmodsData, id, "name");
          String engDesign = aEP_DataTool.getValueById(allHullmodsData, id, "tech/manufacturer");
          String engDesc= aEP_DataTool.getValueById(allHullmodsData, id, "desc");
          String engDescSmod= aEP_DataTool.getValueById(allHullmodsData, id, "sModDesc");

          if (!engName.isEmpty()) {
            spec.setDisplayName(engName);
            spec.setManufacturer(engDesign);
            spec.setDescriptionFormat(engDesc);
            spec.setSModEffectFormat(engDescSmod);
          }
        }
      }

      // ships
      JSONArray allShipsString = Global.getSettings().loadCSV("data/hulls/ship_data_EN.csv", "FSF_MilitaryCorporation");
      ArrayList<aEP_DataTool.RowData> allHullsData = aEP_DataTool.jsonToList(allShipsString);
      if (!allHullsData.isEmpty()) {
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
          String id = spec.getBaseHullId();
          String engName = aEP_DataTool.getValueById(allHullsData, id, "name");
          String engDesign = aEP_DataTool.getValueById(allHullsData, id, "tech/manufacturer");
          String engDestination = aEP_DataTool.getValueById(allHullsData, id, "designation");

          if (!engName.isEmpty()) {
            spec.setHullName(engName);
            spec.setManufacturer(engDesign);
            spec.setDesignation(engDestination);
          }
        }
      }

      // ship system names
      JSONArray allSystemsString = Global.getSettings().loadCSV("data/shipsystems/ship_systems_EN.csv", "FSF_MilitaryCorporation");
      ArrayList<aEP_DataTool.RowData> systemsData = aEP_DataTool.jsonToList(allSystemsString);
      if (!systemsData.isEmpty()) {
        for (ShipSystemSpecAPI spec : Global.getSettings().getAllShipSystemSpecs()) {
          String systemId = spec.getId();
          String engName = aEP_DataTool.getValueById(systemsData, systemId, "name");
          if (!engName.isEmpty()) {
            spec.setName(engName);
          }
        }
      }


      JSONArray allWeaponsString = Global.getSettings().loadCSV("data/weapons/weapon_data_EN.csv", "FSF_MilitaryCorporation");
      ArrayList<aEP_DataTool.RowData> allWeaponsData = aEP_DataTool.jsonToList(allWeaponsString);
      if (!allWeaponsData.isEmpty()) {
        for (aEP_DataTool.RowData row : allWeaponsData) {
          String id = row.getId();
          if(!id.isEmpty() && !id.startsWith("#")){
            WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(id);
            String engName = row.getProperty("name");
            String engDesign = row.getProperty("tech/manufacturer");
            String engUsage = row.getProperty("primaryRoleStr");
            String engEffectDesc =row.getProperty("customPrimary");
            spec.setWeaponName(engName);
            spec.setManufacturer(engDesign);
            spec.setPrimaryRoleStr(engUsage);
            spec.setCustomPrimary(engEffectDesc);
          }
        }

      }

    }catch (Exception e){
      Global.getLogger(this.getClass()).info("Fail to swap language to Eng");
    }
  }
}
