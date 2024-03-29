package data.scripts.ai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipCommand
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.util.Misc
import combat.impl.VEs.aEP_MovingSprite
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_MaoDianDroneAI:aEP_BaseShipAI {
  companion object{
    const val ROTATE_SPEED = 45f
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
        targetLoc = ((aEP_Tool.getNearestFriendCombatShip(ship))?.location?: Vector2f(ship.location?:Vector2f(0f,0f))) as Vector2f?
      }else{
        stat = StraightToTarget()
      }
    }
  }

  inner class StraightToTarget() : aEP_MissileAI.Status() {
    override fun advance(amount: Float) {
      aEP_Tool.moveToPosition(ship,targetLoc!!)
      if(MathUtils.getDistanceSquared(ship.location,targetLoc) < 5000f){
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
        ship.shield?.toggleOn()
      }
    }
  }

}