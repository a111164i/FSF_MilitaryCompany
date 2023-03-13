package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

import java.awt.*;

public class aEP_GlowDeco implements EveryFrameWeaponEffectPlugin
{
  int effective = 0;

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {


    weapon.getSprite().setColor(new Color(200, 100, 100, effective));
  }
}
