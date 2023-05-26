package data.scripts.shipsystems

import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import combat.util.aEP_Tool.Util.isNormalWeaponType
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import data.scripts.shipsystems.aEP_NCReloadScript.RefresherOrb
import com.fs.starfarer.api.combat.WeaponAPI
import combat.impl.VEs.aEP_MovingSmoke
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Tool
import data.scripts.hullmods.aEP_MissilePlatform
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_RequanReload : BaseShipSystemScript() {

  companion object {
    const val RELOAD_RATE_ADD = 0.12f
    const val COOLDOWN_REDUCE = 10f
  }

  var didVisual = false

  override fun apply(stats: MutableShipStatsAPI?, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI


    //使用的一瞬间启用特效
    if (!didVisual) {
      addEffect(RefresherOrb(ship))
      didVisual = true
    }

    //effectLevel == 1f 时运行一次
    if (effectLevel < 1f) return
    
    //回满总装填率
    if(ship.customData.containsKey(aEP_MissilePlatform.ID)){
      val loadingClass = ship.customData[aEP_MissilePlatform.ID] as aEP_MissilePlatform.LoadingMap
      val rateAdd = MathUtils.clamp(RELOAD_RATE_ADD * loadingClass.maxRate,
        0f,loadingClass.maxRate-loadingClass.currRate)
      loadingClass.currRate += rateAdd
    }

    for (w in ship.allWeapons) {
      //少量减少内置导弹cd
      if (w.type == WeaponAPI.WeaponType.MISSILE) {
        if (w.slot.weaponType == WeaponAPI.WeaponType.MISSILE || w.slot.weaponType == WeaponAPI.WeaponType.MISSILE) {
          w.setRemainingCooldownTo(w.cooldownRemaining - COOLDOWN_REDUCE.coerceAtMost(w.cooldownRemaining))
          //来点烟雾
          for (i in 0 until 3) {
            //add cloud
            val colorMult = (Math.random() * 40f).toInt() + 40
            val sizeMult = Math.random().toFloat()
            val ms = aEP_MovingSmoke(w.location)
            ms.setInitVel(aEP_Tool.speed2Velocity(MathUtils.getRandomNumberInRange(0f, 360f), 1.2f))
            ms.lifeTime = 2.5f
            ms.fadeIn = 0.1f
            ms.fadeOut = 0.6f
            ms.size = 20 + 40 * sizeMult
            ms.color = Color(colorMult, colorMult, colorMult, (Math.random() * 40).toInt() + 200)
            addEffect(ms)
          }

        }else if(w.spec.weaponId.contains("aEP_cru_requan_missile")){ //回复系统导弹
          w.ammoTracker.ammo = Math.min(w.ammo + w.spec.burstSize, w.maxAmmo)
        }


      }
    }
  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    didVisual = false
  }

  override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI?): Boolean {
    //复制粘贴这行
    if(ship == null) return false

    if(ship.fluxTracker.maxFlux - ship.fluxTracker.currFlux < ship.system.fluxPerSecond) {
      return false
    }
    return true
  }
}