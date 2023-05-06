package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier;
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.WeaponUtils;

import java.util.HashMap;
import java.util.Map;


public class aEP_BBRadarEffect implements EveryFrameWeaponEffectPlugin
{

  public static final String id = "aEP_BBRadarEffect";
  public static final Map<WeaponAPI.WeaponSize, Float> BONUS_PERCENT = new HashMap<>();
  static {
    BONUS_PERCENT.put(WeaponAPI.WeaponSize.LARGE, 0.45f);
    BONUS_PERCENT.put(WeaponAPI.WeaponSize.MEDIUM, 0.45f);
    BONUS_PERCENT.put(WeaponAPI.WeaponSize.SMALL, 0.45f);
  }

  static final float ANGLE_BEST = 20f;
  static final float ANGLE_TOLERANCE = 90f;
  static final float MIN_BONUS = 0f;

  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
    if(weapon == null) return;
    if(weapon.getShip() != null){
      if(!weapon.getShip().hasListenerOfClass(BB_radar.class) ){
        weapon.getShip().addListener(new BB_radar(weapon));
      }
    }


  }

  static class BB_radar implements WeaponRangeModifier {
    WeaponAPI radar;

    BB_radar(WeaponAPI radar){
      this.radar = radar;
    }

    @Override
    public float getWeaponRangeFlatMod(ShipAPI ship, WeaponAPI weapon) {
      if(weapon.getSpec().getType() == WeaponAPI.WeaponType.ENERGY
              && weapon.getSpec().getSize() == WeaponAPI.WeaponSize.MEDIUM){
        return 100f;
      }
      return 0f;
    }

    @Override
    public float getWeaponRangePercentMod(ShipAPI ship, WeaponAPI weapon) {


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
        float buff = BONUS_PERCENT.get(weapon.getSize()) * buffPercent;
        return buff;
      }

      return 0f;
    }

    @Override
    public float getWeaponRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
      return 1f;
    }
  }



}