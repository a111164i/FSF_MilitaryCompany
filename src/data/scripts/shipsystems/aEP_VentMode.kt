package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_MovingSmoke
import data.scripts.aEP_CombatEffectPlugin
import data.scripts.utils.aEP_DataTool.txt
import data.scripts.utils.aEP_AngleTracker
import data.scripts.utils.aEP_DataTool
import data.scripts.utils.aEP_Tool
import data.scripts.weapons.aEP_DecoAnimation
import org.dark.shaders.light.LightShader
import org.dark.shaders.light.StandardLight
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.max

class aEP_VentMode: BaseShipSystemScript() {

  companion object {
    const val ID = "aEP_VentMode"

    val JITTER_COLOR = Color(240, 50, 50, 60)
    val HIGHLIGHT_COLOR = Color(255, 20, 20, 120)
    val RING_COLOR = Color(180, 90, 90, 85)
    val SMOKE_EMIT_COLOR = Color(250, 250, 250, 60)
    //四角烟雾
    val SMOKE_EMIT_COLOR2 = Color(250, 250, 250, 125)

    const val SOFT_CONVERT_RATE = 0.2f
    const val SOFT_CONVERT_SPEED = 2000f
    const val HULL_DAMAGE_TAKEN_BONUS = 50f

    const val WEAPON_ROF_BONUS = 0.3f

    const val SHIELD_DAMAGE_REDUCE_MULT = 0f
    const val MAX_SPEED_REDUCE_MULT = 0f

    private const val MIN_SECOND_TO_USE = 1f
  }

  var engine = Global.getCombatEngine()
  var amount = 0f
  private val smokeTracker = IntervalUtil(0.2f, 0.2f)
  private val smokeTracker2 = IntervalUtil(0.1f, 0.1f)
  private val sparkTracker = IntervalUtil(0.1f, 0.5f)
  private val sparkTracker2 = IntervalUtil(0.05f, 0.05f)

  var timeElapsedAfterIn = 0f;
  var timeElapsedAfterVenting = 0f;
  var forceDown = false

  var animationLevel = aEP_AngleTracker(0f, 0f, 0.4f, 1f, 0f)

  //run every frame
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴
    if (stats == null || stats.entity == null || stats.entity !is ShipAPI) return
    val ship = stats.entity as ShipAPI
    amount = aEP_Tool.getAmount(ship)
    animationLevel.advance(amount)

    //增减激活系统后的时间
    if((state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE) && !forceDown){
      timeElapsedAfterIn += amount * 1000f
      //自动关闭系统
      if(!isUsable(ship.system, ship)) ship.system.deactivate()

    } else{
      timeElapsedAfterIn -= amount * 1000f
    }
    timeElapsedAfterIn = MathUtils.clamp(timeElapsedAfterIn,0f,1f)

    //增减进入venting后的时间
    if(ship.fluxTracker.isVenting){
      timeElapsedAfterVenting += amount * 1000f
    } else{
      timeElapsedAfterVenting -= amount * 1000f
    }
    timeElapsedAfterVenting = MathUtils.clamp(timeElapsedAfterVenting,0f,1f)


    //不再用老的 timeElapsedAfter 作为Level，而是使用以前做的轮子
    // 只要进入venting或者系统激活，立刻把 to 变为1
    val systemLevel = timeElapsedAfterIn
    val ventLevel = timeElapsedAfterVenting
    val decoLevel = max(systemLevel, ventLevel)

    animationLevel.to = decoLevel

    forceDown = false

