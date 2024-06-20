package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.util.IntervalTracker
import combat.impl.VEs.aEP_MovingSmoke
import combat.plugin.aEP_CombatEffectPlugin
import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import combat.util.aEP_AngleTracker
import combat.util.aEP_DataTool.txt
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.speed2Velocity
import combat.util.aEP_Tool.Util.getAmount
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.getTargetWidthAngleInDistance
import combat.util.aEP_Tool.Util.getWeaponOffsetInAbsoluteCoo
import combat.util.aEP_Tool.Util.isNormalWeaponSlotType
import data.scripts.hullmods.aEP_ReactiveArmor
import data.scripts.hullmods.aEP_Strafe
import data.scripts.weapons.aEP_DecoAnimation
import org.dark.shaders.light.LightShader
import org.dark.shaders.light.StandardLight
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.MathUtils.clamp
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import org.magiclib.util.MagicUI
import java.awt.Color
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.max

class aEP_WeaponReset: BaseShipSystemScript() {

  companion object{

    const val CUSTOM_DATA_KEY = "aEP_WeaponResetBuffer"
    //系统结束时环绕烟雾颜色
    val SMOKE_COLOR = Color(200, 200, 200, 60)
    //排气口喷出烟雾的颜色
    val SMOKE_EMIT_COLOR = Color(250, 250, 250, 160)
    val GLOW_COLOR = Color(255,72,44,118)

    //缓冲区大小是最大容量的几倍
    private val MAX_FLUX_STORE_CAP_PERCENT = 2f
    //返还时，有多少的幅能被直接耗散掉无需返回
    private val FLUX_VENT_PERCENT_ON_RETURN= 0.1f
    private val SHIELD_DAMAGE_REDUCE_MULT = 0.1f
    private val FLUX_DECREASE_PERCENT: MutableMap<String, Float> = HashMap()
    private val FLUX_DECREASE_FLAT: MutableMap<String, Float> = HashMap()
    private val FLUX_RETURN_SPEED: MutableMap<String, Float> = HashMap()
    //武器的射速和装弹速度百分比加成
    private val WEAPON_ROF_PERCENT_BONUS: MutableMap<String, Float> = HashMap()
    init {

      //吸收幅能的速度
      FLUX_DECREASE_PERCENT["aEP_cru_zhongliu"] = 0.25f
      FLUX_DECREASE_FLAT["aEP_cru_zhongliu"] = 600f

      //返回幅能速度是耗散的几分之一
      FLUX_RETURN_SPEED["aEP_cru_zhongliu"] = 0.75f

      WEAPON_ROF_PERCENT_BONUS["aEP_cru_zhongliu"] = 100f
    }

    fun readStoredLevel(ship: ShipAPI): Float{
      if(ship.customData.containsKey(CUSTOM_DATA_KEY)){
        return ship.customData[CUSTOM_DATA_KEY] as Float
      }
      return 0f
    }
  }

  var didActive = false
  private var ship: ShipAPI? = null
  private var storedSoftFlux = 0f
  private var bufferSize = 1f
  private val presmokeTracker = IntervalTracker(0.05f,0.05f)

  private val redSmokeTracker = IntervalUtil(0.1f, 0.2f)
  var timeElapsedAfterVenting = 0f

  val decoTracker =  aEP_AngleTracker(0f,0f,0.75f,1f,0f)

  //runInIdle == true, unapply()只有在被外界强制关闭时才会调用
  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制粘贴这行
    ship = (stats?.entity?: return) as ShipAPI
    val ship = ship as ShipAPI
    if (!ship.isAlive) return

    bufferSize = ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT
    val bufferLvl = (storedSoftFlux/bufferSize).coerceAtMost(1f)
    val amount = getAmount(ship)

    //舰船额外ui
    if (Global.getCombatEngine().playerShip == ship) {
      MagicUI.drawHUDStatusBar(ship,
        bufferLvl, null,null, 0f,
        ship.system.displayName, String.format("%.1f",bufferLvl*100f)+"%",true )
    }


