package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.impl.hullmods.EscortPackage
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_DataTool
import data.scripts.utils.aEP_ID
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_FleetCommand : BaseHullMod() {

  companion object {
    const val ID = "aEP_FleetCommand"
    const val STAT_KEY = "aEP_FleetCommandOn"
    const val EFFECT_RANGE = 1600f
    const val EP_HULLMOD_ID = "escort_package"

    @JvmStatic
    var lastCheckTime: Float = -1f
  }

  private val escortPackage: EscortPackage by lazy {
    EscortPackage().also {
      it.init(Global.getSettings().getHullModSpec(EP_HULLMOD_ID))
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize?): String? {
    return when (index) {
      0 -> String.format("%.0f", EFFECT_RANGE)
      else -> null
    }
  }

  override fun applyEffectsBeforeShipCreation(hullSize: HullSize?, stats: MutableShipStatsAPI?, id: String?) {
  }

  override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {
    ship.mutableStats.dynamic.getMod(STAT_KEY).modifyFlat(id, 1f)
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    super.advanceInCombat(ship, amount)

    if (!ship.isAlive) return
    if (amount <= 0f) return

    val engine = Global.getCombatEngine()
    val currentTime = engine.getTotalElapsedTime(false)

    if (currentTime == lastCheckTime) return
    lastCheckTime = currentTime

    val commandShipsByOwner = mutableMapOf<Int, MutableList<ShipAPI>>()
    for (s in engine.ships) {
      if (s.isHulk) continue
      val stat = s.mutableStats.dynamic.getStat(STAT_KEY)
      if (stat != null && stat.modifiedValue >= 1f) {
        commandShipsByOwner.getOrPut(s.owner) { mutableListOf() }.add(s)
      }
    }

    if (commandShipsByOwner.isEmpty()) return

    for ((owner, commandShips) in commandShipsByOwner) {
      for (s in engine.ships) {
        if (s.isHulk || s.owner != owner) continue
        if (!s.variant.hasHullMod(EP_HULLMOD_ID)) continue

        var nearestDist = Float.MAX_VALUE
        for (cmd in commandShips) {
          val dist = MathUtils.getDistance(s.location, cmd.location)
          if (dist < nearestDist) nearestDist = dist
        }

        if (nearestDist <= EFFECT_RANGE) {
          escortPackage.applyEPEffect(s, s, 1f)
        }
      }
    }
  }

  override fun addPostDescriptionSection(
    tooltip: TooltipMakerAPI,
    hullSize: HullSize,
    ship: ShipAPI?,
    width: Float,
    isForModSpec: Boolean
  ) {
    val highlight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()

    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), com.fs.starfarer.api.ui.Alignment.MID, 5f)

    tooltip.addPara(
      " %s " + aEP_DataTool.txt("aEP", "aEP_FleetCommand", "effect1"),
      5f,
      arrayOf(Misc.getPositiveHighlightColor(), highlight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", EFFECT_RANGE)
    )

    tooltip.addPara(
      " %s " + aEP_DataTool.txt("aEP", "aEP_FleetCommand", "effect2"),
      5f,
      grayColor,
      aEP_ID.HULLMOD_POINT
    )
  }
}
