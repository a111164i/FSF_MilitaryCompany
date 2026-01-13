package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.listeners.*
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.util.ColorShifter
import data.scripts.utils.aEP_DataTool.txt
import java.awt.Color
import java.util.HashMap
import kotlin.math.pow

class aEP_ShieldFloating : aEP_BaseHullMod() {

  companion object{
    const val ID = "aEP_ShieldFloating"
    val SHIFT_COLOR = Color(155,25,255,205)

    private val mag: MutableMap<HullSize, Float> = HashMap<HullSize, Float>().withDefault { 0.025f }
    init {
      mag[HullSize.FIGHTER] = 800f
      mag[HullSize.FRIGATE] = 0.03f
      mag[HullSize.DESTROYER] = 0.027f
      mag[HullSize.CRUISER] = 0.025f
      mag[HullSize.CAPITAL_SHIP] = 0.024f
    }

    val DAMAGE_NEGATION_PER_STACK = 0.04f
    val PER_STACK_TIME = 1.35f

    val MAX_STACKS = 20
  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.HARDENED_SHIELDS)
    notCompatibleList.add(aEP_ShieldControlled.ID)

    allowOnHullsize[ShipAPI.HullSize.FRIGATE] = true
    allowOnHullsize[ShipAPI.HullSize.DESTROYER] = true
    allowOnHullsize[ShipAPI.HullSize.CRUISER] = true
    allowOnHullsize[ShipAPI.HullSize.CAPITAL_SHIP] = true

    requireShield = true
  }

  /**
   * 加入listener用这个
   */
  override fun applyEffectsAfterShipAddedToCombatEngine(ship: ShipAPI, id: String) {
    if (ship.shield == null || ship.shield.type == ShieldAPI.ShieldType.NONE) {
      return
    }

    if(!ship.hasListenerOfClass(ShieldDamageListener::class.java)){
      ship.addListener(ShieldDamageListener(ship))
      ship.setCustomData(ID,1f)
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
    if (index == 0) return String.format("%.1f", mag[hullSize]?.times(100f))+"%"
    if (index == 1) return String.format("%.1f", PER_STACK_TIME)
    if (index == 2) return String.format("%.0f", MAX_STACKS.toFloat())
    if (index == 3) return String.format("-%.0f", MAX_STACKS*DAMAGE_NEGATION_PER_STACK * 100f)+"%"

    return ""
  }

  internal class ShieldDamageListener(val ship: ShipAPI) : DamageListener, AdvanceableListener {
    private val shifter = ColorShifter(ship.shield.innerColor)
    private val ringShifter = ColorShifter(ship.shield.ringColor)

    private var accumulator = 0f
    val buffs = ArrayList<Float>();

    override fun reportDamageApplied(source: Any?, target: CombatEntityAPI?, result: ApplyDamageResultAPI?) {
      //谨防有人中途取消护盾
      ship.shield?:return

      //防止出现越叠高减伤，越难继续叠的情况。计算护盾伤害时抛开本mod自己减伤的影响
      val currentDamageTakenFromThisMod = ship.mutableStats.shieldDamageTakenMult.getMultStatMod(ID)?.value?:1f
      val actualDamageToShield = (result?.damageToShields?:0f) / currentDamageTakenFromThisMod
      accumulator += actualDamageToShield

      //每攒够一定护盾伤害，增加一层buff
      val thres = (mag[ship.hullSize]!! * ship.fluxTracker.maxFlux).coerceAtLeast(0f)
      while(accumulator > thres){
        accumulator -= thres
        buffs.add(PER_STACK_TIME) //每层buff持续2秒,可叠加,时间独立计算,放在队列末尾
        if(buffs.size > MAX_STACKS + 1){
          //防止堆积过多无用的buff,清理掉最早过期的那一层
          buffs.removeAt(0)
        }
      }

    }

    override fun advance(amount: Float) {
      //谨防有人中途取消护盾
      ship.shield?:return

      val stack = (buffs.size).coerceIn(0,MAX_STACKS)
      val level = stack.toFloat()/MAX_STACKS.toFloat()
      shifter.shift(ID,SHIFT_COLOR,0.001f,0.25f,0.6f * level)
      ringShifter.shift(ID,SHIFT_COLOR,0.001f,0.25f,0.6f * level)
      shifter.advance(amount)
      ringShifter.advance(amount)
      ship.shield.innerColor = shifter.curr
      ship.shield.ringColor = ringShifter.curr

      //维持玩家左下角的提示
      val damageReduceMult = level.pow(2f) * MAX_STACKS * DAMAGE_NEGATION_PER_STACK
      if (Global.getCombatEngine().playerShip == ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName+"1",  //key
          Global.getSettings().getSpriteName("aEP_ui", ID),  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          txt("aEP_ShieldFloating05") +String.format("-%.0f",100f * damageReduceMult) +"%",
          false)
      }

      //根据层数增加减伤
      ship.mutableStats.shieldDamageTakenMult.modifyMult(ID,1f - damageReduceMult)

      // 独立处理buff时间，该过期的过期
      if (buffs.isEmpty()) return
      // 从后向前遍历，安全地更新剩余时间并移除已过期的条目
      for (i in buffs.size - 1 downTo 0) {
        val remaining = (buffs[i] - amount).coerceAtLeast(0f)
        if (remaining <= 0f) {
          buffs.removeAt(i)
        } else {
          buffs[i] = remaining
        }
      }

    }


  }

}