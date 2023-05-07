package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.IntervalUtil
import data.scripts.hullmods.aEP_MarkerDissipation
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_FluxBeam : BeamEffectPlugin {

  companion object{
    const val FSF_BONUS = 1.5f
  }

  var fluxDrain = 75f
  var damage = 200f
  val damageTimer = IntervalUtil(0.5f,0.5f)

  init {
    val hlString = Global.getSettings().getWeaponSpec("aEP_ftr_ut_supply_main").customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) fluxDrain = num.toFloat()
      if(i == 2) damage = num.toFloat()
      i += 1
    }
  }

  override fun advance(amount: Float, engine: CombatEngineAPI, beam: BeamAPI) {
    if (beam.didDamageThisFrame()){
      if(beam.damageTarget is ShipAPI) {
        //粒子特效
        for (i in 0 until 4) {
          val particleStartPoint = Vector2f(beam.from.x - beam.to.x, beam.from.y - beam.to.y)
          particleStartPoint.scale(0.65f)
          particleStartPoint.set(beam.to.x + particleStartPoint.x, beam.to.y + particleStartPoint.y)
          val loc = MathUtils.getRandomPointOnLine(beam.to, particleStartPoint)
          loc.set(MathUtils.getRandomPointInCircle(loc, 50f))
          val vel = Vector2f(beam.from.x - loc.x, beam.from.y - loc.y)
          vel.scale(2f)
          engine.addSmoothParticle(
            loc,
            vel,
            MathUtils.getRandomNumberInRange(15f, 30f),
            1f,
            MathUtils.getRandomNumberInRange(0.25f, 0.5f),
            beam.fringeColor
          )
        }


        val target = beam.damageTarget as ShipAPI
        //光束一秒10次
        var fluxDecrease = fluxDrain * 0.1f
        //FSF加成
        if(target.variant?.hasHullMod(aEP_MarkerDissipation.ID) == true) fluxDecrease *= FSF_BONUS


        //阻尼，堡垒盾这种系统产生硬幅能，防止永动机，需要检测
        if (target.system?.isActive == true) {
          val spec = target.system.specAPI
          if (!spec.isDissipationAllowed) {
            fluxDecrease = 0f
          }
        }


        if (beam.source is ShipAPI) {
          val fluxRest = beam.source.maxFlux - beam.source.currFlux
          val targetFlux = target.currFlux
          fluxDecrease = fluxDecrease.coerceAtMost(fluxRest)
          fluxDecrease = fluxDecrease.coerceAtMost(targetFlux)

          beam.source.fluxTracker.increaseFlux(fluxDecrease, true)
          target.fluxTracker.decreaseFlux(fluxDecrease)
        }


      }else{
        damageTimer.advance(0.1f)
        if(damageTimer.intervalElapsed()){
          engine.applyDamage(
            beam.damageTarget,
            beam.damageTarget.location,
            damage,
            DamageType.FRAGMENTATION,
            0f,
            false,
            false,
            beam.source)
        }
      }


    }

  }

}