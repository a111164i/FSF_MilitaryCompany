package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.weapons.aEP_m_s_era3
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_MagazineHeavy(): aEP_BaseHullMod() {
  companion object{
    const val ID = "aEP_MagazineHeavy"
    const val STARTING_POINT = 1f
    const val PERCENT_NEEDED_PER_TRIGGER = 1f

    const val FLAT_NEEDED_PER_TRIGGER = 20f
    const val PER_BONUS = 1f

    const val MAX_BONUS = 75f

    fun computeBonus(stats: MutableShipStatsAPI): Float{
      val realDissi = aEP_Tool.getRealDissipation(stats)
      val baseDissi = stats.fluxDissipation.base

      val startingThreshold = baseDissi * STARTING_POINT

      var excessed = realDissi - startingThreshold

      var bonus = 0f
      while (excessed >= FLAT_NEEDED_PER_TRIGGER){
        excessed -= FLAT_NEEDED_PER_TRIGGER
        bonus += PER_BONUS
      }

      bonus = bonus.coerceAtMost(MAX_BONUS)
      return bonus
    }
  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.MAGAZINES)
    notCompatibleList.add(aEP_HotLoader.ID)
  }

  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize, stats: MutableShipStatsAPI, id: String) {
    val bonus = computeBonus(stats)
    stats.ballisticAmmoRegenMult.modifyPercent(ID, bonus)
    stats.energyAmmoRegenMult.modifyPercent(ID, bonus)

  }


  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize, ship: ShipAPI?): String? {
    ship ?: return ""

    val baseDissi = ship.mutableStats.fluxDissipation.base

    val startingThreshold = baseDissi * STARTING_POINT
    val bonusFlat = FLAT_NEEDED_PER_TRIGGER


    val bonus = computeBonus(ship.mutableStats)

    if (index == 0) return String.format("%.0f", startingThreshold)
    if (index == 1) return String.format("%.0f", bonusFlat)
    if (index == 2) return String.format("+%.0f", PER_BONUS) +"%"
    if (index == 3) return String.format("+%.0f", MAX_BONUS) +"%"
    if (index == 4) return String.format("+%.0f", bonus) + "%"
    else return ""
  }

}