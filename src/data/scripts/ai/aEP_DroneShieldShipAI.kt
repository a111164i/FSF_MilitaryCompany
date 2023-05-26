//by a111164
package data.scripts.ai

import com.fs.starfarer.api.Global
import combat.util.aEP_Tool.Util.getNearestFriendCombatShip
import combat.util.aEP_Tool.Util.setToPosition
import combat.util.aEP_Tool.Util.moveToAngle
import combat.util.aEP_Tool.Util.returnToParent
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAIPlugin
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.combat.ShipAIConfig
import com.fs.starfarer.api.combat.CombatEngineAPI
import org.lwjgl.util.vector.Vector2f
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.findNearestFriendyShip
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.isDead
import data.scripts.ai.aEP_DroneShieldShipAI
import org.lazywizard.lazylib.FastTrig
import org.lazywizard.lazylib.VectorUtils
import java.util.*

class aEP_DroneShieldShipAI(member: FleetMemberAPI, ship: ShipAPI) : aEP_BaseShipAI(ship) {

  companion object {
    private const val DRONE_WIDTH_MULT = 0.9f // 1 means by default no gap between, 2 means gap as big as 1 drone
    private const val FAR_FROM_PARENT = 30f // 30su far from parent's collisionRadius
    private const val KEY2 = "aEP_AroundDroneList"
    private const val KEY3 = "aEP_MyTarget"
  }


  private var shouldDissipate = false


  init {
    if(ship.wing?.sourceShip != null){
      stat = ProtectParent(ship.wing.sourceShip)
    }else{
      val nearest = findNearestFriendyShip(ship)
      nearest?.run { stat = ProtectParent(nearest) }?.run { stat = SelfExplode()}
    }
  }


  override fun advanceImpl(amount: Float) {
    super.advanceImpl(amount)

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
    }

  }

  inner class ProtectParent(val toProtect: ShipAPI): aEP_MissileAI.Status(){
    var droneWidthInAngle = 5f

    init {
      droneWidthInAngle = (FastTrig.atan2(ship.collisionRadius.toDouble(),
        (toProtect.collisionRadius + FAR_FROM_PARENT).toDouble())).toFloat()//得到是rad

      droneWidthInAngle *= 57.296f // 把rad换成degree

      droneWidthInAngle *= (DRONE_WIDTH_MULT*2f)  //半个宽度变成整个宽度
    }

    override fun advance(amount: Float) {
      //母舰挂了就找最近的队友
      if(isDead(toProtect)){
        stat = ProtectParent(findNearestFriendyShip(ship)?:return)
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

      val aimPoint = toProtect.shipTarget?.location?: toProtect.mouseTarget
      val aimMidAngle = VectorUtils.getFacing(VectorUtils.getDirectionalVector(toProtect.location, aimPoint))
      var aimAngle = aimMidAngle
      //找到第一架无人机的角度，左起
      if(totalNum > 1){
        aimAngle -= (droneWidthInAngle * totalNum)/2f
      }

      aimAngle += droneWidthInAngle*(0.5f + currNum)
      val toLocation = getExtendedLocationFromPoint(toProtect.location, aimAngle, toProtect.collisionRadius+ FAR_FROM_PARENT)
      setToPosition(ship, toLocation)
      moveToAngle(ship, aimAngle)


      //shield check
      if (ship.fluxLevel > 0.9) {
        shouldDissipate = true
      }
      if (ship.fluxLevel <= 0.1) {
        shouldDissipate = false
      }
      if (!shouldDissipate) {
        ship.shield.toggleOn()
      } else {
        ship.shield.toggleOff()
      }

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

}