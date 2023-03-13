package data.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import combat.util.aEP_DataTool
import java.awt.Color

class aEP_HotLoader : aEP_BaseHullMod() {

  companion object {
    //private final static float RELOAD_THRESHOLD = 2;//by seconds
    const val RELOAD_PERCENT = 0.25f
    const val EXTRA_SPEED_ON_FIRE = 0.25f
    const val EXTRA_SPEED_ON_SYSTEM = 0.5f
    const val id = "aEP_HotLoader"
    val AMMO_FEEDER = ArrayList<String>()
    init {
      AMMO_FEEDER.add("aEP_ExtremeOverload")
      AMMO_FEEDER.add("aEP_ZLAmmoFeed")
    }
  }

  init {
    notCompatibleList.add("magazines")
    haveToBeWithMod.add("aEP_MarkerDissipation")
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship?.mutableStats ?: return
    if (!ship.hasListenerOfClass(AmmoReloadFaster::class.java)) {
      ship.addListener(AmmoReloadFaster(ship))
    }
  }

  internal class AmmoReloadFaster(var ship: ShipAPI) : AdvanceableListener {
    var reloadingMap: MutableMap<WeaponAPI, Float> = HashMap()
    override fun advance(amount: Float) {
      val bufferLevel = aEP_MarkerDissipation.getBufferLevel(ship)
      var extra = EXTRA_SPEED_ON_FIRE * bufferLevel
      //如果战术系统是高压填弹，再加百分之50速度，暂时封印，太强了
      if(AMMO_FEEDER.contains(ship.hullSpec.shipSystemId) && ship.system.isActive){
        //extra += EXTRA_SPEED_ON_SYSTEM * ship.system.effectLevel
      }
      for (w in ship.allWeapons) {
        if (w.usesAmmo() && !w.slot.isDecorative && !w.slot.isBuiltIn && !w.slot.isSystemSlot) {
          var weaponTimer = 0f
          if (!reloadingMap.containsKey(w)) {
            reloadingMap[w] = 0f
          }
          weaponTimer = reloadingMap[w]!!
          //根据不同的武器类型计算当前真实的弹药恢复速度
          var ammoPerSecond = w.ammoPerSecond
          //Global.getLogger(this.javaClass).info(ammoPerSecond)
          if(w.type == WeaponAPI.WeaponType.BALLISTIC){
            ammoPerSecond *= ship.mutableStats.ballisticAmmoRegenMult.modifiedValue
          } else if(w.type == WeaponAPI.WeaponType.ENERGY){
            ammoPerSecond *= ship.mutableStats.energyAmmoRegenMult.modifiedValue
          }
          weaponTimer += ammoPerSecond * (RELOAD_PERCENT + extra) * amount
          val reloadSize = w.ammoTracker.reloadSize
          if (weaponTimer >= reloadSize) {
            weaponTimer -= reloadSize
            w.ammo = Math.min(w.ammo + reloadSize.toInt(), w.maxAmmo)
          }
          reloadingMap[w] = weaponTimer
        }
      }
    }

  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    //val image = tooltip!!.beginImageWithText(Global.getSettings().getHullModSpec("aEP_HotLoader").spriteName, 48f)
    //image.addPara("- " + aEP_DataTool.txt("ammo_regen_speed_up") + "{%s}", 5f, Color.white, Color.green, (EXTRA_SPEED_ON_FIRE * 100).toInt().toString() + "%")
    //战术系统匹配时，不现实，暂时封印，选择在f的时候加弹药恢复速度
    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"), Alignment.MID, 5f)
    val image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec("aEP_HotLoader").spriteName, 48f)
    image.addPara("- " + aEP_DataTool.txt("ammo_regen_speed_up") + "{%s}", 5f, Color.white, Color.green, (EXTRA_SPEED_ON_FIRE * 100).toInt().toString() + "%")
    tooltip.addImageWithText(5f)
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
    if (index == 0) return "" + (RELOAD_PERCENT * 100).toInt() + "%"
    if (index == 0) return "" + (EXTRA_SPEED_ON_SYSTEM * 100).toInt() + "%"
    return ""
  }

}