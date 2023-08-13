package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import combat.util.aEP_Tool.Util.getAmount
import combat.util.aEP_Tool.Util.speed2Velocity
import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import data.scripts.shipsystems.aEP_WeaponReset.Companion.GLOW_COLOR
import combat.util.aEP_Tool.Util.angleAdd
import combat.util.aEP_Tool.Util.velocity2Speed
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import combat.util.aEP_DataTool.floatDataRecorder
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import combat.impl.VEs.aEP_MovingSmoke
import com.fs.starfarer.api.loading.WeaponSlotAPI
import org.lwjgl.util.vector.Vector2f
import data.scripts.shipsystems.aEP_WeaponReset
import java.util.EnumSet
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import combat.util.aEP_DataTool
import org.lazywizard.lazylib.MathUtils
import data.scripts.weapons.aEP_DecoAnimation
import data.scripts.shipsystems.aEP_ExtremeOverloadScript.stopWeapon
import data.scripts.shipsystems.aEP_ExtremeOverloadScript.Blur
import shaders.aEP_BloomShader
import combat.impl.VEs.aEP_SpreadRing
import data.scripts.shipsystems.aEP_ExtremeOverloadScript.DeflexRing
import combat.impl.aEP_BaseCombatEffect
import org.lazywizard.lazylib.combat.CombatUtils
import org.lazywizard.lazylib.VectorUtils
import combat.util.aEP_Tool.Util.isNormalWeaponSlotType
import combat.util.aEP_Tool.Util.isNormalWeaponType
import data.scripts.shipsystems.aEP_WeaponReset.Companion.STOP_GLOW_COLOR
import shaders.aEP_BloomMask
import org.lwjgl.opengl.GL11
import org.lazywizard.lazylib.FastTrig
import java.awt.Color
import java.lang.Math
import java.util.ArrayList

class aEP_ExtremeOverloadScript : BaseShipSystemScript() {

  companion object {
    const val ROF_BONUS = 1.5f

    //越小过载时间越长
    //每积累等于最大容量百分之多少的幅能，就过载1秒
    const val FLUX_PERCENT_TO_OVERLOAD_TIME = 0.08f
    const val MAX_OVERLOAD_TIME = 12f

    //REDUCE_MULT 是 1-x
    const val WEAPON_COST_REDUCE_MULT = 0.5f
    const val FLUX_DISS_RUDUCE_MULT = 0.75f

    val JITTER_COLOR = Color(240, 50, 50, 60)
    val HIGHLIGHT_COLOR = Color(255, 20, 20, 120)
    val RING_COLOR = Color(180, 90, 90, 85)
    val SMOKE_EMIT_COLOR = Color(250, 250, 250, 60)
    val SMOKE_EMIT_COLOR2 = Color(250, 250, 250, 180)

    const val DEFLEX_RANGE = 600f
    const val RING_WIDTH = 400f
    const val FRINGE_WIDTH = 80f
    const val SPREAD_SPEED = 1000f
    const val DAMAGE_TO_MISSILE = 200f
  }

  var accumulatedFlux = floatDataRecorder()
  var overloadTime = 0f
  var engine = Global.getCombatEngine()
  var amount = 0f
  var didUse = false
  private val smokeTracker = IntervalUtil(0.2f, 0.2f)
  private val smokeTracker2 = IntervalUtil(0.1f, 0.1f)

  //run every frame
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴
    if (stats == null || stats.entity == null || stats.entity !is ShipAPI) return
    val ship = stats.entity as ShipAPI
    amount = getAmount(ship)

    //检测每帧的软幅能增长
    val softFluxNow = ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux
    accumulatedFlux.addRenewData(softFluxNow)

    //计算应该过载多久
    overloadTime = if (accumulatedFlux.total > ship.fluxTracker.maxFlux * FLUX_PERCENT_TO_OVERLOAD_TIME * MAX_OVERLOAD_TIME)
      MAX_OVERLOAD_TIME
    else
      accumulatedFlux.total / (FLUX_PERCENT_TO_OVERLOAD_TIME * ship.fluxTracker.maxFlux)


