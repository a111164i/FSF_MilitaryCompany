package data.shipsystems.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import combat.impl.aEP_BaseCombatEffect;
import combat.plugin.aEP_CombatEffectPlugin;
import combat.util.aEP_Tool;
import org.dark.graphics.util.Tessellate;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static combat.util.aEP_DataTool.txt;
import static java.lang.Math.PI;
import static java.lang.Math.cbrt;
import static org.lwjgl.opengl.GL11.*;

public class aEP_NiuArmorShield extends BaseShipSystemScript
{
  static final Color ACTIVE_COLOR = new Color(255, 165, 90, 240);
  static final Color GLOW_COLOR = new Color(200, 200, 200);
  static final float LIFETIME = 2f;
  static final float DAMAGE_ABSORB = 0.85f;

  static final float RING_START_RADIUS = 1f;
  static final float RING_SPREAD_SPEED = 1f;
  static final float RING_WIDTH = 0.25f;
  static final Color RING_OUTSIDE_COLOR = new Color(255, 165, 90, 120);
  static final Color RING_INSIDE_COLOR = new Color(0, 0, 0, 0);

  float amount = 0;
  float ABSORB_MULT = 1f;

  IntervalUtil ringTimer = new IntervalUtil(1f, 1f);

  @Override
  public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    ShipAPI ship = (ShipAPI) (stats.getEntity());
    StandardLight light = new StandardLight(ship.getLocation(), new Vector2f(0f, 0f), new Vector2f(0f, 0f), ship);
    light.setIntensity(effectLevel / 20f);
    light.setSize(200f);
    light.setColor(new Color(GLOW_COLOR.getRed(), GLOW_COLOR.getGreen(), GLOW_COLOR.getBlue(), 10));
    light.fadeIn(0f);
    light.setLifetime(amount);
    light.setAutoFadeOutTime(0f);
    LightShader.addLight(light);
    ship.setJitterUnder(ship, ACTIVE_COLOR, effectLevel, 24, effectLevel * 10f);

    ABSORB_MULT = ship.getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed();

    if (effectLevel >= 1) {
      if (!ship.hasListenerOfClass(DamageAbsorb.class)) {
        ship.addListener(new DamageAbsorb(ship, DAMAGE_ABSORB));
      }
      if (Global.getCombatEngine().getPlayerShip() == ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(this.getClass().getSimpleName(),//key
          "graphics/aEP_hullsys/marker_dissipation.png",//sprite name,full, must be registed in setting first
          Global.getSettings().getShipSystemSpec("aEP_NiuArmorShield").getName(),//title
          txt("ArmorShield01") + ": " + String.format("%.1f", DAMAGE_ABSORB * 100) + txt("ArmorShield02"),//data
          false);//is debuff
      }
    }


    //ringTimer.advance(aEP_Tool.getAmount(ship));
    //if (ringTimer.intervalElapsed()) aEP_CombatEffectPlugin.Mod.addEffect(new SpreadRing(ship, LIFETIME));

