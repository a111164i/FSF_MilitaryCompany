package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.combat.WeaponAPI
import combat.util.aEP_Tool
import data.scripts.hullmods.aEP_PingdingMainSwapHidden
import data.scripts.shipsystems.aEP_SiegeMode
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.pow

class  aEP_MainGunAttitudeAI : aEP_BaseSystemAI() {

  override fun initImpl() {
    thinkTracker.setInterval(2f,4f)
  }

  data class EnemyData(val ship: ShipAPI, val approachingSpeed: Float, val threatDps: Float, val dist: Float, val angleDist: Float)

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    val system = rightClickSys as ShipSystemAPI

    //展开途中不会思考，防止武器距离变化导致ai反复
    if(system.state == ShipSystemAPI.SystemState.IN) return
    if(system.state == ShipSystemAPI.SystemState.OUT) return


    shouldPhaseActive = false

    // find self max range
    var maxWeaponRange = 0f
    for(w in ship.allWeapons){
      if(w.isDecorative) continue
      if(w.slot.id.equals(aEP_PingdingMainSwapHidden.MAIN_SLOT)) {
        maxWeaponRange = w.range + 9999f
      }
    }

    //aEP_Tool.addDebugLog("weapon: " + maxWeaponRange)

    //------------------------------------------------------------//
    //寻找射程内的敌人
    val aroundEnemies = ArrayList<EnemyData>()
    for(shp in engine.ships){

      var dps = 0f

      if(!aEP_Tool.isEnemy(ship,shp)) continue
      val dist = MathUtils.getDistance(ship.location,shp.location)
      if(dist > maxWeaponRange) continue
      // find enemy threatening damage
      for(w in ship.allWeapons){
        if(aEP_Tool.isNormalWeaponType(w,false)){
          //只统计打的着的武器
          if(MathUtils.getShortestRotation(w.currAngle, VectorUtils.getAngle(w.location,ship.location)).absoluteValue > 30f &&
            !w.hasAIHint(WeaponAPI.AIHints.DO_NOT_AIM)) continue
          if(w.range < dist) continue
          if(w.damageType == DamageType.ENERGY) dps += w.derivedStats.dps * 0.75f
          if(w.damageType == DamageType.FRAGMENTATION) dps += w.derivedStats.dps * 0.25f
        }
      }

      val toEnemy = Vector2f.sub(shp.location, ship.location, null)
      val approachingSpeed = Vector2f.dot(   VectorUtils.clampLength(toEnemy,1f), shp.velocity)

      val angleDist = MathUtils.getShortestRotation(ship.facing, VectorUtils.getAngle(ship.location,shp.location)).absoluteValue

      //aEP_Tool.addDebugLog("dist: "+dist)
      // Store ship and its approaching speed in <aroundEnemies>
      aroundEnemies.add(EnemyData(shp, approachingSpeed, dps, dist,angleDist))
    }

    //------------------------------------------------------------//
    //检测附近敌人的威胁度和主要目标有多近
    var willing = 0f
    for (enemyData in aroundEnemies) {

      val isApproaching = enemyData.approachingSpeed > 0f
      val isRetreatingFast = enemyData.approachingSpeed < -20f

      if(enemyData.ship == ship.shipTarget && !ship.shipTarget.isFighter){
        //基础拥有25，如果目前0幅能，范围内有人就可以开
        willing += 25f

        //如果范围内只有一艘，给与大量加成
        if(aroundEnemies.size == 1){
          willing += 50f
        }

        //如果目标正在接近，给加成
        //越近越加成
        if(isApproaching) {
          willing += 25f
          if(enemyData.approachingSpeed > 5f) willing += 15f
          if(enemyData.approachingSpeed > 10f) willing += 15f
          if(enemyData.approachingSpeed > 15f) willing += 15f
          if(enemyData.approachingSpeed > 20f) willing += 15f
          if(enemyData.approachingSpeed > 25f) willing += 15f
        }
        if(enemyData.dist < maxWeaponRange - 100f) willing += 50f
        if(enemyData.dist < maxWeaponRange - 200f) willing += 50f
        if(enemyData.dist < maxWeaponRange - 400f) willing += 40f
        if(enemyData.dist < maxWeaponRange - 600f) willing += 30f

        //目标在撤退并且即将脱离
        if(isRetreatingFast){
          if(enemyData.dist > (maxWeaponRange - 300f)) willing -= 10f
          if(enemyData.dist > (maxWeaponRange - 200f)) willing -= 10f
          if(enemyData.dist > (maxWeaponRange - 100f)) willing -= 10f
        }

        //迎敌角度不能太歪
        if(enemyData.angleDist > 30f) willing -= 10f
        if(enemyData.angleDist > 60f) willing -= 20f
        if(enemyData.angleDist > 90f) willing -= 30f
        if(enemyData.angleDist > 120f) willing -= 40f

      }else{
        //如果是非目标，根据位置和武器计算威胁，减少意愿
        var multiplier = 1f

        if(enemyData.dist < maxWeaponRange - 200f) multiplier += 0f
        if(enemyData.dist < maxWeaponRange - 400f) multiplier += 0.1f
        if(enemyData.dist < maxWeaponRange - 600f) multiplier += 0.2f
        if(enemyData.dist < maxWeaponRange - 800f) multiplier += 0.3f
        if(enemyData.dist < maxWeaponRange - 1000f) multiplier += 0.4f


        if(enemyData.angleDist > 60f) multiplier += 0.1f
        if(enemyData.angleDist > 90f) multiplier += 0.5f
        if(enemyData.angleDist > 120f) multiplier += 2f

        //正面10秒(每秒打掉0.1)打爆结构，则一定关闭(0.1 * 1000 = 100 willing)
        willing -= (enemyData.threatDps * multiplier / (ship.hitpoints.coerceAtLeast(3000f))) * 250f
      }

    }


    if(ship.fluxTracker.fluxLevel < 0.65f) willing += 10f
    if(ship.fluxTracker.fluxLevel < 0.5f) willing += 15f
    if(ship.fluxTracker.fluxLevel < 0.35f) willing += 20f
    if(ship.fluxTracker.fluxLevel < 0.2f) willing += 25f

    if(ship.fluxTracker.fluxLevel > 0.85f) willing -= 10f
    if(ship.fluxTracker.fluxLevel > 0.9f) willing -= 15f
    if(ship.fluxTracker.fluxLevel > 0.95f) willing -= 25f

    willing *= MathUtils.getRandomNumberInRange(0.9f,1.1f)
    if(willing > 100f) shouldPhaseActive = true
    aEP_Tool.addDebugLog("willing: "+willing.toString())
  }
}