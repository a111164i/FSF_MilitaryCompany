package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.*
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.util.ColorShifter
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import org.lazywizard.lazylib.MathUtils
import java.awt.Color
import java.util.HashMap

class aEP_ShieldFloating : aEP_BaseHullMod() {

  companion object{
    const val ID = "aEP_ShieldFloating"
    const val MAX_SHUNT_PERCENT = 100f
    val SHIFT_COLOR = Color(155,25,255,205)

    //下降是持续的，如果希望6秒内打满缓冲区，触发满加成，应该写12
    //达到最大分流加成所需的波动值
    private val MAX_THRESHOLD = HashMap<ShipAPI.HullSize, Float>()
    init {
      MAX_THRESHOLD[ShipAPI.HullSize.FIGHTER] = 200f
      MAX_THRESHOLD[ShipAPI.HullSize.FRIGATE] = 600f
      MAX_THRESHOLD[ShipAPI.HullSize.DESTROYER] = 900f
      MAX_THRESHOLD[ShipAPI.HullSize.CRUISER] = 1500f
      MAX_THRESHOLD[ShipAPI.HullSize.CAPITAL_SHIP] = 2400f
    }

    //波动值下降速度
    private val DROP_SPEED = HashMap<ShipAPI.HullSize, Float>()
    init {
      DROP_SPEED[ShipAPI.HullSize.FIGHTER] = 400f
      DROP_SPEED[ShipAPI.HullSize.FRIGATE] = 600f
      DROP_SPEED[ShipAPI.HullSize.DESTROYER] = 950f
      DROP_SPEED[ShipAPI.HullSize.CRUISER] = 1800f
      DROP_SPEED[ShipAPI.HullSize.CAPITAL_SHIP] = 2750f
    }

    //最高波动值大小
    private val CAP = HashMap<ShipAPI.HullSize, Float>()
    init {
      CAP[ShipAPI.HullSize.FIGHTER] = 1000f
      CAP[ShipAPI.HullSize.FRIGATE] = 1500f
      CAP[ShipAPI.HullSize.DESTROYER] = 2500f
      CAP[ShipAPI.HullSize.CRUISER] = 5000f
      CAP[ShipAPI.HullSize.CAPITAL_SHIP] = 7500f
    }

    private val DISS_BONUS = HashMap<ShipAPI.HullSize, Float>()
    init {
      DISS_BONUS[ShipAPI.HullSize.FIGHTER] = 50f
      DISS_BONUS[ShipAPI.HullSize.FRIGATE] = 300f
      DISS_BONUS[ShipAPI.HullSize.DESTROYER] = 350f
      DISS_BONUS[ShipAPI.HullSize.CRUISER] = 575f
      DISS_BONUS[ShipAPI.HullSize.CAPITAL_SHIP] = 800f
    }

  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.HARDENED_SHIELDS)
    notCompatibleList.add(aEP_ShieldControlled.ID)

    allowOnHullsize[ShipAPI.HullSize.FRIGATE] = true
    allowOnHullsize[ShipAPI.HullSize.DESTROYER] = true
    allowOnHullsize[ShipAPI.HullSize.CRUISER] = true
    allowOnHullsize[ShipAPI.HullSize.CAPITAL_SHIP] = true

