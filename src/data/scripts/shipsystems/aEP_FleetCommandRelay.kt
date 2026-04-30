package data.scripts.shipsystems

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import data.scripts.aEP_CombatEffectPlugin
import data.scripts.hullmods.aEP_FleetCommand
import data.scripts.utils.aEP_BaseCombatEffect
import data.scripts.utils.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import java.awt.Color

class aEP_FleetCommandRelay : BaseShipSystemScript(), WeaponRangeModifier {

  companion object {
    const val BUFF_DURATION = 20f
    val WEAPON_GLOW = Color(100, 150, 255, 155)
  }

  val lidarWeapons = mutableListOf<WeaponAPI>()
  var buffApplied = false
  var inited = false

  /**
   * 首次进入战斗时调用一次，收集舰船上所有带 LIDAR 标签的装饰性武器
   * 禁用其自动转向，改为由本系统手动控制朝向 target 方向
   *
   * 生命周期：apply() 首次执行 → init() 一次性收集 → 后续帧复用缓存列表
   */
  fun init(ship: ShipAPI) {
    if (inited) return
    inited = true
    for (w in ship.allWeapons) {
      if (w.isDecorative && w.spec.hasTag(Tags.LIDAR)) {
        w.setSuspendAutomaticTurning(true)
        lidarWeapons.add(w)
      }
    }
  }

  /**
   * 主逻辑入口，每帧调用（包括暂停时 amount=0）
   *
   * 状态机遍历：
   *   IDLE → 玩家/AI 按下系统键 → IN（充能阶段）
   *   IN → 充能条满 → ACTIVE（effectLevel=1 的那一帧）
   *   ACTIVE → 系统时间耗尽/手动取消 → OUT（退场阶段）
   *   OUT → 退场动画结束 → COOLDOWN
   *   COOLDOWN → 冷却结束 → IDLE
   *
   * 本系统关注三个关键时机：
   *   1. State.IN 期间 — 压制普通武器，转动 LIDAR 对准目标并间歇开火
   *   2. effectLevel==1f 的那一帧 — 向友军 shipTarget 施加 FleetCommandOn buff
   *   3. IDLE/COOLDOWN 期间 — 重置 buffApplied 标记，为下次激活做准备
   *
   * @param stats  舰船数值，通过 stats.entity 获取 ShipAPI
   * @param id     由系统框架生成的唯一实例 ID，用于 stats.modify*/unmodify* 配对
   * @param state  当前系统状态阶段
   * @param effectLevel 当前效果强度 (0=关闭, 0~1=充能中, 1=完全激活)
   */
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    val ship = (stats?.entity ?: return) as ShipAPI

    if (ship.isHulk) {
      buffApplied = false
      return
    }
    init(ship)

    val active = state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE || state == ShipSystemStatsScript.State.OUT

    //———————————— 充能阶段 (State.IN) ————————————//
    if (state == ShipSystemStatsScript.State.IN) {

      val target = ship.shipTarget
      if (target != null) {
        val amount = aEP_Tool.getAmount(ship)

        // 1. 逐帧平滑转动每个 LIDAR 武器，使其对准 shipTarget
        for (w in lidarWeapons) {
          val angleToTarget = VectorUtils.getAngle(w.location, target.location)

          val angleDist = MathUtils.getShortestRotation(w.currAngle, angleToTarget)
          if (Math.abs(angleDist) > w.slot.arc * 0.5f) continue

          val turnRate = w.turnRate * 3f
          val maxTurnThisFrame = turnRate * amount
          if (Math.abs(angleDist) <= maxTurnThisFrame) {
            w.setFacing(angleToTarget)
          } else {
            w.setFacing(w.currAngle + Math.signum(angleDist) * maxTurnThisFrame)
          }
        }

        // 2. 对准目标后（偏差<10°）且充能进度超过阈值，强制 LIDAR 开火一帧
        val fireThreshold = 0.25f / ship.system.chargeUpDur
        for (w in lidarWeapons) {
          val angleToTarget = VectorUtils.getAngle(w.location, target.location)
          val angleDist = Math.abs(MathUtils.getShortestRotation(w.currAngle, angleToTarget))
          if (angleDist < 10f && effectLevel >= fireThreshold) {
            w.setForceFireOneFrame(true)
          }
        }
      }

      // 3. 为 LIDAR 武器叠加发光贴图，随 effectLevel 增强，提供视觉反馈
      // val glowColor = WEAPON_GLOW
      // for (w in lidarWeapons) {
      //   w.setGlowAmount(effectLevel, glowColor)
      // }
    }

