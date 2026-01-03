package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import data.scripts.weapons.aEP_DecoAnimation
import java.awt.Color

class aEP_RecallDevice : BaseShipSystemScript() {
  companion object{
    val KEY_JITTER = Any()
    val JITTER_COLOR = Color(100, 165, 255, 155)

    const val COVER_DECO_ID = "aEP_des_yuanyang_cover"
    const val LIGHT_SLOT_ID = "LED"
    fun getFighters(carrier: ShipAPI?): List<ShipAPI> {
      val result: MutableList<ShipAPI> = ArrayList()
      for (ship in Global.getCombatEngine().ships) {
        if (!ship.isFighter) continue
        if (ship.wing == null) continue
        if (ship.wing.sourceShip === carrier) {
          result.add(ship)
        }
      }
      return result
    }
  }

  var didActive = false
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    val ship = if (stats.entity is ShipAPI) {
      stats.entity as ShipAPI
    } else {
      return
    }

    //控制装饰武器
    for(w in ship.allWeapons){
      //盖子
      if(w.id.equals(COVER_DECO_ID)){
        val anim = w.effectPlugin as aEP_DecoAnimation
        if(state == ShipSystemStatsScript.State.IDLE) anim.setMoveToLevel(0f)
        else anim.setMoveToLevel(1f)
      }

      //cd灯
      if(w.slot.id.startsWith(LIGHT_SLOT_ID)){
        val num = w.slot.id.removePrefix(LIGHT_SLOT_ID).toInt()
        var lightOff = 6f
        if(num == 1 || num== 2) lightOff = 18f
        if(num == 3 || num== 4) lightOff = 12f

        val anim = w.effectPlugin as aEP_DecoAnimation
        if(ship.system.cooldownRemaining > lightOff) anim.setGlowEffectiveLevel(1f)
        else anim.setGlowToLevel(0f)
      }
    }


    if (effectLevel > 0f) {
      didActive = true
      var firstTime = false
      val fightersKey = ship.id + "_recall_device_target"
      var fighters: List<ShipAPI>? = null
      if (!Global.getCombatEngine().customData.containsKey(fightersKey)) {

        fighters = getFighters(ship)
        Global.getCombatEngine().customData[fightersKey] = fighters
        firstTime = true
      } else {
        fighters = Global.getCombatEngine().customData[fightersKey] as List<ShipAPI>?
      }
      if (fighters == null) { // shouldn't be possible, but still
        fighters = ArrayList()
      }
      for (fighter in fighters) {
        if (fighter.isHulk) continue
        val maxRangeBonus = fighter.collisionRadius * 1f
        val jitterRangeBonus = 5f + effectLevel * maxRangeBonus
        if (firstTime) {
          Global.getSoundPlayer().playSound("system_phase_skimmer", 1f, 0.5f, fighter.location, fighter.velocity)
        }
        fighter.setJitter(
          KEY_JITTER, JITTER_COLOR, effectLevel, 10, 0f, jitterRangeBonus
        )
        if (fighter.isAlive) {
          fighter.isPhased = true
        }
        if (state == ShipSystemStatsScript.State.IN) {
          val alpha = 1f - effectLevel * 0.5f
          fighter.extraAlphaMult = alpha
        }
        if (effectLevel == 1f) {
          if (fighter.wing != null && fighter.wing.source != null) {
            fighter.wing.source.makeCurrentIntervalFast()
            fighter.wing.source.land(fighter)
          } else {
            fighter.extraAlphaMult = 1f
          }
        }
      }
    }
    else{
      if(didActive){
        didActive = false
        unapply(stats,id)
      }
    }

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    var ship: ShipAPI? = null
    ship = if (stats.entity is ShipAPI) {
      stats.entity as ShipAPI
    } else {
      return
    }
    val fightersKey = ship.id + "_recall_device_target"
    Global.getCombatEngine().customData.remove(fightersKey)

  }

}
