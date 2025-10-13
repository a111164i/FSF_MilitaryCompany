package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import combat.util.aEP_ID;
import combat.util.aEP_Tool;
import data.scripts.campaign.intel.*;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static combat.util.aEP_DataTool.txt;

/**
 * 在rules.csv中，在script或者condition栏写“aEP_AdvanceWeaponMission show2 exp"即调用本类中的execute方法
 * param传入一个包含show2，epx这2个值的数列
 * 方法的返回值，用于在condition栏调用时决定是否触发
 * */
public class aEP_AdvanceWeaponMission extends BaseCommandPlugin
{
  InteractionDialogAPI dialog;
  String ruleId;
  java.util.Map<String, MemoryAPI> memoryMap;

  public static final String MISSILE_CARRIER_SPEC_ID = "aEP_des_shendu_mk2";

  /**
   * @param params commodityId, check threshold, moreOrLess.
   */

  @Override
  public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, java.util.Map<String, MemoryAPI> memoryMap) {
    this.dialog = dialog;
    this.ruleId = ruleId;
    this.memoryMap = memoryMap;

    String param = params.get(0).string;
    float num = 0f;

    PersonAPI person = dialog.getInteractionTarget().getActivePerson();
    if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null
            && Global.getSector().getPlayerFleet().getCargo() != null) {
      switch (param) {
        case "highlightPerson":
          String personId = params.get(1).string;
          String onOrOff = params.get(2).string;
          return highlightPerson(personId,onOrOff);
        case "shouldStart":
          return shouldStart();
        case "show1":
          return show1(6f, "rare_bp");
        case "start1": {
          if(person != null) person.getRelToPlayer().setRel(person.getRelToPlayer().getRel() + 0.05f);
          return start1();
        }
        case "check1":
          return check("aEP_AWM1Intel");
        case "complete1": {
          if(person != null)  person.getRelToPlayer().setRel(person.getRelToPlayer().getRel() + 0.1f);
          end("aEP_AWM1Intel");
          return true;
        }
        case "shouldStart2":
          return shouldStart2();
        case "show2":
          return show2();
        case "start2": {
          if(person != null) person.getRelToPlayer().setRel(person.getRelToPlayer().getRel() + 0.1f);
          return start2();
        }
        case "check2":
          return check("aEP_AWM2Intel");
        case "complete2": {
          if(person != null)  person.getRelToPlayer().setRel(person.getRelToPlayer().getRel() + 0.1f);
          end("aEP_AWM2Intel");
          return true;
        }
        case "shouldStart3":
          return shouldStart3();
        case "start3":
          return start3();
        case "check3":
          return check("aEP_AWM3Intel");
        case "complete3": {
          if(person != null)  person.getRelToPlayer().setRel(person.getRelToPlayer().getRel() + 0.1f);
          end("aEP_AWM3Intel");
          return true;
        }
        case "shouldGive3":
          return shouldGive3();
        case "giveShip3":
          return giveShip3();
        case "addContact3":
          return addContact3();
        case "shouldStart4":
          return shouldStart4();
        case "start4":
          return start4();
        case "checkPermission4":
          return checkPermission4();
      }
    }
    return false;
  }

  public boolean check(String className) {
    boolean check = false;
    for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
      if (intel instanceof aEP_BaseMission) {
        aEP_BaseMission mission = (aEP_BaseMission) intel;
        if (mission.getClass().getName().contains(className) && mission.shouldEnd > 0) {
          check = true;
        }
      }
    }
    return check;
  }

  void end(String className) {
    for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
      if (intel instanceof aEP_BaseMission) {
        aEP_BaseMission mission = (aEP_BaseMission) intel;
        if (mission.getClass().getName().contains(className)) {
          mission.readyToEnd = true;
        }
      }
    }

  }

  boolean highlightPerson(String personName, String onOrOff){
    personName = personName.replace("_"," ");
    personName = personName.toLowerCase();
    for (PersonAPI person : dialog.getInteractionTarget().getMarket().getPeopleCopy()) {
      if (person.getName().getFullName().toLowerCase().contains(personName)) {
        if(onOrOff.equals("on")){
          aEP_Tool.Util.addDebugLog("highlight person: "+personName);
          person.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT,true);
        }else {
          aEP_Tool.Util.addDebugLog("highlight off person: "+personName);
          person.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MISSION_IMPORTANT);
        }

        return true;
      }
    }
    return false;
  }

  boolean shouldStart() {
    if(Global.getSector().getMemoryWithoutUpdate().getBoolean("$aEP_isSkipAwmMission") == true){
      return false;
    }

    FactionAPI faction = Global.getSector().getFaction("aEP_FSF");
    boolean has = false;
    for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
      if (market.getPrimaryEntity().getId().equals("aEP_FSF_DefStation")
              && Global.getSector().getPlayerFaction().getRelationship("aEP_FSF") >= 0.20f) {
        for (PersonAPI person : market.getPeopleCopy()) {
          //科学家不在市场中，出去
          if (!person.getMemoryWithoutUpdate().contains("$isaEP_Researcher")) continue;
          //科学家不在通讯录中，出去
          if (market.getCommDirectory().addPerson(person) == null) continue;

          dialog.getInteractionTarget().setActivePerson(person);
          has = true;
          break;
        }
      }
      if(has) break;
    }
    return has;
  }

  boolean show1(float totalWeaponPoint, String tag) {

    List<String> requestWeaponList = new ArrayList<>();
    if (memoryMap.get(MemKeys.FACTION).contains("$AWM_1showed")) {
      requestWeaponList = (List) memoryMap.get(MemKeys.FACTION).get("$AWM_1showed");

    }
    else {
      requestWeaponList = aEP_AWM1Intel.genWeaponList();
      memoryMap.get(MemKeys.FACTION).set("$AWM_1showed", requestWeaponList);
    }

    Color h = Misc.getHighlightColor();
    Color g = Misc.getGrayColor();
    Color c = Global.getSector().getFaction("aEP_FSF").getBaseUIColor();

    dialog.getTextPanel().addPara(txt("AWM01_desc01"));
    for (String fullId : requestWeaponList) {
      String weaponId = fullId.split(aEP_AWM1Intel.SPLITTER)[0];
      String weaponName = Global.getSettings().getWeaponSpec(weaponId).getWeaponName();
      dialog.getTextPanel().addPara("    - {%s}", g, h, weaponName);
    }
    dialog.getTextPanel().addPara(txt("AWM01_desc02"));

    return true;
  }

  boolean start1() {
    //start train event
    List<String> requestWeaponList = new ArrayList<>();
    if (memoryMap.get(MemKeys.FACTION).contains("$AWM_1showed")) {
      requestWeaponList = (List) memoryMap.get(MemKeys.FACTION).get("$AWM_1showed");
      //不放进sector里面everyFrame是不会运行advance的
      //要同时放入sector和intel才起效
      Global.getSector().addScript(new aEP_AWM1Intel(requestWeaponList,dialog.getInteractionTarget().getActivePerson()));
      return true;
    }
    //不放进sector里面everyFrame是不会运行advance的
    Global.getSector().addScript(new aEP_AWM1Intel(aEP_AWM1Intel.genWeaponList(),dialog.getInteractionTarget().getActivePerson()));
    return true;
  }

  boolean shouldStart2() {
    FactionAPI faction = Global.getSector().getFaction("aEP_FSF");
    boolean has = false;
    for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
      if (market.getPrimaryEntity().getId().equals("aEP_FSF_DefStation")
              && Global.getSector().getPlayerFaction().getRelationship("aEP_FSF") >= 0.35f) {
        for (PersonAPI person : market.getPeopleCopy()) {
          //科学家不在市场中，出去
          if (!person.getMemoryWithoutUpdate().contains("$isaEP_Researcher")) continue;
          //科学家不在通讯录中，出去
          if (market.getCommDirectory().addPerson(person) == null) continue;

          dialog.getInteractionTarget().setActivePerson(person);
          has = true;
          break;
        }
      }
      if(has) break;
    }


    boolean levelIsEnough = Global.getSector().getPlayerPerson().getStats().getLevel() >= 6;

    boolean mission1Completed = false;
    if (Global.getSector().getFaction("aEP_FSF").getMemoryWithoutUpdate().get("$AWM_1Complete") != null) {
      if (Global.getSector().getFaction("aEP_FSF").getMemoryWithoutUpdate().get("$AWM_1Complete").equals("true")) {
        mission1Completed = true;
      }
    }


    return has && levelIsEnough && mission1Completed;
  }

  boolean show2() {
    dialog.getOptionPanel().setEnabled(memoryMap.get("local").getString("$option"), false);

    if (!dialog.getOptionPanel().hasOption("aEP_researcher_stage1_talk05")) {
      dialog.getOptionPanel().addOption(txt("aEP_AdvanceWeaponMission01"), "aEP_researcher_stage1_talk05");
    }
    return true;
  }

  boolean start2() {
    FactionAPI faction = Global.getSector().getFaction("aEP_FSF");
    Vector2f toSpawn = new Vector2f(0f, 0f);
    WeightedRandomPicker<StarSystemAPI> systemPicker = new WeightedRandomPicker<StarSystemAPI>();
    for (StarSystemAPI system : Global.getSector().getStarSystems()) {
      float mult = 0f;

      if (system.hasPulsar()) continue;
      if (system.hasTag(Tags.THEME_MISC_SKIP)) {
        mult = 1f;
      }
      else if (system.hasTag(Tags.THEME_MISC)) {
        mult = 3f;
      }
      else if (system.hasTag(Tags.THEME_REMNANT_NO_FLEETS)) {
        mult = 3f;
      }
      else if (system.hasTag(Tags.THEME_RUINS)) {
        mult = 5f;
      }
      else if (system.hasTag(Tags.THEME_REMNANT_DESTROYED)) {
        mult = 3f;
      }
      else if (system.hasTag(Tags.THEME_CORE_UNPOPULATED)) {
        mult = 1f;
      }

      for (MarketAPI market : Misc.getMarketsInLocation(system)) {
        if (market.isHidden()) continue;
        mult = 0f;
        break;
      }

      float distToPlayer = Misc.getDistanceToPlayerLY(system.getLocation());
      float noSpawnRange = Global.getSettings().getFloat("personBountyNoSpawnRangeAroundPlayerLY");
      if (distToPlayer < noSpawnRange) mult = 0f;

      if (mult <= 0) continue;

      float weight = system.getPlanets().size();
      for (PlanetAPI planet : system.getPlanets()) {
        if (planet.isStar()) continue;
        if (planet.getMarket() != null) {
          float h = planet.getMarket().getHazardValue();
          if (h <= 0f) weight += 5f;
          else if (h <= 0.25f) weight += 3f;
          else if (h <= 0.5f) weight += 1f;
        }
      }

      float dist = system.getLocation().length();
      float distMult = Math.max(0, 50000f - dist);

      systemPicker.add(system, weight * mult * distMult);
    }

    StarSystemAPI system = systemPicker.pick();

    if (system != null) {
      toSpawn = new Vector2f(2000f, 2000f);
      SectorEntityToken token = system.createToken(toSpawn);
      for (int i = 1; i <= 12; i++) {
        AsteroidAPI asteroid = token.getContainingLocation().addAsteroid(MathUtils.getRandomNumberInRange(2f, 12f));
        asteroid.setCircularOrbit(token, MathUtils.getRandomNumberInRange(0, 360), MathUtils.getRandomNumberInRange(0, 500), 10000000f);
      }


      //add debris field
      DebrisFieldTerrainPlugin.DebrisFieldParams params = new DebrisFieldTerrainPlugin.DebrisFieldParams(
        250f, // field radius - should not go above 1000 for performance reasons
        1f, // density, visual - affects number of debris pieces
        10000000f, // duration in days
        0f); // days the field will keep generating glowing pieces

      params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.GEN;
      SectorEntityToken debris = Misc.addDebrisField(token.getContainingLocation(), params, new Random());
      debris.setCircularOrbit(token, 0, 0, 10000000f);

      //Global.getSector().getIntelManager().addIntel(new aEP_AWM2Intel(token));
      //Global.getSector().getIntelManager().queueIntel(new aEP_AWM2Intel(token));
      //不放进sector里面everyFrame是不会运行advance的
      Global.getSector().addScript(new aEP_AWM2Intel(token, "aEP_typeB28_variant", "TYPE_B_028"));

    }

    return false;
  }

  boolean shouldStart3() {
    FactionAPI faction = Global.getSector().getFaction("aEP_FSF");
    boolean has = false;
    for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
      if (market.getPrimaryEntity().getId().equals("aEP_FSF_DefStation")
              && Global.getSector().getPlayerFaction().getRelationship("aEP_FSF") >= 0.5f) {
        for (PersonAPI person : market.getPeopleCopy()) {
          //科学家不在市场中，出去
          if (!person.getMemoryWithoutUpdate().contains("$isaEP_Researcher")) continue;
          //科学家不在通讯录中，出去
          if (market.getCommDirectory().addPerson(person) == null) continue;

          dialog.getInteractionTarget().setActivePerson(person);
          has = true;
          break;
        }
      }
      if(has) break;
    }

    boolean levelIsEnough = Global.getSector().getPlayerPerson().getStats().getLevel() >= 8;

    boolean mission2Completed = false;
    if (Global.getSector().getFaction("aEP_FSF").getMemoryWithoutUpdate().get("$AWM_2Complete") != null) {
      if (Global.getSector().getFaction("aEP_FSF").getMemoryWithoutUpdate().get("$AWM_2Complete").equals("true")) {
        mission2Completed = true;
      }
    }


    return has && levelIsEnough && mission2Completed;
  }

  boolean start3() {
    FactionAPI faction = Global.getSector().getFaction("aEP_FSF");
    Vector2f toSpawn = new Vector2f(0f, 0f);
    WeightedRandomPicker<StarSystemAPI> systemPicker = new WeightedRandomPicker<StarSystemAPI>();
    for (StarSystemAPI system : Global.getSector().getStarSystems()) {
      float mult = 0f;

      if (system.hasPulsar()) continue;
      if (system.hasTag(Tags.THEME_MISC_SKIP)) {
        mult = 1f;
      }
      else if (system.hasTag(Tags.THEME_MISC)) {
        mult = 3f;
      }
      else if (system.hasTag(Tags.THEME_REMNANT_NO_FLEETS)) {
        mult = 3f;
      }
      else if (system.hasTag(Tags.THEME_RUINS)) {
        mult = 5f;
      }
      else if (system.hasTag(Tags.THEME_REMNANT_DESTROYED)) {
        mult = 3f;
      }
      else if (system.hasTag(Tags.THEME_CORE_UNPOPULATED)) {
        mult = 1f;
      }

      for (MarketAPI market : Misc.getMarketsInLocation(system)) {
        if (market.isHidden()) continue;
        mult = 0f;
        break;
      }

      float distToPlayer = Misc.getDistanceToPlayerLY(system.getLocation());
      float noSpawnRange = Global.getSettings().getFloat("personBountyNoSpawnRangeAroundPlayerLY");
      if (distToPlayer < noSpawnRange) mult = 0f;

      if (mult <= 0) continue;

      float weight = system.getPlanets().size();
      for (PlanetAPI planet : system.getPlanets()) {
        if (planet.isStar()) continue;
        if (planet.getMarket() != null) {
          float h = planet.getMarket().getHazardValue();
          if (h <= 0f) weight += 5f;
          else if (h <= 0.25f) weight += 3f;
          else if (h <= 0.5f) weight += 1f;
        }
      }

      float dist = system.getLocation().length();
      float distMult = Math.max(0, 50000f - dist);

      systemPicker.add(system, weight * mult * distMult);
    }

    StarSystemAPI system = systemPicker.pick();
    WeightedRandomPicker<PlanetAPI> planetPicker = new WeightedRandomPicker<PlanetAPI>();

    for (PlanetAPI planet : system.getPlanets()) {
      if (!planet.isStar()) {
        planetPicker.add(planet, 1f);
      }
    }

    while (planetPicker.isEmpty()) ;
    {
      system = systemPicker.pick();
      for (PlanetAPI planet : system.getPlanets()) {
        if (!planet.isStar()) {
          planetPicker.add(planet, 1f);
        }
      }
    }


    if (system != null) {
      SectorEntityToken token = planetPicker.pick();
      //Global.getSector().getIntelManager().addIntel(new aEP_AWM2Intel(token));
      //Global.getSector().getIntelManager().queueIntel(new aEP_AWM2Intel(token));
      Global.getSector().addScript(new aEP_AWM3Intel(token, "FSF_pirate"));

    }

    return false;
  }

  boolean shouldGive3() {
    CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
    for (FleetMemberAPI member : fleet.getFleetData().getMembersListWithFightersCopy()) {
      if (member.getHullSpec().getHullId().contains(MISSILE_CARRIER_SPEC_ID)) {
        return true;
      }
    }
    return false;
  }

  boolean giveShip3() {
    FleetMemberAPI toReplace = null;
    CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

    //找到特殊的任务舰，替换成一艘瀑布级
    for (FleetMemberAPI member : fleet.getFleetData().getMembersListWithFightersCopy()) {
      if (member.getHullSpec().getHullId().contains(MISSILE_CARRIER_SPEC_ID)) {
        toReplace = member;
        break;
      }
    }
    if (toReplace != null) {
      if (toReplace.getCaptain() != null) {
        fleet.getFleetData().removeOfficer(toReplace.getCaptain());
      }
      fleet.getFleetData().removeFleetMember(toReplace);
      fleet.getFleetData().addFleetMember("aEP_cru_pubu_Elite");
    }
    fleet.forceSync();

    dialog.getTextPanel().addPara(txt("aEP_AdvanceWeaponMission02"), Color.white, Color.red, toReplace.getHullSpec().getNameWithDesignationWithDashClass());
    dialog.getTextPanel().addPara(txt("aEP_AdvanceWeaponMission03"), Color.white, Color.green, Global.getSettings().getHullSpec("aEP_PuBu").getNameWithDesignationWithDashClass());
    return true;
  }

  boolean addContact3(){
    MarketAPI market = dialog.getInteractionTarget().getMarket();
    PersonAPI person = dialog.getInteractionTarget().getActivePerson();
    Random random = new Random( Misc.random.nextLong() + market.getId().hashCode());
    person.setImportanceAndVoice(PersonImportance.VERY_HIGH, random );
    person.addTag(Tags.CONTACT_MILITARY);
    ContactIntel intel = new ContactIntel(person, market);
    Global.getSector().getIntelManager().addIntel(intel, false, dialog.getTextPanel());
    return true;
  }

  boolean shouldStart4() {
    FactionAPI faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF);
    boolean has = false;
    for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
      if (market.getPrimaryEntity().getId().equals("aEP_FSF_DefStation")
              && Global.getSector().getPlayerFaction().getRelationship("aEP_FSF") >= 0.7f) {
        for (PersonAPI person : market.getPeopleCopy()) {
          //科学家不在市场中，出去
          if (!person.getMemoryWithoutUpdate().contains("$isaEP_Researcher")) continue;
          //科学家不在通讯录中，出去
          if (market.getCommDirectory().addPerson(person) == null) continue;

          dialog.getInteractionTarget().setActivePerson(person);
          has = true;
          break;
        }
      }
      if(has) break;
    }

    boolean levelIsEnough = Global.getSector().getPlayerPerson().getStats().getLevel() >= 10;
    return has && levelIsEnough;
  }

  boolean start4() {
    FactionAPI faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF);
    Global.getSector().addScript(new aEP_AWM4Intel());

    return false;
  }

  boolean checkPermission4() {
    FactionAPI faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF);
    dialog.getOptionPanel().clearOptions();
    if(faction.getMemoryWithoutUpdate().contains("$AWM_4Complete" ) ){
      dialog.getOptionPanel().addOption(txt("AWM04_have_permission"), "aEP_researcher_stage3_guardian_met_have");
    }
    dialog.getOptionPanel().addOption(txt("AWM04_nothave_permission"),"aEP_researcher_stage3_guardian_met_nothave");

    if(Global.getSettings().isDevMode()){
      dialog.getOptionPanel().addOption("Dev mode force pass", "aEP_researcher_stage3_guardian_met_have");
    }

    return false;
  }
}

