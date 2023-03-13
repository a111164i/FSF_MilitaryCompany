package data.scripts.ai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import combat.util.aEP_Tool.Util.getNearestFriendCombatShip
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f

open class aEP_BaseShipAI: ShipAIPlugin {

  var stopFiringTime = 1f;
  var needsRefit = false;
  val shipAIConfig = ShipAIConfig()
  val shipAIFlags = ShipwideAIFlags()
  var ship: ShipAPI?
  var systemAI: ShipSystemAIScript? = null
  var useSystemAi = true

  constructor(ship : ShipAPI?){
    this.ship = ship
  }

  constructor(ship : ShipAPI?, systemScript: ShipSystemAIScript){
    this.ship = ship
    this.systemAI = systemScript
  }

  override fun setDoNotFireDelay(amount: Float) {
    stopFiringTime = amount
  }

  override fun forceCircumstanceEvaluation() {

  }

  override fun advance(amount: Float) {
    stopFiringTime -= amount
    stopFiringTime = MathUtils.clamp(stopFiringTime,0f,999f)
    if(systemAI != null && useSystemAi) {
      systemAI?.advance(amount, Vector2f(0f,0f),Vector2f(0f,0f),ship?.shipTarget)
    }
    aiFlags.advance(amount)
    advanceImpl(amount)
  }

  /**
   * 用这个
   */
  open fun advanceImpl(amount: Float){

  }

  /**
   * Only called for fighters, not regular ships or drones.
   * @return whether the fighter needs refit
   */
  override fun needsRefit(): Boolean {
    return needsRefit
  }

  override fun getAIFlags(): ShipwideAIFlags {
    return shipAIFlags
  }

  override fun cancelCurrentManeuver() {

  }

  override fun getConfig(): ShipAIConfig {
    return shipAIConfig
  }
}