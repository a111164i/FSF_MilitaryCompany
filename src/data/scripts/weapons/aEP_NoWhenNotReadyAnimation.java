package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

public class aEP_NoWhenNotReadyAnimation implements EveryFrameWeaponEffectPlugin
{

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
    if(weapon.getAnimation() == null) return;
    weapon.getAnimation().setFrame(0);
    if (weapon.usesAmmo()) {
      if (weapon.getAmmo() < 1) {
        weapon.getAnimation().setFrame(1);
      }
    }

    if (weapon.getCooldownRemaining() > 0) {
      weapon.getAnimation().setFrame(1);
    }

  }


}
