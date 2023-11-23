package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.*
import org.lwjgl.util.vector.Vector2f

class aEP_RepulseMissileLaunchAI : aEP_BaseSystemAI() {

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false
    if (ship.fluxTracker.currFlux / (ship.fluxTracker.maxFlux + 0.01f) > 0.8f) {
      shouldActive = true
    }
  }

}
