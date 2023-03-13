package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import combat.util.aEP_DataTool;
import combat.util.aEP_Tool;
import campaign.aEP_OpPageManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class aEP_PickReward extends BaseCommandPlugin
{

  public static Map<String, Float> ship_bp = new HashMap<>();
  public static Map<String, Float> bp_package = new HashMap<>();
  public static List<String> itemToBuy = new ArrayList<>();

  static {
    ship_bp.put("aEP_NuanChi", 300f);
    ship_bp.put("aEP_DuiLiu", 200f);
    ship_bp.put("aEP_NeiBo", 240f);

    ship_bp.put("aEP_ShanHu", 200f);
    ship_bp.put("aEP_ReQuan", 180f);

    ship_bp.put("aEP_CengLiu", 120f);

    ship_bp.put("aEP_FanChongLi", 200f);
  }

  static {
    bp_package.put("FSF_openbp", 240f);
  }

  static {
    //type,id,price
    itemToBuy.add(0, "none");//ship_bp, bp_package
    itemToBuy.add(1, "none");
    itemToBuy.add(2, "0");
  }


  @Override
  public boolean execute(java.lang.String ruleId, InteractionDialogAPI dialog, java.util.List<Misc.Token> params, java.util.Map<java.lang.String, MemoryAPI> memoryMap) {

    //if faction memory map is empty, put a new map in it
    MemoryAPI factionMemory = Global.getSector().getFaction("aEP_FSF").getMemoryWithoutUpdate();
    if (factionMemory.get("$ship_bp") == null) {
      factionMemory.set("$ship_bp", ship_bp);
    }
    if (factionMemory.get("$bp_package") == null) {
      factionMemory.set("$bp_package", bp_package);
    }
    ship_bp = (Map) factionMemory.get("$ship_bp");
    bp_package = (Map) factionMemory.get("$bp_package");


    switch (params.get(0).string) {
      case "showPanel":
        showPanel(params.get(1).string, dialog);
        break;
      case "confirmBuying":
        confirmBuying(ruleId, dialog, memoryMap);
        break;
      case "buy":
        buy(ruleId, dialog);
        break;
    }
    return false;
  }


  private void showPanel(String type, InteractionDialogAPI dialog) {
    List<String> toAdd = new ArrayList();
    switch (type) {
      case "ship_bp":
        if (ship_bp.size() == 0) {
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward01"));
        }


        for (Map.Entry entry : ship_bp.entrySet()) {
          String id = (String) entry.getKey();
          toAdd.add(Global.getSettings().getHullSpec(id).getNameWithDesignationWithDashClass());//option name
          toAdd.add("aEP_part03_ConfirmBuying_" + id);//option id
          toAdd.add(String.format(aEP_DataTool.txt("aEP_PickReward02"), ship_bp.get(id),Global.getSettings().getCommoditySpec("aEP_remain_part").getName() ));//option tips
          //dialog.getOptionPanel().addOption(Global.getSettings().getHullSpec(id).getNameWithDesignationWithDashClass(), "aEP_part03_ConfirmBuying_" + id);
          //dialog.getOptionPanel().setTooltip("aEP_part03_ConfirmBuying_" + id,"需要"+ ship_bp.get(id) + "残余零件");
        }

        aEP_OpPageManager opts = new aEP_OpPageManager(toAdd, 4, "aEP_offer_remain_part03");
        opts.show(dialog);
        break;


      case "bp_package":
        if (bp_package.size() == 0) {
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward01"));
        }

        for (Map.Entry entry : bp_package.entrySet()) {
          String id = (String) entry.getKey();
          toAdd.add(Global.getSettings().getSpecialItemSpec(id).getName());
          toAdd.add("aEP_part03_ConfirmBuying_" + id);
          String toAddString = String.format(aEP_DataTool.txt("aEP_PickReward02"),String.format("%.1f",entry.getValue()),Global.getSettings().getCommoditySpec("aEP_remain_part").getName() );
          toAdd.add(toAddString);//option tips
        }
        opts = new aEP_OpPageManager(toAdd, 2, "aEP_offer_remain_part03");
        opts.show(dialog);
        break;

    }
  }

  private void confirmBuying(String ruleId, InteractionDialogAPI dialog, java.util.Map<java.lang.String, MemoryAPI> memoryMap) {
    if (!aEP_Tool.checkCargoAvailable(null, null)) {
      return;
    }

    String id = memoryMap.get("local").getString("$option").replace("aEP_part03_ConfirmBuying_", "");
    float remainPartsInCargo = aEP_Tool.Util.getPlayerCargo().getCommodityQuantity("aEP_remain_part");

    if (ship_bp.containsKey(id)) {
      itemToBuy.add(0, "ship_bp");
      itemToBuy.add(1, id);
      itemToBuy.add(2, ship_bp.get(id) + "");
      dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward03"), Color.white, Color.yellow, Global.getSettings().getHullSpec(id).getNameWithDesignationWithDashClass() + "");
      dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward02"), Color.white, Color.yellow, ship_bp.get(id)+"",Global.getSettings().getCommoditySpec("aEP_remain_part").getName());

    }
    if (bp_package.containsKey(id)) {
      itemToBuy.add(0, "bp_package");
      itemToBuy.add(1, id);
      itemToBuy.add(2, bp_package.get(id) + "");
      dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward04"), Color.white, Color.yellow, Global.getSettings().getSpecialItemSpec(id).getName() + "");
      dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward02"), Color.white, Color.yellow, bp_package.get(id)+"",Global.getSettings().getCommoditySpec("aEP_remain_part").getName());
    }


    dialog.getOptionPanel().addOption("Confirm", "aEP_part03_Buy");
    if (remainPartsInCargo < Float.parseFloat(itemToBuy.get(2))) {
      dialog.getOptionPanel().setEnabled("aEP_part03_Buy", false);
      dialog.getOptionPanel().setTooltip("aEP_part03_Buy", aEP_DataTool.txt("aEP_PickReward05"));

    }
    dialog.getOptionPanel().addOption(aEP_DataTool.txt("aEP_PickReward06"), "aEP_offer_remain_part03");


  }

  private void buy(String ruleId, InteractionDialogAPI dialog) {
    CargoAPI cargo = null;
    MemoryAPI factionMemory = Global.getSector().getFaction("aEP_FSF").getMemoryWithoutUpdate();
    if (aEP_Tool.checkCargoAvailable(null, null)) {
      cargo = aEP_Tool.Util.getPlayerCargo();
    }

    //null check
    if (cargo == null || itemToBuy == null) {
      return;
    }


    String type = itemToBuy.get(0);
    String id = itemToBuy.get(1);
    float price = Float.parseFloat(itemToBuy.get(2));


    //price check
    if (cargo.getCommodityQuantity("aEP_remain_part") < price) {
      dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward05"));
      return;
    }


    //add text and item
    switch (type) {
      case "ship_bp":
        cargo.removeCommodity("aEP_remain_part", price);
        cargo.addSpecial(new SpecialItemData("ship_bp", id), 1);
        dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward07_2"), Color.white, Color.green, Global.getSettings().getHullSpec(id).getNameWithDesignationWithDashClass() + "");
        dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward07"), Color.white, Color.red, ship_bp.get(id).toString(), Global.getSettings().getCommoditySpec("aEP_remain_part").getName());
        ship_bp.remove(id);
        factionMemory.set("$ship_bp", ship_bp);
        break;


      case "bp_package":
        cargo.removeCommodity("aEP_remain_part", price);
        cargo.addSpecial(new SpecialItemData(id, null), 1);
        dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward07_2"), Color.white, Color.green, Global.getSettings().getSpecialItemSpec(id).getName() + "");
        dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward07"), Color.white, Color.red, bp_package.get(id).toString(), Global.getSettings().getCommoditySpec("aEP_remain_part").getName());
        bp_package.remove(id);
        factionMemory.set("$bp_package", bp_package);
        break;
    }


    dialog.getOptionPanel().addOption(aEP_DataTool.txt("aEP_PickReward08"), "aEP_offer_remain_part03");
  }


}