package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;

public class aEP_GlowerAnimation implements EveryFrameWeaponEffectPlugin
{
  float toAlpha = 0f;
  float changeSpeed = 0.1f;

  float blinkTimer = 0f;

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
    ShipSystemAPI system = weapon.getShip().getSystem();
    float level = system.getEffectLevel();
    if (system.getEffectLevel() > 0.1) {
      AnimationAPI anime = weapon.getAnimation();
      anime.setFrame(1);

      blinkTimer = blinkTimer + amount;
      if (blinkTimer > 0.25f) {
        blinkTimer = blinkTimer - 0.25f;
        toAlpha = system.getEffectLevel() - MathUtils.getRandomNumberInRange(0.25f, 0);
      }

      if (toAlpha > level) {
        anime.setAlphaMult(MathUtils.clamp(anime.getAlphaMult() - amount * changeSpeed, 0, 1));
      }
      else {
        anime.setAlphaMult(MathUtils.clamp(anime.getAlphaMult() + amount * changeSpeed, 0, 1));
      }

    }
    else {
      weapon.getAnimation().setFrame(0);
    }

  }


}
