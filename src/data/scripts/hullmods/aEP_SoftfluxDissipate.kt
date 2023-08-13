package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
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

class aEP_SoftfluxDissipate internal constructor() : aEP_BaseHullMod() {
  companion object const {
    const val PER_PUNISH = 5f
    const val PER_PUNISH_CAP = 100f

    const val PER_BONUS = 10f
    const val PER_BONUS_CAP = 10f

    const val ID = "aEP_SoftfluxDissipate"
    const val ID_P = "aEP_SoftfluxDissipate_p"
    const val ID_B = "aEP_SoftfluxDissipate_b"
  }

  init {
    notCompatibleList.add(aEP_BurstDissipate.ID)
    notCompatibleList.add(aEP_RapidDissipate.ID)
    haveToBeWithMod.add(aEP_MarkerDissipation.ID)
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI , id: String) {
    val numOfDissipation = ship.variant.numFluxVents
    val numOfCap = ship.variant.numFluxCapacitors
    //给玩家在装配页面查看最佳增益，实际效果进了战场会浮动
    ship.mutableStats.fluxDissipation.modifyFlat(ID_P,-numOfDissipation* PER_PUNISH)
    ship.mutableStats.fluxCapacity.modifyFlat(ID_P,-numOfCap* PER_PUNISH_CAP)

    if (!ship.hasListenerOfClass(FluxDissipationDynamic::class.java)) {
      ship.addListener(FluxDissipationDynamic(
        ship,
        numOfDissipation * PER_BONUS + numOfCap * PER_BONUS_CAP,
        numOfDissipation * PER_PUNISH))
    }
  }

  override fun applySmodEffectsAfterShipCreationImpl(ship: ShipAPI, stats: MutableShipStatsAPI, id: String) {

  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String? {
    return null
  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    ship?:return
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    //val fluxVent = (ship.variant.numFluxVents + ship.variant.numFluxCapacitors) * BASE_BUFF_PER_VENT
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"),Alignment.MID, 5f)
    //先说常规加成会-5，再说给额外15浮动，这样符合逻辑
    tooltip.addPara("{%s}"+ txt("aEP_SoftfluxDissipate03") , 5f, arrayOf(Color.red), aEP_ID.HULLMOD_POINT, String.format("%.0f", PER_PUNISH))
    tooltip.addPara("{%s}"+ txt("aEP_SoftfluxDissipate05") , 5f, arrayOf(Color.red), aEP_ID.HULLMOD_POINT, String.format("%.0f", PER_PUNISH_CAP))

    //显示不兼容插件
    tooltip.addPara("{%s}"+txt("not_compatible")+"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))


    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor,Alignment.MID, 5f)
    tooltip.addPara("{%s}"+ txt("aEP_SoftfluxDissipate02"), 5f, arrayOf(Color.green),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", PER_BONUS),
      txt("aEP_SoftfluxDissipate07"))
    tooltip.addPara("{%s}"+ txt("aEP_SoftfluxDissipate06"), 5f, arrayOf(Color.green),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", PER_BONUS),
      txt("aEP_SoftfluxDissipate07"))
    tooltip.addPara("{%s}"+ txt("aEP_SoftfluxDissipate08"), 5f, arrayOf(Color.red),
      aEP_ID.HULLMOD_POINT,
      txt("aEP_SoftfluxDissipate07"),
      "0%",
      "100%")

    //额外灰色说明
    //tooltip.addPara(aEP_DataTool.txt("aEP_SoftfluxDissipate08"), Color.gray, 5f)
  }


  inner class FluxDissipationDynamic(val ship: ShipAPI, val maxBonus: Float, val maxPunish:Float):AdvanceableListener{
    val checkTracker = IntervalUtil(0.25f,0.25f)
    var heatingLevel = 0f

    override fun advance(amount: Float) {
      //维持玩家左下角的提示
      val hardPercent = ship.hardFluxLevel
      val bonus =  heatingLevel * maxBonus * (1f - hardPercent)
      val punish = (1f - heatingLevel) * maxPunish



      //val punish = heatingLevel * maxPunish
      if (Global.getCombatEngine().playerShip == ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName+"1",  //key
          Global.getSettings().getHullModSpec(ID).spriteName,  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("aEP_SoftfluxDissipate04")  + (bonus-punish).toInt(),  //data
          (punish - bonus) >= 0
        )
      }

      checkTracker.advance(amount)
      if(!checkTracker.intervalElapsed()) return
      heatingLevel = aEP_MarkerDissipation.getBufferLevel(ship)
      //根据预热程度，增加浮动幅散
      ship.mutableStats.fluxDissipation.modifyFlat(ID_B, bonus)
      ship.mutableStats.fluxDissipation.modifyFlat(ID_P,-punish)

    }
  }
}