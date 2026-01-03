package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.DamageType
import data.scripts.utils.aEP_Tool
import data.scripts.utils.aEP_Tool.Util.checkIsAngleBlockedByShield
import data.scripts.shipsystems.aEP_SwitchPowerMode
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f

class aEP_SwitchPowerModeAI: aEP_BaseSystemAI() {

  companion object {
    // Tuning values (change these to tweak AI behavior)
    const val THINK_INTERVAL_MIN = 0.1f
    const val THINK_INTERVAL_MAX = 0.5f

    // When distance to target is greater than (checkRange + RANGE_MARGIN), prefer high-output
    const val RANGE_MARGIN = 50f


    // Fallback weapon range if no main weapon is found
    const val DEFAULT_CHECK_RANGE = 700f

    // Additional tuning constants requested
    // Treat shields as effectively down (so we can hit armor) when target flux level >= this
    const val TARGET_SHIELD_FAIL_FLUX = 0.9f

    // Ready margin: treat weapons with cooldownRemaining <= this as "ready" (in seconds)
    const val READY_COOLDOWN_MARGIN = 0.5f

    // Long cooldown threshold to qualify as a long-cooldown weapon
    const val LONG_COOLDOWN_THRESHOLD = 4.5f

    // 超过这个幅能水平，不会使用高出力
    const val SELF_FLUX_HEALTHY_THRESHOLD = 0.75f
    // 低于这个水平，会使用高出力模式/速射循环
    const val FLUX_AVOID_THRESHOLD = 0.75f

    // Armor vs penetration heuristics
    // pen/target armor < this, prefer high-output
    const val PEN_ARMOR_RATIO = 0.5f
  }

  override fun initImpl() {
    thinkTracker.setInterval(THINK_INTERVAL_MIN, THINK_INTERVAL_MAX)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {

    val currMode = (ship.customData[aEP_SwitchPowerMode.ID]?:0) as Int
    // compute current effective maximum weapon range for decision-making
    val checkRange = computeMaxWeaponRange()

    // decide desired mode (false -> mode0 rapid, true -> mode1 high-output)
    var desiredMode = false

    // prefer the explicit ship target if present
    var t = ship.shipTarget
    if(t == null){
      t = aEP_Tool.getNearestEnemyCombatShip(ship)
    }

    if(t != null){
      val distToTarget = MathUtils.getDistance(ship, t)

      // quick reject: fighters -> rapid mode
      if(t.isFighter){
        desiredMode = false
      } else {
        // find our highest DPH weapon (burst damage or fallback to dps)
        var bestPen = 0f
        for(w in ship.allWeapons){
          if(!aEP_Tool.isNormalWeaponSlotType(w.slot,false)) continue
          if(w.hasAIHint(WeaponAPI.AIHints.PD)) continue
          // only consider weapons whose weapon type is ENERGY for system applicability/range/cooldown decisions
          if(w.type != WeaponAPI.WeaponType.ENERGY) continue
          // ammo check
          if(w.maxAmmo > 0 && w.ammo <= 0) continue

          // compute DPH (handle beam/burst weapons specially)
          var dph =  w.derivedStats.dps
          if(w.isBeam){
            val burstDamage = w.derivedStats.burstDamage
            val burstDur = w.derivedStats.burstFireDuration
            if(burstDamage > 0f && burstDur > 0f) dph = burstDamage / burstDur
          }else{
            dph = w.derivedStats.damagePerShot
          }

          // compute simple penetration for this weapon based on damage type
          val penMult = when(w.damageType){
            DamageType.ENERGY -> 1f
            DamageType.HIGH_EXPLOSIVE -> 2f
            DamageType.KINETIC -> 0.5f
            else -> 1f
          }
          val pen = dph * penMult

          // choose best by penetration (pen); when selecting, also store its DPH
          if(pen > bestPen){
            bestPen = pen
          }
        }

        // Check shield availability and whether we can hit armor from our angle
        var shieldBlocksArmor = checkIsAngleBlockedByShield(t, ship.location)

        // 幅能太高时，认为护盾失效，直接打装甲
        if(t.fluxLevel >= TARGET_SHIELD_FAIL_FLUX) shieldBlocksArmor = false

        // target armor rating
        val targetArmorRating = t.armorGrid?.armorRating ?: t.hullSpec?.armorRating ?: 400f

        // 1) 穿甲力/地方装甲，越高越倾向于高输出模式
        val needMorePen = if(bestPen > 0f && targetArmorRating > 0f) (bestPen / targetArmorRating) < PEN_ARMOR_RATIO else false
        if(!shieldBlocksArmor && needMorePen){
          desiredMode = true
        }

        // 2) if target is beyond normal range but within extra range, and we have non-PD weapon ready -> high-output
        val extraRange = checkRange + aEP_SwitchPowerMode.ENERGY_WEAPON_RANGE_BONUS + RANGE_MARGIN
        if(distToTarget > checkRange && distToTarget <= extraRange){
          // check if ship has any non-PD weapon ready to fire
          var nonPdReady = false
          for(w in ship.allWeapons){
            if(w.hasAIHint(WeaponAPI.AIHints.PD)) continue
            if(w.type != WeaponAPI.WeaponType.ENERGY) continue
            // need to ensure weapon is able to fire and not on cooldown beyond margin
            if(w.maxAmmo > 0 && w.ammo <= 0) continue
            if(w.cooldownRemaining <= READY_COOLDOWN_MARGIN) { nonPdReady = true; break }
          }
          if(nonPdReady) desiredMode = true
        }

        // 3) self has relatively healthy flux level: only consider high-output if our fluxLevel < SELF_FLUX_HEALTHY_THRESHOLD
        val selfFluxHealthy = ship.fluxLevel < SELF_FLUX_HEALTHY_THRESHOLD
        if(!selfFluxHealthy) desiredMode = false

        // 4) if self healthy and we have long-cooldown, non-PD weapon ready or about to ready, switch to high-output
        if(selfFluxHealthy){
          var hasLongCooldownReady = false
          for(w in ship.allWeapons){
            if(w.hasAIHint(WeaponAPI.AIHints.PD)) continue
            if(w.type != WeaponAPI.WeaponType.ENERGY) continue
            // long-cooldown weapon
            if(w.cooldown >= LONG_COOLDOWN_THRESHOLD){
              // ready now or about to ready
              if(w.cooldownRemaining <= READY_COOLDOWN_MARGIN){
                hasLongCooldownReady = true
                break
              }
            }
          }
          //如果没有任何长冷却武器准备好，就不切换高出力模式，留在速射模式加速冷却
          if(hasLongCooldownReady) desiredMode = true
        }

      }
    } else {
      // no target found: prefer rapid for general defense / reacting to fighters/missiles
      desiredMode = false
    }


    // now set shouldActive: system activation toggles the mode once, so request activation only when desiredMode differs from current mode
    val currIsHighOutput = (currMode == 1)
    shouldActive = (desiredMode != currIsHighOutput)

  }

  // Return the maximum range among the ship's normal (non-PD, non-decorative) weapons.
  fun computeMaxWeaponRange(): Float{
    var maxRange = 0f
    for(w in ship.allWeapons){
      if(!aEP_Tool.isNormalWeaponSlotType(w.slot,false)) continue
      if(w.hasAIHint(WeaponAPI.AIHints.PD)) continue
      if(w.type != WeaponAPI.WeaponType.ENERGY) continue
      if(w.range > maxRange) maxRange = w.range
    }
    return if(maxRange > 0f) maxRange else DEFAULT_CHECK_RANGE
  }

}