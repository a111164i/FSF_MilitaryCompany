package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;

import java.util.HashMap;
import java.util.Map;

public class aEP_RadarAnimation implements EveryFrameWeaponEffectPlugin {
  /**
   * 大于0就是转向鼠标指向
   * 小于0是自动旋转
   * */
  static Map<String, Float> ROTATE_RATE = new HashMap<>();
  static {
    ROTATE_RATE.put("aEP_cap_duiliu_radar",-60f);
    ROTATE_RATE.put("aEP_cap_nuanchi_radar",-180f);
    ROTATE_RATE.put("aEP_ftr_ftr_helicop_fan",-540f);
    ROTATE_RATE.put("aEP_ftr_ut_supply_fan",-540f);
  }

  public float usingRate = -999f;
  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
    if (weapon.getShip() == null) return;
    if (aEP_Tool.Util.isDead(weapon.getShip())) return;

    if(usingRate == -999f){
      if(ROTATE_RATE.containsKey(weapon.getId())){
        usingRate = ROTATE_RATE.get(weapon.getId());
      }
      return;
    }


    if(usingRate > 0){
      float toAngle = VectorUtils.getAngle(weapon.getLocation(), weapon.getShip().getMouseTarget());
      float angleDist = MathUtils.getShortestRotation(weapon.getCurrAngle(), toAngle);
      if (angleDist > 0) {
        weapon.setCurrAngle(weapon.getCurrAngle() + Math.min(angleDist, usingRate * amount));
      }
      else {
        weapon.setCurrAngle(weapon.getCurrAngle() - Math.min(-angleDist, usingRate * amount));
      }
    }else {
      weapon.setCurrAngle(aEP_Tool.angleAdd(weapon.getCurrAngle(),usingRate * amount));
    }
  }


}
                                                 