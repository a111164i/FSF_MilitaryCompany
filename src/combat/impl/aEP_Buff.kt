package combat.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.lazywizard.lazylib.MathUtils

open class aEP_Buff {

  var buffType = "aEP_Buff"
  var index = this.javaClass.simpleName + Math.random() + ""
  var stackNum = 1f
  var maxStack = 1f
  var entity: CombatEntityAPI? = null //apply to a entity
  var isRenew = false
  var shouldEnd = false

  var time = 0f
  var lifeTime = 0f;

  /**
   * 不应该override此方法，使用advanceImpl
   * time 处于 (0,lifeTime] 的区间内
   * time 刚好到 lifeTime的一帧仍然会运行
   * 当 shouldEnd为 true，准备在下一帧结束时，不会运行
   * 当 lifeTime <= 0 时，按时终结机制不生效，请在advanceImpl里面手动控制
   */
  fun advance(amount: Float) {
    //若 entity不为空，则进行 entity检测，不过就直接结束
    if(entity != null) {
      if(!Global.getCombatEngine().isEntityInPlay(entity) ||
        ((entity is ShipAPI) && !(entity as ShipAPI).isAlive)){
        shouldEnd = true
      }
    }
    if(shouldEnd) return

    time += amount
    MathUtils.clamp(time,0f,lifeTime)

    if(time >= lifeTime && lifeTime > 0){
      shouldEnd = true
    }
  }

  /**
   * 效果
   */
  open fun play(){

  }

  fun isExpired(): Boolean {
    if(shouldEnd){
      readyToEnd()
      return true
    }
    return false
  }

  /**
   * 结束时运行一次
   */
  open fun readyToEnd(){

  }
}