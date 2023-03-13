package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;


public class aEP_FluxTubeAnimation implements EveryFrameWeaponEffectPlugin
{
  boolean didOnce = false;

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

    if (weapon.getShip() == null) {
      return;
    }
    ShipAPI ship = weapon.getShip();

    //Global.getCombatEngine().addFloatingText(ship.getLocation(),  weapon.getCooldown()+"", 20f ,new Color(0, 100, 200, 240),ship, 0.25f, 120f);

    if (didOnce == false) {
      ship.removeWeaponFromGroups(weapon);
      didOnce = true;
    }


    if (ship.getFluxTracker().getFluxLevel() > 0.5f && weapon.getCooldownRemaining() <= 0 && weapon.getAmmo() > 0) {
      engine.spawnMuzzleFlashOrSmoke(ship,
        weapon.getSlot(),
        weapon.getSpec(),
        0,
        weapon.getCurrAngle());
      engine.spawnProjectile(ship,
        weapon,
        weapon.getSpec().getWeaponId(),
        weapon.getFirePoint(0),//FirePoint得到的是绝对位置
        weapon.getCurrAngle(),
        ship.getVelocity());
      ship.getFluxTracker().increaseFlux(weapon.getFluxCostToFire(), false);
      weapon.setAmmo(weapon.getAmmo() - 1);
      weapon.setRemainingCooldownTo(weapon.getCooldown());
    }

  }
}
