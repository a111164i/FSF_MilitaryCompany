package data.missions.aEP_test;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.missions.aEP_MissionUtils;


import java.util.List;

public class MissionDefinition implements MissionDefinitionPlugin
{

  public void defineMission(MissionDefinitionAPI api) {


    //设定我方舰队，敌方的舰船名前缀，舰队名称，战役名称
    // Set up the fleets so we can add ships and fighter wings to them.
    // In this scenario, the fleets are attacking each other, but
    // in other scenarios, a fleet may be defending or trying to escape
    api.initFleet(FleetSide.PLAYER, "FSF", FleetGoal.ATTACK, false, 5);
    api.initFleet(FleetSide.ENEMY, "VT", FleetGoal.ATTACK, true);

    // Set a small blurb for each fleet that shows up on the mission detail and
    // mission results screens to identify each side.
    api.setFleetTagline(FleetSide.PLAYER, "FSF Test");
    api.setFleetTagline(FleetSide.ENEMY, "Test");

    // These show up as items in the bulleted list under
    // "Tactical Objectives" on the mission detail screen
    api.addBriefingItem("Test all FSF Ships");


    //在这加自己的船，用装配文件的ID，后面是船名，true和false是“是否是旗舰”的设定
    // Set up the player's fleet.  Variant names come from the
    // files in data/variants and data/variants/fighters
    //主力
    api.addToFleet(FleetSide.PLAYER, "aEP_cap_decomposer_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cap_biaobing_Burst", FleetMemberType.SHIP, "FSF ship", true);
    api.addToFleet(FleetSide.PLAYER, "aEP_cap_duiliu_Standard", FleetMemberType.SHIP, "FSF ship", true);
    api.addToFleet(FleetSide.PLAYER, "aEP_cap_nuanchi_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cap_neibo_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cap_shangshengliu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cap_shangshengliu_mk2_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cap_shangshengliu_mk3_Standard", FleetMemberType.SHIP, "FSF ship", false);

    //巡洋
    api.addToFleet(FleetSide.PLAYER, "aEP_cru_hailiang_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cru_zhongliu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cru_requan_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cru_shanhu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cru_pubu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cru_pingding_Standard", FleetMemberType.SHIP, "FSF ship", false);


    //驱逐
    api.addToFleet(FleetSide.PLAYER, "aEP_des_lianliu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_des_cengliu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_des_yangji_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_des_youjiyan_Def", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_des_youjiyan_mk2_Def", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_des_shendu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_des_yonglang_mk2_Standard", FleetMemberType.SHIP, "FSF ship", false);



    //护卫
    api.addToFleet(FleetSide.PLAYER, "aEP_fga_wanliu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_fga_xiliu_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_fga_xiliu_mk2_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_fga_raoliu_PD", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_fga_yonglang_Mixed", FleetMemberType.SHIP, "FSF ship", false);

    //特殊
    api.addToFleet(FleetSide.PLAYER, "aEP_typeB28_variant", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_des_shendu_mk2_Standard", FleetMemberType.SHIP, "FSF ship", false);
    api.addToFleet(FleetSide.PLAYER, "aEP_cru_lengshe_Standard", FleetMemberType.SHIP, "FSF ship", false);

    //取消filter
    aEP_MissionUtils.restore();


    //在这加敌人的船，一样用装配文件里的ID，加了一艘统治者的Support装配
    // Set up the enemy fleet.
    api.addToFleet(FleetSide.ENEMY, "dominator_Support", FleetMemberType.SHIP, "VT VirtualTarget 01", true);

    //设定地图的尺寸和贴图和里面的星云，陨石，占领点，直接粘的一个原版战役
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


    //加入特殊的每帧效果
    api.addPlugin(new BaseEveryFrameCombatPlugin()
    {
      public void init(CombatEngineAPI engine) {
        engine.getContext().setStandoffRange(6000f);
      }

      public void advance(float amount, List events) {
      }
    });


  }

}




