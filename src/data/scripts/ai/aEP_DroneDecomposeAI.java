//by Tartiflette, for the guiding part
//by a111164
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class aEP_DroneDecomposeAI implements ShipAIPlugin
{

  private static final float MOVEMENT_MOD_CHANGE_RANGE = 400f;//how far from target that drone change its movement mod
  private static final float INTERVAL = 3f;//interval between two target search,by second
  private static final float REFORM_INTERVAL = 0.5f;//interval between reforms when toTarget == parentShip,by second
  private static final float REFORM_RANGE = 1.5f;//interval between reforms when toTarget == parentShip,by second
  private static final float REFORM_DIST_FROM_PARENT = 1.5f;//interval between reforms when toTarget == parentShip,by Colli
  private static final float SEARCH_TARGET_RANGE = 4000f;//max searching range, should equal to wing's max range
  private static final float RETURN_THRESHOLD = 8f;//after have how much supply than drone return
  private static final float SPLIT_INTERVAL = 4f;
  private final ShipwideAIFlags flags = new ShipwideAIFlags();
  private final ShipAIConfig config = new ShipAIConfig();
  private CombatEngineAPI engine;
  private final ShipAPI ship;
  private ShipAPI parentShip;
  private CombatEntityAPI toTarget;
  private Vector2f toTargetPo;
  private float timer;
  private float reformTimer = 99f;
  private float splitTimer = 99f;
  private boolean shouldDissipate = false;
  private boolean shouldReturn = false;


  public aEP_DroneDecomposeAI(FleetMemberAPI member, ShipAPI ship) {
    this.ship = ship;
    this.engine = Global.getCombatEngine();
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
    ///////////////
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
    /////////////

    timer = timer + amount;
    reformTimer = reformTimer + amount;
    splitTimer = splitTimer + amount;

    //检测每个武器携带的补给
    float allSupplies = 0f;
    for (WeaponAPI w : ship.getAllWeapons()) {
      if (engine.getCustomData().get("aEP_decompose_drone_supplies") != null) {
        if(((Map<WeaponAPI, Float>) engine.getCustomData().get("aEP_decompose_drone_supplies")).get(w) != null) {
          allSupplies += ((Map<WeaponAPI, Float>) engine.getCustomData().get("aEP_decompose_drone_supplies")).get(w);
        }
      }
      else {
        Map<WeaponAPI,Float> map = new HashMap<>();
        engine.getCustomData().put("aEP_decompose_drone_supplies",map);
      }
    }

    //should return check
    if (allSupplies >= RETURN_THRESHOLD && !shouldReturn) {
      shouldReturn = true;
    }

    //find a target
    if (!shouldReturn) {
      if (toTarget == null || timer >= INTERVAL) {
        toTarget = findTargetNow(parentShip, ship, SEARCH_TARGET_RANGE);
        if (toTarget == null) {
          toTarget = parentShip;
        }
        timer = 0;
      }
    }

    //null protection
    if (toTarget == null || ship.getLocation() == null) {
      return;
    }
    ship.setShipTarget((ShipAPI) toTarget);
    float dist = MathUtils.getDistance(ship.getLocation(), toTarget.getLocation());


    //shield check
    if (ship.getFluxLevel() > 0.9) {
      shouldDissipate = true;
    }
    if (ship.getFluxLevel() <= 0.1) {
      shouldDissipate = false;
    }

    if (!shouldDissipate) {
      ship.getShield().toggleOn();
    }
    else {
      ship.getShield().toggleOff();
    }

    //return parent if we should
    if (shouldReturn || (allSupplies > 0 && toTarget == parentShip)) {
      toTarget = parentShip;

      returnToParent(ship, parentShip, amount);
      return;
    }


    //normal move
    //Change movement mod due to range
    if (toTarget == parentShip) {
      //find toTargetPo according to target type
      if (reformTimer > REFORM_INTERVAL) {
        reformTimer = 0f;
        Vector2f centerPointAtBackOfParent = aEP_Tool.getExtendedLocationFromPoint(parentShip.getLocation(), parentShip.getFacing(), -(parentShip.getCollisionRadius()) * REFORM_DIST_FROM_PARENT);
        toTargetPo = aEP_Tool.getRandomPointAround(centerPointAtBackOfParent, REFORM_RANGE*parentShip.getCollisionRadius());
      }

      if (MathUtils.getDistance(ship.getLocation(), toTarget.getLocation()) > parentShip.getCollisionRadius() * REFORM_RANGE * 2) {
        aEP_Tool.moveToPosition(ship, toTargetPo);
      }
      else {
        aEP_Tool.Util.setToPosition(ship, toTargetPo);
        aEP_Tool.Util.moveToAngle(ship, parentShip.getFacing());
      }
      return;
    }else {
      reformTimer = 99f;
    }

    if (dist > toTarget.getCollisionRadius() + MOVEMENT_MOD_CHANGE_RANGE) {
      aEP_Tool.moveToPosition(ship, toTarget.getLocation());
    } else {

      if (splitTimer > SPLIT_INTERVAL) {
        splitTimer = 0f;
        toTargetPo = MathUtils.getRandomPointInCircle(toTarget.getLocation(),toTarget.getCollisionRadius() + 100f);
      }
      aEP_Tool.Util.setToPosition(ship, toTargetPo);
      aEP_Tool.Util.moveToAngle(ship, VectorUtils.getFacing(VectorUtils.getDirectionalVector(ship.getLocation(), toTarget.getLocation())));
    }

  }

  private CombatEntityAPI findTargetNow(ShipAPI parentShip, ShipAPI ship, float range) {
    if (parentShip == null || ship == null) {
      return null;
    }

    CombatEntityAPI closestShip = null;
    List<CombatEntityAPI> allEntities = CombatUtils.getEntitiesWithinRange(parentShip.getLocation(), range);
    float closestDist = SEARCH_TARGET_RANGE;
    for (CombatEntityAPI c : allEntities) {
      if (c instanceof ShipAPI && c != null) {
        ShipAPI s = (ShipAPI) c;
        if (s.isHulk() && !s.isPiece() && !s.isStation() && !s.isStationModule() && !s.isDrone() && !s.isFighter() && MathUtils.getDistance(s, ship) < closestDist) {
          closestShip = s;
          closestDist = MathUtils.getDistance(s, ship);
        }


      }
    }
    return closestShip;
  }


  //can't use method in AITool, we have supply to add
  void returnToParent(ShipAPI ship, ShipAPI parentShip, float amount) {
    aEP_Tool.returnToParent(ship, parentShip,amount);

    float supplyNum = 0;
    if (ship.isFinishedLanding()) {
      for (WeaponAPI w : ship.getAllWeapons()) {
        if (((Map<WeaponAPI, Float>) engine.getCustomData().get("aEP_decompose_drone_supplies")).get(w) != null) {
          supplyNum += ((Map<WeaponAPI, Float>) engine.getCustomData().get("aEP_decompose_drone_supplies")).get(w);
          ((Map<WeaponAPI, Float>) engine.getCustomData().get("aEP_decompose_drone_supplies")).put(w, 0f);
        }
        else {
          ((Map<WeaponAPI, Float>) engine.getCustomData().get("aEP_decompose_drone_supplies")).put(w, 0f);
        }
      }

      if (aEP_Tool.checkCargoAvailable(engine, ship)) {
        CargoAPI cargo = aEP_Tool.Util.getPlayerCargo();
        cargo.addSupplies(supplyNum);
        cargo.addCommodity("aEP_remain_part", supplyNum);
      }
      String toAdd = String.format("%s", (int) (supplyNum * 100) / 100);
      Global.getCombatEngine().addFloatingText(ship.getLocation(),
              "+ " + toAdd + " supply",
              30f,
              new Color(250, 250, 50, 250),
              ship,
              0.25f, 120f);
      ship.getWing().getSource().land(ship);
    }

  }


}