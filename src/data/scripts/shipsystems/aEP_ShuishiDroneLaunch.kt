package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.IntervalUtil
import data.scripts.utils.aEP_BaseCombatEffect
import data.scripts.aEP_CombatEffectPlugin
import data.scripts.utils.aEP_Combat
import data.scripts.utils.aEP_ID
import data.scripts.utils.aEP_Tool
import data.scripts.ai.aEP_DroneShieldShipAI
import data.scripts.ai.shipsystemai.aEP_DroneGuardAI
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color
import java.util.ArrayList
import java.util.LinkedList

class aEP_ShuishiDroneLaunch: BaseShipSystemScript() {
  companion object{
    const val ID = "aEP_ShuishiDroneLaunch"
    const val MAX_FIGHTER_AT_SAME = 6
    const val VARIANT_ID = "aEP_ftr_ut_shuishi"
    const val MAX_SPAWN_PER_USE = 4
    const val SYSTEM_RANGE = 1600f
  }


  val spawnTimer = IntervalUtil(0.2f,0.2f)
  var spawned = 0
  val currFighterList = LinkedList<ShipAPI>()

  var guideLineLevel = 0f

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {

    //复制粘贴这行
    val ship = (stats.entity?: return) as ShipAPI
    val amount = aEP_Tool.getAmount(ship)
    //updateDeco(ship, effectLevel,amount)

    val cooldownLevel = MathUtils.clamp((ship.system.cooldownRemaining)/(ship.system.cooldown+0.1f),0f,1f)
    updateIndicator(ship, cooldownLevel)

    for(slot in ship.hullSpec.allWeaponSlotsCopy){
      if(slot.isSystemSlot){
        val loc = slot.computePosition(ship)
        val facing = slot.computeMidArcAngle(ship)
        if(guideLineLevel > 0f){
          updateGuideLine(loc,facing,guideLineLevel)
          //投影轨道完全成型，并且计时器每跳一次，每个系统槽位都刷一个飞机
          //spawned的记数在 createFighter里面，自动的
          if(guideLineLevel >= 1f && spawnTimer.intervalElapsed() && spawned < MAX_SPAWN_PER_USE){
            //落点不可超过系统射程
            var toPoint = ship.mouseTarget
            //如果是ai使用(ship.shipAi != null)，读取母舰的customData，由systemAI放入
            if(ship.customData.containsKey(aEP_ID.SYSTEM_SHIP_TARGET_KEY) && ship.shipAI != null){
              val t = ship.customData[aEP_ID.SYSTEM_SHIP_TARGET_KEY] as ShipAPI
              toPoint.set(MathUtils.getRandomPointInCircle(t.location,t.collisionRadius))
            }else{

            }
            val sysRange = aEP_Tool.getSystemRange(ship, SYSTEM_RANGE)
            if(MathUtils.getDistance(toPoint,ship.location) - ship.collisionRadius > sysRange){
              val angle = VectorUtils.getAngle(ship.location,toPoint);
              toPoint = Vector2f(aEP_Tool.getExtendedLocationFromPoint(ship.location,angle,sysRange+ship.collisionRadius))
            }
            createFighter(loc, facing, toPoint, ship?:continue)
          }
        }
      }
    }

    if(effectLevel <= 0f){
      //guideLineLevel = MathUtils.clamp(guideLineLevel-amount,0f,1f)
      guideLineLevel = 0f
      spawned = 0
      spawnTimer.elapsed = 0f
    }else{
      //guideLineLevel = MathUtils.clamp(guideLineLevel+amount,0f,1f)
      guideLineLevel = effectLevel
      //投影轨道完全成型后，计时器开始读秒
      if(guideLineLevel >=1f) spawnTimer.advance(amount)
    }
  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    //复制粘贴这行
    val ship = (stats.entity?: return) as ShipAPI
    val amount = aEP_Tool.getAmount(ship)
    spawned = 0
    spawnTimer.elapsed = 0f
    //updateDeco(ship, 0f, 0f)
  }

