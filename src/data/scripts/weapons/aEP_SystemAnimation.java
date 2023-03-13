package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import combat.util.aEP_Tool;

public class aEP_SystemAnimation implements EveryFrameWeaponEffectPlugin
{

  private AnimationAPI anime;
  private ShipAPI ship;
  private WeaponAPI weapon;
  private float timer;
  private String id;
  private boolean didPlay = false;


  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {


    ship = weapon.getShip();
    this.weapon = weapon;
    if (engine.isPaused() || weapon.getSlot().isHidden()) {
      return;
    }


    anime = weapon.getAnimation();
    id = weapon.getSpec().getWeaponId();
    float effectLevel = weapon.getShip().getSystem().getEffectLevel();
    timer = timer + amount;

    switch (id) {
      case "aEP_NC_pool":
        if (effectLevel > 0.5f) {
          loopOnce();

        }
        break;
      case "aEP_HL_tower":
        upAndDown(effectLevel > 0.5f);
        break;
    }


    if (!ship.getSystem().isActive()) {
      didPlay = false;
    }

    //timer only start counting after some one did play once
    if (!didPlay) {
      timer = 0f;
    }


  }


  private void loopOnce() {
    if (!didPlay) {
      anime.play();
      didPlay = true;
    }
    if (timer >= anime.getNumFrames() / anime.getFrameRate()) {
      anime.reset();
    }


  }


  //true = up, false = down
  private void upAndDown(boolean upOrDown) {
    if (!didPlay) {
      didPlay = true;
    }

    float frameInterval = 1 / anime.getFrameRate();
    if (timer >= frameInterval) {
      if (upOrDown == true) {
        anime.setFrame((int) aEP_Tool.limitToTop(anime.getFrame() + 1, anime.getNumFrames() - 1, 0));
      }
      else {
        anime.setFrame((int) aEP_Tool.limitToTop(anime.getFrame() - 1, anime.getNumFrames() - 1, 0));
      }
    }
  }
}

        	         	       	       	 
