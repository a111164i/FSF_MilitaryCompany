package data.scripts.ai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.IntervalUtil
import data.scripts.utils.aEP_Tool
import data.scripts.ai.shipsystemai.aEP_BaseSystemAI
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f


open class aEP_BaseShipAI: ShipAIPlugin {

  companion object{
    const val ID = "aEP_BaseShipAI"
  }

  var engine: CombatEngineAPI
  var ship: ShipAPI

  var systemTarget: ShipAPI? = null
  var systemAI: aEP_BaseSystemAI? = null

  var missileDangerDir : Vector2f? = null
  var collisionDangerDir : Vector2f? = null

  val shipAIConfig = ShipAIConfig()
  val shipAIFlags = ShipwideAIFlags()

  var stopFiringTime = 1f
  var needsRefit = false

  var shieldFacing: Float? = null


  var stat: aEP_MissileAI.Status = aEP_MissileAI.Empty()

  constructor(ship : ShipAPI){
    this.ship = ship
    this.engine = Global.getCombatEngine()

  }

  constructor(ship : ShipAPI, systemAI: aEP_BaseSystemAI?){
    this.ship = ship
    this.engine = Global.getCombatEngine()
    this.systemAI = systemAI
    systemAI?.init(ship,ship.system,aiFlags,engine)
  }


  override fun setDoNotFireDelay(amount: Float) {
    stopFiringTime = amount
  }

  override fun forceCircumstanceEvaluation() {

  }

  override fun advance(amount: Float) {
    //如果本体已经死亡，不再运行ai
    if(aEP_Tool.isDead(ship) ){
      return
    }

    //控制护盾
    if(shieldFacing != null){
      ship.shieldTarget?.set(aEP_Tool.getExtendedLocationFromPoint(ship.shieldCenterEvenIfNoShield, shieldFacing!!, 600f))

      //这个方法里面包含了检测舰船是否有护盾
      aEP_Tool.toggleShieldControl(ship,true)
    }else{
      aEP_Tool.toggleShieldControl(ship,false)
    }

    stat.advance(amount)
    stopFiringTime -= amount
    stopFiringTime = MathUtils.clamp(stopFiringTime,0f,999f)
    if(systemAI != null) {
      systemAI?.advance(amount, missileDangerDir, collisionDangerDir, systemTarget?:ship.shipTarget)
    }

    aiFlags.advance(amount)

    if((ship.fighterTimeBeforeRefit <= 1f || ship.wing?.isReturning(ship)?:false )&& stat !is ForceReturn){
      stat = ForceReturn()
    }else{
      advanceImpl(amount)

    }

  }

  /**
   * 非联队飞机/领队用这个
   */
  open fun advanceImpl(amount: Float){

  }

  /**
   * 非领队用这个
   */
  open fun advanceImplWingman(leaderAI: aEP_BaseShipAI, amount: Float){

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


  inner class SelfExplode: aEP_MissileAI.Status(){
    val damageTracker = IntervalUtil(0.5f,0.5f)
    override fun advance(amount: Float) {
      damageTracker.advance(amount)
      if(damageTracker.intervalElapsed()){
        engine.applyDamage(
          ship,
          ship.location,
          ship.armorGrid.armorRating * 0.5f + ship.maxHitpoints*0.1f,
          DamageType.HIGH_EXPLOSIVE,
          0f, true, false, ship)
      }

    }
  }

  open inner class ForceReturn: aEP_MissileAI.Status(){
    var didLand = false

    init {
      //被new的时候下一次回航命令
      ship.wing?.orderReturn(ship)
    }

    override fun advance(amount: Float) {
      //如果根本没有母舰，直接转入自毁
      if(ship.wing?.sourceShip == null){
        stat = SelfExplode()
        return
      }

      //拥有母舰，则开始返回，如果返回途中母舰炸了，转入自毁
      val parent = ship.wing.sourceShip
      if(!parent.isAlive || parent.isHulk || !engine.isEntityInPlay(parent)){
        stat = SelfExplode()
        return
      }

      // 如果被stop returning了，重新审视
      val wing: FighterWingAPI = ship.getWing()
      if (!wing.isReturning(ship)) {
        forceCircumstanceEvaluation()
        return
      }
      aEP_Tool.returnToParent(ship, parent, amount)


      if(ship.isFinishedLanding && !didLand){
        onReturn()
        didLand = true
      }

    }

    open fun onReturn(){

    }
  }

}