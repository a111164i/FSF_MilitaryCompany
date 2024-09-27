//by a111164
package data.scripts.ai

import combat.util.aEP_Tool.Util.moveToPosition
import combat.util.aEP_Tool.Util.moveToAngle
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import org.lwjgl.util.vector.Vector2f
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.findNearestFriendyShip
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.isDead
import data.scripts.hullmods.aEP_ProjectileDenialShield.Companion.keepExplosionProtectListenerToParent
import org.lazywizard.lazylib.FastTrig
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import java.util.*
import kotlin.math.pow

class aEP_DroneShieldShipAI(member: FleetMemberAPI?, ship: ShipAPI) : aEP_BaseShipAI(ship) {

  companion object {
    private const val DRONE_WIDTH_MULT = 0.9f // 1 means by default no gap between, 2 means gap as big as 1 drone
    private const val DRONE_COLLISION_RAD= 35f //有多个不同的drone会用本ai，设置一个通用的

    const val FAR_FROM_PARENT = 45f // 30su far from parent's collisionRadius
    private const val KEY2 = "aEP_AroundDroneList"
    private const val KEY3 = "aEP_MyTarget"
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

    init {
      droneWidthInAngle = (FastTrig.atan2(DRONE_COLLISION_RAD.toDouble(),
        (toProtect.collisionRadius + FAR_FROM_PARENT).toDouble())).toFloat()//得到是rad

      droneWidthInAngle *= 57.296f // 把rad换成degree

      droneWidthInAngle *= (DRONE_WIDTH_MULT*2f)  //半个宽度变成整个宽度
    }

    override fun advance(amount: Float) {
      //母舰挂了就找最近的队友
      if(isDead(toProtect)){
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
      aimPoint = aimPoint?:aEP_Tool.getExtendedLocationFromPoint(toProtect.location,toProtect.facing, 100f+toProtect.collisionRadius)

      val aimMidAngle = VectorUtils.getFacing(VectorUtils.getDirectionalVector(toProtect.location, aimPoint))
      var aimAngle = aimMidAngle
      //找到第一架无人机的角度，左起
      if(totalNum > 1){
        aimAngle -= (droneWidthInAngle * totalNum)/2f
      }

      aimAngle += droneWidthInAngle*(0.5f + currNum)
      val toLocation = getExtendedLocationFromPoint(toProtect.location, aimAngle, toProtect.collisionRadius+ FAR_FROM_PARENT)
      moveToPosition(ship, toLocation)
      moveToAngle(ship, aimAngle)


      //shield check
      // 零幅能开盾，被打满了就关盾直到耗散干净
      if (ship.fluxLevel <= 0.01f) {
        shieldFacing = ship.facing
      }else{
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
      if(aEP_Tool.isDead(s.toProtect)){
        stat = SelfExplode()
      }else{
        return
      }
    }

    val nearest = findNearestFriendyShip(ship)
    nearest?.run {
      stat = ProtectParent(nearest)
    }?:run {
      stat = SelfExplode()
    }
  }
}