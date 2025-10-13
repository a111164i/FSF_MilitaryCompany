//by Tartiflette, for the guiding part
//by a111164
package data.scripts.ai

import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.WeightedRandomPicker
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.getNearestFriendCombatShip
import combat.util.aEP_Tool.Util.getRelativeLocationData
import data.scripts.ai.shipsystemai.aEP_DroneBurstAI
import data.scripts.weapons.aEP_BeamRepair.Companion.HULL_REPAIR_THRESHOLD
import data.scripts.weapons.aEP_BeamRepair.Companion.REPAIR_THRESHOLD
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.absoluteValue


class aEP_DroneRepairShipAI(member: FleetMemberAPI?, ship: ShipAPI) : aEP_BaseShipAI(ship, aEP_DroneBurstAI(ship, ship.system)) {

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

  companion object{
    const val ID = "aEP_DroneRepairShipAI"
  }

  override fun advanceImpl(amount: Float) {
    super.advanceImpl(amount)

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
    val pointTracker = IntervalUtil(0.75f,1.25f)
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
          if(!aEP_Tool.isShipTargetable(ally,
              false,
              false,
              true,
              false,
              false)) continue

          //跳过非常规舰船
          if (ally.hullSize != ShipAPI.HullSize.CAPITAL_SHIP
            && ally.hullSize != ShipAPI.HullSize.CRUISER
            && ally.hullSize != ShipAPI.HullSize.DESTROYER
            && ally.hullSize != ShipAPI.HullSize.FRIGATE) continue

          val damagedPercent = calculateDamagedPercent(ally)
          if(damagedPercent  > 0.01f){
            targetPicker.add(ally, damagedPercent)
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
            aEP_Tool.flyToPosition(ship, parentShip.location)
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
            aEP_Tool.moveToPosition(ship,point)
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
          //紧急处理
          if(formation.size < i + 1){
            return
          }
          val loc = formation[i]
          aEP_Tool.moveToAngle(ship, ship.wing.leader.facing)
          aEP_Tool.moveToPosition(ship, loc)
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

        //每帧更新一次自己碰撞点的绝对坐标（如果不更新，显示的是相对坐标）
        ship.visualBounds?.update(ship.location,ship.facing)
        ship.exactBounds?.update(ship.location,ship.facing)

        val randomPointAtCir =MathUtils.getRandomPointOnCircumference(target.location,target.collisionRadius + 100f)
        val hitPoint = CollisionUtils.getCollisionPoint(randomPointAtCir, target.location, target)
        //处于100内，并且成功生成一个随机附着点，切换到吸附模式
        if(hitPoint != null){
          val hitFacing = VectorUtils.getAngle(hitPoint,target.location)
          //不要贴着边缘，略微往外挪一点
          hitPoint.set(aEP_Tool.getExtendedLocationFromPoint(hitPoint,hitFacing,-30f))
          val relPos = getRelativeLocationData(hitPoint, target, false)
          stat = StickAndFire(relPos, target)
          return
        //处于100内，但是因为相位等情况并没有碰撞
        }else{
          ship.giveCommand(ShipCommand.ACCELERATE,null,0)
          return
        }
      }

      aEP_Tool.flyToPosition(ship, target.location)
    }
  }

  inner class StickAndFire(val relPos: Vector2f, val target:ShipAPI ): aEP_MissileAI.Status(){
    val reformTimer = IntervalUtil(4f,4f)

    //获得基于目标速度一定值的极速和机动性加成，防止永远追不上
    //记得以任何形式的退出本模式，需要取消buff
    init {
      ship.mutableStats.maxSpeed.modifyFlat(ID, target.maxSpeed)
      ship.mutableStats.acceleration.modifyFlat(ID, target.maxSpeed * 2f)
      ship.mutableStats.deceleration.modifyFlat(ID, target.maxSpeed * 2f)

      ship.mutableStats.maxTurnRate.modifyFlat(ID, target.maxTurnRate)
      ship.mutableStats.turnAcceleration.modifyFlat(ID, target.turnAcceleration * 2f)
    }

    override fun advance(amount: Float) {
      //如果弹药用完，转入返回模式，最高优先级
      for(w in ship.allWeapons){
        if(w.usesAmmo() && w.ammoTracker.ammoPerSecond <= 0 && w.ammoTracker.ammo <=0){
          stat = ForceReturn()
          cancelBuff()
          return
        }
      }

      //如果目标失效，转入搜索模式，这里并没写中途修好然后重新索敌的判断，而且只要进入黏附就一定会打完弹药触发返回
      //没必要为了百分之1的情况去增加复杂度
      if(!target.isAlive || target.isHulk || !engine.isEntityInPlay(target)){
        stat = Searching()
        cancelBuff()
        return
      }

      //如果脱离200外，重新转入快速接近模式
      val dist = MathUtils.getDistance(ship,target.location)
      if(dist > 200f) {
        stat = Approaching(target)
        cancelBuff()
        return
      }

      //转换相对位置到绝对位置，吸附舰船
      val absPos = aEP_Tool.getAbsoluteLocation(relPos, target, false)
      val absFacing = VectorUtils.getAngle(ship.location, target.location)
      aEP_Tool.moveToPosition(ship, absPos)
      aEP_Tool.moveToAngle(ship, absFacing)

      //定期换位置，如果武器刚在开火就在结束后立刻换位
      reformTimer.advance(amount)
      for(w in ship.allWeapons){
        if(w.isFiring) reformTimer.elapsed = (reformTimer.intervalDuration - 0.1f)
      }
      if(reformTimer.intervalElapsed()){
        val randomPointAtCir =MathUtils.getRandomPointOnCircumference(target.location,target.collisionRadius+100f)
        val hitPoint = CollisionUtils.getCollisionPoint(randomPointAtCir, target.location, target)

        if(hitPoint != null){
          val hitFacing = VectorUtils.getAngle(hitPoint,target.location)
          //不要贴着边缘，略微往外挪一点
          hitPoint.set(aEP_Tool.getExtendedLocationFromPoint(hitPoint,hitFacing,-30f))
          val relPos = getRelativeLocationData(hitPoint, target, false)
          //刷新同一模式不需要取消buff
          stat = StickAndFire(relPos, target)
          return
        }else{ //处于100内，但是因为相位等情况并没有碰撞

        }
      }


      //开火检测，停稳了，对准了
      val distToAbsPos = MathUtils.getDistance(ship.location,absPos)
      if(distToAbsPos < (50f + target.maxSpeed * 0.25f)
        && MathUtils.getShortestRotation(ship.facing, absFacing).absoluteValue < 10f
        && target.collisionClass != CollisionClass.NONE){
        ship.giveCommand(ShipCommand.SELECT_GROUP,null,0)
        ship.giveCommand(ShipCommand.FIRE,target.location,0)
      }

    }


    fun cancelBuff(){
      ship.mutableStats.maxSpeed.unmodify(aEP_DroneSupplyShipAI.ID)
      ship.mutableStats.acceleration.unmodify(aEP_DroneSupplyShipAI.ID)
      ship.mutableStats.deceleration.unmodify(aEP_DroneSupplyShipAI.ID)

      ship.mutableStats.maxTurnRate.unmodify(aEP_DroneSupplyShipAI.ID)
      ship.mutableStats.turnAcceleration.unmodify(aEP_DroneSupplyShipAI.ID)
    }
  }

  fun calculateDamagedPercent(target:ShipAPI): Float{

    //找到装甲百分比
    val xSize = target.armorGrid.grid.size
    val ySize = target.armorGrid.grid[0].size
    val cellMaxArmor = target.armorGrid.maxArmorInCell

    //计算百分之50以下部分的装甲的损伤百分比
    var totalDamageTaken = 0f
    for (a in 0 until xSize) {
      for (b in 0 until ySize) {
        val armorInCell = target.armorGrid.getArmorValue(a,b)
        if(armorInCell < cellMaxArmor * REPAIR_THRESHOLD) {
          totalDamageTaken += (cellMaxArmor * REPAIR_THRESHOLD - armorInCell)
        }
      }
    }
    val totalMaxArmor = cellMaxArmor * xSize * ySize * REPAIR_THRESHOLD + 0.1f
    var damagedPercent = totalDamageTaken/ (totalMaxArmor)
    damagedPercent = (damagedPercent * damagedPercent)

    //计算结构值损失百分比
    var hullDamageTaken = 0f
    if(target.hitpoints < target.maxHitpoints * HULL_REPAIR_THRESHOLD){
      hullDamageTaken = target.maxHitpoints * HULL_REPAIR_THRESHOLD - target.hitpoints
    }
    val maxHullDamageTaken = target.maxHitpoints * HULL_REPAIR_THRESHOLD + 0.1f
    var hullDamagedPercent = hullDamageTaken/ maxHullDamageTaken
    hullDamagedPercent = (hullDamagedPercent * hullDamagedPercent)

    return hullDamagedPercent + damagedPercent
  }

  fun genBoxFormation(wing:FighterWingAPI, boxSize:Float, vectors: MutableList<Vector2f> ): MutableList<Vector2f>{
    vectors.clear()
    val n = wing.wingMembers.size
    var m = (n/4 + 1)
    if(n%4 > 0) m+=1
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


}