    //在这里修改数值
    //完全关闭后，还原数值
    if(timeElapsedAfterIn <= 0f){
      //在这修改数值
      stats.shieldDamageTakenMult.unmodify(ID)
      stats.armorDamageTakenMult.unmodify(ID)
      stats.hullDamageTakenMult.unmodify(ID)


      stats.maxSpeed.unmodify(ID)
      stats.fluxDissipation.unmodify(ID)

      ship.mutableStats.ballisticRoFMult.unmodify()
      ship.mutableStats.energyRoFMult.unmodify()
      ship.mutableStats.ballisticAmmoRegenMult.unmodify()
      ship.mutableStats.energyAmmoRegenMult.unmodify()

    }
    else{
      //转化幅能
      val convertLevel = 0.5f + (effectLevel-0.5f).coerceAtLeast(0f)
      val softFlux = ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux
      val hardFlux = ship.fluxTracker.hardFlux
      //幅能充足就转换幅能
      if(ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux > 100f){
        //这里选择使用原版的耗散，而不是直接扣除幅能，方便ai理解
        ship.mutableStats.fluxDissipation.modifyFlat(ID, SOFT_CONVERT_SPEED * convertLevel)
        //ship.fluxTracker.decreaseFlux(toConvert)
        //幅能满了就不产生硬幅能了，防止幅能快满就自动关闭
        ship.system.fluxPerSecond = (SOFT_CONVERT_SPEED * SOFT_CONVERT_RATE * convertLevel).coerceIn(0f,  (ship.fluxTracker.maxFlux - ship.fluxTracker.currFlux - 1000f).coerceAtLeast(0f))

        ship.mutableStats.ballisticRoFMult.modifyFlat(ID, WEAPON_ROF_BONUS * convertLevel)
        ship.mutableStats.energyRoFMult.modifyFlat(ID, WEAPON_ROF_BONUS * convertLevel)

        ship.mutableStats.ballisticAmmoRegenMult.modifyFlat(ID, WEAPON_ROF_BONUS * convertLevel)
        ship.mutableStats.energyAmmoRegenMult.modifyFlat(ID, WEAPON_ROF_BONUS * convertLevel)

      //幅能不足时关闭
      }else{
        if(timeElapsedAfterIn >= 1.9f){
          ship.system.deactivate()
        }
      }

      //修改数值
      stats.shieldDamageTakenMult.modifyMult(ID, 1f - SHIELD_DAMAGE_REDUCE_MULT * convertLevel)
      //stats.armorDamageTakenMult.modifyPercent(ID, DAMAGE_TAKEN_BONUS)
      stats.hullDamageTakenMult.modifyPercent(ID, HULL_DAMAGE_TAKEN_BONUS * convertLevel)
      stats.maxSpeed.modifyMult(ID,1f - MAX_SPEED_REDUCE_MULT * convertLevel)
    }

