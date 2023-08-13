package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.impl.VEs.aEP_MovingSmoke
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*

class aEP_VentMode: BaseShipSystemScript() {

  companion object {
    const val ID = "aEP_VentMode"

    val JITTER_COLOR = Color(240, 50, 50, 60)
    val HIGHLIGHT_COLOR = Color(255, 20, 20, 120)
    val RING_COLOR = Color(180, 90, 90, 85)
    val SMOKE_EMIT_COLOR = Color(250, 250, 250, 60)
    val SMOKE_EMIT_COLOR2 = Color(250, 250, 250, 180)

    const val SOFT_CONVERT_RATE = 0.2f
    const val SOFT_CONVERT_SPEED = 2000f
    const val SHIELD_DAMAGE_TAKEN_BONUS = 100f
    const val HULL_DAMAGE_TAKEN_BONUS = 25f
  }


  var engine = Global.getCombatEngine()
  var amount = 0f
  var didUse = false
  private val smokeTracker = IntervalUtil(0.2f, 0.2f)
  private val smokeTracker2 = IntervalUtil(0.1f, 0.1f)
  private val sparkTracker = IntervalUtil(0.1f, 0.4f)

  var timeElapsedAfterIn = 0f;

  //run every frame
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴
    if (stats == null || stats.entity == null || stats.entity !is ShipAPI) return
    val ship = stats.entity as ShipAPI
    amount = aEP_Tool.getAmount(ship)

    timeElapsedAfterIn += amount

