package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import combat.util.aEP_Tool.Util.velocity2Speed
import combat.util.aEP_Tool.Util.speed2Velocity
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import org.lwjgl.util.vector.Vector2f
import org.lazywizard.lazylib.MathUtils
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import java.awt.Color

class aEP_DroneDash : BaseShipSystemScript() {
  companion object {
    const val MAX_SPEED_BONUS = 200f
    const val TURNRATE_BONUS = 60f
    const val DAMAGE_TAKEN = 0.25f
    const val FLUX_PERCENT_BONUS = 100f

    const val ROF_BONUS_FLAT = 2f

    val AFTER_MERGE_COLOR = Color(255, 155, 155, 250)
    val SMOKE_MERGE_COLOR = Color(255, 250, 250, 125)

    const val ID = "aEP_DroneDash"
  }

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //stats的entity有可能为null
    val ship = (stats.entity?: return)as ShipAPI

    val amount = Global.getCombatEngine().elapsedInLastFrame * stats.timeMult.modifiedValue
    stats.maxSpeed.modifyFlat(id, Math.max(effectLevel, 0.5f) * MAX_SPEED_BONUS)
    stats.acceleration.modifyFlat(id, MAX_SPEED_BONUS * 2f)
    //stats.deceleration.modifyMult(id, 0f)
    stats.maxTurnRate.modifyFlat(id, TURNRATE_BONUS)
    stats.turnAcceleration.modifyFlat(id, TURNRATE_BONUS * 2f)

    stats.engineDamageTakenMult.modifyMult(id, DAMAGE_TAKEN)
    stats.armorDamageTakenMult.modifyMult(id, DAMAGE_TAKEN)
    stats.hullDamageTakenMult.modifyMult(id, DAMAGE_TAKEN)

    stats.ballisticRoFMult.modifyFlat(id, ROF_BONUS_FLAT)
    stats.energyRoFMult.modifyFlat(id, ROF_BONUS_FLAT)

    stats.ballisticWeaponFluxCostMod.modifyMult(id, 1f/ROF_BONUS_FLAT)
    stats.energyWeaponFluxCostMod.modifyMult(id, 1f/ROF_BONUS_FLAT)

    stats.fluxDissipation.modifyPercent(ID, FLUX_PERCENT_BONUS)


    //下面只在完全激活时跑一次
    if (effectLevel < 1) return
    val engine = Global.getCombatEngine()
    //初始速度
    ship.velocity.scale(0.1f)
    ship.velocity.set(speed2Velocity(ship.facing, MAX_SPEED_BONUS))
    //加烟
    val num = 12
    for (i in 0 until num) {
      engine.addNebulaSmokeParticle(
        MathUtils.getRandomPointInCircle(ship.location, ship.collisionRadius),
        speed2Velocity(ship.facing - 180f, MathUtils.getRandomNumberInRange(50, 100).toFloat()),
        MathUtils.getRandomNumberInRange(20, 60).toFloat(),
        2f,
        0.25f,
        0.5f, 1f,
        SMOKE_MERGE_COLOR
      )
    }
    //加残影
    var vel = speed2Velocity(ship.facing, -100f)
    ship.addAfterimage(
      AFTER_MERGE_COLOR, 0f, 0f,
      vel.x, vel.y, 5f, 0f,
      0.5f,
      0.5f,
      true,
      false,
      true
    )
    vel = speed2Velocity(ship.facing, -200f)
    ship.addAfterimage(
      AFTER_MERGE_COLOR, 0f, 0f,
      vel.x, vel.y, 5f, 0f,
      0.5f,
      0.5f,
      true,
      false,
      true
    )
    vel = speed2Velocity(ship.facing, -300f)
    ship.addAfterimage(
      AFTER_MERGE_COLOR, 0f, 0f,
      vel.x, vel.y, 5f, 0f,
      0.5f,
      0.5f,
      true,
      false,
      true
    )
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String) {
    //stats的entity有可能为null
    val ship = (stats?.entity?: return)as ShipAPI

    stats.maxSpeed.unmodify(id)
    stats.acceleration.unmodify(id)
    stats.deceleration.unmodify(id)
    //stats.deceleration.modifyMult(id, 0f)
    stats.maxTurnRate.unmodify(id)
    stats.turnAcceleration.unmodify(id)

    stats.engineDamageTakenMult.unmodify(id)
    stats.armorDamageTakenMult.unmodify(id)
    stats.hullDamageTakenMult.unmodify(id)

    stats.ballisticRoFMult.modifyFlat(id, 0f)
    stats.energyRoFMult.modifyFlat(id, 0f)

    stats.ballisticWeaponFluxCostMod.modifyMult(id, 1f)
    stats.energyWeaponFluxCostMod.modifyMult(id, 1f)


    stats.fluxDissipation.modifyPercent(ID, 0f)

  }


}