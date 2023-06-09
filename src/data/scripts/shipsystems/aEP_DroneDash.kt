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
    const val TURN_RATE_BONUS = 250f
    const val ROTATE_SPEED = 20f
    const val END_BUFF_TIME = 0.5f
    const val END_TURN_RATE_BONUS = 180f
    const val END_ACC_BONUS = 50f
    const val DAMAGE_TAKEN = 0.35f

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
    stats.deceleration.modifyMult(id, 0f)
    stats.engineDamageTakenMult.modifyMult(id, DAMAGE_TAKEN)
    stats.armorDamageTakenMult.modifyMult(id, DAMAGE_TAKEN)
    stats.hullDamageTakenMult.modifyMult(id, DAMAGE_TAKEN)
    ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS)
    ship.blockCommandForOneFrame(ShipCommand.DECELERATE)
    ship.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT)
    ship.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT)
    ship.giveCommand(ShipCommand.ACCELERATE, null, 0)
    val angleAndSpeed = velocity2Speed(ship.velocity)
    angleAndSpeed.y += ship.acceleration * amount
    ship.velocity.set(speed2Velocity(angleAndSpeed.x, angleAndSpeed.y))


    //下面只在完全激活时跑一次
    if (effectLevel < 1) return
    val engine = Global.getCombatEngine()
    //速度归0
    ship.velocity.set(Vector2f(0f, 0f))
    //加烟
    val num = 16
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
    val m: MissileAPI? = null
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String) {
    //stats的entity有可能为null
    val ship = (stats?.entity?: return)as ShipAPI

    stats.maxSpeed.unmodify(id)
    stats.acceleration.unmodify(id)
    stats.deceleration.unmodify(id)
    stats.engineDamageTakenMult.unmodify(id)
    stats.armorDamageTakenMult.unmodify(id)
    stats.hullDamageTakenMult.unmodify(id)

    aEP_CombatEffectPlugin.addEffect(aEP_ExtraTurnRate(END_BUFF_TIME,ship))

  }

  internal inner class aEP_ExtraTurnRate(time:Float,ship: ShipAPI) : aEP_BaseCombatEffect(time,ship) {
    override fun init(entity: CombatEntityAPI?) {
      super.init(entity)
      val ship = (entity?: return)as ShipAPI
      ship.mutableStats.maxTurnRate.modifyFlat(ID, END_TURN_RATE_BONUS)
      ship.mutableStats.turnAcceleration.modifyFlat(ID, END_TURN_RATE_BONUS * 2)
      ship.mutableStats.acceleration.modifyPercent(ID, END_ACC_BONUS)
      ship.mutableStats.deceleration.modifyPercent(ID, END_ACC_BONUS)
    }

    override fun advanceImpl(amount: Float) {
      val ship = (entity?: return)as ShipAPI
      ship.setJitterUnder(ID, Color(255, 155, 155, 255), 1f, 18, 1.5f)
    }

    override fun readyToEnd() {
      val ship = entity as ShipAPI
      ship.mutableStats.maxTurnRate.unmodify(ID)
      ship.mutableStats.turnAcceleration.unmodify(ID)
      ship.mutableStats.acceleration.unmodify(ID)
      ship.mutableStats.deceleration.unmodify(ID)
    }

  }

}