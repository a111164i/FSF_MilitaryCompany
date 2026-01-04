package data.scripts.weapons

import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_ID
import data.scripts.utils.aEP_Tool
import org.magiclib.weapons.MagicVectorThruster
import java.awt.Color

class aEP_ThrusterAnimation : MagicVectorThruster() {
  private val e: ShipEngineAPI? = null
  private val amount = 0f
  private val ORIGINAL_HEIGHT = 80f
  private val ORIGINAL_WIDTH = 10f
  var enable = false
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    if (enable) {
      super.advance(amount, engine, weapon)
      engine.addSmoothParticle(weapon.location,Misc.ZERO,40f,1f,aEP_Tool.getAmount(null),ENGINE_GLOW)
    }
  }

  companion object {
    private const val maxFlameState = 30f // 0 == non flame, control flame length
    val ENGINE_GLOW = Color(240,235,170,255)
  }
}