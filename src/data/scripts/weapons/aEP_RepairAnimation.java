package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import combat.util.aEP_Tool;

public class aEP_RepairAnimation implements EveryFrameWeaponEffectPlugin
{

  private static final int NUM_OF_FRAMES = 10;
  private static final float FRAME_PER_SEC = 30;
  private AnimationAPI anime;
  private float controlTime = 0;
  //private Color color;

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {


    if (engine.isPaused() || weapon.getSlot().isHidden()) {
      return;
    }
    anime = weapon.getAnimation();
    if (weapon.getShip().getSystem().getEffectLevel() > 0.1) {
      controlTime = controlTime + amount;
    }
    else {
      controlTime = controlTime - amount;
    }


    if (weapon.isFiring()) {
      controlTime = controlTime + 2 * amount;
      controlTime = aEP_Tool.limitToTop(controlTime, (NUM_OF_FRAMES - 3) / FRAME_PER_SEC - 0.01f, 0);
    }


    controlTime = aEP_Tool.limitToTop(controlTime, NUM_OF_FRAMES / FRAME_PER_SEC - 0.01f, 0);
    anime.setFrame((int) (controlTime * FRAME_PER_SEC));
    return;
  }
}

        	         	       	       	 
