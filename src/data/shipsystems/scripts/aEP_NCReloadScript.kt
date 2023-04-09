package data.shipsystems.scripts

import com.fs.starfarer.api.Global
import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import combat.util.aEP_Tool.Util.speed2Velocity
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import combat.plugin.aEP_CombatEffectPlugin
import data.shipsystems.scripts.aEP_NCReloadScript.RefresherOrb
import com.fs.starfarer.api.combat.WeaponAPI
import data.shipsystems.scripts.aEP_NCReloadScript
import combat.impl.VEs.aEP_MovingSmoke
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.api.combat.CombatEntityAPI
import combat.impl.aEP_BaseCombatEffect
import combat.util.aEP_ID
import data.hullmods.aEP_MissilePlatform
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.ArrayList

class aEP_NCReloadScript : BaseShipSystemScript() {
  var didVisual = false


  companion object {
    const val RELOAD_COOLDOWN_MULT = 0.25f //0.25 = 75 percent off
    const val GLOW_INTERVAL = 0.05f
    const val GLOW_TIME = 0.5f
    const val GLOW_SIZE = 25f
    const val GLOW_SIZE_INTERVAL = 30f
  }



  override fun apply(stats: MutableShipStatsAPI?, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI

    //创造刷新球特效
    if (!didVisual) {
      addEffect(RefresherOrb(ship))
      didVisual = true
    }

    //effectLevel == 1f 时运行一次
    if (effectLevel < 1f) return

    //回满总装填率
    if(ship.customData.containsKey(aEP_MissilePlatform.ID)){
      val loadingClass = ship.customData[aEP_MissilePlatform.ID] as aEP_MissilePlatform.LoadingMap
      loadingClass.currRate = loadingClass.maxRate
    }

    //为每个武器刷出烟雾
    for (w in ship.allWeapons) {
      if (w.type == WeaponAPI.WeaponType.MISSILE && w.slot.weaponType == WeaponAPI.WeaponType.MISSILE) {
        for (i in 0 until 5) {
          //add cloud
          val colorMult = (Math.random() * 40f).toInt() + 40
          val sizeMult = Math.random().toFloat()
          val ms = aEP_MovingSmoke(w.location)
          ms.setInitVel(speed2Velocity(MathUtils.getRandomNumberInRange(0f, 360f), 1.2f))
          ms.lifeTime = 3f
          ms.fadeIn = 0.1f
          ms.fadeOut = 0.5f
          ms.size = 20 + 40 * sizeMult
          ms.color = Color(colorMult, colorMult, colorMult, (Math.random() * 40).toInt() + 200)
          addEffect(ms)
        }
      }
    }


  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    didVisual = false
  }


  class RefresherOrb internal constructor(var target: CombatEntityAPI) : aEP_BaseCombatEffect() {
    var length: Float
    var advanceDist: Float
    var glowTimer = GLOW_INTERVAL
    override fun advance(amount: Float) {
      super.advance(amount)
      if (length > target.collisionRadius * 3f) {
        cleanup()
      }
      glowTimer = glowTimer + amount
      if (glowTimer > GLOW_INTERVAL) {
        glowTimer = glowTimer - GLOW_INTERVAL
        var xLength = length
        var yLength = target.collisionRadius
        while (yLength >= -target.collisionRadius) {
          val toSpawn = Vector2f(target.location.x + xLength, target.location.y + yLength)
          if (MathUtils.getDistance(toSpawn, target.location) <= target.collisionRadius) {
            var alpha = ((1 - MathUtils.getDistance(toSpawn, target.location) / target.collisionRadius) * 250).toInt()
            alpha = MathUtils.clamp(alpha, 0, 250)
            Global.getCombatEngine().addSmoothParticle(
              toSpawn,
              aEP_ID.VECTOR2F_ZERO,
              GLOW_SIZE, 1f,
              GLOW_TIME,
              Color(0, 250, 0, alpha)
            )
            Global.getCombatEngine().addSmoothParticle(
              toSpawn,
              aEP_ID.VECTOR2F_ZERO,
              GLOW_SIZE, 1f,
              GLOW_TIME,
              Color(0, 250, 0, alpha)
            )
          }
          yLength -= advanceDist
          xLength -= advanceDist
        }
        length += advanceDist
      }
      super.advanceImpl(amount)
    }

    init {
      length = -target.collisionRadius
      val num = (target.collisionRadius * 2f / GLOW_SIZE_INTERVAL).toInt()
      advanceDist = GLOW_SIZE_INTERVAL + target.collisionRadius * 2f % GLOW_SIZE_INTERVAL / num
    }
  }

}