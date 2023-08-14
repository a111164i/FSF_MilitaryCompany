package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.*
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.util.ColorShifter
import combat.impl.aEP_BaseCombatEffect
import combat.util.aEP_DataTool
import combat.util.aEP_ID
import jdk.jfr.Threshold
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.HashMap

class aEP_ResistShield internal constructor() : aEP_BaseHullMod() {

  companion object{
    const val ID = "aEP_ResistShield"
    const val MAX_SHUNT_PERCENT = 100f
    //下降是持续的，如果希望6秒内打满缓冲区，触发满加成，应该写12
    val SHIFT_COLOR = Color(155,25,255,195)

    private val MAX_THRESHOLD = HashMap<ShipAPI.HullSize, Float>()
    init {
      MAX_THRESHOLD[ShipAPI.HullSize.FIGHTER] = 200f
      MAX_THRESHOLD[ShipAPI.HullSize.FRIGATE] = 600f
      MAX_THRESHOLD[ShipAPI.HullSize.DESTROYER] = 900f
      MAX_THRESHOLD[ShipAPI.HullSize.CRUISER] = 1500f
      MAX_THRESHOLD[ShipAPI.HullSize.CAPITAL_SHIP] = 2400f
    }

    private val DROP_SPEED = HashMap<ShipAPI.HullSize, Float>()
    init {
      DROP_SPEED[ShipAPI.HullSize.FIGHTER] = 400f
      DROP_SPEED[ShipAPI.HullSize.FRIGATE] = 600f
      DROP_SPEED[ShipAPI.HullSize.DESTROYER] = 1000f
      DROP_SPEED[ShipAPI.HullSize.CRUISER] = 2000f
      DROP_SPEED[ShipAPI.HullSize.CAPITAL_SHIP] = 3000f
    }

    private val CAP = HashMap<ShipAPI.HullSize, Float>()
    init {
      CAP[ShipAPI.HullSize.FIGHTER] = 800f
      CAP[ShipAPI.HullSize.FRIGATE] = 1200f
      CAP[ShipAPI.HullSize.DESTROYER] = 2000f
      CAP[ShipAPI.HullSize.CRUISER] = 4000f
      CAP[ShipAPI.HullSize.CAPITAL_SHIP] = 6000f
    }


  }

  init {
    haveToBeWithMod.add("aEP_MarkerDissipation")
    notCompatibleList.add("hardenedshieldemitter")
    notCompatibleList.add(aEP_ControledShield.ID)
  }


  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    if (ship.shield == null || ship.shield.type == ShieldAPI.ShieldType.NONE) {
      return
    }

    if(!ship.customData.containsKey(ID)){
      val threshold = MAX_THRESHOLD[ship.hullSize]?: 2000f
      val drop = DROP_SPEED[ship.hullSize]?:2000f
      val max = CAP[ship.hullSize]?: 4000f
      ship.listenerManager.addListener(ShieldDamageListener(ship, max, threshold, drop))
      ship.setCustomData(ID,1f)
    }
  }

  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return  true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {

    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    //显示不兼容插件
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_ResistShield01"), 5f, arrayOf(Color.green),
      aEP_ID.HULLMOD_POINT,
      aEP_DataTool.txt("aEP_ResistShield04"))
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_ResistShield02"), 5f, arrayOf(Color.green),
      aEP_ID.HULLMOD_POINT,
      aEP_DataTool.txt("aEP_ResistShield04"),
      String.format("%.0f", MAX_THRESHOLD[hullSize]?:1000f),
      String.format("%.0f", MAX_SHUNT_PERCENT)+"%")
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_ResistShield03"), 5f, arrayOf(Color.green),
      aEP_ID.HULLMOD_POINT,
      aEP_DataTool.txt("aEP_ResistShield04"),
      String.format("%.0f", (DROP_SPEED[hullSize]?:1000f)))

    //显示不兼容插件
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("not_compatible") +"{%s}", 5f, arrayOf(Color.red, highLight),
      aEP_ID.HULLMOD_POINT,
      showModName(notCompatibleList))


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
        accumlated -= dropSpeed * timer.elapsed
        accumlated = MathUtils.clamp(accumlated, 0f, max)
      }


      //维持玩家左下角的提示
      if (Global.getCombatEngine().playerShip == ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName+"1",  //key
          Global.getSettings().getSpriteName("aEP_ui", ID),  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("aEP_ResistShield05") +String.format("%.0f", accumlated) +"  " +String.format("%.0f", level * MAX_SHUNT_PERCENT)+"%",  //data
          false
        )
      }

    }


  }

}