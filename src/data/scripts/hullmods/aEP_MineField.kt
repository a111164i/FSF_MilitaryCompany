package data.scripts.hullmods

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.hullmods.StealthMinefield

class aEP_MineField: StealthMinefield(){

  override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String?) {
    super.applyEffectsAfterShipCreation(ship, id)
  }
}