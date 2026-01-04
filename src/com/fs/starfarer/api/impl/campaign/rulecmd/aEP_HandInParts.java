package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.utils.aEP_DataTool;

import java.awt.*;
import java.util.Map;

public class aEP_HandInParts extends BaseCommandPlugin
{

  static float selectValue;
  String commodity;
  float num;
  String returnCommodity;
  float returnNum;
  String extra;

  /**
   * @param params commodity上交什么, quantity上交多少（可以为负数改为获得）, return commodity返还什么, return quantity返还多少, extra其他功能
   */
  @Override
  public boolean execute(String ruleId, InteractionDialogAPI dialog, java.util.List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

    commodity = params.get(0).string;
    num = 0f;
    if (!params.get(1).type.equals(Misc.TokenType.VARIABLE)) {
      num = Float.parseFloat(params.get(1).string);
    }
    else {
      num = params.get(1).getFloat(memoryMap);
    }
    returnCommodity = params.get(2).string;
    returnNum = Float.parseFloat(params.get(3).string);
    extra = "none";
    if (params.size() >= 5) {
      extra = params.get(4).string;
    }


    //dialog.getTextPanel().addPara(selectorDialog.getOptionPanel().getSelectorValue("aEP_HandInSelector") + "");

    if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getCargo() != null) {
      //get cargo
      CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
      switch (extra) {
        case "chooseNum":  //选择上交多少个，每上交一个返还returnNum个
          if (selectValue == 0f) {
            CargoAPI copy = Global.getFactory().createCargo(false);
            //copy.addAll(cargo);
            //copy.setOrigSource(playerCargo);
            for (CargoStackAPI stack : cargo.getStacksCopy()) {
              CommoditySpecAPI spec = stack.getResourceIfResource();
              if (spec != null && spec.getId().equals(commodity)) {
                copy.addFromStack(stack);
              }
            }
            copy.sort();
            dialog.showCargoPickerDialog("Select",//title
              "Confirm",//comfirm text
              "Cancel",//cancel text
              false,//is small size?
              310f,//width
              copy,//cargo
              new CargoListener(cargo, ruleId, dialog, memoryMap, "", false));
            return true;
          }
          else {
            num = selectValue;
            returnNum = num * returnNum;
            selectValue = 0f;
          }
          break;
        case "chooseToBuy":  //选择获得多少个（Cargo页面自动把selectValue设置为负数），每获得一个返还returnNum个（如果要失去记得写成负数）
          if (selectValue == 0f) {
            CargoAPI copy = Global.getFactory().createCargo(false);
            //copy.addAll(cargo);
            //copy.setOrigSource(playerCargo);
            copy.addCommodity(commodity, 1000);
            copy.sort();
            dialog.showCargoPickerDialog("Select",//title
              "Confirm",//comfirm text
              "Cancel",//cancel text
              false,//is small size?
              310f,//width
              copy,//cargo
              new CargoListener(cargo, ruleId, dialog, memoryMap, "", true));
            return true;
          }
          else {
            num = selectValue;
            returnNum = num * returnNum;
            selectValue = 0f;
          }
          break;
        case "showNum": //仅显示当前拥有多少个commodity，除了community其他都写none和0
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts01"), Color.white, Color.yellow, (int) cargo.getCommodityQuantity(commodity) + " ", Global.getSettings().getCommoditySpec(commodity).getName() + "");
          return true;
      }

      //-----------------------------------------------------------------------------------//
      //检测上交物品的类型单独处理，星币需要单开一栏，它不算物品（依然可以通过负数变成增加）
      //检索顺序为 普通物品，无data的特殊物品，武器，联队LPC
      if (commodity.equals("credits")) {
        if (cargo.getCredits().get() < num) {
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts02"));
          return false;
        }
        cargo.getCredits().subtract(num);
        dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts03"), Color.white, Color.red, (int) num + "");
      }
      else if (!commodity.equals("none") && !commodity.equals("credits")) {
        if (cargo.getCommodityQuantity(commodity) < num) {
          String commdityName = Global.getSettings().getCommoditySpec(commodity).getName();
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts04"), Color.white, Color.red, commdityName);
          return false;
        }
        else {
          // commodity的num大于0是上交，小于0是获得
          if (num >= 0) {
            SettingsAPI settings = Global.getSettings();
            String commdityName = "";
            if (settings.getCommoditySpec(commodity) != null) {
              cargo.removeCommodity(commodity, num);
              commdityName = Global.getSettings().getCommoditySpec(commodity).getName();
            }
            else if (settings.getSpecialItemSpec(commodity) != null) {
              cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, commodity, num);
              commdityName = Global.getSettings().getSpecialItemSpec(commodity).getName();
            }
            else if (settings.getWeaponSpec(commodity) != null) {
              cargo.removeItems(CargoAPI.CargoItemType.WEAPONS, commodity, num);
              commdityName = Global.getSettings().getWeaponSpec(commodity).getWeaponName();
            }
            else if (settings.getFighterWingSpec(commodity) != null) {
              cargo.removeItems(CargoAPI.CargoItemType.FIGHTER_CHIP, commodity, num);
              commdityName = Global.getSettings().getFighterWingSpec(commodity).getWingName();
            }
            dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts05"), Color.white, Color.red, (int) num + "", commdityName + "");
          }
          else {
            SettingsAPI settings = Global.getSettings();
            String commdityName = "";
            // num是负数，记得取反变成正数处理
            if (settings.getCommoditySpec(commodity) != null) {
              cargo.addCommodity(commodity, -num);
              commdityName = Global.getSettings().getCommoditySpec(commodity).getName();
            }
            else if (settings.getSpecialItemSpec(commodity) != null) {
              cargo.addSpecial(new SpecialItemData(commodity, null), -num);
              commdityName = Global.getSettings().getSpecialItemSpec(commodity).getName();
            }
            else if (settings.getWeaponSpec(commodity) != null) {
              cargo.addItems(CargoAPI.CargoItemType.WEAPONS, commodity, -num);
              commdityName = Global.getSettings().getWeaponSpec(commodity).getWeaponName();
            }
            else if (settings.getFighterWingSpec(commodity) != null) {
              cargo.addItems(CargoAPI.CargoItemType.FIGHTER_CHIP, commodity, -num);
              commdityName = Global.getSettings().getFighterWingSpec(commodity).getWingName();
            }
            dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts06"), Color.white, Color.green, (int) -num + "", commdityName + "");
          }

        }
      }

