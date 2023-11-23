package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.FaderUtil
import com.fs.starfarer.api.util.IntervalUtil
import combat.impl.aEP_BaseCombatEffect
import combat.util.aEP_Tool
import org.lwjgl.util.vector.Vector2f

open class aEP_BaseSystemAI : ShipSystemAIScript {

  lateinit var engine: CombatEngineAPI
  lateinit var system: ShipSystemAPI
  lateinit var ship: ShipAPI
  lateinit var flags: ShipwideAIFlags
  var thinkTracker = IntervalUtil(0f, 0.5f)
  var shouldActive = false
  var skipWhenCooldown = false


  constructor(){

  }

  constructor(ship: ShipAPI, system: ShipSystemAPI){
    init(ship, system, ShipwideAIFlags(), Global.getCombatEngine())
  }

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
    thinkTracker.advance(amount)
    if (!thinkTracker.intervalElapsed()) return

    //如果系统正在冷却，不需要思考
    if(system.state == ShipSystemAPI.SystemState.COOLDOWN && skipWhenCooldown){
      shouldActive = false
    }else{
      advanceImpl(amount, missileDangerDir, collisionDangerDir, target)
    }
    aEP_Tool.toggleSystemControl(ship,shouldActive)

  }

  /**
   * 只用调整 shouldUse变量即可，会自动根据 shouldUse的状态来使用
   * */
  open fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    aEP_Tool

  }

  class MarkTarget(liftime:Float, val id:String, val addOrRemove:Float, val target:CombatEntityAPI)
    : aEP_BaseCombatEffect(liftime, target){

      init {
        val data = target.customData[id] as Float?
        data?.let { target.setCustomData(id, data + addOrRemove)  }
          ?: let { target.setCustomData(id, addOrRemove)   }
      }

    override fun readyToEnd() {
      val data = target.customData[id] as Float?
      data?.let { target.setCustomData(id, data - addOrRemove)  }
        ?: let { target.setCustomData(id, 0f)   }
    }
  }

}