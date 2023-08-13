package data.scripts.world;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import combat.util.aEP_ID;
import data.scripts.world.aEP_systems.aEP_FSF_DWR43;
import data.scripts.world.aEP_systems.aEP_FSF_Heng;
import data.scripts.world.aEP_systems.aEP_IND_Lamdor;

import java.util.List;


public class aEP_gen implements SectorGeneratorPlugin {

  @Override
  public void generate(SectorAPI sector) {

    new aEP_FSF_Heng().generate(sector);
    new aEP_IND_Lamdor().generate(sector);
    new aEP_FSF_DWR43().generate(sector);


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


  }


  public void randomGenerate(SectorAPI sector) {

    new aEP_FSF_Heng().randomGenerate(sector);
    new aEP_IND_Lamdor().randdomGenerate(sector);
    new aEP_FSF_DWR43().generate(sector);


    FactionAPI fsf = sector.getFaction("aEP_FSF");

    //default relation
    if (fsf != null) {

      List<FactionAPI> allFactions = sector.getAllFactions();
      for (FactionAPI f : allFactions) {
        fsf.setRelationship(f.getId(), RepLevel.SUSPICIOUS);
      }
      fsf.setRelationship(Factions.PLAYER, 0f);
      fsf.setRelationship(Factions.HEGEMONY, 0.2f);
      fsf.setRelationship(Factions.PERSEAN, 0f);
      fsf.setRelationship(Factions.TRITACHYON, -0.1f);
      fsf.setRelationship(Factions.PIRATES, -0.7f);
      fsf.setRelationship(Factions.INDEPENDENT, 0.3f);
      fsf.setRelationship(Factions.LUDDIC_CHURCH, 0.1f);
      fsf.setRelationship(Factions.LUDDIC_PATH, -0.7f);
      fsf.setRelationship(Factions.DIKTAT, -0.1f);

      fsf.setRelationship(Factions.REMNANTS, -0.7f);
    }

    //mod factions
    //fsf.setRelationship("ORA", RepLevel.WELCOMING);


  }
}
