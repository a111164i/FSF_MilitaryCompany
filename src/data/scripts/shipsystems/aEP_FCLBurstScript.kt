package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.applyImpulse
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import org.lwjgl.util.vector.Vector2f
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import combat.impl.aEP_BaseCombatEffect
import combat.util.aEP_ID
import java.awt.Color

class aEP_FCLBurstScript : BaseShipSystemScript() {
  companion object {
    //开火时自己受到的后向冲量，也是对敌人施加的冲量
    const val IMPULSE = 35000f
    const val MIN_DAMAGE_PERCENT = 0.33f

    const val MAX_GLOW_SIZE = 200f
    val GLOW_COLOR = Color(225, 235, 255, 240)

    const val FULL_DAMAGE_RANGE = 400f

    const val MAX_SPEED_PERCENT_BONUS = 100f
    const val ACC_MULT_PUNISH = 0.5f
    const val ON_FIRE_SPEED_MULT_PUNISH = 0.25f

    const val WEAPON_ID = "aEP_fga_yonglang_main"
    const val WEAPON_GLOW_ID = "aEP_fga_yonglang_glow"
  }

  var smokeTimer = IntervalUtil(0.05f, 0.05f)
  private var ship: ShipAPI? = null
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    ship = stats.entity as ShipAPI
    ship?: return
    val ship = ship as ShipAPI
    val engine = Global.getCombatEngine()
    val amount = Global.getCombatEngine().elapsedInLastFrame * stats.timeMult.modifiedValue
    var weaponNum = 0
    for (weapon in ship.allWeapons) {
      if (!weapon.slot.id.contains("FCL_DECO")) continue
      val toSpawn = getExtendedLocationFromPoint(weapon.location, weapon.currAngle, 24f)
      if (weapon.spec.weaponId == WEAPON_ID) {
        if (ship.system.effectLevel >= 1f) {
          //将开火武器数量＋1
          weaponNum += 1
          val pro = engine.spawnProjectile(
            ship, weapon,
            WEAPON_ID,
            toSpawn,
            weapon.currAngle,
            ship.velocity
          )
          //addEffect(Blink(pro as DamagingProjectileAPI))
          engine.addSmoothParticle(
            toSpawn,  //Vector2f loc,
            Vector2f(0f, 0f),  //Vector2f vel,
            250f,  //float size,
            1f,  //float brightness,
            0.4f,  //float duration,
            Color(200, 200, 200, 250)
          ) //java.awt.Color

          Global.getCombatEngine().addSmoothParticle(
            toSpawn,
            aEP_ID.VECTOR2F_ZERO,
            300f,1f,0.33f,0.15f,
            Color.white)

          Global.getSoundPlayer().playSound(
            "heavy_mortar_fire",
            1f, 1.2f,  // pitch,volume
            ship.location,  //location
            ship.velocity
          ) //velocity
        }
      }

      //set other deco to move
      val anima = weapon.effectPlugin as aEP_DecoAnimation
      //glow to 1 at instant when fire
      if (weaponNum > 0) {
        if (weapon.spec.weaponId == WEAPON_GLOW_ID) anima.setGlowEffectiveLevel(1f)
      }
      //move forward when charging up
      if (state == ShipSystemStatsScript.State.IN) {
        anima.setMoveToLevel(1f)
      }
      //keep still when charging down
      if (state == ShipSystemStatsScript.State.ACTIVE || state == ShipSystemStatsScript.State.OUT) {
        if (effectLevel > 0.5f) {

          //热炮管拖烟
          smokeTimer.advance(amount)
          if (smokeTimer.intervalElapsed() && weapon.spec.weaponId == WEAPON_ID) {
            Global.getCombatEngine().addNebulaParticle(toSpawn, Vector2f(0f,0f),
              40f,2f,
              0.1f,0.4f,2f,
            Color(210,190,180,65))
          }

          //增加极速，减少加速度
          stats.maxSpeed.modifyPercent(id, MAX_SPEED_PERCENT_BONUS)
          stats.acceleration.modifyMult(id, ACC_MULT_PUNISH)
          //调整发光贴图
          if (weapon.spec.weaponId == WEAPON_GLOW_ID) anima.setGlowToLevel((effectLevel - 0.5f) * 2f)

        } else {
          anima.setMoveToLevel(0f)
          if (weapon.spec.weaponId == WEAPON_GLOW_ID) anima.setGlowToLevel(0f)
        }
      }
    }


    //当任何武器开火，立刻大幅度减少自己当前的速度，然后每一个武器施加一定的后向冲量
    if (weaponNum > 0) {
      ship.velocity.scale(ON_FIRE_SPEED_MULT_PUNISH)
      applyImpulse(ship, ship.facing, -IMPULSE * weaponNum)
    }

    //抄的机动推的码，别动就行
    val key = ship!!.id + "_" + id
    val test = Global.getCombatEngine().customData[key]
    if (state == ShipSystemStatsScript.State.IN) {
      if (test == null && effectLevel > 0.2f) {
        Global.getCombatEngine().customData[key] = Any()
        ship!!.engineController.extendLengthFraction.advance(1f)
        for (e in ship!!.engineController.shipEngines) {
          if (e.isSystemActivated) {
            ship!!.engineController.setFlameLevel(e.engineSlot, 1f)
          }
        }
      }
    } else {
      Global.getCombatEngine().customData.remove(key)
    }
  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    stats.acceleration.unmodify(id)
    stats.maxSpeed.unmodify(id)
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): StatusData? {
    return if (index == 0) {
      StatusData("Max Speed Increase" + ": " + MAX_SPEED_PERCENT_BONUS + "%", false)
    } else null
  }

  internal inner class Blink(s: DamagingProjectileAPI) : aEP_BaseCombatEffect() {
    var maxSize: Float
    var glowColor: Color
    var s: DamagingProjectileAPI
    override fun advanceImpl(amount: Float) {
      //根据距离计算伤害
      val nowDist = MathUtils.getDistance(s.spawnLocation, s.location)
      val maxDist = s.weapon.range
      var effectiveLevel = 1f
      if (nowDist > FULL_DAMAGE_RANGE) effectiveLevel = 1f - (nowDist - FULL_DAMAGE_RANGE) / (maxDist - FULL_DAMAGE_RANGE)
      //保底伤害
      effectiveLevel = MathUtils.clamp(effectiveLevel, MIN_DAMAGE_PERCENT,1f)


      s.damageAmount = s.projectileSpec.damage.damage * effectiveLevel
      if (s.didDamage() && s.damageTarget != null) {
        applyImpulse(s.damageTarget, s.facing, IMPULSE * effectiveLevel)
        cleanup()
      }

      //附着一个随距离缩小的光点显示威力
      Global.getCombatEngine().addSmoothParticle(
        s.location,
        Vector2f(0f, 0f),
        maxSize * effectiveLevel,  //size
        1f,  //brightness
        amount * 2,  //duration
        Color(GLOW_COLOR.red, GLOW_COLOR.green, GLOW_COLOR.blue, GLOW_COLOR.alpha)
      )
      //aEP_Tool.addDebugText(effectiveLevel+"");
    }

    init {
      glowColor = GLOW_COLOR
      maxSize = MAX_GLOW_SIZE
      this.s = s
      init(s)
    }
  }

}