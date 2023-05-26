package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import combat.util.aEP_Tool.Util.getAmount
import combat.util.aEP_Tool.Util.speed2Velocity
import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.combat.ShipAPI
import combat.util.aEP_Tool
import org.lwjgl.util.vector.Vector2f
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI
import combat.impl.VEs.aEP_MovingSmoke
import combat.util.aEP_Tool.Util.velocity2Speed
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_NBFiringJet : BaseShipSystemScript() {

  companion object {
    const val MAX_SPEED_FLAT = 300f

    const val ACC_MULT = 0.25f

    const val MAX_TURN_RATE_FLAT = 6f
    const val MAX_TURN_RATE_PERCENT = 50f
    const val TURN_ACC_FLAT = 12f
    const val TURN_ACC_PERCENT = 100f
    const val WEAPON_TURN_RATE_PERCENT = 50f
  }

  var engineBackLeft1:ShipEngineAPI? = null
  var engineBackLeft2:ShipEngineAPI? = null
  var engineBackRight1:ShipEngineAPI? = null
  var engineBackRight2:ShipEngineAPI? = null
  var engineFrontLeft1:ShipEngineAPI? = null
  var engineFrontLeft2:ShipEngineAPI? = null
  var engineFrontRight1:ShipEngineAPI? = null
  var engineFrontRight2:ShipEngineAPI? = null

  var engineBackLeft1Active = false
  var engineBackLeft2Active = false
  var engineBackRight1Active = false
  var engineBackRight2Active = false
  var engineFrontLeft1Active = false
  var engineFrontLeft2Active = false
  var engineFrontRight1Active = false
  var engineFrontRight2Active = false

  var smokeTrailTimer = IntervalUtil(0.1f, 0.1f)


  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    if(engineBackLeft1 == null || engineBackLeft2 == null || engineFrontLeft1 == null || engineFrontLeft2 == null){
      if(engineBackRight1 == null || engineBackRight2 == null || engineFrontRight1 == null || engineFrontRight2 == null){
        findEngines(ship)
      }
    }
    val useLevel = (effectLevel * 0.9f) + 0.1f
    ship.isJitterShields = false
    ship.setJitterUnder(id, Color(240, 100, 50, 200), 1f, 4, 14f)
    //改变速度
    stats.maxSpeed.modifyFlat(id, MAX_SPEED_FLAT * useLevel)
    stats.acceleration.modifyMult(id, ACC_MULT)
    stats.deceleration.modifyMult(id, ACC_MULT)
    stats.turnAcceleration.modifyFlat(id, TURN_ACC_FLAT)
    stats.turnAcceleration.modifyPercent(id, TURN_ACC_PERCENT)
    stats.maxTurnRate.modifyFlat(id, MAX_TURN_RATE_FLAT)
    stats.maxTurnRate.modifyPercent(id, MAX_TURN_RATE_PERCENT)
    stats.weaponTurnRateBonus.modifyPercent(id, WEAPON_TURN_RATE_PERCENT)

    var angle = 0f
    //如果正在前进
    if(ship.engineController.isDecelerating || ship.engineController.isAcceleratingBackwards){
      angle = 180f
      if(ship.engineController.isStrafingLeft){
        angle = 135f
      }else if (ship.engineController.isStrafingRight){
        angle = 225f
      }
    //如果正在后退
    }else if(ship.engineController.isAccelerating){
      angle = 0f
      if(ship.engineController.isStrafingLeft){
        angle = 45f
      }else if (ship.engineController.isStrafingRight){
        angle = 315f
      }
    //如果只在进行侧向漂移
    }else{
      if(ship.engineController.isStrafingLeft){
        angle = 90f
      }else if (ship.engineController.isStrafingRight){
        angle = 270f
      }
    }
    //引擎相对角度加上舰船本身的角度，变成战场绝对角度
    angle = aEP_Tool.angleAdd(angle,ship.facing)

    //烟雾拖尾
    smokeTrailTimer.advance(getAmount(null))
    if (smokeTrailTimer.intervalElapsed()) createSmokeTrail(ship, 1f - effectLevel)

    //降低主引擎和没被激活副引擎的火光
    for (e in ship.engineController.shipEngines) {
      if(!e.isSystemActivated) {
        ship.engineController.setFlameLevel(e.engineSlot,1f-useLevel)
      }else{
        if(e == engineFrontLeft1 && !engineFrontLeft1Active) {
          ship.engineController.setFlameLevel(e.engineSlot,0.4f)
          continue
        }
        if(e == engineFrontLeft2 && !engineFrontLeft2Active) {
          ship.engineController.setFlameLevel(e.engineSlot,0.4f)
          continue
        }
        if(e == engineFrontRight1 && !engineFrontRight1Active) {
          ship.engineController.setFlameLevel(e.engineSlot,0.4f)
          continue
        }
        if(e == engineFrontRight2 && !engineFrontRight2Active) {
          ship.engineController.setFlameLevel(e.engineSlot,0.4f)
          continue
        }
        if(e == engineBackLeft1 && !engineBackLeft1Active) {
          ship.engineController.setFlameLevel(e.engineSlot,0.4f)
          continue
        }
        if(e == engineBackLeft2 && !engineBackLeft2Active) {
          ship.engineController.setFlameLevel(e.engineSlot,0.4f)
          continue
        }
        if(e == engineBackRight1 && !engineBackRight1Active) {
          ship.engineController.setFlameLevel(e.engineSlot,0.4f)
          continue
        }
        if(e == engineBackRight2 && !engineBackRight2Active) {
          ship.engineController.setFlameLevel(e.engineSlot,0.4f)
          continue
        }
        //比较神奇，按加速，会覆盖掉setFlameLevel，同时setFlameLevel的尾焰会莫名的粗短
        if(!ship.engineController.isAccelerating){
          //shift * to是最终量，durIn和Out决定变化速度
          //但是这里每帧都会调用然后刷新,每帧都重新开始淡入，结果是0.5f左右，不是1f
          //in不能为0
          ship.engineController.extendWidthFraction.shift(this,-1f,0.000001f,0f,1f)
          ship.engineController.extendGlowFraction.shift(this,-1f,0.000001f,0f,1f)
          ship.engineController.setFlameLevel(e.engineSlot,1f)
        }
      }

    }

    //下面的只运行一次
    if (effectLevel < 1) return
    //先减速，去除惯性，再朝目标方向加速
    ship.velocity.scale(0.25f)
    val toAddVel = speed2Velocity(angle, MAX_SPEED_FLAT)
    ship.velocity[ship.velocity.x + toAddVel.x] = ship.velocity.y + toAddVel.y

    val angleDistToShipFacing = MathUtils.getShortestRotation(ship.facing,angle)
    giveInitialBurst(ship,angleDistToShipFacing)

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    val ship = (stats?.entity?: return) as ShipAPI

    if(engineBackLeft1 == null || engineBackLeft2 == null || engineFrontLeft1 == null || engineFrontLeft2 == null){
      if(engineBackRight1 == null || engineBackRight2 == null || engineFrontRight1 == null || engineFrontRight2 == null){
        findEngines(ship)
      }
    }
    engineBackLeft1Active = false
    engineBackLeft2Active = false
    engineBackRight1Active = false
    engineBackRight2Active = false
    engineFrontLeft1Active = false
    engineFrontLeft2Active = false
    engineFrontRight1Active = false
    engineFrontRight2Active = false

    stats.maxSpeed.unmodify(id)
    stats.maxTurnRate.unmodify(id)
    stats.turnAcceleration.unmodify(id)
    stats.acceleration.unmodify(id)
    stats.deceleration.unmodify(id)
    stats.weaponTurnRateBonus.unmodify(id)

    //回归最大速度
    val angleAndSpeed = velocity2Speed(ship.velocity)
    val toClamp = angleAndSpeed.y/(ship.mutableStats.maxSpeed.modifiedValue+30f)
    ship.velocity.scale(MathUtils.clamp(1f/toClamp,0.25f,1f))
  }

  fun createSmokeTrail(ship: ShipAPI, level:Float) {
    val useLevel = MathUtils.clamp(level,0f,1f)

    for (e in ship.engineController.shipEngines) {
      if(e == engineFrontLeft1 && !engineFrontLeft1Active) continue
      if(e == engineFrontLeft2 && !engineFrontLeft2Active) continue
      if(e == engineFrontRight1 && !engineFrontRight1Active) continue
      if(e == engineFrontRight2 && !engineFrontRight2Active) continue
      if(e == engineBackLeft1 && !engineBackLeft1Active) continue
      if(e == engineBackLeft2 && !engineBackLeft2Active) continue
      if(e == engineBackRight1 && !engineBackRight1Active)continue
      if(e == engineBackRight2 && !engineBackRight2Active) continue

      if(!e.isSystemActivated) continue
      val loc = e.engineSlot.computePosition(ship.location, ship.facing)
      val ms = aEP_MovingSmoke(loc)
      ms.setInitVel(speed2Velocity(e.engineSlot.computeMidArcAngle(ship.facing), 35f))
      ms.lifeTime = 1f
      ms.fadeIn = 0.2f
      ms.fadeOut = 0.6f
      ms.size = 35f
      ms.sizeChangeSpeed = 35f
      ms.color = Color(255, (100+140*useLevel).toInt(), (100+140*useLevel).toInt(), 80)
      addEffect(ms)
    }
  }

  fun spawnInitBurst(e: ShipEngineAPI, ship: ShipAPI) {
    val loc = e.engineSlot.computePosition(ship.location, ship.facing)
    val vel = speed2Velocity(e.engineSlot.computeMidArcAngle(ship.facing), 40f)
    Global.getCombatEngine().addSmoothParticle(
      loc,
      Vector2f(0f, 0f),
      500f,  //size
      1f,  //brightness
      0.35f,
      0.15f,
      Color(255, 120, 120, 255)
    )
    val ms = aEP_MovingSmoke(loc)
    ms.setInitVel(vel)
    ms.stopSpeed = 1f
    ms.lifeTime = 1.6f
    ms.fadeIn = 0.15f
    ms.fadeOut = 0.45f
    ms.size = 80f
    ms.sizeChangeSpeed = 20f
    ms.color = Color.red
    addEffect(ms)

    Global.getCombatEngine().spawnExplosion(loc,vel,Color(255, 255, 255, 100),80f,1f)
  }

  fun findEngines(ship: ShipAPI){
    for (e in ship.engineController.shipEngines) {
      if(e.engineSlot.angle == 45f){
        if(e.engineSlot.length == 59f) engineFrontLeft1 = e
        if(e.engineSlot.length == 58f) engineFrontLeft2 = e
        continue
      }
      if(e.engineSlot.angle == -45f){
        if(e.engineSlot.length == 59f) engineFrontRight1 = e
        if(e.engineSlot.length == 58f) engineFrontRight2 = e
        continue
      }
      if(e.engineSlot.angle == 135f){
        if(e.engineSlot.length == 59f) engineBackLeft1 = e
        if(e.engineSlot.length == 58f) engineBackLeft2 = e
        continue
      }
      if(e.engineSlot.angle == -135f){
        if(e.engineSlot.length == 59f) engineBackRight1 = e
        if(e.engineSlot.length == 58f) engineBackRight2 = e
        continue
      }
    }

  }

  fun giveInitialBurst(ship: ShipAPI, angleDistToShipFacing:Float){
    if( angleDistToShipFacing <= 22.5f && angleDistToShipFacing > -22.5f){
      //向正前，激活正后引擎
      spawnInitBurst(engineBackRight1!!,ship)
      spawnInitBurst(engineBackLeft1!!,ship)
      engineBackRight1Active = true
      engineBackLeft1Active = true

    }else if(angleDistToShipFacing <= 67.5f && angleDistToShipFacing > 22.5f){
      //向左45度，激活右后引擎
      spawnInitBurst(engineBackRight1!!,ship)
      spawnInitBurst(engineBackRight2!!,ship)
      engineBackRight1Active = true
      engineBackRight2Active = true

    }else if(angleDistToShipFacing <= 112.5f && angleDistToShipFacing > 67.5f){
      //向左90度，激活正右引擎
      spawnInitBurst(engineFrontRight1!!,ship)
      spawnInitBurst(engineBackRight1!!,ship)
      engineFrontRight1Active = true
      engineBackRight1Active = true

    }else if(angleDistToShipFacing <= 157.5f && angleDistToShipFacing > 112.5f){
      //向左135度，激活右前引擎
      spawnInitBurst(engineFrontRight1!!,ship)
      spawnInitBurst(engineFrontRight2!!,ship)
      engineFrontRight1Active = true
      engineFrontRight2Active = true

    }else if(angleDistToShipFacing <= -157.5f || angleDistToShipFacing > 157.5f){
      //向正后，激活正前引擎
      spawnInitBurst(engineFrontRight1!!,ship)
      spawnInitBurst(engineFrontLeft1!!,ship)
      engineFrontRight1Active = true
      engineFrontLeft1Active = true

    }else if(angleDistToShipFacing <= -112.5f && angleDistToShipFacing > -157.5f){
      //向右135度，激活左前引擎
      spawnInitBurst(engineFrontLeft1!!,ship)
      spawnInitBurst(engineFrontLeft2!!,ship)
      engineFrontLeft1Active = true
      engineFrontLeft2Active = true

    }else if(angleDistToShipFacing <= -67.5f && angleDistToShipFacing > -112.5f){
      //向右90度，激活正左引擎
      spawnInitBurst(engineFrontLeft1!!,ship)
      spawnInitBurst(engineBackLeft1!!,ship)
      engineFrontLeft1Active = true
      engineBackLeft1Active = true

    }else if(angleDistToShipFacing <= -22.5f && angleDistToShipFacing > -67.5f){
      //向右45度，激活左后引擎
      spawnInitBurst(engineBackLeft1!!,ship)
      spawnInitBurst(engineBackLeft2!!,ship)
      engineBackLeft1Active = true
      engineBackLeft2Active = true
    }
  }
}