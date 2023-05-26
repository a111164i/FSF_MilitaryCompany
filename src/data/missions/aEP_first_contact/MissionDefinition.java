package data.missions.aEP_first_contact;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import combat.util.aEP_ID;
import combat.util.aEP_Tool;
import data.missions.aEP_MissionUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static combat.util.aEP_DataTool.txt;
import static data.missions.aEP_MissionUtils.disableUnknownWeapon;

public class MissionDefinition implements MissionDefinitionPlugin
{



  public void defineMission(final MissionDefinitionAPI api) {

    //设定我方舰队，敌方的舰船名前缀，舰队名称，战役名称
    // Set up the fleets so we can add ships and fighter wings to them.
    // In this scenario, the fleets are attacking each other, but
    // in other scenarios, a fleet may be defending or trying to escape
    api.getContext().setStandoffRange(10000f);
    api.getContext().fightToTheLast = true;
    api.getContext().aiRetreatAllowed = false;
    api.getContext().setInitialDeploymentBurnDuration(0f);

    api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, false, 5);
    api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true);

    // Set a small blurb for each fleet that shows up on the mission detail and
    // mission results screens to identify each side.
    api.setFleetTagline(FleetSide.PLAYER, txt("aEP_Mission03_01"));
    api.setFleetTagline(FleetSide.ENEMY, txt("aEP_Mission03_02"));

    // These show up as items in the bulleted list under
    // "Tactical Objectives" on the mission detail screen
    api.addBriefingItem(txt("aEP_Mission03_03"));
    api.addBriefingItem(txt("aEP_Mission03_04"));
    api.addBriefingItem(txt("aEP_Mission03_05"));

    //在这加自己的船，用装配文件的ID，后面是船名，true和false是“是否是旗舰”的设定
    // Set up the player's fleet.  Variant names come from the
    // files in data/variants and data/variants/fighters
    String name1 = "破冰";
    api.addToFleet(FleetSide.PLAYER, "aEP_cap_decomposer_Standard", FleetMemberType.SHIP, name1, true);
    String name2 = "汹涌";
    api.addToFleet(FleetSide.PLAYER, "aEP_cru_zhongliu_Standard", FleetMemberType.SHIP, name2, false);
    api.addToFleet(FleetSide.PLAYER, "aEP_fga_raoliu_Standard", FleetMemberType.SHIP, "A02",false);
    api.addToFleet(FleetSide.PLAYER, "aEP_fga_xiliu_Standard", FleetMemberType.SHIP,  "F4A",false);


    //在这加敌人的船，一样用装配文件里的ID，加了一艘统治者的Support装配
    // Set up the enemy fleet.
    api.addToFleet(FleetSide.ENEMY, "aEP_scintilla_Strike", FleetMemberType.SHIP,  false);
    api.addToFleet(FleetSide.ENEMY, "aEP_scintilla_Strike", FleetMemberType.SHIP,  false);

    api.addToFleet(FleetSide.ENEMY, "aEP_brilliant_Standard", FleetMemberType.SHIP, false);

    api.addToFleet(FleetSide.ENEMY, "aEP_lumen_Standard", FleetMemberType.SHIP,  false);
    api.addToFleet(FleetSide.ENEMY, "aEP_lumen_Standard", FleetMemberType.SHIP,  false);
    api.addToFleet(FleetSide.ENEMY, "aEP_glimmer_Assault", FleetMemberType.SHIP,  false);
    api.addToFleet(FleetSide.ENEMY, "aEP_glimmer_Assault", FleetMemberType.SHIP,  false);

    //设置输赢条件
    api.defeatOnShipLoss(name1);
    api.defeatOnShipLoss(name2);

    //过滤插件
    FactionAPI f = Global.getSettings().createBaseFaction(aEP_ID.FACTION_ID_FSF);
    if(Keyboard.isKeyDown(Keyboard.KEY_F)){
      aEP_MissionUtils.filterAllNonFactionalWeapons(f);
    }else {
      aEP_MissionUtils.restore();
    }


    //设定地图的尺寸和贴图和里面的星云，陨石，占领点，直接粘的一个原版战役
    // Set up the map.
    api.setBackgroundSpriteName("graphics/backgrounds/hyperspace1.jpg");
    float width = 6000f;
    float height = 12000f;
    api.initMap(-width / 2f, width / 2f, -height / 2f, height / 2f);


    float minX = -width / 2;
    float minY = -height / 2;


    for (int i = 0; i < 5; i++) {
      float x = (float) Math.random() * width - width / 2;
      float y = (float) Math.random() * height - height / 2;
      float radius = 100f + (float) Math.random() * 400f;
      api.addNebula(x, y, radius);
    }

    // Add an asteroid field
    api.addAsteroidField(minX + width / 2f, minY + height / 2f, 0, 8000f,
      20f, 70f, 80);

    //加入特殊的每帧效果
    api.addPlugin(new BaseEveryFrameCombatPlugin() {
      FactionAPI f = Global.getSettings().createBaseFaction(aEP_ID.FACTION_ID_FSF);
      boolean didOnce = false;
      IntervalUtil checkTracker = new IntervalUtil(2.5f,2.5f);


      public void advance(float amount, List events) {
        checkTracker.advance(amount);
        if(!checkTracker.intervalElapsed()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        //检测损失
        if (!didOnce) {
          CombatFleetManagerAPI manager =  engine.getFleetManager(FleetSide.PLAYER);


          for(ShipAPI ship : engine.getShips()){
            if(ship.getOwner() == 0 && !ship.isFighter() && !ship.isDrone() ) {
              disableUnknownWeapon(f,ship);
            }
          }



          int sizeDisable = 0;
          for(FleetMemberAPI m : manager.getDisabledCopy()){
            //模块被击毁不算在总数内
            if(m.getUnmodifiedDeploymentPointsCost() <= 0) continue;
            sizeDisable += 1;
          }

          if(sizeDisable > 1){
            engine.endCombat(0f,FleetSide.ENEMY);
            didOnce = true;
          }

        }

        if(didOnce){
          engine.removePlugin(this);
        }

      }


    });


  }

}




