package data.shipsystems.scripts

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_BBLockOn : BaseShipSystemScript(){
  companion object{
    const val WEAPON_FLUX_INCREASE_PERCENT = 100f;
    const val SYSTEM_RANGE = 1500f;
  }

  var target : ShipAPI? = null
  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制沾粘这行
    val ship = (stats?.entity?: return)as ShipAPI
    if(target == null) target = ship.shipTarget
    target?: return
    target!!.mutableStats.energyWeaponFluxCostMod.modifyPercent(id,WEAPON_FLUX_INCREASE_PERCENT*effectLevel)
    target!!.mutableStats.ballisticWeaponFluxCostMod.modifyPercent(id,WEAPON_FLUX_INCREASE_PERCENT*effectLevel)
    target!!.mutableStats.missileWeaponFluxCostMod.modifyPercent(id,WEAPON_FLUX_INCREASE_PERCENT*effectLevel)
    target!!.setJitter(id, Color(80,160,235,25),1f*effectLevel,12,0f)
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
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
    val range = ship.mutableStats.systemRangeBonus.computeEffective(SYSTEM_RANGE)
    if(ship.shipTarget != null) {
      if(MathUtils.getDistance(ship.shipTarget,ship) < range){
        return "Ready"
      } else{
        return "Out of Range"
      }
    }
    return "Not Ready"
  }
}