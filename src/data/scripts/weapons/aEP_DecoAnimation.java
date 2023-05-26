package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import combat.util.aEP_AnimationController;
import combat.util.aEP_DecoGlowController;
import combat.util.aEP_DecoMoveController;
import combat.util.aEP_DecoRevoController;

public class aEP_DecoAnimation implements EveryFrameWeaponEffectPlugin
{

  WeaponAPI weapon;
  aEP_DecoMoveController decoMoveController;
  aEP_DecoRevoController decoRevoController;
  aEP_DecoGlowController decoGlowController;
  aEP_AnimationController animeController;


  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
    this.weapon = weapon;
    ShipAPI ship = weapon.getShip();
    if (ship == null) return;
    if (animeController == null && weapon.getAnimation() != null)
      animeController = new aEP_AnimationController(weapon, weapon.getAnimation().getFrameRate());
    if (decoMoveController == null) decoMoveController = new aEP_DecoMoveController(weapon);
    if (decoRevoController == null) decoRevoController = new aEP_DecoRevoController(weapon);
    if (decoGlowController == null) decoGlowController = new aEP_DecoGlowController(weapon);

    //control animation
    if (animeController != null) animeController.advance(amount);
    decoRevoController.advance(amount);
    decoMoveController.advance(amount);
    decoGlowController.advance(amount);
  }


  public void setMoveToLevel(float toLevel) {
    if(decoMoveController != null){
      decoMoveController.setToLevel(toLevel);
    }
  }

  public void setMoveToSideLevel(float toLevel) {
    if (decoMoveController != null) {
      decoMoveController.setToSideLevel(toLevel);
    }
  }

  public void setRevoToLevel(float toLevel) {
    if (decoRevoController != null) {
      decoRevoController.setToLevel(toLevel);
    }
  }

  public void setGlowToLevel(float toLevel) {
    if (decoGlowController != null) {
      decoGlowController.setToLevel(toLevel);
    }
  }

  public void setGlowEffectiveLevel(float effectiveLevel) {
    if (decoGlowController != null) {
      decoGlowController.setEffectiveLevel(effectiveLevel);
    }
  }

  public void setFrameRate(float frameRate) {
    if (animeController != null) {
      animeController.setSpeed(frameRate);
    }
  }

  public void setAnimeToLevel(float tolevel) {
    if (animeController != null) {
      animeController.setToLevel(tolevel);
    }
  }

  public aEP_AnimationController getAnimeController() {
    return animeController;
  }

  public aEP_DecoMoveController getDecoMoveController() {
    return decoMoveController;
  }

  public aEP_DecoRevoController getDecoRevoController() {
    return decoRevoController;
  }

  public aEP_DecoGlowController getDecoGlowController() {
    return decoGlowController;
  }
}

        	         	       	       	 
