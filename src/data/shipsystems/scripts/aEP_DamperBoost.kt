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
import combat.util.aEP_DecoMoveController
import java.awt.Color

class aEP_DamperBoost : BaseShipSystemScript() {
  companion object{
    val ROF_MAG = HashMap<String,Float>()
    init {
      ROF_MAG["aEP_fga_raoliu"] = 50f;
      ROF_MAG["aEP_des_youjiyan"] = 50f;
      ROF_MAG["aEP_des_youjiyan_mk2"] = 50f;
      ROF_MAG["aEP_des_lianliu"] = 50f;
    }
    val EFFECT_ARMOR_FLAT_BONUS = HashMap<String,Float>()
    init {
      EFFECT_ARMOR_FLAT_BONUS["aEP_fga_raoliu"] = 200f;
      EFFECT_ARMOR_FLAT_BONUS["aEP_des_youjiyan"] = 200f;
      EFFECT_ARMOR_FLAT_BONUS["aEP_des_youjiyan_mk2"] = 200f;
      EFFECT_ARMOR_FLAT_BONUS["aEP_des_lianliu"] = 200f;
    }
    val EFFECT_ARMOR_PERCENT_BONUS = HashMap<String,Float>()
    init {
      EFFECT_ARMOR_PERCENT_BONUS["aEP_fga_raoliu"] = 50f;
      EFFECT_ARMOR_PERCENT_BONUS["aEP_des_youjiyan"] = 50f;
      EFFECT_ARMOR_PERCENT_BONUS["aEP_des_youjiyan_mk2"] = 50f;
      EFFECT_ARMOR_PERCENT_BONUS["aEP_des_lianliu"] = 50f;
    }
    val ARMOR_DAMAGE_TAKEN_MULT = HashMap<String,Float>()
    init {
      ARMOR_DAMAGE_TAKEN_MULT["aEP_fga_raoliu"] = 0.5f;
      ARMOR_DAMAGE_TAKEN_MULT["aEP_des_youjiyan"] = 0.5f;
      ARMOR_DAMAGE_TAKEN_MULT["aEP_des_youjiyan_mk2"] = 0.5f;
      ARMOR_DAMAGE_TAKEN_MULT["aEP_des_lianliu"] = 0.5f;
    }

    const val SMALL_FOLD_BRIDGE_SHELL = "aEP_small_fold_bridgeshell"
    const val SMALL_FOLD_ARMOR = "aEP_small_fold_armor"
    const val SMALL_FOLD_BELOW = "aEP_small_fold_below"

    const val LARGE_FOLD_ARMOR = "aEP_cap_shangshengliu_armor"
    const val LARGE_FOLD_BELOW = "aEP_cap_shangshengliu_armor_dark"

    val DAMPER_JITTER_COLOR = Color (255,165,90,65)

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

      //0-0.5时暗面伸出，亮面隐形
      if (convertedLevel < 0.5f) {
        val level = MathUtils.clamp(convertedLevel * 2f, 0f, 1f)
        if (w.spec.weaponId == SMALL_FOLD_ARMOR || w.spec.weaponId == LARGE_FOLD_ARMOR) {
          w.animation.frame = 0
          anima.decoMoveController.range = 5f
          anima.decoMoveController.speed = 2f
          anima.decoMoveController.effectiveLevel = level
        }
        if (w.spec.weaponId == SMALL_FOLD_BELOW || w.spec.weaponId == LARGE_FOLD_BELOW) {
          w.animation.frame = 1
          anima.decoMoveController.range = 5f
          anima.decoMoveController.speed = 2f
          anima.decoMoveController.effectiveLevel = level
        }

      //0.5-1时亮面回缩，暗面隐形，同时盖上舰桥装甲
      } else {
        val level = MathUtils.clamp(2f - convertedLevel * 2f, 0f, 1f)
        if (w.spec.weaponId == SMALL_FOLD_ARMOR || w.spec.weaponId == LARGE_FOLD_ARMOR) {
          w.animation.frame = 1
          anima.decoMoveController.range = 5f
          anima.decoMoveController.speed = 2f
          anima.decoMoveController.effectiveLevel = level
        }
        if (w.spec.weaponId == SMALL_FOLD_BELOW || w.spec.weaponId == LARGE_FOLD_BELOW) {
          w.animation.frame = 0
          anima.decoMoveController.range = 5f
          anima.decoMoveController.speed = 2f
          anima.decoMoveController.effectiveLevel = level
        }

        ship.setJitter(this,DAMPER_JITTER_COLOR,1f-level,1,0f)
      }
      if (w.spec.weaponId == SMALL_FOLD_BRIDGE_SHELL) anima.setMoveToLevel(effectLevel)
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
    stats.ballisticRoFMult.unmodify(id)
    stats.energyRoFMult.unmodify(id)

    stats.armorDamageTakenMult.unmodify(id)
    stats.hullDamageTakenMult.unmodify(id)

    for (w in ship.allWeapons) {
      if (!w.slot.isDecorative) continue
      if(w.effectPlugin !is aEP_DecoAnimation) continue
      val anima = w.effectPlugin as aEP_DecoAnimation
      if (w.spec.weaponId == SMALL_FOLD_ARMOR || w.spec.weaponId == LARGE_FOLD_ARMOR) {
        w.animation.frame = 0
        anima.setMoveToLevel(0f)
      }
      if (w.spec.weaponId == SMALL_FOLD_BELOW || w.spec.weaponId == LARGE_FOLD_BELOW) {
        w.animation.frame = 0
        anima.setMoveToLevel(0f)
      }
      if (w.spec.weaponId == SMALL_FOLD_BRIDGE_SHELL) anima.setMoveToLevel(0f)
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