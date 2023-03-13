package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import combat.util.aEP_Tool;
import combat.util.aEP_DataTool;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;


import static data.hullmods.aEP_HotLoader.EXTRA_SPEED_ON_FIRE;
import static combat.util.aEP_DataTool.txt;

public class aEP_MarkerDissipation extends aEP_BaseHullMod
{


  public static final float BUFFER_AREA = 0.1f;//
  static final Map<HullSize, Float> WEAPON_RANGE_BONUS = new HashMap<>();
  static final float OVERLOAD_TIME_DECREASE = 0.25f;
  static final float DECREASE_SPEED = 0.5f;//
  static final float MAX_OVERLOAD_TIME = 7f;

  private static String id = "aEP_MarkerDissipation";


  boolean showNoModOnUp = false;


  public static float getBufferLevel(ShipAPI ship) {
    if (ship == null || !ship.isAlive() || !ship.getVariant().hasHullMod("aEP_MarkerDissipation")) return 0f;
    aEP_DataTool.floatDataRecorder fluxData = (aEP_DataTool.floatDataRecorder) Global.getCombatEngine().getCustomData().get(ship.getId()+"_"+id);
    if (fluxData == null) return 0f;
    float buffer = fluxData.getTotal();
    float maxFlux = ship.getFluxTracker().getMaxFlux();
    float bufferLevel = aEP_Tool.limitToTop(buffer / (maxFlux * BUFFER_AREA), 1f, 0f);
    //三次方会让曲线更快下降
    return MathUtils.clamp(bufferLevel * bufferLevel * bufferLevel * 2f, 0f, 1f);
  }

  @Override
  public void applyEffectsAfterShipCreationImpl(ShipAPI ship, String id) {
    this.id = id;
    //减少过载时间
    ship.getMutableStats().getOverloadTimeMod().modifyMult(id, 1f - OVERLOAD_TIME_DECREASE);
    //修改护盾贴图
    if (ship.getShield() != null) {
      //set shield inner, outer ring
      ship.getShield().setRadius(ship.getShield().getRadius(),
        Global.getSettings().getSpriteName("aEP_hullstyle", "aEP_shield_inner"),
        Global.getSettings().getSpriteName("aEP_hullstyle", "aEP_shield_outer"));
    }

    if (!ship.hasListenerOfClass(FluxRecorder.class)) {
      aEP_DataTool.floatDataRecorder fluxData = new aEP_DataTool.floatDataRecorder();
      ship.addListener(new FluxRecorder(ship, fluxData));
      Global.getCombatEngine().getCustomData().put(ship.getId()+""+id, fluxData);
    }
  }


  @Override
  public void advanceInCombat(@NotNull ShipAPI ship, float amount) {
    super.advanceInCombat(ship, amount);
    if(ship.getFluxTracker().isOverloaded() && ship.getFluxTracker().getOverloadTimeRemaining() > MAX_OVERLOAD_TIME){
      ship.getFluxTracker().setOverloadDuration(MAX_OVERLOAD_TIME);
    }
  }

  public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
    return null;
  }

  public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
    return true;
  }

  public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f);
    tooltip.addPara("- " + txt("overload_time_reduce") + "{%s}", 5f, Color.white, Color.green, (int) (OVERLOAD_TIME_DECREASE * 100) + "%");
    tooltip.addPara(aEP_DataTool.txt("MD_des04"), Color.gray, 5f);
  }


  void showMod03(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
    TooltipMakerAPI image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec("aEP_HotLoader").getSpriteName(), 48f);
    image.addPara("- " + txt("ammo_regen_speed_up") + "{%s}", 5f, Color.white, Color.green, (int) (EXTRA_SPEED_ON_FIRE * 100) + "%");
    tooltip.addImageWithText(5f);
    showNoModOnUp = false;
  }



  class FluxRecorder implements DamageListener, DamageTakenModifier, AdvanceableListener
  {
    aEP_DataTool.floatDataRecorder fluxData;
    private ShipAPI ship;
    private Object param;
    private DamageAPI damage;

    public FluxRecorder(ShipAPI ship, aEP_DataTool.floatDataRecorder fluxData) {
      this.ship = ship;
      this.fluxData = fluxData;
    }

    @Override
    public void advance(float amount) {
      //get data
      float maxFlux = ship.getFluxTracker().getMaxFlux();
      float softFlux = ship.getFluxTracker().getCurrFlux() - ship.getFluxTracker().getHardFlux();
      float hardFlux = ship.getFluxTracker().getHardFlux();
      float totalDiss = ship.getMutableStats().getFluxDissipation().getModifiedValue();

      //renew after you already got the data from last frame
      fluxData.addRenewData(softFlux);

      //如果检测到本帧软幅能有所下降，开始消减buff区
      //limit soft flux data within buffer area and decrease buffer area
      fluxData.setTotal(aEP_Tool.limitToTop(fluxData.getTotal() - totalDiss * DECREASE_SPEED * amount, maxFlux * BUFFER_AREA, 0f));

      //control which string and boolean to show in stats
      float buffer = fluxData.getTotal();

      //venting check
      if (ship.getFluxTracker().isOverloadedOrVenting()) {
        fluxData.setTotal(0f);
      }
      float bufferLevel = aEP_Tool.limitToTop(buffer / (maxFlux * BUFFER_AREA), 1, 0);
      bufferLevel = MathUtils.clamp(bufferLevel * 2f, 0f, 1f);

      if (Global.getCombatEngine().getPlayerShip() == ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(this.getClass().getSimpleName(),//key
                "graphics/aEP_hullsys/marker_dissipation.png",//sprite name,full, must be registed in setting first
                Global.getSettings().getHullModSpec(id).getDisplayName(),//title
                txt("MD_des01") + (int) (bufferLevel * 100) + "%",//data
                false);//is debuff
      }
      Global.getCombatEngine().getCustomData().put(ship.getId()+"_"+id,fluxData);
    }

    //在 report之前被调用
    //用来记录 DamageAPI
    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
      if(param == null) return null;
      this.param = param;
      this.damage = damage;
      return null;
    }

    @Override
    public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
      //Global.getLogger(this.getClass()).info( "reportDamageApplied");
      //Global.getLogger(this.getClass()).info( result.getDamageToShields() + "");
      if(source == null || damage == null || result.getDamageToShields() <= 0) return;
      if ( (param instanceof BeamAPI && !damage.isForceHardFlux()) || damage.isSoftFlux()) {
        fluxData.setLastFrameData(fluxData.getLast() + result.getDamageToShields());
      }
      return;
    }
  }

}







