package data.shipsystems.scripts.ai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAIScript
import com.fs.starfarer.api.combat.WeaponAPI
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f

class aEP_RequanReloadAI: aEP_BaseSystemAI() {

  override fun initImpl() {
    thinkTracker.setInterval(0.2f,0.3f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    var willing = 0f
    var reloadLevelTotal = 0f
    var totalOp = 0f
    for(w in ship.allWeapons){
      if(!w.slot.isBuiltIn) {
        val op = w.spec.getOrdnancePointCost(null) + 0.01f
        //小心有些武器的只有delay，没用cooldown
        val coolDown = w.cooldown + 0.01f
        val coolDownRemaining = w.cooldownRemaining
        reloadLevelTotal += (op * (coolDownRemaining / coolDown))
        totalOp += op
      }else{
        if(w.ammo <= 1){
          willing += 0.35f
        }
      }
    }
    reloadLevelTotal /= totalOp
    willing += reloadLevelTotal

    //保证幅能小于0.25时，光是用完内置导弹就会使用f
    if(ship.fluxLevel < 0.2f){
      willing += 0.65f
    }else if(ship.fluxLevel > 0.2f && ship.fluxLevel < 0.6f){
      willing +=0.8f* ((0.6f - ship.fluxLevel)/0.4f )
    }

    willing *= MathUtils.getRandomNumberInRange(0.75f,1.25f)
    shouldActive = false
    if(willing >= 1f){
      shouldActive = true
    }
  }
}