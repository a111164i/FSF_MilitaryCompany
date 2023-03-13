package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.hullmods.BallisticRangefinder;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import combat.util.aEP_DataTool;
import combat.util.aEP_Tool;
import  com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.fs.starfarer.api.impl.hullmods.BallisticRangefinder.*;
import static combat.util.aEP_DataTool.txt;

public class aEP_ModuleTargeting extends aEP_BaseHullMod
{
  private static String id = "aEP_ModuleTargeting";
  private static Map mag1 = new HashMap();
  static {
    mag1.put(HullSize.FIGHTER, 0f);
    mag1.put(HullSize.FRIGATE, 10f);
    mag1.put(HullSize.DESTROYER, 20f);
    mag1.put(HullSize.CRUISER, 40f);
    mag1.put(HullSize.CAPITAL_SHIP, 60f);
  }
  private static Map mag2 = new HashMap();
  static {
    mag2.put(HullSize.FIGHTER, 0f);
    mag2.put(HullSize.FRIGATE, 0f);
    mag2.put(HullSize.DESTROYER, 0f);
    mag2.put(HullSize.CRUISER, 35f);
    mag2.put(HullSize.CAPITAL_SHIP, 50f);
  }


  public aEP_ModuleTargeting() {
  }

  /**
   * 使用这个
   * @param ship
   * @param id
   */
  @Override
  public void applyEffectsAfterShipCreationImpl(ShipAPI ship, String id) {
    for(String mId : ship.getVariant().getModuleSlots()){
      ShipVariantAPI m = ship.getVariant().getModuleVariant(mId);
      String syncId = "";
      //禁止模块拥有以下插件
      syncId ="safetyoverrides";
      if(m.hasHullMod(syncId))
        m.removeMod(syncId);
      syncId = "targetingunit";
      if(m.hasHullMod(syncId))
        m.removeMod(syncId);
      syncId = "dedicated_targeting_core";
      if(m.hasHullMod(syncId))
        m.removeMod(syncId);
      syncId = "aEP_TargetSystem";
      if(m.hasHullMod(syncId))
        m.removeMod(syncId);
    }



  }

  /**
   * 在装配页面，module系统还没有初始化，只存在variant的关系，无法获得shipAPI
   * 在进入战场的第一帧加载buff
   * */
  @Override
  public void advanceInCombat(ShipAPI ship, float amount) {
    //只在舰船被部署的第一帧运行一次
    //舰船进入战斗时，才会提供加成
    if(ship.getFullTimeDeployed() > 0.00001f) return;
    for(ShipAPI m : ship.getChildModulesCopy()){
      //aEP_Tool.Util.addDebugLog("in-");
      String syncId = "";
      //防止模块用v排
      m.getMutableStats().getVentRateMult().modifyMult(id, 0);
      syncId = "targetingunit";
      //直接复制的黄定位代码，记得舰船尺寸时用ship，加成时用m
      if(ship.getVariant().hasHullMod(syncId)) {
        m.getMutableStats().getBallisticWeaponRangeBonus().modifyPercent(id, (Float) mag1.get(ship.getHullSize()));
        m.getMutableStats().getEnergyWeaponRangeBonus().modifyPercent(id, (Float) mag1.get(ship.getHullSize()));
      }
      //直接复制的初始定位代码，记得舰船尺寸时用ship，加成时用m
      syncId = "dedicated_targeting_core";
      if(ship.getVariant().hasHullMod(syncId)) {
        m.getMutableStats().getBallisticWeaponRangeBonus().modifyPercent(id, (Float) mag2.get(ship.getHullSize()));
        m.getMutableStats().getEnergyWeaponRangeBonus().modifyPercent(id, (Float) mag2.get(ship.getHullSize()));
      }
    }
  }

  @Override
  public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
    return "";
  }

  @Override
  public boolean shouldAddDescriptionToTooltip(ShipAPI.HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
    return true;
  }

  @Override
  public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
    tooltip.addSectionHeading(txt("aEP_ModuleTargeting01"), Alignment.MID, 5f);
    tooltip.addPara("- "+ "{%s}", 5f, Color.white, Color.green, Global.getSettings().getHullModSpec("dedicated_targeting_core").getDisplayName());
    tooltip.addPara("- "+ "{%s}", 5f, Color.white, Color.green, Global.getSettings().getHullModSpec("targetingunit").getDisplayName());
    tooltip.addSectionHeading(txt("aEP_ModuleTargeting03"), Alignment.MID, 5f);
    tooltip.addPara("- "+ "{%s}", 5f, Color.white, Color.red, Global.getSettings().getHullModSpec("safetyoverrides").getDisplayName());
    tooltip.addPara("- "+ "{%s}", 5f, Color.white, Color.red, Global.getSettings().getHullModSpec("aEP_TargetSystem").getDisplayName());

    tooltip.addPara(aEP_DataTool.txt("aEP_ModuleTargeting02"), Color.gray, 5f);
  }
}