    //激活后才会产生的特效
    if(animationLevel.curr > 0.2f) {
      val glowLevel = animationLevel.curr

      //创造散热器烟雾
      smokeTracker.advance(amount)
      if (smokeTracker.intervalElapsed()) {
        for (w in ship.allWeapons) {
          if (w.id.contains("aEP_cap_duiliu_glow")) {
            val angle = w.currAngle
            val smoke = aEP_MovingSmoke(w.location)
            smoke.setInitVel(aEP_Tool.speed2Velocity(angle, 10f))
            smoke.setInitVel(ship.velocity)
            smoke.stopSpeed = 0.975f
            smoke.fadeIn = 0f
            smoke.fadeOut = 1f
            smoke.lifeTime = 1f + 1f * glowLevel
            smoke.size = 10f
            smoke.sizeChangeSpeed = 25f
            smoke.color = SMOKE_EMIT_COLOR
            aEP_CombatEffectPlugin.addEffect(smoke)
          }
        }
      }

      //创造四角烟雾
      smokeTracker2.advance(amount)
      if (smokeTracker2.intervalElapsed()) {
        for (s in ship.hullSpec.allWeaponSlotsCopy) {
          if (!s.isSystemSlot) continue
          val smokeLoc = s.computePosition(ship)
          val smoke = aEP_MovingSmoke(smokeLoc)
          smoke.lifeTime = 0.5f + 0.25f * glowLevel
          smoke.fadeIn = 0.5f
          smoke.fadeOut = 0.5f
          smoke.size = 20f
          smoke.sizeChangeSpeed = 40f
          smoke.color = SMOKE_EMIT_COLOR2
          smoke.setInitVel(aEP_Tool.speed2Velocity(s.computeMidArcAngle(ship), 100f))
          smoke.stopForceTimer.setInterval(0.05f, 0.05f)
          smoke.stopSpeed = 0.95f
          aEP_CombatEffectPlugin.addEffect(smoke)
        }
      }

      //创造散热栓红色电子烟雾
      sparkTracker.advance(amount)
      if (sparkTracker.intervalElapsed()) {
        for (weapon in ship.allWeapons) {
          if (!weapon.isDecorative) continue
          if (!weapon.spec.weaponId.equals("aEP_cap_duiliu_limiter1")) continue
          if (glowLevel <= 0.9f) continue

          val initColor = Color(250, 50, 50)
          val alpha = 0.3f * glowLevel
          val lifeTime = 3f * glowLevel
          val size = 35f
          val endSizeMult = 1.5f
          val vel = aEP_Tool.speed2Velocity(weapon.currAngle, 30f)
          Vector2f.add(vel, ship.velocity, vel)
          vel.scale(0.5f)
          val loc = aEP_Tool.getExtendedLocationFromPoint(weapon.location, weapon.currAngle, 20f)
          Global.getCombatEngine().addNebulaParticle(
            MathUtils.getRandomPointInCircle(loc, 20f),
            vel,
            size, endSizeMult,
            0.1f, 0.4f,
            lifeTime * MathUtils.getRandomNumberInRange(0.5f, 0.75f),
            aEP_Tool.getColorWithAlpha(initColor, alpha)
          )

        }
      }


      //散热口闪光
      sparkTracker2.advance(amount)
      if (sparkTracker2.intervalElapsed()) {
        for (weapon in ship.allWeapons) {
          if (!weapon.isDecorative) continue
          if (!weapon.spec.weaponId.equals("aEP_cap_duiliu_limiter1")) continue

          val sparkLoc2 = weapon.location
          val sparkRad2 = 30f * glowLevel
          val brightness2 = MathUtils.getRandomNumberInRange(0.2f, 0.4f) * glowLevel
          val light = StandardLight(sparkLoc2, Misc.ZERO, Misc.ZERO, null)
          light.setColor(Color(250, 50, 50))
          light.setLifetime(sparkTracker2.elapsed)
          light.size = sparkRad2
          light.intensity = brightness2
          LightShader.addLight(light)
        }

      }

    }

    openDeco(ship, animationLevel.curr)

