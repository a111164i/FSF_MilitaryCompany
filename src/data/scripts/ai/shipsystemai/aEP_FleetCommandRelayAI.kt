package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import data.scripts.utils.aEP_Tool
import org.lwjgl.util.vector.Vector2f

/**
 * fllet Command Relay 系统的AI决策脚本
 *
 * 生命周期（由父类 aEP_BaseSystemAI 驱动）：
 *   1. 战斗开始时，框架调用 init(ship, system, flags, engine)
 *   2. 每帧调用 advance(amount, missileDangerDir, collisionDangerDir, target)
 *   3. 每隔 0.1~0.5 秒（thinkTracker 间隔）调用一次 advanceImpl() 进行逻辑决策
 *   4. 决策结果通过 shouldActive 变量输出：
 *      - true  → 按下系统键开始充能
 *      - false → 不做操作（若系统已在激活中则保持激活直到充能完成）
 *   5. 系统使用后重新进入 COOLDOWN → IDLE 循环，AI 继续在每帧评估是否再次使用
 *
 * 决策条件：
 *   - shipTarget 必须存在且为友军舰船
 *   - 目标不能已拥有 FleetCommandOn 状态（避免重复施加）
 */
class aEP_FleetCommandRelayAI : aEP_BaseSystemAI() {

  companion object {
    const val STAT_KEY = "aEP_FleetCommandOn"
  }

  /**
   * AI 核心决策，由 thinkTracker 定时触发（约每0.1~0.5秒调用一次）
   *
   * 排除条件（满足任一则 shouldActive = false）：
   *   1. 没有锁定目标或目标不是舰船
   *   2. 目标已阵亡/残骸
   *   3. 目标是敌方（仅对友军使用）
   *   4. 目标已拥有 FleetCommandOn 状态（避免浪费次数）
   *
   * 全部不满足 → shouldActive = true，激活系统
   */
  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false

    val shipTarget = ship.shipTarget
    if (shipTarget == null || shipTarget !is ShipAPI) return
    if (aEP_Tool.isDead(shipTarget)) return
    if (shipTarget.owner != ship.owner) return

    // 目标已有 FleetCommandOn 状态，跳过
    if (shipTarget.mutableStats.dynamic.getStat(STAT_KEY)?.modifiedValue ?: 0f >= 1f) return

    shouldActive = true
  }
}
