package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.api.util.Misc
import java.awt.Color
import java.util.*

class aEP_LidarArray : BaseShipSystemScript(), WeaponRangeModifier {
  companion object{
    val ACTIVE_RANGE_BONUS = HashMap<ShipAPI.HullSize, Float>()

    init {
      ACTIVE_RANGE_BONUS.put(ShipAPI.HullSize.FIGHTER, 25f)
      ACTIVE_RANGE_BONUS.put(ShipAPI.HullSize.FRIGATE, 50f)
      ACTIVE_RANGE_BONUS.put(ShipAPI.HullSize.DESTROYER, 60f)
      ACTIVE_RANGE_BONUS.put(ShipAPI.HullSize.CRUISER, 80f)
      ACTIVE_RANGE_BONUS.put(ShipAPI.HullSize.CAPITAL_SHIP, 100f)
      ACTIVE_RANGE_BONUS.withDefault { 60f }
    }

    val PASSIVE_BONUS = HashMap<ShipAPI.HullSize, Float>()
    init {
      PASSIVE_BONUS.put(ShipAPI.HullSize.FIGHTER, 10f)
      PASSIVE_BONUS.put(ShipAPI.HullSize.FRIGATE, 8f)
      PASSIVE_BONUS.put(ShipAPI.HullSize.DESTROYER, 12f)
      PASSIVE_BONUS.put(ShipAPI.HullSize.CRUISER, 20f)
      PASSIVE_BONUS.put(ShipAPI.HullSize.CAPITAL_SHIP, 25f)
      PASSIVE_BONUS.withDefault { 0.5f }
    }
  }


  // var LIDAR_WINDUP = "lidar_windup"
  var LIDAR_WINDUP = "system_ammo_feeder"

  var WEAPON_GLOW = Color(255, 50, 50, 155)

  var RANGE_BONUS = 100f
  var PASSIVE_RANGE_BONUS = 25f
  var ROF_BONUS = 2f
  var RECOIL_BONUS = 75f
  var PROJECTILE_SPEED_BONUS = 50f


  class LidarDishData {
    var turnDir = 0f
    var turnRate = 0f
    var angle = 0f
    var phase = 0f
    var count = 0f
    var w: WeaponAPI? = null
  }

  protected var dishData: MutableList<LidarDishData> = ArrayList()
  protected var needsUnapply = false
  protected var playedWindup = false
  protected var inited = false

  fun init(ship: ShipAPI) {
    RANGE_BONUS = ACTIVE_RANGE_BONUS[ship.hullSize]?: 25f
    PASSIVE_RANGE_BONUS = PASSIVE_BONUS[ship.hullSize]?: 5f
    if(!ship.hasListenerOfClass(aEP_LidarArray::class.java)) ship.addListener(this)

    //别动下面的，原版内容
    //------------------------------/
    if (inited) return
    inited = true
    needsUnapply = true
    var turnDir = 1
    var index = 0f
    var count = 0f
    for (w in ship.allWeapons) {
      if (w.isDecorative && w.spec.hasTag(Tags.LIDAR)) {
        count++
      }
    }
    val lidar: MutableList<WeaponAPI> = ArrayList()
    for (w in ship.allWeapons) {
      if (w.isDecorative && w.spec.hasTag(Tags.LIDAR)) {
        lidar.add(w)
      }
    }
    Collections.sort(lidar) { o1, o2 -> Math.signum(o1.slot.location.x - o2.slot.location.x).toInt() }
    for (w in lidar) {
      if (w.isDecorative && w.spec.hasTag(Tags.LIDAR)) {
        w.setSuspendAutomaticTurning(true)
        val data = LidarDishData()
        data.turnDir = Math.signum(turnDir.toFloat())
        data.turnRate = 0.5f
        data.turnRate = 0.1f
        data.w = w
        data.angle = 0f
        data.phase = index / count
        data.count = count
        dishData.add(data)
        turnDir = -turnDir
        index++
      }
    }
  }

