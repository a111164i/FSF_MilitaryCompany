package data.shipsystems.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import combat.impl.VEs.aEP_MovingSmoke;
import combat.impl.aEP_BaseCombatEffect;
import combat.plugin.aEP_CombatEffectPlugin;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class aEP_NCReloadScript extends BaseShipSystemScript
{

  static final float RELOAD_COOLDOWN_MULT = 0.25f;//0.25 = 75 percent off
  static final float GLOW_INTERVAL = 0.05f;
  static final float GLOW_TIME = 0.5f;
  static final float GLOW_SIZE = 25f;
  static final float GLOW_SIZE_INTERVAL = 30f;
  static List smokeSprites = new ArrayList();
  private ShipAPI ship;


  private boolean didRefresherOrb = false;
  private boolean didOnce = false;
  {
    smokeSprites.add("aEP_FX.thick_smoke01");
    smokeSprites.add("aEP_FX.thick_smoke02");
    smokeSprites.add("aEP_FX.thick_smoke03");
  }

  public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    this.ship = (ShipAPI) stats.getEntity();
    if (ship == null) return;


    if (!didRefresherOrb) {
      aEP_CombatEffectPlugin.Mod.addEffect(new RefresherOrb(ship));
      didRefresherOrb = true;
    }

    if (!didOnce) {
      for (WeaponAPI w : ship.getAllWeapons()) {
        if (w.getSpec().getType() == WeaponAPI.WeaponType.MISSILE && w.getCooldownRemaining() > 0.5) {
          w.beginSelectionFlash();
          w.setRemainingCooldownTo(w.getCooldownRemaining() * RELOAD_COOLDOWN_MULT);
          int cloudNum = 0;
          while (cloudNum < 5) {
            //add cloud
            int x = (int) (Math.random() * smokeSprites.size());
            int colorMult = (int) (Math.random() * 40f) + 40;
            float sizeMult = (float) Math.random();
            aEP_MovingSmoke ms = new aEP_MovingSmoke(w.getLocation());
            ms.setInitVel(aEP_Tool.Util.speed2Velocity(MathUtils.getRandomNumberInRange(0f,360f),1.2f));
            ms.setLifeTime(4f);
            ms.setFadeIn(0.1f);
            ms.setFadeOut(0.7f);
            ms.setSize(20 + 40 * sizeMult);
            ms.setColor(new Color(colorMult, colorMult, colorMult, (int) (Math.random() * 40) + 200));
            aEP_CombatEffectPlugin.Mod.addEffect(ms);
            cloudNum = cloudNum + 1;
          }
        }
        didOnce = true;
      }
    }
  }


  public void unapply(MutableShipStatsAPI stats, String id) {
    didRefresherOrb = false;
    didOnce = false;
  }

  public StatusData getStatusData(int index, State state, float effectLevel) {
    return null;
  }

  public static class RefresherOrb extends aEP_BaseCombatEffect
  {
    CombatEntityAPI target;
    float length;
    float advanceDist;
    float glowTimer = GLOW_INTERVAL;

    RefresherOrb(CombatEntityAPI target) {
      this.target = target;
      this.length = -target.getCollisionRadius();
      int num = (int) ((target.getCollisionRadius() * 2f) / GLOW_SIZE_INTERVAL);
      advanceDist = GLOW_SIZE_INTERVAL + ((target.getCollisionRadius() * 2f) % GLOW_SIZE_INTERVAL) / num;
    }

    @Override
    public void advance(float amount) {
      super.advance(amount);
      if (length > target.getCollisionRadius() * 3f) {
        cleanup();
      }

      glowTimer = glowTimer + amount;
      if (glowTimer > GLOW_INTERVAL) {
        glowTimer = glowTimer - GLOW_INTERVAL;
        float xLength = length;
        float yLength = target.getCollisionRadius();
        while (yLength >= -target.getCollisionRadius()) {

          Vector2f toSpawn = new Vector2f(target.getLocation().x + xLength, target.getLocation().y + yLength);
          if (MathUtils.getDistance(toSpawn, target.getLocation()) <= target.getCollisionRadius()) {
            int alpha = (int) ((1 - MathUtils.getDistance(toSpawn, target.getLocation()) / target.getCollisionRadius()) * 250);
            alpha = MathUtils.clamp(alpha, 0, 250);
            Global.getCombatEngine().addSmoothParticle(toSpawn,
                    new Vector2f(0, 0),
                    GLOW_SIZE,
                    1,
                    GLOW_TIME,
                    new Color(0, 250, 0, alpha));
            Global.getCombatEngine().addSmoothParticle(toSpawn,
                    new Vector2f(0, 0),
                    GLOW_SIZE,
                    1,
                    GLOW_TIME,
                    new Color(0, 250, 0, alpha));
          }
          yLength = yLength - advanceDist;
          xLength = xLength - advanceDist;
        }
        length = length + advanceDist;

      }
      super.advanceImpl(amount);
    }


  }
}
