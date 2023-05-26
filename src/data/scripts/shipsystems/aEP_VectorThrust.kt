package data.scripts.shipsystems

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.util.IntervalTracker
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_VectorThrust : BaseShipSystemScript() {

  companion object{
    const val SLOW_FACTOR = 5f
    const val MAX_PERCENT_BUFF = 250f
    const val MAX_PERCENT_FORWARD_BUFF = 50f
    const val ACC_PERCENT_BUFF = 1000f

    const val MAX_TURN_PERCENT_BUFF = 200f
    const val ACC_TURN_PERCENT_BUFF = 400f

    val AFTER_IMAGE_COLOR = Color(190,100,93,30)
  }

  val afterImageTracker = IntervalTracker(0.033f,0.033f)

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    val angleAndSpeed = aEP_Tool.velocity2Speed(ship.velocity)
    val angleDist = MathUtils.getShortestRotation(angleAndSpeed.x,ship.facing)
    val amount = aEP_Tool.getAmount(ship)

    val slowFactor = MathUtils.clamp(1f - SLOW_FACTOR *amount,0f,1f)
    if((ship.engineController.isAccelerating) && (angleDist> 135 || angleDist <- 135)){
      ship.velocity.scale(slowFactor)
    }else if(ship.engineController.isStrafingLeft && (angleDist> 45 && angleDist < 135)) {
      //手动给左右飘逸加上加速度
      ship.velocity.scale(slowFactor)
    }else if(ship.engineController.isStrafingRight && (angleDist < -45 && angleDist > -135)) {
      ship.velocity.scale(slowFactor)
    }else if(ship.engineController.isAcceleratingBackwards && (angleDist < 45 && angleDist > - 45)) {
      ship.velocity.scale(slowFactor)
    }


    if(!ship.engineController.isTurningLeft && !ship.engineController.isTurningRight){
      ship.angularVelocity = ship.angularVelocity*slowFactor
    }

    //比较神奇，按加速，会覆盖掉setFlameLevel，同时setFlameLevel的尾焰会莫名的粗短
    for(e in ship.engineController.shipEngines){
      //shift * to是最终量，durIn和Out决定变化速度
      //但是这里每帧都会调用然后刷新,每帧都重新开始淡入，结果是0.5f左右，不是1f
      //in不能为0
      ship.engineController.extendWidthFraction.shift(this,-0.7f,0.000001f,0f,1f)
      ship.engineController.extendGlowFraction.shift(this,-0.7f,0.000001f,0f,1f)
      ship.engineController.setFlameLevel(e.engineSlot,1f)
    }

    afterImageTracker.advance(amount)
    if(afterImageTracker.intervalElapsed()){
      ship.addAfterimage(
        AFTER_IMAGE_COLOR,
        0f,0f,
        -ship.velocity.x,-ship.velocity.y,
        0f,
        0f,0.15f,0.85f,
        true,true,false)
    }


    ship.mutableStats.maxTurnRate.modifyPercent(id, MAX_TURN_PERCENT_BUFF)
    ship.mutableStats.turnAcceleration.modifyPercent(id, ACC_TURN_PERCENT_BUFF)

    //前进后退时效果削弱
    if(ship.engineController.isAcceleratingBackwards || ship.engineController.isAccelerating){
      //如果在全力前进或者后退，没有同时侧移时，削弱更多
      if(!ship.engineController.isStrafingLeft && ship.engineController.isStrafingRight){
        ship.mutableStats.maxSpeed.modifyPercent(id, MAX_PERCENT_FORWARD_BUFF *0.5f)
      }else{
        ship.mutableStats.maxSpeed.modifyPercent(id, MAX_PERCENT_FORWARD_BUFF)
      }
    }else{
      ship.mutableStats.maxSpeed.modifyPercent(id, MAX_PERCENT_BUFF)
    }

    ship.mutableStats.deceleration.modifyPercent(id, ACC_PERCENT_BUFF)
    ship.mutableStats.acceleration.modifyPercent(id, ACC_PERCENT_BUFF)
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    val ship = (stats?.entity?: return) as ShipAPI
    ship.mutableStats.maxTurnRate.unmodify(id)
    ship.mutableStats.turnAcceleration.unmodify(id)

    ship.mutableStats.maxSpeed.unmodify(id)
    ship.mutableStats.acceleration.unmodify(id)
    ship.mutableStats.deceleration.unmodify(id)
  }
}