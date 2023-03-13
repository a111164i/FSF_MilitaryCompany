package combat.plugin

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.util.IntervalUtil
import combat.plugin.aEP_BuffEffect
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import combat.impl.aEP_Buff
import java.util.ArrayList
import java.util.WeakHashMap

class aEP_BuffEffect : BaseEveryFrameCombatPlugin() {
  var engine: CombatEngineAPI? = null
  var tracker = IntervalUtil(RUN_INTERVAL, RUN_INTERVAL)

  override fun init(engine: CombatEngineAPI) {
    this.engine = engine
    if (engine.customData["aEP_BuffEffect"] == null) {
      engine.customData["aEP_BuffEffect"] = aEP_BuffEffect()
    }
  }

  override fun advance(amount: Float, events: List<InputEventAPI>) {
    if (engine == null) {
      engine = Global.getCombatEngine()
      return
    }
    if (engine!!.isPaused) return
    tracker.advance(amount)
    if (!tracker.intervalElapsed()) return
    //////////////////////////////////////////////////
    //                                              //
    //      below here run only once per 0.1 sec    //
    //                                              //
    //////////////////////////////////////////////////

    //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(),timer+"",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,5f);
    val buffMap: MutableMap<CombatEntityAPI, MutableMap<String, MutableList<aEP_Buff>>>? = buffMap
    val toRemoveList: MutableList<CombatEntityAPI> = ArrayList()
    for ((key, value) in Companion.buffMap!!) {
      val entity = key
      val entityBuffMap = value as Map<String, List<*>>
      if (Global.getCombatEngine().isEntityInPlay(entity)) {
        for ((key1, value1) in entityBuffMap) {
          val buffType = key1
          val buffs: MutableList<aEP_Buff> = value1 as MutableList<aEP_Buff>
          val expiredBuffs: MutableList<aEP_Buff> = ArrayList()
          var newestBuff: aEP_Buff? = null
          var newestTime = 9999999f
          var allStackNum = 0f
          var originStackForNewestBuff = 0f
          //做一个循环，找到剩余时间最长的同种 buff，算出同类 buff层数和，运行一次
          for (buff in buffs) {
            if (buff.isExpired() || buff.stackNum <= 0f) {
              expiredBuffs.add(buff)
            } else {
              allStackNum += buff.stackNum
              if (buff.time < newestTime) {
                newestTime = buff.time
                newestBuff = buff
                originStackForNewestBuff = buff.stackNum
              }
              //对于刷新型
              //把其他同类 buff的层数都给它
              //清理掉同类 buff，只留下剩余时间最长的那个
              if (buff.isRenew) buff.stackNum = 0f
            }
            buff.advance(RUN_INTERVAL)
          }
          if (newestBuff != null) {
            newestBuff.stackNum = Math.min(allStackNum, newestBuff.maxStack)
            if (!newestBuff.shouldEnd && newestBuff.stackNum > 0) {
              newestBuff.play()
            } else {
              newestBuff.readyToEnd()
            }
            if (!newestBuff.isRenew) {
              newestBuff.stackNum = originStackForNewestBuff
            }
          }
          //remove expired buffs
          buffs.removeAll(expiredBuffs)
        }
      } else {
        toRemoveList.add(entity)
      }
    }

    //remove expired entity
    for (expiredEntity in toRemoveList) {
      buffMap?.remove(expiredEntity)
    }
  }

  companion object {
    const val RUN_INTERVAL = 0.1f
    fun checkBuffList(e: CombatEntityAPI, buffType: String): Boolean {
      val buffMap: Map<CombatEntityAPI, MutableMap<String, MutableList<aEP_Buff>>>? = buffMap
      return buffMap!!.containsKey(e) && buffMap[e]!!.containsKey(buffType)
    }

    @JvmStatic
    fun addThisBuff(entity: CombatEntityAPI, buffClass: Any?) {
      val buffMap = buffMap
      if (buffClass is aEP_Buff == false) return
      if (buffMap!!.containsKey(entity)) {
        if (buffMap[entity]!!.containsKey(buffClass.buffType)) {
          buffMap[entity]!![buffClass.buffType]!!.add(buffClass)
          return
        }
        val buffs: MutableList<aEP_Buff> = ArrayList()
        buffs.add(buffClass)
        buffMap[entity]!![buffClass.buffType] = buffs
        return
      }
      val buffs: MutableList<aEP_Buff> = ArrayList()
      buffs.add(buffClass)
      val map: MutableMap<String, MutableList<aEP_Buff>> = WeakHashMap()
      map[buffClass.buffType] = buffs
      buffMap[entity] = map
    }

    //封装好，不能在任何外部保留 buffMap的引用
    private val buffMap: MutableMap<CombatEntityAPI, MutableMap<String, MutableList<aEP_Buff>>>?
      private get() {
        val buffMapKey = "aEP_BuffMap"
        if (!Global.getCombatEngine().customData.containsKey(buffMapKey)) {
          val buffMap: MutableMap<CombatEntityAPI, MutableMap<String, MutableList<aEP_Buff>>> = WeakHashMap()
          Global.getCombatEngine().customData[buffMapKey] = buffMap
          return buffMap
        }
        return Global.getCombatEngine().customData[buffMapKey] as MutableMap<CombatEntityAPI, MutableMap<String, MutableList<aEP_Buff>>>?
      }
  }
}