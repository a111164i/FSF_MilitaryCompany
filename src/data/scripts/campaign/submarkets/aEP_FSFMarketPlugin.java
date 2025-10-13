package data.scripts.campaign.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import combat.util.aEP_ID;
import data.scripts.FSFModPlugin;
import lunalib.lunaSettings.LunaSettings;

import java.util.*;

import static combat.util.aEP_DataTool.txt;
import static data.scripts.campaign.intel.aEP_CruiseMissileLoadIntel.S1_ITEM_ID;
import static data.scripts.campaign.intel.aEP_CruiseMissileLoadIntel.S2_ITEM_ID;

public class aEP_FSFMarketPlugin extends BaseSubmarketPlugin {

  static float REQUIRE_RELATIONSHIP = 80f;
  public static String ID = "aEP_FSFMarket";

  @Override
  public void init(SubmarketAPI submarket) {
    this.submarket = submarket;
    this.market = submarket.getMarket();
    minSWUpdateInterval = 60f;
    //被创建时立刻刷新一次
    sinceSWUpdate = 99999999f;

    //如果启用了跳过主线，刷新出特殊武器
    if(FSFModPlugin.isLunalibEnabled){
      boolean shouldSkip = LunaSettings.getBoolean("FSF_MilitaryCorporation","aEP_SettingMissionSkipAwm");
      if(shouldSkip){
        getCargo().addWeapons("aEP_b_m_lighting", 4);
        getCargo().addWeapons("aEP_b_l_railwaygun", 1);
        int numShenduMk2 = 1;
        while (numShenduMk2 > 0){
          getCargo().addMothballedShip(FleetMemberType.SHIP,"aEP_des_shendu_mk2_Standard",null);
          numShenduMk2 -= 1;
        }
      }
    }
  }

  @Override
  public void updateCargoPrePlayerInteraction() {
    sinceLastCargoUpdate = 0f;
    CargoAPI marketCargo = submarket.getCargo();

    //如果可以刷新就开始加东西
    if (!okToUpdateShipsAndWeapons()) return;
    sinceSWUpdate = 0f;

    //在清理掉老东西以前，读取一下是否清理掉了某些东西
    CargoAPI beforeClean = getCargo().createCopy();
    //copyCargo并不会带着cargo里面的getMothballedShips一起复制
    List<FleetMemberAPI> ships = getCargo().getMothballedShips().getMembersListCopy();
    pruneWeapons(0f);
    pruneShips(0f);
    getCargo().clear();
    getCargo().getMothballedShips().clear();

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


    addAdvanceShipList();
    addCruiseMissile();
    addMissionReward(beforeClean, ships);


    getCargo().sort();
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
      if (!fsfLevel.isAtWorst(RepLevel.NEUTRAL)) return txt("aEP_Market01");
      return txt("aEP_Market00");
    }
  }

  //普通道具 是否可以买入卖出，调用于两方的cargo
  @Override
  public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
    if(commodityId == null || action == null) return true;

    float fsfLevel = Global.getSector().getFaction("aEP_FSF").getRelToPlayer().getRel();
    if(action == TransferAction.PLAYER_BUY){
      return false;
    }
    if(action == TransferAction.PLAYER_SELL){
      return true;
    }
    return true;
  }

  //特殊物品 是否可以买入卖出，调用于两方的cargo
  @Override
  public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
    //这2行必须加上，如果是普通的道具就直接走上面那个，因为这个函数会被每一个stack调用
    if(stack == null || action == null) return true;
    if (stack.isCommodityStack()) return isIllegalOnSubmarket((String) stack.getData(), action);

    float fsfLevel = Global.getSector().getFaction("aEP_FSF").getRelToPlayer().getRel();
    if(action == TransferAction.PLAYER_BUY) {
      return false;
    }
    if(action == TransferAction.PLAYER_SELL) {
      return true;
    }
    return true;
  }

  //普通和特殊道具通用的买入卖出文本
  @Override
  public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
    if(stack == null || action == null) return "";

    float fsfLevel = Global.getSector().getFaction("aEP_FSF").getRelToPlayer().getRel();
    if(action == TransferAction.PLAYER_BUY){
      return "";
    }

    if(action == TransferAction.PLAYER_SELL){
      return txt("aEP_Market00");
    }
    return "";
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

  @Override
  public boolean isEnabled(CoreUIAPI ui) {

    if(Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF).getRelToPlayer().getRel() < REQUIRE_RELATIONSHIP/100f){
      return false;
    }
    return true;
  }

  @Override
  public String getTooltipAppendix(CoreUIAPI ui) {
    if(Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF).getRelToPlayer().getRel() < REQUIRE_RELATIONSHIP/100f){
      return String.format(txt("aEP_Market01"), (int)REQUIRE_RELATIONSHIP +"" );
    }
    return "";
  }

  @Override
  public String getName() {
    return super.getName();
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

  private void addAdvanceShipList(){
    for(String shipSpecId : submarket.getFaction().getKnownShips()){
      ShipHullSpecAPI spec = Global.getSettings().getHullSpec(shipSpecId);
      if(spec.hasTag("FSF_advancebp")){
        ShipVariantAPI emptyVariant = Global.getSettings().createEmptyVariant(spec.getHullId()+ "_empty",spec);
        FleetMemberAPI member = null;
        member = Global.getFactory().createFleetMember(FleetMemberType.SHIP,emptyVariant);
        member.getRepairTracker().setMothballed(true);
        member.getRepairTracker().setCR(0.5f);
        getCargo().getMothballedShips().addFleetMember(member);
      }
    }
  }

  private void addCruiseMissile(){
    getCargo().addSpecial(new SpecialItemData(S1_ITEM_ID,null), 1f);
    getCargo().addSpecial(new SpecialItemData(S2_ITEM_ID,null), 2f);
  }

 private void addMissionReward(CargoAPI beforeClean, List<FleetMemberAPI> ships ){
    float numLightingGun = beforeClean.getQuantity(CargoAPI.CargoItemType.WEAPONS,"aEP_b_m_lighting" );
    float numRailwayGun = beforeClean.getQuantity(CargoAPI.CargoItemType.WEAPONS,"aEP_b_l_railwaygun" );
    int numShenduMk2 = 0;
    for(FleetMemberAPI m : ships){
      if(m.getVariant().getHullSpec().getHullId().contains("aEP_des_shendu_mk2")){
        numShenduMk2 +=1;
      }
    }


    getCargo().addWeapons("aEP_b_m_lighting", (int) numLightingGun);
    getCargo().addWeapons("aEP_b_l_railwaygun", (int) numRailwayGun);
    while (numShenduMk2 > 0){
      getCargo().addMothballedShip(FleetMemberType.SHIP,"aEP_des_shendu_mk2_Standard",null);
      numShenduMk2 -= 1;
    }

 }
}
