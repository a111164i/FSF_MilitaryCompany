package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.IntervalUtil
import data.scripts.utils.aEP_BaseCombatEffect
import data.scripts.utils.aEP_Tool
import org.lwjgl.util.vector.Vector2f

open class aEP_BaseSystemAI : ShipSystemAIScript {

  companion object{

  }

  lateinit var engine: CombatEngineAPI
  var system: ShipSystemAPI? = null
  lateinit var ship: ShipAPI
  lateinit var flags: ShipwideAIFlags
  var thinkTracker = IntervalUtil(0.1f, 0.5f)
  var shouldActive = false
  var skipWhenCooldown = false
  var minActiveTime = 0f //系统最小激活时间, 对于自由切换的系统，ai不会连续开关
  var timeElapsedActive = 0f
  var isRightClickSys = false

  constructor(){

  }

  constructor(ship: ShipAPI, system: ShipSystemAPI, isRightClick : Boolean){
    isRightClickSys = isRightClick
    init(ship, system, ShipwideAIFlags(), Global.getCombatEngine())
  }

  override fun init(ship: ShipAPI, system: ShipSystemAPI?, flags: ShipwideAIFlags, engine: CombatEngineAPI) {
    this.ship = ship
    this.engine = engine
    this.flags = flags
    this.system = system
    initImpl()
    if(isRightClickSys) this.system = ship.phaseCloak
  }

  /**
   * 用这个，在这里处理是否是右键系统
   * */
  open fun initImpl() {

  }

  override fun advance(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    if (engine.isPaused) {
      return
    }
    thinkTracker.advance(amount)
    if (!thinkTracker.intervalElapsed()) return

    if(system?.state == ShipSystemAPI.SystemState.IN
      || system?.state == ShipSystemAPI.SystemState.ACTIVE
      || system?.state == ShipSystemAPI.SystemState.OUT ){
      timeElapsedActive+=thinkTracker.elapsed
    }else{
      timeElapsedActive = 0f
    }

    var shouldSkip = false
    //如果系统正在冷却，不需要思考
    if(system?.state == ShipSystemAPI.SystemState.COOLDOWN && skipWhenCooldown){
      shouldSkip = true
      shouldActive = false
    }


    if(!shouldSkip) advanceImpl(amount, missileDangerDir, collisionDangerDir, target)

    //保证最小激活时间
    if(system?.isActive?:false && !shouldActive && timeElapsedActive < minActiveTime) {
      shouldActive = true
    }

    //执行系统开关
    if(system != null &&  aEP_Tool.toggleSystemControl(system!!,shouldActive) ) {
      if (!isRightClickSys){
        ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0)
      } else {
        ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null,0)
      }
    }
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