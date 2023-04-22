package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import combat.util.aEP_Tool
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f

class  aEP_DamperTankerAI : aEP_BaseSystemAI() {
  val shouldOn = false
  var keepOnTime = 0f

  val FAST_REACT_RANGE = 100f
  val USE_THRESHOLD = 250f

  override fun initImpl() {
    thinkTracker.setInterval(0.05f,0.15f)
  }

  override fun advance(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {

    super.advance(amount, missileDangerDir, collisionDangerDir, target)
    //检测附近的制导导弹，如果目标是自己，立刻开盾
    for(m in AIUtils.getNearbyEnemyMissiles(ship,FAST_REACT_RANGE)){
      if(!m.isGuided) continue

    }
    if(shouldOn || keepOnTime > 0){
      keepOnTime -= aEP_Tool.getAmount(null)
      if(ship.system.state == ShipSystemAPI.SystemState.IDLE)
        ship.useSystem()
    }else{
      if(ship.system.isActive)
        ship.useSystem()
    }
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {

  }
}