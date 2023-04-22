package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.IntervalUtil
import combat.util.aEP_DataTool.txt
import combat.util.aEP_Tool
import java.awt.Color

class aEP_BBLockOn : BaseShipSystemScript(){
  companion object{
    const val WEAPON_FLUX_INCREASE_PERCENT = 100f;
    const val SYSTEM_RANGE = 1500f;
    val JITTER_COLOR = Color(80,160,235,25)
    var TEXT_COLOR = Color(120,190,255,215)
  }

  val textTracker = IntervalUtil(3f,3f)
  var target : ShipAPI? = null
  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制沾粘这行
    val ship = (stats?.entity?: return)as ShipAPI
    if(target == null) target = ship.shipTarget
    val target = target?: return
    target.mutableStats.energyWeaponFluxCostMod.modifyPercent(id, WEAPON_FLUX_INCREASE_PERCENT)
    target.mutableStats.ballisticWeaponFluxCostMod.modifyPercent(id, WEAPON_FLUX_INCREASE_PERCENT)
    target.mutableStats.missileWeaponFluxCostMod.modifyPercent(id, WEAPON_FLUX_INCREASE_PERCENT)
    target.setJitter(id, JITTER_COLOR,1f*effectLevel,12,0f)

    textTracker.advance(aEP_Tool.getAmount(ship))
    if(textTracker.intervalElapsed()){
      Global.getCombatEngine().addFloatingText(target.location,txt(this.javaClass.simpleName+"01"),20f, TEXT_COLOR,target,0.5f,2f)
    }
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    textTracker.forceIntervalElapsed()

    target ?: return
    target!!.mutableStats.energyWeaponFluxCostMod.unmodify(id)
    target!!.mutableStats.ballisticWeaponFluxCostMod.unmodify(id)
    target!!.mutableStats.missileWeaponFluxCostMod.unmodify(id)
    target == null
  }

  override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {
    return aEP_Tool.checkTargetWithinSystemRange(ship, SYSTEM_RANGE)
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String {
    if(ship.shipTarget != null) {
      if(aEP_Tool.checkTargetWithinSystemRange(ship, SYSTEM_RANGE)){
        return "In Range"
      } else{
        return "Out of Range"
      }
    }
    return "Need Target"
  }
}