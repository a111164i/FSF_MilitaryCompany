package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.combat.WeaponAPI
import data.scripts.utils.aEP_Tool
import data.scripts.shipsystems.aEP_SiegeMode
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.absoluteValue
import kotlin.math.pow

class  aEP_SiegeModeAI : aEP_BaseSystemAI() {

  companion object{
    fun scanNearbyEnemies(searchRange: Float, aroundEnemies: MutableList<EnemyData>, ship: ShipAPI): ShipAPI {
      var localNearestAimed = ship
      var localDistToNearestAim = 99999f

      for (enemy in Global.getCombatEngine().ships) {

        if (!aEP_Tool.isEnemy(ship, enemy)) continue
        // 不统计战机等无效目标
        if (!aEP_Tool.isShipTargetable(
            enemy,
            true,
            true,
            true,
            true,
            false
          )
        ) continue
        // 只统计自己武器射程内的敌人，自己射程内没有有效目标就不考虑开系统
        val dist = MathUtils.getDistance(ship.location, enemy.location)
        if (dist > searchRange) continue

        // 不统计不瞄准自己且自己也不瞄的敌人
        // ai不喜欢R锁定敌人，只能通过碰撞检测来判断瞄准线上的敌人
        val enemyAimSelf = CollisionUtils.getCollides(enemy.mouseTarget, enemy.location, ship.location, ship.collisionRadius + 50f)
        val selfAimEnemy = CollisionUtils.getCollides(ship.mouseTarget, ship.location, enemy.location, enemy.collisionRadius + 50f)
        if (!enemyAimSelf && !selfAimEnemy) continue

        // 找到瞄准线上离自己最近的当成是本舰的目标，鼠标位置会放在武器的最远落点，且目标距离不能超过最大武器射程
        if (selfAimEnemy && dist < localDistToNearestAim && dist < searchRange - 100f) {
          localNearestAimed = enemy
          localDistToNearestAim = dist
        }

        // 统计有威胁的敌人的数据
        var dps = 0f
        for (w in ship.allWeapons) {
          //不统计导弹武器
          if (!aEP_Tool.isNormalWeaponType(w, false)) continue
          //不统计PD武器
          if (w.hasAIHint(WeaponAPI.AIHints.PD_ONLY) || (w.hasAIHint(WeaponAPI.AIHints.PD))) continue
          //只统计射程够的武器
          if (w.range < dist) continue
          //只统计指向自己的武器
          if (MathUtils.getShortestRotation(
              w.currAngle, VectorUtils.getAngle(w.location, ship.location)
            ).absoluteValue > 10f && !w.hasAIHint(WeaponAPI.AIHints.DO_NOT_AIM)
          ) continue
          //计算威胁dps
          val damageTypeAdjust = when (w.damageType) {
            DamageType.ENERGY -> 1f
            DamageType.FRAGMENTATION -> 0.25f
            DamageType.HIGH_EXPLOSIVE -> 1.5f
            else -> 0.75f
          }
          dps *= damageTypeAdjust
          //敌人幅能在50%以上时，随着幅能增加威胁度降低，最高降低50%
          val fluxLevel = enemy.fluxLevel
          val fluxFactor = 0.5f
          dps *= 1f - (((fluxLevel - 0.5f) / (1f - 0.5f)).pow(5) * fluxFactor).coerceIn(0f, fluxFactor)
        }

        val toEnemy = Vector2f.sub(enemy.location, ship.location, null)
        val approachingSpeed = Vector2f.dot(VectorUtils.clampLength(toEnemy, 1f), enemy.velocity)
        val angleDist = MathUtils.getShortestRotation(ship.facing, VectorUtils.getAngle(ship.location, enemy.location)).absoluteValue

        //记录周围敌人的dps，接近率等数据
        aroundEnemies.add(EnemyData(enemy, approachingSpeed, dps, dist, angleDist))
      }

      return localNearestAimed
    }
  }

  override fun initImpl() {
    thinkTracker.setInterval(0.5f,1.5f)
    minActiveTime = 8f
  }

  data class EnemyData(val ship: ShipAPI, val approachingSpeed: Float, val threatDps: Float, val dist: Float, val angleDist: Float)

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    //展开途中不会思考，防止武器距离变化导致ai反复
    if(system?.state == ShipSystemAPI.SystemState.IN) return
    if(system?.state == ShipSystemAPI.SystemState.OUT) return


    shouldActive = false

    // find max range
    var maxWeaponRange = 0f
    for(w in ship.allWeapons){
      if(w.isDecorative) continue
      //没激活时需要计算激活后的射程，激活时就用当前射程
      w.spec.maxRange
      var newRange = w.range + w.spec.maxRange * (aEP_SiegeMode.RANGE_BONUS_PERCENT)/100f + aEP_SiegeMode.RANGE_BONUS_FLAT
      if(system?.isActive == true) newRange = w.range

      if(newRange > maxWeaponRange){
        maxWeaponRange = newRange
      }
    }