    //———————————— effectLevel==1 的那一帧 ————————————//
    if (effectLevel == 1f && !buffApplied) {
      buffApplied = true
      val target = ship.shipTarget
      if (target is ShipAPI && target.owner == ship.owner && !aEP_Tool.isDead(target)) {
        applyFleetCommandBuff(target)
      }
    }

    //———————————— 空闲/冷却阶段 ————————————//
    if (state == ShipSystemStatsScript.State.IDLE || state == ShipSystemStatsScript.State.COOLDOWN) {
      if (buffApplied) {
        buffApplied = false
      }
    }
  }

  /**
   * 向目标友军舰船施加持续 BUFF_DURATION 秒的 aEP_FleetCommandOn 状态
   *
   * 实现方式：
   *   1. 在目标的 stats.dynamic 上注册一个 modifyFlat 修改，令 FleetCommandOn 值 >= 1f
   *   2. 创建一个 aEP_BaseCombatEffect 实例，作为定时器在 BUFF_DURATION 秒后自动移除 stat 修改
   *
   * 状态同步：
   *   stats 修改使得 aEP_FleetCommand.advanceInCombat() 能检测到该船拥有指挥能力，
   *   从而对周围 1600 范围内安装了护航套件的友军施加增益。
   *   这是复用已有的 hullmod 逻辑而非重新实现。
   */
  private fun applyFleetCommandBuff(target: ShipAPI) {
    val buffId = "aEP_FleetCommandRelay"

    aEP_FleetCommand.applyFleetCommand(target.mutableStats, buffId)

    val buffEffect = object : aEP_BaseCombatEffect(BUFF_DURATION, target) {

      override fun advanceImpl(amount: Float) {
        if (aEP_Tool.isDead(target)) {
          shouldEnd = true
          return
        }
      }

      override fun readyToEnd() {
        aEP_FleetCommand.unapplyFleetCommand(target.mutableStats, buffId)
      }
    }

    aEP_CombatEffectPlugin.addEffect(buffEffect)
  }

  /**
   * 由于 runScriptWhileIdle=true，此方法不会被调用
   * 所有卸载逻辑在 apply() 的 IDLE/COOLDOWN 分支中处理
   */
  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
  }

  /**
   * 系统在 IDLE/ACTIVE/COOLDOWN 期间都可以调用来展示 HUD 状态信息
   * 返回 null 表示不显示额外状态文字
   */
  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): StatusData? {
    return null
  }

  //———————————— WeaponRangeModifier 接口实现 ————————————//
  // 以下三个方法在武器射程计算时被调用，用于动态修改 LIDAR 武器的射程
  // 保证激光雷达光束能够抵达 shipTarget 的位置

  override fun getWeaponRangePercentMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    return 0f
  }

  override fun getWeaponRangeMultMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    return 1f
  }

  /**
   * 当系统处于激活状态时，将 LIDAR 装饰武器的射程扩展到能覆盖目标友舰的距离
   * 计算规则：取目标实际距离 与 500 基础值中的较大者（武器自带100射程）
   *
   * 此方法在每帧渲染前由引擎调用，对每个武器分别计算
   */
  override fun getWeaponRangeFlatMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    ship ?: return 0f
    if (!weapon.spec.hasTag(Tags.LIDAR) || ship.system?.isActive != true) return 0f

    var lidarRange = 300f
    val target = ship.shipTarget
    if (target != null) {
      val dist = MathUtils.getDistance(weapon.location, target.location)
      lidarRange = Math.max(lidarRange, dist)
    }
    return lidarRange
  }
}
