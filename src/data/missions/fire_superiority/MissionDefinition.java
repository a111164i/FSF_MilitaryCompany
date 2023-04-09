package data.missions.fire_superiority;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import combat.util.aEP_Tool;


import java.util.List;

import static combat.util.aEP_DataTool.txt;

public class MissionDefinition implements MissionDefinitionPlugin
{

  public void defineMission(final MissionDefinitionAPI api) {

    // Set up the fleets so we can add ships and fighter wings to them.
    // In this scenario, the fleets are attacking each other, but
    // in other scenarios, a fleet may be defending or trying to escape
    api.initFleet(FleetSide.PLAYER, "FSF", FleetGoal.ATTACK, false, 5);
    api.initFleet(FleetSide.ENEMY, "VT", FleetGoal.ATTACK, true);

    // Set a small blurb for each fleet that shows up on the mission detail and
    // mission results screens to identify each side.
    api.setFleetTagline(FleetSide.PLAYER, txt("aEP_Mission01_01"));
    api.setFleetTagline(FleetSide.ENEMY, txt("aEP_Mission01_02"));

    // These show up as items in the bulleted list under
    // "Tactical Objectives" on the mission detail screen
    api.addBriefingItem(txt("aEP_Mission01_03"));
    api.addBriefingItem(txt("aEP_Mission01_04"));
    api.addBriefingItem(txt("aEP_Mission01_05"));


    // Set up the player's fleet.  Variant names come from the
    // files in data/variants and data/variants/fighters
    //api.addToFleet(FleetSide.PLAYER, "harbinger_Strike", FleetMemberType.SHIP, "TTS Invisible Hand", true, CrewXPLevel.VETERAN);
    api.addToFleet(FleetSide.PLAYER, "aEP_des_cengliu_Standard", FleetMemberType.SHIP, "FSF ship", true);
    api.addToFleet(FleetSide.PLAYER, "aEP_des_cengliu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_fga_raoliu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_fga_raoliu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_fga_raoliu_Standard", FleetMemberType.SHIP, "FSF ship", true);



    // Set up the enemy fleet.
    //api.addToFleet(FleetSide.ENEMY, "mule_Standard", FleetMemberType.SHIP, false);
    //api.addToFleet(FleetSide.ENEMY, "tarsus_Standard", FleetMemberType.SHIP, false);
    //api.addToFleet(FleetSide.ENEMY, "buffalo2_FS", FleetMemberType.SHIP, false);
    api.addToFleet(FleetSide.ENEMY, "enforcer_Balanced", FleetMemberType.SHIP, "VirtualTarget", false);
    //api.addToFleet(FleetSide.ENEMY, "dominator_Assault", FleetMemberType.SHIP, "VT VirtualTarget 02", false);
    api.addToFleet(FleetSide.ENEMY, "enforcer_Balanced", FleetMemberType.SHIP, "VirtualTarget", false);
    api.addToFleet(FleetSide.ENEMY, "enforcer_Balanced", FleetMemberType.SHIP, "VirtualTarget", false);
    api.addToFleet(FleetSide.ENEMY, "enforcer_Balanced", FleetMemberType.SHIP, "VirtualTarget", true);
    api.addToFleet(FleetSide.ENEMY, "enforcer_Balanced", FleetMemberType.SHIP, "VirtualTarget", true);




    // Set up the map.
    float width = 24000f;
    float height = 18000f;
    api.initMap(-width / 2f, width / 2f, -height / 2f, height / 2f);

    float minX = -width / 2;
    float minY = -height / 2;

    api.addNebula(minX + width * 0.5f - 300, minY + height * 0.5f, 1000);
    api.addNebula(minX + width * 0.5f + 300, minY + height * 0.5f, 1000);

    for (int i = 0; i < 5; i++) {
      float x = (float) Math.random() * width - width / 2;
      float y = (float) Math.random() * height - height / 2;
      float radius = 100f + (float) Math.random() * 400f;
      api.addNebula(x, y, radius);
    }

    // Add an asteroid field
    api.addAsteroidField(minX + width / 2f, minY + height / 2f, 0, 8000f,
      20f, 70f, 100);

    api.getContext().aiRetreatAllowed = false;
    api.getContext().fightToTheLast = true;


    api.addPlugin(new BaseEveryFrameCombatPlugin() {
      public void init(CombatEngineAPI engine) {
        engine.getContext().setStandoffRange(6000f);
      }

      public void advance(float amount, List events) {
        for(ShipAPI s : Global.getCombatEngine().getShips()){
          if(s.getOwner() == 0 && s.getVariant().hasHullMod("safetyoverrides")){
            Global.getCombatEngine().removeEntity(s);
          }
        }
      }
    });


  }

}




