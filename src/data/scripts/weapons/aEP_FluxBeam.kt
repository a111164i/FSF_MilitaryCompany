package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.IntervalUtil
import combat.util.aEP_Tool
import data.scripts.hullmods.aEP_MarkerDissipation
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_FluxBeam : BeamEffectPlugin {

  companion object{
    const val FSF_BONUS = 2f
    const val MAX_SPEED_DIS_CAP = 1f

  }

  var fluxDrain = 100f
  var damage = 200f
  val damageTimer = IntervalUtil(0.25f,0.25f)

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


        val target = pickTarget(beam.damageTarget as ShipAPI)
        //光束一秒10次
        var fluxDecrease = fluxDrain * 0.1f


        //阻尼，堡垒盾这种系统产生硬幅能，防止永动机，需要检测
        if (target.system?.isActive == true) {
          val spec = target.system.specAPI
          if (!spec.isHardDissipationAllowed || !spec.isDissipationAllowed) {
            fluxDecrease = 0f
          }
        }

        //单条Beam的每秒吸取量不能超过目标当前幅散的1倍,防止有些系统会降低幅能耗散
        //val targetCurrDis = aEP_Tool.getRealDissipation(target) * 0.1f * MAX_SPEED_DIS_CAP
        //fluxDecrease = MathUtils.clamp(fluxDecrease, 0f, targetCurrDis)

        if (beam.source is ShipAPI) {
          val fluxRest = beam.source.maxFlux - beam.source.currFlux
          val targetFlux = target.currFlux
          //计算fsf加成
          if(beam.source.variant?.hasHullMod(aEP_MarkerDissipation.ID) == true){
            fluxDecrease *= FSF_BONUS
            fluxDecrease = fluxDecrease.coerceAtMost(fluxRest)
            fluxDecrease = fluxDecrease.coerceAtMost(targetFlux)
            target.fluxTracker.decreaseFlux(-fluxDecrease)
            beam.source.fluxTracker.increaseFlux(fluxDecrease/ FSF_BONUS, true)
          }else{ //正常加成
            fluxDecrease = fluxDecrease.coerceAtMost(fluxRest)
            fluxDecrease = fluxDecrease.coerceAtMost(targetFlux)
            target.fluxTracker.decreaseFlux(fluxDecrease)
            beam.source.fluxTracker.increaseFlux(fluxDecrease, true)
          }
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

  fun pickTarget(ship: ShipAPI): ShipAPI{

    //把本船，本船的模块，本船的母舰，统统视为一个整体
    val checkShipList = ArrayList<ShipAPI>()
    if(ship.isStationModule && ship.parentStation != null){
      checkShipList.add(ship.parentStation)
      checkShipList.addAll(ship.parentStation.childModulesCopy)
    }
    if(ship.isShipWithModules && ship.childModulesCopy.size > 0){
      checkShipList.add(ship)
      checkShipList.addAll(ship.childModulesCopy)
    }

    //找当前幅能最高的模块，如果本体的幅能超过50%直接优先选择本体
    var maxFluxLevel = 0f
    var toReturn = checkShipList.get(0)
    if(toReturn.fluxLevel > 0.5f){
      return toReturn
    }
    for ( s in checkShipList){
      if(s.fluxLevel > maxFluxLevel){
        maxFluxLevel = s.fluxLevel
        toReturn = s
      }
    }



    return toReturn
  }
}