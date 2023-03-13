package data.shipsystems.scripts.ai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.util.WeightedRandomPicker
import combat.util.aEP_Tool
import data.shipsystems.scripts.aEP_MaodianDroneLaunch
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f

class aEP_MDDroneLaunchAI:aEP_BaseSystemAI() {
  override fun initImpl() {
    thinkTracker.setInterval(0.2f,0.3f)
  }

  fun getMultiple(size :ShipAPI.HullSize): Float{
    if(size == ShipAPI.HullSize.CAPITAL_SHIP)
      return 2f
    else if (size == ShipAPI.HullSize.CRUISER)
      return 1.75f
    else if (size == ShipAPI.HullSize.DESTROYER)
      return 1f
    else if (size == ShipAPI.HullSize.FRIGATE)
      return 0.75f
    return 0f
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    val targetWeightPicker = WeightedRandomPicker<ShipAPI>()
    //遍历友军，根据危险程度加入权重选择器
    for(s in AIUtils.getNearbyAllies(ship, aEP_Tool.getSystemRange(ship,aEP_MaodianDroneLaunch.SYSTEM_RANGE) + ship.collisionRadius + 200f)){
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
      ship.customData["aEP_MDDroneLaunchAI_assign_target"] = aEP_Tool.getExtendedLocationFromPoint(target.location,target.facing,target.collisionRadius/2f)
      ship.useSystem()
    }
  }

  fun getWeight(s : ShipAPI): Float{
    val multiple = getMultiple(s.hullSize)
    //舰体级别系数为0的直接跳过
    if(multiple <= 0) return 0f
    var weight = 0f
    //准备好数据
    val hullLevel = s.hullLevel
    val fluxLevel = s.fluxLevel

    val hullLevelWeight = 75f
    val hullLost = 1f - hullLevel
    if(hullLost > 0.25f){
      weight += (hullLost-0.25f) *(1f-0.25f) * hullLevelWeight
    }

    val fluxLevelWeight = 150f
    if(fluxLevel > 0.25f){
      weight += (fluxLevel-0.2f)/(1-0.2f) * fluxLevelWeight
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
    return weight
  }
}