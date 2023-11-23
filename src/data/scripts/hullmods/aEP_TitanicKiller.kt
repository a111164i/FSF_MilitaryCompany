package data.scripts.hullmods

import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier
import com.fs.starfarer.api.impl.campaign.ids.Stats
import org.lwjgl.util.vector.Vector2f

class aEP_TitanicKiller: aEP_BaseHullMod(), DamageDealtModifier {
  companion object{
    const val ID = "aEP_TitanicKiller"

    const val DAMAGE_MULTIPLIER = 1.15f
    const val MAX_DAMAGE_MULTIPLIER = 3f

    val START_DP = LinkedHashMap<ShipAPI.HullSize, Float>()
    init {
      START_DP[ShipAPI.HullSize.CAPITAL_SHIP] = 45f
      START_DP[ShipAPI.HullSize.CRUISER] = 24f
      START_DP[ShipAPI.HullSize.DESTROYER] = 12f
      START_DP[ShipAPI.HullSize.FRIGATE] = 6f
    }

    val DP_STEP = LinkedHashMap<ShipAPI.HullSize, Float>()
    init {
      DP_STEP[ShipAPI.HullSize.CAPITAL_SHIP] = 5f
      DP_STEP[ShipAPI.HullSize.CRUISER] = 4f
      DP_STEP[ShipAPI.HullSize.DESTROYER] = 3f
      DP_STEP[ShipAPI.HullSize.FRIGATE] = 2f
    }
  }


  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String {
    if (index == 0){
      return String.format("%.0f/%.0f/%.0f/%.0f",
        START_DP[ShipAPI.HullSize.FRIGATE],
        START_DP[ShipAPI.HullSize.DESTROYER],
        START_DP[ShipAPI.HullSize.CRUISER],
        START_DP[ShipAPI.HullSize.CAPITAL_SHIP])

    }
    else if (index == 1){
      return String.format("%.0f/%.0f/%.0f/%.0f",
        DP_STEP[ShipAPI.HullSize.FRIGATE],
        DP_STEP[ShipAPI.HullSize.DESTROYER],
        DP_STEP[ShipAPI.HullSize.CRUISER],
        DP_STEP[ShipAPI.HullSize.CAPITAL_SHIP])

    }
    else if (index == 2)
    {
      return String.format("%.0f", (DAMAGE_MULTIPLIER - 1f) * 100f) + "%"

    }
    else if (index == 3){
      return String.format("%.0f", MAX_DAMAGE_MULTIPLIER * 100f) + "%"

    }
    return ""
  }

  //以下参数供listener使用


  override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String) {
    if(ship?.hasListenerOfClass(aEP_TitanicKiller::class.java) == false){
      ship.addListener(this)
    }
  }

  override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
    param?:return null
    target?: return null

    if(target is ShipAPI){
      var startDp = START_DP[target.hullSize]?:24f
      var dpStep = DP_STEP[target.hullSize]?:4f
      var dp = target.hullSpec.suppliesToRecover
      //如果打到的是模块，算本体部署点
      if(target.isStationModule && target.parentStation != null){
        dp = target.parentStation.hullSpec.suppliesToRecover
        startDp = START_DP[target.parentStation.hullSpec.hullSize]?:24f
        dpStep = DP_STEP[target.parentStation.hullSpec.hullSize]?:4f
      }

      var temp = dp
      var damageMult =1f
      while(temp > startDp){
        damageMult *= DAMAGE_MULTIPLIER
        temp -= dpStep

        if(damageMult >= MAX_DAMAGE_MULTIPLIER){
          damageMult = MAX_DAMAGE_MULTIPLIER
          break
        }

      }
      damage.modifier.modifyFlat(ID, damageMult - 1f)
      return ID
    }

    return null
  }
}