    val chargeLevel = 1f - (1f - effectLevel) * (1f - effectLevel)
    //创造散热器烟雾
    smokeTracker.advance(amount)
    if (smokeTracker.intervalElapsed()) {
      for (w in ship.allWeapons) {
        if (w.id.contains("aEP_cap_duiliu_limiter_glow")) {
          val angle = w.currAngle
          val smoke = aEP_MovingSmoke(w.location)
          smoke.setInitVel(speed2Velocity(angle, 10f))
          smoke.setInitVel(ship.velocity)
          smoke.stopSpeed = 0.975f
          smoke.fadeIn = 0f
          smoke.fadeOut = 1f
          smoke.lifeTime = 1f + 1f * effectLevel
          smoke.size = 10f
          smoke.sizeChangeSpeed = 25f
          smoke.color = SMOKE_EMIT_COLOR
          addEffect(smoke)
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
        smoke.setInitVel(speed2Velocity(s.computeMidArcAngle(ship), 100f))
        smoke.stopForceTimer.setInterval(0.05f, 0.05f)
        smoke.stopSpeed = 0.95f
        addEffect(smoke)
      }
    }

    //move deco weapon
    openDeco(ship, effectLevel)

    //施加buff
    stats.ballisticRoFMult.modifyFlat(id, chargeLevel * ROF_BONUS)
    stats.ballisticAmmoRegenMult.modifyMult(id, chargeLevel * ROF_BONUS)
    stats.ballisticWeaponFluxCostMod.modifyMult(id, 1f - effectLevel * WEAPON_COST_REDUCE_MULT)

    stats.energyRoFMult.modifyFlat(id, chargeLevel * ROF_BONUS)
    stats.energyWeaponFluxCostMod.modifyMult(id, chargeLevel * ROF_BONUS)
    stats.energyAmmoRegenMult.modifyMult(id, 1f - effectLevel * WEAPON_COST_REDUCE_MULT)

    stats.fluxDissipation.modifyMult(id, 1f - effectLevel * FLUX_DISS_RUDUCE_MULT)

    //add weapon glow and jitter
    ship.setWeaponGlow(
      chargeLevel,  //float glow,
      GLOW_COLOR,  //java.awt.Color color,
      EnumSet.of(WeaponType.BALLISTIC, WeaponType.ENERGY))

    ship.isJitterShields = false
    //舰体微微泛红
    ship.setJitter(
      ship,
      JITTER_COLOR,
      chargeLevel,  // intensity
      1,  //copies
      0f) // range

    //如果需要过载，在最后时刻高亮
    val threshold = 0.9f
    if (effectLevel > threshold && overloadTime > 0.1f) {
      ship.setJitter(
        ship,
        HIGHLIGHT_COLOR,
        (effectLevel - threshold) / (1f - threshold),  // intensity
        1,  //copies
        1f) // range
    }
    didUse = true
  }

  //run once when unapply
  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    val ship = stats.entity as ShipAPI

    //在这修改数值
    stats.ballisticRoFMult.unmodify(id)
    stats.ballisticAmmoRegenMult.unmodify(id)
    stats.ballisticWeaponFluxCostMod.unmodify(id)

    stats.energyRoFMult.unmodify(id)
    stats.energyWeaponFluxCostMod.unmodify(id)
    stats.energyAmmoRegenMult.unmodify(id)

    stats.fluxDissipation.unmodify(id)
    if (!didUse) return
    //stop weapon glowing
    ship.setWeaponGlow(
      0f,
      GLOW_COLOR,
      EnumSet.allOf(WeaponType::class.java))

    //move decos
    openDeco(ship, 0f)

