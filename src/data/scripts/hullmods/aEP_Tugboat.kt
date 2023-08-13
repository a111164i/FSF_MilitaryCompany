package data.scripts.hullmods

import com.fs.starfarer.api.campaign.BuffManagerAPI.Buff
import com.fs.starfarer.api.fleet.FleetMemberAPI
import combat.util.aEP_Tool.Util.addDebugLog
import org.lazywizard.lazylib.MathUtils
import java.util.ArrayList

class aEP_Tugboat: aEP_BaseHullMod() {

  companion object{
    const val ID = "aEP_Tugboat"
    const val MAX_SPEED_BONUS = 4f
  }

  override fun advanceInCampaign(member: FleetMemberAPI?, amount: Float) {
    if(member?.fleetData?.fleet == null) return
    val allMemberList = member.fleetData.combatReadyMembersListCopy
    val toRemoveList = ArrayList<FleetMemberAPI>()

    //如果自己处于封存状态，无法拖人
    if(member.isMothballed){
      return
    }
    //如果自己存在buff了，无法拖人
    if(member.buffManager.getBuff(TowCableBuff.ID) != null){
      return
    }

    //从全舰队列表中得到待拖对象
    for(m in allMemberList){
      //已经存在buff，正在被拖的不可以拖
      if(m.buffManager.getBuff(TowCableBuff.ID) != null){
        toRemoveList.add(m)
      }
      //已经封存的不可以拖
      if(m.isMothballed){
        toRemoveList.add(m)
      }
      addDebugLog(m.shipName + m.stats.maxBurnLevel.modifiedValue)

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

    //待拖对象中没有合适的船就直接return
    if(allMemberList.size < 1){
      return
    }

    //用这艘的拖船去拖当前最慢的成员
    //因为拖船之间不能相互拖，所以最慢的拖船决定了舰队的速度上限
    //这个buff会被同时加给拖船和被拖的buffManager，lifeTime消耗速度是正常的2倍
    val lifeTime = MathUtils.getRandomNumberInRange(0.05f,0.15f) * 2f
    member.buffManager.addBuff(TowCableBuff(member, allMemberList[0],lifeTime))
    allMemberList[0].buffManager.addBuff(TowCableBuff(member, allMemberList[0],lifeTime))
    //Global.getLogger(this.javaClass).info(test.toString())
  }

  open class TowCableBuff( ) : Buff {
    companion object{
      const val ID = "aEP_TowCableBuff"
    }

    private var days = 0f
    private var maxDur = 0f
    private var shouldEnd = false

    lateinit var tug:FleetMemberAPI
    lateinit var slowest:FleetMemberAPI


    constructor(tug: FleetMemberAPI,slowest: FleetMemberAPI, lifeTime: Float):this(){
      this.tug = tug
      this.slowest = slowest
      this.maxDur = lifeTime
    }

    override fun isExpired(): Boolean {
      if(shouldEnd){
        slowest.stats.maxBurnLevel.unmodify(ID)
        //aEP_Tool.addDebugLog(slowest.hullId+" end")
      }
      return shouldEnd
    }

    override fun getId(): String {
      return ID
    }

    //当任何一个新buff加进去，所有的老buff都会执行一次apply
    //buff会同时施加给拖船和被拖的船，这里不要使用member
    override fun apply(member: FleetMemberAPI) {
      val tugSpeedNow = tug.stats.maxBurnLevel.modifiedValue
      val slowestSpeedNow = slowest.stats.maxBurnLevel.modifiedValue

      //速度差大于0
      if(tugSpeedNow - slowestSpeedNow > 0f){
        slowest.stats.maxBurnLevel.modifyFlat(ID, (tugSpeedNow - slowestSpeedNow).coerceAtMost(MAX_SPEED_BONUS))
      }
      //aEP_Tool.addDebugLog(slowest.hullId+" after apply "+slowest.stats.maxBurnLevel.modifiedValue)
    }

    override fun advance(days: Float) {

      //如果拖船和被拖船不在同个舰队中，取消buff
      if(tug.fleetData != slowest.fleetData){
        shouldEnd = true
      }

      if(this.days >= maxDur){
        shouldEnd = true
        return
      }
      this.days += days
      this.days = this.days.coerceAtMost(maxDur)
    }
  }
}


