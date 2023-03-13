package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import combat.util.aEP_Tool;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class aEP_HLTowerAnimation implements EveryFrameWeaponEffectPlugin
{

  private static final float frameChangeInterval = 0.1f;
  private AnimationAPI anime;
  private float timer = 0;

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {


    ShipAPI ship = weapon.getShip();
    if (engine.isPaused() || weapon.getSlot().isHidden()) {
      return;
    }
    timer = timer + amount;
    if (timer > frameChangeInterval) {
      timer = 0;
    }
    else {
      return;
    }


    anime = weapon.getAnimation();
    float effectLevel = weapon.getShip().getSystem().getEffectLevel();


    if (effectLevel > 0.5) {
      Global.getCombatEngine().addSmoothParticle(aEP_Tool.getExtendedLocationFromPoint(weapon.getSlot().computePosition(ship), weapon.getCurrAngle(), -8f),//Vector2f loc,
        new Vector2f(0f, 0f),//Vector2f vel,
        25f,//float size,
        0.2f,//float brightness
        0.2f,//float duration,
        new Color(220, 120, 120, (int) (240 * effectLevel)));//java.awt.Color color)
      Global.getCombatEngine().addSmoothParticle(aEP_Tool.getExtendedLocationFromPoint(weapon.getSlot().computePosition(ship), weapon.getCurrAngle(), -8f),//Vector2f loc,
        new Vector2f(0f, 0f),//Vector2f vel,
        100f,//float size,
        0.2f,//float brightness
        0.2f,//float duration,
        new Color(220, 120, 120, (int) (80 * effectLevel)));//java.awt.Color color)
      if (anime.getFrame() < anime.getNumFrames() - 1) {
        anime.setFrame(anime.getFrame() + 1);
      }

    }


    if (effectLevel < 0.5 && anime.getFrame() > 0) {
      anime.setFrame(anime.getFrame() - 1);
    }


  }
}

        	         	       	       	 
