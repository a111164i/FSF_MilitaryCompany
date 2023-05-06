//by a111164
package data.scripts.ai

import com.fs.starfarer.api.Global
import combat.util.aEP_Tool.Util.getNearestFriendCombatShip
import combat.util.aEP_Tool.Util.setToPosition
import combat.util.aEP_Tool.Util.moveToAngle
import combat.util.aEP_Tool.Util.returnToParent
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAIPlugin
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.combat.ShipAIConfig
import com.fs.starfarer.api.combat.CombatEngineAPI
import org.lwjgl.util.vector.Vector2f
import combat.util.aEP_Tool
import data.scripts.ai.aEP_DroneShieldShipAI
import org.lazywizard.lazylib.FastTrig
import java.util.WeakHashMap
import org.lazywizard.lazylib.VectorUtils
import java.util.ArrayList

class aEP_DroneShieldShipAI(member: FleetMemberAPI, ship: ShipAPI) : aEP_BaseShipAI(ship) {

  companion object {
    private const val DRONE_WIDTH_MULT = 0.9f // 1 means by default no gap between, 2 means gap as big as 1 drone
    private const val FAR_FROM_PARENT = 30f // 30su far from parent's collisionRadius
    private const val RESET_TIME = 30f //by second;
    private const val KEY1 = "protectTarget"
    private const val KEY2 = "dronePosition"
  }

  private var protectTarget //ShipAPI drone, CombatEntity target
      : MutableMap<ShipAPI, CombatEntityAPI>? = null
  private var dronePosition: MutableMap<ShipAPI, Int?>? = null
  private var parentShip: ShipAPI? = null
  private var target: CombatEntityAPI? = null
  private var targetPo: Vector2f? = null
  private var timer = 0f
  private var shouldReset = false
  private var shouldDissipate = false
  private var shouldReturn = false
  private val droneSequence = ArrayList<ShipAPI>()




  override fun advance(amount: Float) {
    if (engine == null || engine!!.isPaused || ship == null) {
      engine = Global.getCombatEngine()
      return
    }

    //get parent ship
    parentShip = if (ship.wing.sourceShip == null) {
      getNearestFriendCombatShip(ship)
    } else {
      ship.wing.sourceShip
    }

    //get protectTarget list
    if (protectTarget == null) {
      if (engine.customData.containsKey(KEY1)) {
        protectTarget = engine.customData[KEY1] as MutableMap<ShipAPI, CombatEntityAPI>?
      } else {
        protectTarget = WeakHashMap()
        engine.customData[KEY1] = protectTarget
      }
    }
    //get dronePosition list
    if (dronePosition == null) {
      if (engine.customData.containsKey(KEY2)) {
        dronePosition = engine.customData[KEY2] as MutableMap<ShipAPI, Int?>?
      } else {
        dronePosition = WeakHashMap()
        engine.customData[KEY2] = dronePosition
      }
    }
    if (parentShip == null || !parentShip!!.isAlive || !ship.isAlive) {
      protectTarget!!.remove(ship)
      return
    }
    timer += amount

    //for ai find target of parentShip
    target = parentShip!!.shipTarget
    targetPo = if (target == null) {
      parentShip!!.mouseTarget
    } else {
      target!!.location
    }
    var targetAngle = VectorUtils.getFacing(VectorUtils.getDirectionalVector(parentShip!!.location, targetPo))
    protectTarget!![ship] = parentShip!!
    droneSequence.clear()

    //regularly reset position
    if (timer > RESET_TIME) {
      shouldReset = true
      timer = 0f
    }


    // get all drone with same target, add them to List:droneSequence
    // get all dead drone and remove them from all 3 list
    val allDestroyedDrone = ArrayList<ShipAPI>()
    for ((key1, value) in protectTarget!!) {
      val key = key1
      val `val` = value as ShipAPI
      if (`val` === parentShip) {
        droneSequence.add(key)
      }
      if (!key.isAlive) {
        allDestroyedDrone.add(key)
        dronePosition!!.remove(ship)
        droneSequence.remove(ship)
      }
    }
    for (s in allDestroyedDrone) {
      protectTarget!!.remove(s)
    }


    //if this drone is not in position List(means a new drone was produced) or timer is too large(this drone lives too long), then
    //reset all drones's sequence number that with same ship(that will force drones reformat)
    var droneSequenceNum = 0
    if (dronePosition!![ship] == null || shouldReset) {
      var i = 0
      while (i < droneSequence.size) {
        val s = droneSequence[i]
        dronePosition!![s] = i
        i += 1
      }
      shouldReset = false
    } else {
      droneSequenceNum = dronePosition!![ship]!!
    }


    //caculate which angle for each drone
    if (droneSequence.size == 1) {
    } else {
      //change face angle due to number of drones
      val droneWidth = (DRONE_WIDTH_MULT * 2 * 57.3f //rad to degrees
          * Math.asin(
        (ship.collisionRadius
            / (parentShip!!.collisionRadius + FAR_FROM_PARENT)).toDouble()
      ).toFloat()) //drone width by degrees
      targetAngle = targetAngle - droneWidth * (droneSequence.size - 1) / 2 + droneWidth * droneSequenceNum
    }
    val targetLocation = findTargetLocation(parentShip!!, targetAngle)
    setToPosition(ship, targetLocation)
    moveToAngle(ship, VectorUtils.getFacing(VectorUtils.getDirectionalVector(parentShip!!.location, ship.location)))


    //shield check
    if (ship.fluxLevel > 0.9) {
      shouldDissipate = true
    }
    if (ship.fluxLevel <= 0.1) {
      shouldDissipate = false
    }
    if (!shouldDissipate) {
      ship.shield.toggleOn()
    } else {
      ship.shield.toggleOff()
    }
    if (ship.wing.isReturning(ship)) {
      shouldReturn = true
    }

    //return check
    if (shouldReturn) {
      returnToParent(ship, parentShip, amount)
    }
  }

  private fun findTargetLocation(toProtectTarget: CombatEntityAPI, targetAngle: Float): Vector2f {
    val shipCollisionRadius = toProtectTarget.collisionRadius + FAR_FROM_PARENT
    val xAxis = FastTrig.cos(Math.toRadians(targetAngle.toDouble())).toFloat() * shipCollisionRadius
    val yAxis = FastTrig.sin(Math.toRadians(targetAngle.toDouble())).toFloat() * shipCollisionRadius
    val targetPosition = Vector2f(0f, 0f)
    targetPosition.setX(toProtectTarget.location.getX() + xAxis)
    targetPosition.setY(toProtectTarget.location.getY() + yAxis)
    return targetPosition
  }

}