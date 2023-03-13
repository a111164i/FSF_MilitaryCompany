package data.shipsystems.scripts.ai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import org.lwjgl.util.vector.Vector2f

class aEP_DroneTimeAlterAI: aEP_BaseSystemAI(){

  override fun initImpl() {
    thinkTracker.setInterval(1.5f,0.5f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    if(ship.fullTimeDeployed < 3f ) return

    shouldActive = false
    if(system.state == ShipSystemAPI.SystemState.IDLE) shouldActive = true
  }
}