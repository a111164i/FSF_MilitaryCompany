package data.scripts.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.Misc;
import combat.util.aEP_ID;
import data.scripts.world.aEP_systems.aEP_FSF_DWR43;
import data.scripts.world.aEP_systems.aEP_FSF_Heng;
import data.scripts.world.aEP_systems.aEP_IND_Lamdor;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.Vector;

import static com.fs.starfarer.api.impl.campaign.rulecmd.aEP_AdvanceWeaponMission.MISSILE_CARRIER_SPEC_ID;


public class aEP_gen implements SectorGeneratorPlugin {

  @Override
  public void generate(SectorAPI sector) {

    new aEP_FSF_Heng().generate(sector);
    new aEP_IND_Lamdor().generate(sector);
    new aEP_FSF_DWR43().generate(sector);


    commonGen(sector);

  }


  public void randomGenerate(SectorAPI sector) {

    new aEP_FSF_Heng().randomGenerate(sector);
    new aEP_IND_Lamdor().randdomGenerate(sector);
    new aEP_FSF_DWR43().generate(sector);


    FactionAPI fsf = sector.getFaction("aEP_FSF");

    commonGen(sector);

  }

  public void commonGen(SectorAPI sector){
    FactionAPI fsf = sector.getFaction(aEP_ID.FACTION_ID_FSF);

    //default relation
    if (fsf != null) {

      List<FactionAPI> allFactions = sector.getAllFactions();
      for (FactionAPI f : allFactions) {
        fsf.setRelationship(f.getId(),0f);

        //template, no particular other mod faction to adjust yet
        if(f.getId().equals("")) fsf.setRelationship(f.getId(),0f);
      }
      fsf.setRelationship(Factions.PLAYER, 0f);
      fsf.setRelationship(Factions.HEGEMONY, 0.2f);
      fsf.setRelationship(Factions.PERSEAN, 0f);
      fsf.setRelationship(Factions.TRITACHYON, -0.1f);
      fsf.setRelationship(Factions.PIRATES, -0.5f);
      fsf.setRelationship(Factions.INDEPENDENT, 0.35f);
      fsf.setRelationship(Factions.LUDDIC_CHURCH, 0.1f);
      fsf.setRelationship(Factions.LUDDIC_PATH, -0.5f);
      fsf.setRelationship(Factions.DIKTAT, -0.15f);

      fsf.setRelationship(Factions.REMNANTS, -0.75f);
    }

    //mod factions
    //fsf.setRelationship("ORA", RepLevel.WELCOMING);

    //找到askonia星系
    StarSystemAPI ask = sector.getStarSystem("Askonia");
    if(ask != null){
      if(Global.getSettings().getMissionScore("aEP_assassination") >= 100){

        //创造一个的残骸实体，并绑上打捞参数

        DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData("aEP_fga_shuangshen_Standard", ShipRecoverySpecial.ShipCondition.WRECKED, 0f), false);
        params.ship.shipName = "Prototype";
        params.ship.nameAlwaysKnown = true;
        params.durationDays = 999999999f;
        params.ship.addDmods = true;
        SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(ask,
            Entities.WRECK, Factions.NEUTRAL, params);
        //设置实体位置
        Vector2f location = MathUtils.getRandomPointOnCircumference(Misc.ZERO,16000f);
        ship.setLocation(location.x, location.y);
        ship.setId("aEP_assassination_reward");
        ship.setDiscoverable(true);
        ship.setSensorProfile(8000f);

        //创造一份特殊舰船打捞数据
        ShipRecoverySpecial.ShipRecoverySpecialData params2 = new ShipRecoverySpecial.ShipRecoverySpecialData("Prototype");
        ShipRecoverySpecial.PerShipData data = new ShipRecoverySpecial.PerShipData("aEP_fga_shuangshen_Standard", ShipRecoverySpecial.ShipCondition.WRECKED);
        data.addDmods = true;
        data.shipName = "Prototype";
        data.condition = ShipRecoverySpecial.ShipCondition.WRECKED;
        data.nameAlwaysKnown = true;
        params2.addShip(data);
        params2.storyPointRecovery = true;

        //把特殊打捞数据绑给实体
        Misc.setSalvageSpecial(ship, params2);
      }
    }
  }
}
