package data.shipsystems.scripts

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.combat.FighterLaunchBayAPI
import data.shipsystems.scripts.aEP_DecomposerFastBuildScript
import com.fs.starfarer.api.combat.FighterWingAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.api.combat.ShipSystemAPI
import java.awt.Color

class aEP_DecomposerFastBuildScript : BaseShipSystemScript() {
  companion object {
    const val EXTRA_NUM_MULT = 1f //by mult, to mult
    const val FRR_DECREASE_SPEED_MOD = 900f //by percent to mult
    const val NEW_FTR_ATK_MULT = 0.5f
    const val NEW_FTR_DEF_MULT = 0.5f
    const val NEW_FTR_LIFE = 20f //
    const val RATE_COST = 0.15f
  }

  var ship: ShipAPI? = null
  var engine = Global.getCombatEngine()

  //aEP_Tool.floatDataRecorder
  var didUsed = false
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    ship = stats.entity as ShipAPI
    val jitterColor = Color(100, 50, 50, 60)
    ship!!.setJitterUnder(ship, jitterColor, effectLevel, 24, effectLevel * 40f)

    //先产出额外战机，必须在effectiveLevel为1时设置才起效
    if (effectLevel >= 1f) {
      for (bay in ship!!.launchBaysCopy) {
        if (bay.wing != null) {
          val rate = Math.max(0.35f, bay.currRate - RATE_COST)
          bay.currRate = rate
          //bay.makeCurrentIntervalFast();
          val wing = bay.wing
          if (!ship!!.hullSpec.builtInWings.contains(bay.wing.spec.id) && effectLevel == 1f) {
            bay.makeCurrentIntervalFast()
            val spec = bay.wing.spec
            val addForWing = (wing.spec.numFighters * EXTRA_NUM_MULT).toInt()
            val maxTotal = spec.numFighters + addForWing
            var actualAdd = maxTotal - bay.wing.wingMembers.size
            actualAdd = Math.min(spec.numFighters, actualAdd)
            if (actualAdd > 0) {
              bay.fastReplacements = bay.fastReplacements + addForWing
              bay.extraDeployments = actualAdd
              bay.extraDeploymentLimit = maxTotal
              bay.extraDuration = NEW_FTR_LIFE
            }
          }
        }
      }
      didUsed = true
    }

    //注意起效后有一小段延迟新战机才出仓，等effectiveLevel下降一会后，再降格所有战机
    if (effectLevel <= 0.1f && didUsed) {
      for (bay in ship!!.launchBaysCopy) {
        if (bay.wing != null && ship!!.variant.nonBuiltInWings.contains(bay.wing.wingId)) {
          for (ftr in bay.wing.wingMembers) {
            ftr.mutableStats.damageToFighters.modifyMult(id, NEW_FTR_ATK_MULT)
            ftr.mutableStats.damageToFrigates.modifyMult(id, NEW_FTR_ATK_MULT)
            ftr.mutableStats.damageToDestroyers.modifyMult(id, NEW_FTR_ATK_MULT)
            ftr.mutableStats.damageToCruisers.modifyMult(id, NEW_FTR_ATK_MULT)
            ftr.mutableStats.damageToCapital.modifyMult(id, NEW_FTR_ATK_MULT)
            engine.addFloatingText(ftr.location, "Degraded", 15f, Color.red, ftr, 1f, 1f)
          }
        }
      }
      didUsed = false
    }
  }

  //run once when unapply
  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    ship = stats.entity as ShipAPI
    didUsed = false
  }

}