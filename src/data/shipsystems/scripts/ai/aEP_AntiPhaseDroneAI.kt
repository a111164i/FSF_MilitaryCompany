package data.shipsystems.scripts.ai

import com.fs.starfarer.api.combat.ShipAPI
import combat.util.aEP_Tool
import data.shipsystems.scripts.aEP_AntiPhaseDrone.Companion.MAX_DISTANCE
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f

class aEP_AntiPhaseDroneAI:aEP_BaseSystemAI() {

  override fun initImpl() {
    thinkTracker.setInterval(0.5f,0.5f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    if(ship.fullTimeDeployed < 10f) return

    var willing = 0f
    var target: ShipAPI? = null
    //80乘以1.25浮动值极限达到100
    //所以此处给82，意味着只要不是全场已经没有敌人（willing获得减值），只要有3个充能就有低概率发动技能
    val numOfCharge = ship.system.ammo
    when (numOfCharge){
      4-> willing += 125f
      3-> willing += 82f
      2-> willing += 50f
      1-> willing += 10f
    }

    //如果根本探测不到敌人，willing减999，不可能发动技能
    val nearest = aEP_Tool.getNearestEnemyCombatShip(ship)
    target = nearest
    if(target != null){
      if(target.isPhased) willing += 125f

      val dist = MathUtils.getDistance(target,ship)
      if(dist < aEP_Tool.getSystemRange(ship,MAX_DISTANCE)){
        //2次充能有90的欲望
        willing +=50f
        if(dist < aEP_Tool.getSystemRange(ship,MAX_DISTANCE)/2f){
          //2次充能有125的欲望
          //1次充能有85的欲望
          willing += 25f
        }
      }else{
        if(dist < aEP_Tool.getSystemRange(ship,MAX_DISTANCE)*1.5f) willing+=5f

      }
    }else{
      willing -= 999f
    }

    shouldActive = false
    if(willing * MathUtils.getRandomNumberInRange(0.75f,1.25f) > 100f){
      val before:ShipAPI? = ship.shipTarget
      ship.shipTarget = target
      shouldActive = true
    }
  }
}