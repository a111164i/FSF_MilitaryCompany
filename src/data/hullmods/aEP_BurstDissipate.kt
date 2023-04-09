package data.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_BurstDissipate internal constructor(): aEP_BaseHullMod() {
  companion object const {
    const val PER_BONUS = 8f
    const val PER_PUNISH = 16f
    const val SHIELD_DROP = 0.1f
    const val ID = "aEP_BurstDissipate"
    const val ID_P = "aEP_BurstDissipate_p"
    const val ID_B = "aEP_BurstDissipate_b"
  }

  init {
    notCompatibleList.add(aEP_SoftfluxDissipate.ID)
    notCompatibleList.add(aEP_RapidDissipate.ID)
    notCompatibleList.add(HullMods.SAFETYOVERRIDES)
    haveToBeWithMod.add(aEP_MarkerDissipation.ID)
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    val numOfDissipation = ship.variant.numFluxVents
    ship.mutableStats.shieldAbsorptionMult.modifyFlat(ID_P, SHIELD_DROP)
    ship.mutableStats.fluxDissipation.modifyFlat(ID_B,numOfDissipation* PER_BONUS)
    if (!ship.hasListenerOfClass(FluxDissipationDynamic::class.java)) {
      ship.addListener(FluxDissipationDynamic(ship, numOfDissipation * PER_BONUS, numOfDissipation * PER_PUNISH))
    }
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize): String? {
    return null
  }

  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    ship?:return
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    //val fluxVent = (ship.variant.numFluxVents + ship.variant.numFluxCapacitors) * BASE_BUFF_PER_VENT
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("{%s}"+ txt("aEP_BurstDissipate01"), 5f, arrayOf(Color.green), aEP_ID.HULLMOD_POINT, String.format("%.0f", PER_BONUS))
    tooltip.addPara("{%s}"+ txt("aEP_BurstDissipate04"), 5f, arrayOf(Color.red), aEP_ID.HULLMOD_POINT, String.format("%.0f", SHIELD_DROP*100f)+"%")

    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor, Alignment.MID, 5f)
    val image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec(ID).spriteName, 48f)
    image.addPara("{%s}"+ txt("aEP_BurstDissipate02"), 5f, arrayOf(Color.red), aEP_ID.HULLMOD_POINT, String.format("%.0f", PER_PUNISH- PER_BONUS))

    tooltip.addImageWithText(5f)
  }


  inner class FluxDissipationDynamic(val ship: ShipAPI, val maxBonus: Float, val maxPunish:Float): AdvanceableListener {
    val checkTracker = IntervalUtil(0.25f,0.25f)
    var heatingLevel = 0f

    override fun advance(amount: Float) {
      //维持玩家左下角的提示
      val bonus =  maxBonus
      val punish = heatingLevel * maxPunish
      if (Global.getCombatEngine().playerShip == ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName+"1",  //key
          Global.getSettings().getHullModSpec(ID).spriteName,  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("aEP_BurstDissipate03")  + (bonus-punish).toInt(),  //data
          punish>bonus
        )
      }

      checkTracker.advance(amount)
      if(!checkTracker.intervalElapsed()) return
      heatingLevel = aEP_MarkerDissipation.getBufferLevel(ship)
      //根据预热程度，抑制幅散
      ship.mutableStats.fluxDissipation.modifyFlat(ID_P,-punish)

    }
  }
}