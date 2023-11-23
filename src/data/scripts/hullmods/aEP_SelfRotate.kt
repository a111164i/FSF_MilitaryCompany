package data.scripts.hullmods

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import combat.util.aEP_Tool

class aEP_SelfRotate : aEP_BaseHullMod() {
  companion object {
    const val id = "aEP_SelfRotate"
    const val REVERSE_TAG = "aEP_SelfRotateReverse"
  }

  //插件是炸了也会运行的
  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    super.advanceInCombat(ship, amount)


    if(aEP_Tool.isDead(ship)) return
    ship.ensureClonedStationSlotSpec()
    val slot = ship.stationSlot ?: return

    var angle = slot.angle
    //angle += amount * ROTATION_DEGREES_PER_SECOND;
    //angle += amount * ROTATION_DEGREES_PER_SECOND;
    var dir = -1f
    if(ship.hullSpec.hints.contains(ShipHullSpecAPI.ShipTypeHints.UNDER_PARENT) || ship.hullSpec.tags.contains(REVERSE_TAG)){
      dir = 1f
    }
    angle += amount * dir * ship.mutableStats.maxTurnRate.baseValue

    slot.angle = angle

  }
}