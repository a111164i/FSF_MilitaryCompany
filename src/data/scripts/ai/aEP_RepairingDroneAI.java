//by Tartiflette, for the guiding part
//by a111164
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class aEP_RepairingDroneAI implements ShipAIPlugin
{

  private final float DAMPING = 0.05f;
  private final float MAX_SPEED;
  private final ShipwideAIFlags flags = new ShipwideAIFlags();
  private final ShipAIConfig config = new ShipAIConfig();
  private CombatEngineAPI engine;
  private final ShipAPI ship;
  private String id;
  private SpriteAPI sprite;
  private ShipAPI toTarget;
  private ShipAPI parentShip;
  private int timer = 540;
  private float dist;
  private Vector2f toTargetPo;
  private boolean shouldFire = false;
  private CombatEntityAPI target;
  private final Vector2f lead = new Vector2f();

  public aEP_RepairingDroneAI(FleetMemberAPI member, ShipAPI ship) {
    this.ship = ship;
    this.engine = Global.getCombatEngine();
    MAX_SPEED = ship.getMaxSpeed();
  }

  public void cancelCurrentManeuver() {
  }

  public void forceCircumstanceEvaluation() {
  }

  public void setDoNotFireDelay(float amount) {
  }

  public ShipwideAIFlags getAIFlags() {
    return this.flags;
  }

  public boolean needsRefit() {
    return false;
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

      return;
    }

    timer = timer + 1;
    boolean isReturning = false;

    //return check
    for (WeaponAPI wp : ship.getAllWeapons()) {
      if (wp.getAmmo() == 0) {
        isReturning = true;
        sprite = wp.getSprite();
      }


    }
    //find the most damaged target------------------------------------------------------------------------------------------------------------------------
    if (timer >= 600) {
      toTarget = findTargetNow(parentShip);
      timer = 0;
    }


    //------------------------------------------------------------------------------------------------------------
    //go to target process

    if (!isReturning && toTarget != null) {

      toTargetPo = toTarget.getLocation();
      dist = MathUtils.getDistanceSquared(ship.getLocation(), toTargetPo);

      float wonderRange = 0f;
      if (toTarget.getHullSize() == ShipAPI.HullSize.DESTROYER) {
        wonderRange = 50f;
      }
      if (toTarget.getHullSize() == ShipAPI.HullSize.CRUISER) {
        wonderRange = 100f;
      }
      if (toTarget.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) {
        wonderRange = 175f;
      }


      if (dist >= (wonderRange * wonderRange)) {

        Vector2f directionVec = VectorUtils.getDirectionalVector(ship.getLocation(), toTargetPo);
        float directionAngle = VectorUtils.getFacing(directionVec);


        float aimAngle = MathUtils.getShortestRotation(ship.getFacing(), directionAngle);
        if (aimAngle < 25) {
          ship.giveCommand(ShipCommand.TURN_RIGHT, null, 0);
          ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
        }
        if (aimAngle > -25) {
          ship.giveCommand(ShipCommand.TURN_LEFT, null, 0);
          ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
        }
        if (Math.abs(aimAngle) <= 45) {

          ship.giveCommand(ShipCommand.ACCELERATE, null, 0);

        }

      }
      else {

        ship.giveCommand(ShipCommand.ACCELERATE, null, 0);

      }

      //Firecheck
      if (shouldFire == true && dist <= 90000f && Math.abs(MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getFacing(VectorUtils.getDirectionalVector(ship.getLocation(), toTargetPo)))) < 5f) {
        if (ship.getShield() != null && ship.getShield().isOn()) {
          if (Math.abs(MathUtils.getShortestRotation(ship.getShield().getFacing(), VectorUtils.getFacing(VectorUtils.getDirectionalVector(ship.getLocation(), toTargetPo)))) > ship.getShield().getActiveArc() / 2 + 5) {
            ship.giveCommand(ShipCommand.FIRE, toTargetPo, 0);
          }
        }
        else {
          ship.giveCommand(ShipCommand.FIRE, toTargetPo, 0);
        }
      }


    }

    // --------------------------------------------------------------------------------------------


    //-------------------------------------------------------------------------------------------------
    //return process


    if (isReturning == true && parentShip != null && parentShip.isAlive()) {

      aEP_Tool.returnToParent(ship, parentShip, amount);
      toTargetPo = parentShip.getLocation();
      dist = MathUtils.getDistanceSquared(ship.getLocation(), toTargetPo);

    }

    //systemAIcheck
    if (dist <= 160000f && ship.getSystem().isActive() || ship.getSystem().isActive() && Math.abs(MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getFacing(VectorUtils.getDirectionalVector(ship.getLocation(), toTargetPo)))) > 45f) {
      ship.useSystem();
    }
    if (dist >= 160000f && !ship.getSystem().isActive() && Math.abs(MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getFacing(VectorUtils.getDirectionalVector(ship.getLocation(), toTargetPo)))) <= 45f) {
      ship.useSystem();
    }


  }


  public void init(CombatEngineAPI engine) {
    this.engine = engine;
  }

  //to get a target------------------------------------------------------------------------------------------------------------------------------------------------------
  private ShipAPI findTargetNow(ShipAPI shp) {

    List<ShipAPI> aroundShips = AIUtils.getNearbyAllies(shp, 4000f);
    ShipAPI returnShip = shp;
    aroundShips.add(shp);
    shouldFire = false;
    float maxDamagedShip = 0f;
    for (ShipAPI s : aroundShips) {
      if (s.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP || s.getHullSize() == ShipAPI.HullSize.CRUISER || s.getHullSize() == ShipAPI.HullSize.DESTROYER) {


        int xSize = s.getArmorGrid().getGrid().length;
        int ySize = s.getArmorGrid().getGrid()[0].length;
        float cellMaxArmor = s.getArmorGrid().getMaxArmorInCell();
        float totalDamageTaken = 0f;

        for (int a = 0; a < xSize; a++) {
          for (int b = 0; b < ySize; b++) {
            totalDamageTaken = totalDamageTaken + (cellMaxArmor - s.getArmorGrid().getArmorValue(a, b));
          }
        }
        // now we have total damagetaken for this ship S

        if (totalDamageTaken / (cellMaxArmor * xSize * ySize) > maxDamagedShip)//find the ship took most damage among all ships
        {
          returnShip = s;
          maxDamagedShip = totalDamageTaken / (cellMaxArmor * xSize * ySize);

        }
      }

    }


    int xSize = returnShip.getArmorGrid().getLeftOf() + returnShip.getArmorGrid().getRightOf();
    int ySize = returnShip.getArmorGrid().getAbove() + returnShip.getArmorGrid().getBelow();
    float cellMaxArmor = returnShip.getArmorGrid().getMaxArmorInCell();
    for (int x = 0; x < xSize; x++) {
      for (int y = 0; y < ySize; y++) {
        float armorNow = returnShip.getArmorGrid().getArmorValue(x, y);
        float armorLevel = (armorNow / cellMaxArmor);

        // get minArmorLevel position
        if (armorLevel <= 0.5) {
          shouldFire = true;
          return returnShip;
        }
      }
    }

    //engine.addFloatingText(returnShip.getLocation(),"toRepair", 15f ,new Color(100,100,100,250),returnShip, 0.25f, 20f);
    return shp;
  }


}