    //如果需要过载，调用特效
    if (overloadTime > 0.1f) endSystem(ship)
    accumulatedFlux.reset()
    didUse = false
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): StatusData? {
    if (index == 0) {
      return StatusData(aEP_DataTool.txt("ExtremeOverload01") + ": " + accumulatedFlux.total.toInt(), false)
    } else if (index == 1) {
      return StatusData(aEP_DataTool.txt("ExtremeOverload02") + ": " + (overloadTime * 100f).toInt() / 100f, true)
    } else if (index == 2) {
      val chargeLevel = 1f - (1f - effectLevel) * (1f - effectLevel)
      return StatusData(aEP_DataTool.txt("ExtremeOverload03") + ": " + (chargeLevel * ROF_BONUS * 100).toInt() + "%", false)
    } else if (index == 3) {
      return StatusData(aEP_DataTool.txt("ExtremeOverload04") + ": " + (effectLevel * WEAPON_COST_REDUCE_MULT * 100).toInt() + "%", false)
    } else if (index == 4) {
      return StatusData(aEP_DataTool.txt("ExtremeOverload05") + ": " + (effectLevel * FLUX_DISS_RUDUCE_MULT * 100).toInt() + "%", true)
    }
    return null
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String? {
    return null
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

  fun endSystem(ship: ShipAPI) {
    //give a stop weapon debuff
    addEffect(stopWeapon(overloadTime, ship))
    val blur = Blur(ship, ship.collisionRadius, 1f)
    blur.renderInShader = true
    addEffect(blur)
    aEP_BloomShader.add(blur)

    /* float facing = 0f;
    while (facing < 360) {
      Global.getCombatEngine().spawnEmpArc(ship,//ShipAPI damageSource,
        aEP_Tool.getExtendedLocationFromPoint(ship.getLocation(), facing, (float) Math.random() * ship.getCollisionRadius() * 2f),// Vector2f point,
        ship,// CombatEntityAPI pointAnchor,
        ship,// CombatEntityAPI empTargetEntity,
        DamageType.ENERGY,// DamageType damageType,
        0f,// float damAmount,
        0f,// float empDamAmount,
        ship.getCollisionRadius() * 4f,// float maxRange,
        null,// java.lang.String impactSoundId,
        25f,// float thickness,
        new Color(100, 100, 100, 80),// java.awt.Color fringe,
        new Color(150, 50, 50, 120));// java.awt.Color core)
      facing += (float) Math.random() * 20f;
    }*/

    /* //create distortion
    WaveDistortion wave = new WaveDistortion(ship.getLocation(), new Vector2f(0, 0));
    wave.setSize(DEFLEX_RANGE);
    wave.setLifetime(0.5f);
    wave.fadeInSize(0.5f);
    wave.setIntensity(20f);
    wave.fadeOutIntensity(1f);
    DistortionShader.addDistortion(wave);*/

    //create ring
    val ring: aEP_SpreadRing = DeflexRing(
      SPREAD_SPEED,
      RING_WIDTH,
      RING_COLOR,
      0f,
      DEFLEX_RANGE,
      ship.location,
      ship)
    ring.layers = EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER)
    ring.initColor.setToColor(0f, 0f, 0f, 0f, 1f)
    addEffect(ring)

    /*   //create ring fringe
    aEP_SpreadRing ringFringe = new aEP_SpreadRing(
      SPREAD_SPEED,
      FRINGE_WIDTH,
      new Color(250, 250, 250, 120),
      0f,
      DEFLEX_RANGE,
      ship.getLocation());
    ringFringe.getInitColor().setToColor(0, 0, 0, 0, 2f);
    ringFringe.setLayers(EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER));
    ringFringe.getEndColor().setColor(200, 200, 200, 0);
    ringFringe.getInitColor().setToColor(0,0,0,0,1);
    aEP_CombatEffectPlugin.Mod.addEffect(ringFringe);*/

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
        Color(255, 225, 225, 30))
      i++
    }
  }

  internal inner class stopWeapon(time: Float, var ship: ShipAPI) : aEP_BaseCombatEffect() {
    init {
      this.entity = ship
      lifeTime = time
      ship.mutableStats.ventRateMult.modifyMult("aEP_stopWeapon", 0f)
    }


    override fun advanceImpl(amount: Float) {
      overloadTime -= amount
      if (overloadTime <= 0f) {
        shouldEnd = true
        return
      }
      if (Global.getCombatEngine().playerShip === ship) {
        val name = aEP_ExtremeOverloadScript::class.java.simpleName.replace("Script", "")
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName,  //key
          Global.getSettings().getShipSystemSpec(name).iconSpriteName,  //sprite name,full, must be registed in setting first
          aEP_DataTool.txt("ExtremeOverload02"), ((overloadTime * 100f).toInt() / 100f).toString() + "",  //data
          true
        )
      }

      for (w in ship.allWeapons) {
        if(isNormalWeaponSlotType(w.slot,true)){
          if(w.hasAIHint(WeaponAPI.AIHints.PD)){
            continue
          }
        }
        //持续激活，然后记得手动关闭一次，不会自动关闭发光
        w.setGlowAmount(1f, STOP_GLOW_COLOR)
        w.setForceNoFireOneFrame(true)
      }

      //禁止自动开火，禁止手动开火，完事
      //096加入更精确的方法
      //ship.isHoldFireOneFrame = true
      //ship.blockCommandForOneFrame(ShipCommand.FIRE)

      //如果系统立刻再次激活，立刻打断禁开火
      if (ship.system != null && ship.system.isActive) {
        shouldEnd = true
      }
    }

    override fun readyToEnd() {
      ship.setWeaponGlow(0f,
        GLOW_COLOR,
        EnumSet.allOf(WeaponType::class.java))
      ship.mutableStats.ventRateMult.unmodify("aEP_stopWeapon")
    }

  }

  internal inner class DeflexRing(speed: Float, width: Float, initColor: Color?, startRadius: Float, endRadius: Float, center: Vector2f?, e: CombatEntityAPI) : aEP_SpreadRing(speed, width, initColor!!, startRadius, endRadius, center!!) {
    init {
      this.entity = e
    }

    var list: MutableList<CombatEntityAPI> = ArrayList()
    override fun advanceImpl(amount: Float) {
      //Global.getLogger(this.getClass()).info("actived");
      val entity = entity as CombatEntityAPI
      for (proj in CombatUtils.getProjectilesWithinRange(center, DEFLEX_RANGE)) {
        if (proj.source.owner != entity.owner && MathUtils.getDistance(proj.location, entity.location) < ringRadius) {
          if (list.contains(proj)) continue
          var projFacing = VectorUtils.getFacing(proj.velocity)
          val projLocToShipAngle = VectorUtils.getAngle(proj.location, entity.location)
          val angleDist = MathUtils.getShortestRotation(projLocToShipAngle, projFacing)
          if (Math.abs(angleDist) < 60) {
            projFacing = if (angleDist > 0) {
              angleAdd(projLocToShipAngle, 60f)
            } else {
              angleAdd(projLocToShipAngle, -60f)
            }
            val projSpeed = velocity2Speed(proj.velocity).y
            val projNewVelocity = speed2Velocity(projFacing, projSpeed)
            proj.facing = projFacing
            proj.velocity.setX(projNewVelocity.getX())
            proj.velocity.setY(projNewVelocity.getY())
            var effectiveDamage = proj.damage.damage
            if (proj.damage.type == DamageType.FRAGMENTATION) effectiveDamage /= 4f
            list.add(proj)
          }
        }
      }
      for (proj in CombatUtils.getMissilesWithinRange(center, DEFLEX_RANGE)) {
        if (proj.source.owner != entity.owner && MathUtils.getDistance(proj.location, entity.location) < ringRadius) {
          if (proj.collisionClass == null || proj.collisionClass == CollisionClass.NONE) continue
          if (list.contains(proj)) continue
          engine.applyDamage(
            proj,  //target
            proj.location,  //point
            DAMAGE_TO_MISSILE,  //damage
            DamageType.FRAGMENTATION,
            0f,
            true,  //deal softflux
            true,  //is bypass shield
            proj) //damage source
          list.add(proj)
          var effectiveDamage = proj.damage.damage
          if (proj.damage.type == DamageType.FRAGMENTATION) effectiveDamage /= 4f
        }
      }
    }
  }

  internal inner class Blur(var anchor: CombatEntityAPI, var toRenderRadius: Float, lifeTime: Float) : aEP_BloomMask() {
    init {
      this.entity = anchor
      this.lifeTime = lifeTime
    }

    override fun advanceImpl(amount: Float) {
      if (anchor is ShipAPI) {
        val ship = anchor as ShipAPI
        ship.isJitterShields = false
        ship.setJitter(
          ship,
          HIGHLIGHT_COLOR,
          1f - time / lifeTime,  // intensity
          1,  //copies
          1f
        ) // range
      }
    }

    override fun draw() {
      aEP_BloomShader.setLevelX(4f * (1 - time / lifeTime))
      aEP_BloomShader.setLevelY(4f * (1 - time / lifeTime))
      aEP_BloomShader.setLevelAlpha(1f - time / lifeTime)

      //begin
      val numOfVertex = 36
      GL11.glBegin(GL11.GL_POLYGON)
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
      val center = anchor.location
      var converted = convertPositionToScreen(center)

      //画中心点
      GL11.glTexCoord2f(converted.x, converted.y)
      GL11.glVertex2f(center.x, center.y)

      //画圆边
      for (i in 0..numOfVertex) {
        val r = toRenderRadius
        val pointNear = Vector2f(center.x + r * FastTrig.cos(2f * Math.PI * i / numOfVertex).toFloat(), center.y + r * FastTrig.sin(2f * Math.PI * i / numOfVertex).toFloat())
        converted = convertPositionToScreen(pointNear)
        GL11.glTexCoord2f(converted.x, converted.y)
        GL11.glVertex2f(pointNear.x, pointNear.y)
      }
      GL11.glEnd()
    }

  }

}