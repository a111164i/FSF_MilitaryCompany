package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import java.util.HashMap
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import combat.util.aEP_DataTool
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageAPI
import org.lwjgl.util.vector.Vector2f
import org.lazywizard.lazylib.MathUtils
import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import java.awt.Color

class aEP_VanguardHull : aEP_BaseHullMod() {
  companion object {
    //光束一秒结算10次，每次的伤害减免要乘一个系数
    const val BEAM_PER_HIT_REDUCE_COMPROMISE = 0.2f

    const val REDUCE_PERCENT = 0.60f
    const val FLUX_REDUCE_PER_HIT = 4f
    private val REDUCE_AMOUNT = HashMap<HullSize, Float>()
    init {
      REDUCE_AMOUNT[HullSize.FIGHTER] = 0f
      REDUCE_AMOUNT[HullSize.FRIGATE] = 20f
      REDUCE_AMOUNT[HullSize.DESTROYER] = 20f
      REDUCE_AMOUNT[HullSize.CRUISER] = 20f
      REDUCE_AMOUNT[HullSize.CAPITAL_SHIP] = 30f
    }

    const val SMOD_REDUCE_PERCENT = 0.50f
    const val SMOD_FLUX_REDUCE_PER_HIT = 8f
    private val SMOD_REDUCE_AMOUNT = HashMap<HullSize, Float>()
    init {
      SMOD_REDUCE_AMOUNT[HullSize.FIGHTER] = 0f
      SMOD_REDUCE_AMOUNT[HullSize.FRIGATE] = 40f
      SMOD_REDUCE_AMOUNT[HullSize.DESTROYER] = 40f
      SMOD_REDUCE_AMOUNT[HullSize.CRUISER] = 45f
      SMOD_REDUCE_AMOUNT[HullSize.CAPITAL_SHIP] = 50f
    }
    const val ID = "aEP_VanguardHull"
  }

  init {
    haveToBeWithMod.add("aEP_MarkerDissipation")
    notCompatibleList.add("reinforcedhull")
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    ship.mutableStats.dynamic.getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(id, 1000f)
    if (!ship.hasListenerOfClass(aEP_VanguardDamageTaken::class.java)) {
      if(isSMod(ship)){
        ship.addListener(aEP_VanguardDamageTaken(
          SMOD_REDUCE_AMOUNT[ship.hullSize]?:0f,
          SMOD_REDUCE_PERCENT,
          SMOD_FLUX_REDUCE_PER_HIT,
          ship))

      }else{
        ship.addListener(aEP_VanguardDamageTaken(
          REDUCE_AMOUNT[ship.hullSize]?:0f,
          REDUCE_PERCENT,
          FLUX_REDUCE_PER_HIT,
          ship))
      }

    }
  }

  internal class aEP_VanguardDamageTaken(val reduceAmount: Float,val reduceChance: Float, val fluxReduce: Float, val ship: ShipAPI) : DamageTakenModifier, AdvanceableListener {
    val checkTracker = IntervalUtil(0.25f,0.25f)
    var heatingLevel = 0f

    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      if (shieldHit) return null
      if (MathUtils.getRandomNumberInRange(0f, 1f) >= reduceChance) return null
      var toReduce = reduceAmount
      var realDamage = damage.damage
      //对于激光，每秒伤害被分为10段，这里这里认为每4次tick等同一个弹丸，每次减伤为正常的0.25倍，
      if (param is BeamAPI) toReduce *= BEAM_PER_HIT_REDUCE_COMPROMISE
      //不可减到0伤害，故为-1
      toReduce = Math.min(toReduce, (damage.damage - 1f).coerceAtLeast(0f) )

      //这个modifier的基础值1f，实际伤害 = modifier * baseDamage
      val modifierFlatChange =toReduce/(realDamage+0.1f)
      damage.modifier.modifyFlat(ID, -modifierFlatChange)
      //生成绿字
      Global.getCombatEngine().addFloatingDamageText(
        point,
        modifierFlatChange * realDamage,
        Color(100, 250, 150, 180),
        target,
        null
      )

      //如果舰船预热完全，每次生效都会减少幅能
      if (heatingLevel >= 0.95f){
        ship.fluxTracker.decreaseFlux(fluxReduce)
      }
      return ID
    }

    override fun advance(amount: Float) {
      checkTracker.advance(amount)
      if(!checkTracker.intervalElapsed()) return
      //根据受益者的预热程度，更新转换率
      heatingLevel = aEP_MarkerDissipation.getBufferLevel(ship)
    }

  }


  //在学习船插的时候，ship的参数可能为null
  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  //在船插还是物品的的时候，ship的参数可能为null
  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    //tooltip.addGrid( 5 * 5f + 10f);
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("{%s}"+ txt("aEP_VanguardHull01"), 5f, arrayOf(Color.green,highLight,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", REDUCE_PERCENT * 100f)+"%",
      (REDUCE_AMOUNT[hullSize]?:0f).toInt().toString())

    //显示不兼容插件
    tooltip.addPara("{%s}"+txt("not_compatible")+"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))


    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor,Alignment.MID, 5f)
    tooltip.addPara("{%s}"+ txt("aEP_VanguardHull02"), 5f, arrayOf(Color.green,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.1f", FLUX_REDUCE_PER_HIT))
  }

  override fun addSModEffectSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean, isForBuildInList: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    //Smod自带一个绿色的标题，不需要再来个标题
    //tooltip.addSectionHeading(aEP_DataTool.txt("effect"),Alignment.MID, 5f)

    tooltip.addPara("{%s}"+ txt("aEP_VanguardHull04"), 5f, arrayOf(Color.green,highLight,highLight),
      aEP_ID.HULLMOD_POINT,
      (REDUCE_AMOUNT[hullSize]?:0f).toInt().toString(),
      (SMOD_REDUCE_AMOUNT[hullSize]?:0f).toInt().toString())
    tooltip.addPara("{%s}"+ txt("aEP_VanguardHull05"), 5f, arrayOf(Color.green,highLight,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", FLUX_REDUCE_PER_HIT),
      String.format("%.0f", SMOD_FLUX_REDUCE_PER_HIT))
    tooltip.addPara("{%s}"+ txt("aEP_VanguardHull03"), 5f, arrayOf(Color.red,highLight,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", REDUCE_PERCENT * 100f) + "%",
      String.format("%.0f", SMOD_REDUCE_PERCENT * 100f) +"%")

  }
}