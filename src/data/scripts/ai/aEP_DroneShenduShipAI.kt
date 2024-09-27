package data.scripts.ai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.util.IntervalTracker
import combat.util.aEP_Tool
import data.scripts.ai.shipsystemai.aEP_DroneTimeAlterAI
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.pow

class aEP_DroneShenduShipAI(member: FleetMemberAPI?, ship: ShipAPI) : aEP_BaseShipAI(ship, aEP_DroneTimeAlterAI()) {

  val newAttackLocTracker = IntervalTracker(1f,4f)
  var attackLoc = Vector2f(ship.location)

  override fun advanceImpl(amount: Float) {
    if(ship.shipTarget == null ){
      //重置一下索敌
      forceCircumstanceEvaluation()
    }
    //索敌失败时缓缓前进
    ship.shipTarget?:run {
      aEP_Tool.flyToPosition(ship, attackLoc)
    }
    val target = ship.shipTarget?:return

    //从这往后target不会为null了
    if(aEP_Tool.isDead(target)){
      ship.shipTarget = null
      return
    }

    val distSq = MathUtils.getDistanceSquared(ship,target)
    val attackDist = target.collisionRadius + 50f
    val attackDistSq = attackDist.pow(2)
    val dist2AttackPointSq = MathUtils.getDistanceSquared(ship.location,attackLoc)

    if(distSq > attackDistSq){
      aEP_Tool.flyThroughPosition(ship, target.location)
    }else{
      newAttackLocTracker.advance(amount)
      if(newAttackLocTracker.intervalElapsed() || dist2AttackPointSq < 100f){
        attackLoc = MathUtils.getRandomPointOnCircumference(target.location,attackDist)
      }
      //aEP_Tool.addDebugPoint(attackLoc)
      aEP_Tool.moveToAngle(ship, VectorUtils.getAngle(ship.location,target.location))
      aEP_Tool.moveToPosition(ship, attackLoc)
    }
  }

  override fun forceCircumstanceEvaluation() {
    val newTarget = aEP_Tool.getNearestEnemyCombatShip(ship)
    if(newTarget != null){
      val angle = MathUtils.getRandomNumberInRange(0f,360f)
      val d = MathUtils.getRandomNumberInRange(newTarget.collisionRadius/2f,newTarget.collisionRadius + 75f)
      attackLoc = aEP_Tool.speed2Velocity(angle,d)
    }else{
      attackLoc = aEP_Tool.getExtendedLocationFromPoint(ship.location,ship.facing,75f)
    }
    ship.shipTarget = newTarget
  }


}