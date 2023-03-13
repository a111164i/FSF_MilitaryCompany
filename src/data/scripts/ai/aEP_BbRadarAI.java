//by a111164
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

public class aEP_BbRadarAI implements AutofireAIPlugin
{


  private final ShipwideAIFlags flags = new ShipwideAIFlags();
  private final ShipAIConfig config = new ShipAIConfig();
  private final CombatEngineAPI engine;
  private final WeaponAPI weapon;
  private final ShipAPI ship;
  private CombatEntityAPI target;
  private Vector2f toTargetPo;
  private boolean shouldFire = false;

  public aEP_BbRadarAI(WeaponAPI weapon) {
    this.ship = weapon.getShip();
    this.weapon = weapon;
    this.engine = Global.getCombatEngine();

  }

  public void forceOff() {
    return;
  }

  public Vector2f getTarget() {
    return toTargetPo;
  }

  public ShipAPI getTargetShip() {
    if (target instanceof ShipAPI) {
      return (ShipAPI) target;
    }
    else {
      return null;
    }
  }

  public MissileAPI getTargetMissile() {
    if (target instanceof MissileAPI) {
      return (MissileAPI) target;
    }
    else {
      return null;
    }
  }

  public WeaponAPI getWeapon() {
    return weapon;
  }

  public boolean shouldFire() {
    return shouldFire;
  }

  public void advance(float amount) {

    if (engine.isPaused() || engine == null) {
      return;
    }


    target = ship.getShipTarget();
    shouldFire = false;

    //null check
    if (target == null || (target instanceof ShipAPI && !((ShipAPI) target).isAlive())) {
            /*
            toTargetPo = ship.getMouseTarget();
            CombatEntityAPI nearestTarget = AIUtils.getNearestEnemy(new SimpleEntity(toTargetPo));
            if(MathUtils.getDistance(nearestTarget.getLocation() , toTargetPo) < nearestTarget.getCollisionRadius())
            {
                target = nearestTarget;
            }

             */

      return;
    }


    toTargetPo = target.getLocation();


    ShipAPI friendlyInLine = aEP_Tool.isFriendlyInLine(weapon);
    Vector2f hitPoint = CollisionUtils.getCollisionPoint(weapon.getLocation(), toTargetPo, target);
    shouldFire = friendlyInLine == null && hitPoint != null && MathUtils.getDistance(weapon.getLocation(), hitPoint) < weapon.getRange() && Math.abs(MathUtils.getShortestRotation(weapon.getCurrAngle(), VectorUtils.getAngle(weapon.getLocation(), toTargetPo))) < 2;


  }


}  