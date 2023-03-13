package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

import java.util.ArrayList;
import java.util.List;

public class aEP_ChaingunAnimation implements EveryFrameWeaponEffectPlugin
{


  private static final List<String> flames = new ArrayList<>();

  static {
    flames.add("weapons.LBCG_flame");
    flames.add("weapons.LBCG_flame_core");
    flames.add("weapons.LBCG_flame2");
    flames.add("weapons.LBCG_flame2_core");
  }

  private boolean ammoFired = false;
  private final int spriteNum = 0;

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

    ShipAPI ship = weapon.getShip();
    if (engine.isPaused() || weapon.getSlot().isHidden() || !ship.isAlive()) {
      return;
    }
    float chargeLevel = weapon.getChargeLevel();
    if (chargeLevel < 1) {
      weapon.getAnimation().setFrameRate(14);
      ammoFired = false;
    }
    else {
      weapon.getAnimation().setFrameRate(28);
    }
    return;
  }


}
