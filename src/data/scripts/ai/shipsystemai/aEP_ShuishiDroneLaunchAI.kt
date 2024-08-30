package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.util.WeightedRandomPicker
import combat.util.aEP_Combat
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_ShuishiDroneLaunch
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f

class aEP_ShuishiDroneLaunchAI: aEP_BaseSystemAI() {

  companion object{
    const val ID = "aEP_ShuishiDroneLaunch"
  }

  override fun initImpl() {
    thinkTracker.setInterval(0.5f,1.5f)
  }

  fun getMultiple(size :ShipAPI.HullSize): Float{
    if(size == ShipAPI.HullSize.CAPITAL_SHIP)
      return 2f
    else if (size == ShipAPI.HullSize.CRUISER)
      return 2f
    else if (size == ShipAPI.HullSize.DESTROYER)
      return 1f
    else if (size == ShipAPI.HullSize.FRIGATE)
      return 1f
    return 0f
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false

    //如果系统已经启动，暂停索敌
    if(ship.system.state != ShipSystemAPI.SystemState.IDLE) return

    val targetWeightPicker = WeightedRandomPicker<ShipAPI>()
    //遍历友军，根据危险程度加入权重选择器
    for(s in AIUtils.getNearbyAllies(ship, aEP_Tool.getSystemRange(ship, aEP_ShuishiDroneLaunch.SYSTEM_RANGE) + ship.collisionRadius + 200f)){
      //危险阈值
      val threshold = 75f
      var weight = getWeight(s)
      weight += 25f - ship.fluxLevel * ship.fluxLevel * 25f
      if(weight > threshold){
        targetWeightPicker.add(s,weight*getMultiple(s.hullSize))
      }
    }
    targetWeightPicker.add(ship,getWeight(ship)*getMultiple(ship.hullSize))
    val target = targetWeightPicker.pick()

    if(target != null){
      ship.setCustomData(aEP_ID.SYSTEM_SHIP_TARGET_KEY,target)
      shouldActive = true
    }

  }

  fun getWeight(s : ShipAPI): Float{
    val multiple = getMultiple(s.hullSize)
    //舰体级别系数为0的直接跳过
    if(multiple <= 0) return 0f
    var weight = 0f
    //准备好数据
    val hullLevel = s.hullLevel
    val hardPercent = MathUtils.clamp(s.fluxTracker.hardFlux/s.fluxTracker.currFlux,0f,1f)
    val fluxLevel = s.fluxLevel

    val hullLevelWeight = 75f
    val hullLost = 1f - hullLevel
    if(hullLost > 0.25f){
      weight += (hullLost-0.25f) *(1f-0.25f) * hullLevelWeight
    }

    val softFluxLevelWeight = 75f
    val fluxLevelWeight = 200f
    val threshold = 0.2f
    if(fluxLevel >threshold){
      val softLevel = (s.fluxTracker.currFlux - s.fluxTracker.hardFlux)/(s.fluxTracker.maxFlux+1f)
      val hardLevel = s.fluxTracker.hardFlux/(s.fluxTracker.maxFlux+1f)
      weight += (hardLevel-threshold)/(1-threshold) * fluxLevelWeight
      weight += (softLevel-threshold)/(1-threshold) * softFluxLevelWeight
    }

    val inDpsDangerFlagWeight = 75f
    if(fluxLevel > 0.25f && s.shipAI?.aiFlags?.hasFlag(ShipwideAIFlags.AIFlags.IN_CRITICAL_DPS_DANGER) == true){
      weight += inDpsDangerFlagWeight
    }

    val needsHelpFlagWeight = 100f
    if(fluxLevel > 0.35f && s.shipAI?.aiFlags?.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP) == true){
      weight += needsHelpFlagWeight
    }

    val overloadWeight = 150f
    if(s.fluxTracker.isOverloaded){
      weight += overloadWeight
    }
    weight *= (0.4f + 0.6f * hardPercent)
    return weight * MathUtils.getRandomNumberInRange(0.75f,1.25f)
  }
}