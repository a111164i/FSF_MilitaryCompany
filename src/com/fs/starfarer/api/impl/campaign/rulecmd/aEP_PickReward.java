package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
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
  public static Map<String, Float> wep_bp = new HashMap<>();
  public static Map<String, Float> bp_package = new HashMap<>();
  public static List<String> itemToBuy = new ArrayList<>();

  static {
    ship_bp.put("aEP_cap_nuanchi", 300f);
    ship_bp.put("aEP_cap_duiliu", 240f);
    ship_bp.put("aEP_cap_neibo", 240f);

    ship_bp.put("aEP_cru_shanhu", 200f);
    ship_bp.put("aEP_cru_requan", 180f);

    ship_bp.put("aEP_fga_yonglang", 200f);
  }

  static {
    wep_bp.put("aEP_b_l_aa40", 150f);
  }


  static {
    bp_package.put("FSF_openbp", 240f);
    bp_package.put("FSF_weapon_small_bp", 200f);
  }

  static {
    //type, id, price, display_name
    itemToBuy.add(0, "none");//ship_bp, bp_package, wep_bp
    itemToBuy.add(1, "none");
    itemToBuy.add(2, "0");
    itemToBuy.add(3, "");

  }


  @Override
  public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

    //if faction memory map is empty, put a new map in it
    // 用于记录哪些蓝图已经被换过了，放进faction的memkey，换掉一个就从里面移除一个
    MemoryAPI factionMemory = Global.getSector().getFaction("aEP_FSF").getMemoryWithoutUpdate();
    if (factionMemory.get("$ship_bp") == null) {
      factionMemory.set("$ship_bp", ship_bp);
    }
    if (factionMemory.get("$bp_package") == null) {
      factionMemory.set("$bp_package", bp_package);
    }
    if (factionMemory.get("$wep_bp") == null) {
      factionMemory.set("$wep_bp", wep_bp);
    }

    wep_bp = (Map) factionMemory.get("$wep_bp");
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
        if (ship_bp.isEmpty()) {
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward01"));
        }
        for (Map.Entry entry : ship_bp.entrySet()) {
          String id = (String) entry.getKey();
          toAdd.add(Global.getSettings().getHullSpec(id).getNameWithDesignationWithDashClass());//option name
          toAdd.add("aEP_part03_ConfirmBuying_" + id);//option id
          toAdd.add(String.format(aEP_DataTool.txt("aEP_PickReward02"), ship_bp.get(id),Global.getSettings().getCommoditySpec("aEP_remain_part").getName() ));//option tips
        }
        aEP_OpPageManager opts = new aEP_OpPageManager(toAdd, 4, "aEP_offer_remain_part03");
        opts.show(dialog);
        break;

      case "bp_package":
        if (bp_package.isEmpty()) {
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward01"));
        }
        for (Map.Entry entry : bp_package.entrySet()) {
          String id = (String) entry.getKey();
          toAdd.add(Global.getSettings().getSpecialItemSpec(id).getName());
          toAdd.add("aEP_part03_ConfirmBuying_" + id);
          toAdd.add(String.format(aEP_DataTool.txt("aEP_PickReward02"),String.format("%.1f",entry.getValue()),Global.getSettings().getCommoditySpec("aEP_remain_part").getName()));//option tips
        }
        opts = new aEP_OpPageManager(toAdd, 2, "aEP_offer_remain_part03");
        opts.show(dialog);
        break;

      case "wep_bp":
        if (wep_bp.isEmpty()) {
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward01"));
        }
        for (Map.Entry entry : wep_bp.entrySet()) {
          String id = (String) entry.getKey();
          toAdd.add(Global.getSettings().getHullSpec(id).getNameWithDesignationWithDashClass());//option name
          toAdd.add("aEP_part03_ConfirmBuying_" + id);//option id
          toAdd.add(String.format(aEP_DataTool.txt("aEP_PickReward02"), wep_bp.get(id),Global.getSettings().getCommoditySpec("aEP_remain_part").getName() ));//option tips
        }
        opts = new aEP_OpPageManager(toAdd, 4, "aEP_offer_remain_part03");
        opts.show(dialog);
        break;
    }
  }

  private void confirmBuying(String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
    if (!aEP_Tool.checkCargoAvailable(null, null)) {
      return;
    }

    String id = memoryMap.get("local").getString("$option").replace("aEP_part03_ConfirmBuying_", "");
    float remainPartsInCargo = aEP_Tool.Util.getPlayerCargo().getCommodityQuantity("aEP_remain_part");

    if (ship_bp.containsKey(id)) {
      itemToBuy.add(0, Items.SHIP_BP);
      itemToBuy.add(1, id);
      itemToBuy.add(2, ship_bp.get(id) + "");
      itemToBuy.add(3, Global.getSettings().getHullSpec(id).getNameWithDesignationWithDashClass());
    }
    if (bp_package.containsKey(id)) {
      itemToBuy.add(0, "bp_package");
      itemToBuy.add(1, id);
      itemToBuy.add(2, bp_package.get(id) + "");
      itemToBuy.add(3, Global.getSettings().getSpecialItemSpec(id).getName());
    }
    if (wep_bp.containsKey(id)) {
      itemToBuy.add(0, Items.WEAPON_BP);
      itemToBuy.add(1, id);
      itemToBuy.add(2, wep_bp.get(id) + "");
      itemToBuy.add(3, Global.getSettings().getWeaponSpec(id).getWeaponName());
    }

    dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward03"), Color.white, Color.yellow, itemToBuy.get(3));
    dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward02"), Color.white, Color.yellow, itemToBuy.get(2),Global.getSettings().getCommoditySpec("aEP_remain_part").getName());

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
    String displayName = itemToBuy.get(3);


    //price check
    if (cargo.getCommodityQuantity("aEP_remain_part") < price) {
      dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward05"));
      return;
    }

    //add text and item
    switch (type) {
      case "ship_bp":
        cargo.removeCommodity("aEP_remain_part", price);
        cargo.addSpecial(new SpecialItemData(Items.SHIP_BP, id), 1);
        ship_bp.remove(id);
        factionMemory.set("$ship_bp", ship_bp);
        break;

      case "bp_package":
        cargo.removeCommodity("aEP_remain_part", price);
        cargo.addSpecial(new SpecialItemData(id, null), 1);
        bp_package.remove(id);
        factionMemory.set("$bp_package", bp_package);
        break;

      case "wep_bp":
        cargo.removeCommodity("aEP_remain_part", price);
        cargo.addSpecial(new SpecialItemData(Items.WEAPON_BP, id), 1);
        bp_package.remove(id);
        factionMemory.set("$wep_bp", wep_bp);
        break;
    }

    dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward07_2"), Color.white, Color.green, displayName);
    dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_PickReward07"), Color.white, Color.red, price+"", Global.getSettings().getCommoditySpec("aEP_remain_part").getName());

    dialog.getOptionPanel().addOption(aEP_DataTool.txt("aEP_PickReward08"), "aEP_offer_remain_part03");
  }


}