  //这个方法只有在玩家开的时候才会每帧调用，不要在这里取巧
  override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI?): Boolean {
    return super.isUsable(system, ship)
  }

  fun updateDeco(ship: ShipAPI, level:Float, amount:Float){
    val rotatorLevel = (level * 2f).coerceAtMost(1f)
    val rail2Level = ((level - 0.5f)*2f).coerceAtLeast(0f)

    if(rail2Level >= 0.75f) spawnTimer.advance(amount)
    //1f为绿，0f为红
    var systemUseLevel = 1f-level
    var ammoLevel = 1f
    if(ship.system.ammo <= 0){
      ammoLevel = ship.system.ammoReloadProgress
    }
    var cooldownLevel = 1f
    if(ship.system.cooldownRemaining > 0f){
      cooldownLevel = MathUtils.clamp((ship.system.cooldownRemaining/ship.system.cooldown),0f,1f)
      cooldownLevel = 1f-cooldownLevel
    }
    val glowLevel = Math.min(Math.min(ammoLevel, systemUseLevel),cooldownLevel )

    for(w in ship.allWeapons){
      if(!w.isDecorative) continue
      if(w.spec.weaponId.equals("aEP_des_shuishi_base")) {
        val plugin = w.effectPlugin as aEP_DecoAnimation
        plugin.setMoveToLevel(rotatorLevel)
        continue
      }
      if(w.spec.weaponId.equals("aEP_des_shuishi_rotator")) {
        val plugin = w.effectPlugin as aEP_DecoAnimation
        plugin.setRevoToLevel(rotatorLevel)
        continue
      }
      if(w.spec.weaponId.equals("aEP_des_shuishi_rail1")) {
        val plugin = w.effectPlugin as aEP_DecoAnimation
        plugin.setRevoToLevel(rotatorLevel)
        if(spawnTimer.intervalElapsed()) {
          var toPoint = ship.mouseTarget
          //如果是ai使用(ship.shipAi != null)，读取母舰的customData，由systemAI放入
          if(ship.customData.containsKey(aEP_ID.SYSTEM_SHIP_TARGET_KEY) && ship.shipAI != null){
            val t = ship.customData[aEP_ID.SYSTEM_SHIP_TARGET_KEY] as ShipAPI
             toPoint.set(MathUtils.getRandomPointInCircle(t.location,t.collisionRadius))
          }else{

          }

          //落点不可超过系统射程
          val sysRange = aEP_Tool.getSystemRange(ship, SYSTEM_RANGE)
          if(MathUtils.getDistance(toPoint,ship.location) - ship.collisionRadius > sysRange){
            val angle = VectorUtils.getAngle(ship.location,toPoint);
            toPoint = Vector2f(aEP_Tool.getExtendedLocationFromPoint(ship.location,angle,sysRange+ship.collisionRadius))
          }

          createFighter(w.location, w.currAngle, toPoint, w.ship?:continue)
          spawnTimer.advance(0.1f)
        }

        continue
      }
      if(w.spec.weaponId.equals("aEP_des_shuishi_rail2")) {
        val plugin = w.effectPlugin as aEP_DecoAnimation
        plugin.setMoveToLevel(rail2Level)
        plugin.setRevoToLevel(rotatorLevel)
        val startLoc = aEP_Tool.getExtendedLocationFromPoint(w.location,w.currAngle, 30f)
        updateGuideLine(startLoc,w.currAngle,rail2Level)
        continue
      }
      if(w.spec.weaponId.equals("aEP_des_shuishi_rail_red")) {
        val plugin = w.effectPlugin as aEP_DecoAnimation
        plugin.setGlowToLevel(1f- glowLevel)
        plugin.setRevoToLevel(rotatorLevel)
        continue
      }
      if(w.spec.weaponId.equals("aEP_des_shuishi_rail_green")) {
        val plugin = w.effectPlugin as aEP_DecoAnimation
        plugin.setGlowToLevel(glowLevel)
        plugin.setRevoToLevel(rotatorLevel)
        continue
      }
    }
  }

  fun updateGuideLine(startLoc:Vector2f, facing:Float, level:Float){
    val step = 10f
    val num = 4
    for( i in 0 until num){
      //越近level越高，先渲染
      val renderLevel = MathUtils.clamp((level - i.toFloat()/num.toFloat()) * num,0f,1f)
      val renderLoc = aEP_Tool.getExtendedLocationFromPoint(startLoc,facing,step*i)
      val a = (renderLevel/0.33f).coerceAtMost(1f)
      val colorChangeLevel = ((renderLevel-0.67f)*3f).coerceAtLeast(0f)
      val renderC = Color(1f-colorChangeLevel,colorChangeLevel,0f, a)
      //需要改变颜色和透明度时，必须使用不同的sprite实体，否则改一个全改
      val sprite = Global.getSettings().getSprite("aEP_FX","forward")
      //渲染原图
      MagicRender.singleframe(
        sprite,
        MathUtils.getRandomPointInCircle(renderLoc,0.3f*level),
        Vector2f(16f,8f),
        facing-90f,
        renderC,
        true,
        CombatEngineLayers.ABOVE_SHIPS_LAYER)

    }
  }

  fun updateIndicator(ship: ShipAPI, level:Float){

    for(w in ship.allWeapons){
      if(w.slot.id.equals("INDICATE 000")){
        val animation = w.effectPlugin as aEP_DecoAnimation
        if(level > 0.65f){
          animation.decoGlowController.toLevel = 1f
        }else{
          animation.decoGlowController.toLevel = 0f
        }

      }
      if(w.slot.id.equals("INDICATE 001")){
        val animation = w.effectPlugin as aEP_DecoAnimation
        if(level > 0.35f){
          animation.decoGlowController.toLevel = 1f
        }else{
          animation.decoGlowController.toLevel = 0f
        }
      }
      if(w.slot.id.equals("INDICATE 002")){
        val animation = w.effectPlugin as aEP_DecoAnimation
        if(level > 0f){
          animation.decoGlowController.toLevel = 1f
        }else{
          animation.decoGlowController.toLevel = 0f
        }
      }
    }
  }

  fun createFighter(loc:Vector2f, facing: Float, toPoint:Vector2f ,source:ShipAPI){
    if(spawned >= MAX_SPAWN_PER_USE) return
    spawned += 1

    updateFighterList()
    if(currFighterList.size >= MAX_FIGHTER_AT_SAME){
      for(f in currFighterList){
        if(f.customData?.containsKey(aEP_Combat.RecallFighterJitter.ID) != true){
          val despawn = Despawn(f)
          aEP_CombatEffectPlugin.addEffect(despawn)
          break
        }
      }
    }

    //闪一下，盖住战机出生
    //闪光
    Global.getCombatEngine().addSmoothParticle(
      loc,
      aEP_ID.VECTOR2F_ZERO,
      100f,1f,0.1f,0.25f,Color.white)
    aEP_Tool.spawnCompositeSmoke(loc, 50f, 2f,  Color(250, 250, 250, 105),aEP_ID.VECTOR2F_ZERO)
    aEP_Tool.spawnCompositeSmoke(loc, 100f, 3f,  Color(150, 150, 150, 105),aEP_ID.VECTOR2F_ZERO)


    //用fxDrone是没有自带ai的
    val variant = Global.getSettings().getVariant(VARIANT_ID)
    val drone = Global.getCombatEngine().createFXDrone(variant)
    //并不是特效无人机，而是实体，去掉这个tag。这个tag会被createFXDrone()自动添加
    drone.tags.remove(Tags.VARIANT_FX_DRONE)
    drone.isDrone = true
    drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f, source)
    drone.layer = CombatEngineLayers.FIGHTERS_LAYER
    drone.owner = source.owner
    drone.location.set(loc)
    drone.facing = facing
    Global.getCombatEngine().addEntity(drone)
    val ai = aEP_DroneShieldShipAI(drone.fleetMember, drone)
    val systemAi = aEP_DroneGuardAI()
    systemAi.init(drone, drone.system, ai.aiFlags, Global.getCombatEngine())
    ai.systemAI = systemAi
    drone.shipAI = ai

    currFighterList.add(drone)

    //找到距离toPoint最近的友军
    var closest: ShipAPI? = null
    var distance: Float
    var closestDistance = Float.MAX_VALUE
    for (tmp in AIUtils.getAlliesOnMap(source)) {
      if(aEP_Tool.isDead(tmp)) continue
      if(!aEP_Tool.isShipTargetable(
          tmp,
          true,true,false,true,false))continue
      if (aEP_Tool.isEnemy(source,tmp)) continue
      distance = MathUtils.getDistance(tmp, toPoint)
      if (distance < closestDistance) {
        closest = tmp
        closestDistance = distance
      }
    }
    if(closest is ShipAPI){
      val state = ai.ProtectParent(closest)
      state.forceTag = true
      ai.stat = state
    }

    //生成proj，绑定飞机
    val proj = Global.getCombatEngine().spawnProjectile(
      source, null,"aEP_cru_pubu_main",
      loc,facing,null) as DamagingProjectileAPI
    aEP_CombatEffectPlugin.addEffect(Speeding(0.15f,drone, proj, toPoint))
  }

  fun updateFighterList(){
    val toRemoveList = ArrayList<ShipAPI>()
    for(f in currFighterList){
      if(aEP_Tool.isDead(f)){
        toRemoveList.add(f)
      }
    }
    currFighterList.removeAll(toRemoveList)
  }

  inner class Despawn(val f:ShipAPI) : aEP_Combat.RecallFighterJitter(1f, f){
    override fun onRecall() {
      currFighterList.remove(f)
      Global.getCombatEngine().removeEntity(entity)
    }


  }

  class Speeding: aEP_BaseCombatEffect {
    val max_speed_flat_bonus = 600f
    val max_turn_rate_mult = 0f

    val id = "aEP_Speeding"
    var fighter:ShipAPI
    var teleportTo:Vector2f? = null

    constructor(lifeTime: Float,fighter:ShipAPI, proj:CombatEntityAPI,  teleportTo:Vector2f?){
      this.lifeTime = lifeTime
      this.fighter = fighter
      this.teleportTo?.set(teleportTo)
      init(proj)


      fighter.mutableStats.maxSpeed.modifyFlat(id, max_speed_flat_bonus)
      fighter.mutableStats.armorDamageTakenMult.modifyMult(id , aEP_FighterLaunch.DAMAGE_TAKEN_WHEN_SPEEDING)
      fighter.mutableStats.hullDamageTakenMult.modifyMult(id , aEP_FighterLaunch.DAMAGE_TAKEN_WHEN_SPEEDING)
      fighter.mutableStats.maxTurnRate.modifyMult(id, max_turn_rate_mult)
      fighter.location.set(proj.location)
      fighter.facing = proj.facing
    }

    override fun advanceImpl(amount: Float) {
      //proj是entity，所以proj消失后本buff自动结束，不需要检测
      //如果fighter失效，也要结束本buff
      if(aEP_Tool.isDead(fighter)){
        shouldEnd = true
        return
      }
      val proj = entity as CombatEntityAPI
      //同步位置和速度
      fighter.facing = proj.facing
      fighter.velocity.set(proj.velocity)
      fighter.location.set(proj.location)

      fighter.setJitter(id, Color.RED, 1f, 1, 0f)

      fighter.giveCommand(ShipCommand.ACCELERATE,null,0)
      fighter.blockCommandForOneFrame(ShipCommand.FIRE)
      fighter.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS)
      fighter.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT)
      fighter.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT)
      fighter.blockCommandForOneFrame(ShipCommand.DECELERATE)


//      //本buff最多还剩0.5，传送需要1秒，所以传送结束时本buff已经结束了，不会被强制拉回来
//      if(lifeTime - time < 0.5f && !fighter.customData.containsKey(aEP_Combat.StandardTeleport.ID)){
//        val tel = aEP_Combat.StandardTeleport(0.5f,fighter,teleportTo,fighter.facing)
//        aEP_CombatEffectPlugin.addEffect(tel)
//      }

    }

    override fun readyToEnd() {
      //注意当玩家操控时，shipAI为null
      fighter.shipAI?.cancelCurrentManeuver()
      fighter.shipAI?.forceCircumstanceEvaluation()

      fighter.mutableStats.maxSpeed.unmodify(id)
      fighter.mutableStats.armorDamageTakenMult.unmodify(id)
      fighter.mutableStats.hullDamageTakenMult.unmodify(id)
      fighter.mutableStats.maxTurnRate.unmodify(id)

      fighter.removeCustomData("aEP_RecallFighter")

      Global.getCombatEngine().removeEntity(entity)
    }
  }


}
