package data.missions.two_vs_four;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import combat.util.aEP_Tool;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class MissionDefinition implements MissionDefinitionPlugin
{

  public void defineMission(MissionDefinitionAPI api) {

    //设定我方舰队，敌方的舰船名前缀，舰队名称，战役名称
    // Set up the fleets so we can add ships and fighter wings to them.
    // In this scenario, the fleets are attacking each other, but
    // in other scenarios, a fleet may be defending or trying to escape
    api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, false, 5);
    api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true);

    // Set a small blurb for each fleet that shows up on the mission detail and
    // mission results screens to identify each side.
    api.setFleetTagline(FleetSide.PLAYER, "Lamdor星际巡逻队");
    api.setFleetTagline(FleetSide.ENEMY, "海盗不可能凑出来的'海盗'舰队");

    // These show up as items in the bulleted list under
    // "Tactical Objectives" on the mission detail screen
    api.addBriefingItem("敌人比你快得多，不要被包围了，让海量级掩护你");
    api.addBriefingItem("海量级战术系统的燃料已经用完了，你不可能追得上任何一艘敌人");
    api.addBriefingItem("你的峰值比他们长，坚持就是胜利");


    //在这加自己的船，用装配文件的ID，后面是船名，true和false是“是否是旗舰”的设定
    // Set up the player's fleet.  Variant names come from the
    // files in data/variants and data/variants/fighters
    api.addToFleet(FleetSide.PLAYER, "aEP_HaiLiang_Standard", FleetMemberType.SHIP, "Lamdor Defender", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_ZhongLiu_Standard", FleetMemberType.SHIP, "Ship Shredder", true);


    //在这加敌人的船，一样用装配文件里的ID，加了一艘统治者的Support装配
    // Set up the enemy fleet.
    api.addToFleet(FleetSide.ENEMY, "aurora_Balanced", FleetMemberType.SHIP, "Unknown", true);
    api.addToFleet(FleetSide.ENEMY, "aurora_Balanced", FleetMemberType.SHIP, "Unknown", false);
    api.addToFleet(FleetSide.ENEMY, "shrike_Attack", FleetMemberType.SHIP, "Unknown", false);
    api.addToFleet(FleetSide.ENEMY, "shrike_Attack", FleetMemberType.SHIP, "Unknown", false);

    //设置输赢条件
    api.defeatOnShipLoss("Lamdor Defender");
    api.defeatOnShipLoss("Ship Shredder");

    //设定地图的尺寸和贴图和里面的星云，陨石，占领点，直接粘的一个原版战役
    // Set up the map.
    float width = 12000f;
    float height = 12000f;
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
    //Add planet
    api.addPlanet(0f, 0f, 600f, "aEP_IND_Homeplanet", 15f, true);

    //加入特殊的每帧效果
    api.addPlugin(new BaseEveryFrameCombatPlugin()
    {
      boolean didOnce = false;
      final boolean setFlag = false;

      public void init(CombatEngineAPI engine) {
        engine.getContext().setStandoffRange(6000f);
        engine.getContext().fightToTheLast = true;
        engine.getContext().aiRetreatAllowed = false;
        engine.getContext().setInitialDeploymentBurnDuration(0f);
        engine.setSideDeploymentOverrideSide(FleetSide.PLAYER);

      }

      public void advance(float amount, List events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        //布置战场
        if (!didOnce && !Global.getCombatEngine().getCombatUI().isShowingDeploymentDialog()) {
          //如果一个也没部署，跳过
          if(Global.getCombatEngine().getFleetManager(0).getReservesCopy().size()>=2) return;
          didOnce = true;

          //如果没有完全部署
          List<FleetMemberAPI> shipsNotDeployed = new ArrayList<>();
          if(Global.getCombatEngine().getFleetManager(0).getReservesCopy().size()>0) {
            //把没有部署的全部强制部署
            shipsNotDeployed.addAll(Global.getCombatEngine().getFleetManager(0).getReservesCopy());
            for (FleetMemberAPI reserve : shipsNotDeployed) {
              ShipAPI ship = Global.getCombatEngine().getFleetManager(0).spawnFleetMember(reserve, new Vector2f(0f, 0f), 90f, 0f);
            }
          }

          //移动我方舰船的到合适位置
          for (ShipAPI s : Global.getCombatEngine().getShips()) {
            if(s == null || s.getName() == null || s.getLocation() == null) continue;
            if (s.getName().equals("Lamdor Defender")) s.getLocation().set(new Vector2f(200, 200));
            if (s.getName().equals("Ship Shredder")) s.getLocation().set(new Vector2f(-200, -200));
          }

          //部署敌人
          float angle = 0f;
          for (FleetMemberAPI reserve : Global.getCombatEngine().getFleetManager(1).getReservesCopy()) {
            Global.getCombatEngine().getFleetManager(1).spawnFleetMember(reserve, aEP_Tool.getExtendedLocationFromPoint(new Vector2f(0f, 0f), angle + 45f, 3000f), angle + 225f, 0f);
            angle += 45f;
          }

          //移除海量的系统使用次数
          for (ShipAPI s : Global.getCombatEngine().getShips()) {
            if (s.getHullSpec().getHullId().equals("aEP_HaiLiang")) {
              s.getSystem().setAmmo(0);
            }
          }
           ;
        }

      }
    });


  }

}




