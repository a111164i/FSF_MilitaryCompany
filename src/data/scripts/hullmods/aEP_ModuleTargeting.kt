package data.scripts.hullmods

import com.fs.starfarer.api.Global
import java.util.HashMap
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import java.awt.Color

class aEP_ModuleTargeting : aEP_BaseHullMod() {
  companion object {
    private const val id = "aEP_ModuleTargeting"
    private val mag1 = HashMap<String, Float>()

    init {
      mag1["aEP_cru_hailiang3"] = 30f
      mag1["aEP_cap_neibo_turret"] = 60f
    }

  }

  init {
    notCompatibleList.add(HullMods.DEDICATED_TARGETING_CORE)
    notCompatibleList.add(HullMods.INTEGRATED_TARGETING_UNIT)
  }

  /**
   * 使用这个
   * @param ship
   * @param id
   */
  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship.mutableStats.ballisticWeaponRangeBonus.modifyPercent(Companion.id, (mag1[ship.hullSpec.baseHullId])?:0f)
    ship.mutableStats.energyWeaponRangeBonus.modifyPercent(Companion.id, (mag1[ship.hullSpec.baseHullId])?:0f)
  }


  /**
   * 在装配页面，module系统还没有初始化，只存在variant的关系，无法获得shipAPI
   * 在进入战场的第一帧加载buff
   */
  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if(ship.isStationModule)

    //只在舰船被部署的第一帧运行一次
    //舰船进入战斗时，才会提供加成
    if (ship.fullTimeDeployed > 0.00001f) return

  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize, ship: ShipAPI?): String? {
    ship ?: return "0%"
    if (index == 0) return String.format("+%.0f", mag1[ship.hullSpec.baseHullId]?: 40f) +"%"
    return null
  }


}