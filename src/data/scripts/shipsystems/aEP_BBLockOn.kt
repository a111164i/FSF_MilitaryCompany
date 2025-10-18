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
import combat.util.aEP_Tool.Util.getInfoTextWithinSystemRange
import java.awt.Color

class aEP_BBLockOn : BaseShipSystemScript(){
  companion object{
    const val WEAPON_FLUX_INCREASE_PERCENT = 200f;
    const val SYSTEM_RANGE = 1500f;
    val JITTER_COLOR = Color(20,40,225,35)
    var TEXT_COLOR = Color(120,190,255,250)
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
    target.setJitter(id, JITTER_COLOR,1f*effectLevel,12,5f)

    textTracker.advance(aEP_Tool.getAmount(ship))
    if(textTracker.intervalElapsed()){
      Global.getCombatEngine().addFloatingText(target.location,txt("aEP_BBLockOn01"),20f, TEXT_COLOR,target,1f,1f)
    }
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    textTracker.forceIntervalElapsed()

    target ?: return
    target!!.mutableStats.energyWeaponFluxCostMod.unmodify(id)
    target!!.mutableStats.ballisticWeaponFluxCostMod.unmodify(id)
    target!!.mutableStats.missileWeaponFluxCostMod.unmodify(id)
    target = null
  }

  override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {
    val dist = aEP_Tool.checkTargetWithinSystemRange(ship, ship.shipTarget?.location, SYSTEM_RANGE, ship.shipTarget?.collisionRadius)
    return dist <= 0f
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String {
    return getInfoTextWithinSystemRange(ship, ship.shipTarget?.location, SYSTEM_RANGE,ship.shipTarget?.collisionRadius)
  }
}