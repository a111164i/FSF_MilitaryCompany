package data.scripts.ai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.IntervalUtil
import combat.util.aEP_Tool
import data.scripts.util.MagicTargeting
import data.scripts.weapons.aEP_cruise_missile_weapon_shot.Companion.TARGET_KEY

class aEP_CruiseMissileAI: aEP_BaseShipAI {

  var t: CombatEntityAPI? = null
  val m: ShipAPI
  var s: ShipAPI? = null
  var stat: Status = Status()

  constructor(m: ShipAPI, ship: ShipAPI?) : super(ship) {
    this.m = m
    this.s = ship
    if(ship?.shipTarget != null && !ship.shipTarget.isFighter && !ship.shipTarget.isDrone){
      t = ship.shipTarget
      stat = StraightToTarget()
    }else{
      stat = Searching()
    }

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

      if(t is ShipAPI){
        val t = t as ShipAPI
        if(t.isPhased){
          m.giveCommand(ShipCommand.ACCELERATE,null,0)
        }else{
          if(t.location != null){
            aEP_Tool.flyThroughPosition(m,t.location)
          }else{
            stat = Searching()
          }
        }
      }
    }
  }


  inner class Searching() : Status() {
    val searchTracker = IntervalUtil(0.25f,0.25f)
    override fun advance(amount: Float) {
      //如果t被人为设置，或者上一帧已经找到了目标，就直接转入StraightToTarget
      if(t != null){
        stat = StraightToTarget()
        return
      }

      m.giveCommand(ShipCommand.ACCELERATE,null,0)
      searchTracker.advance(amount)
      if(!searchTracker.intervalElapsed()) return
      t = MagicTargeting.pickTarget(
        m,
        MagicTargeting.targetSeeking.LOCAL_RANDOM,
        9999999,
        360,
        0,
        5,
        10,
        40,
        60,
        false
      )
    }
  }
}