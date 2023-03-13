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
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.Alignment
import java.awt.Color

class aEP_VanguardHull : aEP_BaseHullMod() {
  companion object {
    const val REDUCE_PERCENT = 0.70f
    const val FLUX_REDUCE_PER_HIT = 4f
    const val BEAM_PER_HIT_REDUCE_COMPROMISE = 0.1f
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
    //remove itself if ship has not compatible hullmod
    if (!isApplicableToShip(ship)) {
      ship.variant.removeMod(id)
      return
    }
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
    //tooltip.addGrid( 5 * 5f + 10f);
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("- " + aEP_DataTool.txt("VA_des01"), 5f, Color.white, Color.green, String.format("%.0f", REDUCE_PERCENT * 100f), REDUCE_AMOUNT[hullSize]!!.toInt().toString() + "")
    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"), Alignment.MID, 5f)
    val image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec("aEP_VanguardHull").spriteName, 48f)
    image.addPara("- " + aEP_DataTool.txt("reduce_flux_per_hit"), 5f, Color.white, Color.green, String.format("%.1f", FLUX_REDUCE_PER_HIT))
    tooltip.addImageWithText(5f)
  }

  internal class aEP_VanguardDamageTaken(reduceAmount: Float, ship: ShipAPI) : DamageTakenModifier {
    var ship: ShipAPI
    var id = "aEP_VanguardDamageTaken"
    var reduceAmount = 0f
    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      //会null的
      param?: return null

      if (MathUtils.getRandomNumberInRange(0f, 1f) > REDUCE_PERCENT) return null
      if (shieldHit) return null
      val damageAmount = damage.damage
      var toReduce = Math.min(reduceAmount, damageAmount - 1)
      if (param is BeamAPI) {
        toReduce /= 4f
      }
      val convertToMult = toReduce / Math.max(damageAmount, 1f)
      //这个flat是修改的mult
      Global.getCombatEngine().addFloatingDamageText(
        point,
        toReduce,
        Color(100, 250, 150, 180),
        target,
        null
      )
      damage.modifier.modifyFlat(id, -convertToMult)
      //aEP_Tool.addDebugText(damage.getDamage()+"_"+toReduce);
      if (aEP_MarkerDissipation.getBufferLevel(ship) >= 1f) ship.fluxTracker.decreaseFlux(getFluxReduce(damage, toReduce / reduceAmount * FLUX_REDUCE_PER_HIT))
      return id
    }

    fun getFluxReduce(damage: DamageAPI, baseReduce: Float): Float {
      return if (damage.type == DamageType.FRAGMENTATION) {
        baseReduce / 4f
      } else baseReduce
    }

    init {
      this.reduceAmount = reduceAmount
      this.ship = ship
    }
  }

  init {
    haveToBeWithMod.add("aEP_MarkerDissipation")
    notCompatibleList.add("reinforcedhull")
  }
}