package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import combat.util.aEP_Tool;
import org.lwjgl.util.vector.Vector2f;

public class aEP_DecomposerFastBuildAI implements ShipSystemAIScript
{
  CombatEngineAPI engine;
  ShipSystemAPI system;
  ShipAPI ship;
  ShipwideAIFlags flags;

  IntervalUtil think = new IntervalUtil(0.2f, 0.3f);

  @Override
  public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
    this.ship = ship;
    this.system = system;
    this.engine = engine;
    this.flags = flags;
  }


  @Override
  public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {

    if (engine.isPaused()) {
      return;
    }
    think.advance(amount);
    if (!think.intervalElapsed()) return;

    int allOp = 0;
    float lostOp = 0;
    for (FighterWingAPI wing : ship.getAllWings()) {
      if (ship.getHullSpec().getBuiltInWings().contains(wing.getSpec().getId())) continue;
      int specNum = wing.getSpec().getNumFighters();
      int lostNow = -wing.getWingMembers().size() + specNum;
      float opCost = wing.getSpec().getOpCost(null);
      allOp += opCost;
      if (lostNow > 0) lostOp += lostNow * opCost / specNum;
    }
    //aEP_Tool.addDebugText(lostOp/allOp+"");
    float lostRate = (lostOp / allOp);
    if (lostRate > 0.35f) ship.useSystem();

    if (lostRate <= 0f && aEP_Tool.getFighterReplaceRate(0, ship) > 0.9f) ship.useSystem();
    //aEP_Tool.addDebugText(aEP_Tool.getFighterReplaceRate(0,ship) +"");
  }
}