  fun rotateLidarDishes(active: Boolean, effectLevel: Float) {
    val amount = Global.getCombatEngine().elapsedInLastFrame
    var turnRateMult = 1f
    if (active) {
      turnRateMult = 20f
    }
    //turnRateMult = 0.1f;
    //boolean first = true;
    for (data in dishData) {
      val arc = data.w!!.arc
      var useTurnDir = data.turnDir
      if (active) {
        useTurnDir = Misc.getClosestTurnDirection(data.angle, 0f)
      }
      val delta = useTurnDir * amount * data.turnRate * turnRateMult * arc
      if (active && effectLevel > 0f && Math.abs(data.angle) < Math.abs(delta * 1.5f)) {
        data.angle = 0f
      } else {
        data.angle += delta
        data.phase += 1f * amount
        if (arc < 360f) {
          if (data.angle > arc / 2f && data.turnDir > 0f) {
            data.angle = arc / 2f
            data.turnDir = -1f
          }
          if (data.angle < -arc / 2f && data.turnDir < 0f) {
            data.angle = -arc / 2f
            data.turnDir = 1f
          }
        } else {
          data.angle = data.angle % 360f
        }
      }
      val facing = data.angle + data.w!!.arcFacing + data.w!!.ship.facing
      data.w!!.setFacing(facing)
      data.w!!.updateBeamFromPoints()
      //			if (first) {
//				System.out.println("Facing: " + facing);
//				first = false;
//			}
    }
  }

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    val ship = stats.entity as ShipAPI
    if (ship == null || ship.isHulk) {
      if (needsUnapply) {
        unmodify(id, stats)
        for (w in ship.allWeapons) {
          if (!w.isDecorative && w.slot.isHardpoint && !w.isBeam &&
            (w.type == WeaponType.BALLISTIC || w.type == WeaponType.ENERGY)
          ) {
            w.setGlowAmount(0f, null)
          }
        }
        needsUnapply = false
      }
      return
    }
    init(ship)

    //lidarFacingOffset += am
    val active = state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE || state == ShipSystemStatsScript.State.OUT
    rotateLidarDishes(active, effectLevel)
    if (active) {
      modify(id, stats, effectLevel)
      needsUnapply = true
    } else {
      if (needsUnapply) {
        unmodify(id, stats)
        for (w in ship.allWeapons) {
          if (w.slot.isSystemSlot) continue
          if (!w.isDecorative && w.slot.isHardpoint && !w.isBeam &&
            (w.type == WeaponType.BALLISTIC || w.type == WeaponType.ENERGY)
          ) {
            w.setGlowAmount(0f, null)
          }
        }
        needsUnapply = false
      }
    }

    //下面开始是激活状态
    if (!active) return
    for (w in ship.allWeapons) {
      if (w.slot.isSystemSlot) continue
      if (w.type == WeaponType.MISSILE) continue
      if (state == ShipSystemStatsScript.State.IN) {
        if (!(w.isDecorative && w.spec.hasTag(Tags.LIDAR))) {
          w.setForceNoFireOneFrame(true)
        }
      } else {
        if (!(!w.isDecorative && w.slot.isHardpoint && !w.isBeam &&
              (w.type == WeaponType.BALLISTIC || w.type == WeaponType.ENERGY))
        ) {
          w.setForceNoFireOneFrame(true)
        }
      }
    }
    val glowColor = WEAPON_GLOW
    for (w in ship.allWeapons) {
      if (!w.isDecorative && w.slot.isHardpoint && !w.isBeam &&
        (w.type == WeaponType.BALLISTIC || w.type == WeaponType.ENERGY)
      ) {
        w.setGlowAmount(effectLevel, glowColor)
      }
    }

    //		for (WeaponAPI w : ship.getAllWeapons()) {
//			if (w.isDecorative() && w.getSpec().hasTag(Tags.LIDAR)) {
//				if (state == State.IN) {
//					w.setForceFireOneFrame(true);
//				}
//			}
//		}

    // always wait a quarter of a second before starting to fire the targeting lasers
    // this is the worst-case turn time required for the dishes to face front
    // doing this to keep the timing of the lidar ping sounds consistent relative
    // to when the windup sound plays
    var fireThreshold = 0.25f / ship.system.chargeUpDur
    fireThreshold += 0.02f // making sure there's only 4 lidar pings; lines up with the timing of the lidardish weapon