    //开启的一秒内，禁止手动关闭
    if(timeElapsedAfterIn <= 1f){
      ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM)
    }

    if(state == ShipSystemStatsScript.State.OUT){
      timeElapsedAfterIn = 0f
    }

    //创造散热器烟雾
    smokeTracker.advance(amount)
    if (smokeTracker.intervalElapsed()) {
      for (w in ship.allWeapons) {
        if (w.id.contains("aEP_cap_duiliu_limiter_glow")) {
          val angle = w.currAngle
          val smoke = aEP_MovingSmoke(w.location)
          smoke.setInitVel(aEP_Tool.speed2Velocity(angle, 10f))
          smoke.setInitVel(ship.velocity)
          smoke.stopSpeed = 0.975f
          smoke.fadeIn = 0f
          smoke.fadeOut = 1f
          smoke.lifeTime = 1f + 1f * effectLevel
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
        smoke.lifeTime = 0.5f + 0.25f * effectLevel
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

    //创造散热栓烟雾
    sparkTracker.advance(amount * effectLevel)
    if (sparkTracker.intervalElapsed()) {
      for (weapon in ship.allWeapons) {
        if (!weapon.isDecorative) continue
        if (!weapon.spec.weaponId.equals("aEP_cap_duiliu_limiter")) continue
        if (effectLevel <= 0.9f) continue

        var initColor = Color(250,50,50)
        var alpha = 0.3f
        var lifeTime = 3f
        var size = 35f
        var endSizeMult = 1.5f
        var vel = aEP_Tool.speed2Velocity(weapon.currAngle,30f)
        Vector2f.add(vel,ship.velocity,vel)
        vel.scale(0.5f)
        val loc = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle,20f)
        Global.getCombatEngine().addNebulaParticle(
          MathUtils.getRandomPointInCircle(loc,20f),
          vel,
          size, endSizeMult,
          0.1f, 0.4f,
          lifeTime * MathUtils.getRandomNumberInRange(0.5f,0.75f),
          aEP_Tool.getColorWithAlpha(initColor,alpha))

      }
    }

    //散热栓闪光
    for (weapon in ship.allWeapons) {
      if (!weapon.isDecorative) continue
      if (!weapon.spec.weaponId.equals("aEP_cap_duiliu_limiter")) continue
      val glowLevel = (timeElapsedAfterIn/2f).coerceAtMost(1f)
      for (i in 1..6){

        val sparkLoc = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle,i*5f)
        val sparkRad = MathUtils.getRandomNumberInRange(15f,20f) * glowLevel
        val brightness = MathUtils.getRandomNumberInRange(0.5f, 0.75f) * glowLevel
        //闪光
        Global.getCombatEngine().addSmoothParticle(
          sparkLoc,
          Misc.ZERO,
          sparkRad,brightness,0.5f,Global.getCombatEngine().elapsedInLastFrame*2f,
          Color(250,50,50))
      }

    }

    //move deco weapon
    openDeco(ship, effectLevel)

    //转化幅能
    val convertLevel = 0.5f + (effectLevel-0.5f).coerceAtLeast(0f)
    val softFlux = ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux
    val hardFlux = ship.fluxTracker.hardFlux
    if(isUsable(ship.system, ship)){
      val toConvert = softFlux.coerceAtMost(SOFT_CONVERT_SPEED * amount * convertLevel)

      //这里选择使用原版的耗散，而不是直接扣除幅能，方便ai理解
      ship.mutableStats.fluxDissipation.modifyFlat(ID, SOFT_CONVERT_SPEED * convertLevel)
      //ship.fluxTracker.decreaseFlux(toConvert)
      ship.system.fluxPerSecond = SOFT_CONVERT_SPEED * SOFT_CONVERT_RATE * convertLevel
      //ship.fluxTracker.increaseFlux(toConvert * SOFT_CONVERT_RATE, true)

    }else{
      ship.system.deactivate()
    }

    //修改数值
    stats.shieldDamageTakenMult.modifyPercent(ID, SHIELD_DAMAGE_TAKEN_BONUS * convertLevel)
    //stats.armorDamageTakenMult.modifyPercent(ID, DAMAGE_TAKEN_BONUS)
    stats.hullDamageTakenMult.modifyPercent(ID, HULL_DAMAGE_TAKEN_BONUS * convertLevel)


    ship.isJitterShields = true
    //舰体微微泛红
    ship.setJitter(
      ship,
      JITTER_COLOR,
      effectLevel,  // intensity
      1,  //copies
      0f) // range


    didUse = true
  }

  //run once when unapply
  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    val ship = stats.entity as ShipAPI

    timeElapsedAfterIn = 0f

    //在这修改数值
    stats.shieldDamageTakenMult.unmodify(ID)
    stats.armorDamageTakenMult.unmodify(ID)
    stats.hullDamageTakenMult.unmodify(ID)

    stats.fluxDissipation.unmodify(ID)

    if (!didUse) return

    //move decos
    openDeco(ship, 0f)

    didUse = false
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): ShipSystemStatsScript.StatusData? {
    val convertLevel = 0.5f + (effectLevel-0.5f).coerceAtLeast(0f)
    if (index == 0) {
      return ShipSystemStatsScript.StatusData(String.format(aEP_DataTool.txt("aEP_VentMode01"),
        String.format("%.0f", SOFT_CONVERT_SPEED * convertLevel) ,
        String.format("%.0f", SOFT_CONVERT_SPEED * SOFT_CONVERT_RATE) ),
        false)
    } else if (index == 1) {
      return ShipSystemStatsScript.StatusData(String.format(aEP_DataTool.txt("aEP_VentMode02") ,
        String.format("%.0f", SHIELD_DAMAGE_TAKEN_BONUS * convertLevel) + "%"),
        true)
    }
    else if (index == 2) {
      return ShipSystemStatsScript.StatusData(String.format(aEP_DataTool.txt("aEP_VentMode07") ,
        String.format("%.0f", HULL_DAMAGE_TAKEN_BONUS * convertLevel) + "%"),
        true)
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
    val hardFlux = ship.fluxTracker.hardFlux
    if(softFlux < SOFT_CONVERT_SPEED* 0.5f) return false

    return true
  }

  fun openDeco(ship: ShipAPI, effectLevel: Float) {
    //move deco weapon
    for (weapon in ship.allWeapons) {
      if (weapon.slot.id.contains("AM")) {
        val to = MathUtils.clamp(effectLevel * 2f, 0f, 1f)
        if (weapon.slot.id.contains("01") || weapon.slot.id.contains("04")) {
          (weapon.effectPlugin as aEP_DecoAnimation).decoMoveController.range = 6f
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToLevel(to)
        }
        if (weapon.slot.id.contains("02") || weapon.slot.id.contains("05")) {
          (weapon.effectPlugin as aEP_DecoAnimation).decoMoveController.range = 18f
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToLevel(to)
        }
        if (weapon.slot.id.contains("03")) {
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToLevel(effectLevel)
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToSideLevel(effectLevel)
          (weapon.effectPlugin as aEP_DecoAnimation).setRevoToLevel(effectLevel)
        }
        if (weapon.slot.id.contains("06")) {
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToLevel(effectLevel)
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToSideLevel(effectLevel)
          (weapon.effectPlugin as aEP_DecoAnimation).setRevoToLevel(effectLevel)
        }
      }
      if (weapon.slot.id.contains("LM")) {
        val to = MathUtils.clamp((effectLevel - 0.5f) * 2f, 0f, 1f)
        (weapon.effectPlugin as aEP_DecoAnimation).setMoveToLevel(to)
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