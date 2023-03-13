package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import combat.impl.VEs.aEP_MovingSprite;
import combat.plugin.aEP_CombatEffectPlugin;
import data.hullmods.aEP_MarkerDissipation;

import combat.util.aEP_Tool;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class aEP_MarkerAnimation implements EveryFrameWeaponEffectPlugin
{

  private static final Color glow = new Color(240, 240, 240, 1);
  private static final List smokeSprites = new ArrayList();
  private static final List<Color> smokeColor = new ArrayList();

  static {
    if (smokeSprites.isEmpty()) {
      smokeSprites.add("aEP_FX.particle_smoke01");
      smokeSprites.add("aEP_FX.particle_smoke02");
      smokeSprites.add("aEP_FX.particle_smoke03");
    }
  }

  static {
    if (smokeColor.isEmpty()) {
      smokeColor.add(new Color(80, 40, 40, 240));
      smokeColor.add(new Color(80, 20, 20, 240));
      smokeColor.add(new Color(220, 50, 50, 200));
      smokeColor.add(new Color(150, 100, 100, 200));
      smokeColor.add(new Color(220, 50, 150, 180));
      smokeColor.add(new Color(180, 100, 110, 180));
      smokeColor.add(new Color(200, 100, 100, 160));
      smokeColor.add(new Color(220, 50, 60, 160));
    }

  }

  private AnimationAPI anime;
  private int openState = 0;//initial open state
  private float timer = 0;
  private float glowTimer = 0f;
  private float animeTimer = 0f;
  private boolean shouldOpen = false;

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
    if (engine.isPaused() || weapon.getSlot().isHidden() || weapon.getShip() == null) {
      return;
    }
    ShipAPI ship = weapon.getShip();
    float level = aEP_MarkerDissipation.getBufferLevel(ship);
    anime = weapon.getAnimation();
    int numOfFrame = anime.getNumFrames();
    float softFlux = ship.getFluxTracker().getCurrFlux() - ship.getFluxTracker().getHardFlux();
    shouldOpen = level > 0.9f;


    //Global.getCombatEngine().addFloatingText(ship.getLocation(),  "x", 20f ,new Color (0, 100, 200, 240),ship, 0.25f, 120f);
    animeTimer = animeTimer + amount;
    float interval = 1f / anime.getFrameRate();
    if (animeTimer >= interval) {
      animeTimer = animeTimer - interval;
      if (shouldOpen == false) {
        openState = openState - 1;
      }
      else {
        openState = openState + 1;
      }
    }


    //limit to top
    openState = MathUtils.clamp(openState, 0, numOfFrame - 1);
    float effectiveLevel = Float.valueOf(openState) / numOfFrame;

    //if ship is destroyed, set sprite transparent
    if (!weapon.getShip().isAlive()) {
      anime.setAlphaMult(0.5f);
    }


    //spawn sprites
    if (effectiveLevel > 0.5f) {
      //add sprite timer
      timer = timer + amount;
      glowTimer = glowTimer + amount;
      float offset1 = 1f;
      float offset2 = 5f;
      float offset3 = 5f;

      //create sprite
      if (timer > 0.1f) {
        timer = 0f;
        Random rand = new Random();
        if (smokeSprites.size() >= 3) {
          //SpriteAPI s = (SpriteAPI)smokeSprites.get(rand.nextInt(smokeSprites.size()));
          String s = (String) smokeSprites.get(rand.nextInt(smokeSprites.size()));
          Vector2f size = new Vector2f(16f, 16f);
          Vector2f sizeChange = new Vector2f(16f, 16f);
          int x = rand.nextInt(smokeColor.size());
          Color c = smokeColor.get(x);

          aEP_MovingSprite ms = new aEP_MovingSprite(aEP_Tool.getExtendedLocationFromPoint(weapon.getSlot().computePosition(ship), weapon.getCurrAngle(), 5f),// spawn location
                  aEP_Tool.Util.speed2Velocity(weapon.getCurrAngle() + (float) Math.random() * 30f - 15f, 0f + (float) (Math.random() * 18f)),
                  (float) (Math.random() * 60) - 30f,//sprite angle change per frame
                  weapon.getCurrAngle(),//start sprite angle
                  0f,//fade time by frame
                  0f,//full time by frame
                  2f * effectiveLevel,//fade out time by frame
                  sizeChange,//size change per frame
                  size,//size
                  s,//sprite
                  c);
          ms.setInitVel(ship.getVelocity());
          aEP_CombatEffectPlugin.Mod.addEffect(ms);

          x = rand.nextInt(smokeColor.size());
          c = smokeColor.get(x);
          ms= new aEP_MovingSprite((aEP_Tool.getExtendedLocationFromPoint(weapon.getSlot().computePosition(ship), weapon.getCurrAngle() - 90f, 5f)),// spawn location
                  aEP_Tool.Util.speed2Velocity(weapon.getCurrAngle() + (float) Math.random() * 30f - 15f, 0f + (float) (Math.random() * 18f)),// move toward facing
                  (float) (Math.random() * 60) - 30f,//sprite angle change per frame
                  weapon.getCurrAngle(),//start sprite angle
                  0f,//fade time by frame
                  0f,//full time by frame
                  1.5f * effectiveLevel,//fade out time by frame
                  sizeChange,//size change per frame
                  size,//size
                  s,//sprite
                  c);
          ms.setInitVel(ship.getVelocity());
          aEP_CombatEffectPlugin.Mod.addEffect(ms);

          x = rand.nextInt(smokeColor.size());
          c = smokeColor.get(x);
          ms = new aEP_MovingSprite((aEP_Tool.getExtendedLocationFromPoint(weapon.getSlot().computePosition(ship), weapon.getCurrAngle() + 90f, 5f)),// spawn location
                  aEP_Tool.Util.speed2Velocity(weapon.getCurrAngle() + (float) Math.random() * 30 - 15f, 0f + (float) (Math.random() * 18f)),// move toward facing
                  (float) (Math.random() * 60) - 30,//sprite angle change per frame
                  weapon.getCurrAngle(),//start sprite angle
                  0f,//fade time by frame
                  0f,//full time by frame
                  1.5f * effectiveLevel,//fade out time by frame
                  sizeChange,//size change per frame
                  size,//size
                  s,//sprite
                  c);
          ms.setInitVel(ship.getVelocity());
          aEP_CombatEffectPlugin.Mod.addEffect(ms);

          offset1 = 0.5f + MathUtils.getRandomNumberInRange(0, 1);
          offset2 = 4.5f + MathUtils.getRandomNumberInRange(0, 1);
          offset3 = 4.5f + MathUtils.getRandomNumberInRange(0, 1);

        }

      }


      if (weapon.getSpec().getWeaponId().equals("aEP_marker")) {


        //create glow when dissipate at difference speed
        float colorMult = effectiveLevel;
        Vector2f weaponLoc = aEP_Tool.getExtendedLocationFromPoint(weapon.getLocation(), weapon.getCurrAngle(), offset1);

        Global.getCombatEngine().addSmoothParticle(weaponLoc,//Vector2f loc,
          aEP_Tool.Util.speed2Velocity(weapon.getCurrAngle(), 0f),//Vector2f vel,
          10f,//float size,
          1f,//float brightness
          amount,//float duration,
          new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), (int) (120 * colorMult)));//java.awt.Color color)


        Global.getCombatEngine().addSmoothParticle(aEP_Tool.getExtendedLocationFromPoint(weaponLoc, weapon.getCurrAngle() + 90f, offset2),//Vector2f loc,
          aEP_Tool.Util.speed2Velocity(weapon.getCurrAngle(), 0f),//Vector2f vel,
          10f,//float size,
          1f,//float brightness
          amount,//float duration,
          new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), (int) (120 * colorMult)));//java.awt.Color color)


        Global.getCombatEngine().addSmoothParticle(aEP_Tool.getExtendedLocationFromPoint(weaponLoc, weapon.getCurrAngle() - 90f, offset3),//Vector2f loc,
          aEP_Tool.Util.speed2Velocity(weapon.getCurrAngle(), 0f),//Vector2f vel,
          10f,//float size,
          1f,//float brightness
          amount,//float duration,
          new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), (int) (120 * colorMult)));//java.awt.Color color)

        RippleDistortion ripple;
        ripple = new RippleDistortion(aEP_Tool.getRandomPointAround(weaponLoc, 10f), new Vector2f(MathUtils.getRandomNumberInRange(0, 10), MathUtils.getRandomNumberInRange(0, 10)));
        ripple.setSize(10f);
        ripple.setAutoFadeSizeTime(amount);
        ripple.setLifetime(amount * 10f);
        ripple.flip(true);
        ripple.setIntensity(1 * colorMult);
        DistortionShader.addDistortion(ripple);

      }

    }
    else {
      timer = 0f;
      glowTimer = 0f;
    }


    anime.setFrame(openState);
    return;


  }
}

        	         	       	       	 
