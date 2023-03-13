package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;

public class aEP_DroneBurstAI implements ShipSystemAIScript
{

  static final float SLOW_RANGE = 400f;
  static final float SLOW_ANGLE_DIST = 60f;

  private CombatEngineAPI engine;
  private ShipAPI ship;
  private ShipSystemAPI system;

  private ShipAPI target;
  private Vector2f toTargetPo;

  @Override
  public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {


    return;

  }


  @Override
  public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
    this.ship = ship;
    this.system = system;
    this.engine = engine;
  }
}