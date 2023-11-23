package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.util.WeightedRandomPicker
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_AntiPhaseDrone.Companion.MAX_DISTANCE
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.pow

class aEP_AntiPhaseDroneAI: aEP_BaseSystemAI() {

  override fun init(ship: ShipAPI, system: ShipSystemAPI, flags: ShipwideAIFlags, engine: CombatEngineAPI) {
    super.init(ship, system, flags, engine)
  }

  override fun initImpl() {
    thinkTracker.setInterval(0.5f,1.5f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    if(ship.fullTimeDeployed < 10f) return

    var willing = 0f
    //80乘以1.25浮动值极限达到100
    //所以此处给82，意味着只要不是全场已经没有敌人（willing获得减值），只要有3个充能就有低概率发动技能
    val numOfCharge = ship.system.ammo
    when (numOfCharge){
      4-> willing += 125f
      3-> willing += 85f
      2-> willing += 50f
      1-> willing += 10f
    }

    //如果根本探测不到敌人，willing减999，不可能发动技能
    val picker = WeightedRandomPicker<ShipAPI>()
    var target: ShipAPI? = null
    val systemDistSq = aEP_Tool.getSystemRange(ship, MAX_DISTANCE).pow(2)
    for( s in AIUtils.getEnemiesOnMap(ship)){

      if(!aEP_Tool.isShipTargetable(
          s, true, true,
          true, true,
          false)) continue

      val distSq = MathUtils.getDistanceSquared(s, ship)
      val distLevel = (1f - (distSq/systemDistSq).pow(2)).coerceAtLeast(0.1f)
      //把系统范围内的有效目标都加入一个picker，根据距离选择权重
      //对于相位目标天生多5倍优先级
      if(distSq < systemDistSq){
        if(s.isPhased){
          picker.add(s,distLevel * 5f)
        }else{
          picker.add(s,distLevel)
        }
      }
    }
    target = picker.pick()


    if(target != null){
      if(target.isPhased) willing += 125f

      val distSq = MathUtils.getDistanceSquared(target,ship)
      val sysDistSq =  aEP_Tool.getSystemRange(ship,MAX_DISTANCE).pow(2)
      if(distSq < sysDistSq){
        //2次充能有90的欲望
        willing +=50f
        if(distSq < sysDistSq/4f){
          //2次充能有125的欲望
          //1次充能有85的欲望
          willing += 25f
        }
      }else{
        if(distSq < sysDistSq*2f) willing+=5f

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