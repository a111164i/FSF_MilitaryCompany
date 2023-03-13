package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import static data.shipsystems.scripts.aEP_FCLBurstScript.FULL_DAMAGE_RANGE;
import static data.shipsystems.scripts.aEP_FCLBurstScript.MIN_DAMAGE_PERCENT;

public class aEP_FCLBurstAI implements ShipSystemAIScript
{
  CombatEngineAPI engine;
  ShipSystemAPI system;
  ShipAPI ship;
  ShipwideAIFlags flags;
  IntervalUtil think = new IntervalUtil(0.1f, 0.1f);
  float willing = 0;

  @Override
  public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
    this.ship = ship;
    this.system = system;
    this.engine = engine;
    this.flags = flags;
  }


  @Override
  public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
    if (engine.isPaused() || target == null || target.getAIFlags() == null || target.isFighter()) {
      return;
    }

    think.advance(amount);
    willing = 0;
    if (!think.intervalElapsed()) return;


    float maxWillingRange = Global.getSettings().getWeaponSpec("aEP_FCL").getMaxRange();
    Vector2f hitPoint = CollisionUtils.getCollisionPoint(ship.getLocation(), aEP_Tool.getExtendedLocationFromPoint(ship.getLocation(), ship.getFacing(), maxWillingRange), target);
    if (hitPoint != null) {
      float dist = MathUtils.getDistance(hitPoint, ship.getLocation());
      float maxDist = maxWillingRange;
      float effectiveLevel = 1f;
      if (dist > FULL_DAMAGE_RANGE) effectiveLevel = 1f - (dist - FULL_DAMAGE_RANGE) / (maxDist - FULL_DAMAGE_RANGE);
      //保底伤害
      effectiveLevel = MathUtils.clamp(effectiveLevel, MIN_DAMAGE_PERCENT,1f);
      willing += 150f*effectiveLevel;
    }

    if (flags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP)) willing += 25f;
    if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)) willing += 50f;
    if (flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) willing += 15f;
    if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACK_OFF)) willing += 50f;
    if (flags.hasFlag(ShipwideAIFlags.AIFlags.IN_CRITICAL_DPS_DANGER)) willing += 30f;
    if (flags.hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_FLUX)) willing += 25f;

    if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACK_OFF_MIN_RANGE)) willing -= 30f;
    if (flags.hasFlag(ShipwideAIFlags.AIFlags.MAINTAINING_STRIKE_RANGE)) willing -= 60f;
    if (flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING)) willing -= 70f;
    if (flags.hasFlag(ShipwideAIFlags.AIFlags.MANEUVER_TARGET)) willing -= 25f;


    //aEP_Tool.addDebugText(willing+"");
    willing *= MathUtils.getRandomNumberInRange(0.8f, 1.2f);
    if (willing >= 130f && hitPoint != null) {
      ship.useSystem();
    }
  }

}