package data.scripts.ai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipCommand
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_MaodianDroneLaunch
import data.scripts.shipsystems.aEP_MaodianDroneLaunch.Companion.MAX_HOLD_TIME
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f

class aEP_MaoDianDroneAI:aEP_BaseShipAI {
  companion object{
    const val ROTATE_SPEED = 30f
  }

  var target: ShipAPI? = null
  var targetLoc: Vector2f? = null

  constructor(ship: ShipAPI):super(ship){
    stat = Searching()
  }

  fun setToTarget(loc:Vector2f){
    targetLoc = loc
    stat = StraightToTarget()
  }

  inner class Searching() : aEP_MissileAI.Status(){
    override fun advance(amount: Float){
      if(targetLoc == null){
        targetLoc = ((aEP_Tool.getNearestFriendCombatShip(ship))?.location?: Vector2f(ship.location?:Vector2f(0f,0f)))
      }else{
        stat = StraightToTarget()
      }
    }
  }

  inner class StraightToTarget() : aEP_MissileAI.Status() {
    override fun advance(amount: Float) {
      time += amount
      if(time > aEP_MaodianDroneLaunch.MAX_FLY_TIME){
        stat = SelfExplode()
        return
      }

      val distSq = MathUtils.getDistanceSquared(ship.location,targetLoc)

      if(distSq > 640000f){
        aEP_Tool.flyThroughPosition(ship,targetLoc!!)
      }else{
        aEP_Tool.moveToPosition(ship,targetLoc!!)
      }

      if(distSq< 4000f){
        targetLoc = null
        stat = HoldShield()
      }
    }
  }

  inner class HoldShield : aEP_MissileAI.Status() {


    override fun advance(amount: Float) {

      //速度大于1就减速，小于1开始转圈
      if(ship.velocity.x * ship.velocity.x + ship.velocity.y * ship.velocity.y < 4){
        if(ship.angularVelocity > -ROTATE_SPEED){
          ship.giveCommand(ShipCommand.TURN_RIGHT,null,0)
        }else{
          ship.giveCommand(ShipCommand.TURN_LEFT,null,0)
        }
      }else{
        ship.giveCommand(ShipCommand.DECELERATE,null,0)
      }

      //速度够慢就开盾
      if(ship.velocity.x < 10 && ship.velocity.y < 10){
        shieldFacing = ship.facing
      }else{
        shieldFacing = null
      }

      time += amount
      if(time > aEP_MaodianDroneLaunch.MAX_HOLD_TIME || ship.fluxLevel >= 0.99f || ship.fluxTracker.isOverloaded) {
        stat = SelfExplode()
        return
      }
    }
  }

}