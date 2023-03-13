package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import combat.util.aEP_Tool;
import org.lwjgl.util.vector.Vector2f;


public class aEP_DefenseDroneHatchAnimation implements EveryFrameWeaponEffectPlugin
{

  private static final float findParentWeaponRange = 100f;
  private static final float maxopenState = 30;// 0 == opened, controll opening speed
  private AnimationAPI anime;
  private boolean ammoFired;
  private float openState = 30;// initial open state
  private boolean fullyOpened = true;

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

    ShipAPI ship = weapon.getShip();
    if (engine.isPaused() || weapon.getSlot().isHidden()) {
      return;
    }
    anime = weapon.getAnimation();
    float numOfFrame = anime.getNumFrames();
    // get nearst weapon
    Vector2f myLocation = weapon.getSlot().getLocation();
    float dist = findParentWeaponRange;
    WeaponAPI parentWeapon = null;
    for (WeaponAPI w : ship.getAllWeapons()) {
      if (!(w.getSlot().isDecorative())) {
        Vector2f parentWeaponLocation = w.getSlot().getLocation();
        Vector2f distVector = aEP_Tool.getDistVector(myLocation, parentWeaponLocation);
        if (dist > aEP_Tool.velocity2Speed(distVector).y) {
          parentWeapon = w;
          dist = aEP_Tool.velocity2Speed(distVector).y;
        }
      }
    }

    // null check
    if (parentWeapon == null) {
      return;
    }

    if (parentWeapon.isFiring() && openState > 0 && fullyOpened == true) {
      fullyOpened = false;
    }

    if (openState < 0) {
      openState = 0;
      fullyOpened = true;
    }
    if (openState > maxopenState) {
      openState = maxopenState;
    }


    //controll open stats
    if (parentWeapon.isFiring() || !fullyOpened) {
      openState = openState - 2f;
    }
    if (!parentWeapon.isFiring()) {
      openState = openState + 1f;
    }


    //controll animation

    if (openState <= 5 * maxopenState / numOfFrame) {
      if (openState <= 4 * maxopenState / numOfFrame) {
        if (openState <= 3 * maxopenState / numOfFrame) {
          if (openState <= 2 * maxopenState / numOfFrame) {
            if (openState <= 1 * maxopenState / numOfFrame) {
              anime.setFrame(4);
              return;
            }
            anime.setFrame(3);
            return;
          }
          anime.setFrame(2);
          return;
        }
        anime.setFrame(1);
        return;
      }
      anime.setFrame(0);
      return;
    }


    //if ship is destroyed, set sprite transparent
    if (weapon.getShip().isHulk()) {
      anime.setAlphaMult(0.5f);
    }
  }
}

        	         	       	       	 
