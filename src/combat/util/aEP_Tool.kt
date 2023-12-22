//by a111164
package combat.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.ids.Tags.VARIANT_FX_DRONE
import com.fs.starfarer.api.impl.combat.RecallDeviceStats
import com.fs.starfarer.api.impl.combat.dem.DEMEffect
import com.fs.starfarer.api.loading.ProjectileSpecAPI
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.ui.Fonts
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.combat.entities.BallisticProjectile
import combat.impl.VEs.aEP_MovingSmoke
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.FastTrig
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lazywizard.lazylib.ui.LazyFont
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import org.magiclib.plugins.MagicRenderPlugin
import org.magiclib.util.MagicRender
import java.awt.Color
import kotlin.math.*

class aEP_Tool {

  companion object Util {
    val REPAIR_COLOR = Color(250, 250, 178, 240)
    val REPAIR_COLOR2 = Color(250, 220, 70, 250)

    /**
     * 旋转舰船朝向到指定位置
     * @return 转向需要的时间
     * */
    fun moveToAngle(ship: ShipAPI, toAngle: Float) {
      var angleDist = 0f
      val angleNow = ship.facing

      //get which side to turn and how much to turn, negative = you are in the left side
      angleDist = MathUtils.getShortestRotation(angleNow, toAngle)

      //Global.getCombatEngine().addFloatingText(ship.getLocation(),  "turnRate", 20f ,new Color (0, 100, 200, 240),ship, 0.25f, 120f);
      var turnRight = false //true == should turn right, false == should turn left
      turnRight = angleDist < 0
      val angleDistBeforeStop = ship.angularVelocity * ship.angularVelocity / (ship.mutableStats.turnAcceleration.modifiedValue * 2 + 1)
      if (turnRight) {
        if (ship.angularVelocity > 0) //want to turn right but is turning to left, turnRateNow > 0
        {
          ship.giveCommand(ShipCommand.TURN_RIGHT, null, 0)
        } else  //want to turn right and is turning right, turnRateNow < 0
        {
          if (Math.abs(angleDist) - 2 >= angleDistBeforeStop) //accelerate till maxTurnRate
          {
            //((ShipAPI)ship).giveCommand(ShipCommand.TURN_RIGHT,null,0);
            ship.giveCommand(ShipCommand.TURN_RIGHT, null, 0)
          } else {
            //((ShipAPI)ship).giveCommand(ShipCommand.TURN_LEFT,null,0);
            ship.giveCommand(ShipCommand.TURN_LEFT, null, 0)
          }
        }
      } else  //to turn left
      {
        if (ship.angularVelocity < 0) //want to turn left but is turning to right, turnRateNow < 0
        {
          ship.giveCommand(ShipCommand.TURN_LEFT, null, 0)
        } else  //want to turn left and is turning left, turnTateNow > 0
        {
          if (Math.abs(angleDist) - 2 > angleDistBeforeStop) //accelerate till maxTurnRate
          {
            ship.giveCommand(ShipCommand.TURN_LEFT, null, 0)
          } else {
            ship.giveCommand(ShipCommand.TURN_RIGHT, null, 0)
          }
        }
      }
    }

    /**
     * 旋转导弹朝向到指定位置
     * @return 转向需要的时间
     * */
    fun moveToAngle(ship: MissileAPI, toAngle: Float) {
      ship.flightTime
      var angleDist = 0f
      val angleNow = ship.facing

      //get which side to turn and how much to turn, negative = you are in the left side
      angleDist = MathUtils.getShortestRotation(angleNow, toAngle)


      //Global.getCombatEngine().addFloatingText(ship.getLocation(),  "turnRate", 20f ,new Color (0, 100, 200, 240),ship, 0.25f, 120f);
      var turnRight = false //true == should turn right, false == should turn left
      turnRight = angleDist < 0
      val angleDistBeforeStop = ship.angularVelocity * ship.angularVelocity / (ship.turnAcceleration * 2 + 1)
      if (turnRight) {
        if (ship.angularVelocity > 0) //want to turn right but is turning to left, turnRateNow > 0
        {
          ship.giveCommand(ShipCommand.TURN_RIGHT)
        } else  //want to turn right and is turning right, turnRateNow < 0
        {
          if (Math.abs(angleDist) - 2 >= angleDistBeforeStop) //accelerate till maxTurnRate
          {
            //((ShipAPI)ship).giveCommand(ShipCommand.TURN_RIGHT,null,0);
            ship.giveCommand(ShipCommand.TURN_RIGHT)
          } else {
            //((ShipAPI)ship).giveCommand(ShipCommand.TURN_LEFT,null,0);
            ship.giveCommand(ShipCommand.TURN_LEFT)
          }
        }
      } else  //to turn left
      {
        if (ship.angularVelocity < 0) //want to turn left but is turning to right, turnRateNow < 0
        {
          ship.giveCommand(ShipCommand.TURN_LEFT)
        } else  //want to turn left and is turning left, turnTateNow > 0
        {
          if (Math.abs(angleDist) - 2 > angleDistBeforeStop) //accelerate till maxTurnRate
          {
            ship.giveCommand(ShipCommand.TURN_LEFT)
          } else {
            ship.giveCommand(ShipCommand.TURN_RIGHT)
          }
        }
      }
    }

    //turn weapon to angle with accelerate
    fun moveToAngle(toAngle: Float, MAX_SPEED: Float, ACC: Float, w: WeaponAPI, turnRate: Float, amount: Float): Float {
      var turnRate = turnRate
      var angleDist = 0f
      val angleNow = w.currAngle
      val turnRateNow = turnRate // minus = turning to right
      val maxTurnRate = MAX_SPEED * amount
      val accTurnRate = ACC * amount
      val decTurnRate = ACC * amount
      val trueMult = 1 / amount
      val arcAngle = w.arcFacing


      //get which side to turn and how much to turn, minus = you are in the left side
      angleDist = if (w.arc >= 360) {
        MathUtils.getShortestRotation(angleNow, toAngle)
      } else {
        // getShortest > 0 == toAngle is at right side of arcAngle
        val toAngleDist = MathUtils.getShortestRotation(arcAngle, toAngle)
        val nowAngleDist = MathUtils.getShortestRotation(arcAngle, w.currAngle)
        toAngleDist - nowAngleDist
        //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(),angleDist + "",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,2f);
      }

      //stable ship's direction if it is nearly there
      if (Math.abs(angleDist) <= maxTurnRate && Math.abs(turnRateNow) <= decTurnRate) {
        w.currAngle = toAngle
        return 0f
      }
      //Global.getCombatEngine().addFloatingText(getExtendedLocationFromPoint(ship.getLocation(), ship.getFacing(), 50f), turnRateNow + "turnRate", 20f ,new Color (0, 100, 200, 240),ship, 0.25f, 120f);
      var turnRight = false //true == should turn right, false == should turn left
      turnRight = angleDist < 0

      //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(), turnRight + "",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,2f);
      val angleDistBeforeStop = turnRateNow / 2 * (turnRateNow / (accTurnRate * trueMult))
      turnRate = if (turnRight) {
        if (turnRateNow > 0) //want to turn right but is turning to left, turnRateNow > 0
        {
          if (turnRateNow >= decTurnRate) //stop turning left, till turnRateNow is 0
          {
            turnRateNow - decTurnRate
          } else {
            0f
          }

          //((ShipAPI)ship).giveCommand(ShipCommand.TURN_RIGHT,null,0);
        } else  //want to turn right and is turning right, turnRateNow < 0
        {
          if (Math.abs(angleDist) >= angleDistBeforeStop) //accelerate till maxTurnRate
          {
            //((ShipAPI)ship).giveCommand(ShipCommand.TURN_RIGHT,null,0);
            if (Math.abs(turnRateNow) <= maxTurnRate * trueMult) {
              turnRateNow - accTurnRate
            } else {
              -maxTurnRate * trueMult
            }
          } else {
            //((ShipAPI)ship).giveCommand(ShipCommand.TURN_LEFT,null,0);
            if (Math.abs(turnRateNow) >= decTurnRate) //decelerate till 0
            {
              turnRateNow + decTurnRate
            } else {
              0.toFloat()
            }
          }
        }
      } else  //to turn left
      {
        if (turnRateNow < 0) //want to turn left but is turning to right, turnRateNow < 0
        {
          //((ShipAPI)ship).giveCommand(ShipCommand.TURN_LEFT,null,0);
          if (Math.abs(turnRateNow) >= decTurnRate) //stop turning right, till turnRateNow is 0
          {
            turnRateNow + decTurnRate
          } else {
            0.toFloat()
          }
        } else  //want to turn left and is turning left, turnTateNow > 0
        {
          if (Math.abs(angleDist) > angleDistBeforeStop) //accelerate till maxTurnRate
          {
            //((ShipAPI)ship).giveCommand(ShipCommand.TURN_LEFT,null,0);
            if (turnRateNow <= maxTurnRate * trueMult) {
              turnRateNow + accTurnRate
            } else {
              maxTurnRate * trueMult
            }
          } else {
            //((ShipAPI)ship).giveCommand(ShipCommand.TURN_RIGHT,null,0);
            if (turnRateNow >= decTurnRate) //decelerate till 0
            {
              turnRateNow - decTurnRate
            } else {
              0.toFloat()
            }
          }
        }
      }
      return turnRate
    }

    //+ = turnLeft, - + turn right
    @JvmStatic
    fun angleAdd(originAngle: Float, addAngle: Float): Float {
      var addAngle = addAngle
      val finalAngle = originAngle + addAngle
      while (addAngle > 360) addAngle = addAngle - 360
      while (addAngle < 0) addAngle = addAngle + 360
      return finalAngle
    }

    @JvmStatic
    fun getTimeNeedToTurn(angle: Float, toAngle: Float, turnRate: Float, acc: Float, dec: Float, maxTurnRate: Float): Float {
      var turnRate = turnRate
      var time = 0f
      var dist = 0f

      //no acc is invalid
      if (acc <= 0f) {
        return 999f
      }

      //get which side to turn and how much to turn, minus = you are in the left side
      //turn rate > = 0 means turing to left
      dist = MathUtils.getShortestRotation(angle, toAngle)
      var sameSide = dist <= 0 || turnRate >= 0
      if (dist < 0 && turnRate > 0) {
        sameSide = false
      }
      while (Math.abs(dist) > 1f && turnRate > 1f) {

        //Global.getCombatEngine().addFloatingText(Global.getCombatEngine().getPlayerShip().getMouseTarget(),time + "", 20f ,new Color(100,100,100,100),Global.getCombatEngine().getPlayerShip(), 0.25f, 120f);

        //stop turning to the other side first
        if (!sameSide) {
          time = time + Math.abs(turnRate / dec)
          turnRate = 0f
          dist = dist - turnRate / 2 * Math.abs(turnRate / dec)
        } else {
          //if we are here, we stopped turning opposite
          //if we already turning to fast and should slow down from now, do this method again when we reach target angle
          if (Math.abs(dist) <= turnRate / 2 * Math.abs(turnRate / dec)) {
            val delta = Math.sqrt((turnRate * turnRate - 2 * acc * dist).toDouble()).toFloat()
            val resolve1 = (-turnRate + delta) / -acc
            val resolve2 = (-turnRate - delta) / -acc
            return if (resolve1 > resolve2) {
              time + resolve2
            } else time + resolve1
          }


          //if we can acc to max speed before dec
          val distToSlow = maxTurnRate / 2 * (maxTurnRate / acc)
          if (distToSlow <= Math.abs(dist)) {
            time = time + 2 * (maxTurnRate / acc)
            dist = Math.abs(dist) - distToSlow
            return time + dist / maxTurnRate
          }


          //now the only situation is we acc first and dec before reach max turn speed
          dist = Math.abs(dist)
          return time + (2 * dist - turnRate * turnRate) / turnRate + turnRate / acc
        }
      }
      return time
    }

    /**
     * 用于直飞到目标点，会停在目标点上
     * 舰船类
     * */
    @JvmStatic
    fun moveToPosition(entity: ShipAPI, toPosition: Vector2f) {
      val directionVec = VectorUtils.getDirectionalVector(entity.location, toPosition)
      val directionAngle = VectorUtils.getFacing(directionVec)
      val distSq = MathUtils.getDistanceSquared(entity.location, toPosition)
      val angleAndSpeed = velocity2Speed(entity.velocity)
      val speedAngle = VectorUtils.getFacing(entity.velocity)
      val timeToSlowDown = angleAndSpeed.y / entity.deceleration //time to slow down to zero speed(by seconds,squared)				s
      val faceAngleDiff = MathUtils.getShortestRotation(entity.facing, directionAngle)
      val velAngleDiff = abs(MathUtils.getShortestRotation(speedAngle, directionAngle))

      //控制加速减速
      //以出发点方向为切线，向目标点做圆，求圆弧长度得到目标距离。
      //飞过目标距离所花的时间，小于等于转过面向需要的时间
      val sine = abs(FastTrig.sin(Math.toRadians(velAngleDiff.toDouble())))
      val rSq = (distSq/4)/(sine*sine)
      val arcDistSq = 3.14f * 3.14f * 4f * rSq
      val timeArcSq = arcDistSq/(angleAndSpeed.y * angleAndSpeed.y + 1)
      val timeTurn = abs(velAngleDiff/(entity.angularVelocity+1))
      //如果船飞过圆弧需要时间小于转向需要时间,即转向时间不足，减速，提前0.5秒转向当缓冲
      if( timeArcSq < (timeTurn+0.5f).pow(2) ){
        entity.giveCommand(ShipCommand.DECELERATE, null, 0)
      }else{//差别在容忍范围内
        //如果靠近目标点，开始减速
        if (distSq <= (angleAndSpeed.y * timeToSlowDown + 10) * (angleAndSpeed.y * timeToSlowDown + 10)) {
          entity.giveCommand(ShipCommand.DECELERATE, null, 0)
        }else{
          if(faceAngleDiff > 20 && faceAngleDiff < 90){
            entity.giveCommand(ShipCommand.STRAFE_LEFT,null,0)
          }else if(faceAngleDiff < -20 && faceAngleDiff > -90){
            entity.giveCommand(ShipCommand.STRAFE_RIGHT,null,0)
          } else{
            entity.giveCommand(ShipCommand.ACCELERATE, null, 0)
          }
        }
      }

      //旋转朝向，注意速度方向并不会跟着旋转
      moveToAngle(entity, directionAngle)
    }

    /**
     * 用于撞击制导，会穿过目标点而不减速
     * 舰船类
     * */
    @JvmStatic
    fun flyThroughPosition(entity: ShipAPI, toPosition: Vector2f?) {
      val directionVec = VectorUtils.getDirectionalVector(entity.location, toPosition)
      val directionAngle = VectorUtils.getFacing(directionVec)
      val distSq = MathUtils.getDistanceSquared(entity.location, toPosition)
      val angleAndSpeed = velocity2Speed(entity.velocity)
      val speedAngle = VectorUtils.getFacing(entity.velocity)
      val faceAngleDiff = MathUtils.getShortestRotation(entity.facing, directionAngle)
      val velAngleDiff = abs(MathUtils.getShortestRotation(speedAngle, directionAngle))


      //控制加速减速
      //以出发点方向为切线，向目标点做圆，求圆弧长度得到目标距离。
      //飞过目标距离所花的时间，小于等于转过面向需要的时间
      val sine = abs(FastTrig.sin(Math.toRadians(velAngleDiff.toDouble())))
      val rSq = (distSq/4)/(sine*sine)
      val arcDistSq = 3.14f * 3.14f * 4f * rSq
      val timeArcSq = arcDistSq/(angleAndSpeed.y * angleAndSpeed.y + 1)
      val timeTurn = abs(velAngleDiff/(entity.angularVelocity+1))
      //如果船飞过圆弧需要时间小于转向需要时间,即转向时间不足，减速
      if( timeArcSq < timeTurn*timeTurn ){
        entity.giveCommand(ShipCommand.DECELERATE, null, 0)
      }else{//差别在容忍范围内
        if(faceAngleDiff > 20 && faceAngleDiff < 90){
          entity.giveCommand(ShipCommand.STRAFE_LEFT,null,0)
        }else if(faceAngleDiff < -20 && faceAngleDiff > -90){
          entity.giveCommand(ShipCommand.STRAFE_RIGHT,null,0)
        } else{
          entity.giveCommand(ShipCommand.ACCELERATE, null, 0)
        }
      }

      //旋转朝向，注意速度方向并不会跟着旋转
      moveToAngle(entity, directionAngle)
    }

    /**
     * 用于撞击制导，会穿过目标点而不减速
     * 导弹类
     * */
    @JvmStatic
    fun flyThroughPosition(entity: MissileAPI, toPosition: Vector2f?) {
      val directionVec = VectorUtils.getDirectionalVector(entity.location, toPosition)
      val directionAngle = VectorUtils.getFacing(directionVec)
      val distSq = MathUtils.getDistanceSquared(entity.location, toPosition)
      val angleAndSpeed = velocity2Speed(entity.velocity)
      val speedAngle = VectorUtils.getFacing(entity.velocity)
      val velAngleDiff = abs(MathUtils.getShortestRotation(speedAngle, directionAngle))

      //控制加速减速
      //以出发点方向为切线，向目标点做圆，求圆弧长度得到目标距离。
      //飞过目标距离所花的时间，小于等于转过面向需要的时间
      val sine = abs(FastTrig.sin(Math.toRadians(velAngleDiff.toDouble())))
      val rSq = (distSq/4)/(sine*sine)
      val arcDistSq = 3.14f * 3.14f * 4f * rSq
      val timeArcSq = arcDistSq/(angleAndSpeed.y * angleAndSpeed.y + 1)
      val timeTurn = abs(velAngleDiff/(entity.angularVelocity+1))
      //如果船飞过圆弧需要时间小于转向需要时间,即转向时间不足，减速
      if( timeArcSq < timeTurn*timeTurn ){
        entity.giveCommand(ShipCommand.DECELERATE)
      }else{//差别在容忍范围内
        entity.giveCommand(ShipCommand.ACCELERATE)
      }

      //旋转朝向，注意速度方向并不会跟着旋转
      moveToAngle(entity, directionAngle)
    }

    /**
     * 用于平移到目标点，不改变朝向
     * 舰船类
     * */
    @JvmStatic
    fun setToPosition(ship: ShipAPI, toPosition: Vector2f?) {
      val directionVec =  VectorUtils.resize(VectorUtils.getDirectionalVector(ship.location, toPosition),ship.maxSpeed)
      val velDirectDiff = Vector2f(directionVec.x-ship.velocity.x,directionVec.y-ship.velocity.y)
      val diffAngle = angleAdd(VectorUtils.getFacing(velDirectDiff),-ship.facing)
      val distSq = MathUtils.getDistanceSquared(ship.location, toPosition)
      val angleAndSpeed = velocity2Speed(ship.velocity)
      val timeToSlowDown = angleAndSpeed.y / ship.deceleration //time to slow down to zero speed(by seconds,squared)

      //求当前速度向量和理想速度向量（全速指向目标方向）的差，在前后左右4个方向投影，哪个方向投影最大说明当前最需要加速哪个方向
      val sin = FastTrig.sin(Math.toRadians(diffAngle.toDouble())).toFloat()
      val cos = FastTrig.cos(Math.toRadians(diffAngle.toDouble())).toFloat()
      val f = cos * velDirectDiff.length()
      val b = -f
      val l = sin * velDirectDiff.length()
      val r = -l
      val list = floatArrayOf(f,b,r,l)
      list.sort()

      //如果靠近目标点，开始减速
      if (distSq <= (angleAndSpeed.y * timeToSlowDown + 10).pow(2)) {
        ship.giveCommand(ShipCommand.DECELERATE, null, 0)
        return
      }

      if(list[3]==f){
        ship.giveCommand(ShipCommand.ACCELERATE,null,0)
      }else if(list[3]==b){
        ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS,null,0)
      }else if(list[3]==r){
        ship.giveCommand(ShipCommand.STRAFE_RIGHT,null,0)
      }else if(list[3]==l){
        ship.giveCommand(ShipCommand.STRAFE_LEFT,null,0)
      }

    }
    @JvmStatic
    fun setToPositionHighAccuracy(ship: ShipAPI, toPosition: Vector2f?) {

      val directionVec =  VectorUtils.resize(VectorUtils.getDirectionalVector(ship.location, toPosition),ship.maxSpeed)
      val velDirectDiff = Vector2f(directionVec.x-ship.velocity.x,directionVec.y-ship.velocity.y)
      val diffAngle = angleAdd(VectorUtils.getFacing(velDirectDiff),-ship.facing)
      val distSq = MathUtils.getDistanceSquared(ship.location, toPosition)
      val angleAndSpeed = velocity2Speed(ship.velocity)
      val timeToSlowDown = angleAndSpeed.y / ship.deceleration //time to slow down to zero speed(by seconds,squared)

      //求当前速度向量和理想速度向量（全速指向目标方向）的差，在前后左右4个方向投影，哪个方向投影最大说明当前最需要加速哪个方向
      val sin = FastTrig.sin(Math.toRadians(diffAngle.toDouble())).toFloat()
      val cos = FastTrig.cos(Math.toRadians(diffAngle.toDouble())).toFloat()
      val f = cos * velDirectDiff.length()
      val b = -f
      val l = sin * velDirectDiff.length()
      val r = -l
      val list = floatArrayOf(f,b,r,l)
      list.sort()

      //如果靠近目标点，开始减速
      if (distSq <= (angleAndSpeed.y * timeToSlowDown + 0.1).pow(2)) {
        ship.giveCommand(ShipCommand.DECELERATE, null, 0)
        return
      }

      if(list[3]==f){
        ship.giveCommand(ShipCommand.ACCELERATE,null,0)
      }else if(list[3]==b){
        ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS,null,0)
      }else if(list[3]==r){
        ship.giveCommand(ShipCommand.STRAFE_RIGHT,null,0)
      }else if(list[3]==l){
        ship.giveCommand(ShipCommand.STRAFE_LEFT,null,0)
      }

    }

    /**
     * 不使用引擎，通过设定速度将实体不改变面向的飞行穿越目标点
     * */
    @JvmStatic
    fun forceSetThroughPosition(e: CombatEntityAPI, toPosition: Vector2f, amount: Float,acc: Float, maxSpeed: Float) {
      val directionVec = VectorUtils.getDirectionalVector(e.location, toPosition)
      val directionAngle = VectorUtils.getFacing(directionVec)
      val acceleration = acc * amount
      val xAxis = FastTrig.cos(Math.toRadians(directionAngle.toDouble())).toFloat() * acceleration
      val yAxis = FastTrig.sin(Math.toRadians(directionAngle.toDouble())).toFloat() * acceleration

      e.velocity.setX(e.velocity.getX() + xAxis)
      e.velocity.setY(e.velocity.getY() + yAxis)
      e.velocity.set(clampVelocityTo(e.velocity,maxSpeed))

      //如果速度方向和目标方向非常接近时，设置同步
      //acc越接近speed，同步的角度越大,最多在加速度为10倍时，设为5度
      val speedFacing = VectorUtils.getFacing(e.velocity)
      val angleDist = MathUtils.getShortestRotation(speedFacing,directionAngle)
      if(Math.abs(angleDist) < Math.min(acc/(maxSpeed*2f + 1f),5f) && angleDist != 0f ){
        VectorUtils.rotate(e.velocity,angleDist)
      }else{

      }
    }

    @JvmStatic
    fun clampVelocityTo(vel: Vector2f, toSpeed: Float): Vector2f{
      val angleAndSpeed = velocity2Speed(vel)
      angleAndSpeed.y = MathUtils.clamp(angleAndSpeed.y,0f,toSpeed)
      return speed2Velocity(angleAndSpeed)
    }

    /**
     * @return (angle, speed)
     * */
    @JvmStatic
    fun velocity2Speed(velocity: Vector2f): Vector2f {
      if(velocity.x == 0f && velocity.y == 0f){
        return Vector2f(0f,0f)
      }

      val x = VectorUtils.getFacing(velocity)
      val y = Math.sqrt((velocity.getX() * velocity.getX() + velocity.getY() * velocity.getY()).toDouble()).toFloat()
      return Vector2f(x, y)
    }

    fun velocity2SpeedSq(velocity: Vector2f): Vector2f {
      if(velocity.x == 0f && velocity.y == 0f){
        return Vector2f(0f,0f)
      }
      val x = VectorUtils.getFacing(velocity)
      val y = (velocity.getX() * velocity.getX() + velocity.getY() * velocity.getY())
      return Vector2f(x, y)
    }


    fun speed2Velocity(angle: Float, speed: Float): Vector2f {
      val xAxis = FastTrig.cos(Math.toRadians(angle.toDouble())).toFloat() * speed
      val yAxis = FastTrig.sin(Math.toRadians(angle.toDouble())).toFloat() * speed
      return Vector2f(xAxis, yAxis)
    }

    fun speed2Velocity(angleAndSpeed: Vector2f): Vector2f {
      val angle = angleAndSpeed.x
      val speed = angleAndSpeed.y
      val xAxis = FastTrig.cos(Math.toRadians(angle.toDouble())).toFloat() * speed
      val yAxis = FastTrig.sin(Math.toRadians(angle.toDouble())).toFloat() * speed
      return Vector2f(xAxis, yAxis)
    }

    fun facing2Vector(facing: Float): Vector2f {
      val xAxis = FastTrig.cos(Math.toRadians(facing.toDouble())).toFloat()
      val yAxis = FastTrig.sin(Math.toRadians(facing.toDouble())).toFloat()
      return Vector2f(xAxis, yAxis)
    }

    @JvmStatic
    fun getDistVector(from: Vector2f, to: Vector2f): Vector2f {
      val X = to.getX() - from.getX()
      val Y = to.getY() - from.getY()
      return Vector2f(X, Y)
    }

    @JvmStatic
    fun getExtendedLocationFromPoint(point: Vector2f, facing: Float, dist: Float): Vector2f {
      val xAxis = FastTrig.cos(Math.toRadians(facing.toDouble())).toFloat() * dist
      val yAxis = FastTrig.sin(Math.toRadians(facing.toDouble())).toFloat() * dist
      return Vector2f(point.getX() + xAxis, point.getY() + yAxis)
    }

    fun getLocationTurnedRightByDegrees(originPosition: Vector2f?, turnAroundWhere: Vector2f, degrees: Float): Vector2f {
      var angle = VectorUtils.getAngle(turnAroundWhere, originPosition)
      val dist = MathUtils.getDistance(turnAroundWhere, originPosition)
      angle += degrees
      if (angle > 365) {
        angle -= 365f
      }
      return getExtendedLocationFromPoint(turnAroundWhere, angle, dist)
    }

    fun getDegreesTurnedRightByLocation(originPosition: Vector2f?, turnAroundWhere: Vector2f, angleToTurn: Float): Vector2f {
      val angle = VectorUtils.getAngle(turnAroundWhere, originPosition)
      val dist = MathUtils.getDistance(turnAroundWhere, originPosition)
      return getExtendedLocationFromPoint(turnAroundWhere, angleAdd(angle, angleToTurn), dist)
    }

    fun getTargetWidthAngleInDistance(from: Vector2f?, target: CombatEntityAPI): Float {
      return (2 * 57.3f //57.3 is to convert from rad to degrees
          * Math.asin(
        (target.collisionRadius
            / MathUtils.getDistance(from, target.location)).toDouble()
      ).toFloat()) //drone width by degrees
    }

    @JvmStatic
    fun getTargetWidthAngleInDistance(from: Vector2f?, targetLocation: Vector2f?, targetRadius: Float): Float {
      return if (targetRadius >= MathUtils.getDistance(from, targetLocation)) {
        0f
      //57.3 is to convert from rad to degrees
      } else 2 * 57.3f * asin(
        (targetRadius
            / MathUtils.getDistance(from, targetLocation)).toDouble()
      ).toFloat()
      //drone width by degrees
    }

    @JvmStatic
    fun isFriendlyInLine(from: WeaponAPI): ShipAPI? {
      val weaponRange = from.range
      val weaponFacing = from.currAngle
      val allShipsInArc: MutableList<CombatEntityAPI> = ArrayList()
      val allShips = CombatUtils.getEntitiesWithinRange(from.slot.computePosition(from.ship), weaponRange)
        ?: return null
      //return false if there is no ship around
      for (s in allShips) {
        val targetFacing = VectorUtils.getAngle(from.slot.computePosition(from.ship), s.location)
        val targetWidth = getTargetWidthAngleInDistance(from.slot.computePosition(from.ship), s)
        if (Math.abs(MathUtils.getShortestRotation(weaponFacing, targetFacing)) < targetWidth / 2 && s is ShipAPI && !s.isFighter) {
          //Global.getCombatEngine().addFloatingText(s.getLocation(),MathUtils.getShortestRotation(weaponFacing , targetFacing) + "", 20f ,new Color(100,100,100,100),s, 0.25f, 120f);
          allShipsInArc.add(s)
        }
      }
      var closestDist = 4000f
      var closestShip: CombatEntityAPI? = null
      for (c in allShipsInArc) {
        if (MathUtils.getDistance(c, from.ship) < closestDist) {
          closestDist = MathUtils.getDistance(c, from.ship)
          closestShip = c
        }
      }
      if (closestShip == null) {
        return null
      }
      val ship = closestShip as ShipAPI
      return if (ship.isAlly || ship.owner == 0) {
        ship
      } else {
        null
      }
    }

    @JvmStatic
    fun isFriendlyInLine(from: Vector2f, to: Vector2f): ShipAPI? {
      val dist = MathUtils.getDistance(from,to)
      val facing = VectorUtils.getAngle(from,to)
      val allShipsInArc: MutableList<CombatEntityAPI> = ArrayList()
      val allShips = CombatUtils.getEntitiesWithinRange(from, dist) ?: return null
      for (s in allShips) {
        val targetFacing = VectorUtils.getAngle(from, s.location)
        val targetWidth = getTargetWidthAngleInDistance(from, s)
        if (Math.abs(MathUtils.getShortestRotation(facing, targetFacing)) < targetWidth / 2 && s is ShipAPI && !s.isFighter) {
          //Global.getCombatEngine().addFloatingText(s.getLocation(),MathUtils.getShortestRotation(weaponFacing , targetFacing) + "", 20f ,new Color(100,100,100,100),s, 0.25f, 120f);
          allShipsInArc.add(s)
        }
      }
      var closestDist = 4000f
      var closestShip: CombatEntityAPI? = null
      for (c in allShipsInArc) {
        if (MathUtils.getDistance(c, from) < closestDist) {
          closestDist = MathUtils.getDistance(c, from)
          closestShip = c
        }
      }
      if (closestShip == null) {
        return null
      }
      val ship = closestShip as ShipAPI
      return if (ship.isAlly || ship.owner == 0) {
        ship
      } else {
        null
      }
    }

    fun isEnemyInRange(from: WeaponAPI): ShipAPI? {
      val weaponRange = from.range
      val weaponFacing = from.currAngle
      val allShipsInArc: MutableList<CombatEntityAPI> = ArrayList()
      val allShips = CombatUtils.getEntitiesWithinRange(from.slot.computePosition(from.ship), weaponRange)
        ?: return null
      //return false if there is no ship around
      for (s in allShips) {
        val targetFacing = VectorUtils.getAngle(from.slot.computePosition(from.ship), s.location)
        val targetWidth = getTargetWidthAngleInDistance(from.slot.computePosition(from.ship), s)
        if (Math.abs(MathUtils.getShortestRotation(weaponFacing, targetFacing)) < targetWidth / 2 && s is ShipAPI && !s.isFighter) {
          //Global.getCombatEngine().addFloatingText(s.getLocation(),MathUtils.getShortestRotation(weaponFacing , targetFacing) + "", 20f ,new Color(100,100,100,100),s, 0.25f, 120f);
          allShipsInArc.add(s)
        }
      }
      var closestDist = 4000f
      var closestShip: CombatEntityAPI? = null
      for (c in allShipsInArc) {
        if (MathUtils.getDistance(c, from.ship) < closestDist) {
          closestDist = MathUtils.getDistance(c, from.ship)
          closestShip = c
        }
      }
      if (closestShip == null) {
        return null
      }
      val ship = closestShip as ShipAPI
      return if (!ship.isAlly && ship.owner != 0) {
        ship
      } else {
        null
      }
    }

    @JvmStatic
    fun aimToPoint(weapon: WeaponAPI, toTargetPo: Vector2f?) {
      val ship = weapon.ship
      val angle = VectorUtils.getDirectionalVector(weapon.location, toTargetPo)
      val angleFacing = VectorUtils.getFacing(angle)
      val maxTurnRate = weapon.turnRate / 60f
      val angleDist = MathUtils.getShortestRotation(weapon.currAngle, angleFacing)
      if (Math.abs(MathUtils.getShortestRotation(weapon.slot.angle + ship.facing, angleFacing)) > weapon.slot.arc / 2) {
      } else {
        if (angleDist >= 0) {
          if (angleDist > maxTurnRate) {
            weapon.currAngle = weapon.currAngle + maxTurnRate
          } else {
            weapon.currAngle = angleFacing
          }
        } else {
          if (angleDist < -maxTurnRate) {
            weapon.currAngle = weapon.currAngle - maxTurnRate
          } else {
            weapon.currAngle = angleFacing
          }
        }
      }
    }

    /**
     * 把武器转到某个方向
     * */
    @JvmStatic
    fun aimToAngle(weapon: WeaponAPI, angle: Float) {
      val ship = weapon.ship
      val maxTurnRate = weapon.turnRate / 60f
      val angleDist = MathUtils.getShortestRotation(weapon.currAngle, angle)
      if (abs(MathUtils.getShortestRotation(weapon.slot.angle + ship.facing, angle)) > weapon.slot.arc / 2) {
      } else {
        if (angleDist >= 0) {
          if (angleDist > maxTurnRate) {
            weapon.currAngle = weapon.currAngle + maxTurnRate
          } else {
            weapon.currAngle = angle
          }
        } else {
          if (angleDist < -maxTurnRate) {
            weapon.currAngle = weapon.currAngle - maxTurnRate
          } else {
            weapon.currAngle = angle
          }
        }
      }
    }


    //by seconds
    @JvmStatic
    fun projTimeToHitShip(proj: CombatEntityAPI, ship: CombatEntityAPI): Float {
      val projFlyRange = 1600f
      var projEnd = proj.location
      projEnd = if (proj is MissileAPI && proj.isGuided) {
        ship.location
      } else {
        getExtendedLocationFromPoint(proj.location, VectorUtils.getFacing(proj.velocity), projFlyRange)
      }
      val hitPoint = CollisionUtils.getCollisionPoint(proj.location, projEnd, ship)
      return if (hitPoint == null) {
        1000f
      } else {
        MathUtils.getDistance(proj.location, hitPoint) / velocity2Speed(proj.velocity).y
      }
    }

    @JvmStatic
    fun returnToParent(ship: ShipAPI, parentShip: ShipAPI?, amount: Float): Boolean {
      ship.giveCommand(ShipCommand.HOLD_FIRE, null, 0)
      val id = "aEP_ReturnToParent"
      //没有母舰直接自爆
      if ( (parentShip?.launchBaysCopy?.size?:0) <= 0) {
        val callBackColor = if (ship.shield != null) ship.shield.innerColor else Color(100, 100, 200, 200)
        Global.getCombatEngine().spawnExplosion(
          ship.location,  //loc
          Vector2f(0f, 0f),  //velocity
          callBackColor,  //color
          ship.collisionRadius * 3f,  //range
          0.5f) //duration
        Global.getCombatEngine().removeEntity(ship)
        return false
      }
      val parentShip = parentShip as ShipAPI

      //landing check
      val landingStarted = ship.isLanding
      val toTargetPo = ship.wing.source.getLandingLocation(ship)
      var dist = MathUtils.getDistance(ship.location, toTargetPo)


      //距离降落100外时，飞到100内，取消额外机动性
      if (dist > 100f) {
        ship.mutableStats.maxSpeed.unmodify(id)
        ship.mutableStats.acceleration.unmodify(id)
        ship.mutableStats.deceleration.unmodify(id)
        //如果之前开始了降落动画，而因为某些原因离开母舰100距离，取消降落
        moveToPosition(ship, toTargetPo)
        if (landingStarted) {
          ship.abortLanding()
        }
      //距离降落100内时，增加额外的机动性，准备平移到50内开始降落
      } else {
        ship.mutableStats.maxSpeed.modifyFlat(id, parentShip.maxSpeed + ship.maxSpeed)
        ship.mutableStats.acceleration.modifyFlat(id,parentShip.maxSpeed )
        ship.mutableStats.deceleration.modifyFlat(id,parentShip.maxSpeed )
        moveToAngle(ship, parentShip.facing)
        setToPosition(ship, toTargetPo)
      }

      //成功平移到50内
      if (dist <= 50f) {
        //如果没开始降落，让飞机开始播放降落动画
        if (!landingStarted) {
          ship.beginLandingAnimation(parentShip)
        }
      }

      //成功播放完降落动画而没有离开超过100距离
      //让飞机降落
      if (ship.isFinishedLanding) {
        ship.wing.source.land(ship)
        return true
      }
      return false
    }

    fun getSpriteRelPoint(spriteCenter: Vector2f, spriteSize: Vector2f, facing: Float, boundPoint: Vector2f?): Vector2f {
      val dist = MathUtils.getDistance(spriteCenter, boundPoint)
      val originFacing = VectorUtils.getAngle(spriteCenter, boundPoint)
      val boundPointInStandardCoord = getExtendedLocationFromPoint(spriteCenter, originFacing - facing + 90f, dist)
      val lowerLeftInStandardCoord = Vector2f(spriteCenter.x - spriteSize.x / 2f, spriteCenter.y - spriteSize.y / 2f)
      return Vector2f((boundPointInStandardCoord.x - lowerLeftInStandardCoord.x) / spriteSize.x, (boundPointInStandardCoord.y - lowerLeftInStandardCoord.y) / spriteSize.y)
    }

    //x is angle,y is dist
    fun getRelativeLocationData(hitPoint: Vector2f?, target: CombatEntityAPI, relativeToShield: Boolean): Vector2f {
      val absoluteAngle = VectorUtils.getAngle(target.location, hitPoint)
      var angle = 0f
      angle = if (relativeToShield) {
        angleAdd(absoluteAngle, -(target.shield?.facing?:target.facing) )
      } else {
        angleAdd(absoluteAngle, -target.facing)
      }
      val dist = MathUtils.getDistance(target.location, hitPoint)
      return Vector2f(angle, dist)
    }

    fun getAbsoluteLocation(angle: Float, dist: Float, target: CombatEntityAPI, relativeToShield: Boolean): Vector2f {
      return if (relativeToShield) {
        val absoluteAngle = angleAdd(angle, target.shield.facing)
        getExtendedLocationFromPoint(target.location, absoluteAngle, dist)
      } else {
        val absoluteAngle = angleAdd(angle, target.facing)
        getExtendedLocationFromPoint(target.location, absoluteAngle, dist)
      }
    }

    fun getAbsoluteLocation(relativeData: Vector2f, target: CombatEntityAPI, relativeToShield: Boolean): Vector2f {
      val angle = relativeData.x
      val dist = relativeData.y
      return if (relativeToShield) {
        val absoluteAngle = angleAdd(angle, target.shield?.facing?:target.facing)
        getExtendedLocationFromPoint(target.location, absoluteAngle, dist)
      } else {
        val absoluteAngle = angleAdd(angle, target.facing)
        getExtendedLocationFromPoint(target.location, absoluteAngle, dist)
      }
    }

    // +x = to top, -y = to right
    fun getWeaponOffsetInAbsoluteCoo(relativeCoo: Vector2f, w: WeaponAPI): Vector2f {
      var absOffset = getExtendedLocationFromPoint(w.location, w.currAngle, relativeCoo.x)
      absOffset = getExtendedLocationFromPoint(absOffset, angleAdd(w.currAngle, 90f), relativeCoo.y)
      return absOffset
    }

    fun getDistForLocToHitShield(loc: Vector2f, ship: ShipAPI): Float {
      if (ship.shield == null || ship.getShield().getType() == ShieldAPI.ShieldType.NONE) {
        return 9999f
      }
      val inAngle = Math.abs(MathUtils.getShortestRotation(VectorUtils.getAngle(ship.location, loc), ship.shield.facing)) < ship.shield.activeArc / 2
      return if (inAngle) {
        MathUtils.getDistance(loc, ship.location) - ship.shield.radius
      } else {
        9999f
      }
    }


    /**
     * 不会为空，没有找到时弹出一个提示
     * */
    fun getPlayerCargo():CargoAPI{
      if(Global.getSector()?.playerFleet?.cargo != null){
        return Global.getSector().playerFleet.cargo
      }
      addDebugLog("getPlayerCargo error: player cargo not found")
      return Global.getFactory().createCargo(false)
    }

    @JvmStatic
    fun checkCargoAvailable(engine: CombatEngineAPI?, ship: ShipAPI?): Boolean {
      if (engine == null) {
        if (ship != null && ship.owner == 0) {
          return true
        }
        if (ship == null) {
          return Global.getSector().playerFleet != null
        }
      } else {
        return engine.isInCampaign && !engine.isInCampaignSim && ship != null && ship.owner == 0
      }
      return false
    }

    @JvmStatic
    fun getNearestFriendCombatShip(e: CombatEntityAPI): ShipAPI? {
      var distMost = Float.MAX_VALUE
      var returnShip: ShipAPI? = null

      val ships = Global.getCombatEngine().ships

      for (s in ships) {
        if(s.owner != e.owner) continue
        if (!s.isFrigate && !s.isDestroyer && !s.isCruiser && !s.isCapital) continue

        val dist = MathUtils.getDistance(s, e)
        if (dist < distMost) {
          returnShip = s
          distMost = dist
        }
      }

      return returnShip
    }

    @JvmStatic
    fun getNearestEnemyCombatShip(e: CombatEntityAPI): ShipAPI? {
      var distMostSq = Float.MAX_VALUE
      var returnShip: ShipAPI? = null

      val ships = Global.getCombatEngine().ships

      for (s in ships) {
        if (s == e) continue
        //entity处于玩家侧时，还得排除黄圈的友军，不能只用owner
        if(isEnemy(e, s) == false) continue
        if (!s.isFrigate && !s.isDestroyer && !s.isCruiser && !s.isCapital) continue

        val distSq = MathUtils.getDistanceSquared(s, e)
        if (distSq < distMostSq) {
          returnShip = s
          distMostSq = distSq
        }
      }

      return returnShip
    }

    @JvmStatic
    fun getNearestEnemyCombatShip(loc: Vector2f, owner: Int): ShipAPI? {
      var distMost = Float.MAX_VALUE
      var returnShip: ShipAPI? = null

      val ships = Global.getCombatEngine().ships

      for (s in ships) {
        if (s.owner == owner) continue
        //entity处于玩家侧时，还得排除黄圈的友军，不能只用owner
        if (s.isAlly && owner == 0) continue
        if (!s.isFrigate && !s.isDestroyer && !s.isCruiser && !s.isCapital) continue

        val dist = MathUtils.getDistance(s, loc)
        if (dist < distMost) {
          returnShip = s
          distMost = dist
        }
      }

      return returnShip
    }

    @JvmStatic
    fun isNormalWeaponSlotType(slot: WeaponSlotAPI, containMissile: Boolean): Boolean {
      return if (slot.weaponType != WeaponAPI.WeaponType.DECORATIVE &&
        slot.weaponType != WeaponAPI.WeaponType.BUILT_IN &&
        slot.weaponType != WeaponAPI.WeaponType.LAUNCH_BAY &&
        slot.weaponType != WeaponAPI.WeaponType.SYSTEM &&
        slot.weaponType != WeaponAPI.WeaponType.STATION_MODULE) {
        if (containMissile) {
          true
        } else {
          slot.weaponType != WeaponAPI.WeaponType.MISSILE
        }
      } else false
    }

    @JvmStatic
    fun isNormalWeaponType(w: WeaponAPI, containMissile: Boolean): Boolean {
      return if (w.type != WeaponAPI.WeaponType.DECORATIVE &&
        w.type != WeaponAPI.WeaponType.BUILT_IN &&
        w.type != WeaponAPI.WeaponType.LAUNCH_BAY &&
        w.type != WeaponAPI.WeaponType.SYSTEM &&
        w.type != WeaponAPI.WeaponType.STATION_MODULE) {
        if (containMissile) {
          true
        } else {
          w.type != WeaponAPI.WeaponType.MISSILE
        }
      } else false
    }

    fun getOutOfRangeWeaponCostPercent(group: WeaponGroupAPI?, toTargetPo: Vector2f?, rangeFix: Float): Float {
      if (group == null) {
        return 1f
      }
      var allCost = 0.01f
      var ableToFireCost = 0.01f
      for (w in group.weaponsCopy) {
        if (w.distanceFromArc(toTargetPo) == 0f && isNormalWeaponSlotType(w.slot, false)) {
          allCost += w.spec.getOrdnancePointCost(null, null)
          if (MathUtils.getDistance(w.location, toTargetPo) > w.range - rangeFix) {
            ableToFireCost += w.spec.getOrdnancePointCost(null, null)
          }
        }
      }
      return MathUtils.clamp(ableToFireCost / allCost, 0f, 1f)
    }

    @JvmStatic
    fun toggleSystemControl(systemShip: ShipAPI, shouldUse: Boolean) {
      if (shouldUse && systemShip.system.state == ShipSystemAPI.SystemState.IDLE) {
        systemShip.useSystem()
      }
      val isActived = (systemShip.system.state == ShipSystemAPI.SystemState.ACTIVE ||
          systemShip.system.state == ShipSystemAPI.SystemState.IN ||
          systemShip.system.state == ShipSystemAPI.SystemState.OUT)

      if (!shouldUse && isActived) {
        systemShip.useSystem()
      }
    }

    @JvmStatic
    fun toggleShieldControl(systemShip: ShipAPI, shouldOn: Boolean) {
      val shield = systemShip.shield?: return
      if(shouldOn && shield.isOff) {
        systemShip.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK,null,0)
        return
      }

      if(!shouldOn && shield.isOn){
        systemShip.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK,null,0)
        return
      }
    }


    fun getShipLength(s: CombatEntityAPI): Float {
      if (s is ShipAPI && s.getExactBounds() != null) {
        var toReturn = 1f
        for (seg in s.getExactBounds().segments) {
          val range = MathUtils.getDistance(seg.p2, s.getLocation())
          if (toReturn < range) toReturn = range
        }
        return toReturn
      }
      return s.collisionRadius + 1f
    }

    @JvmStatic
    fun applyImpulse(entity: CombatEntityAPI, applyAngle: Float, impulse: Float, maxSpeed: Float) {
      val mass = Math.max(1f, entity.mass)
      val currAas = velocity2Speed(entity.velocity)
      val angleDist = MathUtils.getShortestRotation(applyAngle, currAas.x).absoluteValue
      val rad = Math.toRadians(angleDist.toDouble())

      val cos = FastTrig.cos(rad).toFloat()
      val projCurrSpeed = currAas.y * cos
      val level = ((maxSpeed - projCurrSpeed)/maxSpeed).coerceAtLeast(-1f).coerceAtMost(1f)
      if(level <= 0f) return

      val addVel = speed2Velocity(applyAngle,  impulse / mass)
      var newVel = Vector2f(entity.velocity.x + addVel.x, entity.velocity.y + addVel.y)

      entity.velocity.set(newVel)
    }

    @JvmStatic
    fun setMovement(entity: CombatEntityAPI, applyAngle: Float, impulse: Float, amount: Float) {
      val mass = Math.max(1f, entity.mass)
      val vel = speed2Velocity(applyAngle, impulse / mass)
      vel.scale(amount)


     entity.location.set(entity.location.x + vel.x, entity.location.y + vel.y)

    }

    fun getLongestNormalWeapon(ship: ShipAPI): WeaponAPI? {
      var longest = 0f
      var toReturn: WeaponAPI? = null
      for (w in ship.allWeapons) {
        if (isNormalWeaponType(w, false)) {
          if (w.range > longest) {
            longest = w.range
            toReturn = w
          }
        }
      }
      return toReturn
    }

    @JvmStatic
    fun addDebugText(input: String?) {
      Global.getCombatEngine().addFloatingText(Global.getCombatEngine().playerShip.location, input, 20f, Color(100, 100, 100, 100), Global.getCombatEngine().playerShip, 1f, 1f)
    }

    @JvmStatic
    fun addDebugLog(input: String?) {
      Global.getLogger(this.javaClass).info(input)
    }

    @JvmStatic
    fun addDebugText(input: String?, loc: Vector2f?) {
      Global.getCombatEngine().addFloatingText(loc, input, 20f, Color(100, 100, 100, 100), null, 1f, 1f)
    }

    @JvmStatic
    fun addDebugPoint(loc: Vector2f?){
      if(Global.getCombatEngine().isPaused) return
      MagicRender.battlespace(Global.getSettings().getSprite("graphics/fx/hit_glow.png"),
        loc,
        Vector2f(0f, 0f),
        Vector2f(24f, 24f),
        Vector2f(0f, 0f),
        0f,
        0f,
        Color(100, 250, 100, 120),
        true, 0f, 0.5f, 0.1f)
    }

    fun getColorWithAlphaChange(ori: Color, alphaMult: Float): Color {
      return Color(ori.red,
        ori.green,
        ori.blue,
        (ori.alpha * alphaMult).toInt().coerceAtLeast(0).coerceAtMost(255))
    }

    fun getColorWithAlpha(ori: Color, newAlphaLevel: Float): Color {
      return Color(ori.red, ori.green, ori.blue, MathUtils.clamp((255f * newAlphaLevel).toInt(),0,255))
    }

    fun getHitPoint(t: CombatEntityAPI, range: Float, facing: Float, point: Vector2f): Vector2f? {
      var toReturn: Vector2f? = null
      val checkPoint = getExtendedLocationFromPoint(point, facing, range)
      if (t is ShipAPI) {
        if (t.getShield() != null && t.getShield().isOn && t.getShield().isWithinArc(checkPoint) && MathUtils.getDistance(checkPoint, t.getLocation()) < t.getShield().radius) return checkPoint
      }
      toReturn = CollisionUtils.getCollisionPoint(point, checkPoint, t)
      return toReturn
    }

    @JvmStatic
    fun getFighterReplaceRate(reduceAmount: Float, ship: ShipAPI): Float {
      var totalFFR = 0f
      var num = 0
      for (bay in ship.launchBaysCopy) {
        num += 1
        val rate = Math.max(0.35f, bay.currRate - reduceAmount)
        bay.currRate = rate
        totalFFR += bay.currRate
      }
      return totalFFR / num
    }

    @JvmStatic
    fun getAmount(ship: ShipAPI?): Float {
      if (Global.getCombatEngine().isPaused) return 0f
      val engine = Global.getCombatEngine()
      return if (ship == null) engine.elapsedInLastFrame * engine.timeMult.modifiedValue else engine.elapsedInLastFrame * engine.timeMult.modifiedValue * ship.mutableStats.timeMult.modifiedValue
    }

    fun rotateVector(Vec: Vector2f, toAngle: Float, speed: Float, amount: Float): Vector2f {
      val angleNow = VectorUtils.getFacing(Vec)
      var turnAngle = MathUtils.getShortestRotation(angleNow, toAngle)
      if(turnAngle > 0) turnAngle = Math.min(turnAngle,speed*amount)
      else turnAngle = -Math.min(-turnAngle,speed*amount)
      return VectorUtils.rotate(Vec, turnAngle)
    }

    //exclude fighters
    fun findNearestEnemyShip(entity: CombatEntityAPI): ShipAPI? {
      var closest: ShipAPI? = null
      var distance: Float
      var closestDistance = Float.MAX_VALUE
      for (tmp in AIUtils.getEnemiesOnMap(entity)) {
        if (tmp.isFighter || tmp.owner == entity.owner) continue
        distance = MathUtils.getDistance(tmp, entity.location)
        if (distance < closestDistance) {
          closest = tmp
          closestDistance = distance
        }
      }
      return closest
    }

    //exclude fighters
    fun findNearestFriendyShip(entity: CombatEntityAPI): ShipAPI? {
      var closest: ShipAPI? = null
      var distance: Float
      var closestDistance = Float.MAX_VALUE
      for (tmp in AIUtils.getAlliesOnMap(entity)) {

        if (aEP_Tool.isEnemy(entity,tmp)) continue
        if(tmp.isFighter) continue
        distance = MathUtils.getDistance(tmp, entity.location)
        if (distance < closestDistance) {
          closest = tmp
          closestDistance = distance
        }
      }
      return closest
    }

    fun getAbsPos(angleAndLength: Vector2f, basePoint: Vector2f): Vector2f {
      val angle = angleAndLength.x
      val length = angleAndLength.y
      val xAxis = FastTrig.cos(Math.toRadians(angle.toDouble())).toFloat() * length
      val yAxis = FastTrig.sin(Math.toRadians(angle.toDouble())).toFloat() * length
      return Vector2f(xAxis + basePoint.x, yAxis + basePoint.y)
    }

    fun getAbsPos(angleAndLength: Vector2f, basePoint: Vector2f, baseAngle: Float): Vector2f {
      val angle = angleAndLength.x + baseAngle
      val length = angleAndLength.y
      val xAxis = FastTrig.cos(Math.toRadians(angle.toDouble())).toFloat() * length
      val yAxis = FastTrig.sin(Math.toRadians(angle.toDouble())).toFloat() * length
      return Vector2f(xAxis + basePoint.x, yAxis + basePoint.y)
    }

    fun firingSmoke(loc: Vector2f, facing: Float, param:FiringSmokeParam, ship: ShipAPI?) {
      val smokeSize = param.smokeSize
      val smokeRange = param.smokeSizeRange
      val smokeEndSizeMult = param.smokeEndSizeMult

      val smokeSpread = param.smokeSpread.coerceAtLeast(1f)
      val maxSpreadRange = param.maxSpreadRange

      val smokeTime = param.smokeTime

      val smokeColor = param.smokeColor
      val smokeAlphaMult = param.smokeAlpha
      val num: Int = param.smokeNum.toInt()

      val smokeStopSpeed = param.smokeStopSpeed
      val smokeSpeed = param.smokeInitSpeed

      //add cloud
      var i = 0
      while (i < num) {
        val size = smokeSize + MathUtils.getRandomNumberInRange(-smokeRange, smokeRange)
        val angleRandom = MathUtils.getRandomNumberInRange(-smokeSpread / 2f, smokeSpread / 2f)
        val angleMult = (smokeSpread / 2f - Math.abs(angleRandom)) / (1 + smokeSpread / 2f)
        val spawnDist = MathUtils.getRandomNumberInRange(0f, maxSpreadRange)
        val ms = aEP_MovingSmoke(getExtendedLocationFromPoint(loc, facing + angleRandom, spawnDist))
        ms.velocity = speed2Velocity(facing+angleRandom, smokeSpeed * angleMult * MathUtils.getRandom().nextFloat())
        ms.fadeIn = 0f
        ms.fadeOut = 0.8f
        ms.size = size
        ms.lifeTime = smokeTime
        ms.sizeChangeSpeed = (smokeEndSizeMult-1f) * size / smokeTime
        ms.color = aEP_Tool.getColorWithAlpha(smokeColor,smokeAlphaMult)
        ms.setInitVel(ship?.velocity?:Vector2f(0f,0f))
        ms.stopSpeed = smokeStopSpeed
        aEP_CombatEffectPlugin.addEffect(ms)
        i ++
      }
    }

    fun firingSmokeNebula(loc: Vector2f, facing: Float, param:FiringSmokeParam, ship: ShipAPI?) {
      val smokeSize = param.smokeSize
      val smokeRange = param.smokeSizeRange
      val smokeEndSizeMult = param.smokeEndSizeMult

      val smokeSpread = param.smokeSpread
      val maxSpreadRange = param.maxSpreadRange

      val smokeTime = param.smokeTime

      val smokeColor = param.smokeColor
      val smokeAlphaMult = param.smokeAlpha
      val num: Int = param.smokeNum.toInt()

      val smokeStopSpeed = param.smokeStopSpeed
      val smokeSpeed = param.smokeInitSpeed

      //add cloud
      var i = 0
      while (i < num) {
        val size = smokeSize + MathUtils.getRandomNumberInRange(-smokeRange, smokeRange)
        val angleRandom = MathUtils.getRandomNumberInRange(-smokeSpread / 2f, smokeSpread / 2f)
        val angleMult = (smokeSpread / 2f - Math.abs(angleRandom)) / (1 + smokeSpread / 2f)
        val spawnDist = MathUtils.getRandomNumberInRange(0f, maxSpreadRange)
        var vel = speed2Velocity(facing+angleRandom, smokeSpeed * angleMult * MathUtils.getRandom().nextFloat())
        vel = Vector2f(vel.x + (ship?.velocity?.x?:0f),vel.y + (ship?.velocity?.y?:0f) )
        Global.getCombatEngine().addNebulaParticle(
          getExtendedLocationFromPoint(loc, facing + angleRandom, spawnDist),
          vel,
          size, smokeEndSizeMult,
          0f, 0.2f,
          smokeTime, aEP_Tool.getColorWithAlpha(smokeColor,smokeAlphaMult))
        i ++
      }
    }

    fun isShipInPlayerFleet(ship:ShipAPI,alwaysTrueIfNotCampaign:Boolean): Boolean{
      if(Global.getCombatEngine().isInCampaign && Global.getSector().playerFleet.fleetData.membersListCopy.contains(ship.fleetMember)){
        return true
      }
      if(!Global.getCombatEngine().isInCampaign && alwaysTrueIfNotCampaign){
        return true
      }
      return false
    }

    /**
     * 加上自身的碰撞半径但是不加上对面的碰撞半径
     * @return 距离进入系统范围还有多远
     * */
    fun checkTargetWithinSystemRange(ship: ShipAPI, toLocation: Vector2f?, baseRange: Float): Int{
      //默认返回false，所以初始为-1f
      val range = ship.mutableStats?.systemRangeBonus?.computeEffective(baseRange) ?: -1f
      if(toLocation == null) return 9999
      val dist = MathUtils.getDistance(toLocation,ship.location) - ship.collisionRadius
      if(dist <= range){
        return 0
      }
      return (dist - range).toInt()

    }

    fun getInfoTextWithinSystemRange(ship: ShipAPI, toLocation: Vector2f?, baseRange: Float): String{
      if(toLocation == null) return "Need Target"
      val dist = checkTargetWithinSystemRange(ship, toLocation, baseRange)
      if(dist <= 0f){
        return "In Range"
      } else{
        //round to nearest 50
        val rounded =  ((dist / 50f).roundToInt() + 1 ) * 50
        return "Out of Range: $rounded"
      }


    }

    fun checkMouseWithinSystemRange(ship: ShipAPI?, baseRange: Float): Boolean{
      val range = ship?.mutableStats?.systemRangeBonus?.computeEffective(baseRange) ?: -9999999999f
      if(ship?.mouseTarget != null) {
        if(MathUtils.getDistance(ship.mouseTarget,ship.location) - ship.collisionRadius< range){
          return true
        }
      }
      return false
    }

    fun txtOfMouseWithinSystemRange(ship: ShipAPI?, baseRange: Float): String{
      val range = ship?.mutableStats?.systemRangeBonus?.computeEffective(baseRange) ?: -9999999999f
      if(ship?.mouseTarget != null) {
        if(MathUtils.getDistance(ship.mouseTarget,ship.location) - ship.collisionRadius< range){
          return ""
        }
      }
      return "Out of Range"
    }

    fun getSystemRange(ship: ShipAPI?, baseRange: Float):Float{
      val range = ship?.mutableStats?.systemRangeBonus?.computeEffective(baseRange) ?: baseRange
      return range
    }

    fun getRealDissipation(stats: MutableShipStatsAPI?):Float{
      stats?:return 0f
      return stats.fluxDissipation.modifiedValue ?:0f
    }

    fun getRealDissipation(ship: ShipAPI?):Float{
      ship?:return 0f
      return ship.mutableStats.fluxDissipation.modifiedValue ?:0f
    }

    fun spawnCompositeSmoke(loc: Vector2f, radius: Float, lifeTime:Float, color:Color?){
      val c = color?: Color(240,240,240)
      Global.getCombatEngine().addNebulaSmokeParticle(loc,
        aEP_ID.VECTOR2F_ZERO,
        radius,
        1f,
        0f,
        0f,
        lifeTime,
        getColorWithAlpha(c, 0.2f *(c.alpha/255f)))


      Global.getCombatEngine().addNebulaSmokeParticle(loc,
        aEP_ID.VECTOR2F_ZERO,
        radius,
        1f,
        0f,
        0f,
        lifeTime,
        getColorWithAlpha(c, 0.2f *(c.alpha/255f)))

      val smoke = aEP_MovingSmoke(loc)
      smoke.size = radius * 3f/4f
      smoke.fadeIn = 0.1f
      smoke.fadeOut = 0.8f
      smoke.lifeTime = lifeTime
      smoke.sizeChangeSpeed = 0f
      smoke.color = getColorWithAlpha(c, 0.4f *(c.alpha/255f))
      aEP_CombatEffectPlugin.addEffect(smoke)

      //生成烟雾
      //生成雾气
      var i = 0
      while (i < 360) {
        val p = getExtendedLocationFromPoint(loc, i.toFloat(), radius/2f)
        val smoke = aEP_MovingSmoke(p)
        smoke.size = radius/2f
        smoke.fadeIn = 0.1f
        smoke.fadeOut = 0.8f
        smoke.lifeTime = lifeTime
        smoke.sizeChangeSpeed = -(smoke.size/4f)/lifeTime
        smoke.setInitVel(speed2Velocity(i.toFloat(),smoke.sizeChangeSpeed/2f))
        smoke.color = getColorWithAlpha(c, 0.2f *(c.alpha/255f))
        aEP_CombatEffectPlugin.addEffect(smoke)
        i += 30
      }
    }

    fun spawnCompositeSmoke(loc: Vector2f, radius: Float, lifeTime:Float, color:Color?, vel:Vector2f){
      val c = color?: Color(240,240,240)
      Global.getCombatEngine().addNebulaSmokeParticle(loc,
        vel,
        radius,
        1f,
        0f,
        0f,
        lifeTime,
        getColorWithAlpha(c, 0.2f *(c.alpha/255f)))


      Global.getCombatEngine().addNebulaSmokeParticle(loc,
        vel,
        radius,
        1f,
        0f,
        0f,
        lifeTime,
        getColorWithAlpha(c, 0.2f *(c.alpha/255f)))

      val smoke = aEP_MovingSmoke(loc)
      smoke.size = radius * 3f/4f
      smoke.fadeIn = 0.1f
      smoke.fadeOut = 0.8f
      smoke.lifeTime = lifeTime
      smoke.sizeChangeSpeed = 0f
      smoke.color = getColorWithAlpha(c, 0.4f *(c.alpha/255f))
      smoke.setInitVel(vel)
      aEP_CombatEffectPlugin.addEffect(smoke)


      //生成烟雾
      //生成雾气
      var i = 0
      while (i < 360) {
        val p = getExtendedLocationFromPoint(loc, i.toFloat(), radius/2f)
        val smoke = aEP_MovingSmoke(p)
        smoke.size = radius/2f
        smoke.fadeIn = 0.1f
        smoke.fadeOut = 0.8f
        smoke.lifeTime = lifeTime
        smoke.sizeChangeSpeed = -(smoke.size/4f)/lifeTime
        smoke.setInitVel(speed2Velocity(i.toFloat(),smoke.sizeChangeSpeed/2f))
        smoke.setInitVel(vel)
        smoke.color = getColorWithAlpha(c, 0.2f *(c.alpha/255f))
        aEP_CombatEffectPlugin.addEffect(smoke)
        i += 45
      }
    }

    fun spawnSingleCompositeSmoke(loc: Vector2f, radius: Float, lifeTime:Float, color:Color?){
      val c = color?: Color(240,240,240)
      Global.getCombatEngine().addNebulaSmokeParticle(loc,
        Vector2f(0f,0f),
        radius,
        1.2f,
        0f,
        0f,
        lifeTime,
        getColorWithAlpha(c, 0.35f *(c.alpha/255f)))
      val smoke = aEP_MovingSmoke(loc)
      smoke.size = radius/2f
      smoke.fadeIn = 0.1f
      smoke.fadeOut = 0.8f
      smoke.lifeTime = lifeTime
      smoke.sizeChangeSpeed = (smoke.size/4f)/lifeTime
      smoke.color = getColorWithAlpha(c, 0.65f *(c.alpha/255f))
      aEP_CombatEffectPlugin.addEffect(smoke)

    }

    fun computeDamageToShip(source:ShipAPI?, target: ShipAPI?, weapon:WeaponAPI?, damage:Float, type:DamageType?, hitShield:Boolean): Float {
      var d = damage
      var weaponSpec = weapon?.spec?.type?:WeaponAPI.WeaponType.DECORATIVE

      val sourceStat : MutableShipStatsAPI? = source?.mutableStats
      val targetStat : MutableShipStatsAPI? = target?.mutableStats

      //武器加成
      var weaponMult = 1f
      when (weaponSpec) {
        WeaponAPI.WeaponType.BALLISTIC ->
          weaponMult = sourceStat?.ballisticWeaponDamageMult?.modifiedValue ?: 1f
        WeaponAPI.WeaponType.ENERGY ->
          weaponMult = sourceStat?.energyWeaponDamageMult?.modifiedValue ?: 1f
        WeaponAPI.WeaponType.MISSILE ->
          weaponMult = sourceStat?.missileWeaponDamageMult?.modifiedValue ?: 1f

        else -> {}
      }


      //光束加成
      var beamMult = 1f
      if(weapon?.isBeam == true) beamMult = sourceStat?.beamWeaponDamageMult?.modifiedValue ?: 1f

      //对护盾易伤加成
      var allDamageToShieldDealtMult = sourceStat?.damageToTargetShieldsMult?.modifiedValue ?: 1f


      //对舰体级别加成
      var targetSizeMult = 1f
      when (target?.hullSize?: ShipAPI.HullSize.DEFAULT) {
        ShipAPI.HullSize.CAPITAL_SHIP ->
          targetSizeMult = sourceStat?.damageToCapital?.modifiedValue ?: 1f
        ShipAPI.HullSize.CRUISER ->
          targetSizeMult = sourceStat?.damageToCruisers?.modifiedValue ?: 1f
        ShipAPI.HullSize.DESTROYER ->
          targetSizeMult = sourceStat?.damageToDestroyers?.modifiedValue ?: 1f
        ShipAPI.HullSize.FRIGATE ->
          targetSizeMult = sourceStat?.damageToFrigates?.modifiedValue ?: 1f
        ShipAPI.HullSize.FIGHTER ->
          targetSizeMult = sourceStat?.damageToFighters?.modifiedValue ?: 1f

        else -> {}
      }

      //目标舰体/装甲易伤加成
      var targetDamageTakenMult = 1f
      when (type) {
        DamageType.KINETIC ->
          targetDamageTakenMult = targetStat?.kineticDamageTakenMult?.modifiedValue ?: 1f
        DamageType.ENERGY ->
          targetDamageTakenMult = targetStat?.energyDamageTakenMult?.modifiedValue ?: 1f
        DamageType.HIGH_EXPLOSIVE ->
          targetDamageTakenMult = targetStat?.highExplosiveDamageTakenMult?.modifiedValue ?: 1f
        DamageType.FRAGMENTATION ->
          targetDamageTakenMult = targetStat?.fragmentationDamageTakenMult?.modifiedValue ?: 1f

        else -> {}
      }

      //目标护盾易伤加成
      var targetShieldDamageTakenMult = 1f
      when (type) {
        DamageType.KINETIC ->
          targetShieldDamageTakenMult = targetStat?.kineticShieldDamageTakenMult?.modifiedValue ?:1f
        DamageType.ENERGY ->
          targetShieldDamageTakenMult = targetStat?.energyShieldDamageTakenMult?.modifiedValue ?: 1f
        DamageType.HIGH_EXPLOSIVE ->
          targetShieldDamageTakenMult = targetStat?.highExplosiveShieldDamageTakenMult?.modifiedValue ?: 1f
        DamageType.FRAGMENTATION ->
          targetShieldDamageTakenMult = targetStat?.fragmentationShieldDamageTakenMult?.modifiedValue ?: 1f

        else -> {}
      }
      targetShieldDamageTakenMult *= targetDamageTakenMult

      //目标船体易伤加成
      var targetAllDamageTakenMult = targetStat?.hullDamageTakenMult?.modifiedValue ?: 1f
      //目标护盾易伤加成
      var targetShieldAllDamageTakenMult = targetStat?.shieldDamageTakenMult?.modifiedValue ?: 1f


      //目标投射物易伤加成
      var projDamageTakenMult = 1f
      if(weapon?.isBeam == false) projDamageTakenMult = targetStat?.projectileDamageTakenMult?.modifiedValue ?: 1f
      //目标投射物对护盾易伤加成
      var projShieldDamageTakenMult = 1f
      if(weapon?.isBeam == false) projShieldDamageTakenMult = targetStat?.projectileShieldDamageTakenMult?.modifiedValue ?: 1f
      projShieldDamageTakenMult *= projDamageTakenMult

      //目标光束易伤加成
      var beamDamgeTakenMult = 1f
      if(weapon?.isBeam == true) beamDamgeTakenMult = targetStat?.beamDamageTakenMult?.modifiedValue ?: 1f
      //目标光束对护盾易伤加成
      var beamShieldDamgeTakenMult = 1f
      if(weapon?.isBeam == true) beamShieldDamgeTakenMult = targetStat?.beamShieldDamageTakenMult?.modifiedValue ?: 1f
      beamShieldDamgeTakenMult *= beamDamgeTakenMult


      if(!hitShield?: false)
        return (d * weaponMult * beamMult * targetSizeMult * targetDamageTakenMult * targetAllDamageTakenMult *
            projDamageTakenMult * beamDamgeTakenMult)
      else
        return (d * weaponMult * beamMult * targetSizeMult * allDamageToShieldDealtMult * targetShieldDamageTakenMult * targetShieldAllDamageTakenMult *
            projShieldDamageTakenMult * beamShieldDamgeTakenMult)
    }

    fun applyPureArmorDamage(weapon: WeaponAPI?, d: Float, target: ShipAPI, point: Vector2f) {
      val engine = Global.getCombatEngine()
      val grid = target.armorGrid
      val cell = grid.getCellAtLocation(point) ?: return
      val gridWidth = grid.grid.size
      val gridHeight: Int = grid.grid[0].size
      var damageDealt = 0f
      for (i in -2..2) {
        for (j in -2..2) {
          if ((i == 2 || i == -2) && (j == 2 || j == -2)) continue  // skip corners
          val cx = cell[0] + i
          val cy = cell[1] + j
          if (cx < 0 || cx >= gridWidth || cy < 0 || cy >= gridHeight) continue
          var damMult = 1 / 30f
          damMult = if (i == 0 && j == 0) {
            1 / 15f
          } else if (i <= 1 && i >= -1 && j <= 1 && j >= -1) { // S hits
            1 / 15f
          } else { // T hits
            1 / 30f
          }
          val armorInCell = grid.getArmorValue(cx, cy)
          var damage = d * damMult
          damage = Math.min(damage, armorInCell)
          if (damage <= 0) continue
          target.armorGrid.setArmorValue(cx, cy, Math.max(0f, armorInCell - damage))
          damageDealt += damage
        }
      }
      if (damageDealt > 0) {
        if (Misc.shouldShowDamageFloaty(weapon?.ship, target)) {
          engine.addFloatingDamageText(point, damageDealt, Misc.FLOATY_ARMOR_DAMAGE_COLOR, target, weapon?.ship)
        }
        target.syncWithArmorGridState()
      }
    }

    fun isDead(ship: CombatEntityAPI) : Boolean{
      if(ship !is ShipAPI){
        return !Global.getCombatEngine().isEntityInPlay(ship)
      }

      if(!ship.isAlive || ship.isHulk || !Global.getCombatEngine().isEntityInPlay(ship)) return true
      return false
    }

    fun isEnemy(self:CombatEntityAPI, target: CombatEntityAPI): Boolean{
      //0 = player, 1 = enemy, 100 = neutral (used for ship hulks)
      //如果自己是绿圈，对方是红圈
      if(self.owner == 0){
        if(target.owner == 1) return true
      }

      //如果自己是红圈，对方不是红圈
      if(self.owner == 1){
        if(target.owner != 1) return true
      }

      //如果自己是黄圈，对方是红圈
      if(self is ShipAPI && self.isAlly){
        if(target.owner == 1) return true
      }

      return false
    }

    fun isShipTargetable(target: ShipAPI,
                          canAimPhased:Boolean,
                          canAimStation:Boolean,
                          canAimModule:Boolean,
                          canAimNoneCollision:Boolean,
                          canAimFighter:Boolean): Boolean{
      if(!Global.getCombatEngine().isEntityInPlay(target)) return false
      if(target.isHulk || !target.isAlive || !Global.getCombatEngine().isEntityInPlay(target)) return false
      if(!target.isTargetable) return false
      if(target.hasTag(Tags.VARIANT_FX_DRONE)) return false
      if(target.isPhased && !canAimPhased) return false
      if(target.collisionClass == CollisionClass.NONE && !canAimNoneCollision) return false
      if(target.isFighter && !canAimFighter) return false
      if(target.isStation && !canAimStation) return false
      if(target.isStationModule && !canAimModule) return false
      return true
    }

    fun getWeaponInSlot(slotId: String, ship: ShipAPI): WeaponAPI?{
      for(w in ship.allWeapons){
        if (w.slot.id.equals(slotId)){
          return w
        }
      }
      return null
    }

    /**
     * @param convertRate 单个格子每次loop的最大维修量，越低单次维修的越均匀，一般为5不会造成棋盘状装甲，不可为0
     * @return 维修剩余百分比，0f代表完全用于维修，1f代表不需要维修，本次维修值完全溢出
     * */
    fun findToRepair(ship: ShipAPI, repairAmount: Float, armorMaxPercent: Float, hpMaxPercent: Float, maxRepairPerGrid: Float, convertRate: Float, sparkEvenNotRepairing: Boolean): Float{
      //维修装甲
      val engine = Global.getCombatEngine()
      val xSize = ship.armorGrid.leftOf + ship.armorGrid.rightOf
      val ySize = ship.armorGrid.above + ship.armorGrid.below
      val cellMaxArmor = ship.armorGrid.maxArmorInCell

      var toRepair = repairAmount.coerceAtLeast(0f)
      var didSpark = false

      while (toRepair > 0f){
        //find the lowest armor grid
        var minArmorLevel = 10f
        var minX = 0
        var minY = 0
        for (x in 0 until xSize) {
          for (y in 0 until ySize) {
            val armorNow = ship.armorGrid.getArmorValue(x, y)
            val armorLevel = armorNow / cellMaxArmor
            if (armorLevel <= minArmorLevel) {
              minArmorLevel = armorLevel
              minX = x
              minY = y
            }
          }
        }

        val armorAtMin = ship.armorGrid.getArmorValue(minX, minY)
        val threshold = cellMaxArmor * armorMaxPercent
        val needRepair = threshold - armorAtMin

        // 如果当前最低的一块甲不满就修复
        if ( needRepair > 0f) {
          var toAddArmor = 0f

          if(needRepair > maxRepairPerGrid){
            toAddArmor = maxRepairPerGrid
            toRepair -= toAddArmor
          }else{
            toAddArmor = needRepair
            toRepair -= toAddArmor
          }
          ship.armorGrid.setArmorValue(minX, minY, armorAtMin + toAddArmor)
          //第一个修好的格子创造一个火花，每次维修最多生成一次
          val minArmorLoc = ship.armorGrid.getLocation(minX,minY)

          if(!didSpark){
            spawnRepairSpark(minArmorLoc,ship.velocity)
            didSpark = true
          }


        }else{//如果当前装甲最低的格子都不用修，break出去
          break
        }
      }

      //如果装甲修好了，维修点数还有省的就加结构
      if(toRepair > 0){
        val hullDamagedBelowThreshold = ship.maxHitpoints * hpMaxPercent - ship.hitpoints
        if(hullDamagedBelowThreshold > 0) {
          //如果之前没有修到装甲，目前需要修结构，就随机抽一个点刷闪光
          if(!didSpark ){
            val randomLoc = MathUtils.getRandomPointInCircle(ship.location,ship.collisionRadius*2f/3f)
            spawnRepairSpark(randomLoc,ship.velocity)
            didSpark = true
          }

          val repairToHull = (toRepair * convertRate).coerceAtMost(hullDamagedBelowThreshold)
          ship.hitpoints += repairToHull
          toRepair -= repairToHull / convertRate
        }
      }

      //如果结构也没修，装甲也没修，看看是否强制刷一个火花
      if(!didSpark && sparkEvenNotRepairing){
        val randomLoc = MathUtils.getRandomPointInCircle(ship.location,ship.collisionRadius*2f/3f)
        spawnRepairSpark(randomLoc,ship.velocity)
        didSpark = true
      }

      toRepair = toRepair.coerceAtLeast(0f)

      ship.syncWithArmorGridState()
      ship.syncWeaponDecalsWithArmorDamage()

      return toRepair/repairAmount
    }

    fun spawnRepairSpark(loc: Vector2f, vel: Vector2f){
      val engine = Global.getCombatEngine()
      engine.spawnExplosion(
        loc, vel, REPAIR_COLOR, 16f, 0.6f)
      engine.spawnExplosion(
        loc, vel, REPAIR_COLOR2, 32f, 0.4f)
      engine.addHitParticle(
        loc, vel, 150f, 0.75f, 0.1f, 0.2f, REPAIR_COLOR)
      var i = 0
      while (i < 36) {
        val speed = 25f + MathUtils.getRandomNumberInRange(0,175)
        val randomVel = speed2Velocity(MathUtils.getRandomNumberInRange(0f, 360f), speed)
        Vector2f.add(vel,randomVel,randomVel)
        engine!!.addSmoothParticle(
          loc,
          randomVel,  //velocity
          MathUtils.getRandomNumberInRange(3f, 8f),
          1f,  // brightness
          MathUtils.getRandomNumberInRange(0.25f, 0.5f),  //particle live time
          REPAIR_COLOR2)
        i += 1
      }
    }

    fun findEmptyLocationAroundPoint(point: Vector2f, checkStep: Float, maxCheckRange : Float):Vector2f{

      val startLoc = Vector2f(point)
      //当找不到完全不冲突的点时，使用次要选择
      //找到尽可能离其他船远的点，并且尽可能贴近原中心的点
      var emptyLocs = ArrayList<Vector2f>()
      var secondChoice = startLoc

      val startAngle = Math.random().toFloat() * 360f

      var maxSearch = maxCheckRange
      var i = 0
      var extend = 0f

      //周围360每个点都过一遍，从内圈到外圈，内圈如果找到了任何一个，就不用再找外圈了
      while (extend < maxSearch){
        i += 1
        extend = i * checkStep
        var angle = 0f
        val angleStep = 360f / (7f * extend/checkStep).toInt()
        while (angle < 360) {
          val currLoc = getExtendedLocationFromPoint(startLoc,angle+startAngle,  extend)
          //aEP_Tool.addDebugText(i.toString(),currLoc)
          //搜索一下距离这个点最近的舰船
          var didCollide = false
          for (other in Global.getCombatEngine().ships) {
            if(other.isPhased || other.collisionClass == CollisionClass.NONE) continue
            if(other.isFighter || other.collisionClass == CollisionClass.FIGHTER) continue
            val dist = MathUtils.getDistanceSquared(other, currLoc)
            //与任何其他一个船相碰，就跳过这个点
            if (dist <= 0f) {
              //如果这个碰撞的船半径比最大搜索半径都大，动态放大搜索半径
              if(other.collisionRadius > maxSearch) maxSearch = other.collisionRadius
              didCollide = true
              break
            }
          }
          //搜了全部的船都没有碰撞的，就认为这个点是备选点
          if(!didCollide) {
            emptyLocs.add(currLoc)
            break
          }
          angle += angleStep
        }
        if(!emptyLocs.isEmpty()) {
          break
        }

      }

      if (!emptyLocs.isEmpty()) {
        return emptyLocs[MathUtils.getRandomNumberInRange(0,emptyLocs.size-1)]
      }
      return secondChoice
    }

    fun computeCurrentManeuveringDir(ship: ShipAPI):Float{
      var angle = 0f
      //如果正在后退
      if(ship.engineController.isAcceleratingBackwards){
        angle = 180f
        if(ship.engineController.isStrafingLeft){
          angle = 135f
        }else if (ship.engineController.isStrafingRight){
          angle = 225f
        }
        //如果正在前进
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

      //如果正在进行减速，方向为当前速度的逆方向
      if(ship.engineController.isDecelerating && (ship.velocity.x > 0 || ship.velocity.y > 0)){
        val angleAndSpeed = velocity2Speed(ship.velocity)
        angle = angleAndSpeed.x - 180f
      }

      return angle
    }

    fun ignoreSlow(ship: ShipAPI, on:Boolean){
      val ID = "aEP_Tool_ignoreSlow"
      ship.mutableStats.maxSpeed.unmodify(ID)
      ship.mutableStats.acceleration.unmodify(ID)
      ship.mutableStats.deceleration.unmodify(ID)
      ship.mutableStats.maxTurnRate.unmodify(ID)
      ship.mutableStats.turnAcceleration.unmodify(ID)
      if(!on) return

      val modifiedMaxSpeed = ship.mutableStats.maxSpeed.modified+ 0.1f
      val baseMaxSpeed = ship.mutableStats.maxSpeed.baseValue
      if(modifiedMaxSpeed < baseMaxSpeed){
        ship.mutableStats.maxSpeed.modifyMult(ID, baseMaxSpeed/modifiedMaxSpeed)
      }

      ship.mutableStats.acceleration.unmodify(ID)
      val modifiedAcc = ship.mutableStats.acceleration.modified+ 0.1f
      val baseAcc = ship.mutableStats.acceleration.baseValue
      if(modifiedAcc < baseAcc) {
        ship.mutableStats.acceleration.modifyMult(ID, baseAcc / modifiedAcc)
      }

      ship.mutableStats.deceleration.unmodify(ID)
      val modifiedDec = ship.mutableStats.deceleration.modified+ 0.1f
      val baseDec = ship.mutableStats.deceleration.baseValue
      if(modifiedDec < baseDec) {
        ship.mutableStats.deceleration.modifyMult(ID, baseDec / modifiedDec)
      }

      ship.mutableStats.maxTurnRate.unmodify(ID)
      val modifiedTr = ship.mutableStats.maxTurnRate.modified+ 0.1f
      val baseTr = ship.mutableStats.maxTurnRate.baseValue
      if(modifiedTr < baseTr) {
        ship.mutableStats.maxTurnRate.modifyMult(ID, baseTr / modifiedTr)
      }

      ship.mutableStats.turnAcceleration.unmodify(ID)
      val modifiedTacc = ship.mutableStats.turnAcceleration.modified+ 0.1f
      val baseTacc = ship.mutableStats.turnAcceleration.baseValue
      if(modifiedTacc  < baseTacc) {
        ship.mutableStats.turnAcceleration.modifyMult(ID, baseTacc / modifiedTacc)
      }
    }

    fun ignoreShipInitialVel(entity: CombatEntityAPI, shipVel: Vector2f): Vector2f{
      val toReturn = Vector2f(0f,0f)
      toReturn.set(Vector2f.sub(entity.velocity, shipVel,null))
      return toReturn
    }

    fun addSmoothParticle(point: Vector2f, vel: Vector2f, size:Float, brightness:Float, durIn:Float,duration:Float, color: Color){
      val sprite = Global.getSettings().getSprite("aEP_FX","smooth_particle")
      sprite.setSize(size,size)
      sprite.color = getColorWithAlpha(color, brightness)
      sprite.setAdditiveBlend()
      MagicRenderPlugin.addBattlespace(
        sprite, Vector2f(point), Vector2f(vel),
        Misc.ZERO, 0f,
        durIn,0f, duration, CombatEngineLayers.ABOVE_PARTICLES)
    }

    fun addSmoothParticle(point: Vector2f, vel: Vector2f, size:Float, brightness:Float, durIn:Float, duration:Float, durOut:Float, color: Color){
      val sprite = Global.getSettings().getSprite("aEP_FX","smooth_particle")
      sprite.setSize(size,size)
      sprite.color = getColorWithAlpha(color, brightness)
      sprite.setAdditiveBlend()
      MagicRenderPlugin.addBattlespace(
        sprite, Vector2f(point), Vector2f(vel),
        Misc.ZERO, 0f,
        durIn,duration, durOut, CombatEngineLayers.ABOVE_PARTICLES)
    }

    fun addHitParticle(point: Vector2f, vel: Vector2f, size:Float, brightness:Float, durIn:Float, duration:Float, color: Color){
      val sprite = Global.getSettings().getSprite("aEP_FX","hit_particle")
      sprite.setSize(size,size)
      sprite.color = getColorWithAlpha(color, brightness)
      sprite.setAdditiveBlend()
      MagicRenderPlugin.addBattlespace(
        sprite, Vector2f(point), Vector2f(vel),
        Misc.ZERO, 0f,
        durIn,0f, duration, CombatEngineLayers.ABOVE_PARTICLES)
    }

    fun addHitParticle(point: Vector2f, vel: Vector2f, size:Float, brightness:Float, durIn:Float, duration:Float, durOut:Float, color: Color){
      val sprite = Global.getSettings().getSprite("aEP_FX","hit_particle")
      sprite.setSize(size,size)
      sprite.color = getColorWithAlpha(color, brightness)
      sprite.setAdditiveBlend()
      MagicRenderPlugin.addBattlespace(
        sprite, Vector2f(point), Vector2f(vel),
        Misc.ZERO, 0f,
        durIn, duration, durOut, CombatEngineLayers.ABOVE_PARTICLES)

    }

    fun addLensFlare(point: Vector2f,vel: Vector2f, size:Vector2f, growth:Vector2f, angle: Float, color: Color, durIn:Float,duration: Float){
      //横杠闪光
      val sprite = Global.getSettings().getSprite("graphics/fx/starburst_glow1.png")
      sprite.setSize(size.x,size.y)
      sprite.angle = angle
      sprite.color = color
      sprite.setAdditiveBlend()
      MagicRenderPlugin.addBattlespace(
        sprite, Vector2f(point), Vector2f(vel),
        growth, 0f,
        durIn, 0f, duration, CombatEngineLayers.ABOVE_PARTICLES)

    }

    fun addExplosionParticleCloud(point: Vector2f, maxRad:Float, coreRad:Float, sizeMin:Float,  sizeMax:Float, num:Int, duration:Float, color: Color){
     for ( i in 0 until num){
       var speed = maxRad/(duration+0.1f)
       val angle = MathUtils.getRandomNumberInRange(0f, 360f)
       val loc = getExtendedLocationFromPoint(point,angle,coreRad * MathUtils.getRandomNumberInRange(0f,1f))

       speed *= MathUtils.getRandomNumberInRange(0.25f,1.5f)
       val vel = speed2Velocity(angle, speed)

       val size = MathUtils.getRandomNumberInRange(sizeMin, sizeMax)
       val time = duration * MathUtils.getRandomNumberInRange(0.25f,1.5f)
       addSmoothParticle(loc,vel,size,(color.alpha/255.1f),
         0f,time * 0.25f,time * 0.75f ,color)
     }

    }

    fun spawnDirectionalExplosion(
      point: Vector2f, facing: Float , num: Int, minSize:Float, maxSize:Float, color: Color,
      minDur:Float, maxDur:Float, spreadArc:Float, scatter:Float, minVel:Float, maxVel:Float,
      endSizeMultMin:Float,  endSizeMultMax:Float) {
      if (Global.getCombatEngine().viewport.isNearViewport(point, maxVel * maxDur + maxSize * endSizeMultMax + 100f)) {

        val spawnPoint = Vector2f(point)
        for (i in 0 until num) {
          var angleOffset = Math.random().toFloat()
          if (angleOffset > 0.2f) {
            angleOffset *= angleOffset
          }
          var speedMult = 1f - angleOffset
          speedMult = 0.5f + speedMult * 0.5f
          angleOffset *= sign((Math.random().toFloat() - 0.5f))
          angleOffset *= spreadArc / 2f
          val theta = Math.toRadians((facing + angleOffset).toDouble()).toFloat()
          val r = (Math.random() * Math.random() * scatter).toFloat()
          val x = cos(theta.toDouble()).toFloat() * r
          val y = sin(theta.toDouble()).toFloat() * r
          val pLoc = Vector2f(spawnPoint.x + x, spawnPoint.y + y)
          var speed = minVel + (maxVel - minVel) * Math.random().toFloat()
          speed *= speedMult
          val pVel = Misc.getUnitVectorAtDegreeAngle(Math.toDegrees(theta.toDouble()).toFloat())
          pVel.scale(speed)
          val pSize = minSize + (maxSize - minSize) * Math.random().toFloat()
          val pDur = minDur + (maxDur - minDur) * Math.random().toFloat()
          val endSize = endSizeMultMin + (endSizeMultMax - endSizeMultMin) * Math.random().toFloat()
          Global.getCombatEngine().addNebulaParticle(pLoc, pVel, pSize, endSize, 0.1f, 0.5f, pDur, color)
        }
      }
    }

    /**
     * 如果武器下线，把血量修到离满只差10滴血，下一帧让电脑自动修复
    * */
    fun keepWeaponAlive(w: WeaponAPI){
      if(w.isDisabled && w.currHealth < w.maxHealth-10f){
        w.currHealth = w.maxHealth-10f
      }
    }

    fun attachMissileToProj(entity: MissileAPI, range: Float, speed: Float){
      val spec = (Global.getSettings().getWeaponSpec("aEP_ut_missile_attacher").projectileSpec as ProjectileSpecAPI)
      spec.maxRange = range
      spec.setMoveSpeed(speed)
      val attachProj = Global.getCombatEngine().spawnProjectile(
        null,null,"aEP_ut_missile_attacher",entity.location,entity.facing,null) as BallisticProjectile

      aEP_CombatEffectPlugin.addEffect(object : aEP_BaseCombatEffect(0f, attachProj){
        val missile = entity

        override fun advanceImpl(amount: Float) {
          missile.velocity.set(attachProj.velocity)
          missile.location.set(attachProj.location)

          if(!Global.getCombatEngine().isEntityInPlay(missile)){
            shouldEnd = true
          }
        }

        override fun readyToEnd() {
          Global.getCombatEngine().removeEntity(attachProj)
          if(Global.getCombatEngine().isEntityInPlay(missile) && !missile.isFizzling){
            missile.flameOut()
          }
        }
      })

      spec.maxRange = 1f
      spec.setMoveSpeed(1f)
    }

    fun compensateMissileInitialSpeed(projectile: MissileAPI){
      val speedNow = projectile.spec.launchSpeed
      val maxSpeed = projectile.maxSpeed
      val range = projectile.maxRange
      var acc = projectile.acceleration
      if(maxSpeed <= speedNow) return
      if(acc <= 0) return
      if(range <= 0) return
      val speedDiff = maxSpeed - speedNow
      val timeToAcc = speedDiff/acc
      val timeCompensation = timeToAcc/2f
      projectile.maxFlightTime = range/maxSpeed
      projectile.maxFlightTime += timeCompensation
    }
  }
  class FiringSmokeParam{
    var smokeSize = 0f
    var smokeEndSizeMult = 1.5f
    var smokeSizeRange = 0f
    //角度散步，不能为0，否则烟雾不动
    var smokeSpread = 1f
    var smokeTime = 0f
    //出生距离，会在0到这个范围内刷出烟
    var maxSpreadRange = 0f
    var smokeColor = Color(230,230,230)
    var smokeAlpha = 1f
    var smokeNum = 0

    var smokeInitSpeed = 100f
    //每0.1秒速度乘一次这个系数
    var smokeStopSpeed = 0.9f
  }
}

