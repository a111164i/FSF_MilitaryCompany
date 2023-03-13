package data.shipsystems.scripts.ai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import combat.util.aEP_Tool
import data.shipsystems.scripts.aEP_FighterLaunch
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.abs

class aEP_FighterLaunchAI:aEP_BaseSystemAI(){

  override fun initImpl() {
    thinkTracker.setInterval(1f,1f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    if(ship.fullTimeDeployed < 10f) return

    var willingToUse = 0f

    //如果ai不想出动战机，判死刑
    val isPullbackFighters = ship.isPullBackFighters
    if (isPullbackFighters) willingToUse -= 100f

    //不装战机时默认不激活，所以分子为0
    var fighterOpAround = 0f
    var totalFighterOp = 1f

    //计算多少比例的战机处于可召回范围内
    for(bay in ship.launchBaysCopy){
      bay?: continue
      bay.wing?:continue
      if(!aEP_FighterLaunch.isValidWing(bay.wing)) continue
      for(fighter in bay.wing?.wingMembers?:continue){
        if(fighter?.shipAI?.needsRefit() == true) continue
        val range = MathUtils.getDistance(ship.location,fighter.location)
        val fighterOp = Global.getSettings().getFighterWingSpec(fighter.wing.wingId).getOpCost(null)/fighter.wing.spec.numFighters
        if(range < aEP_Tool.getSystemRange(ship, aEP_FighterLaunch.Companion.RECALL_RANGE - 100f)){
          fighterOpAround +=fighterOp
        }
      }
      totalFighterOp += bay.wing?.spec?.getOpCost(null)?:1f
    }

    //根据可发射战机数量计算willing
    val x= fighterOpAround/totalFighterOp
    if(x > 0.8f){
      willingToUse += 100f
    }else if(x <= 0.8f && x > 0.6f){
      willingToUse += 75f
    }else if(x <= 0.6f && x > 0.35f){
      willingToUse += 10f
    }  else if(x <= 0.35f){
      willingToUse += -100f
    }
    //aEP_Tool.addDebugLog("amount: "+willingToUse.toString())

    //计算是否有敌人位于射界
    //根据第一个进入射界敌人的远近计算willing
    val maxSearchRange = 3000f
    val endPoint = aEP_Tool.getExtendedLocationFromPoint(ship.location,ship.facing,maxSearchRange)
    var t:ShipAPI? = null
    for(enemy in AIUtils.getNearbyEnemies(ship,maxSearchRange)){
      //对于超过60度以外的敌人不计算碰撞，减少碰撞的计算量
      val enemyAngle = VectorUtils.getAngle(ship.location,enemy.location)
      if(Math.abs(MathUtils.getShortestRotation(ship.facing,enemyAngle)) > 60f ) continue
      if(CollisionUtils.getCollisionPoint(ship.location,endPoint,enemy) != null) {
        val dist = MathUtils.getDistance(ship.location, enemy.location)
        //根据距离计算willing
        if(dist > 4000f){
          willingToUse += 25f
        } else if(dist < 3500f && dist > 2500f){
          willingToUse += 50f
        }else if(dist < 2500f && dist > 1600f){
          willingToUse += 100f
        }else if (dist< 1600 && dist >1200){
          willingToUse += 75f
        }else if (dist< 1200 && dist > 600){
          willingToUse += 0f
        } else if (dist < 600){
          willingToUse += -100f
        }
        t = enemy
        break
      }
    }

    //如果本体足够安全，幅能水平低，就肆意乱射
    if(ship.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.SAFE_FROM_DANGER_TIME) || ship.fluxLevel < 0.1f){
      willingToUse += 99f
    }

    if(ship.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) willingToUse -= 25f
    if(ship.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP)) willingToUse -= 50f

    willingToUse *= MathUtils.getRandomNumberInRange(0.75f,1.25f)

    shouldActive = false
    if(willingToUse >= 125f){
      shouldActive = true
      ship.isPullBackFighters = true
    }
  }
}