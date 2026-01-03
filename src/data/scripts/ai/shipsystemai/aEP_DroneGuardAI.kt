package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import data.scripts.utils.aEP_BaseCombatEffect
import data.scripts.aEP_CombatEffectPlugin.Mod.addEffect
import data.scripts.utils.aEP_Combat
import data.scripts.utils.aEP_Tool
import data.scripts.shipsystems.aEP_DroneGuard.Companion.MAX_DIST
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.pow

class aEP_DroneGuardAI: aEP_BaseSystemAI() {
  companion object{
    const val ALERT_TIME = 0.9f
    const val ID = "aEP_DroneGuardAI"
    const val THRESHOLD = 450f
    const val BLINK_TIME_BEFORE_HIT = 0.25f
  }

  var currProj:DamagingProjectileAPI? = null
  var currBeam:BeamAPI? = null

  override fun initImpl() {
    thinkTracker.setInterval(0.01f,0.05f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false
    val systemRange = aEP_Tool.getSystemRange(ship,MAX_DIST)
    val nearShips = AIUtils.getNearbyAllies(ship, systemRange)
    val nearEntities = CombatUtils.getEntitiesWithinRange(ship.location, systemRange * 1.5f)

    //每帧更新一次自己碰撞点的绝对坐标（如果不更新，显示的是相对坐标）
    ship.visualBounds?.update(ship.location,ship.facing)
    ship.exactBounds?.update(ship.location,ship.facing)

    //检查是否可以拦截弹丸
    for(proj in nearEntities){
      //这里的得到的是全部entity，先过滤一道只剩下弹丸
      if(proj !is DamagingProjectileAPI) continue
      val proj = proj as DamagingProjectileAPI
      //排除无效的拦截对象，比如伤害太低的弹丸，或者已经是友军拦截对象的弹丸
      if(proj.customData.containsKey(ID)) continue
      //modifier基础值是1
      var damageAmount = proj.damage.modifier.modifiedValue * proj.damage.damage
      when(proj.damage.type){
        DamageType.ENERGY-> damageAmount  *= 0.75f
        DamageType.KINETIC-> damageAmount *= 0.75f
        DamageType.FRAGMENTATION-> damageAmount *= 0.4f
        else -> {}
      }
      if(damageAmount < THRESHOLD) continue

      val angleAndVel = aEP_Tool.velocity2Speed(proj.velocity)
      val hitPoint = aEP_Tool.getExtendedLocationFromPoint(proj.location, angleAndVel.x, angleAndVel.y * ALERT_TIME)
      for(s in nearShips){

        //排除无效的保护对象，比如不要保护飞机，友军射出的弹丸
        if(s.isDrone) continue
        if(s.isFighter) continue
        if(s.collisionClass == CollisionClass.NONE) continue
        if(s.owner == proj.owner) continue
        //排除已经进入友军碰撞圈的导弹，不可能拦截了
        //这个需要用于计算拦截点，不能只算Sq
        val distProj2Target = MathUtils.getDistance(proj.location, s.location)
        if(distProj2Target <= s.collisionRadius) continue

        //如果目标弹丸会在飞行ALERT_TIME后划过任意友军的碰撞圈，启动系统
        val willHit = CollisionUtils.getCollides(proj.location, hitPoint,s.location, s.collisionRadius)
        if(willHit){
          //把第一位置定到目标弹丸飞行BLINK_TIME_BEFORE_HIT后的位置
          val interceptPoint = aEP_Tool.getExtendedLocationFromPoint(
            proj.location,
            angleAndVel.x,
            BLINK_TIME_BEFORE_HIT * angleAndVel.y + ship.collisionRadius + 20f)
          //如果目标弹丸飞的太快，第一位置已经处于保护友军碰撞圈的范围内了，就使用第二位置，直接在撞在导弹上
          val distPoint2TargetSq = MathUtils.getDistanceSquared(interceptPoint, s.location)
          if(distPoint2TargetSq <= (s.collisionRadius).pow(2)) {
            interceptPoint.set(aEP_Tool.getExtendedLocationFromPoint(
              proj.location,
              angleAndVel.x,
              20f))
          }

          val distInterceptPoint2ShipSq = MathUtils.getDistanceSquared(interceptPoint, ship.location)
          if(distInterceptPoint2ShipSq < aEP_Tool.getSystemRange(ship, MAX_DIST).pow(2)){
            ship.mouseTarget.set(interceptPoint)
            //每个弹丸不会重复被拦截
            addEffect(aEP_Combat.MarkTarget(2f,ID,1f,proj))
            currProj = proj
            shouldActive = true
            return
          }
        }
      }
    }


    //检查是否可以拦截光束
    val allBeams = engine.beams
    for(beam in allBeams){
      if(beam.damageTarget !is ShipAPI) continue
      val damageTarget = beam.damageTarget as ShipAPI
      //光束必须有damageTarget，且是个友军
      if((damageTarget.owner) != ship.owner) continue
      if(damageTarget.isDrone) continue
      if(damageTarget.isFighter) continue
      if(damageTarget.collisionClass == CollisionClass.NONE) continue

      //光束伤害太低不触发
      var d = beam.damage.damage
      when(beam.damage.type){
        DamageType.KINETIC -> d *= 0.75f
        DamageType.HIGH_EXPLOSIVE -> d *= 1f
        DamageType.ENERGY -> d *= 0.8f
        DamageType.FRAGMENTATION -> d *= 0.5f
        else -> {}
      }
      if(beam.damage.damage < THRESHOLD) continue
      //beamAPI没有customData哦，把数据存在engine里面，这个光束不能是其他队友的目标


      if(engine.customData[ID] is ArrayList<*>
        && (engine.customData[ID] as ArrayList<*>).contains(beam) )continue

      //光束的起始点必须要在目标碰撞圈的外面至少75f，太近了视觉效果看不出拦了激光
      val from2Target = MathUtils.getDistance(beam.from, beam.damageTarget.location)
      val extraRange = 75f
      if(from2Target <= beam.damageTarget.collisionRadius + extraRange) continue
      CollisionUtils.getCollisionPoint(beam.from, beam.to, beam.damageTarget)

      //找到光束和碰撞圈的交点外延100f，太贴近
      val facingTarget2From = VectorUtils.getAngle(beam.damageTarget.location, beam.from)
      val hitPoint = aEP_Tool.getExtendedLocationFromPoint(
        beam.damageTarget.location,
        facingTarget2From,
        beam.damageTarget.collisionRadius + extraRange)
      //交点需要在系统范围内
      if(MathUtils.getDistance(ship.location, hitPoint) > systemRange) continue
      ship.mouseTarget.set(hitPoint)

      //把beam存入engine的customData中，因为beam不自带，所有为了防止内存泄露同时加入1秒后从customData中移除的脚本
      engine.customData[ID]?: let {
        val list = ArrayList<BeamAPI>()
        engine.customData.set(ID, list )
      }
      (engine.customData[ID] as ArrayList<BeamAPI>).add(beam)
      addEffect(RemoveBeamFromListAfter(beam, 1f))

      currBeam = beam
      shouldActive = true
      return

    }

    //拦截结束以后解除弹丸已经被锁定的状态，可以被其他ai选中
    if(system?.state === ShipSystemAPI.SystemState.IDLE && (currProj != null || currBeam != null) ){
      currProj?.removeCustomData(ID)
      currProj = null
      currBeam = null
    }

  }


  class RemoveBeamFromListAfter(val beam :BeamAPI, delay:Float) : aEP_BaseCombatEffect(delay){
    override fun readyToEnd() {
      Global.getCombatEngine().customData[ID]?: let {
        val list = ArrayList<BeamAPI>()
        Global.getCombatEngine().customData.set(ID, list )
      }
      (Global.getCombatEngine().customData[ID] as ArrayList<BeamAPI>).remove(beam)
    }
  }
}