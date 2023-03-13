package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;

public class aEP_EmptyWeaponAnimation implements EveryFrameWeaponEffectPlugin
{
  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

    if (weapon.getShip() == engine.getPlayerShip()) {
      return;
    }


    for (WeaponGroupAPI wg : weapon.getShip().getWeaponGroupsCopy()) {
      wg.toggleOn();
    }
    weapon.getShip().giveCommand(ShipCommand.SELECT_GROUP, null, 0);

  }
}
