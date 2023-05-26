//by a111164
package combat.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.util.Misc
import combat.impl.VEs.aEP_MovingSmoke
import combat.plugin.aEP_CombatEffectPlugin
import data.scripts.util.MagicRender
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.FastTrig
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.awt.geom.Line2D
import kotlin.math.abs
import kotlin.math.asin

class aEP_Tool {

  companion object Util {
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
      //如果船飞过圆弧需要时间小于转向需要时间,即转向时间不足，减速
      if( timeArcSq < timeTurn*timeTurn ){
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
      val entity = ship
      val directionVec =  VectorUtils.resize(VectorUtils.getDirectionalVector(entity.location, toPosition),ship.maxSpeed)
      val directionAngle = VectorUtils.getFacing(directionVec)
      val velDirectDiff = Vector2f(directionVec.x-ship.velocity.x,directionVec.y-ship.velocity.y)
      val diffAngle = aEP_Tool.angleAdd(VectorUtils.getFacing(velDirectDiff),-ship.facing)
      val distSq = MathUtils.getDistanceSquared(entity.location, toPosition)
      val angleAndSpeed = velocity2Speed(entity.velocity)
      val timeToSlowDown = angleAndSpeed.y / entity.deceleration //time to slow down to zero speed(by seconds,squared)

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
      if (distSq <= (angleAndSpeed.y * timeToSlowDown + 10) * (angleAndSpeed.y * timeToSlowDown + 10)) {
        entity.giveCommand(ShipCommand.DECELERATE, null, 0)
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
      val x = VectorUtils.getFacing(velocity)
      val y = Math.sqrt((velocity.getX() * velocity.getX() + velocity.getY() * velocity.getY()).toDouble()).toFloat()
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
      if (Math.abs(MathUtils.getShortestRotation(weapon.slot.angle + ship.facing, angle)) > weapon.slot.arc / 2) {
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
          0.5f
        ) //duration
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
        ship.mutableStats.acceleration.unmodify(id )
        //如果之前开始了降落动画，而因为某些原因离开母舰100距离，取消降落
        moveToPosition(ship, toTargetPo)
        if (landingStarted) {
          ship.abortLanding()
        }
      //距离降落100内时，增加额外的机动性，准备平移到50内开始降落
      } else {
        ship.mutableStats.maxSpeed.modifyFlat(id, parentShip.maxSpeed + ship.maxSpeed)
        ship.mutableStats.acceleration.modifyFlat(id,parentShip.maxSpeed )
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

    fun getWeaponOffsetInAbsoluteCoo(w: WeaponAPI): List<Vector2f> {
      val absCoo: MutableList<Vector2f> = ArrayList()
      if (w.slot.isTurret) {
        for (offset in w.spec.turretFireOffsets) {
          var absOffset = getExtendedLocationFromPoint(w.location, w.currAngle, offset.x)
          absOffset = getExtendedLocationFromPoint(absOffset, angleAdd(w.currAngle, 90f), offset.y)
          absCoo.add(absOffset)
        }
      } else {
        for (offset in w.spec.hardpointFireOffsets) {
          var absOffset = getExtendedLocationFromPoint(w.location, w.currAngle, offset.x)
          absOffset = getExtendedLocationFromPoint(absOffset, angleAdd(w.currAngle, 90f), offset.y)
          absCoo.add(absOffset)
        }
      }
      return absCoo
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

    fun limitToTop(input: Float, limit: Float): Float {
      return if (input > limit) {
        limit
      } else input
    }

    @JvmStatic
    fun limitToTop(input: Float, limitMax: Float, limitMin: Float): Float {
      if (input > limitMax) {
        return limitMax
      }
      return if (input < limitMin) {
        limitMin
      } else input
    }

    fun getFirstDecimal(input: Float): Float {
      val input2string = java.lang.Float.toString(input)
      val allString = input2string.split("\\.".toRegex()).toTypedArray()
      return allString[1].toFloat()
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
    fun getNearestFriendCombatShip(e: CombatEntityAPI?): ShipAPI? {
      var distMost = Float.MAX_VALUE
      var returnShip: ShipAPI? = null
      for (s in AIUtils.getAlliesOnMap(e)) {
        if (s.isFrigate || s.isDestroyer || s.isCruiser || s.isCapital) {
          val dist = MathUtils.getDistance(e, s)
          if (dist < distMost) {
            returnShip = s
            distMost = dist
          }
        }
      }
      return returnShip
    }

    @JvmStatic
    fun getNearestEnemyCombatShip(e: CombatEntityAPI?): ShipAPI? {
      var distMost = Float.MAX_VALUE
      var returnShip: ShipAPI? = null
      for (s in AIUtils.getEnemiesOnMap(e)) {
        if (s.isFrigate || s.isDestroyer || s.isCruiser || s.isCapital) {
          val dist = MathUtils.getDistance(e, s)
          if (dist < distMost) {
            returnShip = s
            distMost = dist
          }
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

    @JvmStatic
    fun getRandomPointAround(center: Vector2f, range: Float): Vector2f {
      val angle = MathUtils.getRandomNumberInRange(0, 360).toFloat()
      val dist = MathUtils.getRandomNumberInRange(0f, range)
      return getExtendedLocationFromPoint(center, angle, dist)
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
      return limitToTop(ableToFireCost / allCost, 1f, 0f)
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
    fun applyImpulse(entity: CombatEntityAPI, applyAngle: Float, impulse: Float) {
      val mass = Math.max(1f, entity.mass)
      val addVel = speed2Velocity(applyAngle, impulse / mass)
      var newVel = Vector2f(entity.velocity.x + addVel.x, entity.velocity.y + addVel.y)
      //转换为速度
      var angleAndSpeed = velocity2Speed(newVel)

      //游戏限速599
      angleAndSpeed.setY(Math.min(599f, angleAndSpeed.y))

      newVel = speed2Velocity(angleAndSpeed)
      entity.velocity.set(newVel)
    }


    @JvmStatic
    fun isWithinArc(target: ShipAPI?, weapon: WeaponAPI): Boolean {
      return CollisionUtils.getCollisionPoint(weapon.location, getExtendedLocationFromPoint(weapon.location, weapon.currAngle, weapon.range), target) != null
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
      MagicRender.battlespace(Global.getSettings().getSprite("graphics/fx/hit_glow.png"),
        loc,
        Vector2f(0f, 0f),
        Vector2f(24f, 24f),
        Vector2f(0f, 0f),
        0f,
        0f,
        Color(100, 250, 100, 120),
        true, 0f, 0f, 0.1f
      )
    }

    fun getColorWithAlphaChange(ori: Color, alphaLevel: Float): Color {
      return Color(ori.red, ori.green, ori.blue, (ori.alpha * alphaLevel).toInt().coerceAtMost(255))
    }

    fun getColorWithAlpha(ori: Color, alphaLevel: Float): Color {
      return Color(ori.red, ori.green, ori.blue, MathUtils.clamp((255f * alphaLevel).toInt(),0,255))
    }

    fun getColorWithChange(ori: Color, alphaLevel: Float): Color {
      return Color((ori.blue * alphaLevel).toInt(), (ori.blue * alphaLevel).toInt(), (ori.blue * alphaLevel).toInt(), (ori.alpha * alphaLevel).toInt())
    }

    fun getPointRotateVector(point: Vector2f?, center: Vector2f, angle: Float): Vector2f {
      val dist = MathUtils.getDistance(point, center)
      var angleNow = VectorUtils.getAngle(center, point)
      angleNow = angleAdd(angleNow, angle)
      return getExtendedLocationFromPoint(center, angleNow, dist)
    }

    fun getPointRotateVector(point: Vector2f?, dist: Float, center: Vector2f, angle: Float): Vector2f {
      var angleNow = VectorUtils.getAngle(center, point)
      angleNow = angleAdd(angleNow, angle)
      return getExtendedLocationFromPoint(center, angleNow, dist)
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
        if (tmp.isFighter || tmp.owner != entity.owner) continue
        distance = MathUtils.getDistance(tmp, entity.location)
        if (distance < closestDistance) {
          closest = tmp
          closestDistance = distance
        }
      }
      return closest
    }

    //angle, length
    fun getAngleAndDist(point: Vector2f, center: Vector2f, centerFacing: Float): Vector2f {
      val x = -VectorUtils.getAngle(center, point) - 90 - centerFacing
      val dist = Vector2f(point.x - center.x, point.y - center.y)
      val y = Math.sqrt((dist.getX() * dist.getX() + dist.getY() * dist.getY()).toDouble()).toFloat()
      return Vector2f(x, y)
    }

    fun getAbsPos(angleAndLength: Vector2f, basePoint: Vector2f): Vector2f {
      val angle = angleAndLength.x
      val length = angleAndLength.y
      val xAxis = FastTrig.cos(Math.toRadians(angle.toDouble())).toFloat() * length
      val yAxis = FastTrig.sin(Math.toRadians(angle.toDouble())).toFloat() * length
      return Vector2f(xAxis + basePoint.x, yAxis + basePoint.y)
    }

    fun getAbsPos(angleAndLength: Vector2f, basePoint: Vector2f, baseAngle: Float): Vector2f {
      val angle = angleAndLength.x
      val length = angleAndLength.y
      val xAxis = FastTrig.cos(Math.toRadians(angle.toDouble())).toFloat() * length
      val yAxis = FastTrig.sin(Math.toRadians(angle.toDouble())).toFloat() * length
      return Vector2f(xAxis + basePoint.x, yAxis + basePoint.y)
    }

    fun firingSmoke(loc: Vector2f, facing: Float, param:FiringSmokeParam, ship: ShipAPI?) {
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

    fun getShipSpecName(spec:String):String{
      return Global.getSettings().getHullSpec(spec).hullName
    }

    fun getHullModName(spec: String):String{
      return Global.getSettings().getHullModSpec(spec).displayName
    }

    /**
     * 加上自身的碰撞半径但是不加上对面的碰撞半径
     * */
    fun checkTargetWithinSystemRange(ship: ShipAPI?, baseRange: Float): Boolean{
      //默认返回false，所以初始为-1f
      val range = ship?.mutableStats?.systemRangeBonus?.computeEffective(baseRange) ?: -1f
      if(ship?.shipTarget != null) {
        if(MathUtils.getDistance(ship.shipTarget.location,ship.location) - ship.collisionRadius< range){
          return true
        }
      }
      return false
    }

    fun getTargetWithinSystemRange(ship: ShipAPI?, baseRange: Float): Float{
      val range = ship?.mutableStats?.systemRangeBonus?.computeEffective(baseRange) ?: 9999999999f
      if(ship?.shipTarget != null) {
        if(MathUtils.getDistance(ship.shipTarget.location,ship.location) - ship.collisionRadius< range){
          return range
        }
      }
      return 9999999999f
    }

    fun txtOfTargetWithinSystemRange(ship: ShipAPI?, baseRange: Float): String{
      val range = ship?.mutableStats?.systemRangeBonus?.computeEffective(baseRange) ?: -9999999999f
      if(ship?.shipTarget != null) {
        if(MathUtils.getDistance(ship.shipTarget.location,ship.location) - ship.collisionRadius< range){
          return ""
        }
      }
      return "Out of Range"
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
        getColorWithAlpha(c, 0.2f *(c.alpha/255f))
      )


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

    fun applyPureArmorDamage(weapon: WeaponAPI?, d: Float, target: ShipAPI?, point: Vector2f?) {
      val engine = Global.getCombatEngine()
      val grid = target?.armorGrid ?: return
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

    fun isDead(ship: ShipAPI) : Boolean{
      if(!ship.isAlive || ship.isHulk || !Global.getCombatEngine().isEntityInPlay(ship)) return true
      return false
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
    const val HULLMOD_BULLET = "     --"
    const val CONFIRM = "Confirm"
    const val CANCEL = "Cancel"
    const val RETURN = "Return"
    const val SELECT = "Select"
  }
}