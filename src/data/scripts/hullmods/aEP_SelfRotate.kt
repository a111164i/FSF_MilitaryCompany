package data.scripts.hullmods

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipCommand
import combat.util.aEP_Tool
import java.awt.Color

class aEP_SelfRotate : aEP_BaseHullMod() {
  companion object {
    const val id = "aEP_SelfRotate"
    val EXPLOSION_COLOR_OVERRIDE = Color(255,255,255,5)
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    super.advanceInCombat(ship, amount)
    ship.giveCommand(ShipCommand.TURN_LEFT,null,0)

    //只有MD03模块才会往下走
    if(ship.stationSlot?.id?.equals("MD02") == true) return
    val it = ship?.parentStation?.childModulesCopy?.iterator()
    while(it?.hasNext() == true){
      val next = it.next()
      if(next?.stationSlot?.id?.equals("MD02") == true || next.isAlive){
        ship.facing = aEP_Tool.angleAdd(next.facing,180f)
      }

      if(next?.stationSlot?.id?.equals("MD01") == true || next.isAlive){
        next.explosionScale = 0f
      }
    }

  }
}