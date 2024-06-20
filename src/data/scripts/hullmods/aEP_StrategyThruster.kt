package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_ID
import java.awt.Color

class aEP_StrategyThruster:aEP_BaseHullMod(), AdvanceableListener {
  companion object{
    const val ID = "aEP_StrategyThruster"
    const val ALLOW_LEVEL_BONUS = 10f

    const val FLUX_SHUNT_PERCENT_BONUS = 0f

    const val TIME_TO_MAX_BOOST = 1f

    val ZERO_FLUX_SPEED_BONUS = LinkedHashMap<ShipAPI.HullSize, Float>()
    const val DEFAULT_BOOST_BONUS = 0f
    init {
      ZERO_FLUX_SPEED_BONUS[ShipAPI.HullSize.CAPITAL_SHIP] = 0f
      ZERO_FLUX_SPEED_BONUS[ShipAPI.HullSize.CRUISER] = 0f
      ZERO_FLUX_SPEED_BONUS[ShipAPI.HullSize.DESTROYER] = 0f
      ZERO_FLUX_SPEED_BONUS[ShipAPI.HullSize.FRIGATE] = 0f
    }

    const val CAP_PUNISH_REDUCE_MULT = 0.1f

  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.UNSTABLE_INJECTOR)

    banShipList.add("aEP_cru_hailiang3")

    canInstallOnCarrier = false
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship.mutableStats.zeroFluxMinimumFluxLevel.modifyFlat(ID, ALLOW_LEVEL_BONUS/100f)
    ship.mutableStats.zeroFluxSpeedBoost.modifyFlat(ID, ZERO_FLUX_SPEED_BONUS[ship.hullSpec.hullSize]?: DEFAULT_BOOST_BONUS)
    ship.mutableStats.hardFluxDissipationFraction.modifyFlat(ID, FLUX_SHUNT_PERCENT_BONUS/100f)
    ship.mutableStats.fluxCapacity.modifyMult(ID, 1f - CAP_PUNISH_REDUCE_MULT)

    if(!ship.hasListenerOfClass(this::class.java)){
      val cls = aEP_StrategyThruster()
      cls.ship = ship
      cls.bonus = ZERO_FLUX_SPEED_BONUS[ship.hullSpec.hullSize]?: DEFAULT_BOOST_BONUS
      ship.addListener(cls)
    }

  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {

    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highlight = Misc.getHighlightColor()
    val negativeHighlight = Misc.getNegativeHighlightColor()

    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()

    val titleTextColor: Color = faction.color
    val factionColor: Color = faction.baseUIColor
    val factionDarkColor = faction.darkUIColor
    val factionBrightColor = faction.brightUIColor

    //主效果
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    val bonus = ZERO_FLUX_SPEED_BONUS[hullSize]?: DEFAULT_BOOST_BONUS
    var nowSpeed = bonus+50f
    if(ship?.mutableStats?.zeroFluxSpeedBoost?.flatMods?.containsKey(ID) == true){
      nowSpeed = ship.mutableStats?.zeroFluxSpeedBoost?.modifiedValue?:nowSpeed
    }

//    addPositivePara(tooltip,"aEP_StrategyThruster01", arrayOf(
//      String.format("+%.0f", bonus),
//      String.format("%.0f", nowSpeed))
//    )

    val level = ALLOW_LEVEL_BONUS
    addPositivePara(tooltip,"aEP_StrategyThruster02", arrayOf(
      String.format("+%.0f", level)+"%",
      String.format("%.0f", ship?.mutableStats?.zeroFluxMinimumFluxLevel?.modifiedValue?.times(100f)?:level)+"%")
    )


    //负面
    addNegativePara(tooltip,"aEP_StrategyThruster03", arrayOf(
      String.format("-%.0f", CAP_PUNISH_REDUCE_MULT*100f)+"%")
    )

    //显示不兼容插件
    showIncompatible(tooltip)

    //预热完全后额外效果
    //tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor, Alignment.MID, 5f)

    //灰字额外说明
    //tooltip.addPara(aEP_DataTool.txt("MD_des04"), grayColor, 5f)

  }


  //----------------------------------------//
  //往下是listener
  //以下变量是给listener用
  var bonus = DEFAULT_BOOST_BONUS
  var ship:ShipAPI? = null
  var time = 0f
  override fun advance(amount: Float) {

    ship?:return
    val ship = ship as ShipAPI

    //防止在装配页面出现数据异常
    if(ship.fullTimeDeployed < 1f) return

    val level = time/ TIME_TO_MAX_BOOST

    //脱离0幅能加速时，buff消退
    if(!ship.isEngineBoostActive){
      time = (time - amount * 3f).coerceAtLeast(0f)
    }else{
      time = (time + amount).coerceAtMost(TIME_TO_MAX_BOOST)
      //改变颜色
      ship.engineController.fadeToOtherColor(ID, Color.green, null,1f,0.35f * level)

    }
  }

}