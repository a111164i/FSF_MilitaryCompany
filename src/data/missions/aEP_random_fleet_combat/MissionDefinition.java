package data.missions.aEP_random_fleet_combat;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.loading.RoleEntryAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONObject;

public class MissionDefinition implements MissionDefinitionPlugin {

  float FP = 150f;

  JSONObject setting;
  {
    try {
       setting = Global.getSettings().loadJSON("data/missions/aEP_random_fleet_combat/descriptor.json");

    } catch (Exception e1) {
    }

  }

  private boolean checkIsValidEnemy(String variantId){
    //尝试读取json里面子弹丸的数量和伤害来除
    try {
      String prefix = setting.getString("player_prefix");
      String prefixEnemy = setting.getString("enemy_prefix");

      if(!prefixEnemy.equals("")){
        //如果写了前缀，应用前缀
        if(variantId.startsWith(prefixEnemy)) return  true;
      }else {
        //如果没写，排除掉对面的prefix，除了对面的船，都可以刷
        if(variantId.startsWith(prefix)) return false;
        return true;
      }
    }catch (Exception e1){

    }
    return false;
  }

  private boolean checkIsValidFriendly(String variantId){

    //尝试读取json里面子弹丸的数量和伤害来除
    try {
      String prefix = setting.getString("player_prefix");
      String prefixEnemy = setting.getString("enemy_prefix");

      if(!prefix.equals("")){
        //如果写了前缀，应用前缀
        if(variantId.startsWith(prefix)) return  true;
      }else {
        //如果没写，排除掉对面的prefix，除了对面的船，都可以刷
        if(variantId.startsWith(prefixEnemy)) return false;
        return true;
      }
    }catch (Exception e1){

    }
    return false;
  }

  private WeightedRandomPicker<String> ships = new WeightedRandomPicker();
  private WeightedRandomPicker<String> ships2= new WeightedRandomPicker();
  private void addShip(String variant, float weight) {
    ships.add(variant,weight);
  }
  private void addShip2(String variant, float weight) {
    ships2.add(variant,weight);
  }

  private void generateFleet(float maxFP, FleetSide side, WeightedRandomPicker<String> ships, MissionDefinitionAPI api) {
    int currFP = 0;

    //随机舰队
    boolean didFlag = false;
    while (true) {
      String id = ships.pick();
      currFP += api.getFleetPointCost(id);
      if (currFP > maxFP) {
        return;
      }

      if (id.endsWith("_wing")) {
        api.addToFleet(side, id, FleetMemberType.FIGHTER_WING, false);
      } else {
        api.addToFleet(side, id, FleetMemberType.SHIP, false);
      }
    }
  }