class aEP_ID{
  companion object{
    val VECTOR2F_ZERO = Vector2f(0f,0f)
    const val FACTION_ID_FSF = "aEP_FSF"
    const val FACTION_ID_FSF_ADV = "aEP_FSF_adv"
    const val HULLMOD_POINT = "#"
    const val HULLMOD_BULLET = "    -- "
    const val CONFIRM = "Confirm"
    const val CANCEL = "Cancel"
    const val RETURN = "Return"
    const val SELECT = "Select"

    const val SYSTEM_SHIP_TARGET_KEY = "aEP_systemShipTargetKey"

    const val FACTION_NAME_EN = "FSF Military Corporation"

    const val FACTION_RANK_FACTION_LEADER = "Board Representative"

    const val FACTION_POST_PROGRAM_DIRECTOR = "Program Director"
    const val FACTION_POST_FACTION_LEADER = "Board Representative"
    const val FACTION_POST_BUSINESS_MANAGER = "Business Manager"

  }
}

class aEP_Render{
  companion object{
    //保持引用，节约资源
    val FONT1 = LazyFont.loadFont("graphics/fonts/victor14.fnt")
    var drawableString = FONT1.createText()

    /**
     * 在combatLayerRenderingPlugin里面必须使用战场绝对坐标
     * */
    fun openGL11CombatLayerRendering() {
      //这些设定都要在begin之前设置好
      GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
      GL11.glMatrixMode(GL11.GL_PROJECTION)
      GL11.glPushMatrix()

      //画纯色图不需要材质，打开材质就一定要绑定，就会导致画不出东西
      GL11.glDisable(GL11.GL_TEXTURE_2D)
      //这里不做绑定
      //GL11.glBindTexture(GL11.GL_TEXTURE_2D, Global.getSettings().getSprite("aEP_FX", "thick_smoke_all2").textureId)

      GL11.glEnable(GL11.GL_BLEND)
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    }


    /**
     * From MagicUI,使用相对屏幕的坐标
     * */
    fun openGL11() {
      //这些设定都要在begin之前设置好

      GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
      GL11.glMatrixMode(GL11.GL_PROJECTION)
      GL11.glPushMatrix()

      //设置视窗的起点(单位为像素)，长宽
      GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight())
      //设置投影方式，x坐标变为窗口width，y坐标变为窗口height
      GL11.glOrtho(0.0, Display.getWidth().toDouble(), 0.0, Display.getHeight().toDouble(), -1.0, 1.0)

      //画纯色图不需要材质，打开材质就一定要绑定，就会导致画不出东西
      GL11.glDisable(GL11.GL_TEXTURE_2D)
      //这里不做绑定
      //GL11.glBindTexture(GL11.GL_TEXTURE_2D, Global.getSettings().getSprite("aEP_FX", "thick_smoke_all2").textureId)

      GL11.glEnable(GL11.GL_BLEND)
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    }