    //激活中
    if(state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE || state == ShipSystemStatsScript.State.OUT){
      //执行一次
      if(!didActive){
        didActive = true
        //把presmokeTracker加满，保证进入DOWN时第一帧就会出presmoke
        presmokeTracker.advance(999f)
      }

      //ACTIVE和IN的时候吸收幅能
      if( state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE){
        //计算当前软硬幅能和对应船体的软幅能缓冲速度
        val hard = (ship.fluxTracker.hardFlux).coerceAtLeast(0f)
        val soft = (ship.fluxTracker.currFlux - hard).coerceAtLeast(0f)
        val speedPercent = FLUX_DECREASE_PERCENT[ship.hullSpec.baseHullId]?:0.5f
        val speedFlat = FLUX_DECREASE_FLAT[ship.hullSpec.baseHullId]?:150f

        //监测缓冲区满了没有，满了就强制关闭系统
        //软幅能散完了也是
        if(storedSoftFlux >= bufferSize-1f){
          ship.system.deactivate()
          ship.system.cooldownRemaining = ship.system.cooldown
        }

        //吸收幅能，速度为 幅能的百分比 + 固定值
        var toReturnThisFrame = (speedPercent * soft + speedFlat)* amount
        //aEP_Tool.addDebugLog(ship.system.chargeActiveDur.toString())
        if(soft > 0){
          //限制吸收的软幅能量不超过剩余软幅能
          var toReduce = toReturnThisFrame.coerceAtMost(soft)
          ship.fluxTracker.increaseFlux(-toReduce,false)
          toReturnThisFrame -= toReduce
          storedSoftFlux += toReduce
        }

        //在激活时，从排幅口喷出短的烟雾
        if(presmokeTracker.intervalElapsed()) {
          for (w in ship.allWeapons) {
            if (!w.slot.isDecorative) continue
            if (!w.spec.weaponId.contains("aEP_cru_zhongliu_side_glow")) continue
            val smokeLoc = w.location
            val smoke = aEP_MovingSmoke(smokeLoc)
            smoke.lifeTime = 0.35f
            smoke.fadeIn = 0.5f
            smoke.fadeOut = 0.5f
            smoke.size = 20f
            smoke.sizeChangeSpeed = 100f
            smoke.color = SMOKE_EMIT_COLOR
            smoke.setInitVel(speed2Velocity(w.currAngle, 300f))
            smoke.stopForceTimer.setInterval(0.05f, 0.05f)
            smoke.stopSpeed = 0.975f
            addEffect(smoke)
          }
        }
        //后于检测，保证之前加满的第一帧能进去
        presmokeTracker.advance(getAmount(ship))
      }

      //DOWN的时候释放四周喷长烟雾特效
      if(state == ShipSystemStatsScript.State.OUT){
        if(presmokeTracker.intervalElapsed()) {
          for (w in ship.allWeapons) {
            if (!w.slot.isDecorative) continue
            if (!w.spec.weaponId.equals("aEP_cru_zhongliu_side_glow")) continue
            val smokeLoc = w.location
            val smoke = aEP_MovingSmoke(smokeLoc)
            smoke.lifeTime = 0.65f
            smoke.fadeIn = 0.25f
            smoke.fadeOut = 0.25f
            smoke.size = 20f
            smoke.sizeChangeSpeed = 100f
            smoke.color = SMOKE_EMIT_COLOR
            smoke.setInitVel(speed2Velocity(w.currAngle, 250f))
            smoke.stopForceTimer.setInterval(0.05f, 0.05f)
            smoke.stopSpeed = 0.95f
            addEffect(smoke)
          }
        }
        //后于检测，保证之前加满的第一帧能进去
        presmokeTracker.advance(aEP_Tool.getAmount(ship))
        //玩个梗，降低光束伤害
        ship.mutableStats.beamDamageTakenMult.modifyMult(id,0.2f)
      }

      //给武器打粒子
      ship.setWeaponGlow(
        effectLevel,
        GLOW_COLOR,
        EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.ENERGY))

