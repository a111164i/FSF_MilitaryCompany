//by Tartiflette, for the guiding part
//by a111164
package data.scripts.ai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.ListMap
import com.fs.starfarer.api.util.WeightedRandomPicker
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.getNearestFriendCombatShip
import combat.util.aEP_Tool.Util.getRelativeLocationData
import combat.util.aEP_Tool.Util.isDead
import data.scripts.ai.shipsystemai.aEP_DroneBurstAI
import data.scripts.weapons.aEP_RepairBeam.Companion.HULL_REPAIR_THRESHOLD
import data.scripts.weapons.aEP_RepairBeam.Companion.REPAIR_THRESHOLD
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.FastTrig
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector
import org.lwjgl.util.vector.Vector2f
import java.lang.Math.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue
import kotlin.math.cos


class aEP_DroneSupplyShipAI(member: FleetMemberAPI, ship: ShipAPI) : aEP_BaseShipAI(ship, aEP_DroneBurstAI()) {

  companion object{
    const val ID = "aEP_DroneSupplyShipAI"
  }

  private var parentShip: ShipAPI? = null

  init {
    //get parent ship
    parentShip = if (ship.wing.sourceShip == null) {
      getNearestFriendCombatShip(ship)
    } else {
      ship.wing.sourceShip
    }
    stat = Searching()

  }


  override fun advanceImpl(amount: Float) {
    super.advanceImpl(amount)
    if(stat is StickAndFire){
      //把自己的目标存入自己的customMap，用于检测需不需要踢掉
      //一定用setCustomData()这个方法才能初始化原本为空的customMap
      val sa = stat as StickAndFire
      if(ship.customData[ID] != sa.target){
        ship.setCustomData(ID,sa.target)
      }
    }else{
      if(ship.customData[ID] != null){
        ship.setCustomData(ID,null)
      }
    }

    //同步系统目标，使用系统ai
    systemTarget = null
    if(stat is Approaching){
      val sa = stat as Approaching
      systemTarget = sa.target
    }

    if(stat is Searching && parentShip != null){
      val sa = stat as Searching
      systemTarget = parentShip
    }

    if(stat is ForceReturn && parentShip != null){
      val sa = stat as ForceReturn
      systemTarget = parentShip
    }

  }

  inner class Searching(): aEP_MissileAI.Status(){
    val searchTracker = IntervalUtil(0.4f,0.6f)
    val pointTracker = IntervalUtil(1f,1f)
    var point = ship.location?: Vector2f(0f,0f)
    override fun advance(amount: Float) {
      //如果弹药用完，转入返回模式，最高优先级
      for(w in ship.allWeapons){
        if(w.usesAmmo() && w.ammoTracker.ammoPerSecond <= 0 && w.ammoTracker.ammo <=0){
          stat = ForceReturn()
          return
        }
      }

      //隔一段时间做一次搜索
      searchTracker.advance(amount)
      if(searchTracker.intervalElapsed()){
        val aroundShips = AIUtils.getNearbyAllies(ship, ship.wing.range)
        val targetPicker = WeightedRandomPicker<ShipAPI>()
        for(ally in aroundShips){
          //跳过非常规舰船
          if (ally.hullSize != ShipAPI.HullSize.CAPITAL_SHIP
            && ally.hullSize != ShipAPI.HullSize.CRUISER
            && ally.hullSize != ShipAPI.HullSize.DESTROYER
            && ally.hullSize != ShipAPI.HullSize.FRIGATE) continue

          val factor = calculateFactor(ally)
          if(factor  > 100f){
            targetPicker.add(ally, factor)
          }
        }

        //从picker里面抽取目标，抽中了就进入粗略接近模式
        val target:ShipAPI? = targetPicker.pick()
        if(target != null){
          stat = Approaching(target)
          return
        }
      }

      //如果有母舰就执行环绕，如果没有母舰的友军当成母舰，还是找不到就自爆
      if(parentShip != null){
        //如果有母舰但是母舰寄了，自爆
        val parentShip = parentShip as ShipAPI
        if(aEP_Tool.isDead(parentShip)){
          stat = SelfExplode()
          return
        }

        //执行列阵，队长计算队列并且随机晃动
        val dist = MathUtils.getDistance(ship, parentShip)
        if(ship.isWingLeader){
          if(dist > 200){
            aEP_Tool.moveToPosition(ship, parentShip.location)
          }else{
            pointTracker.advance(amount)
            if(pointTracker.intervalElapsed()){
              point = aEP_Tool.getExtendedLocationFromPoint(
                parentShip.location,
                parentShip.facing + MathUtils.getRandomNumberInRange(-45f,45f),
                -parentShip.collisionRadius)
              point.set(MathUtils.getRandomPointInCircle(point,100f))
            }
            aEP_Tool.moveToAngle(ship, parentShip.facing)
            aEP_Tool.setToPosition(ship,point)
          }
        //僚机跟随阵型
        }else{
          //生成阵型
          var formation = ArrayList<Vector2f>()
          genBoxFormation(ship.wing,50f,formation)
          //找到自己的战机编号
          var i = 0
          for(f in ship.wing.wingMembers){
            if(f == ship) break
            i += 1
          }
          val loc = formation[i]
          aEP_Tool.moveToAngle(ship, ship.wing.leader.facing)
          aEP_Tool.setToPosition(ship, loc)
        }


      }else{
        parentShip = aEP_Tool.getNearestFriendCombatShip(ship)
        if(parentShip == null){
          stat = SelfExplode()
          return
        }
      }

    }
  }

