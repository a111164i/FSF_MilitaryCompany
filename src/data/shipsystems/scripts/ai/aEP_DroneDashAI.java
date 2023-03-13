package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

public class aEP_DroneDashAI extends aEP_BaseSystemAI
{

  float willing = 0;
  @Override
  public void initImpl() {
    getThinkTracker().setInterval(0.5f,0.5f);
  }

  @Override
  public void advanceImpl(float amount, @Nullable Vector2f missileDangerDir, @Nullable Vector2f collisionDangerDir, @Nullable ShipAPI target) {
    willing = 0;
    Vector2f mousePoint = ship.getMouseTarget();
    if (ship.getShipTarget() != null) mousePoint = ship.getShipTarget().getLocation();
    if (ship.getWing() != null && ship.getWing().getSourceShip() != null)
      if (ship.getWing().getSourceShip().isPullBackFighters())
        mousePoint = ship.getWing().getSourceShip().getLocation();

    if (mousePoint == null) return;
    if (MathUtils.getDistance(mousePoint, ship.getLocation()) > 75 + ship.getCollisionRadius()) willing += 50;
    if (Math.abs(MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getAngle(ship.getLocation(), mousePoint))) < 10f)
      willing += 35;
    if (ship.getEngineController().isAccelerating()) willing += 25;
    //aEP_Tool.addDebugText(willing+"");
    willing += MathUtils.getRandomNumberInRange(0f, 50f);
    if (willing >= 100f)
      ship.useSystem();

  }



}