    /**
     * From MagicUI
     * */
    fun closeGL11() {
      GL11.glDisable(GL11.GL_TEXTURE_2D)
      GL11.glDisable(GL11.GL_BLEND)
      GL11.glPopMatrix()
      GL11.glPopAttrib()
    }

  }

}

class aEP_Combat{
  companion object{
    fun getTargetCurrentAimed (id : String, target: CombatEntityAPI) : Float{
      val data = target.customData[id] as Float?
      return data?:0f
    }
  }


  /**
   * 一个单纯的计数类，加入时给key加1f，结束时给key减1f，用于各种防止不同舰船的同一系统使用在同一目标上
   * */
  class MarkTarget(liftime:Float, val id:String, val addOrRemove:Float, val target:CombatEntityAPI)
    : aEP_BaseCombatEffect(liftime, target){

    init {
      val data = target.customData[id] as Float?
      data?.let { target.setCustomData(id, data + addOrRemove)  }
        ?: let { target.setCustomData(id, addOrRemove)   }
    }

    override fun readyToEnd() {
      val data = target.customData[id] as Float?
      data?.let { target.setCustomData(id, data - addOrRemove)  }
        ?: let { target.setCustomData(id, 0f)   }
    }
  }

  /**
   * onRecall()可以自由修改，特效部分不需要飞机存在母舰
   * */
  open class RecallFighterJitter: aEP_BaseCombatEffect{
    companion object{
      const val ID = "aEP_RecallFighter"
    }

