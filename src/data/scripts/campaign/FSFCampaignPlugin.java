package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import combat.util.aEP_ID;
import combat.util.aEP_Tool;
import data.scripts.FSFModPlugin;
import data.scripts.campaign.econ.environment.aEP_MilitaryZone;
import data.scripts.world.aEP_systems.aEP_FSF_DWR43;
import lunalib.lunaSettings.LunaSettings;


public class FSFCampaignPlugin implements EveryFrameScript {

  private float dayTimer = 0;

  public boolean isDone() {
    return false;
  }

  public boolean runWhilePaused() {
    return true;
  }

  public void advance(float amount) {
    if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) {
      return;
    }


    //run per 0.1 day
    dayTimer = dayTimer + Global.getSector().getClock().convertToDays(amount);
    float checkInterval = 1f;
    if (dayTimer < checkInterval) return;
    dayTimer = dayTimer - checkInterval;


    FactionAPI fsf = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF);
    FactionAPI fsfAdv = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF_ADV);

    //如果启用了跳过主线，加入memKey
    if(FSFModPlugin.isLunalibEnabled){
      boolean shouldSkip = LunaSettings.getBoolean("FSF_MilitaryCorporation","aEP_SettingMissionSkipAwm");
      if(shouldSkip){
        Global.getSector().getMemoryWithoutUpdate().set("$aEP_isSkipAwmMission", true);
      }
    }

    //check and create persons
    for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {

      String id = market.getPrimaryEntity().getId();
      if (id.equals("aEP_FSF_SaleStation")) {
        checkConsultant(market);
      }

      if (id.equals("aEP_FSF_DefStation")) {
        checkResearcher(market);
      }

      //2个特殊市场的特殊环境
      if (id.equals(aEP_FSF_DWR43.MINING_PLANET_MARKET_ID)) {
        checkCondition(market);
      }

      if (id.equals(aEP_FSF_DWR43.FACTORY_STATION_MARKET_ID)) {
        checkCondition(market);
      }



    }

    //检测magicBounty，给势力加入一艘新船
    if(Global.getSector().getMemoryWithoutUpdate().contains("$aEP_MagicBounty01")
            && (boolean)Global.getSector().getMemoryWithoutUpdate().get("$aEP_MagicBounty01")){

      String shipId = "aEP_fga_chichao";
      if(!fsf.getKnownShips().contains(shipId)) fsf.getKnownShips().add(shipId);
      if(!fsfAdv.getKnownShips().contains(shipId)) fsfAdv.getKnownShips().add(shipId);
    }

  }

  void checkConsultant(MarketAPI market) {
    if (market.getFaction() == Global.getSector().getFaction("aEP_FSF")) {
      boolean hasConsultant = false;
      for (PersonAPI person : market.getPeopleCopy()) {
        if (person.getMemoryWithoutUpdate().contains("$isaEP_Consult")) {
          hasConsultant = true;
          break;
        }
      }

      if (!hasConsultant) {
        PersonAPI person = market.getFaction().createRandomPerson();
        String rankId = Ranks.GROUND_MAJOR;
        if (market.getSize() >= 6) {
          rankId = "aEP_manager";
        }
        else {
          rankId = "aEP_employee";
        }
        person.setPostId("aEP_consultant");
        person.setRankId(rankId);
        person.getMemoryWithoutUpdate().set("$isaEP_Consult", Boolean.valueOf(true));
        market.getCommDirectory().addPerson(person);
        market.addPerson(person);
      }
    }
    else // remove consultant if station is not controlled by FSF
    {

      for (PersonAPI person : market.getPeopleCopy()) {
        if (person.getMemoryWithoutUpdate().contains("$isaEP_Consult")) {
          market.getCommDirectory().removePerson(person);
          market.removePerson(person);
        }
      }

    }
  }

  void checkResearcher(MarketAPI market) {
    FactionAPI FSF = Global.getSector().getFaction("aEP_FSF");

    //第一次运行时会创造一个人物，之后只会在通讯目录中移除添加，而不从市场中移除添加
    boolean has = false;
    for (PersonAPI person : market.getPeopleCopy()) {
      if (person.getMemoryWithoutUpdate().contains("$isaEP_Researcher")) {
        has = true;
        break;
      }
    }
    if (!has) {
      PersonAPI person = market.getFaction().createRandomPerson();
      person.setName(new FullName("Lili", "Yang", FullName.Gender.FEMALE));
      person.setPortraitSprite("graphics/aEP_portraits/1.png");
      person.setPostId("aEP_researcher");
      person.setRankId("aEP_director");
      //一定要写true，不能是1f或者其他的占位，因为在rules里面检测的true/false
      person.getMemoryWithoutUpdate().set("$isaEP_Researcher", true);
      market.getCommDirectory().addPerson(person);
      market.addPerson(person);
    }



    if (market.getFaction() == Global.getSector().getFaction("aEP_FSF")
            && Global.getSector().getPlayerFaction().getRelationship("aEP_FSF") >= -2f) {

      //当满足出现条件时，如果市场存在，但是通讯录中不存在，把人物加入通讯录
      for (PersonAPI person : market.getPeopleCopy()) {
        if (person.getMemoryWithoutUpdate().contains("$isaEP_Researcher")) {
          if(market.getCommDirectory().getEntryForPerson(person) == null){
            market.getCommDirectory().addPerson(person);
          }
        }
      }

    } else {

      //当不满足出现条件时，把人物移除通讯录
      for (PersonAPI person : market.getPeopleCopy()) {
        if (person.getMemoryWithoutUpdate().contains("$isaEP_Researcher")) {
          market.getCommDirectory().removePerson(person);
        }
      }
    }

  }

  void checkCondition(MarketAPI market) {
    market.removeCondition(aEP_MilitaryZone.ID);
    if (market.getFaction().getId().equals(aEP_ID.FACTION_ID_FSF)) {
      market.addCondition(aEP_MilitaryZone.ID);
    }
  }

  public static class CustomEvent {

    public float time = 0f;//1 = 1 second, total eclipsed time
    public float lifeTime = 0f;//1 = 1 second


    public boolean play() {
      return true;
    }

    public void readyToEnd() {
    }

    public void addTime(float amount) {
      this.time = this.time + amount;
    }

    public void setTime(float time) {
      this.time = time;
    }

    public void setLifeTime(float lifeTime) {
      this.lifeTime = lifeTime;
    }

    public float getLimitedTimePassed() {
      return aEP_Tool.limitToTop(time, lifeTime, 0);
    }

    public boolean checkTime() {
      return time > lifeTime;
    }

    public void endNow() {
      time = lifeTime + 1f;
    }
  }

}


