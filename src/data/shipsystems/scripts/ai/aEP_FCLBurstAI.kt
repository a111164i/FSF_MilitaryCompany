package data.shipsystems.scripts.ai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import com.fs.starfarer.api.util.IntervalUtil
import org.lwjgl.util.vector.Vector2f
import data.shipsystems.scripts.aEP_FCLBurstScript
import org.lazywizard.lazylib.CollisionUtils
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.isFriendlyInLine
import org.lazywizard.lazylib.MathUtils

class aEP_FCLBurstAI : aEP_BaseSystemAI() {
  companion object{
    const val WEAPON_SPEC_ID = "aEP_fga_yonglang_main"
    val MAX_RANGE = Global.getSettings().getWeaponSpec(WEAPON_SPEC_ID).maxRange - 100f
  }

  override fun initImpl() {
    thinkTracker.setInterval(0.33f,0.33f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {

    shouldActive = false

    val detectPoint = aEP_Tool.getExtendedLocationFromPoint(ship.location,ship.facing,MAX_RANGE)

    //打不到敌人也滚了
    target?: return
    if(target.isFighter) return
    //幅能不够用就直接滚了
    if(ship.maxFlux < 2000f || ship.maxFlux - ship.currFlux < 2000f) return
    //可能打到友军，出去
    if(isFriendlyInLine(ship.location, detectPoint) != null)  return
    //打不到敌人也出去
    val hitPoint = CollisionUtils.getCollisionPoint(ship.location, detectPoint, target) ?: return
    val dist = MathUtils.getDistance(ship.location,hitPoint)

    //开始计算意愿
    var willing = 0f

    //处于近程内，更想用
    val halfRange = MAX_RANGE * 2f/3f
    if(dist < halfRange){
      willing += 50f * (1f - dist/halfRange)
    }


    //自己状态越健康越想用
    val fluxLevelBelowThreshold = MathUtils.clamp(ship.currFlux/(ship.maxFlux - 2000f),0f,1f)
    willing += 75f *  fluxLevelBelowThreshold

    if (system.ammo > 2) willing += 15f
    if (system.ammo > 1) willing += 15f

    //对手即将过载，更想用了
    val targetRestFlux = target.maxFlux - ship.currFlux
    if(targetRestFlux <  5000f){
      willing += 50f * (5000f-targetRestFlux)/5000f
    }


    if(flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING)) willing -= 25f

    willing *= MathUtils.getRandomNumberInRange(0.75f, 1.25f)
    //aEP_Tool.addDebugLog(willing.toString())
    if (willing >= 100f) {
      shouldActive = true
    }
  }

}