    var color = RecallDeviceStats.JITTER_COLOR

    constructor(lifeTime: Float, f:ShipAPI) : super(lifeTime, f){
      f.setCustomData(ID,1f)
    }

    override fun advance(amount: Float) {
      //若 entity不为空，则进行 entity检测，不过就直接结束
      if(entity != null) {
        loc.set(entity?.location?:Vector2f(0f,0f))

        if(!Global.getCombatEngine().isEntityInPlay(entity)){
          shouldEnd = true
        }

        if(entity is ShipAPI){
          val ship = entity as ShipAPI
          if(!ship.isAlive || ship.isHulk){
            shouldEnd = true
          }
        }
      }

      if(shouldEnd) return

      time += amount
      MathUtils.clamp(time,0f,lifeTime)
      advanceImpl(amount)

      //播放特效
      val fighter = entity as ShipAPI
      val effectLevel = time/lifeTime
      val maxRangeBonus = fighter.collisionRadius * 1f
      val jitterRangeBonus: Float = 5f + effectLevel * maxRangeBonus
      fighter.isJitterShields = true
      fighter.setJitter(ID, color, effectLevel, 10, 0f, jitterRangeBonus)

      //被召回是一种相位，需要持续维持
      if (fighter.isAlive) fighter.isPhased = true
      val alpha = 1f - effectLevel * 0.5f
      fighter.extraAlphaMult = alpha

      if(time >= lifeTime && lifeTime > 0){
        //不可以在这里removeEntity，会造成jitter异常，很怪
        //onRecall()
        shouldEnd = true

      }


    }

