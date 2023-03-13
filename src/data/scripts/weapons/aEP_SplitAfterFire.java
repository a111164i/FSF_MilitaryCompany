package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;

import java.util.HashMap;
import java.util.Map;

public class aEP_SplitAfterFire implements OnFireEffectPlugin
{
  static final Map<String, Integer[]> mag = new HashMap<>();

  static {
    //to split proj id
    //0，一颗弹丸分裂的数量 1，速度随机变化
    mag.put("aEP_pompom_shot", new Integer[]{1, 10});
    mag.put("aEP_yangji_flak", new Integer[]{1, 20});
  }


  @Override
  public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
    if (!mag.containsKey(projectile.getProjectileSpecId())) return;
    int num = mag.get(projectile.getProjectileSpecId())[0];
    int speedVariant = mag.get(projectile.getProjectileSpecId())[1];
    int i = 0;
    while (i < num) {
      i += 1;
      float weaponSpreadNow = weapon.getCurrSpread();
      DamagingProjectileAPI newProj = (DamagingProjectileAPI) engine.spawnProjectile(weapon.getShip(),
        weapon,
        weapon.getSpec().getWeaponId(),
        projectile.getLocation(),
        weapon.getCurrAngle() + MathUtils.getRandomNumberInRange(-weaponSpreadNow / 2f, weaponSpreadNow / 2f),
        weapon.getShip().getVelocity());
      newProj.setDamageAmount(projectile.getDamageAmount() / num);
      float speedChange = 1f - MathUtils.getRandomNumberInRange(-speedVariant, speedVariant) / 100f;
      newProj.getVelocity().set(newProj.getVelocity().x * speedChange, newProj.getVelocity().y * speedChange);

    }
    engine.removeEntity(projectile);
  }
}
