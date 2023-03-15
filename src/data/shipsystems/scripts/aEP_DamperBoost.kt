package data.shipsystems.scripts

import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import data.shipsystems.scripts.aEP_DamperBoost
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import combat.util.aEP_DataTool
import java.awt.Color

class aEP_DamperBoost : BaseShipSystemScript() {
  companion object{
    val ROF_MAG = HashMap<String,Float>()
    init {
      ROF_MAG["aEP_cov_RaoLiu"] = 50f;
      ROF_MAG["aEP_YouJiYan"] = 50f;
      ROF_MAG["aEP_YouJiYan_mk2"] = 50f;
      ROF_MAG["aEP_LiAnLiu"] = 50f;
    }
    val EFFECT_ARMOR_FLAT_BONUS = HashMap<String,Float>()
    init {
      EFFECT_ARMOR_FLAT_BONUS["aEP_fga_raoliu"] = 200f;
      EFFECT_ARMOR_FLAT_BONUS["aEP_YouJiYan"] = 200f;
      EFFECT_ARMOR_FLAT_BONUS["aEP_YouJiYan_mk2"] = 200f;
      EFFECT_ARMOR_FLAT_BONUS["aEP_LiAnLiu"] = 200f;
    }
    val EFFECT_ARMOR_PERCENT_BONUS = HashMap<String,Float>()
    init {
      EFFECT_ARMOR_PERCENT_BONUS["aEP_fga_raoliu"] = 50f;
      EFFECT_ARMOR_PERCENT_BONUS["aEP_YouJiYan"] = 50f;
      EFFECT_ARMOR_PERCENT_BONUS["aEP_YouJiYan_mk2"] = 50f;
      EFFECT_ARMOR_PERCENT_BONUS["aEP_LiAnLiu"] = 50f;
    }
    val ARMOR_DAMAGE_TAKEN_MULT = HashMap<String,Float>()
    init {
      ARMOR_DAMAGE_TAKEN_MULT["aEP_fga_raoliu"] = 0.5f;
      ARMOR_DAMAGE_TAKEN_MULT["aEP_YouJiYan"] = 0.5f;
      ARMOR_DAMAGE_TAKEN_MULT["aEP_YouJiYan_mk2"] = 0.5f;
      ARMOR_DAMAGE_TAKEN_MULT["aEP_LiAnLiu"] = 0.5f;
    }
  }



  private lateinit var ship: ShipAPI
  private val id = "aEP_DamperBoost"
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    ship = (stats.entity?:return) as ShipAPI
    var convertedLevel = effectLevel
    for (w in ship.allWeapons) {
      if (!w.slot.isDecorative) continue
      if(w.effectPlugin !is aEP_DecoAnimation) continue
      val anima = w.effectPlugin as aEP_DecoAnimation
      //extend out
      if (convertedLevel < 0.5f) {
        val level = MathUtils.clamp(convertedLevel * 2f, 0f, 1f)
        if (w.spec.weaponId == "aEP_raoliu_armor" || w.spec.weaponId == "aEP_shangshengliu_armor") {
          w.sprite.color = Color(0, 0, 0, 0)
          anima.setMoveToLevel(level)
        }
        if (w.spec.weaponId == "aEP_raoliu_armor_dark" || w.spec.weaponId == "aEP_shangshengliu_armor_dark") {
          w.sprite.color = Color(255, 255, 255)
          anima.setMoveToLevel(level)
        }
        if (w.spec.weaponId == "aEP_raoliu_hull") {
          anima.setMoveToLevel(level)
        }
      } else {
        val level = MathUtils.clamp(2f - convertedLevel * 2f, 0f, 1f)
        if (w.spec.weaponId == "aEP_raoliu_armor" || w.spec.weaponId == "aEP_shangshengliu_armor") {
          val black = (255 * effectLevel).toInt()
          w.sprite.color = Color(black, black, black)
          anima.setMoveToLevel(level)
        }
        if (w.spec.weaponId == "aEP_raoliu_armor_dark" || w.spec.weaponId == "aEP_shangshengliu_armor_dark") {
          anima.setMoveToLevel(level)
        }
      }
      if (w.spec.weaponId == "aEP_raoliu_bridge") anima.setMoveToLevel(effectLevel)
    }

    //modify here
    val RofPercent = ROF_MAG[ship.hullSpec.hullId]?: 0f
    val damageTakenMult = ARMOR_DAMAGE_TAKEN_MULT[ship.hullSpec.hullId]?: 1f
    val armorFlat = EFFECT_ARMOR_FLAT_BONUS[ship.hullSpec.hullId]?: 0f
    val armorPercent = (EFFECT_ARMOR_PERCENT_BONUS[ship.hullSpec.hullId]?: 0f)
    val toAdd = armorFlat + (ship.hullSpec?.armorRating?:0f) * (armorPercent/100f)
    stats.effectiveArmorBonus.modifyFlat(id, toAdd * effectLevel)
    stats.ballisticRoFMult.modifyPercent(id,RofPercent * effectLevel)
    stats.energyRoFMult.modifyPercent(id, RofPercent * effectLevel)
    stats.armorDamageTakenMult.modifyMult(id, damageTakenMult)
    stats.hullDamageTakenMult.modifyMult(id, damageTakenMult)
  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    ship = (stats.entity?:return) as ShipAPI
    stats.effectiveArmorBonus.unmodify(id)
    stats.armorDamageTakenMult.unmodify(id)
    stats.hullDamageTakenMult.unmodify(id)
    stats.ballisticRoFMult.unmodify(id)
    stats.energyRoFMult.unmodify(id)

    for (w in ship.allWeapons) {
      if (!w.slot.isDecorative) continue
      if(w.effectPlugin !is aEP_DecoAnimation) continue
      val anima = w.effectPlugin as aEP_DecoAnimation
      if (w.spec.weaponId == "aEP_raoliu_armor" || w.spec.weaponId == "aEP_shangshengliu_armor") {
        w.sprite.color = Color(0, 0, 0, 0)
        anima.setMoveToLevel(0f)
      }
      if (w.spec.weaponId == "aEP_raoliu_armor_dark" || w.spec.weaponId == "aEP_shangshengliu_armor_dark") {
        w.sprite.color = Color(0, 0, 0, 0)
        anima.setMoveToLevel(0f)
      }
      if (w.spec.weaponId == "aEP_raoliu_hull") {
        anima.setMoveToLevel(0f)
      }
      if (w.spec.weaponId == "aEP_raoliu_bridge") anima.setMoveToLevel(0f)
    }
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): StatusData? {
    if (index == 0) {
      val armorFlat = EFFECT_ARMOR_FLAT_BONUS[ship?.hullSpec?.hullId]?: 0f
      val armorPercent = (EFFECT_ARMOR_PERCENT_BONUS[ship?.hullSpec?.hullId]?: 0f)
      val toAdd = armorFlat + (ship?.hullSpec?.armorRating?:0f) *  (armorPercent/100f)
      return StatusData(aEP_DataTool.txt("aEP_LADamper01") + (toAdd * effectLevel).toInt(), false)
    }else if (index == 1) {
      val toAdd = ROF_MAG[ship.hullSpec.hullId?:""]?:0f
      return StatusData(aEP_DataTool.txt("aEP_LADamper02") + (toAdd * effectLevel).toInt()+"%", false)
    }
    return null
  }

}