    open fun onRecall(){

    }

    override fun readyToEnd() {
      val fighter = entity as ShipAPI
      fighter.isPhased = false
      fighter.extraAlphaMult = 1f
      fighter.removeCustomData(ID)

      if(!aEP_Tool.isDead(fighter)){
        onRecall()
      }

    }
  }

  open class StandardTeleport(lifeTime: Float, ship:ShipAPI, val toLoc: Vector2f, val facing:Float): aEP_BaseCombatEffect(lifeTime, ship){
    companion object{
      const val ID = "aEP_StandardTeleport"
    }

    var color = RecallDeviceStats.JITTER_COLOR

    init {
      ship.setCustomData(ID,1f)

    }

    override fun advance(amount: Float) {
      super.advance(amount)

      val fighter = entity as ShipAPI

      val effectLevel = time/lifeTime
      val maxRangeBonus = fighter.collisionRadius * 0.25f
      val jitterRangeBonus: Float = 20f + effectLevel * maxRangeBonus
      fighter.setJitter(ID, color, effectLevel, 10, 0f, jitterRangeBonus)

      //在落点来几个残影，最后一个在正中心不抖动
      val sprite = Global.getSettings().getSprite(fighter.hullSpec.spriteName)
      val size = Vector2f( sprite.width,sprite.height)
      val renderLoc = MathUtils.getRandomPointInCircle(toLoc,20f * (0.1f + 0.9f*(1f-effectLevel)) )
      val c = Misc.setAlpha(color,(255 * effectLevel).toInt())
      for(i in 0 until 4){
        MagicRender.singleframe(sprite,
          renderLoc, size,
          //magicRender的角度开始点比游戏多90
          facing-90f, c, true)
      }
      MagicRender.singleframe(sprite,
        toLoc, size,
        //magicRender的角度开始点比游戏多90
        facing-90f, c, true)

      val alpha = 1f - effectLevel * 0.5f
      fighter.extraAlphaMult = alpha


    }

