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
      mag1["aEP_cru_hailiang3"] = 40f
      mag1["aEP_cap_neibo_turret"] = 60f

    }

  }

  init {
    notCompatibleList.add(HullMods.SAFETYOVERRIDES)
    notCompatibleList.add(HullMods.DEDICATED_TARGETING_CORE)
    notCompatibleList.add(HullMods.INTEGRATED_TARGETING_UNIT)
    notCompatibleList.add(HullMods.CONVERTED_HANGAR)
  }

  /**
   * 使用这个
   * @param ship
   * @param id
   */
  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship.mutableStats.ballisticWeaponRangeBonus.modifyPercent(Companion.id, (mag1[ship.hullSpec.hullId])?:0f)
    ship.mutableStats.energyWeaponRangeBonus.modifyPercent(Companion.id, (mag1[ship.hullSpec.hullId])?:0f)
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

  override fun getDescriptionParam(index: Int, hullSize: HullSize, ship: ShipAPI?): String {
    return ""
  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    tooltip.addSectionHeading(aEP_DataTool.txt("effect"),Alignment.MID, 5f)
    tooltip.addPara("{%s}"+txt("aEP_ModuleTargeting02")+"{%s}", 5f, arrayOf(Color.green, highLight), aEP_ID.HULLMOD_POINT, (mag1[ship?.hullSpec?.hullId?:""]?:0).toInt().toString()+"%")
    tooltip.addPara("{%s}"+txt("not_compatible")+"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))

  }
}