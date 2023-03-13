package data.shipsystems.scripts

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import combat.util.aEP_DataTool


class aEP_LADamper : BaseShipSystemScript() {
  val INCOMING_DAMAGE_CAPITAL = 0.5f
  private val EFFECT_ARMOR_FLAT_BONUS = 200f
  private val EFFECT_ARMOR_PERCENT_BONUS = 0.5f
  private val ARMOR_DAMAGE_REDUCE = 0.75f //by mult
  private val WEAPON_ROF_MOD_PERCENT = 75f

  private var ship: ShipAPI? = null

  override fun apply(stats: MutableShipStatsAPI, id: String?, state: ShipSystemStatsScript.State, effectLevel: Float) {
    ship = stats.entity as ShipAPI
    var convertedLevel = effectLevel
    if (state == ShipSystemStatsScript.State.ACTIVE) convertedLevel = 1f

    //在IN和OUT状态都也关闭护盾
    ship?.shield?.toggleOff()

    //modify here
    val toAdd = EFFECT_ARMOR_FLAT_BONUS + ship!!.hullSpec.armorRating * EFFECT_ARMOR_PERCENT_BONUS
    stats.effectiveArmorBonus.modifyFlat(id, toAdd * effectLevel)
    stats.armorDamageTakenMult.modifyMult(id, ARMOR_DAMAGE_REDUCE * effectLevel)
    stats.ballisticRoFMult.modifyPercent(id,WEAPON_ROF_MOD_PERCENT * effectLevel)
    stats.energyRoFMult.modifyPercent(id, WEAPON_ROF_MOD_PERCENT * effectLevel)

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String?) {
    ship = stats.entity as ShipAPI
    stats.effectiveArmorBonus.unmodify(id)
    stats.armorDamageTakenMult.unmodify(id)
    stats.ballisticRoFMult.unmodify(id)
    stats.energyRoFMult.unmodify(id)

  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State?, effectLevel: Float): StatusData? {
    if (index == 0) {
      val toAdd = EFFECT_ARMOR_FLAT_BONUS + ship!!.hullSpec.armorRating * EFFECT_ARMOR_PERCENT_BONUS
      return StatusData(aEP_DataTool.txt("aEP_LADamper01") + (toAdd * effectLevel).toInt(), false)
    } else if (index == 1) {
      val toAdd = WEAPON_ROF_MOD_PERCENT * effectLevel
      return StatusData(aEP_DataTool.txt("aEP_LADamper02") + (toAdd * effectLevel).toInt()+"%", false)
    }
    return null
  }
}