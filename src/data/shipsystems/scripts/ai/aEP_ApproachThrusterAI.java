package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import combat.util.aEP_Tool;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.util.vector.Vector2f;

public class aEP_ApproachThrusterAI extends aEP_BaseSystemAI
{

  @Override
  public void initImpl() {
    getThinkTracker().setInterval(0.2f,0.3f);
  }

  @Override
  public void advanceImpl(float amount, @Nullable Vector2f missileDangerDir, @Nullable Vector2f collisionDangerDir, @Nullable ShipAPI target) {
    if (target == null || target.getAIFlags() == null || target.isFighter()) {
      return;
    }
    aEP_Tool.toggleSystemControl(ship, flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING)
            || flags.hasFlag(ShipwideAIFlags.AIFlags.MOVEMENT_DEST));
  }

}