      //武器修复
      stats.combatWeaponRepairTimeMult.modifyMult(id, 0.1f)

      val rofPercentBonus = WEAPON_ROF_PERCENT_BONUS[ship.hullSpec.baseHullId]?: 100f

      //ballistic weapon buff
      stats.ballisticRoFMult.modifyPercent(id, rofPercentBonus * effectLevel)
      stats.ballisticAmmoRegenMult.modifyPercent(id, rofPercentBonus * effectLevel)

      //energy weapon buff
      stats.energyRoFMult.modifyPercent(id, rofPercentBonus * effectLevel)
      stats.energyAmmoRegenMult.modifyPercent(id, rofPercentBonus * effectLevel)

      stats.shieldDamageTakenMult.modifyMult(id, 1f - SHIELD_DAMAGE_REDUCE_MULT * effectLevel)

      //取消护盾维持，防止出现把盾维吸入导致每秒幅散散了个空气的问题
      ship.mutableStats.shieldUpkeepMult.modifyMult(id,0f)

    }
    else{  //系统为IDLE时
      //激活结束后运行一次
      if(didActive){
        unapply(stats, id)
      }

      //返还幅能
      //默认全速返还
      var toReturnThisFrame = aEP_Tool.getRealDissipation(ship) * getAmount(ship)
      //如果当前幅能不为空，慢速返还
      if(ship.fluxTracker.fluxLevel > 0.01f) toReturnThisFrame *= (FLUX_RETURN_SPEED[ship.hullSpec.baseHullId]?:1f)

      if(ship.fluxTracker.isVenting) toReturnThisFrame = 0f;
      if(storedSoftFlux > 0){
        //限制返还软幅能量不超过剩余容量和储存的软幅能量
        var toAdd = toReturnThisFrame.coerceAtMost(storedSoftFlux)
        toAdd = toAdd.coerceAtMost(ship.maxFlux - ship.currFlux)
        ship.fluxTracker.increaseFlux(toAdd,false)
        //每秒最大返还量 - 软幅能返还量 = 剩下给硬幅能的量
        toReturnThisFrame -= toAdd
        storedSoftFlux -= toAdd * (1f + FLUX_VENT_PERCENT_ON_RETURN)
        storedSoftFlux = storedSoftFlux.coerceAtLeast(0f)
      }
    }

    //增减进入venting后的时间
    if(ship.fluxTracker.isVenting || state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE || state == ShipSystemStatsScript.State.OUT){
      decoTracker.to = 1f
    } else{
      decoTracker.to = 0f
    }

    ship.setCustomData(CUSTOM_DATA_KEY, storedSoftFlux/bufferSize)

    //控制装饰武器
    decoTracker.advance(amount)
    val decoLevel = decoTracker.curr

    updateDecos(ship,decoLevel, amount)
    updateHeadDecos(ship,decoLevel)
    updateBottomIndicators(ship)

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String?) {
    //复制粘贴这行
    ship = (stats.entity?: return) as ShipAPI
    val ship = ship as ShipAPI

    didActive = false
    //ballistic weapon buff
    stats.ballisticRoFMult.unmodify(id)
    stats.ballisticAmmoRegenMult.unmodify(id)

    //energy weapon buff
    stats.energyRoFMult.unmodify(id)
    stats.energyAmmoRegenMult.unmodify(id)

    //flux consume reduce
    stats.ballisticWeaponFluxCostMod.unmodify(id)
    stats.energyWeaponRangeBonus.unmodify(id)

    //取消玩梗的光束伤害减免
    stats.beamDamageTakenMult.unmodify(id)

    //还原护盾维持
    ship.mutableStats.shieldUpkeepMult.unmodify(id)

    //取消武器维修
    stats.combatWeaponRepairTimeMult.unmodify(id)

    spawnSmoke(ship, 30)

    ship.setWeaponGlow(
      0f,
      GLOW_COLOR,
      EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.ENERGY))

  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State?, effectLevel: Float): StatusData? {
    val ship = (ship?:return null)
    if(state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE || state == ShipSystemStatsScript.State.OUT) {
      if (index == 0) {
        val rofPercentBonus = WEAPON_ROF_PERCENT_BONUS[ship.hullSpec.baseHullId]?: 100f
        return  StatusData(txt("aEP_WeaponReset01")+": "+ String.format("+%.0f",rofPercentBonus)+"%", false)
      }
      if (index == 1) {
        return  StatusData(txt("aEP_WeaponReset02")+": "+ String.format("-%.0f", SHIELD_DAMAGE_REDUCE_MULT*100f)+"%", false)
      }
    }
    return  null
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String {
    var toShow = ""
    toShow += " Buffer: "+ storedSoftFlux.toInt()+ " / "+bufferSize.toInt()
    return toShow
  }

  override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {
    if(storedSoftFlux >= bufferSize * 0.9f) return false
    return true
  }

  fun spawnSmoke(ship: ShipAPI, minSmokeDist: Int) {
    var moveAngle = 0f
    val angleToTurn = getTargetWidthAngleInDistance(ship.location, getExtendedLocationFromPoint(ship.location, 0f, ship.collisionRadius), minSmokeDist.toFloat())
    while (moveAngle < 360f) {
      val outPoint = CollisionUtils.getCollisionPoint(getExtendedLocationFromPoint(ship.location, moveAngle, ship.collisionRadius + 10), ship.location, ship)
      val lifeTime = 2f
      val extendRange = 0.5f
      val speed = speed2Velocity(VectorUtils.getAngle(ship.location, outPoint), extendRange * ship.collisionRadius)
      val ms = aEP_MovingSmoke(outPoint!!)
      ms.lifeTime = lifeTime
      ms.fadeIn = 0.25f
      ms.fadeOut = 0.5f
      ms.setInitVel(speed)
      ms.size = minSmokeDist * 3f
      ms.sizeChangeSpeed = minSmokeDist * extendRange * 3f / lifeTime
      ms.color = SMOKE_COLOR
      ms.stopSpeed = 0.75f
      addEffect(ms)
      moveAngle += angleToTurn
    }
    moveAngle = 0f
    while (moveAngle < 360f) {
      val outPoint = CollisionUtils.getCollisionPoint(getExtendedLocationFromPoint(ship.location, moveAngle, ship.collisionRadius + 10), ship.location, ship)
      val lifeTime = 2f
      val extendRange = 0.5f
      val speed = speed2Velocity(VectorUtils.getAngle(ship.location, outPoint), extendRange * ship.collisionRadius + minSmokeDist * 6f)
      val ms = aEP_MovingSmoke(outPoint!!)
      ms.lifeTime = lifeTime
      ms.fadeIn = 0.25f
      ms.fadeOut = 0.5f
      ms.setInitVel(speed)
      ms.size = minSmokeDist * 6f
      ms.sizeChangeSpeed = minSmokeDist * extendRange * 6f / lifeTime
      ms.color = SMOKE_COLOR
      ms.stopSpeed = 0.75f
      addEffect(ms)
      moveAngle += angleToTurn
    }
  }

  fun updateDecos(ship: ShipAPI, effectLevel: Float, amount: Float){
    var slide_l : aEP_DecoAnimation? = null
    var slide_r : aEP_DecoAnimation? = null
    var vent_1 : aEP_DecoAnimation? = null
    var vent_2 : aEP_DecoAnimation? = null

    for(w in ship.allWeapons){
      if(w.spec.weaponId.equals("aEP_cru_zhongliu_slide_l")) slide_l = w.effectPlugin as aEP_DecoAnimation
      if(w.spec.weaponId.equals("aEP_cru_zhongliu_slide_r")) slide_r = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("VENT_POINT_0")) vent_1 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("VENT_POINT_1")) vent_2 = w.effectPlugin as aEP_DecoAnimation
    }

    slide_l?:return
    slide_r?:return
    vent_1?:return
    vent_2?:return

    if(effectLevel <= 0.4f){
      val convertedLevel = (0.4f - effectLevel)/0.4f
      slide_l.setMoveToLevel(1f)
      slide_l.setMoveToSideLevel(convertedLevel)
      slide_r.setMoveToLevel(1f)
      slide_r.setMoveToSideLevel(convertedLevel)
      vent_1.setGlowToLevel(0f)
      vent_2.setGlowToLevel(0f)
    }
    else if(effectLevel <= 1f){
      val convertedLevel = (1f - effectLevel)/0.6f
      slide_l.setMoveToLevel(convertedLevel)
      slide_l.setMoveToSideLevel(0f)
      slide_r.setMoveToLevel(convertedLevel)
      slide_r.setMoveToSideLevel(0f)
      vent_1.setGlowToLevel(1f)
      vent_2.setGlowToLevel(1f)
    }

    //创造散热红色电子烟雾 以及散热口闪光
    redSmokeTracker.advance(amount)
    if (redSmokeTracker.intervalElapsed()) {
      if (effectLevel > 0.9f){
        var initColor = Color(250,50,50)
        var alpha = 0.3f * effectLevel
        var lifeTime = 3f * effectLevel
        var size = 35f
        var endSizeMult = 1.5f
        var vel = aEP_Tool.speed2Velocity(vent_1.weapon.currAngle,30f)
        Vector2f.add(vel,ship.velocity,vel)
        vel.scale(0.5f)
        val loc1 = aEP_Tool.getExtendedLocationFromPoint(vent_1.weapon.location,vent_1.weapon.currAngle,20f)
        Global.getCombatEngine().addNebulaParticle(
          MathUtils.getRandomPointInCircle(loc1,20f),
          vel,
          size, endSizeMult,
          0.1f, 0.4f,
          lifeTime * MathUtils.getRandomNumberInRange(0.5f,0.75f),
          aEP_Tool.getColorWithAlpha(initColor,alpha))

        val loc2 = aEP_Tool.getExtendedLocationFromPoint(vent_2.weapon.location,vent_2.weapon.currAngle,20f)
        Global.getCombatEngine().addNebulaParticle(
          MathUtils.getRandomPointInCircle(loc2,20f),
          vel,
          size, endSizeMult,
          0.1f, 0.4f,
          lifeTime * MathUtils.getRandomNumberInRange(0.5f,0.75f),
          aEP_Tool.getColorWithAlpha(initColor,alpha))

        //散热口闪光
        val sparkLoc1 = vent_1.weapon.location
        val sparkLoc2 = vent_2.weapon.location
        val sparkRad = 25f * effectLevel
        val brightness = MathUtils.getRandomNumberInRange(0.25f, 0.5f) * effectLevel
        // vent 1 light
        var light = StandardLight(sparkLoc1, Misc.ZERO, Misc.ZERO, null)
        light.setColor(Color(250, 50, 50))
        light.setLifetime(redSmokeTracker.elapsed * 3f)
        //light.fadeOut(0.1f)
        light.size = sparkRad
        light.intensity = brightness
        LightShader.addLight(light)
        // vent 2 light
        light = StandardLight(sparkLoc2, Misc.ZERO, Misc.ZERO, null)
        light.setColor(Color(250, 50, 50))
        light.setLifetime(redSmokeTracker.elapsed * 3f)
        //light.fadeOut(0.1f)
        light.size = sparkRad
        light.intensity = brightness
        LightShader.addLight(light)


      }
    }

    //散热口闪光1
    val glowLevel = vent_1.decoGlowController.effectiveLevel
    for (i in 1..3){
      val sparkLoc = getExtendedLocationFromPoint(vent_1.weapon.location,vent_1.weapon.currAngle-90f,8f - i*4f)
      sparkLoc.set(MathUtils.getRandomPointInCircle(sparkLoc, 0.5f))
      val sparkRad = MathUtils.getRandomNumberInRange(15f,20f) * glowLevel
      val brightness = MathUtils.getRandomNumberInRange(0.5f, 1f) * glowLevel
      //闪光
      Global.getCombatEngine().addSmoothParticle(
        sparkLoc,
        Misc.ZERO,
        sparkRad,brightness,0.5f,Global.getCombatEngine().elapsedInLastFrame*2f,
        Color(250,50,50))
    }
    //散热口闪光2
    for (i in 1..3){
      val sparkLoc = getExtendedLocationFromPoint(vent_2.weapon.location,vent_2.weapon.currAngle-90f,8f - i*4f)
      sparkLoc.set(MathUtils.getRandomPointInCircle(sparkLoc, 0.5f))
      val sparkRad = MathUtils.getRandomNumberInRange(15f,20f) * glowLevel
      val brightness = MathUtils.getRandomNumberInRange(0.5f, 1f) * glowLevel
      //闪光
      Global.getCombatEngine().addSmoothParticle(
        sparkLoc,
        Misc.ZERO,
        sparkRad,brightness,0.5f,Global.getCombatEngine().elapsedInLastFrame*2f,
        Color(250,50,50))
    }

  }

  fun updateHeadDecos(ship: ShipAPI, effectLevel: Float){
    var head_l : aEP_DecoAnimation? = null
    var head_r : aEP_DecoAnimation? = null
    var l1 : aEP_DecoAnimation? = null
    var l2 : aEP_DecoAnimation? = null
    var l3 : aEP_DecoAnimation? = null


    for(w in ship.allWeapons){
      if(w.spec.weaponId.equals("aEP_cru_zhongliu_head_l")) head_l = w.effectPlugin as aEP_DecoAnimation
      if(w.spec.weaponId.equals("aEP_cru_zhongliu_head_r")) head_r = w.effectPlugin as aEP_DecoAnimation

      if(w.slot.id.equals("ID_L1")) l1 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_L2")) l2 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_L3")) l3 = w.effectPlugin as aEP_DecoAnimation

    }

    head_l?:return
    head_r?:return
    l1?:return
    l2?:return
    l3?:return

    if(effectLevel >= 0.25f){
      head_l.setMoveToSideLevel(1f)
      head_r.setMoveToSideLevel(1f)
    }else{
      head_l.setMoveToSideLevel(0f)
      head_r.setMoveToSideLevel(0f)
    }

    var currCharge = 0
    val storedLvl = (storedSoftFlux/bufferSize).coerceAtMost(1f)
    if(storedLvl > 0.25) currCharge = 3
    if(storedLvl > 0.5f) currCharge = 2
    if(storedLvl > 0.7f) currCharge = 1
    if(storedLvl > 0.9f) currCharge = 0

    l1.decoGlowController.speed = 20f
    l2.decoGlowController.speed = 20f
    l3.decoGlowController.speed = 20f
    if(head_l.decoMoveController.effectiveSideLevel > 0.25f){
      if(currCharge <= 0){
        l1.setGlowToLevel(0f)
        l2.setGlowToLevel(0f)
        l3.setGlowToLevel(0f)
      }else if (currCharge <= 1){
        l1.setGlowToLevel(1f)
        l2.setGlowToLevel(0f)
        l3.setGlowToLevel(0f)
      }else if (currCharge <= 2){
        l1.setGlowToLevel(1f)
        l2.setGlowToLevel(1f)
        l3.setGlowToLevel(0f)
      }else if (currCharge <= 3){
        l1.setGlowToLevel(1f)
        l2.setGlowToLevel(1f)
        l3.setGlowToLevel(1f)
      }
    }else{
      l1.setGlowToLevel(0f)
      l2.setGlowToLevel(0f)
      l3.setGlowToLevel(0f)
    }

  }

  fun updateBottomIndicators(ship: ShipAPI){
    val storedLevel = (storedSoftFlux/bufferSize).coerceAtMost(1f)

    //----------//
    //渲染屁股侧面的2个栅孔颜色
    val left = ship.hullSpec.getWeaponSlot("TAIL_GLOW_L").computePosition(ship)
    val right = ship.hullSpec.getWeaponSlot("TAIL_GLOW_R").computePosition(ship)

    val alpha = (storedLevel/0.15f).coerceAtMost(1f)
    val c = Color(
      clamp((storedLevel-0.15f)*1.18f ,0f,1f),
      1f*(1f - clamp((storedLevel-0.15f)*1.18f ,0f,1f)),0f, alpha)

    val sprite1 = Global.getSettings().getSprite("aEP_FX","zhongliu_tail_glow")
    val sprite2 = Global.getSettings().getSprite("aEP_FX","zhongliu_tail_glow")
    val sprite3 = Global.getSettings().getSprite("aEP_FX","zhongliu_tail_glow")
    val sprite4 = Global.getSettings().getSprite("aEP_FX","zhongliu_tail_glow")

    sprite1.setAdditiveBlend()
    sprite2.setAdditiveBlend()
    sprite3.setAdditiveBlend()
    sprite4.setAdditiveBlend()

    val left1 = (MathUtils.getRandomPointInCircle(left,0.36f))
    val left2 = (MathUtils.getRandomPointInCircle(left,0.36f))
    val right1 = (MathUtils.getRandomPointInCircle(right,0.36f))
    val right2 = (MathUtils.getRandomPointInCircle(right,0.36f))
    MagicRender.singleframe(
      sprite1, left1, Vector2f(sprite1.width,sprite1.height), ship.facing + 90f,
      c,true)
    MagicRender.singleframe(
      sprite2, left2, Vector2f(sprite2.width,sprite2.height), ship.facing + 90f,
      c,true)
    MagicRender.singleframe(
      sprite3, right1, Vector2f(sprite3.width,sprite3.height), ship.facing + 90f,
      c,true, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
    MagicRender.singleframe(
      sprite4, right2, Vector2f(sprite4.width,sprite4.height), ship.facing + 90f,
      c,true, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)

    //----------//
    //渲染舰桥的3个指示灯
    val light1 = ship.hullSpec.getWeaponSlot("ID_R1").computePosition(ship)
    val light2 = ship.hullSpec.getWeaponSlot("ID_R2").computePosition(ship)
    val light3 = ship.hullSpec.getWeaponSlot("ID_R3").computePosition(ship)

    val lightSprite1 = Global.getSettings().getSprite("aEP_FX","neibo_indicator_glow")
    val lightSprite2 = Global.getSettings().getSprite("aEP_FX","neibo_indicator_glow")
    val lightSprite3 = Global.getSettings().getSprite("aEP_FX","neibo_indicator_glow")

    lightSprite1.setAdditiveBlend()
    lightSprite2.setAdditiveBlend()
    lightSprite3.setAdditiveBlend()

    val cLight1 = Color(
      clamp((storedLevel-0.1f) * 3.33f,0f,1f),
      1f*(1f -   clamp((storedLevel-0.1f) * 3.3f,0f,1f)),
      0f, alpha)
    val cLight2 = Color(
      clamp((storedLevel-0.4f) * 3.33f,0f,1f),
      1f*(1f -   clamp((storedLevel-0.4f) * 3.3f,0f,1f)),
      0f, alpha)
    val cLight3 = Color(
      clamp((storedLevel-0.7f) * 3.33f,0f,1f),
      1f*(1f -   clamp((storedLevel-0.7f) * 3.3f,0f,1f)),
      0f, alpha)

    val loc1 = (MathUtils.getRandomPointInCircle(light1,0.2f))
    val loc2 = (MathUtils.getRandomPointInCircle(light2,0.2f))
    val loc3 = (MathUtils.getRandomPointInCircle(light3,0.2f))
    MagicRender.singleframe(
      lightSprite1, loc1, Vector2f(lightSprite1.width,lightSprite1.height), ship.facing - 90f,
      cLight1,true, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
    MagicRender.singleframe(
      lightSprite2, loc2, Vector2f(lightSprite2.width,lightSprite2.height), ship.facing - 90f,
      cLight2,true, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
    MagicRender.singleframe(
      lightSprite3, loc3, Vector2f(lightSprite3.width,lightSprite3.height), ship.facing - 90f,
      cLight3,true, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
  }
}
