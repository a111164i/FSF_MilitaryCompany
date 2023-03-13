package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

public class aEP_UpdateData extends BaseCommandPlugin
{


  @Override
  public boolean execute(java.lang.String ruleId, InteractionDialogAPI dialog, java.util.List<Misc.Token> params, java.util.Map<java.lang.String, MemoryAPI> memoryMap) {
    switch (params.get(0).string) {
      case "remain_parts_in_cargo":
        if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getCargo() != null) {
          CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
          float remain_part_now = cargo.getCommodityQuantity("aEP_remain_part");

          //put in global memory
          Global.getSector().getMemory().set("$aEP_remain_part_in_cargo", remain_part_now);
          Global.getSector().getFaction("aEP_FSF").getMemory().set("$aEP_remain_part_in_cargo", remain_part_now);


          return !(remain_part_now < 1);
        }
        break;

      case "crew_in_cargo":
        if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getCargo() != null) {
          CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
          float crew_now = cargo.getCommodityQuantity("crew");

          //put in global memory
          Global.getSector().getFaction("aEP_FSF").getMemory().set("$aEP_crew_in_cargo", crew_now);


          return !(crew_now < 1);
        }
        break;
    }
    return true;
  }
}