  inner class Approaching(val target: ShipAPI): aEP_MissileAI.Status(){
    override fun advance(amount: Float) {
      //如果弹药用完，转入返回模式，最高优先级
      for(w in ship.allWeapons){
        w ?: continue
        if(w.usesAmmo() && w.ammoTracker.ammoPerSecond <= 0 && w.ammoTracker.ammo <=0){
          stat = ForceReturn()
          return
        }
      }

      //如果目标失效，转入搜索模式
      if(aEP_Tool.isDead(target)){
        stat = Searching()
        return
      }

      //如果成功进入100内
      val dist = MathUtils.getDistance(ship,target)
      if(dist < 100f){
        stat = StickAndFire(target)
      }

      aEP_Tool.moveToPosition(ship, target.location)
    }
  }

  inner class StickAndFire(val target:ShipAPI ): aEP_MissileAI.Status(){
    val pos = Vector2f(0f,0f)

    init {
      //获得基于目标速度一定值的极速和机动性加成，防止永远追不上
      ship.mutableStats.maxSpeed.modifyFlat(ID, target.maxSpeed)
      ship.mutableStats.acceleration.modifyFlat(ID, target.maxSpeed * 2f)
      ship.mutableStats.deceleration.modifyFlat(ID, target.maxSpeed * 2f)

      ship.mutableStats.maxTurnRate.modifyFlat(ID, target.maxTurnRate)
      ship.mutableStats.turnAcceleration.modifyFlat(ID, target.turnAcceleration * 2f)
    }

    override fun advance(amount: Float) {
      //如果幅能太高，转入返回模式，最高优先级
      if(ship.fluxLevel > 0.9f){
        stat = ForceReturn()
        ship.mutableStats.maxSpeed.unmodify(ID)
        ship.mutableStats.acceleration.unmodify(ID)
        ship.mutableStats.deceleration.unmodify(ID)

        ship.mutableStats.maxTurnRate.unmodify(ID)
        ship.mutableStats.turnAcceleration.unmodify(ID)
        return
      }

      //如果目标失效，转入搜索模式，
      if(aEP_Tool.isDead(target) || target.fluxLevel <= 0f){
        stat = Searching()
        ship.mutableStats.maxSpeed.unmodify(ID)
        ship.mutableStats.acceleration.unmodify(ID)
        ship.mutableStats.deceleration.unmodify(ID)

        ship.mutableStats.maxTurnRate.unmodify(ID)
        ship.mutableStats.turnAcceleration.unmodify(ID)
        return
      }

      //如果脱离200外，重新转入快速接近模式
      val dist = MathUtils.getDistance(ship,target)
      if(dist > 200f) {
        stat = Approaching(target)
        ship.mutableStats.maxSpeed.unmodify(ID)
        ship.mutableStats.acceleration.unmodify(ID)
        ship.mutableStats.deceleration.unmodify(ID)

        ship.mutableStats.maxTurnRate.unmodify(ID)
        ship.mutableStats.turnAcceleration.unmodify(ID)
        return
      }

      //获取支援机位置表
      //这个部分管加不管踢，踢的部分在findPos()里面
      //如果目标之前没有支援机，为目标创建一个支援机位置表
      var dronePosition = LinkedList<ShipAPI>()
      if(!target.customData.containsKey(ID)){
        dronePosition.add(ship)
        target.setCustomData(ID,dronePosition)
      //如果目标目前有支援机，每帧读取他的位置表
      //如果自己目前不在这个位置表中，把自己加入
      }else{
        dronePosition = target.customData[ID] as LinkedList<ShipAPI>
        if(!dronePosition.contains(ship)){
          dronePosition.add(ship)
        }
      }

      //转换相对位置到绝对位置，吸附舰船
      //在findPos()时，会先从位置表里面踢掉已经离开的成员
      pos.set(findPos(target, dronePosition) )
      aEP_Tool.setToPosition(ship, pos)
      aEP_Tool.moveToAngle(ship, target.facing)

      //开火检测，停稳了，对准了
      val distToAbsPos = MathUtils.getDistance(ship.location, pos)
      if(distToAbsPos < 50f + target.maxSpeed * 0.25f ){
        ship.giveCommand(ShipCommand.SELECT_GROUP,null,0)
        ship.giveCommand(ShipCommand.FIRE,target.location,0)
      }

    }
  }