  public void defineMission(MissionDefinitionAPI api) {

    //getEntriesForRole这个带着factionId的方法在战役里不知道为啥只能得到一个空的list
    //这里得到全部的variant然后过滤一遍
    List<RoleEntryAPI> combatCap = Global.getSettings().getDefaultEntriesForRole("combatCapital");
    List<RoleEntryAPI> combatCru= Global.getSettings().getDefaultEntriesForRole("combatLarge");
    List<RoleEntryAPI> combatDes = Global.getSettings().getDefaultEntriesForRole("combatMedium");
    List<RoleEntryAPI> combatFga = Global.getSettings().getDefaultEntriesForRole("combatSmall");
    //主力舰/4，巡洋/2.5，驱逐/1.5，护卫为1
    for(RoleEntryAPI role : combatCap){
      if(!checkIsValidFriendly(role.getVariantId())) continue;
      addShip(role.getVariantId(), role.getWeight()/4f);
    }
    for(RoleEntryAPI role : combatCru){
      if(!checkIsValidFriendly(role.getVariantId())) continue;
      addShip(role.getVariantId(), role.getWeight()/2.5f);
    }
    for(RoleEntryAPI role : combatDes){
      if(!checkIsValidFriendly(role.getVariantId())) continue;
      addShip(role.getVariantId(), role.getWeight()/1.5f);
    }
    for(RoleEntryAPI role : combatFga){
      if(!checkIsValidFriendly(role.getVariantId())) continue;
      addShip(role.getVariantId(), role.getWeight()/1f);
    }


    List<RoleEntryAPI> carrierLarge = Global.getSettings().getDefaultEntriesForRole("carrierLarge");
    List<RoleEntryAPI> carrierMed= Global.getSettings().getDefaultEntriesForRole("carrierMedium");
    List<RoleEntryAPI> carrierSmall = Global.getSettings().getDefaultEntriesForRole("carrierSmall");

    //主力舰或巡洋/3，驱逐/1.5，护卫为1
    for(RoleEntryAPI role : carrierLarge){
      if(!checkIsValidFriendly(role.getVariantId())) continue;
      addShip(role.getVariantId(), role.getWeight()/3f);
    }
    for(RoleEntryAPI role : carrierMed){
      if(!checkIsValidFriendly(role.getVariantId())) continue;
      addShip(role.getVariantId(), role.getWeight()/1.5f);
    }
    for(RoleEntryAPI role : carrierSmall){
      if(!checkIsValidFriendly(role.getVariantId())) continue;
      addShip(role.getVariantId(), role.getWeight()/1f);
    }

    //--------------------------------------------------------------//
    //创造敌人的list
    //主力舰/4，巡洋/2.5，驱逐/1.5，护卫为1
    for(RoleEntryAPI role : combatCap){
      if(!checkIsValidEnemy(role.getVariantId())) continue;
      addShip2(role.getVariantId(), role.getWeight()/4f);
    }
    for(RoleEntryAPI role : combatCru){
      if(!checkIsValidEnemy(role.getVariantId())) continue;
      addShip2(role.getVariantId(), role.getWeight()/2.5f);
    }
    for(RoleEntryAPI role : combatDes){
      if(!checkIsValidEnemy(role.getVariantId())) continue;
      addShip2(role.getVariantId(), role.getWeight()/1.5f);
    }
    for(RoleEntryAPI role : combatFga){
      if(!checkIsValidEnemy(role.getVariantId())) continue;
      addShip2(role.getVariantId(), role.getWeight()/1f);
    }


    //主力舰或巡洋/3，驱逐/1.5，护卫为1
    for(RoleEntryAPI role : carrierLarge){
      if(!checkIsValidEnemy(role.getVariantId())) continue;
      addShip2(role.getVariantId(), role.getWeight()/3f);
    }
    for(RoleEntryAPI role : carrierMed){
      if(!checkIsValidEnemy(role.getVariantId())) continue;
      addShip2(role.getVariantId(), role.getWeight()/1.5f);
    }
    for(RoleEntryAPI role : carrierSmall){
      if(!checkIsValidEnemy(role.getVariantId())) continue;
      addShip2(role.getVariantId(), role.getWeight()/1f);
    }


    // Set up the fleets so we can add ships and fighter wings to them.
    // In this scenario, the fleets are attacking each other, but
    // in other scenarios, a fleet may be defending or trying to escape
    api.initFleet(FleetSide.PLAYER, "ISS", FleetGoal.ATTACK, true, 5);
    api.initFleet(FleetSide.ENEMY, "ISS", FleetGoal.ATTACK, true, 5);

    // Set a small blurb for each fleet that shows up on the mission detail and
    // mission results screens to identify each side.
    api.setFleetTagline(FleetSide.PLAYER, "Your forces");
    api.setFleetTagline(FleetSide.ENEMY, "Enemy forces");

    // These show up as items in the bulleted list under
    // "Tactical Objectives" on the mission detail screen
    api.addBriefingItem("Defeat all enemy forces");

    // Set up the fleets
    generateFleet(FP, FleetSide.PLAYER, ships, api);
    generateFleet(FP, FleetSide.ENEMY, ships2, api);

    api.getContext().aiRetreatAllowed = false;
    api.getContext().objectivesAllowed = true;
    api.getContext().setPlayerCommandPoints(99);


    // Set up the map.
    float width = 24000f;
    float height = 18000f;
    api.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);

    float minX = -width/2;
    float minY = -height/2;


    for (int i = 0; i < 50; i++) {
      float x = (float) Math.random() * width - width/2;
      float y = (float) Math.random() * height - height/2;
      float radius = 100f + (float) Math.random() * 400f;
      api.addNebula(x, y, radius);
    }

    // Add objectives
    api.addObjective(minX + width * 0.25f + 2000, minY + height * 0.25f + 2000, "nav_buoy");
    api.addObjective(minX + width * 0.75f - 2000, minY + height * 0.25f + 2000, "comm_relay");
    api.addObjective(minX + width * 0.75f - 2000, minY + height * 0.75f - 2000, "nav_buoy");
    api.addObjective(minX + width * 0.25f + 2000, minY + height * 0.75f - 2000, "comm_relay");
    api.addObjective(minX + width * 0.5f, minY + height * 0.5f, "sensor_array");

    String [] planets = {"barren", "terran", "gas_giant", "ice_giant", "cryovolcanic", "frozen", "jungle", "desert", "arid"};
    String planet = planets[(int) (Math.random() * (double) planets.length)];
    float radius = 100f + (float) Math.random() * 150f;
    api.addPlanet(0, 0, radius, planet, 200f, true);
  }

}





