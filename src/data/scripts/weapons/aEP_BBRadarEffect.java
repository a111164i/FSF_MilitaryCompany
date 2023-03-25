package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier;
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier;
import combat.impl.aEP_Buff;
import combat.plugin.aEP_BuffEffect;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.WeaponUtils;

import java.util.HashMap;
import java.util.Map;

import static data.scripts.a111164ModPlugin.BB_Radar_ID;

public class aEP_BBRadarEffect implements EveryFrameWeaponEffectPlugin
{

  public static final String id = "aEP_BBRadarEffect";
  public static final Map<WeaponAPI.WeaponSize, Float> BONUS_PERCENT = new HashMap<>();
  static {
    BONUS_PERCENT.put(WeaponAPI.WeaponSize.LARGE, 0.8f);
    BONUS_PERCENT.put(WeaponAPI.WeaponSize.MEDIUM, 0.8f);
    BONUS_PERCENT.put(WeaponAPI.WeaponSize.SMALL, 0.8f);
  }

  static final float ANGLE_BEST = 15f;
  static final float ANGLE_TOLERANCE = 120f;
  static final float MIN_BONUS = 0.25f;
  static final float DAMAGE_INCREASE_PERCENT = 10f;

  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
    if(weapon.getShip() != null){
      if(!weapon.getShip().hasListenerOfClass(BB_radar.class) ){
        weapon.getShip().addListener(new BB_radar());
      }
    }


  }

  static class BB_radar implements WeaponRangeModifier {
    CombatEntityAPI hittingTarget;
    WeaponAPI radar;

    @Override
    public float getWeaponRangeFlatMod(ShipAPI ship, WeaponAPI weapon) {
      if(weapon.getSpec().getType() == WeaponAPI.WeaponType.ENERGY && weapon.getSpec().getSize() == WeaponAPI.WeaponSize.MEDIUM){
        return 100f;
      }
      return 0f;
    }

    @Override
    public float getWeaponRangePercentMod(ShipAPI ship, WeaponAPI weapon) {

      if (weapon.getSpec().getWeaponId().equals(BB_Radar_ID)) {
        radar = weapon;
        hittingTarget = null;
        for (BeamAPI beam : Global.getCombatEngine().getBeams()) {
          if (beam.getWeapon() == weapon) {
            hittingTarget = beam.getDamageTarget();
            if(hittingTarget instanceof ShipAPI){
              aEP_BuffEffect.addThisBuff(hittingTarget,new Buff((ShipAPI) hittingTarget));
            }
          }
        }
      }
      if(radar == null) return 0f;

      //雷达下线时禁用射程加成
      if(radar.isDisabled()) return 0f;

      //无论雷达是否开火，朝向雷达方向的非PD武器获得射程加成
      if(weapon.hasAIHint(WeaponAPI.AIHints.PD)) return 0f;
      if (aEP_Tool.isNormalWeaponType(weapon, false)) {
        float buffPercent = 0f;
        float angleDist = Math.abs(MathUtils.getShortestRotation(radar.getCurrAngle(), weapon.getCurrAngle()));
        if(angleDist <= ANGLE_BEST) buffPercent = 1f;
        if(angleDist > ANGLE_BEST && angleDist <= ANGLE_TOLERANCE){
          buffPercent = 1f - angleDist/ANGLE_TOLERANCE;
        }
        //保底起效百分之25
        buffPercent = MathUtils.clamp(buffPercent, MIN_BONUS, 1f);
        //帮助一下ai，武器不开火的时候加满范围
        if(ship.getShipAI() != null && weapon.getChargeLevel() == 0f) buffPercent = 1f;
        return BONUS_PERCENT.get(weapon.getSize()) * buffPercent;
      }

      return 0f;
    }

    @Override
    public float getWeaponRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
      return 1f;
    }
  }

  static class Buff extends aEP_Buff {
    ShipAPI target;
    Buff(ShipAPI target){
      setBuffType(id);
      setEntity(target);
      setLifeTime(0.5f);
      setMaxStack(1f);
      setStackNum(1f);
      setRenew(true);
      this.target = target;
    }
    @Override
    public void play() {
      target.getMutableStats().getEnergyDamageTakenMult().modifyPercent(getBuffType(),DAMAGE_INCREASE_PERCENT);
    }

    @Override
    public void readyToEnd() {
      target.getMutableStats().getEnergyDamageTakenMult().unmodify(getBuffType());
    }
  }

}