      //对于蓝图这种有data的特殊物品，使用extra处理
      switch (extra) {
        case "ship_bp":
          cargo.addSpecial(new SpecialItemData("ship_bp", returnCommodity), returnNum);
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts07"), Color.white, Color.green, Global.getSettings().getHullSpec(returnCommodity).getNameWithDesignationWithDashClass() + "");
          returnCommodity = "none";
          break;
        case "wp_bp":
          cargo.addSpecial(new SpecialItemData("weapon_bp", returnCommodity), returnNum);
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts07"), Color.white, Color.green, Global.getSettings().getWeaponSpec(returnCommodity).getWeaponName() + "");
          returnCommodity = "none";
          break;

      }

      //-----------------------------------------------------------------------------------//
      //返还物品，物品检测的逻辑较为简单.所以单纯只是要给奖励物品的话，通过上交物品的数量改为负数来实现获得，而返还物品写none 0
      if (returnCommodity.equals("credits")) {
        if (returnNum < 0) {
          returnNum = Math.abs(returnNum);
          cargo.getCredits().subtract(returnNum);
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts03"), Color.white, Color.red, (int) returnNum + "");
        } else {
          cargo.getCredits().add(returnNum);
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts03_2"), Color.white, Color.green, (int) returnNum + "");
        }
      } else if (!returnCommodity.equals("none")) {
        if (returnNum < 0) {
          returnNum = Math.abs(returnNum);
          cargo.removeCommodity(returnCommodity, returnNum);
          String returnCommdityName = Global.getSettings().getCommoditySpec(returnCommodity).getName();
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts05"), Color.white, Color.red, (int) returnNum + "", returnCommdityName + "");
        } else {
          cargo.addCommodity(returnCommodity, returnNum);
          String returnCommdityName = Global.getSettings().getCommoditySpec(returnCommodity).getName();
          dialog.getTextPanel().addPara(aEP_DataTool.txt("aEP_HandInParts06"), Color.white, Color.green, (int) returnNum + "", returnCommdityName + "");
        }

      }


