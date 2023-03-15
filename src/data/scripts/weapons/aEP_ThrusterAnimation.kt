package data.scripts.weapons

import data.scripts.weapons.MagicVectorThruster
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.WeaponAPI

class aEP_ThrusterAnimation : MagicVectorThruster() {
  private val e: ShipEngineAPI? = null
  private val amount = 0f
  private val ORIGINAL_HEIGHT = 80f
  private val ORIGINAL_WIDTH = 10f
  var enable = false
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    if (enable) {
      super.advance(amount, engine, weapon)
    }
  }

  companion object {
    private const val maxFlameState = 30f // 0 == non flame, control flame length
  }
}