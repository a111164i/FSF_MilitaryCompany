package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_ID
import data.scripts.weapons.aEP_DecoAnimation
import java.awt.Color

class aEP_OpenFieldHangar: aEP_BaseHullMod(), AdvanceableListener {
  companion object{
    const val ID = "aEP_OpenFieldHangar"
    const val ACTIVE_COOLDOWN = 15f
    const val EXTRA_DEPLOYMENT_TIME = Float.MAX_VALUE
    const val RATE_COST = 0f

    //如果安装超级战机，会受到冷却时间的惩罚
    const val PUNISH_START_OP = 15f
    const val PUNISH_PER_OP = 0.3f

    fun computePunish(ship: ShipAPI?):Float{
      ship ?: return 0f
      var punish = 0f
      for(wingId in ship.variant.fittedWings){
        val op = Global.getSettings().getFighterWingSpec(wingId).getOpCost(null)
        if(op > PUNISH_START_OP){
          punish += (op - PUNISH_START_OP) * PUNISH_PER_OP
        }
      }
      return punish
    }
  }

  init {
    //notCompatibleList.add(HullMods.UNSTABLE_INJECTOR)

  }

  //这些变量都是作为listener使用的
  var ship:ShipAPI? = null
  var activeCooldown = 8f


  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    this.ship = ship
    ship.addListener(this)
  }

  override fun advance(amount: Float) {
    ship?: return
    activeCooldown -= amount
    activeCooldown = activeCooldown.coerceAtLeast(0f)

    var r1 : aEP_DecoAnimation? = null
    var r2 : aEP_DecoAnimation? = null
    var r3 : aEP_DecoAnimation? = null
    var r4 : aEP_DecoAnimation? = null
    var r5 : aEP_DecoAnimation? = null
    var r6 : aEP_DecoAnimation? = null

    for(w in ship!!.allWeapons){
      if(w.slot.id.equals("ID_R1")) r1 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_R2")) r2 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_R3")) r3 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_R4")) r4 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_R5")) r5 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_R6")) r6 = w.effectPlugin as aEP_DecoAnimation
    }

    r1?:return
    r2?:return
    r3?:return
    r4?:return
    r5?:return
    r6?:return

    val step = ACTIVE_COOLDOWN/(7)
    if(activeCooldown <= step*1){
      r1.setGlowToLevel(1f)
      r2.setGlowToLevel(1f)
      r3.setGlowToLevel(1f)
      r4.setGlowToLevel(1f)
      r5.setGlowToLevel(1f)
      r6.setGlowToLevel(1f)
    }
    else if(activeCooldown <= step*2){
      r1.setGlowToLevel(0f)
      r2.setGlowToLevel(1f)
      r3.setGlowToLevel(1f)
      r4.setGlowToLevel(1f)
      r5.setGlowToLevel(1f)
      r6.setGlowToLevel(1f)
    }
    else if(activeCooldown <= step*3){
      r1.setGlowToLevel(0f)
      r2.setGlowToLevel(0f)
      r3.setGlowToLevel(1f)
      r4.setGlowToLevel(1f)
      r5.setGlowToLevel(1f)
      r6.setGlowToLevel(1f)
    }
    else if(activeCooldown <= step*4){
      r1.setGlowToLevel(0f)
      r2.setGlowToLevel(0f)
      r3.setGlowToLevel(0f)
      r4.setGlowToLevel(1f)
      r5.setGlowToLevel(1f)
      r6.setGlowToLevel(1f)
    }
    else if(activeCooldown <= step*5){
      r1.setGlowToLevel(0f)
      r2.setGlowToLevel(0f)
      r3.setGlowToLevel(0f)
      r4.setGlowToLevel(0f)
      r5.setGlowToLevel(1f)
      r6.setGlowToLevel(1f)
    }
    else if(activeCooldown <= step*6){
      r1.setGlowToLevel(0f)
      r2.setGlowToLevel(0f)
      r3.setGlowToLevel(0f)
      r4.setGlowToLevel(0f)
      r5.setGlowToLevel(0f)
      r6.setGlowToLevel(1f)
    }
    else if(activeCooldown <= step*7){
      r1.setGlowToLevel(0f)
      r2.setGlowToLevel(0f)
      r3.setGlowToLevel(0f)
      r4.setGlowToLevel(0f)
      r5.setGlowToLevel(0f)
      r6.setGlowToLevel(0f)
    }

    //刚刚激活后的一小段时间产生jitter的特效
//    val jitterTime = 2f
//    val jitterStopTime = ACTIVE_COOLDOWN - jitterTime
//    if(activeCooldown > jitterStopTime){
//      val jitterColor = Color(100, 50, 50, 120)
//      val jitterLevel = ((activeCooldown - jitterStopTime)/jitterTime)
//      ship!!.setJitterUnder(ship, jitterColor, jitterLevel, 24, jitterLevel * 50f)
//
//    }


    //定期激活
    if(activeCooldown <= 0f){

      var shouldUse = false
      for (bay in ship!!.launchBaysCopy) {
        if (bay.wing != null) {
          val wing = bay.wing
          if (!ship!!.hullSpec.builtInWings.contains(bay.wing.spec.id)) {
            //不再立刻刷新战机
            //bay.makeCurrentIntervalFast()
            val spec = bay.wing.spec
            val addForWing = wing.spec.numFighters * 1
            val maxTotal = spec.numFighters + addForWing
            var actualAdd = maxTotal - bay.wing.wingMembers.size
            actualAdd = Math.min(spec.numFighters, actualAdd)
            if (actualAdd > 0) {
              shouldUse = true
              val rate = Math.max(0.35f, bay.currRate - RATE_COST)
              bay.currRate = rate
              //不再立刻刷新战机
              //bay.fastReplacements += actualAdd
              //增加额外部署上限
              bay.extraDeployments = actualAdd
              bay.extraDeploymentLimit = maxTotal
              bay.extraDuration = EXTRA_DEPLOYMENT_TIME
            }
          }
        }
      }
      if(shouldUse){
        activeCooldown+= (ACTIVE_COOLDOWN)
      }
    }

  }

  override fun applyEffectsToFighterSpawnedByShip(fighter: ShipAPI, ship: ShipAPI, id: String) {
    fighter.mutableStats.damageToFrigates.modifyMult(ID, 0.5f)
    fighter.mutableStats.damageToDestroyers.modifyMult(ID, 0.5f)
    fighter.mutableStats.damageToCruisers.modifyMult(ID, 0.5f)
    fighter.mutableStats.damageToCapital.modifyMult(ID, 0.5f)
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?, ship: ShipAPI?): String? {
    //if (index == 0) return String.format("%.0f", ACTIVE_COOLDOWN)
    if (index == 0) return String.format("%.0f", 200f) +"%"
    if (index == 1) return String.format("-%.0f", 50f) +"%"
    else return null
  }
}