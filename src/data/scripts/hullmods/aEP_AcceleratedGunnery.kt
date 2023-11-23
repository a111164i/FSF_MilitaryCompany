package data.scripts.hullmods

import com.fs.starfarer.api.combat.ShipAPI

class aEP_AcceleratedGunnery: aEP_BaseHullMod() {
  companion object{
    const val ID = "aEP_AcceleratedGunnery"
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    ship?: return
    if(ship.isHulk || !ship.isAlive) return

    ship.mutableStats.ballisticRoFMult.modifyFlat(ID, 0.2f)
    ship.mutableStats.energyRoFMult.modifyFlat(ID, 0.2f)
    if(ship.system != null && ship.system.isActive){
      ship.mutableStats.ballisticRoFMult.modifyFlat(ID, 0.5f)
      ship.mutableStats.energyRoFMult.modifyFlat(ID, 0.5f)
    }
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?, ship: ShipAPI?): String? {
    if (index == 0) return String.format("%.0f", 20f) +"%"
    if (index == 1) return String.format("%.0f", 50f) +"%"
    return null
  }
}