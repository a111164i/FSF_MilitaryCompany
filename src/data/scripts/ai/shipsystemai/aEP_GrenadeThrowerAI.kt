package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import data.scripts.utils.aEP_Tool
import data.scripts.weapons.aEP_des_lianliu_grenade_thrower_shot
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.pow

class aEP_GrenadeThrowerAI: aEP_BaseSystemAI() {

  var WEAPON_RANGE = 400f

  override fun initImpl() {
    thinkTracker.setInterval(0.05f,0.15f)
    WEAPON_RANGE = Global.getSettings().getWeaponSpec(aEP_des_lianliu_grenade_thrower_shot.ID.replace("_shot","")).maxRange
  }


  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false

    var willing = 0f
    var willingAttack = 0f

    //当前充能越多，越随意用来防空
    val numOfCharge = ship.system.ammo
    when (numOfCharge){
      4-> willing += 40f
      3-> willing += 20f
      2-> willing += 5f
      1-> willing += -5f
    }
    when (numOfCharge){
      4-> willingAttack += 10f
      3-> willingAttack += 0f
      2-> willingAttack += 0f
      1-> willingAttack += -10f
    }


    //过滤出在覆盖范围内的全部导弹，统计一个f可以消灭的导弹的总伤害
    var totalDamage = 0f
    val weaponRangeSq = (WEAPON_RANGE).pow(2)
    if(missileDangerDir != null){
      for(m in Global.getCombatEngine().missiles){
        if(!aEP_Tool.isEnemy(ship,m)) continue
        if(aEP_Tool.isDead(m)) continue
        val distSq = MathUtils.getDistanceSquared(ship,m)
        if(distSq > weaponRangeSq) continue
        val angleDistToFront = MathUtils.getShortestRotation(ship.facing, VectorUtils.getAngle(ship.location,m.location))
        if(angleDistToFront > 80f || angleDistToFront < -80f) continue
        totalDamage += m.damageAmount
      }
    }
    //如果总伤害超过2000f，使用系统
    val thresholdDamageSq = 2000f.pow(2)
    willing += (totalDamage.pow(2)/thresholdDamageSq) * 100f



    //无论有没有目标，船头有飞机都可以重拳出击
    var totalFighters = 0f
    if(missileDangerDir != null){
      for(s in Global.getCombatEngine().ships){
        if(!aEP_Tool.isEnemy(ship,s)) continue
        if(aEP_Tool.isDead(s)) continue
        if(!aEP_Tool.isShipTargetable(
            s,false, false,
            false,false,true)) return
        if(!s.isFighter) continue
        val distSq = MathUtils.getDistanceSquared(ship,s)
        if(distSq > weaponRangeSq) continue
        val angleDistToFront = MathUtils.getShortestRotation(ship.facing, VectorUtils.getAngle(ship.location,s.location))
        if(angleDistToFront > 80f || angleDistToFront < -80f) continue
        totalFighters += s.hitpoints
        totalFighters += s.armorGrid.armorRating * 2f
        if(s.shield != null){
          totalFighters += (s.maxFlux-s.currFlux) * s.shield.fluxPerPointOfDamage
        }
      }
    }
    //如果船头战机的总血量伤害超过1500f，使用系统
    val totalFightersThreshold = 1500f.pow(2)
    willing += (totalFighters.pow(2)/totalFightersThreshold) * 100f

    //判断当前目标和自己的距离，越近越想用
    var target: ShipAPI? = null
    if(ship.shipTarget != null){
      target = ship.shipTarget
    }

    if(ship.aiFlags != null){
      if( ship.aiFlags.getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET) is ShipAPI)  {
        val moveT = ship.aiFlags.getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET) as ShipAPI
        if(aEP_Tool.isEnemy(ship,moveT)){
          target = moveT
        }
      }

      //任何情况ai觉得需要停止靠近，取消进攻性使用
      if( ship.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF))  {
        //aEP_Tool.addDebugLog("BACK_OFF")
        target = null
      }

      //任何情况ai觉得需要停止靠近，取消进攻性使用
      if( ship.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.BACK_OFF_MIN_RANGE))  {
        //aEP_Tool.addDebugLog("BACK_OFF_MIN_RANGE")
        target = null
      }
    }


    //寻找到舰船目标或者移动目标，根据距离判断要不要进攻性使用
    if(target != null){
      if(aEP_Tool.isEnemy(ship, target)) {
        val angleDistToFront = MathUtils.getShortestRotation(ship.facing, VectorUtils.getAngle(ship.location,target.location))
        if(angleDistToFront < 60f && angleDistToFront > -60f){
          val distSq = MathUtils.getDistanceSquared(ship, target)
          if (distSq <= weaponRangeSq) {
            val distSqLevel = distSq/(weaponRangeSq)
            //根据距离，越近就增加进攻使用欲望
            willingAttack += 100f * (1f-distSqLevel)
            //aEP_Tool.addDebugLog("base willing: " + willingAttack.toString())
            //距离小于武器距离的1/3，直接激活，最快速度打空
            if(distSq <= weaponRangeSq/9f)
              shouldActive = true
          }
        }
      }
    }

    willing += MathUtils.getRandomNumberInRange(-25f,15f)
    willingAttack += MathUtils.getRandomNumberInRange(-25f,50f)


    //aEP_Tool.addDebugLog("atk willing: " + willing.toString())
    if(willing > 100f) shouldActive = true
    if(willingAttack > 100f) shouldActive = true
  }
}