      //add in Handed memory
      switch (commodity) {
        case "aEP_remain_part":
          if (Global.getSector().getFaction("aEP_FSF").getMemory().get("$aEP_remain_part_HandedInPast") != null) {
            int handedPartsInPast = ((Float) Global.getSector().getFaction("aEP_FSF").getMemory().get("$aEP_remain_part_HandedInPast")).intValue();
            //Global.getSector().getMemory().set("$aEP_remain_part_HandedInPast", (handedPartsInPast + numToHandIn));
            Global.getSector().getFaction("aEP_FSF").getMemory().set("$aEP_remain_part_HandedInPast", (handedPartsInPast + num));
          }
          else {
            //Global.getSector().getMemory().set("$aEP_remain_part_HandedInPast", numToHandIn);
            Global.getSector().getFaction("aEP_FSF").getMemory().set("$aEP_remain_part_HandedInPast", num);
          }
          break;
      }
    }
    return false;
  }

  class CargoListener implements CargoPickerListener
  {
    CargoAPI playerCargo;
    String ruleId;
    InteractionDialogAPI dialog;
    Map<String, MemoryAPI> memoryMap;
    String params;
    boolean isToBuy;

    CargoListener(CargoAPI playerCargo, String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, String params, boolean isToBuy) {
      this.playerCargo = playerCargo;
      this.ruleId = ruleId;
      this.dialog = dialog;
      this.memoryMap = memoryMap;
      this.params = params;
      this.isToBuy = isToBuy;
    }

    public void pickedCargo(CargoAPI cargo) {
      cargo.sort();
      for (CargoStackAPI stack : cargo.getStacksCopy()) {
        if (stack.getCommodityId().equals(commodity)) {
          selectValue = stack.getSize();
          if (isToBuy) {
            selectValue = -selectValue;
          }
        }
      }
      dialog.getOptionPanel().clearOptions();
      dialog.getOptionPanel().addOption("Continue", ruleId);
    }

    public void cancelledCargoSelection() {
      selectValue = 0f;
    }

    public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
      FactionAPI faction = Global.getSector().getFaction("aEP_FSF");

      float pad = 3f;
      float opad = 10f;
      float width = 310f;

      panel.setParaFontOrbitron();
      panel.addPara(Misc.ucFirst(faction.getDisplayName()), faction.getBaseUIColor(), 1f);
      panel.setParaFontDefault();
      panel.addImage(faction.getLogo(), width * 1f, 3f);

      float num = 0f;

      for (CargoStackAPI stack : cargo.getStacksCopy()) {
        //碰到过，做个判空以防万一
        if(stack.getCommodityId() == null) continue;
        if (stack.getCommodityId().equals(commodity)) {
          num = stack.getSize();
        }
        //playerCargo.removeItems(stack.getType(), stack.getData(), stack.getSize());
      }
      num = returnNum * num;

      if (returnCommodity.equals("credits")) {
        if (isToBuy) {
          panel.addPara(aEP_DataTool.txt("aEP_HandInParts03"), opad, Color.white, Color.red, (int) num + "");
        }
        else {
          panel.addPara(aEP_DataTool.txt("aEP_HandInParts03_2"), opad, Color.white, Color.green, (int) num + "");
        }

      }
      else {
        if (!returnCommodity.equals("none")) {
          String returnCommdityName = Global.getSettings().getCommoditySpec(returnCommodity).getName();
          if (isToBuy) {
            panel.addPara(aEP_DataTool.txt("aEP_HandInParts05"), opad, Color.white, Color.red, (int) num + "", returnCommdityName + "");
          }
          else {
            panel.addPara(aEP_DataTool.txt("aEP_HandInParts06"), opad, Color.white, Color.green, (int) num + "", returnCommdityName + "");
          }
        }
      }


    }
  }
}