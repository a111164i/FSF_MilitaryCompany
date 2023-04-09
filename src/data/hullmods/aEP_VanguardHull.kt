package data.hullmods

import com.fs.starfarer.api.Global
import data.hullmods.aEP_BaseHullMod
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import java.util.HashMap
import data.hullmods.aEP_VanguardHull
import com.fs.starfarer.api.combat.ShipAPI
import data.hullmods.aEP_VanguardHull.aEP_VanguardDamageTaken
import com.fs.starfarer.api.ui.TooltipMakerAPI
import combat.util.aEP_DataTool
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageAPI
import org.lwjgl.util.vector.Vector2f
import org.lazywizard.lazylib.MathUtils
import com.fs.starfarer.api.combat.BeamAPI
import data.hullmods.aEP_MarkerDissipation
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_Tool
import java.awt.Color

class aEP_VanguardHull : aEP_BaseHullMod() {
  companion object {
    const val REDUCE_PERCENT = 0.70f
    const val FLUX_REDUCE_PER_HIT = 4f
    const val BEAM_PER_HIT_REDUCE_COMPROMISE = 0.2f
    private val REDUCE_AMOUNT = HashMap<HullSize, Float>()

    init {
      REDUCE_AMOUNT[HullSize.FIGHTER] = 0f
      REDUCE_AMOUNT[HullSize.FRIGATE] = 20f
      REDUCE_AMOUNT[HullSize.DESTROYER] = 20f
      REDUCE_AMOUNT[HullSize.CRUISER] = 20f
      REDUCE_AMOUNT[HullSize.CAPITAL_SHIP] = 30f
    }
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    ship.mutableStats.dynamic.getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(id, 1000f)
    if (!ship.hasListenerOfClass(aEP_VanguardDamageTaken::class.java)) {
      ship.addListener(aEP_VanguardDamageTaken(REDUCE_AMOUNT[ship.hullSize]!!.toFloat(), ship))
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
    tooltip.addPara("{%s}"+ txt("VA_des01"), 5f, arrayOf(Color.green), aEP_ID.HULLMOD_POINT,  String.format("%.0f", REDUCE_PERCENT * 100f), REDUCE_AMOUNT[hullSize]?.toInt()?.toString()?:0f.toString())

    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor,Alignment.MID, 5f)
    val image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec("aEP_VanguardHull").spriteName, 48f)
    image.addPara("{%s}"+ txt("reduce_flux_per_hit"), 5f, arrayOf(Color.green), aEP_ID.HULLMOD_POINT,  String.format("%.1f", FLUX_REDUCE_PER_HIT))
    tooltip.addImageWithText(5f)

  }

  internal class aEP_VanguardDamageTaken(reduceAmount: Float, ship: ShipAPI) : DamageTakenModifier, AdvanceableListener {
    var ship: ShipAPI = ship
    var id = "aEP_VanguardDamageTaken"
    var reduceAmount = reduceAmount
    val checkTracker = IntervalUtil(0.25f,0.25f)
    var heatingLevel = 0f

    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      if (shieldHit) return null
      if (MathUtils.getRandomNumberInRange(0f, 1f) >= REDUCE_PERCENT) return null
      var toReduce = reduceAmount
      var realDamage = damage.damage
      //对于激光，每秒伤害被分为10段，这里这里认为每4次tick等同一个弹丸，每次减伤为正常的0.25倍，
      if (param is BeamAPI) toReduce *= BEAM_PER_HIT_REDUCE_COMPROMISE
      //不可减到0伤害，故为-1
      toReduce = Math.min(toReduce, (damage.damage - 1f).coerceAtLeast(0f) )

      //这个modifier的基础值1f，实际伤害 = modifier * baseDamage
      val modifierFlatChange =toReduce/(realDamage+0.1f)
      damage.modifier.modifyFlat(id, -modifierFlatChange)
      //生成绿字
      Global.getCombatEngine().addFloatingDamageText(
        point,
        modifierFlatChange * realDamage,
        Color(100, 250, 150, 180),
        target,
        null
      )

      //如果舰船预热完全，每次生效都会减少幅能
      if (heatingLevel >= 1f){
        ship.fluxTracker.decreaseFlux(FLUX_REDUCE_PER_HIT)
      }
      return id
    }

    override fun advance(amount: Float) {
      checkTracker.advance(amount)
      if(!checkTracker.intervalElapsed()) return
      //根据受益者的预热程度，更新转换率
      heatingLevel = aEP_MarkerDissipation.getBufferLevel(ship)
    }

  }

  init {
    haveToBeWithMod.add("aEP_MarkerDissipation")
    notCompatibleList.add("reinforcedhull")
  }
}