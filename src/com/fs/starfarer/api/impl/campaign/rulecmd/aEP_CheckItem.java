package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

public class aEP_CheckItem extends BaseCommandPlugin
{
  /**
   * @param params commodityId, check threshold, moreOrLess.
   */

  @Override
  public boolean execute(String ruleId, InteractionDialogAPI dialog, java.util.List<Misc.Token> params, java.util.Map<String, MemoryAPI> memoryMap) {

    String commodity = params.get(0).string;
    float num = 0f;
    if (!params.get(1).type.equals(Misc.TokenType.VARIABLE)) {
      num = Float.parseFloat(params.get(1).string);
    }
    else {
      num = params.get(1).getFloat(memoryMap);
    }
    String moreOrLess = params.get(2).string;


    if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getCargo() != null) {
      //get cargo
      CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();


      float numInCargo = 0f;
      if (commodity.equals("credits")) {
        numInCargo = cargo.getCredits().get();
      }
      else {
        numInCargo = cargo.getCommodityQuantity(commodity);
      }

      if (moreOrLess.equals("more")) {
        return numInCargo > num;
      }

      if (moreOrLess.equals("less")) {
        return numInCargo < num;
      }

      if (moreOrLess.equals("checkId")) {
        return memoryMap.get("local").getString("$option").contains(commodity);

      }

    }
    return false;
  }
}
