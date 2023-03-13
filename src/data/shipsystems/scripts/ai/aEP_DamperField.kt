package data.shipsystems.scripts.ai

import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.IntervalUtil
import org.lwjgl.util.vector.Vector2f


class aEP_DamperField : ShipSystemAIScript {
  var engine: CombatEngineAPI? = null
  var system: ShipSystemAPI? = null
  var ship: ShipAPI? = null
  var flags: ShipwideAIFlags? = null
  var think = IntervalUtil(0.2f, 0.3f)
  override fun init(ship: ShipAPI, system: ShipSystemAPI, flags: ShipwideAIFlags, engine: CombatEngineAPI) {
    this.ship = ship
    this.system = system
    this.engine = engine
    this.flags = flags
  }

  override fun advance(amount: Float, missileDangerDir: Vector2f, collisionDangerDir: Vector2f, target: ShipAPI) {
    if (!think.intervalElapsed()) return

  }
}