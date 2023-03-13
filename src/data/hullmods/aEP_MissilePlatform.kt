package data.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import combat.util.aEP_DataTool
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.WeaponAPI
import java.util.WeakHashMap
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.util.IntervalUtil
import org.lazywizard.lazylib.MathUtils
import java.awt.Color
import java.lang.Math.abs
import java.util.ArrayList

class aEP_MissilePlatform : aEP_BaseHullMod() {

  companion object {
    private const val MISSILE_HITPOINT_BUFF = 10f //by percent
    private const val MISSILE_SPEED_BUFF = 10f //by percent
    private const val MISSILE_ROF_BUFF = -0f //by percent
    private const val ZEROFLUX_SPEED_BUFF = -20f //0.1 means 1 sec produce 0.1 percent
    private val MAX_RELOAD_SPEED = HashMap<WeaponAPI.WeaponSize,Float>()
    init {
      MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.LARGE] = 0.08f
      MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.MEDIUM] = 0.06f
      MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.SMALL] = 0.05f
    }
  }

  init {
    notCompatibleList.add("missleracks")
  }
  val ammoLoaderTracker = IntervalUtil(1f,1f)

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if (ship?.mutableStats == null) return

    val stats = ship.mutableStats
    stats.missileHealthBonus.modifyPercent(id, MISSILE_HITPOINT_BUFF)
    stats.missileMaxSpeedBonus.modifyPercent(id, MISSILE_SPEED_BUFF)
    stats.missileRoFMult.modifyPercent(id, MISSILE_ROF_BUFF)
    stats.zeroFluxSpeedBoost.modifyFlat(id, ZEROFLUX_SPEED_BUFF)
    if (!ship.hasListenerOfClass(LoadingMap::class.java)) {
      ship.addListener(LoadingMap(ship))
    }
  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return  true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    //tooltip.addGrid( 5 * 5f + 10f);
    tooltip.addPara("- " + aEP_DataTool.txt("missile_health_up") + "{%s}", 5f, Color.white, Color.green, MISSILE_HITPOINT_BUFF.toInt().toString() + "%")
    tooltip.addPara("- " + aEP_DataTool.txt("MP_des04") + "{%s}", 5f, Color.white, Color.green, MISSILE_HITPOINT_BUFF.toInt().toString() + "%")
    tooltip.addPara("- " + aEP_DataTool.txt("MP_des01"), 5f, Color.white, Color.green, String.format("%.1f", MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.SMALL]?.times(100) ?: 0))
    tooltip.addPara("- " + aEP_DataTool.txt("MP_des02"), 5f, Color.white, Color.green, String.format("%.1f", MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.MEDIUM]?.times(100) ?: 0))
    tooltip.addPara("- " + aEP_DataTool.txt("MP_des03"), 5f, Color.white, Color.green, String.format("%.1f", MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.LARGE]?.times(100) ?: 0))
    //实际上就是把原本扩展架的量慢慢给玩家，不需要再限制射速了
    //tooltip.addPara("- " + aEP_DataTool.txt("MP_des05") + "{%s}", 5f, Color.white, Color.red, abs(MISSILE_ROF_BUFF).toInt().toString() + "%")
    tooltip.addPara("- " + aEP_DataTool.txt("zero_flux_speed_reduce") + "{%s}", 5f, Color.white, Color.red, Math.abs(ZEROFLUX_SPEED_BUFF).toInt().toString() + "")
    tooltip.addPara("- " + aEP_DataTool.txt("not_compatible") + "{%s}", 5f, Color.white, Color.red, Global.getSettings().getHullModSpec("missleracks").displayName)
  }

  private inner class LoadingMap constructor(var ship: ShipAPI) : AdvanceableListener {
    var MPTimerMap: MutableMap<WeaponAPI, Float> = WeakHashMap()
    override fun advance(amount: Float) {
      if (ship.currentCR < 0.4f) {
        return
      }

      ammoLoaderTracker.advance(amount)
      if(!ammoLoaderTracker.intervalElapsed()) return
      for (w in ship.allWeapons) {
        val slot = w.slot
        if (!slot.isBuiltIn && !slot.isDecorative && !slot.isHidden && !slot.isSystemSlot) {
          if (w.type == WeaponAPI.WeaponType.MISSILE && w.usesAmmo()) {
            val opPerMissile = w.spec.getOrdnancePointCost(null)/w.spec.maxAmmo
            val reloadSpeed = MAX_RELOAD_SPEED[w.size]?.div(opPerMissile)
            putInMap(w, reloadSpeed?:0f, 1f)
          }
        }
      }
    }

    private fun putInMap(w: WeaponAPI, reloadSpeed: Float, amount: Float) {
      //Global.getCombatEngine().addFloatingText(w.getLocation(),reloadSpeed+"",20f,new Color(100,100,100,100),w.getShip(),1f,5f);
      if (MPTimerMap[w] == null) {
        MPTimerMap[w] = 0f
      } else {
        val toReload = MPTimerMap[w]!! + reloadSpeed * amount
        if (toReload >= w.ammoTracker.reloadSize.toInt()) {
          MPTimerMap[w] = (toReload - w.ammoTracker.reloadSize.toInt())
          w.ammo = Math.min(w.ammo + w.ammoTracker.reloadSize.toInt(), w.maxAmmo)
        } else MPTimerMap[w] = toReload
      }
    }
  }
}