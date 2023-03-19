package data.shipsystems.scripts.ai

import com.fs.starfarer.api.Global
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import com.fs.starfarer.api.combat.ShipSystemAIScript
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.util.IntervalUtil
import org.lwjgl.util.vector.Vector2f
import data.shipsystems.scripts.aEP_FCLBurstScript
import org.lazywizard.lazylib.CollisionUtils
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils

class aEP_FCLBurstAI : aEP_BaseSystemAI() {

  override fun initImpl() {
    thinkTracker.setInterval(0.33f,0.33f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive = false
    target?:return

    var willing = 0f
    val weaponRange = Global.getSettings().getWeaponSpec(aEP_FCLBurstScript.WEAPON_ID).maxRange - 150f
    val hitPoint = CollisionUtils.getCollides(ship.location, getExtendedLocationFromPoint(ship.location, ship.facing, weaponRange), target.location, target.collisionRadius)
      ?: return

    //幅能不够用就直接滚了
    if(ship.maxFlux < 2000f || ship.maxFlux - ship.currFlux < 2000f) return
    val fluxLevelBelowThreshold = MathUtils.clamp(ship.currFlux/(ship.maxFlux - 2000f),0f,1f)

    willing += 70f *  fluxLevelBelowThreshold

    if (system.ammo > 2) willing += 20f
    if (system.ammo > 1) willing += 20f

    //对手即将过载，更想用了
    val targetRestFlux = target.maxFlux - ship.currFlux
    if(targetRestFlux <  5000f){
      willing += 50f * (5000f-targetRestFlux)/5000f
    }
    if (flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING)) willing -= 25f

    willing *= MathUtils.getRandomNumberInRange(0.75f, 1.25f)
    aEP_Tool.addDebugLog(willing.toString())
    if (willing >= 100f) {
      shouldActive = true
    }
  }

}