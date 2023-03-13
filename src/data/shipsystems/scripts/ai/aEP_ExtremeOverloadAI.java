package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;

public class aEP_ExtremeOverloadAI implements ShipSystemAIScript
{

  CombatEngineAPI engine;
  ShipSystemAPI system;
  ShipAPI ship;
  ShipwideAIFlags flags;
  WeaponAPI weap;
  WeaponGroupAPI group;

  @Override
  public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
    this.ship = ship;
    this.system = system;
    this.engine = engine;
    this.flags = flags;
  }

  @Override
  public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
  }
}