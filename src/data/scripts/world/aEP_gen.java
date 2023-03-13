package data.scripts.world;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import data.scripts.world.aEP_systems.aEP_FSF_Heng;
import data.scripts.world.aEP_systems.aEP_IND_Lamdor;

import java.util.List;


public class aEP_gen implements SectorGeneratorPlugin
{

  @Override
  public void generate(SectorAPI sector) {

    new aEP_FSF_Heng().generate(sector);
    new aEP_IND_Lamdor().generate(sector);
    FactionAPI fsf = sector.getFaction("aEP_FSF");

    //default relation
    if (fsf != null) {

      List<FactionAPI> allFactions = sector.getAllFactions();
      for (FactionAPI f : allFactions) {
        fsf.setRelationship(f.getId(), RepLevel.SUSPICIOUS);
      }
      FactionAPI player = sector.getFaction(Factions.PLAYER);
      FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
      FactionAPI tritachyon = sector.getFaction(Factions.TRITACHYON);
      FactionAPI pirates = sector.getFaction(Factions.PIRATES);
      FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);
      FactionAPI church = sector.getFaction(Factions.LUDDIC_CHURCH);
      FactionAPI path = sector.getFaction(Factions.LUDDIC_PATH);
      FactionAPI diktat = sector.getFaction(Factions.DIKTAT);
      FactionAPI persean = sector.getFaction(Factions.PERSEAN);
      FactionAPI guard = sector.getFaction(Factions.LIONS_GUARD);

      fsf.setRelationship(player.getId(), 0f);
      fsf.setRelationship(hegemony.getId(), 0.2f);
      fsf.setRelationship(tritachyon.getId(), -0.1f);
      fsf.setRelationship(pirates.getId(), -0.7f);
      fsf.setRelationship(independent.getId(), 0.25f);
      fsf.setRelationship(persean.getId(), 0.1f);
      fsf.setRelationship(church.getId(), 0f);
      fsf.setRelationship(path.getId(), -0.7f);
      fsf.setRelationship(diktat.getId(), -0.1f);
    }

    //mod factions
    //fsf.setRelationship("ORA", RepLevel.WELCOMING);


  }
}
