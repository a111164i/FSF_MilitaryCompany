package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import combat.util.aEP_Tool
import data.scripts.hullmods.aEP_SpecialHull
import data.scripts.shipsystems.aEP_VentMode
import data.scripts.shipsystems.aEP_WeaponReset
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f

class aEP_WeaponResetAI: aEP_BaseSystemAI() {
  override fun initImpl() {
    thinkTracker.setInterval(0.25f,0.75f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {


    val softFlux = ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux
    val hardLevel = ship.hardFluxLevel
    val fluxLevel = ship.fluxLevel
    val softLevel = softFlux/ship.maxFlux
    val storedLevel = aEP_WeaponReset.readStoredLevel(ship)
    var willing = 0f

    if(softLevel > 0.6f) willing += 110f
    else if(softLevel > 0.5f) willing += 100f
    else if(softLevel > 0.45f) willing += 95f
    else if(softLevel > 0.4f) willing += 85f
    else if(softLevel > 0.35f) willing += 80f
    else if(softLevel > 0.3f) willing += 70f
    else if(softLevel > 0.25f) willing += 60f
    else if(softLevel > 0.2f) willing += 50f
    else if(softLevel > 0.15f) willing += 35f
    else if(softLevel > 0.1f) willing += 20f
    else if(softLevel > 0.05f) willing += 20f

    if(storedLevel > 0.85f) willing += -20f
    else if(storedLevel > 0.8f) willing += -10f
    else if(storedLevel > 0.75f) willing += -5f
    else if(storedLevel > 0.7f) willing += -0f
    else if(storedLevel > 0.65f) willing += 10f
    else if(storedLevel > 0.6f) willing += 20f
    else if(storedLevel > 0.5f) willing += 40f
    else if(storedLevel > 0.4f) willing += 55f
    else if(storedLevel > 0.3f) willing += 70f
    else if(storedLevel > 0.2f) willing += 75f
    else if(storedLevel > 0.1f) willing += 85f
    else if(storedLevel >= 0f) willing += 90f

    aEP_Tool.addDebugLog(willing.toString()+"_"+timeElapsedActive.toString())

    if(timeElapsedActive > 2f){
      shouldActive = false
    }
    willing *= MathUtils.getRandomNumberInRange(0.9f,1.1f)
    if(willing >= 100f) shouldActive = true
  }
}