  fun calculateFactor(target:ShipAPI): Float{
    var fluxLevelFactor = 0f
    if(target.fluxLevel < 0.5f){
      fluxLevelFactor = (target.fluxLevel - 0.5f) * 2f
      fluxLevelFactor *= 2000f
    }

    var currFluxFactor = target.currFlux * 0.8f

    return fluxLevelFactor + currFluxFactor
  }

  fun genBoxFormation(wing:FighterWingAPI, boxSize:Float, vectors: MutableList<Vector2f> ): MutableList<Vector2f>{
    vectors.clear()
    val n = wing.wingMembers.size
    val m = (n/4 + 1).coerceAtLeast(2)
    val size = (boxSize * m)
    val center = wing.leader.location

    // calculate the distance between each vector
    val step = size / (m - 1)
    val angle = wing.leader.facing?: 0f

    // add vectors along the top edge
    for (i in 0 until m) {
      val x = i * step
      val p = Vector2f(center.x - x,  center.y)
      VectorUtils.rotateAroundPivot(p,center,angle-45)
      vectors.add(p)
    }

    // add vectors along the right edge
    for (i in 1 until m) {
      val y = i * step
      val p = Vector2f(center.x - size, center.y - y)
      VectorUtils.rotateAroundPivot(p,center,angle-45)
      vectors.add(p)
    }

    // add vectors along the bottom edge
    for (i in 1 until m) {
      val x = size - i * step
      val p = Vector2f(center.x - x, center.y - size)
      VectorUtils.rotateAroundPivot(p,center,angle-45)
      vectors.add(p)
    }

    // add vectors along the left edge
    for (i in 1 until m - 1) {
      val y = size - i * step
      val p = Vector2f(center.x, center.y - y)
      VectorUtils.rotateAroundPivot(p,center,angle-45)
      vectors.add(p)
    }

    return vectors
  }

  private fun findPos(target: CombatEntityAPI, dronePosition: LinkedList<ShipAPI>): Vector2f {
    val pos = Vector2f(0f,0f)

    //清理list中失效的战机，战机会每帧把当前的母舰更新到customData的id中
    //查战机customData的id里面的母舰，是不是当前需要findPos的母舰
    //如果不是，踢掉
    val toRemove = ArrayList<ShipAPI>()
    for(drone in dronePosition){
      if(isDead(drone)) toRemove.add(drone)
      val droneTarget = drone.customData[ID]
      if(droneTarget != target) toRemove.add(drone)
    }
    dronePosition.removeAll(toRemove)

    //找到自己的战机编号
    var i = 0
    for(f in dronePosition){
      if(ship == f) break
      i += 1
    }

    val totalNum = dronePosition.size
    val angleAdd = (120f/totalNum).coerceAtMost(120f)
    var startAngle = target.facing -180f - 60f
    pos.set(aEP_Tool.getExtendedLocationFromPoint(
      target.location,
      startAngle+angleAdd*i,
      target.collisionRadius+100f ))
    //aEP_Tool.addDebugPoint(pos)

    return pos
  }

}