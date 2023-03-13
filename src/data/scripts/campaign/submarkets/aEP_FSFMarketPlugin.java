package data.scripts.campaign.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import combat.util.aEP_DataTool;
import combat.util.aEP_ID;
import org.lazywizard.lazylib.MathUtils;

import java.util.*;

public class aEP_FSFMarketPlugin extends BaseSubmarketPlugin
{


  @Override
  public void init(SubmarketAPI submarket) {
    this.submarket = submarket;
    this.market = submarket.getMarket();
  }

  @Override
  public void updateCargoPrePlayerInteraction() {
    sinceLastCargoUpdate = 0f;
    CargoAPI marketCargo = submarket.getCargo();

    //如果可以刷新就开始加东西
    if (!okToUpdateShipsAndWeapons()) return;
    sinceSWUpdate = 0f;
    pruneWeapons(0f);
    pruneShips(0f);

    //加入所有玩家没学会的势力专用船插
    for (HullModSpecAPI mod : Global.getSettings().getAllHullModSpecs()) {
      if(mod.isHidden())continue;
      if(!mod.hasTag("FSF")) continue;
      String modName = mod.getId();
      if (!Global.getSector().getPlayerFleet().getFaction().getKnownHullMods().contains(modName)) {
        if (!cargoAlreadyHasMod(modName)) {
          marketCargo.addHullmods(modName, 1);
        }
      }
    }

    getCargo().getMothballedShips().clear();
    getCargo().sort();
  }

  @Override
  public boolean shouldHaveCommodity(CommodityOnMarketAPI com) {
    return super.shouldHaveCommodity(com);
  }

  //舰队是否可以买入卖出，调用于两方的cargo
  @Override
  public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
    if(member == null || action == null) return false;
    if(action == TransferAction.PLAYER_BUY) {
      return false;
    }
    else {
      return true;
    }
  }

  //舰队买入卖出文本
  @Override
  public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
    if(member == null || action == null) return "";
    FactionAPI player = Global.getSector().getPlayerFaction();
    RepLevel fsfLevel = Global.getSector().getFaction("aEP_FSF").getRelationshipLevel(player);
    if(action == TransferAction.PLAYER_BUY){
      return "";
    }else {
      if (!fsfLevel.isAtWorst(RepLevel.NEUTRAL)) return aEP_DataTool.txt("aEP_Market01");
      return aEP_DataTool.txt("aEP_Market00");
    }
  }

  //普通道具是否可以买入卖出，调用于两方的cargo
  @Override
  public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
    if(commodityId == null || action == null) return true;
    if(action == TransferAction.PLAYER_BUY) {
      return false;
    }
    else {
      if(commodityId.equals("aEP_remain_part")) return false;
      return true;
    }
  }

  //特殊道具是否可以买入卖出，调用于两方的cargo
  @Override
  public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
    //这2行必须加上，如果是普通的道具就直接走上面那个，因为这个函数会被每一个stack调用
    if(stack == null || action == null) return true;
    if (stack.isCommodityStack()) return isIllegalOnSubmarket((String) stack.getData(), action);

    if(action == TransferAction.PLAYER_BUY) {
      return false;
    }
    else {
      return true;
    }
  }

  //通用的买入卖出文本
  @Override
  public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
    if(stack == null || action == null) return "";
    FactionAPI player = Global.getSector().getPlayerFaction();
    RepLevel fsfLevel = Global.getSector().getFaction("aEP_FSF").getRelationshipLevel(player);
    if(action == TransferAction.PLAYER_BUY){
      return "";
    }else {
      if (!fsfLevel.isAtWorst(RepLevel.NEUTRAL)) return aEP_DataTool.txt("aEP_Market01");
      return aEP_DataTool.txt("aEP_Market00");
    }

  }

  @Override
  public float getTariff() {
    return 1f;
  }

  @Override
  public boolean isHidden() {
    if(submarket.getFaction().getId().equals("aEP_FSF")){
      return false;
    }
    return true;
  }

  @Override
  public boolean isParticipatesInEconomy() {
    return false;
  }


  private void addNormalShipList(){
    FactionDoctrineAPI doctrineOverrided = submarket.getFaction().getDoctrine().clone();

    doctrineOverrided.setCombatFreighterProbability(0.25f);
    doctrineOverrided.setShipSize(5);
    doctrineOverrided.setShipQuality(5);


    doctrineOverrided.setCarriers(3);
    doctrineOverrided.setWarships(3);
    doctrineOverrided.setPhaseShips(3);
    doctrineOverrided.setFleets(4);

    //这3个东西设为0以后，上升流mk1的爆率会和mk3一样低
    doctrineOverrided.setCombatFreighterProbability(0f);
    doctrineOverrided.setCombatFreighterCombatUseFraction(0f);
    doctrineOverrided.setCombatFreighterCombatUseFractionWhenPriority(0f);


    //addShip("aEP_ShangShengLiu_mk3_Standard",false,5);
    addShips(submarket.getFaction().getId(),//faction id
            100f, // combat
            40f, // freighter
            40f, // tanker
            20f, // transport
            20f, // liner
            20f, // utilityPts
            1.5f, // qualityOverride
            0f, // qualityMod
            ShipPickMode.PRIORITY_ONLY,//FactionAPI.ShipPickMode modeOverride, at what priority to pick ship in all availables
            doctrineOverrided, 20);// FactionDoctrineAPI doctrineOverride, at what fraction to pick ship among all availables
  }
}
