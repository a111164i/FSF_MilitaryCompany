package data.scripts.campaign.econ.environment;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionProductionAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;


public class aEP_MassiveProduce extends BaseMarketConditionPlugin
{
  private static final float PRICE_MULT = 0.75f;

  public void apply(String id) {
    FactionAPI faction;
    faction = market.getFaction();
    FactionProductionAPI production = faction.getProduction();
    production.setCostMult(PRICE_MULT);

  }

  public void unapply(String id) {
    FactionProductionAPI production = market.getFaction().getProduction();
    production.setCostMult(1);
  }

}