    override fun readyToEnd() {
      val fighter = entity as ShipAPI
      fighter.isPhased = false
      fighter.extraAlphaMult = 1f
      fighter.removeCustomData(ID)

      //如果是正常通过时间导致的结束
      if(time >= lifeTime && !aEP_Tool.isDead(fighter)){
        val c = Misc.setAlpha(color,255)

        fighter.location.set(toLoc)
        Global.getCombatEngine().addHitParticle(
          toLoc,Misc.ZERO,fighter.collisionRadius+200f,
          1f,0.1f,0.2f,c)
        Global.getCombatEngine().addHitParticle(
          toLoc,Misc.ZERO,fighter.collisionRadius+200f,
          1f,0.1f,0.4f,c)

        for(i in 1..3){
          val relativeLoc = VectorUtils.getDirectionalVector(fighter.velocity, Vector2f(0f,0f))
          var velocity = Vector2f(-relativeLoc.x, -relativeLoc.y)
          val dist = (fighter.collisionRadius)/i
          relativeLoc.scale(dist)
          velocity.scale(dist * 3f)
          val dur = 0.233f
          val out = 0.1f

          fighter.addAfterimage(
            c,
            relativeLoc.x, relativeLoc.y,
            velocity.x, velocity.y, 5f, 0f,
            dur,
            out, true, false, true)

          fighter.shipAI?.cancelCurrentManeuver()
          fighter.shipAI?.forceCircumstanceEvaluation()
        }

        onTel()
      }
    }

