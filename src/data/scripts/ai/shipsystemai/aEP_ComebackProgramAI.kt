package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import data.scripts.shipsystems.aEP_ComebackProgram
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f

class aEP_ComebackProgramAI: aEP_BaseSystemAI() {

  override fun initImpl() {
    thinkTracker.setInterval(0.25f,0.75f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false
    if(system?.state != ShipSystemAPI.SystemState.IDLE) return


    for(f in AIUtils.getAlliesOnMap(ship)){
      if(!aEP_ComebackProgram.checkIsShipValidLite(f, ship)) continue
      var threat = 0f

      //检测装甲损失
      val xSize = ship.armorGrid.leftOf + ship.armorGrid.rightOf
      val ySize = ship.armorGrid.above + ship.armorGrid.below
      val cellMaxArmor = ship.armorGrid.maxArmorInCell
      val maxHullVal = ship.maxHitpoints

      var armorLostPercent = 0f
      var hullLostPercent = 0f

      var totalArmorAllCell = 0f

      for (x in 0 until xSize) {
        for (y in 0 until ySize) {
          val armorNow = ship.armorGrid.getArmorValue(x, y)
          armorLostPercent += (cellMaxArmor - armorNow).coerceAtLeast(0f)
          totalArmorAllCell += cellMaxArmor
        }
      }

      armorLostPercent /= totalArmorAllCell
      hullLostPercent = (1f - f.hullLevel).coerceAtLeast(0f)

      //血线紧急的优先度高于装甲损失
      threat += hullLostPercent * 2f
      threat += armorLostPercent
      if((f.fluxTracker.isOverloaded && f.fluxTracker.overloadTimeRemaining > 3f)) threat *=2f

      if(threat > 1.2f){
        ship.shipTarget = f
        shouldActive = true
        break
      }
    }
  }
}