//by a111164
package data.scripts.ai

import data.scripts.utils.aEP_Tool.Util.moveToPosition
import data.scripts.utils.aEP_Tool.Util.moveToAngle
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.util.WeightedRandomPicker
import org.lwjgl.util.vector.Vector2f
import data.scripts.utils.aEP_Tool.Util.findNearestFriendyShip
import data.scripts.utils.aEP_Tool.Util.getExtendedLocationFromPoint
import data.scripts.utils.aEP_Tool.Util.isDead
import data.scripts.hullmods.aEP_ProjectileDenialShield.Companion.keepExplosionProtectListenerToParent
import org.lazywizard.lazylib.FastTrig
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import java.util.*
import kotlin.math.pow

class aEP_DroneShieldShipAI(member: FleetMemberAPI?, ship: ShipAPI) : aEP_BaseShipAI(ship) {

  companion object {
    private const val DRONE_WIDTH_MULT = 0.9f // 1 means by default no gap between, 2 means gap as big as 1 drone
    private const val DRONE_COLLISION_RAD= 35f //有多个不同的drone会用本ai，设置一个通用的

    const val RETHINK_INTERVAL = 20f //最低5秒，最长多少秒重新搜索一次目标
    const val FAR_FROM_PARENT = 45f // 30su far from parent's collisionRadius
    private const val KEY2 = "aEP_AroundDroneList"
    private const val KEY3 = "aEP_MyTarget"

    fun getMultiple(size :ShipAPI.HullSize): Float{
      if(size == ShipAPI.HullSize.CAPITAL_SHIP)
        return 1f
      else if (size == ShipAPI.HullSize.CRUISER)
        return 1f
      else if (size == ShipAPI.HullSize.DESTROYER)
        return 1f
      else if (size == ShipAPI.HullSize.FRIGATE)
        return 1f
      return 0f
    }

    fun getWeight(s : ShipAPI): Float{
      val multiple = getMultiple(s.hullSize)
      //舰体级别系数为0的直接跳过
      if(multiple <= 0) return 0f
      var weight = 0f
      //准备好数据
      val hullLevel = s.hullLevel
      val hardPercent = MathUtils.clamp(s.fluxTracker.hardFlux/s.fluxTracker.currFlux,0f,1f)
      val fluxLevel = s.fluxLevel


      //对于有盾船，幅能高于这个比例才视为有危险
      if(s.shield != null){
        val threshold = 0.25f
        val softFluxLevelWeight = 75f
        val fluxLevelWeight = 200f

        if(fluxLevel >threshold) {
          val softLevel = (s.fluxTracker.currFlux - s.fluxTracker.hardFlux) / (s.fluxTracker.maxFlux + 1f)
          val hardLevel = s.fluxTracker.hardFlux / (s.fluxTracker.maxFlux + 1f)
          weight += ((hardLevel - threshold) / (1f - threshold)).pow(2) * fluxLevelWeight
          weight += ((softLevel - threshold) / (1f - threshold)).pow(2) * softFluxLevelWeight
        }else{
          weight -= 200f
        }
      }else{ //对于无盾船，以结构为阈值
        val hullLevelWeight = 300f

        if(hullLevel >= 0.95f){
          weight -= 200f
        }else{
          weight += (1f - hullLevel).pow(2) * hullLevelWeight
        }
      }

      //Only set for phase ships using the default phase cloak AI.
      val inDpsDangerFlagWeight = 75f
      if(s.shipAI?.aiFlags?.hasFlag(ShipwideAIFlags.AIFlags.IN_CRITICAL_DPS_DANGER) == true){
        weight += inDpsDangerFlagWeight
      }

      val needsHelpFlagWeight = 100f
      if( s.shipAI?.aiFlags?.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP) == true){
        weight += needsHelpFlagWeight
      }

      val hasIncomingDangerFlagWeight = 75f
      if( s.shipAI?.aiFlags?.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE) == true){
        weight += hasIncomingDangerFlagWeight
      }

      val overloadWeight = 150f
      if(s.fluxTracker.isOverloaded){
        weight += overloadWeight
      }

