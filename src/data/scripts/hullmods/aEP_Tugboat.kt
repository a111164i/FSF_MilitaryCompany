package data.scripts.hullmods

import com.fs.starfarer.api.campaign.BuffManagerAPI.Buff
import com.fs.starfarer.api.fleet.FleetMemberAPI
import java.util.ArrayList

class aEP_Tugboat: aEP_BaseHullMod() {

  companion object{
    const val ID = "aEP_Tugboat"
  }

  var lastTime = 0f


  override fun advanceInCampaign(member: FleetMemberAPI?, amount: Float) {
    if(member?.fleetData?.fleet == null) return
    val allMemberList = member.fleetData.combatReadyMembersListCopy
    val toRemoveList = ArrayList<FleetMemberAPI>()
    val givenId = ID+"_"+member.id

    //如果自己处于封存状态，无法拖人
    if(member.isMothballed){
      return
    }

    //从全舰队列表中去掉拖船，得到待拖对象
    for(m in allMemberList){
      //如果本艘拖船已经给全舰队中任何一艘舰船上过buff，立刻return
      if(m.buffManager.getBuff(givenId) != null){
        //aEP_Tool.addDebugLog(member.shipName)
        return
      }
      //移除掉本身就有这个船插的船
      if(m.variant.hasHullMod(ID)){
        toRemoveList.add(m)
      }
    }
    allMemberList.removeAll(toRemoveList)

    //Comparator返回负数即不需要交换，1在2前面。返回整数相反。
    //这里，如果1大于2，返回整数需要交换，把大的放在后面
    val comparator = Comparator<FleetMemberAPI>(function = fun (m1:FleetMemberAPI,m2:FleetMemberAPI) : Int{
      if(m1.stats.maxBurnLevel.modifiedValue > m2.stats.maxBurnLevel.modifiedValue){
        return 1
      }
      return -1
    })
    //排序，速度小到大
    allMemberList.sortWith(comparator)

    //船队中没有船就直接return
    if(allMemberList.size <= 0){
      return
    }

    //用这艘的拖船去拖当前最慢的成员
    //因为拖船之间不能相互拖，所以最慢的拖船决定了舰队的速度上限
    for(otherShip in allMemberList){
      //aEP_Tool.addDebugLog(otherShip.hullId+" + "+otherShip.stats.maxBurnLevel.modifiedValue)
      //在buff的构造函数里面要修改，否则advance就要等到下一帧了
      if(otherShip.buffManager.getBuff(givenId) == null){
        otherShip.buffManager.addBuff(TowCableBuff(givenId, member ,otherShip))
      }
      return
    }
    //Global.getLogger(this.javaClass).info(test.toString())
  }

  class TowCableBuff( ) : Buff {
    private var days = 0f
    private val maxDur = 0.2f
    private var shouldEnd = false
    private var buffId = ""
    lateinit var tug:FleetMemberAPI
    lateinit var slowest:FleetMemberAPI


    constructor(buffId: String,tug: FleetMemberAPI,slowest: FleetMemberAPI):this(){
      this.buffId = buffId
      this.tug = tug
      this.slowest = slowest
      if(tug.stats.maxBurnLevel.modifiedValue > slowest.stats.maxBurnLevel.modifiedValue){
        slowest.stats.maxBurnLevel.modifyFlat(buffId, tug.stats.maxBurnLevel.modifiedValue- slowest.stats.maxBurnLevel.modifiedValue)
      }

    }

    override fun isExpired(): Boolean {
      if(shouldEnd){
        slowest.stats.maxBurnLevel.unmodify(buffId)
        //aEP_Tool.addDebugLog(slowest.hullId+" end")
      }
      return shouldEnd
    }

    override fun getId(): String {
      return buffId
    }

    //这个buff是施加在慢船上的，这里的member就是slowest
    //当任何一个新buff加进去，所有的老buff都会执行一次apply
    override fun apply(member: FleetMemberAPI) {
      if(tug.stats.maxBurnLevel.modifiedValue > slowest.stats.maxBurnLevel.modifiedValue){
        slowest.stats.maxBurnLevel.modifyFlat(buffId, tug.stats.maxBurnLevel.modifiedValue- slowest.stats.maxBurnLevel.modifiedValue)
      }
      //aEP_Tool.addDebugLog(slowest.hullId+" after apply "+slowest.stats.maxBurnLevel.modifiedValue)
    }

    override fun advance(days: Float) {
      if(tug.stats.maxBurnLevel.modifiedValue > slowest.stats.maxBurnLevel.modifiedValue){
        slowest.stats.maxBurnLevel.modifyFlat(buffId, tug.stats.maxBurnLevel.modifiedValue- slowest.stats.maxBurnLevel.modifiedValue)
      }

      if(this.days > maxDur){
        shouldEnd = true
        return
      }
      this.days += days
    }
  }
}


