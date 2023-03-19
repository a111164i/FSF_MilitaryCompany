package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import combat.util.aEP_ID;
import combat.util.aEP_Tool;
import data.scripts.campaign.econ.environment.aEP_MilitaryZone;
import data.scripts.world.aEP_systems.aEP_FSF_DWR43;

import static data.scripts.world.aEP_systems.aEP_FSF_DWR43.MINING_PLANET_MARKET_ID;


public class a111164CampaignPlugin implements EveryFrameScript
{


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
    if (dayTimer < 1) return;
    dayTimer = dayTimer - 1f;


    //check and create persons
    for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {

      String id = market.getPrimaryEntity().getId();
      if (id.equals("aEP_FSF_SaleStation")) {
        checkConsultant(market);
      }

      if (id.equals("aEP_FSF_DefStation")) {
        checkResearcher(market);
      }

      if (id.equals(aEP_FSF_DWR43.MINING_PLANET_MARKET_ID)) {
        checkCondition(market);
      }

      if (id.equals(aEP_FSF_DWR43.FACTORY_STATION_MARKET_ID)) {
        checkCondition(market);
      }

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
    if (market.getFaction() == Global.getSector().getFaction("aEP_FSF") && Global.getSector().getPlayerFaction().getRelationship("aEP_FSF") > 0.25f) {
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
        person.getMemoryWithoutUpdate().set("$isaEP_Researcher", Boolean.valueOf(true));
        market.getCommDirectory().addPerson(person);
        market.addPerson(person);
      }
    }
    else // remove if station is not controlled by FSF
    {

      for (PersonAPI person : market.getPeopleCopy()) {
        if (person.getMemoryWithoutUpdate().contains("$isaEP_Researcher")) {
          market.getCommDirectory().removePerson(person);
          market.removePerson(person);
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

    public float time = 0f;//1 = 1 second, total life time till now
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


