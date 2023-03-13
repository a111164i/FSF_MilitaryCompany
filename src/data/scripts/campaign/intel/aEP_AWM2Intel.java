package data.scripts.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import combat.util.aEP_ID;
import combat.util.aEP_Tool;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

import static combat.util.aEP_DataTool.txt;

public class aEP_AWM2Intel extends aEP_BaseMission
{
  CampaignFleetAPI targetFleet;
  SectorEntityToken token;
  String shipName;

  public aEP_AWM2Intel(SectorEntityToken whereToSpawn, String variantId, String targetShipId) {
    this.sector = Global.getSector();
    this.faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF);
    this.shipName = targetShipId;
    ending = false;
    ended = false;
    this.token = whereToSpawn;
    setName(this.getClass().getSimpleName());
    setPostingLocation(token);


    //add Fleet
    CampaignFleetAPI targetFleet = Global.getFactory().createEmptyFleet("derelict", "奇怪的无人舰", true);
    targetFleet.getFleetData().addFleetMember(variantId);
    targetFleet.getFleetData().setFlagship(targetFleet.getFleetData().getMembersListWithFightersCopy().get(0));
    targetFleet.getFlagship().setId(targetShipId);
    targetFleet.getFleetData().setOnlySyncMemberLists(false);
    targetFleet.getFleetData().sort();
    targetFleet.setAI(null);
    targetFleet.setNullAIActionText("...");
    //add captain
    PersonAPI person = Global.getFactory().createPerson();
    person.setFaction("derelict");

    CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(Commodities.GAMMA_CORE);
    person.setAICoreId(Commodities.GAMMA_CORE);
    person.setName(new FullName(spec.getName(), "", FullName.Gender.ANY));
    person.setPortraitSprite("graphics/portraits/portrait_ai3b.png");
    person.getStats().setLevel(8);
    person.getStats().setSkillLevel(Skills.CONTAINMENT_PROCEDURES,1);
    person.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE,1);
    person.getStats().setSkillLevel(Skills.COORDINATED_MANEUVERS,1);
    person.getStats().setSkillLevel(Skills.POLARIZED_ARMOR,1);
    person.getStats().setSkillLevel(Skills.TARGET_ANALYSIS,1);
    person.getStats().setSkillLevel(Skills.BALLISTIC_MASTERY,2);
    person.getStats().setSkillLevel(Skills.MISSILE_SPECIALIZATION,2);
    person.getStats().setSkillLevel(Skills.HELMSMANSHIP,2);
    person.getStats().setSkillLevel(Skills.POINT_DEFENSE,2);
    person.getStats().setSkillLevel(Skills.IMPACT_MITIGATION,2);
    person.getStats().setSkillLevel(Skills.SYSTEMS_EXPERTISE,2);
    person.getStats().setSkillLevel(Skills.FIELD_MODULATION,1);


    person.setRankId(Ranks.SPACE_CAPTAIN);
    person.setPersonality(Personalities.RECKLESS);

    person.setFleet(targetFleet);
    targetFleet.getFlagship().setCaptain(person);
    targetFleet.setCommander(person);
    targetFleet.forceSync();

    token.getContainingLocation().spawnFleet(token, 0f, 0f, targetFleet);
    targetFleet.getMemoryWithoutUpdate().set("$isTypeB28", true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_FIGHT_TO_THE_LAST, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.STORY_CRITICAL, true);
    this.targetFleet = targetFleet;
    setMapLocation(targetFleet);

    setImportant(true);
    Global.getSector().getIntelManager().addIntel(this);
    Global.getSector().getIntelManager().queueIntel(this);
  }

  @Override
  public void advanceImpl(float amount) {
    //Global.getSector().getPlayerFleet().addFloatingText("1",Color.BLUE,0.75f);

    //如果目标舰队中目标舰船已经消失，或者目标舰队已经消失，就算完成
    boolean isGone = true;
    for (FleetMemberAPI member : targetFleet.getFleetData().getMembersListWithFightersCopy()) {
      if (member.getId().equals(shipName)) {
        isGone = false;
      }
    }
    if (!targetFleet.isAlive()) isGone = true;


    //击杀目标后把新的任务地点改为FSF基地
    //高亮FSF人物
    if((isGone && shouldEnd==0) || shouldEnd ==1){
      SectorEntityToken fsfBase = Global.getSector().getEconomy().getMarket("aEP_FSF_DefStation").getPrimaryEntity();
      setMapLocation(fsfBase);
      for(PersonAPI person : fsfBase.getMarket().getPeopleCopy()){
        if(person.getName().getFullName().toLowerCase().contains("lili yang")){
          person.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT,true);
        }
      }
      shouldEnd = 1;
    }

  }

  @Override
  public void readyToEnd() {
    targetFleet.despawn();
    SectorEntityToken fsfBase = Global.getSector().getEconomy().getMarket("aEP_FSF_DefStation").getPrimaryEntity();
    for(PersonAPI person : fsfBase.getMarket().getPeopleCopy()){
      if(person.getName().getFullName().toLowerCase().contains("lili yang")){
        person.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MISSION_IMPORTANT);
      }
    }
  }

  @Override
  public String getIcon() {
    return Global.getSettings().getSpriteName("aEP_icons", "AWM1");
  }

  //this part control brief bar on lower left
  @Override
  public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
    Color hightLight = Misc.getHighlightColor();
    Color grayColor = Misc.getGrayColor();
    Color whiteColor = Misc.getTextColor();
    Color barColor = faction.getDarkUIColor();
    Color titleTextColor = faction.getColor();
    Color c = faction.getBaseUIColor();

    info.setParaFontDefault();
    info.addPara(txt("AWM02_title"), c, 3f);
    info.setBulletedListMode(BULLET);
    info.addPara(txt("AWM02_mission01"), 10f, whiteColor, hightLight, token.getContainingLocation().getName());
  }


  //this control info part on right
  @Override
  public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
    Color hightLight = Misc.getHighlightColor();
    Color grayColor = Misc.getGrayColor();
    Color whiteColor = Misc.getTextColor();
    Color barColor = faction.getDarkUIColor();
    Color titleTextColor = faction.getColor();


    info.setParaFontDefault();
    info.addImages(250f, 90f, 3f, 10f, targetFleet.getCommander().getPortraitSprite(), targetFleet.getFaction().getCrest());
    info.addPara(txt("AWM02_mission01") + ": ", 10f, whiteColor, targetFleet.getFaction().getBaseUIColor(), targetFleet.getContainingLocation().getName());
    info.addShipList(8, 1 + targetFleet.getMembersWithFightersCopy().size() / 8, 40f, barColor, targetFleet.getMembersWithFightersCopy(), 10f);
    info.addSectionHeading(txt("mission_require"), titleTextColor, barColor, Alignment.MID, 30f);
    info.addPara(txt("mission_destroy"), hightLight, 3f);
    info.setBulletedListMode(BULLET);
    if (shouldEnd==1) {
      info.addPara(txt("mission_destroyed"), Color.green, 10f);
    }


    if (Global.getSettings().isDevMode()) {
      info.addPara("devMode force finish", Color.yellow, 10f);
      info.addButton("Finish Mission", "Finish Mission", 120, 20, 20f);
    }

  }

  @Override
  public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
    if (buttonId.equals("Finish Mission")) {
      shouldEnd = 1;
    }
  }

  //control tags
  @Override
  public Set<String> getIntelTags(SectorMapAPI map) {
    Set<String> tags = new LinkedHashSet();
    tags.add("aEP_FSF");
    tags.add("Missions");
    return tags;
  }

  @Override
  public String getSortString() {
    return "FSF";
  }
}