    //------------------------------------------------------------//
    //寻找射程内的敌人和距离自己鼠标最近的人
    maxWeaponRange *= 0.9f //防止ai在射程开关，本来射程就是溢出的，只搜索进入射程200内的敌人
    val aroundEnemies = ArrayList<aEP_SiegeModeAI.EnemyData>()
    val nearestAimed = scanNearbyEnemies(maxWeaponRange, aroundEnemies, ship)
    //------------------------------------------------------------//
    var willing = 0f
    var nearestEnemy = ship
    var nearest = 99999f

    //如果正在防空，不考虑开系统
    if(ship.shipTarget != null && ship.shipTarget.isFighter) willing -= 100f

    //检测附近敌人的威胁度和主要目标有多近
    for (enemyData in aroundEnemies) {
      val isApproaching = enemyData.approachingSpeed > 0f
      val isRetreatingFast = enemyData.approachingSpeed < -20f

      //顺便寻找最近的敌人
      if(enemyData.dist < nearest){
        nearest = enemyData.dist
        nearestEnemy = enemyData.ship
      }

      //对于自己正在瞄准的目标，位置越合适开火，愿意度越高
      if(enemyData.ship == nearestAimed){

        //基础拥有50，保证在：敌人刚进入范围，缓慢接近，周围没有dps威胁，自身幅能健康，正对敌人的情况下就会开系统
        willing += 50f

        //如果目标正在接近，给加成
        if (isApproaching) {
          willing += 5f
          if (enemyData.approachingSpeed > 10f) willing += 10f
          if (enemyData.approachingSpeed > 20f) willing += 15f
          if (enemyData.approachingSpeed > 30f) willing += 20f
        }

        // 敌人越近越加成
        if (enemyData.dist < maxWeaponRange - 150f) willing += 10f
        if (enemyData.dist < maxWeaponRange - 300f) willing += 20f
        if (enemyData.dist < maxWeaponRange - 450f) willing += 30f
        if (enemyData.dist < maxWeaponRange - 600f) willing += 40f

        //目标在撤退并且即将脱离
        if (isRetreatingFast) {
          if (enemyData.dist > (maxWeaponRange - 450f)) willing -= 10f
          if (enemyData.dist > (maxWeaponRange - 300f)) willing -= 10f
          if (enemyData.dist > (maxWeaponRange - 150f)) willing -= 20f
        }

        //迎敌角度不能太歪
        if (enemyData.angleDist > 30f) willing -= 10f
        if (enemyData.angleDist > 60f) willing -= 10f
        if (enemyData.angleDist > 90f) willing -= 20f
        if (enemyData.angleDist > 120f) willing -= 30f

      }

      //对于主目标和非主目标，dps越高威胁越大，根据它们的威胁减少意愿度
      // 随着距离增加威胁度降低，900内系数为1，1500为0.75，2000外为0.5
      var distFactor = 1f
      if (enemyData.dist > 900f) {
        distFactor = 1f - ((enemyData.dist - 900f) / 1200f * 0.5f).coerceIn(0f, 0.5f)
      }
      val threatDps = enemyData.threatDps * distFactor
      if (threatDps > 100f) willing -= 1f
      if (threatDps > 500f) willing -= 3f
      if (threatDps > 1000f) willing -= 5f
      if (threatDps > 1500f) willing -= 6f
      if (threatDps > 2000f) willing -= 10f
      if (threatDps > 2500f) willing -= 15f
    }

    //如果最近的敌人也很远，增加意愿度，从900开始，到1500完全加满
    // 如果敌人在900 内，减少意愿度
    // 增加的意愿度和减少的意愿度分别用 farWillingBonus和nearWillingPenalty表示
    if(nearestEnemy != ship) {
      val farWillingBonus = 35f
      val nearWillingPenalty = 50f
      if (nearest > 900f) {
        val farBonus = ((nearest - 900f) / (1500f - 900f) * farWillingBonus).coerceIn(0f, farWillingBonus)
        willing += farBonus
      } else {
        val nearPenalty = ((900f - nearest) / 900f * nearWillingPenalty).coerceIn(0f, nearWillingPenalty)
        willing -= nearPenalty
      }
    }


    //幅能高于50%后随着幅能增加减少意愿度
    // 最多减少fluxWilling点
    val fluxLevel = ship.fluxLevel
    val fluxWilling = 50f
    if(fluxLevel > 0.5f){
      val fluxPenalty = ((fluxLevel.pow(5) - 0.5f) / 0.5f * fluxWilling).coerceIn(0f,fluxWilling)
      willing -= fluxPenalty
    }


    willing *= MathUtils.getRandomNumberInRange(0.9f,1.1f)
    if(willing > 100f) shouldActive = true

    //aEP_Tool.addDebugText("willing: "+willing.toString(),ship.location)
  }

}