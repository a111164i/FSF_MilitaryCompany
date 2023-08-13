package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.util.WeightedRandomPicker
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_MaodianDroneLaunch
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f

class aEP_MaoDianDroneLaunchAI: aEP_BaseSystemAI() {
  companion object{
    const val TARGET_KEY = "aEP_MDDroneLaunchAI_assign_target"
  }

  override fun initImpl() {
    thinkTracker.setInterval(1f,1f)
  }

  fun getMultiple(size :ShipAPI.HullSize): Float{
    if(size == ShipAPI.HullSize.CAPITAL_SHIP)
      return 4f
    else if (size == ShipAPI.HullSize.CRUISER)
      return 3f
    else if (size == ShipAPI.HullSize.DESTROYER)
      return 1f
    else if (size == ShipAPI.HullSize.FRIGATE)
      return 0.5f
    return 0f
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    val targetWeightPicker = WeightedRandomPicker<ShipAPI>()
    //遍历友军，根据危险程度加入权重选择器
    for(s in AIUtils.getNearbyAllies(ship, aEP_Tool.getSystemRange(ship, aEP_MaodianDroneLaunch.SYSTEM_RANGE) + ship.collisionRadius + 200f)){
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
    shouldActive = false
    if(target != null){
      //如果是ai使用，把ai的目标塞入母舰的customData
      //默认方向是目标舰船的头向
      val loc = aEP_Tool.getExtendedLocationFromPoint(target.location, target.facing,target.collisionRadius + 200f)
      //如果护盾目标和锁定目标任何一个存在，就往这个方向释放
      if(target.shieldTarget != null || target.shipTarget != null){
        val facing = VectorUtils.getAngle(target.location, target.shieldTarget?:target.shipTarget.location)
        loc.set(aEP_Tool.getExtendedLocationFromPoint(target.location, facing ,target.collisionRadius + 200f))
      }

      loc.set(MathUtils.getRandomPointInCircle(loc,100f))
      ship.customData[TARGET_KEY] = loc
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