    //fireThreshold = 0f;
    for (data in dishData) {
      var skip = data.phase % 1f > 1f / data.count
      //skip = data.phase % 1f > 0.67f;
      skip = false
      if (skip) continue
      if (data.w!!.isDecorative && data.w!!.spec.hasTag(Tags.LIDAR)) {
        if (state == ShipSystemStatsScript.State.IN && Math.abs(data.angle) < 5f && effectLevel >= fireThreshold) {
          data.w!!.setForceFireOneFrame(true)
        }
      }
    }
    if ((state == ShipSystemStatsScript.State.IN && effectLevel > 0.67f || state == ShipSystemStatsScript.State.ACTIVE) && !playedWindup) {
      Global.getSoundPlayer().playSound(LIDAR_WINDUP, 1f, 1f, ship.location, ship.velocity)
      playedWindup = true
    }
  }


  fun modify(id: String, stats: MutableShipStatsAPI, effectLevel: Float) {
    val mult = 1f + ROF_BONUS * effectLevel
    //float mult = 1f + ROF_BONUS;
    stats.ballisticWeaponRangeBonus.modifyPercent(id, RANGE_BONUS)
    stats.energyWeaponRangeBonus.modifyPercent(id, RANGE_BONUS)
    stats.ballisticRoFMult.modifyMult(id, mult)
    stats.energyRoFMult.modifyMult(id, mult)
    //stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f));
    stats.maxRecoilMult.modifyMult(id, 1f - 0.01f * RECOIL_BONUS)
    stats.recoilPerShotMult.modifyMult(id, 1f - 0.01f * RECOIL_BONUS)
    stats.recoilDecayMult.modifyMult(id, 1f - 0.01f * RECOIL_BONUS)
    stats.ballisticProjectileSpeedMult.modifyPercent(id, PROJECTILE_SPEED_BONUS)
    stats.energyProjectileSpeedMult.modifyPercent(id, PROJECTILE_SPEED_BONUS)
  }

  fun unmodify(id: String, stats: MutableShipStatsAPI) {
    stats.ballisticWeaponRangeBonus.modifyPercent(id, PASSIVE_RANGE_BONUS)
    stats.energyWeaponRangeBonus.modifyPercent(id, PASSIVE_RANGE_BONUS)
    //		stats.getBallisticWeaponRangeBonus().unmodifyPercent(id);
//		stats.getEnergyWeaponRangeBonus().unmodifyPercent(id);
    stats.ballisticRoFMult.unmodifyMult(id)
    stats.energyRoFMult.unmodifyMult(id)
    stats.maxRecoilMult.unmodifyMult(id)
    stats.recoilPerShotMult.unmodifyMult(id)
    stats.recoilDecayMult.unmodifyMult(id)
    stats.ballisticProjectileSpeedMult.unmodifyPercent(id)
    stats.energyProjectileSpeedMult.unmodifyPercent(id)
    playedWindup = false
  }


  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    // never called due to runScriptWhileIdle:true in the .system file
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): StatusData? {
    if (state == ShipSystemStatsScript.State.IDLE || state == ShipSystemStatsScript.State.COOLDOWN) {
      if (index == 3) {
        return StatusData("weapon range +" + PASSIVE_RANGE_BONUS.toInt() + "%", false)
      }
    }
    if (effectLevel <= 0f) return null

    //float mult = 1f + ROF_BONUS;
    val mult = 1f + ROF_BONUS
    val bonusPercent = ((mult - 1f) * 100f).toInt().toFloat()
    if (index == 3) {
      return StatusData("weapon range +" + RANGE_BONUS.toInt() + "%", false)
    }
    if (index == 2) {
      return StatusData("rate of fire +" + bonusPercent.toInt() + "%", false)
    }
    //		if (index == 1) {
//			return new StatusData("ballistic flux use -" + (int) FLUX_REDUCTION + "%", false);
//		}
    if (index == 1) {
      return StatusData("weapon recoil -" + RECOIL_BONUS.toInt() + "%", false)
    }
    return if (index == 0 && PROJECTILE_SPEED_BONUS > 0) {
      StatusData("projectile speed +" + PROJECTILE_SPEED_BONUS.toInt() + "%", false)
    } else null
  }

  override fun getDisplayNameOverride(state: ShipSystemStatsScript.State, effectLevel: Float): String? {
    return if (state == ShipSystemStatsScript.State.IDLE || state == ShipSystemStatsScript.State.COOLDOWN) {
      "lidar array - passive"
    } else null
  }

  override fun getWeaponRangePercentMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
   return 0f
  }

  override fun getWeaponRangeMultMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    return 1f
  }

  override fun getWeaponRangeFlatMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    ship?:return 0f
    //只单独修改lidar武器的距离
    if(!weapon.spec.hasTag(Tags.LIDAR) || ship.system?.isActive == false) return 0f

    var lidarRange = 250f
    for (w in ship.allWeapons) {
      if (!w.isDecorative && w.slot.isHardpoint && !w.isBeam &&
        (w.type == WeaponType.BALLISTIC || w.type == WeaponType.ENERGY)
      ) {
        lidarRange = Math.max(lidarRange, w.range)
      }
    }
    lidarRange += 50f
    return lidarRange
  }
}