      //硬幅能比例只放大，不加减
      weight *= (0.4f + 0.6f * hardPercent)
      //结构值只放大，不加减
      val hullLost = 1f - hullLevel
      if(hullLost > 0.4f || s.hitpoints < 5000f){
        weight *= 2f
      }else{
        weight *= 1f + hullLost
      }


      return weight * MathUtils.getRandomNumberInRange(0.75f,1.25f)
    }
  }


  init {
    if(ship.wing?.sourceShip != null){
      stat = ProtectParent(ship.wing.sourceShip)
    }else{
      val nearest = findNearestFriendyShip(ship)
      nearest?.run {
        stat = ProtectParent(nearest)
      }?:run {
        stat = SelfExplode()
      }
    }
  }

  override fun advanceImpl(amount: Float) {

    if(stat is ProtectParent){
      //把自己的目标存入自己的customMap，用于检测需不需要踢掉
      //一定用setCustomData()这个方法才能初始化原本为空的customMap
      val now = stat as ProtectParent
      if(ship.customData[KEY3] != now){
        ship.setCustomData(KEY3,now)
      }
      ship.setCustomData(KEY3,now.toProtect)
    }else{
      if(ship.customData[KEY3] != null){
        ship.setCustomData(KEY3,null)
      }
      shieldFacing = null
    }

  }

  inner class ProtectParent(val toProtect: ShipAPI): aEP_MissileAI.Status(){
    var droneWidthInAngle = 5f
    //是否检测因为距离过远而脱离，默认不检测，用于远距离强制无人机改变目标
    var forceTag = false
    //只有实际进入保护圈了才会计算时间，中间飞行不算
    var timeElapsed = 0f
    var timeShouldRethink = 0f

    init {
      droneWidthInAngle = (FastTrig.atan2(DRONE_COLLISION_RAD.toDouble(),
        (toProtect.collisionRadius + FAR_FROM_PARENT).toDouble())).toFloat()//得到是rad

      droneWidthInAngle *= 57.296f // 把rad换成degree

      droneWidthInAngle *= (DRONE_WIDTH_MULT*2f)  //半个宽度变成整个宽度
      timeShouldRethink = MathUtils.getRandomNumberInRange(5f,RETHINK_INTERVAL)
    }

    override fun advance(amount: Float) {
      //母舰挂了就找最近的队友
      if(isDead(toProtect) || (timeElapsed > timeShouldRethink && !forceTag)){
        timeElapsed = 0f
        forceCircumstanceEvaluation()
        return
      }

      //这个部分管加不管踢，在findNum()里面
      //如果目标之前没有支援机，为目标创建一个支援机位置表
      var dronePosition = LinkedList<ShipAPI>()
      if(!toProtect.customData.containsKey(KEY2)){
        dronePosition.add(ship)
        toProtect.setCustomData(KEY2, dronePosition)
      //如果目标目前有支援机，每帧读取他的位置表
      //如果自己目前不在这个位置表中，把自己加入
      }else{
        dronePosition = toProtect.customData[KEY2] as LinkedList<ShipAPI>
        if(!dronePosition.contains(ship)){
          dronePosition.add(ship)
        }
      }

      //找的自己是位置表里面的第几号，同时踢出位置表里面已经离开的成员，在findNum()里面完成
      val currNum = findNum(toProtect,dronePosition)
      val totalNum = dronePosition.size

      var aimPoint:Vector2f? = toProtect.shipTarget?.location
      if(aimPoint == null && toProtect.aiFlags != null && toProtect.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.MANEUVER_TARGET)){
        if(toProtect.aiFlags.getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET) is ShipAPI){
          aimPoint = Vector2f((toProtect.aiFlags.getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET) as ShipAPI).location)
        }
      }
      if(aimPoint == null){
        aimPoint = toProtect.mouseTarget
      }
      aimPoint = aimPoint?: getExtendedLocationFromPoint(toProtect.location,toProtect.facing, 100f+toProtect.collisionRadius)

      val aimMidAngle = VectorUtils.getFacing(VectorUtils.getDirectionalVector(toProtect.location, aimPoint))
      var aimAngle = aimMidAngle
      //找到第一架无人机的角度，左起
      if(totalNum > 1){
        aimAngle -= (droneWidthInAngle * totalNum)/2f
      }

      aimAngle += droneWidthInAngle*(0.5f + currNum)
      val toLocation = getExtendedLocationFromPoint(toProtect.location, aimAngle, toProtect.collisionRadius+ FAR_FROM_PARENT)
      //只有靠近了才会开始记时
      if(MathUtils.getDistance(toLocation, ship.location) <200f){
        timeElapsed += amount
      }
      moveToPosition(ship, toLocation)
      moveToAngle(ship, aimAngle)

      //shipsystem check
      if(MathUtils.getDistance(ship.location, toLocation) > 800f){
        if(ship.system != null && ship.system.id.equals("aEP_DroneTeleport")){
          ship.mouseTarget.set(toLocation.x, toLocation.y)
          ship.useSystem()
        }
      }

      //shield check
      // 硬幅能散完就开盾
      if (ship.hardFluxLevel <= 0f) {
        shieldFacing = ship.facing
      }
      //被打了超过50就关
      if (ship.hardFluxLevel > 0.7f){
        shieldFacing = null
      }

      //离目标太远不开盾
      if (MathUtils.getDistanceSquared(ship, toProtect) > (toProtect.collisionRadius + 500f).pow(2f)) {
        shieldFacing = null
      }

      keepExplosionProtectListenerToParent(ship,toProtect)

    }
  }

  private fun findNum(target: CombatEntityAPI, dronePosition: LinkedList<ShipAPI>): Int {
    //清理list中失效的战机，战机会每帧把当前的母舰更新到customData的id中
    //查战机customData的id里面的母舰，是不是当前需要findPos的母舰
    //如果不是，踢掉
    val toRemove = ArrayList<ShipAPI>()
    for(drone in dronePosition){
      if(isDead(drone)) toRemove.add(drone)
      val droneTarget = drone.customData[KEY3]
      if(droneTarget != target) toRemove.add(drone)
    }
    dronePosition.removeAll(toRemove)

    //找到自己的战机编号
    var i = 0
    for(f in dronePosition){
      if(ship == f) break
      i += 1
    }


    return i
  }

  override fun forceCircumstanceEvaluation() {

    //如果已经在保护，并且被设置了强制跟随，就不再重新搜寻目标
    if(stat is ProtectParent && (stat as ProtectParent).forceTag){
      val s =  (stat as ProtectParent)
      if(isDead(s.toProtect)){
        stat = SelfExplode()
      }else{
        return
      }
    }


    //战术系统刷出来的和没有出击距离的联队，选择最近的友方
    if((ship?.wing?.range)?:0f <= 0f){
      if(ship.wing == null || ship.wing.sourceShip == null){
        val nearest = findNearestFriendyShip(ship)
        nearest?.run {
          stat = ProtectParent(nearest)
        }?:run {
          stat = SelfExplode()
        }
      }else{
        if(!isDead(ship.wing.sourceShip)){
          stat = ProtectParent(ship.wing.sourceShip)
        }else{
          stat = SelfExplode()
        }
      }

    } else { // 如果有出击距离，使用战术系统的逻辑
      val targetWeightPicker = WeightedRandomPicker<ShipAPI>()
      val wingRange = ship.wing.range
      val motherShip = ship.wing.sourceShip?:ship
      //遍历友军，根据危险程度加入权重选择器
      for(s in AIUtils.getNearbyAllies(motherShip, wingRange + ship.collisionRadius + 500f)){
        //危险阈值
        val threshold = 0f
        var weight = getWeight(s)
        if(weight > threshold){
          targetWeightPicker.add(s,weight*getMultiple(s.hullSize))
        }
      }
      targetWeightPicker.add(ship,getWeight(ship)*getMultiple(ship.hullSize))
      val target = targetWeightPicker.pick()
      target?:return
      stat = ProtectParent(target)
    }



  }

}