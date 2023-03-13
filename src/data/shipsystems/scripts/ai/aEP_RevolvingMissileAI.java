package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;

public class aEP_RevolvingMissileAI implements ShipSystemAIScript
{
  ShipAPI ship;
  ShipSystemAPI system;
  ShipwideAIFlags flags;
  CombatEngineAPI engine;

  @Override
  public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
    this.ship = ship;
    this.system = system;
    this.flags = flags;
    this.engine = engine;
  }

  @Override
  public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
    if (ship.getFluxTracker().getHardFlux() / (ship.getFluxTracker().getMaxFlux() + 0.01f) > 0.75f) ship.useSystem();
  }
}