    /**
     * 在舰船的坐标已经移动，特效已经生成，ai被强制重新索敌之后
     * */
    open fun onTel(){

    }
  }

  open class AddJitterBlink(val up: Float, val full:Float, val down: Float, val ship:ShipAPI): aEP_BaseCombatEffect(up+full+down, ship){

    init {
      aEP_CombatEffectPlugin.addEffect(this)
    }

    companion object{
      const val ID = "aEP_JitterBlink"
    }

    var color = RecallDeviceStats.JITTER_COLOR
    var jitterUnder = false
    var jitterShield = false
    var maxRange = 10f
    var maxRangePercent = 0.2f
    var copyNum = 5

    override fun advanceImpl(amount: Float) {
      var level = 1f
      if(time < up) level = time/up
      if(time > up + full) level = 1f-((time -up-full)/down)
      val c = Misc.scaleAlpha(color,level)
      if(jitterUnder){
        ship.isJitterShields = jitterShield
        ship.setJitterUnder(ID, c, level, copyNum, (maxRange + ship.collisionRadius * maxRangePercent) * level)
      }else{
        ship.isJitterShields = jitterShield
        ship.setJitter(ID, c, level, copyNum, (maxRange + ship.collisionRadius * maxRangePercent) * level)
      }
    }

  }


