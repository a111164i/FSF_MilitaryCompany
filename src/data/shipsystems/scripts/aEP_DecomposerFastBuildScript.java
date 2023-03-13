package data.shipsystems.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import combat.util.aEP_Tool;

import java.awt.*;

public class aEP_DecomposerFastBuildScript extends BaseShipSystemScript
{
  static final float EXTRA_NUM_MULT = 1;//by mult, to mult
  static final float FRR_DECREASE_SPEED_MOD = 900;//by percent to mult
  static final float NEW_FTR_ATK_MULT = 0.6f;
  static final float NEW_FTR_DEF_MULT = 0.5f;
  static final float NEW_FTR_LIFE = 20f;//

  static final float RATE_COST = 0.15f;


  ShipAPI ship;
  CombatEngineAPI engine = Global.getCombatEngine();

  //aEP_Tool.floatDataRecorder
  boolean didUsed = false;


  @Override
  public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    ship = (ShipAPI) stats.getEntity();
    Color jitterColor = new Color(100, 50, 50, 60);
    ship.setJitterUnder(ship, jitterColor, effectLevel, 24, effectLevel * 40f);

    //先产出额外战机，必须在effectiveLevel为1时设置才起效
    if(effectLevel >= 1f){
      for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
        if (bay.getWing() != null) {
          float rate = Math.max(0.35f, bay.getCurrRate() - RATE_COST);
          bay.setCurrRate(rate);
          //bay.makeCurrentIntervalFast();
          FighterWingAPI wing = bay.getWing();
          if (!ship.getHullSpec().getBuiltInWings().contains(bay.getWing().getSpec().getId()) && effectLevel == 1f) {
            bay.makeCurrentIntervalFast();
            FighterWingSpecAPI spec = bay.getWing().getSpec();
            int addForWing = (int) (wing.getSpec().getNumFighters() * EXTRA_NUM_MULT);
            int maxTotal = spec.getNumFighters() + addForWing;
            int actualAdd = maxTotal - bay.getWing().getWingMembers().size();
            actualAdd = Math.min(spec.getNumFighters(), actualAdd);
            if (actualAdd > 0) {
              bay.setFastReplacements(bay.getFastReplacements() + addForWing);
              bay.setExtraDeployments(actualAdd);
              bay.setExtraDeploymentLimit(maxTotal);
              bay.setExtraDuration(NEW_FTR_LIFE);
            }

          }
        }
      }
      didUsed = true;
    }

    //注意起效后有一小段延迟新战机才出仓，等effectiveLevel下降一会后，再降格所有战机
    if (effectLevel <= 0.1f && didUsed) {
      for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
        if (bay.getWing() != null && ship.getVariant().getNonBuiltInWings().contains(bay.getWing().getWingId())) {
          for (ShipAPI ftr : bay.getWing().getWingMembers()) {
            ftr.getMutableStats().getDamageToFighters().modifyMult(id, NEW_FTR_ATK_MULT);
            ftr.getMutableStats().getDamageToFrigates().modifyMult(id, NEW_FTR_ATK_MULT);
            ftr.getMutableStats().getDamageToDestroyers().modifyMult(id, NEW_FTR_ATK_MULT);
            ftr.getMutableStats().getDamageToCruisers().modifyMult(id, NEW_FTR_ATK_MULT);
            ftr.getMutableStats().getDamageToCapital().modifyMult(id, NEW_FTR_ATK_MULT);
            engine.addFloatingText(ftr.getLocation(),"Degraded",15f,Color.red,ftr,1f,1f);
          }
        }
      }
      didUsed = false;
    }


  }


  @Override  //run once when unapply
  public void unapply(MutableShipStatsAPI stats, String id) {
    ship = (ShipAPI) stats.getEntity();
    didUsed = false;
  }


  @Override
  public StatusData getStatusData(int index, State state, float effectLevel) {
    return null;
  }

  @Override
  public String getInfoText(ShipSystemAPI system, ShipAPI ship) {

    return null;
  }


}