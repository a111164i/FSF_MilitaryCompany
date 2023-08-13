package data.scripts.hullmods

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
import java.awt.Color

class aEP_BurstDissipate internal constructor(): aEP_BaseHullMod() {
  companion object const {
    const val PER_BONUS = 8f
    const val PER_PUNISH = 8f
    const val SHIELD_DROP = 8f

    const val SHIELD_DROP_PER_CAP = 0.8f
    const val BONUS_PER_CAP = 8f


    const val ID = "aEP_BurstDissipate"
    const val ID_P = "aEP_BurstDissipate_p"
    const val ID_B = "aEP_BurstDissipate_b"
  }

  init {
    notCompatibleList.add(aEP_SoftfluxDissipate.ID)
    notCompatibleList.add(aEP_RapidDissipate.ID)
    haveToBeWithMod.add(aEP_MarkerDissipation.ID)
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    val numOfDissipation = ship.variant.numFluxVents
    val numOfCap = ship.variant.numFluxCapacitors
    //给玩家在装配页面查看最佳增益，实际效果进了战场会浮动
    ship.mutableStats.fluxDissipation.modifyFlat(ID_B,numOfDissipation * PER_BONUS + numOfCap * BONUS_PER_CAP)
    ship.mutableStats.shieldAbsorptionMult.modifyPercent(ID_P, SHIELD_DROP) //+ numOfCap * SHIELD_DROP_PER_CAP)
    if (!ship.hasListenerOfClass(FluxDissipationDynamic::class.java)) {
      ship.addListener(FluxDissipationDynamic(
        ship,
        numOfDissipation * PER_BONUS + numOfCap * BONUS_PER_CAP,
        numOfDissipation * PER_PUNISH,
        numOfDissipation,
        numOfCap))
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
    tooltip.addPara("{%s}"+ txt("aEP_BurstDissipate05"), 5f, arrayOf(Color.green), aEP_ID.HULLMOD_POINT, String.format("%.0f", BONUS_PER_CAP))
    tooltip.addPara("{%s}"+ txt("aEP_BurstDissipate04"), 5f, arrayOf(Color.red), aEP_ID.HULLMOD_POINT, String.format("%.0f", SHIELD_DROP)+"%")

    //显示不兼容插件
    tooltip.addPara("{%s}"+txt("not_compatible")+"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))


    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor, Alignment.MID, 5f)
    //val image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec(ID).spriteName, 48f)
    tooltip.addPara("{%s}"+ txt("aEP_BurstDissipate02"), 5f, arrayOf(Color.red), aEP_ID.HULLMOD_POINT, String.format("%.0f", PER_PUNISH))
    tooltip.addPara("{%s}"+ txt("aEP_BurstDissipate06"), 5f, arrayOf(Color.red), aEP_ID.HULLMOD_POINT, String.format("%.1f", SHIELD_DROP_PER_CAP)+"%")

    //tooltip.addImageWithText(5f)

    //额外灰色说明
    //tooltip.addPara(aEP_DataTool.txt("aEP_BurstDissipate08"), Color.gray, 5f)
  }


  inner class FluxDissipationDynamic(val ship: ShipAPI, val maxBonus: Float, val maxPunish:Float, val numOfVent:Int ,val numOfCap:Int): AdvanceableListener {
    val checkTracker = IntervalUtil(0.25f,0.25f)
    var heatingLevel = 0f

    override fun advance(amount: Float) {
      //维持玩家左下角的提示
      val bonus =  (1f - heatingLevel) * maxBonus
      val punish = heatingLevel * maxPunish
      if (Global.getCombatEngine().playerShip == ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName+"1",  //key
          Global.getSettings().getHullModSpec(ID).spriteName,  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("aEP_BurstDissipate03")  + (bonus-punish).toInt(),  //data
          punish>bonus)
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName+"2",  //key
          Global.getSettings().getHullModSpec(ID).spriteName,  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("aEP_BurstDissipate07")  + String.format("%.1f",( SHIELD_DROP + heatingLevel * numOfCap * SHIELD_DROP_PER_CAP))+"%",  //data
          true)
      }

      checkTracker.advance(amount)
      if(!checkTracker.intervalElapsed()) return
      heatingLevel = aEP_MarkerDissipation.getBufferLevel(ship)
      //根据预热程度，抑制幅散
      ship.mutableStats.fluxDissipation.modifyFlat(ID_B,bonus)
      ship.mutableStats.fluxDissipation.modifyFlat(ID_P,-punish)
      //根据预热程度，更改盾效率
      ship.mutableStats.shieldAbsorptionMult.modifyPercent(ID_P, SHIELD_DROP + heatingLevel * numOfCap * SHIELD_DROP_PER_CAP)
    }
  }
}