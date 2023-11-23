package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import data.scripts.hullmods.aEP_SpecialHull
import data.scripts.shipsystems.aEP_VentMode
import org.lwjgl.util.vector.Vector2f

class aEP_VentModeAI: aEP_BaseSystemAI() {
  override fun initImpl() {
    thinkTracker.setInterval(0.1f,0.4f)
  }

  var timeElapsedActive = 0f

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {

    val softFlux = ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux
    val hardLevel = ship.hardFluxLevel

    if(hardLevel > 0.7f) {
      shouldActive = false
      return
    }

    //if(heatLevel < 0.5f) return

    if(softFlux/ship.currFlux > 0.15f) shouldActive = true
  }
}