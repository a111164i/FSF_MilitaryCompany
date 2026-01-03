package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import data.scripts.hullmods.aEP_SpecialHull
import data.scripts.shipsystems.aEP_VentMode
import org.lwjgl.util.vector.Vector2f

class aEP_VentModeAI: aEP_BaseSystemAI() {
  override fun initImpl() {
    thinkTracker.setInterval(0.1f,0.5f)
  }


  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {

    val softLevel = ((ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux)/ship.maxFlux).coerceIn(0f,1f)
    val hardLevel = ship.hardFluxLevel

    if(hardLevel > 0.8f) {
      shouldActive = false
      return
    }


    //if(heatLevel < 0.5f) return
    if(softLevel > 0.5f) ship.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_FLUX, 0.5f)
    if(softLevel > 0.1f) shouldActive = true
  }
}