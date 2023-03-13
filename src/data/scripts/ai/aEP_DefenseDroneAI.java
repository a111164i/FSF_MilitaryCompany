//by a111164
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;


public class aEP_DefenseDroneAI implements ShipAIPlugin
{

  private static final float DRONE_WIDTH_MULT = 0.9f;// 1 means by default no gap between, 2 means gap as big as 1 drone
  private static final float FAR_FROM_PARENT = 30f;// 30su far from parent's collisionRadius
  private static final float RESET_TIME = 30f;//by second;
  private static final Map<ShipAPI, CombatEntityAPI> protectTarget = new WeakHashMap<ShipAPI, CombatEntityAPI>();//ShipAPI drone, CombatEntity target
  private static final Map<ShipAPI, Integer> dronePosition = new WeakHashMap<ShipAPI, Integer>();
  private final ShipwideAIFlags flags = new ShipwideAIFlags();
  private final ShipAIConfig config = new ShipAIConfig();
  private CombatEngineAPI engine;
  private final ShipAPI ship;
  private ShipAPI parentShip;
  private CombatEntityAPI target;
  private Vector2f targetPo;
  private float timer = 0;
  private boolean shouldReset = false;
  private boolean shouldDissipate = false;
  private final boolean didOnce = false;
  private boolean shouldReturn = false;
  private final ArrayList<ShipAPI> droneSequence = new ArrayList<ShipAPI>();

  public aEP_DefenseDroneAI(FleetMemberAPI member, ShipAPI ship) {
    this.ship = ship;

  }

  public void cancelCurrentManeuver() {
  }

  public void forceCircumstanceEvaluation() {
    shouldReset = true;
  }

  public void setDoNotFireDelay(float amount) {
  }

  public ShipwideAIFlags getAIFlags() {
    return this.flags;
  }

  public boolean needsRefit() {
    return shouldReturn;
  }

  public ShipAIConfig getConfig() {
    return this.config;
  }

  @Override
  public void advance(float amount) {


    if (engine == null || engine.isPaused() || ship == null) {
      engine = Global.getCombatEngine();
      return;
    }

    //get parent ship
    if (ship.getWing().getSourceShip() == null) {
      parentShip = aEP_Tool.getNearestFriendCombatShip(ship);
    }
    else {
      parentShip = ship.getWing().getSourceShip();
    }

    if (parentShip == null || !parentShip.isAlive() || !ship.isAlive()) {
      protectTarget.remove(ship);
      return;
    }

    timer = timer + amount;

    //for ai find target of parentShip
    target = parentShip.getShipTarget();
    if (target == null) {
      targetPo = parentShip.getMouseTarget();
    }
    else {
      targetPo = target.getLocation();
    }


    float targetAngle = VectorUtils.getFacing(VectorUtils.getDirectionalVector(parentShip.getLocation(), targetPo));
    protectTarget.put(ship, parentShip);


    droneSequence.clear();

    //regularly reset position
    if (timer > RESET_TIME) {
      shouldReset = true;
      timer = 0f;
    }

    //switch AI check ,I moved that part to systemscript
    /*
    if(parentShip.getSystem().isOn())
    {

        ShipAIPlugin fighterai = new FighterAI((Ship)ship,(L)ship.getWing() );
        ship.setShipAI(fighterai);
        dronePosition.remove(ship);
        shouldReset = true;
    }
    */

    // get all drone with same target, add them to List:droneSequence
    //get all dead drone and remove them from all 3 list
    ArrayList<ShipAPI> allDestroyedDrone = new ArrayList<ShipAPI>();
    for (Map.Entry entry : protectTarget.entrySet()) {

      ShipAPI key = (ShipAPI) entry.getKey();
      ShipAPI val = (ShipAPI) entry.getValue();

      if (val == parentShip) {
        droneSequence.add(key);
      }

      if (!key.isAlive()) {
        allDestroyedDrone.add(key);
        dronePosition.remove(ship);
        droneSequence.remove(ship);
      }
    }

    for (ShipAPI s : allDestroyedDrone) {
      protectTarget.remove(s);
    }


    //if this drone is not in position List(means a new drone was produced) or timer is too large(this drone lives too long), then
    //reset all drones's sequence number that with same ship(that will force drones reformat)
    int droneSequenceNum = 0;
    if (dronePosition.get(ship) == null || shouldReset) {
      int i = 0;
      while (i < droneSequence.size()) {
        ShipAPI s = droneSequence.get(i);
        dronePosition.put(s, i);
        i = i + 1;
      }
      shouldReset = false;
    }
    else {
      droneSequenceNum = dronePosition.get(ship);
    }


    //caculate which angle for each drone
    if (droneSequence.size() == 1) {

    }
    else {
      //change face angle due to number of drones
      float droneWidth = DRONE_WIDTH_MULT * 2 * 57.3f //rad to degrees
        * (float) Math.asin(ship.getCollisionRadius()
        / (parentShip.getCollisionRadius() + FAR_FROM_PARENT));//drone width by degrees
      targetAngle = targetAngle - droneWidth * (droneSequence.size() - 1) / 2 + droneWidth * droneSequenceNum;

    }


    Vector2f targetLocation = findTargetLocation(parentShip, targetAngle);
    aEP_Tool.Util.setToPosition(ship, targetLocation);
    aEP_Tool.Util.moveToAngle(ship, VectorUtils.getFacing(VectorUtils.getDirectionalVector(parentShip.getLocation(), ship.getLocation())));


    //shield check
    if (ship.getFluxLevel() > 0.95) {
      shouldDissipate = true;
    }
    if (ship.getFluxLevel() <= 0) {
      shouldDissipate = false;
    }

    if (!shouldDissipate) {
      ship.getShield().toggleOn();
    }
    else {
      ship.getShield().toggleOff();
    }

    if (ship.getWing().isReturning(ship)) {
      shouldReturn = true;
    }

    //return check
    if (shouldReturn) {
      aEP_Tool.returnToParent(ship, parentShip, amount);
    }


  }


  //return the most endangering target it to intercept
  private CombatEntityAPI findTargetNow(CombatEntityAPI toProtectTarget) {
    List<DamagingProjectileAPI> allProjs = CombatUtils.getProjectilesWithinRange(toProtectTarget.getLocation(), 1000f);
    DamagingProjectileAPI mostDangerProj = null;
    float mostDamage = 0f;
    for (DamagingProjectileAPI Proj : allProjs) {


      if (Proj.getDamageAmount() > mostDamage) {
        mostDangerProj = Proj;
      }


    }

    return mostDangerProj;

  }


  private Vector2f findTargetLocation(CombatEntityAPI toProtectTarget, float targetAngle) {
    float shipCollisionRadius = toProtectTarget.getCollisionRadius() + FAR_FROM_PARENT;
    float xAxis = (float) Math.cos(Math.toRadians(targetAngle)) * shipCollisionRadius;
    float yAxis = (float) Math.sin(Math.toRadians(targetAngle)) * shipCollisionRadius;
    Vector2f targetPosition = new Vector2f(0f, 0f);
    targetPosition.setX(toProtectTarget.getLocation().getX() + xAxis);
    targetPosition.setY(toProtectTarget.getLocation().getY() + yAxis);
    return targetPosition;
  }


}  
  
   
      
   

