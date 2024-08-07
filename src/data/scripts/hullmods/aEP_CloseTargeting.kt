package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.WeaponAPI.*
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.skills.aEP_SkillAnalyze
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.pow

class aEP_CloseTargeting : aEP_BaseHullMod(), DamageDealtModifier, DamageTakenModifier {

  companion object{
    const val ID = "aEP_CloseTargeting"

    var PROJECTILE_SPEED_BONUS = 50f
    var SPREAD_REDUCE_MULT = 0.5f
    var DAMAGE_TAKEN_REDUCE_MULT = 0f

    val PD_WEAPON_RANGE_BONUS = HashMap<HullSize, Float>()
    init {
      PD_WEAPON_RANGE_BONUS[HullSize.CAPITAL_SHIP] = 60f
      PD_WEAPON_RANGE_BONUS[HullSize.CRUISER] = 60f
      PD_WEAPON_RANGE_BONUS[HullSize.DESTROYER] = 55f
      PD_WEAPON_RANGE_BONUS[HullSize.FRIGATE] = 50f
    }

  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.INTEGRATED_TARGETING_UNIT)
    notCompatibleList.add(HullMods.DEDICATED_TARGETING_CORE)
    notCompatibleList.add(HullMods.ADVANCED_TARGETING_CORE)

  }


  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize, stats: MutableShipStatsAPI, id: String) {
    stats.beamPDWeaponRangeBonus.modifyPercent(ID, PD_WEAPON_RANGE_BONUS[hullSize]?:60f)
    stats.nonBeamPDWeaponRangeBonus.modifyPercent(ID, PD_WEAPON_RANGE_BONUS[hullSize]?:60f)

    stats.ballisticProjectileSpeedMult.modifyPercent(ID, PROJECTILE_SPEED_BONUS)
    stats.energyProjectileSpeedMult.modifyPercent(ID, PROJECTILE_SPEED_BONUS)

    stats.recoilPerShotMult.modifyMult(ID,1f - SPREAD_REDUCE_MULT)
    stats.maxRecoilMult.modifyMult(ID,1f - SPREAD_REDUCE_MULT)


  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if(!ship.hasListenerOfClass(aEP_CloseTargeting::class.java)){
      //因为listener里面不需要变量，所以直接加入this
      ship.addListener(this)
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize): String? {
    if (index == 0) return String.format("+%.0f", PD_WEAPON_RANGE_BONUS[hullSize]?: 60f) +"%"
    if (index == 1) return String.format("-%.0f", SPREAD_REDUCE_MULT * 100f) +"%"
    if (index == 2) return String.format("+%.0f", PROJECTILE_SPEED_BONUS) +"%"
    //if (index == 3) return String.format("-%.0f", DAMAGE_TAKEN_REDUCE_MULT * 100f) +"%"
    return null
  }


  override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
    var source  = 1234

    //拿到自己的owner
    if(param is ShipAPI) source = param.owner
    if(param is EmpArcEntityAPI) source = param.owner
    if(param is BeamAPI) source = param.weapon?.ship?.owner?:1234
    if(param is DamagingProjectileAPI) source = param.weapon?.ship?.owner?:1234

    //如果自己的owner等于目标的owner，不造成伤害（误伤）
    if(target is ShipAPI && target.owner == source){
      damage.modifier.modifyMult(ID, 0.01f)
      return ID
    }
    return null
  }

  override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f?, shieldHit: Boolean): String? {

    if(param is ShipAPI) {
      if(param.isFighter){
        damage.modifier.modifyMult(ID, 1f - DAMAGE_TAKEN_REDUCE_MULT)
        return ID
      }
    }
    if(param is BeamAPI) {
      if(param.source?.isFighter == true){
        damage.modifier.modifyMult(ID, 1f - DAMAGE_TAKEN_REDUCE_MULT)
        return ID
      }
    }
    if(param is DamagingProjectileAPI) {
      //来自飞机，或者是导弹
      if(param.source?.isFighter == true || param is MissileAPI || param.isFromMissile){
        damage.modifier.modifyMult(ID, 1f - DAMAGE_TAKEN_REDUCE_MULT)
        return ID
      }
    }

    return null
  }
}