  /**
   * 在init里面包含将自己加入plugin的部分，使用只需要new。多个不同来源的减速效果取最大值
   * */
  class AddStandardSlow(slowTime: Float, slowReduceMult: Float, accReduceMult: Float , val target: ShipAPI) : aEP_BaseCombatEffect(0f,target){
    companion object{
      const val ID = "aEP_StandardSlow"
    }

    val data = ArrayList<SlowData>()

    init {
      val slowData = SlowData()
      if(slowTime < 1f){
        slowData.fullTime = 0.1f
        slowData.fadingTime = slowTime
      }else{
        slowData.fullTime = slowTime
        slowData.fadingTime = 0.5f
      }

      slowData.speedReduceMult = slowReduceMult
      slowData.accReduceMult = accReduceMult

      //正在处于别的减速buff中
      if(target.customData.containsKey(ID)){
        val slowManager = target.customData[ID] as AddStandardSlow
        slowManager.data.add(slowData)
      }else{ //第一次被减速
        target.setCustomData(ID, this)
        data.add(slowData)
        aEP_CombatEffectPlugin.addEffect(this)
      }
    }


    override fun advanceImpl(amount: Float) {
      var maxReduceMult = 0f
      var maxAccReduceMult = 0f

      val expired = HashSet<SlowData>()
      for(d in data) {
        var reduce = d.speedReduceMult
        var accReduce = d.accReduceMult
        if(d.timeElapsed > d.fullTime) {
          val level = ((d.timeElapsed - d.fullTime)/d.fadingTime).coerceAtMost(1f)
          reduce *= (1f - level)
          accReduce *= (1f - level)
        }
        //找到当前最大的减速值
        if(reduce > maxReduceMult) maxReduceMult = reduce
        if(accReduce > maxAccReduceMult) maxAccReduceMult = accReduce
        //advance计时器
        d.timeElapsed += amount
        if(d.timeElapsed >= d.fullTime + d.fadingTime) expired.add(d)
      }
      data.removeAll(expired)

      maxReduceMult = maxReduceMult.coerceAtMost(1f)
      maxAccReduceMult = maxAccReduceMult.coerceAtMost(1f)
      //修改数据
      target.mutableStats.maxSpeed.modifyMult(ID, 1f - maxReduceMult)

      target.mutableStats.acceleration.modifyMult(ID, 1f - maxAccReduceMult)
      target.mutableStats.deceleration.modifyMult(ID, 1f - maxAccReduceMult)

      target.mutableStats.maxTurnRate.modifyMult(ID, 1f - maxAccReduceMult)
      target.mutableStats.turnAcceleration.modifyMult(ID, 1f - maxAccReduceMult)

      if(data.size <= 0){
        shouldEnd = true
      }

      //一艘船只能同时存在一个manager类
      if(target.customData.containsKey(ID) && target.customData[ID] != this){
        shouldEnd = true
      }
    }

    override fun readyToEnd() {
      //修改数据
      target.mutableStats.maxSpeed.unmodify(ID)

      target.mutableStats.acceleration.unmodify(ID)
      target.mutableStats.deceleration.unmodify(ID )

      target.mutableStats.maxTurnRate.unmodify(ID )
      target.mutableStats.turnAcceleration.unmodify(ID)

      if(target.customData.containsKey(ID) && target.customData[ID] == this){
        target.customData.remove(ID)
      }
    }

    class SlowData{
      var accReduceMult = 0.25f
      var speedReduceMult = 0.25f
      var fadingTime = 0.5f
      var fullTime = 0.5f
      var timeElapsed = 0f
    }
  }

  class AddDamageReduction(lifeTime: Float, damageReduceMult: Float, val target: CombatEntityAPI) : aEP_BaseCombatEffect(0f,target){
    companion object{
      const val ID = "aEP_DamageReduce"
    }

    val allData = ArrayList<Data>()

    init {
      if(target is ShipAPI || target is DamagingProjectileAPI) {
        val data = Data()
        data.fullTime = lifeTime
        data.damageReduceMult = damageReduceMult

        //正在处于别的buff中
        if(target.customData.containsKey(ID)){
          val manager = target.customData[ID] as AddDamageReduction
          manager.allData.add(data)
        }else{ //第一次被施加debuff
          target.setCustomData(ID, this)
          allData.add(data)
          aEP_CombatEffectPlugin.addEffect(this)
        }
      }
    }

    override fun advanceImpl(amount: Float) {
      var maxReduceMult = 0f

      val expired = HashSet<Data>()
      for(d in allData) {
        var reduce = d.damageReduceMult
        //找到当前最大的减速值
        if(reduce > maxReduceMult) maxReduceMult = reduce
        //advance计时器
        d.timeElapsed += amount
        if(d.timeElapsed >= d.fullTime) expired.add(d)
      }
      allData.removeAll(expired)
      maxReduceMult = maxReduceMult.coerceAtMost(1f)


      //修改数据
      if(target is ShipAPI){
        target.mutableStats.damageToFighters.modifyMult(ID, 1f - maxReduceMult)
        target.mutableStats.damageToFrigates.modifyMult(ID, 1f - maxReduceMult)
        target.mutableStats.damageToDestroyers.modifyMult(ID, 1f - maxReduceMult)
        target.mutableStats.damageToCruisers.modifyMult(ID, 1f - maxReduceMult)
        target.mutableStats.damageToCapital.modifyMult(ID, 1f - maxReduceMult)
      }
      if(target is DamagingProjectileAPI){
        target.damage.modifier.modifyMult(ID,1f - maxReduceMult)
      }
      //对于dem，找到距离完全为0的fxdrone，施加减低伤害的buff
      if(target is MissileAPI){
        if(target.weapon != null && target.projectileSpec?.onFireEffect is DEMEffect){
          for(f in Global.getCombatEngine().ships){
            if(!f.variant.hasTag(VARIANT_FX_DRONE)) continue
            //距离完全为0
            if(MathUtils.getDistance(f.location, target.location) > 0) continue
            AddDamageReduction(0.25f, maxReduceMult , f)
          }
        }
      }

      if(allData.size <= 0){
        shouldEnd = true
      }

      //一艘船只能同时存在一个manager类
      if(target.customData.containsKey(ID) && target.customData[ID] != this){
        shouldEnd = true
      }
    }

    override fun readyToEnd() {
      //修改数据
      if(target is ShipAPI){
        target.mutableStats.damageToFighters.unmodify(ID)
        target.mutableStats.damageToFrigates.unmodify(ID)
        target.mutableStats.damageToDestroyers.unmodify(ID)
        target.mutableStats.damageToCruisers.unmodify(ID)
        target.mutableStats.damageToCapital.unmodify(ID)
      }
      if(target is DamagingProjectileAPI){
        target.damage.modifier.unmodify(ID)
      }

      if(target.customData.containsKey(ID) && target.customData[ID] == this){
        target.customData.remove(ID)
      }
    }

    class Data{
      var damageReduceMult = 0f
      var fullTime = 0.5f
      var timeElapsed = 0f
    }
  }

}