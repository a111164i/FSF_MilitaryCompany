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
import com.fs.starfarer.api.impl.campaign.ids.HullMods
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

    const val REDUCE_CHANCE = 0.60f
    private val REDUCE_AMOUNT = HashMap<HullSize, Float>()
    init {
      REDUCE_AMOUNT[HullSize.FIGHTER] = 0f
      REDUCE_AMOUNT[HullSize.FRIGATE] = 20f
      REDUCE_AMOUNT[HullSize.DESTROYER] = 20f
      REDUCE_AMOUNT[HullSize.CRUISER] = 20f
      REDUCE_AMOUNT[HullSize.CAPITAL_SHIP] = 25f
    }

    const val SMOD_REDUCE_CHANCE = 0.50f
    private val SMOD_REDUCE_AMOUNT = HashMap<HullSize, Float>()
    init {
      SMOD_REDUCE_AMOUNT[HullSize.FIGHTER] = 0f
      SMOD_REDUCE_AMOUNT[HullSize.FRIGATE] = 40f
      SMOD_REDUCE_AMOUNT[HullSize.DESTROYER] = 40f
      SMOD_REDUCE_AMOUNT[HullSize.CRUISER] = 40f
      SMOD_REDUCE_AMOUNT[HullSize.CAPITAL_SHIP] = 45f
    }
    const val ID = "aEP_VanguardHull"
  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.REINFORCEDHULL)
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    ship.mutableStats.dynamic.getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(id, 1000f)
    if (!ship.hasListenerOfClass(aEP_VanguardDamageTaken::class.java)) {
      if(isSMod(ship)){
        ship.addListener(aEP_VanguardDamageTaken(
          SMOD_REDUCE_AMOUNT[ship.hullSize]?:0f,
          SMOD_REDUCE_CHANCE, ship))

      }else{
        ship.addListener(aEP_VanguardDamageTaken(
          REDUCE_AMOUNT[ship.hullSize]?:0f,
          REDUCE_CHANCE, ship))
      }

    }
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize?): String? {
    if (index == 0) return String.format("%.0f", REDUCE_CHANCE * 100f) +"%"
    if (index == 1) return String.format("%.0f", REDUCE_AMOUNT[hullSize]?: 20f)
    return null
  }

  override fun getSModDescriptionParam(index: Int, hullSize: HullSize?): String? {
    if (index == 0) return String.format("%.0f", SMOD_REDUCE_CHANCE * 100f) +"%"
    if (index == 1) return String.format("%.0f", SMOD_REDUCE_AMOUNT[hullSize]?: 40f)
    return null
  }

  internal class aEP_VanguardDamageTaken(val reduceAmount: Float, val reduceChance: Float, val ship: ShipAPI) : DamageTakenModifier {

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

      return ID
    }


  }

}