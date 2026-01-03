package data.scripts.campaign.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.utils.aEP_ID;
import data.scripts.campaign.entity.aEP_CruiseMissileEntityPlugin;
import data.scripts.hullmods.aEP_CruiseMissileCarrier;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.fs.starfarer.api.impl.campaign.rulecmd.aEP_AdvanceWeaponMission.MISSILE_CARRIER_SPEC_ID;
import static data.scripts.utils.aEP_DataTool.txt;

public class aEP_AWM3Intel extends aEP_BaseMission
{
  CampaignFleetAPI targetFleet;
  SectorEntityToken token;
  String shipName;
  int didGenShip = 0;
  int didShipRecovered = 0;
  int didHighlightLili = 0;


  public aEP_AWM3Intel(SectorEntityToken whereToSpawn, String targetShipId) {
    super(0f);
    shipName = targetShipId;
    this.token = whereToSpawn;

    // Create FSF fleet paramV3
    float qualityMod = Misc.getShipQuality(null, aEP_ID.FACTION_ID_FSF); // 0-1，动态统计质量最高的市场太麻烦了，写死
    FleetParamsV3 params = new FleetParamsV3(
            null,
            aEP_ID.FACTION_ID_FSF,
            1f,// qualityMod
            FleetTypes.TASK_FORCE,
            0f, // combatPts
            0f, // freighterPts
            0f, // tankerPts
            0f, // transportPts
            0f, // linerPts
            0f,  // utilityPts
            0f); // quality mod
    params.maxShipSize = 5; //0战机，1护卫，2驱逐，3巡洋，4主力
    params.treatCombatFreighterSettingAsFraction = true;
    params.averageSMods = 1;
    params.ignoreMarketFleetSizeMult = true;

    // If user has preset variants in addShips, they are already in the list; add any built-in presets here
    // addShips is the single source of presets - add entries to it to include them in the fleet
    params.addShips = new ArrayList<String>();
    // 使用flagshipVariantId时，在生成完舰队后，如果旗舰的variant不是这个，就把旗舰转变为这艘船
    params.flagshipVariantId = MISSILE_CARRIER_SPEC_ID+"_Standard";
    // 旗舰默认最大的，所以这里4艘暖池的一艘被转变为了导弹船
    params.addShips.add("aEP_cap_nuanchi_Elite");
    params.addShips.add("aEP_cap_nuanchi_Elite");
    params.addShips.add("aEP_cap_nuanchi_Elite");
    params.addShips.add("aEP_cap_nuanchi_Elite");

    params.addShips.add("aEP_cru_requan_Assault");
    params.addShips.add("aEP_cru_requan_Assault");

    params.addShips.add("aEP_cru_shanhu_Standard");
    params.addShips.add("aEP_cru_shanhu_Standard");

    // 剩下的都留给自动生成
    params.withOfficers = true;

    //手捏一个指挥官，因为旗舰是个特殊的小船，不需要战斗技能，多给几个指挥官技能
    PersonAPI person = Global.getFactory().createPerson();
    person.setPortraitSprite("graphics/portraits/portrait_pirate02.png");
    person.setName(new FullName("phrex","jin", FullName.Gender.MALE));
    person.setGender(person.getName().getGender());
    person.setFaction(Factions.PIRATES);
    person.setRankId(Ranks.SPACE_CAPTAIN);
    person.setVoice(Voices.VILLAIN);
    person.setPersonality(Personalities.STEADY);
    person.getStats().setSkillLevel(Skills.ELECTRONIC_WARFARE,1);
    person.getStats().setSkillLevel(Skills.TACTICAL_DRILLS,1);
    person.getStats().setSkillLevel(Skills.CREW_TRAINING,1);
    person.getStats().setSkillLevel(Skills.OFFICER_MANAGEMENT,1);
    person.getStats().setLevel(4);
    //不为空，不使用自动生成的指挥官
    params.commander = person;

    //如果自动生成指挥官，那么4级给1个舰队技能，6级给2个，看setting里面的commanderLevelForOneSkill。能刷到什么看faction里面的设置
    // params.noCommanderSkills = false;
    CampaignFleetAPI targetFleet = FleetFactoryV3.createFleet(params);
    //刷出来以后立刻inflate一下，随机一下装配和s插，这个质量高
    targetFleet.inflateIfNeeded();

    // Create pirate fleet paramV3
    FleetParamsV3 params2 = new FleetParamsV3(
            null,
            Factions.PIRATES,
            0.5f,// qualityOverride
            FleetTypes.TASK_FORCE,
            60f, // combatPts
            0f, // freighterPts
            0f, // tankerPts
            0f, // transportPts
            0f, // linerPts
            0f,  // utilityPts
            0f); // quality mod，大部分时间用不到
    //海盗生成一些随机小船，不要大船
    params2.maxShipSize = 2; //0战机，1护卫，2驱逐，3巡洋，4主力
    params2.treatCombatFreighterSettingAsFraction = true;
    params2.averageSMods = 0;
    params2.ignoreMarketFleetSizeMult = true;
    params2.withOfficers = true;
    CampaignFleetAPI pirateFleet = FleetFactoryV3.createFleet(params2);
    //刷出来以后立刻inflate一下，随机一下装配和d插，海盗的质量低
    pirateFleet.inflateIfNeeded();
    pirateFleet.getFleetData().sort();
    pirateFleet.getFleetData().setSyncNeeded();
    pirateFleet.getFleetData().syncIfNeeded();
    pirateFleet.forceSync();

    //把随机出来的海盗舰队合并进目标舰队
    for(FleetMemberAPI member : pirateFleet.getMembersWithFightersCopy()){
      //不要把战机联队加进去，用addFleetMemeber()的时候会自己处理的
      if(member.isFighterWing()) continue;
      targetFleet.getFleetData().addFleetMember(member);
    }

    //找到targetFleet中第一艘舰船id是MISSILE_CARRIER_SPEC_ID的船，它就是本次任务中的导弹航母
    FleetMemberAPI missileCarrier = null;
    for (FleetMemberAPI member : targetFleet.getMembersWithFightersCopy()) {
      if (member.getVariant().getHullSpec().getBaseHullId().equals(MISSILE_CARRIER_SPEC_ID)) {
        missileCarrier = member;
        break;
      }
    }
    //设定特殊的船名，后续识别该舰队是否消灭也是用的这个船名
    missileCarrier.setShipName(shipName);

    //完成舰队构成后，主舰队再sync
    //为了军官roll出来的技能符合FSF势力的设定，targetFleet初始的FSF阵营，现在把归属改回海盗
    targetFleet.setFaction(Factions.PIRATES,true);
    targetFleet.getFleetData().sort();
    targetFleet.getFleetData().setSyncNeeded();
    targetFleet.getFleetData().syncIfNeeded();
    targetFleet.forceSync();

    //给导弹航母上导弹
    aEP_CruiseMissileCarrier.LoadingMissile loading = new aEP_CruiseMissileCarrier.LoadingMissile();
    loading.setFleetMember(missileCarrier.getId());
    loading.setLoadedNum(1);
    Global.getSector().addScript(loading);

    targetFleet.setFaction("pirates");
    targetFleet.setName(txt("AWM03_mission03"));


    targetFleet.getCargo().addSpecial(new SpecialItemData(aEP_CruiseMissileCarrier.SPECIAL_ITEM_ID,null),30f);
    SalvageEntityGenDataSpec.DropData drop = new SalvageEntityGenDataSpec.DropData();
    drop.addSpecialItem(aEP_CruiseMissileCarrier.SPECIAL_ITEM_ID, 5);
    targetFleet.addDropValue(drop);

    //加入舰队必须调用这个
    token.getContainingLocation().spawnFleet(token, 0f, 0f, targetFleet);
    targetFleet.getAI().addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, token, Float.MAX_VALUE, null);
    //给ai舰队上对玩家发射导弹的脚本
    targetFleet.addScript(new EntityWantToMissileAttackPlayer(targetFleet));
    targetFleet.getMemoryWithoutUpdate().set("$isFSFPirate", true);
    targetFleet.getMemoryWithoutUpdate().set("$core_fightToTheLast", true);
    targetFleet.getMemoryWithoutUpdate().set("$cfai_holdVsStronger", true);
    targetFleet.getMemoryWithoutUpdate().set("$cfai_makeAllowDisengage", false);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true);
    this.targetFleet = targetFleet;
    setMapLocation(targetFleet);


    Global.getSector().getIntelManager().addIntel(this);
  }

  @Override
  public void advanceImpl(float amount) {

    //检测目标舰队还在不在
    boolean isGone = true;
    for (FleetMemberAPI member : targetFleet.getFleetData().getMembersListCopy()) {
      if ((member.getShipName() != null ? member.getShipName() : "").equals(shipName)) {
        isGone = false;
      }
    }
    if (!targetFleet.isAlive()) isGone = true;

    //在目标舰队消失后，生成一次残骸，并把目标点放在残骸上
    if (isGone && didGenShip == 0) {

      //创造一个的残骸实体，并绑上打捞参数
      DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData(MISSILE_CARRIER_SPEC_ID+"_Standard", ShipRecoverySpecial.ShipCondition.WRECKED, 0f), false);
      params.ship.shipName = shipName;
      params.ship.nameAlwaysKnown = true;
      params.durationDays = 999999999f;
      params.ship.addDmods = true;
      SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(targetFleet.getContainingLocation(),
              Entities.WRECK, Factions.NEUTRAL, params);
      //设置实体位置
      Vector2f toLocation = targetFleet.getLocation();
      ship.setLocation(toLocation.x, toLocation.y);
      ship.setDiscoverable(true);

      //创造一份特殊舰船打捞数据
      ShipRecoverySpecial.ShipRecoverySpecialData params2 = new ShipRecoverySpecial.ShipRecoverySpecialData("Prototype");
      ShipRecoverySpecial.PerShipData data = new ShipRecoverySpecial.PerShipData(MISSILE_CARRIER_SPEC_ID+"_Standard", ShipRecoverySpecial.ShipCondition.AVERAGE);
      data.addDmods = true;
      data.shipName = shipName;
      data.condition = ShipRecoverySpecial.ShipCondition.AVERAGE;
      data.nameAlwaysKnown = true;
      params2.addShip(data);
      params2.storyPointRecovery = true;
      //把特殊打捞数据绑给实体
      Misc.setSalvageSpecial(ship, params2);

      //上高亮
      ship.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT,true);
      ship.getMemoryWithoutUpdate().set(MemFlags.STORY_CRITICAL,true);
      //把新的任务点放在残骸上
      setPostingLocation(ship);
      setMapLocation(ship);
      //进入阶段1
      didGenShip = 1;
    }

    //检测残骸是否消失，被打捞或者被放弃
    if(didGenShip == 1 && didShipRecovered == 0){
      if(!mapLocation.isAlive()){
        didShipRecovered = 1;
        shouldEnd = 1;
      }
    }

    //生成残骸后，如果残骸被打捞，把新的目标点放在基地里
    //高亮FSF人物
    if(shouldEnd > 0 && didHighlightLili == 0){
      SectorEntityToken fsfBase = Global.getSector().getEconomy().getMarket("aEP_FSF_DefStation").getPrimaryEntity();
      setPostingLocation(fsfBase);
      setMapLocation(fsfBase);
      for(PersonAPI person : fsfBase.getMarket().getPeopleCopy()){
        if(person.getName().getFullName().toLowerCase().contains("lili yang")){
          person.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT,true);
        }
      }
      didHighlightLili = 1;
    }

  }

  @Override
  public void readyToEnd() {
    targetFleet.despawn();
    SectorEntityToken fsfBase = Global.getSector().getEconomy().getMarket("aEP_FSF_DefStation").getPrimaryEntity();
    setPostingLocation(fsfBase);
    setMapLocation(fsfBase);
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
    Color h = Misc.getHighlightColor();
    Color g = Misc.getGrayColor();
    Color c = faction.getBaseUIColor();

    info.setParaFontDefault();
    info.addPara(txt("AWM03_title"), c, 3f);
    info.setBulletedListMode(BULLET);
    info.addPara(txt("AWM03_mission01"), 10f, g, h, token.getContainingLocation().getName(), ((PlanetAPI) token).getTypeNameWithWorld());

  }

  //this control big info part on right
  @Override
  public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
    Color hightLight = Misc.getHighlightColor();
    Color grayColor = Misc.getGrayColor();
    Color whiteColor = Misc.getTextColor();
    Color barColor = faction.getDarkUIColor();
    Color titleTextColor = faction.getColor();

    //显示目标舰队
    if(didGenShip==0){
      info.setParaFontDefault();
      info.addImages(250f, 90f, 3f, 10f, targetFleet.getCommander().getPortraitSprite(), targetFleet.getFaction().getCrest());
      info.addPara(txt("AWM03_mission02") + ": ", 10f, whiteColor, targetFleet.getFaction().getBaseUIColor(), targetFleet.getCommander().getNameString());
      info.addShipList(8, 1 + targetFleet.getMembersWithFightersCopy().size() / 8, 40f, barColor, targetFleet.getMembersWithFightersCopy(), 10f);
      info.addSectionHeading(txt("mission_require"), titleTextColor, barColor, Alignment.MID, 30f);
      info.addPara(txt("mission_destroy"), hightLight, 3f);
    }
    //目标舰队检测被摧毁，特殊船刚刚生成
    if(didGenShip==1) {
      info.setParaFontDefault();
      info.addImages(250f, 90f, 3f, 10f, targetFleet.getCommander().getPortraitSprite(), targetFleet.getFaction().getCrest());
      info.addPara(txt("mission_destroyed"), hightLight, 3f);
      info.addSectionHeading(txt("mission_require"), titleTextColor, barColor, Alignment.MID, 30f);
      if(didShipRecovered == 1){
        info.addPara(txt("mission_complete"), hightLight, 3f);
      }else {
        info.addPara(txt("AWM03_mission04"), hightLight, 3f);
      }

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


  class EntityWantToMissileAttackPlayer implements EveryFrameScript {
    CampaignFleetAPI token;
    //几天装填一发
    float reloadTime = 1.5f;
    //玩家持续在视野中出现几秒就会发射
    float timeNeedToAim = 0.2f;
    //by day


    float timeAfterLastLaunch = 0f;
    float timeAiming = 0f;

    EntityWantToMissileAttackPlayer(CampaignFleetAPI fleet) {

      reloadTime = 1.5f;
      //玩家持续在视野中出现几秒就会发射
      timeNeedToAim = 0.2f;
      token = fleet;
    }

    /**
     * @return true when the script is finished and can be cleaned up by the engine.
     */
    @Override
    public boolean isDone() {
      for (FleetMemberAPI member : token.getMembersWithFightersCopy()) {
        if (member.getVariant().getHullSpec().getHullId().contains(MISSILE_CARRIER_SPEC_ID)) {
          return false;
        }
      }
      return true;
    }

    /**
     * @return whether advance() should be called while the campaign engine is paused.
     */
    @Override
    public boolean runWhilePaused() {
      return false;
    }

    /**
     * @param amount in seconds. Use SectorAPI.getClock() to figure out how many campaign days that is.
     */
    @Override
    public void advance(float amount) {
      CampaignFleetAPI fleet = token;
      float amountToDay = Misc.getDays(amount);
      timeAfterLastLaunch += amountToDay;
      if (Global.getSector().getPlayerFleet().isVisibleToSensorsOf(fleet)
              && MathUtils.getDistance(fleet.getLocation(), Global.getSector().getPlayerFleet().getLocation()) < 1200
              && timeAfterLastLaunch > reloadTime){
        timeAiming += amountToDay;
        if(timeAiming > timeNeedToAim){
          launchToPlayer(fleet);
          timeAfterLastLaunch = 0f;
          timeAiming = 0f;
        }
      }
    }

    void launchToPlayer(CampaignFleetAPI fleet) {

      String variatantId = "aEP_CruiseMissile";
      CustomCampaignEntityAPI missile = fleet.getContainingLocation().addCustomEntity(
              null,
              txt("MissileEntityName"),
              "aEP_CruiseMissile",
              fleet.getFaction().getId());

      missile.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
      missile.setFacing(VectorUtils.getAngle(fleet.getLocation(),Global.getSector().getPlayerFleet().getLocation()));
      missile.setContainingLocation(fleet.getContainingLocation());
      missile.setLocation(fleet.getLocation().x, fleet.getLocation().y);
      aEP_CruiseMissileEntityPlugin plugin = (aEP_CruiseMissileEntityPlugin)missile.getCustomPlugin();
      plugin.setVariantId(variatantId);
      plugin.setTargetFleet(Global.getSector().getPlayerFleet());
    }

  }
}
