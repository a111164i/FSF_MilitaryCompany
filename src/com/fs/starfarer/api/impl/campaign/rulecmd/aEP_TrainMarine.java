package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Misc;
import combat.util.aEP_DataTool;
import data.scripts.campaign.intel.aEP_MarineTrainIntel;

import java.awt.*;

public class aEP_TrainMarine extends BaseCommandPlugin
{
  private static final float PRICE_PER_CREW = 75f;
  private static final float DAYS_TO_TRAIN = 90f;

  private static float toTrainNum;

  @Override
  public boolean execute(String ruleId, InteractionDialogAPI dialog, java.util.List<Misc.Token> params, java.util.Map<String, MemoryAPI> memoryMap) {
    switch (params.get(0).string) {
      case "selection":
        if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getCargo() != null) {
          CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
          int numOfCrew = cargo.getCrew();
          Global.getSector().getFaction("aEP_FSF").getMemory().set("$aEP_crew_in_cargo", numOfCrew);

          //credits and crew check
          if (cargo.getCredits().get() < 100 * PRICE_PER_CREW || cargo.getCrew() < 100) {
            dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_TrainMarine01"));
            return true;
          }

          //show price
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_TrainMarine02"), Color.yellow, (int) PRICE_PER_CREW + "");

          //add selector
          dialog.getOptionPanel().addSelector("Select Num",
            "aEP_trainMarineSelector",//set id to get selector's state later
            Color.WHITE,//bar color
            400f,// Width in pixels, including value label on the right.
            50f,// Width of the value label on the right.
            100,//min
            Math.min(cargo.getCrew(), (cargo.getCredits().get() / PRICE_PER_CREW) - 1),//max
            ValueDisplayMode.VALUE,//How to display the value
            null);//Tooltip text. Can be null.
          dialog.getOptionPanel().setSelectorValue("aEP_trainMarineSelector", 100);


          toTrainNum = dialog.getOptionPanel().getSelectorValue("aEP_trainMarineSelector");
          dialog.getOptionPanel().addOption("Confirm", "aEP_marine_training_start");


        }
        break;
      case "start":
        if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getCargo() != null) {

          //如果选择以前有SelectedValue，可以直接get
          toTrainNum = dialog.getOptionPanel().getSelectorValue("aEP_trainMarineSelector");

          //remove crew
          CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
          cargo.removeCommodity("crew", toTrainNum);
          //remove credits
          cargo.getCredits().subtract(toTrainNum * PRICE_PER_CREW);

          //start train event
          Global.getSector().addScript(new aEP_MarineTrainIntel(DAYS_TO_TRAIN, toTrainNum));


          //show how much you spent
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_TrainMarine03"), Color.red, (int) (toTrainNum * PRICE_PER_CREW) + "");


          //clear panel and add a new option
          //you can't add option in rules.csv, that will auto make a new optionAPI so you can't get selector
          dialog.getOptionPanel().clearOptions();
          dialog.getOptionPanel().addOption("Continue", "aEP_marine_training_deal");
        }
        break;
      case "check":
        boolean check = false;
        if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getCargo() != null) {
          for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
            if (intel instanceof aEP_MarineTrainIntel && ((aEP_MarineTrainIntel) intel).shouldEnd) {
              check = true;
            }
          }
        }
        return check;
      case "complete":
        if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getCargo() != null) {
          CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
          for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
            if (intel instanceof aEP_MarineTrainIntel && ((aEP_MarineTrainIntel) intel).shouldEnd) {
              cargo.addCommodity("marines", ((aEP_MarineTrainIntel) intel).trainNumber);
              dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_TrainMarine04"),Color.white,Color.yellow, Integer.toString((int)((aEP_MarineTrainIntel) intel).trainNumber));
              ((aEP_MarineTrainIntel) intel).setEnded(true);
            }
          }
        }

    }
    return false;
  }


}
