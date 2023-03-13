package data.shipsystems.scripts

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.IntervalUtil
import combat.util.aEP_Tool
import java.awt.Color

class aEP_FuelBurn: BaseShipSystemScript() {
  //这个控制一次充能可以加速多久
  var consumeTimer = IntervalUtil(1f, 1f)
  val fuelUse = 10f;
  var activeCompensation = 1
  override fun apply(stats: MutableShipStatsAPI?, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //stats的entity有可能为null
    val ship = (stats?.entity?: return)as ShipAPI
    val amount = aEP_Tool.getAmount(ship)

    consumeTimer.advance(amount * effectLevel)
    if (consumeTimer.intervalElapsed()) {
      if(activeCompensation > 0){
        ship.system.ammo = (ship.system.ammo + 1).coerceAtMost(ship.system.maxAmmo)
        activeCompensation = 0
      }
      ship.system.ammo = (ship.system.ammo - 1).coerceAtLeast(0)
      var fuelNow = 0f;
      //只有玩家舰队在生涯非模拟中才适用
      if(aEP_Tool.isShipInPlayerFleet(ship,false)){
        fuelNow = Global.getSector().playerFleet.cargo.fuel
        if(fuelNow > fuelUse) {
          Global.getSector().playerFleet.cargo.removeFuel(fuelUse)
          val toAdd = String.format("%s",fuelUse.toInt())
          Global.getCombatEngine().addFloatingText(
            ship.location,
            "lost $toAdd fuel",
            20f,
            Color(200, 50, 50, 240),
            ship,
            0.25f, 120f
          )
        } else {
          ship.system.ammo = 0
          ship.system.deactivate()
        }
      }


    }
    //modify here
    stats.maxSpeed.modifyFlat(id, effectLevel * MAX_SPEED_BONUS)
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    //stats的entity有可能为null
    val ship = (stats?.entity?: return)as ShipAPI
    activeCompensation += 1

    //modify here
    stats.maxSpeed.unmodify(id)
  }

  override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI?): Boolean {
    ship?: return false
    if(aEP_Tool.isShipInPlayerFleet(ship,true)){
      var fuelNow = Global.getSector()?.playerFleet?.cargo?.fuel?:999f
      if(fuelNow <= fuelUse){
        return false
      }
    }
    return true
  }

  override fun getInfoText(system: ShipSystemAPI?, ship: ShipAPI?): String {
    ship?: return ""
    if(aEP_Tool.isShipInPlayerFleet(ship,true)){
      var fuelNow = Global.getSector()?.playerFleet?.cargo?.fuel?:999f
      if(fuelNow <= fuelUse){
        return "Out of Fuel"
      }
    }
    return ""
  }

  companion object {
    const val MAX_SPEED_BONUS = 180f
  }

}