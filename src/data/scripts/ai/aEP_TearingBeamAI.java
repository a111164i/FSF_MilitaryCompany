//by a111164
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class aEP_TearingBeamAI implements AutofireAIPlugin
{

  private static final float BEAM_SPEED = 1000f;
  private final ShipwideAIFlags flags = new ShipwideAIFlags();
  private final ShipAIConfig config = new ShipAIConfig();
  private final CombatEngineAPI engine;
  private final WeaponAPI weapon;
  private final ShipAPI ship;
  private CombatEntityAPI toTarget;
  private Vector2f toTargetPo;
  private boolean haveTarget = false;
  private boolean shouldFire = false;

  public aEP_TearingBeamAI(WeaponAPI weapon) {
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
    if (toTarget instanceof ShipAPI) {
      return (ShipAPI) toTarget;
    }
    else {
      return null;
    }
  }

  public MissileAPI getTargetMissile() {
    if (toTarget instanceof MissileAPI) {
      return (MissileAPI) toTarget;
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

    if (engine.isPaused()) {
      return;
    }

    if (toTarget == null) {
      this.toTargetPo = aEP_Tool.getExtendedLocationFromPoint(weapon.getLocation(), weapon.getSlot().getAngle() + ship.getFacing(), 100f);
      haveTarget = false;
      this.shouldFire = false;
    }
    else {
      this.toTargetPo = toTarget.getLocation();
    }

    //战机的自动开火ai
    if (ship.isFighter()) {
      toTarget = null;
      float closestDist = weapon.getRange();
      for (CombatEntityAPI c : CombatUtils.getEntitiesWithinRange(ship.getLocation(), closestDist)) {
        if (c instanceof ShipAPI && c != null) {
          ShipAPI s = (ShipAPI) c;
          if (s.isHulk() && !s.isPiece()) {
            if (!s.isDrone() && !s.isFighter() && MathUtils.getDistance(s, weapon.getLocation()) < closestDist) {
              toTarget = s;
              closestDist = MathUtils.getDistance(s, weapon.getLocation());
            }
          }

        }
      }

      shouldFire = false;
      if (toTarget != null && ((ShipAPI) toTarget).isHulk() && !((ShipAPI) toTarget).isPiece()) {
        if (CollisionUtils.getCollisionPoint(weapon.getLocation(), aEP_Tool.getExtendedLocationFromPoint(weapon.getLocation(), weapon.getCurrAngle(), weapon.getRange()), toTarget) != null) {
          shouldFire = true;
        }
      }
      else {
        toTarget = null;

      }

      return;
    }


    //choose AI for normal ship
    if (ship.getSystem().getEffectLevel() < 0.9f) {
      toTarget = null;
      workAsPDAgainstMissiles();
      return;
    }
    else {
      toTarget = null;
      workAsHulkDecomposer();
      return;
    }


  }

  private void workAsPDAgainstMissiles() {
    List<MissileAPI> ms = AIUtils.getNearbyEnemyMissiles(ship, weapon.getRange() + 250f);
    //if toTarget is not in range/destroyed , clear it
    if (toTarget != null && !ms.contains(toTarget)) {
      haveTarget = false;
      toTarget = null;
    }
    //find a new target if !haveTarget
    for (MissileAPI m : ms) {
      float toFacing = VectorUtils.getFacing(VectorUtils.getDirectionalVector(weapon.getLocation(), m.getLocation()));
      float flyTime = aEP_Tool.projTimeToHitShip(m, ship);
      float killTime = m.getHitpoints() / weapon.getDerivedStats().getDps();
      float turnTime = MathUtils.getShortestRotation(weapon.getCurrAngle(), toFacing) / weapon.getTurnRate();
      //we have 4 cannon here, so only need 1/4 of time to kill a missile
      if (killTime + turnTime < (flyTime) * 4f + 0.2f && !haveTarget) {
        toTarget = m;
        haveTarget = true;
      }

    }


    //null protection and clear toTarget if it will not hit our ship
    if (toTarget == null) {
      this.shouldFire = false;
      return;
    }
    else {
      float toFacing = VectorUtils.getFacing(VectorUtils.getDirectionalVector(weapon.getLocation(), toTarget.getLocation()));
      float flyTime = aEP_Tool.projTimeToHitShip(toTarget, ship);
      float killTime = toTarget.getHitpoints() / weapon.getDerivedStats().getDps();
      float turnTime = MathUtils.getShortestRotation(weapon.getCurrAngle(), toFacing) / weapon.getTurnRate();
      if (killTime + turnTime > (flyTime) * 4f + 0.2f) {
        haveTarget = false;
      }
    }

    //fire check
    this.shouldFire = Math.abs(MathUtils.getShortestRotation(weapon.getCurrAngle(), VectorUtils.getFacing(VectorUtils.getDirectionalVector(weapon.getLocation(), toTarget.getLocation())))) < 5f;

  }

  private void workAsHulkDecomposer() {
    List<CombatEntityAPI> aroundShips = CombatUtils.getEntitiesWithinRange(ship.getLocation(), 2001f);

    //find the closest hulk
    float closestDist = weapon.getRange() + 10f;
    for (CombatEntityAPI c : aroundShips) {
      if (c instanceof ShipAPI) {
        ShipAPI s = (ShipAPI) c;
        if (s.isHulk() && !s.isPiece()) {
          if (!s.isDrone() && !s.isFighter() && MathUtils.getDistance(s, weapon.getLocation()) < closestDist) {
            toTarget = s;
            closestDist = MathUtils.getDistance(s, weapon.getLocation());
          }
        }

      }
    }

    //武器指向目标中心，或者已经处于目标体内，就开火
    if (toTarget != null && ((ShipAPI) toTarget).isHulk() && !((ShipAPI) toTarget).isPiece()) {
      if (Math.abs(MathUtils.getShortestRotation(weapon.getCurrAngle(), VectorUtils.getFacing(VectorUtils.getDirectionalVector(weapon.getLocation(), toTarget.getLocation())))) < 5f
        || CollisionUtils.isPointWithinBounds(weapon.getShip().getLocation(),toTarget)==true) {
        this.shouldFire = true;
      }
    }
    else {
      toTarget = null;
      this.shouldFire = false;
    }
  }
}  