    requireShield = true
  }


  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    if (ship.shield == null || ship.shield.type == ShieldAPI.ShieldType.NONE) {
      return
    }


    if(!ship.hasListenerOfClass(ShieldDamageListener::class.java)){
      val threshold = MAX_THRESHOLD[ship.hullSize]?: 2000f
      val drop = DROP_SPEED[ship.hullSize]?:2000f
      val max = CAP[ship.hullSize]?: 4000f
      ship.addListener(ShieldDamageListener(ship, max, threshold, drop))
      ship.setCustomData(ID,1f)
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

    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f)
    //正面
    addPositivePara(tooltip, "aEP_ShieldFloating02", arrayOf(
      txt("aEP_ShieldFloating01")
    ))
    addPositivePara(tooltip, "aEP_ShieldFloating04", arrayOf(
      txt("aEP_ShieldFloating01"),
      String.format("+%.0f", DISS_BONUS[hullSize]),
      String.format("+%.0f", MAX_SHUNT_PERCENT) +"%"
    ))

    //列表展示关于波动值的数据
    val col2W0 = width * 0.5f
    //第一列显示的名称，尽可能可能的长
    val col1W0 = (width - col2W0 - PARAGRAPH_PADDING_BIG)
    tooltip.beginTable(
      factionColor, factionDarkColor, factionBrightColor,
      TEXT_HEIGHT_SMALL, true, true,
      *arrayOf<Any>(
        txt("aEP_ShieldFloating01"), col1W0,
        "Statistics", col2W0)
    )
    tooltip.addRow(
      Alignment.MID, highlight, txt("max"),
      Alignment.MID, highlight, String.format("%.0f", CAP[hullSize]),
    )
    tooltip.addRow(
      Alignment.MID, highlight, txt("aEP_ShieldFloating03"),
      Alignment.MID, highlight, String.format("%.0f", MAX_THRESHOLD[hullSize]),
    )
    tooltip.addRow(
      Alignment.MID, highlight,  txt("decrease")+txt("speed"),
      Alignment.MID, negativeHighlight, String.format("-%.0f /s", DROP_SPEED[hullSize]),
    )
    tooltip.addTable("", 0, PARAGRAPH_PADDING_SMALL)



    //显示不兼容插件
    showIncompatible(tooltip)


    //tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor, Alignment.MID, 5f)
    //val image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec(ID).spriteName, 48f)


    //tooltip.addImageWithText(5f)

    //额外灰色说明
    //tooltip.addPara(aEP_DataTool.txt("aEP_RapidDissipate02"), Color.gray, 5f)

  }

  internal class ShieldDamageListener(val ship: ShipAPI, val max:Float, val threshold: Float, val dropSpeed:Float) : DamageListener, AdvanceableListener {
    private var accumlated = 0f
    private val timer = IntervalUtil(0.25f,0.25f)
    private val shifter = ColorShifter(ship.shield.innerColor)
    private val ringShifter = ColorShifter(ship.shield.ringColor)
    private val dissipationBonus = DISS_BONUS[ship.hullSpec.hullSize]?:300f
    var level = 0f
    override fun reportDamageApplied(source: Any?, target: CombatEntityAPI?, result: ApplyDamageResultAPI?) {
      //谨防有人中途取消护盾
      ship.shield?:return

      val actualDamageToShield = result?.damageToShields?:0f
      accumlated += actualDamageToShield
      accumlated = MathUtils.clamp(accumlated, 0f, max)
      level = accumlated/threshold
      level = MathUtils.clamp(level,0f,1f)
    }

    override fun advance(amount: Float) {
      //谨防有人中途取消护盾
      ship.shield?:return

      //to，durIn, durOut, shiftPercent
      shifter.shift(ID,SHIFT_COLOR,0.001f,0.25f,0.6f * level)
      ringShifter.shift(ID,SHIFT_COLOR,0.001f,0.25f,0.6f * level)
      shifter.advance(amount)
      ringShifter.advance(amount)
      ship.shield.innerColor = shifter.curr
      ship.shield.ringColor = ringShifter.curr

      timer.advance(amount)
      if(timer.intervalElapsed()){
        level = accumlated/threshold
        level = MathUtils.clamp(level,0f,1f)
        ship.mutableStats.hardFluxDissipationFraction.modifyFlat(ID, level * MAX_SHUNT_PERCENT/100f)
        ship.mutableStats.fluxDissipation.modifyFlat(ID, level * dissipationBonus)
        accumlated -= dropSpeed * timer.elapsed
        accumlated = MathUtils.clamp(accumlated, 0f, max)
      }


      //维持玩家左下角的提示
      if (Global.getCombatEngine().playerShip == ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName+"1",  //key
          Global.getSettings().getSpriteName("aEP_ui", ID),  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("level")
            +": "+String.format("%.0f", level * MAX_SHUNT_PERCENT)+"%"
            +" "+String.format("+%.0f", level*dissipationBonus)
            +" "+String.format("%.0f / %.0f", accumlated, max),  //data
          false
        )
      }

    }


  }

}