    ship.isJitterShields = true
    //舰体微微泛红
    ship.setJitter(
      ship, JITTER_COLOR, effectLevel, 1, 0f) // range

  }

  //run once when unapply
  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    val ship = stats.entity as ShipAPI
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): ShipSystemStatsScript.StatusData? {
    val convertLevel = 0.5f + (effectLevel-0.5f).coerceAtLeast(0f)
    if(effectLevel > 0f){
      if (index == 0) {
        return ShipSystemStatsScript.StatusData(String.format(
          aEP_DataTool.txt("aEP_VentMode01"),
          String.format("%.0f", SOFT_CONVERT_SPEED * convertLevel) ,
          String.format("%.0f", SOFT_CONVERT_SPEED * SOFT_CONVERT_RATE) ),
          false)
      } else if (index == 1) {
        return  StatusData(txt("aEP_WeaponReset01")+": "+ String.format("+%.0f",WEAPON_ROF_BONUS*100f * convertLevel)+"%", false)
      }
      else if (index == 2) {
        return ShipSystemStatsScript.StatusData(String.format(aEP_DataTool.txt("aEP_VentMode07") ,
          String.format("+%.0f", HULL_DAMAGE_TAKEN_BONUS * convertLevel) + "%"),
          true)
      }

    }


    return null
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String? {
    if(!isUsable(system,ship)){
      return txt("aEP_VentMode06")
    }
    return ""
  }

  override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {
    val softFlux = ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux
    if(softFlux < SOFT_CONVERT_SPEED * MIN_SECOND_TO_USE) return false

    return true
  }

  fun openDeco(ship: ShipAPI, effectLevel: Float) {
    //move deco weapon
    for (weapon in ship.allWeapons) {
      if (weapon.spec.weaponId.contains("aEP_cap_duiliu_armor_l1") ||
        weapon.spec.weaponId.contains("aEP_cap_duiliu_armor_r1")) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)
        if(effectLevel <= 0.25f){
          val convertedLevel =  (effectLevel/0.25f).coerceAtMost(1f)
          controller.setMoveToLevel(0f)
          controller.setMoveToSideLevel(convertedLevel)
          controller.setRevoToLevel(0f)
          continue
        }else if(effectLevel <= 0.75f){
          val convertedLevel =  ((effectLevel-0.25f)/0.5f).coerceAtMost(1f)
          controller.setMoveToLevel(convertedLevel)
          controller.setMoveToSideLevel(1f)
          controller.setRevoToLevel(convertedLevel)
          continue
        } else if(effectLevel <= 1f){
          controller.setMoveToLevel(1f)
          controller.setMoveToSideLevel(1f)
          controller.setRevoToLevel(1f)
          continue
        }
        continue
      }
      if (weapon.spec.weaponId.contains("aEP_cap_duiliu_armor_l2") ||
        weapon.spec.weaponId.contains("aEP_cap_duiliu_armor_r2")) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)
        if(effectLevel <= 0.1f){
          controller.setMoveToLevel(0f)
          controller.setMoveToSideLevel(0f)
          controller.setRevoToLevel(0f)
          continue
        }else if(effectLevel <= 0.55f){
          val convertedLevel =  ((effectLevel-0.1f)/0.45f).coerceAtMost(1f)
          controller.setMoveToLevel(convertedLevel)
          controller.setMoveToSideLevel(0f)
          controller.setRevoToLevel(0.3f * convertedLevel)
          continue
        } else if(effectLevel <= 1f){
          val convertedLevel =  ((effectLevel-0.55f)/0.45f).coerceAtMost(1f)
          controller.setMoveToLevel(1f)
          controller.setMoveToSideLevel(0f)
          controller.setRevoToLevel(0.3f + 0.7f * convertedLevel)
          continue
        }
        continue
      }
      if (weapon.spec.weaponId.contains("aEP_cap_duiliu_armor_l3") ||
        weapon.spec.weaponId.contains("aEP_cap_duiliu_armor_r3")) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)
        if(effectLevel <= 0.55f){
          controller.setMoveToLevel(0f)
          controller.setMoveToSideLevel(0f)
          controller.setRevoToLevel(0f)
          continue
        }else if(effectLevel <= 1f){
          val convertedLevel =  ((effectLevel-0.55f)/0.45f).coerceAtMost(1f)
          controller.setMoveToLevel(convertedLevel)
          controller.setMoveToSideLevel(convertedLevel)
          controller.setRevoToLevel(convertedLevel)
          continue
        }
        continue
      }
      if (weapon.spec.weaponId.contains("aEP_cap_duiliu_limiter")) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)
        if(effectLevel <= 0.65f){
          controller.setMoveToLevel(0f)
          controller.setMoveToSideLevel(0f)
          controller.setRevoToLevel(0f)
          continue
        }else if(effectLevel <= 1f){
          val convertedLevel =  ((effectLevel-0.65f)/0.35f).coerceAtMost(1f)
          controller.setMoveToLevel(convertedLevel)
          controller.setMoveToSideLevel(convertedLevel)
          controller.setRevoToLevel(convertedLevel)
          continue
        }
        continue
      }

      if (weapon.slot.id.contains("GW")) {
        val to = MathUtils.clamp((effectLevel - 0.5f) * 2f, 0f, 1f)
        //((aEP_DecoAnimation) weapon.getEffectPlugin()).setMoveToLevel(to);
        (weapon.effectPlugin as aEP_DecoAnimation).setGlowToLevel(effectLevel)
        //aEP_Tool.addDebugText(""+weapon.getAnimation().getFrame());
      }
    }
  }

  fun endSystemVisualEffect(ship: ShipAPI) {

    //生成烟雾
    var i = 0f
    while (i < 36f) {
      val maxDist = ship.collisionRadius * 1.5f
      val p = MathUtils.getRandomPointInCircle(ship.location, maxDist)
      val dist = MathUtils.getDistance(p, ship.location)
      Global.getCombatEngine().addNebulaSmokeParticle(
        p,
        Vector2f(0f, 0f),
        200f,
        1.5f,
        0.1f,
        0.3f,
        0.5f + 2.5f * (dist / ship.collisionRadius),
        Color(255, 225, 225, 30)
      )
      i++
    }
  }


}