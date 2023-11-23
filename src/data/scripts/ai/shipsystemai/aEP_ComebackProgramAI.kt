package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_ComebackProgram
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f

class aEP_ComebackProgramAI: aEP_BaseSystemAI() {

  override fun initImpl() {
    thinkTracker.setInterval(0.25f,1f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false
    if(system.state != ShipSystemAPI.SystemState.IDLE) return

    for(f in AIUtils.getAlliesOnMap(ship)){
      if(!aEP_ComebackProgram.checkIsShipValidLite(f, ship)) continue
      if(f.hitpoints/f.maxHitpoints < 0.6f){
        ship.shipTarget = f
        shouldActive = true
        break
      }
    }
  }
}