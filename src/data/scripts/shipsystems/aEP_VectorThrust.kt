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

    const val MAX_FLAT_BUFF = 75f
    const val MAX_FLAT_FORWARD_BUFF = 20f

    const val ACC_FLAT_BUFF = 400f

    const val MAX_TURN_FLAT_BUFF = 30f
    const val ACC_TURN_FLAT_BUFF = 100f

    val AFTER_IMAGE_COLOR = Color(190,100,93,30)
  }

  val afterImageTracker = IntervalTracker(0.033f,0.033f)

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    val angleAndSpeed = aEP_Tool.velocity2Speed(ship.velocity)
    val angleDist = MathUtils.getShortestRotation(angleAndSpeed.x,ship.facing)
    val amount = aEP_Tool.getAmount(ship)

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


    ship.mutableStats.maxTurnRate.modifyFlat(id, MAX_TURN_FLAT_BUFF)
    ship.mutableStats.turnAcceleration.modifyFlat(id, ACC_TURN_FLAT_BUFF)

    if(ship.engineController.isAcceleratingBackwards || ship.engineController.isAccelerating){
      if(!ship.engineController.isStrafingLeft && ship.engineController.isStrafingRight){
        //如果在全力前进或者后退，没有同时侧移时，削弱更多
        ship.mutableStats.maxSpeed.modifyFlat(id, MAX_FLAT_FORWARD_BUFF * 0.5f)
      }else{
        //前进后退时效果削弱
        ship.mutableStats.maxSpeed.modifyFlat(id, MAX_FLAT_FORWARD_BUFF)
      }

    }else{
      ship.mutableStats.maxSpeed.modifyFlat(id, MAX_FLAT_BUFF)
    }

    ship.mutableStats.deceleration.modifyFlat(id, ACC_FLAT_BUFF)
    ship.mutableStats.acceleration.modifyFlat(id, ACC_FLAT_BUFF)

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