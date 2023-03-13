package data.shipsystems.scripts.ai

import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.FaderUtil
import com.fs.starfarer.api.util.IntervalUtil
import combat.util.aEP_Tool
import org.lwjgl.util.vector.Vector2f

open class aEP_BaseSystemAI : ShipSystemAIScript {

  lateinit var engine: CombatEngineAPI
  lateinit var system: ShipSystemAPI
  lateinit var ship: ShipAPI
  lateinit var flags: ShipwideAIFlags
  var thinkTracker = IntervalUtil(0f, 0.4f)
  var shouldActive = false

  override fun init(ship: ShipAPI, system: ShipSystemAPI, flags: ShipwideAIFlags, engine: CombatEngineAPI) {
    this.ship = ship
    this.system = system
    this.engine = engine
    this.flags = flags
    initImpl()
  }

  /**
   * 用这个
   * */
  open fun initImpl() {

  }

  override fun advance(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    if (engine.isPaused) {
      return
    }
    if(system.state != ShipSystemAPI.SystemState.IDLE) return
    thinkTracker.advance(amount)
    if (!thinkTracker.intervalElapsed()) return
    advanceImpl(amount, missileDangerDir, collisionDangerDir, target)
    aEP_Tool.toggleSystemControl(ship,shouldActive)

  }

  /**
   * 只用调整 shouldUse变量即可，会自动根据 shouldUse的状态来使用
   * */
  open fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {


  }
}