    if (effectLevel >= 1) {
      if (!ship.hasListenerOfClass(DamageAbsorb.class)) {
        ship.addListener(new DamageAbsorb(ship, DAMAGE_ABSORB));
      }
    }
  }


  @Override
  public void unapply(MutableShipStatsAPI stats, String id) {
    ShipAPI ship = (ShipAPI) (stats.getEntity());
    if (ship.hasListenerOfClass(DamageAbsorb.class)) {
      ship.removeListenerOfClass(DamageAbsorb.class);
      ship.getSpriteAPI().setColor(new Color(250, 250, 250));
      ship.getSpriteAPI().setBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
      ship.getSpriteAPI().setAlphaMult(1);
    }
    ABSORB_MULT = ship.getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed();


  }


  class DamageAbsorb implements DamageTakenModifier {
    float absorbMult;
    ShipAPI ship;

    DamageAbsorb(ShipAPI ship, float absorbMult) {
      this.ship = ship;
      this.absorbMult = absorbMult;
    }


    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
      if(param == null) return null;
      float effectiveLevel = 0;
      if (ship.getPhaseCloak() != null) {
        effectiveLevel = ship.getPhaseCloak().getEffectLevel();
      }


      if (!ship.isAlive()) {
        return null;
      }

      String id = "aEP_NiuArmorShield";
      if (damage.getType() == DamageType.HIGH_EXPLOSIVE) {
        damage.getModifier().modifyMult(id, (1 - absorbMult * effectiveLevel));
        ship.getFluxTracker().increaseFlux(damage.getDamage() * 1f * absorbMult * effectiveLevel * ABSORB_MULT, true);
      }
      if (damage.getType() == DamageType.KINETIC) {
        damage.getModifier().modifyMult(id, (1 - absorbMult));
        ship.getFluxTracker().increaseFlux(damage.getDamage() * 1f * absorbMult * effectiveLevel * ABSORB_MULT, true);
      }
      if (damage.getType() == DamageType.ENERGY) {
        damage.getModifier().modifyMult(id, (1 - absorbMult));
        ship.getFluxTracker().increaseFlux(damage.getDamage() * 1f * absorbMult * effectiveLevel * ABSORB_MULT, true);
      }
      if (damage.getType() == DamageType.FRAGMENTATION) {
        damage.getModifier().modifyMult(id, (1 - absorbMult));
        ship.getFluxTracker().increaseFlux(damage.getDamage() * 0.25f * absorbMult * effectiveLevel * ABSORB_MULT, true);
      }
      return id;
    }
  }

  class SpreadRing extends aEP_BaseCombatEffect {
    ShipAPI ship;

    SpreadRing(ShipAPI ship, float lifeTime) {
      this.ship = ship;
      setLifeTime(lifeTime);
      init(ship);
    }

    @Override
    public void advanceImpl(float amount) {
      BoundsAPI bounds = ship.getExactBounds();
      if (bounds != null) {
        Vector2f center = ship.getLocation();
        float level = getTime()/getLifeTime();
        float radiusChange = RING_SPREAD_SPEED * ship.getCollisionRadius() * getLifeTime() * level;
        float toRenderRadius = MathUtils.clamp(0 + radiusChange, 10f, 100000f);

        //calculate num of vertex
        int numOfVertex = MathUtils.clamp(120 + (int) ((toRenderRadius + RING_WIDTH * ship.getCollisionRadius()) / 5f), 180, 720);


        int redNear = (int) aEP_Tool.limitToTop((RING_INSIDE_COLOR.getRed()), 255, 0);
        int greenNear = (int) aEP_Tool.limitToTop((RING_INSIDE_COLOR.getGreen()), 255, 0);
        int blueNear = (int) aEP_Tool.limitToTop((RING_INSIDE_COLOR.getBlue()), 255, 0);
        int alphaNear = (int) aEP_Tool.limitToTop((RING_INSIDE_COLOR.getAlpha()), 255, 0);

        int redFar = (int) aEP_Tool.limitToTop((RING_OUTSIDE_COLOR.getRed()), 255, 0);
        int greenFar = (int) aEP_Tool.limitToTop((RING_OUTSIDE_COLOR.getGreen()), 255, 0);
        int blueFar = (int) aEP_Tool.limitToTop((RING_OUTSIDE_COLOR.getBlue()), 255, 0);
        int alphaFar = (int) aEP_Tool.limitToTop((RING_OUTSIDE_COLOR.getAlpha()), 255, 0);

        float width = RING_WIDTH * ship.getCollisionRadius();

        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        bounds.update(ship.getLocation(), ship.getFacing());

        SpriteAPI sprite = Global.getSettings().getSprite("aEP_FX", "ring");
        GL11.glEnable(GL_STENCIL_TEST);

        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_TEXTURE_2D);
        GL11.glColorMask(false, false, false, false);
        GL11.glStencilFunc(GL_ALWAYS, GL_POLYGON_STIPPLE_BIT, 255);
        GL11.glStencilMask(255);
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
        GL11.glClearStencil(0);
        GL11.glClear(GL_STENCIL_BUFFER_BIT);

        Tessellate.render(bounds, 1.0f, 1.0f, 1.0f, ship.getId());


        GL11.glColorMask(true, true, true, true);
        GL11.glStencilFunc(GL_EQUAL, GL_POLYGON_STIPPLE_BIT, 255);
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
        GL11.glStencilMask(0);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL_QUAD_STRIP);

        for (int i = 0; i <= numOfVertex; i++) {
          if (toRenderRadius < RING_START_RADIUS + width) {
            Vector2f pointNear = new Vector2f(center.x + RING_START_RADIUS * (float) FastTrig.cos((2f * PI * i) / numOfVertex), center.y + RING_START_RADIUS * (float) FastTrig.sin((2f * PI * i) / numOfVertex));
            Vector2f pointFar = new Vector2f(center.x + (toRenderRadius) * (float) FastTrig.cos(2 * PI * i / numOfVertex), center.y + (toRenderRadius) * (float) FastTrig.sin(2 * PI * i / numOfVertex));

            GL11.glColor4ub((byte) redNear, (byte) greenNear, (byte) blueNear, (byte) alphaNear);
            glVertex2f(pointNear.x, pointNear.y);
            GL11.glColor4ub((byte) redFar, (byte) greenFar, (byte) blueFar, (byte) alphaFar);
            glVertex2f(pointFar.x, pointFar.y);
          }
          else {
            Vector2f pointNear = new Vector2f(center.x + (toRenderRadius - width) * (float) FastTrig.cos((2f * PI * i) / numOfVertex), center.y + (toRenderRadius - width) * (float) FastTrig.sin((2f * PI * i) / numOfVertex));
            Vector2f pointFar = new Vector2f(center.x + (toRenderRadius) * (float) FastTrig.cos(2 * PI * i / numOfVertex), center.y + (toRenderRadius) * (float) FastTrig.sin(2 * PI * i / numOfVertex));

            GL11.glColor4ub((byte) redNear, (byte) greenNear, (byte) blueNear, (byte) alphaNear);
            glVertex2f(pointNear.x, pointNear.y);
            GL11.glColor4ub((byte) redFar, (byte) greenFar, (byte) blueFar, (byte) alphaFar);
            glVertex2f(pointFar.x, pointFar.y);
          }
          //aEP_Tool.addDebugText("1",point);
        }
        GL11.glEnd();

        GL11.glDisable(GL_STENCIL_TEST);
        GL11.glStencilFunc(GL_ALWAYS, 0, 255);
        GL11.glPopAttrib();

      }

    }

  }
}
