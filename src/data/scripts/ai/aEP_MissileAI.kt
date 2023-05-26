package data.scripts.ai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.IntervalUtil
import combat.util.aEP_Tool
import data.scripts.util.MagicTargeting


class aEP_MissileAI: GuidedMissileAI, MissileAIPlugin {
  var t: CombatEntityAPI? = null
  val m: MissileAPI
  var s: ShipAPI? = null
  var stat: Status = Status()

  constructor(m:MissileAPI, ship: ShipAPI?){
    this.m = m
    this.s = ship
    if(ship?.shipTarget != null){
      t = ship.shipTarget
      stat = StraightToTarget()
    }else{
      stat = Searching()
    }

  }

  override fun getTarget(): CombatEntityAPI? {
    return t
  }


  override fun setTarget(target: CombatEntityAPI?) {
    this.t = target
  }


  override fun advance(amount: Float) {
    stat.advance(amount)
  }

  open class Status {
    open fun advance(amount: Float){

    }
  }

  inner class StraightToTarget() : Status() {

    override fun advance(amount: Float) {
      if(t == null || !Global.getCombatEngine().isInPlay(t) || (t is ShipAPI && !(t as ShipAPI).isAlive)){
        stat = Searching()
        return
      }
      val target = t as CombatEntityAPI
      if(target is ShipAPI && (target as ShipAPI).isPhased){
        m.giveCommand(ShipCommand.ACCELERATE)
      }else{
        aEP_Tool.flyThroughPosition(m,target.location)
      }
    }
  }


  inner class Searching() : Status() {
    val searchTracker = IntervalUtil(0.25f,0.25f)
    override fun advance(amount: Float) {
      m.giveCommand(ShipCommand.ACCELERATE)
      searchTracker.advance(amount)
      if(!searchTracker.intervalElapsed()) return
      t = MagicTargeting.pickTarget(
        m, MagicTargeting.targetSeeking.LOCAL_RANDOM,
        ((m.spec.maxFlightTime - m.flightTime) * m.maxSpeed/2f).toInt(),
        360,
        0,
        5,
        20,
        40,
        60,
        false
      )
      if(t != null)stat = StraightToTarget()
    }
  }
}