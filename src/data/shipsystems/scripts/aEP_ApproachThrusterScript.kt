package data.shipsystems.scripts

import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import combat.impl.VEs.aEP_MovingSprite
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import data.scripts.weapons.aEP_DecoAnimation
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_ApproachThrusterScript : BaseShipSystemScript() {
  //这个控制一次充能可以加速多久
  var consumeTimer = IntervalUtil(3f, 3f)
  var activeCompensation = 0
  var didUse = false
  override fun apply(stats: MutableShipStatsAPI?, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //stats的entity有可能为null
    val ship = (stats?.entity?: return)as ShipAPI

    didUse = true
    if(activeCompensation > 0){
      ship.system.ammo = (ship.system.ammo + activeCompensation).coerceAtMost(ship.system.maxAmmo)
      activeCompensation = 0
    }

    val amount = aEP_Tool.getAmount(ship)
    stats.maxSpeed.modifyFlat(id, effectLevel * MAX_SPEED_BONUS)
    stats.acceleration.modifyFlat(id, effectLevel * MAX_SPEED_BONUS)
    consumeTimer.advance(amount * effectLevel)
    if (consumeTimer.intervalElapsed()) {
      ship.system.ammo = (ship.system.ammo - 1).coerceAtLeast(0);
    }
    val leftAmmo = ship.system.ammo

    if (leftAmmo <= 0) {
      ship.system.deactivate()
    }
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    //stats的entity有可能为null
    val ship = (stats?.entity?: return)as ShipAPI
    activeCompensation += 1
    if(ship.system.ammo <= 0 ||  didUse){
      controlDeco(ship,true)
      didUse = false
      if(ship.system.ammo > 0){
        addEffect(ReloadTank(ship))
      }

    }
  }

  fun controlDeco(ship:ShipAPI, on:Boolean){
    //控制装饰武器
    var oilL: WeaponAPI? = null
    var oilR: WeaponAPI? = null
    for (weapon in ship.allWeapons) {
      if (weapon.slot.id.contains("OL01")) {
        oilL = weapon
      }
      if (weapon.slot.id.contains("OL02")) {
        oilR = weapon
      }
      if (weapon.slot.id.contains("HD01")) {
        oilL?: continue
        if(on){
          oilL.animation.frame = 1
          val ms = aEP_MovingSprite(oilL.location, Vector2f(18f,62f),ship.facing-90f, "graphics/weapons/aEP_hailiang/Oil00.png")
          ms.setInitVel(aEP_Tool.speed2Velocity(ship.facing, -180f))
          ms.setInitVel(ship.velocity)
          ms.stopSpeed = 0.9f
          ms.angleSpeed = MathUtils.getRandomNumberInRange(-25f, 25f)
          ms.lifeTime = 10f
          ms.fadeIn = 0f
          ms.fadeOut = 0.1f
          ms.color = Color(255,255,255)
          aEP_CombatEffectPlugin.addEffect(ms)
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToLevel(1f)
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToSideLevel(1f)
        }else{
          oilL.animation.frame = 0
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToLevel(0f)
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToSideLevel(0f)
        }
      }
      if (weapon.slot.id.contains("HD02")) {
        oilR?: continue
        if(on){
          oilR.animation.frame = 1
          val ms = aEP_MovingSprite(oilR.location, Vector2f(18f,62f),ship.facing-90f, "graphics/weapons/aEP_hailiang/Oil00.png")
          ms.setInitVel(aEP_Tool.speed2Velocity(ship.facing, -180f))
          ms.setInitVel(ship.velocity)
          ms.stopSpeed = 0.9f
          ms.angleSpeed = MathUtils.getRandomNumberInRange(-25f, 25f)
          ms.lifeTime = 10f
          ms.fadeIn = 0f
          ms.fadeOut = 0.1f
          ms.color = Color(255,255,255)
          aEP_CombatEffectPlugin.addEffect(ms)
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToLevel(1f)
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToSideLevel(1f)
        }else{
          oilR.animation.frame = 0
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToLevel(0f)
          (weapon.effectPlugin as aEP_DecoAnimation).setMoveToSideLevel(0f)
        }
      }
    }
  }

  inner class ReloadTank(val ship: ShipAPI) : aEP_BaseCombatEffect(1f,ship){
    override fun readyToEnd() {
      controlDeco(ship,false)
    }
  }

  companion object {
    const val MAX_SPEED_BONUS = 150f
  }
}