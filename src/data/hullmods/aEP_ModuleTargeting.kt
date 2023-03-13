package data.hullmods

import com.fs.starfarer.api.Global
import data.hullmods.aEP_BaseHullMod
import java.util.HashMap
import data.hullmods.aEP_ModuleTargeting
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import combat.util.aEP_DataTool
import java.awt.Color

class aEP_ModuleTargeting : aEP_BaseHullMod() {
  companion object {
    private const val id = "aEP_ModuleTargeting"
    private val mag1 = HashMap<HullSize, Float>()
    private val mag2 = HashMap<HullSize, Float>()

    init {
      mag1[HullSize.FIGHTER] = 0f
      mag1[HullSize.FRIGATE] = 10f
      mag1[HullSize.DESTROYER] = 20f
      mag1[HullSize.CRUISER] = 40f
      mag1[HullSize.CAPITAL_SHIP] = 60f
    }

    init {
      mag2[HullSize.FIGHTER] = 0f
      mag2[HullSize.FRIGATE] = 0f
      mag2[HullSize.DESTROYER] = 0f
      mag2[HullSize.CRUISER] = 35f
      mag2[HullSize.CAPITAL_SHIP] = 50f
    }
  }

  /**
   * 使用这个
   * @param ship
   * @param id
   */
  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    for (mId in ship.variant.moduleSlots) {
      val m = ship.variant.getModuleVariant(mId)
      var syncId = ""
      //禁止模块拥有以下插件
      syncId = "safetyoverrides"
      if (m.hasHullMod(syncId)) m.removeMod(syncId)
      syncId = "targetingunit"
      if (m.hasHullMod(syncId)) m.removeMod(syncId)
      syncId = "dedicated_targeting_core"
      if (m.hasHullMod(syncId)) m.removeMod(syncId)
      syncId = "aEP_TargetSystem"
      if (m.hasHullMod(syncId)) m.removeMod(syncId)
    }
  }

  /**
   * 在装配页面，module系统还没有初始化，只存在variant的关系，无法获得shipAPI
   * 在进入战场的第一帧加载buff
   */
  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    //只在舰船被部署的第一帧运行一次
    //舰船进入战斗时，才会提供加成
    if (ship.fullTimeDeployed > 0.00001f) return
    for (m in ship.childModulesCopy) {
      var syncId = ""
      //防止模块用v排
      m.mutableStats.ventRateMult.modifyMult(id, 0f)
      syncId = "targetingunit"
      //直接复制的黄定位代码，记得舰船尺寸时用ship，加成时用m
      if (ship.variant.hasHullMod(syncId)) {
        m.mutableStats.ballisticWeaponRangeBonus.modifyPercent(id, (mag1[ship.hullSize] as Float?)!!)
        m.mutableStats.energyWeaponRangeBonus.modifyPercent(id, (mag1[ship.hullSize] as Float?)!!)
      }
      //直接复制的初始定位代码，记得舰船尺寸时用ship，加成时用m
      syncId = "dedicated_targeting_core"
      if (ship.variant.hasHullMod(syncId)) {
        m.mutableStats.ballisticWeaponRangeBonus.modifyPercent(id, (mag2[ship.hullSize] as Float?)!!)
        m.mutableStats.energyWeaponRangeBonus.modifyPercent(id, (mag2[ship.hullSize] as Float?)!!)
      }
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize, ship: ShipAPI): String {
    return ""
  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    tooltip.addSectionHeading(aEP_DataTool.txt("aEP_ModuleTargeting01"), Alignment.MID, 5f)
    tooltip.addPara("- " + "{%s}", 5f, Color.white, Color.green, Global.getSettings().getHullModSpec("dedicated_targeting_core").displayName)
    tooltip.addPara("- " + "{%s}", 5f, Color.white, Color.green, Global.getSettings().getHullModSpec("targetingunit").displayName)
    tooltip.addSectionHeading(aEP_DataTool.txt("aEP_ModuleTargeting03"), Alignment.MID, 5f)
    tooltip.addPara("- " + "{%s}", 5f, Color.white, Color.red, Global.getSettings().getHullModSpec("safetyoverrides").displayName)
    tooltip.addPara("- " + "{%s}", 5f, Color.white, Color.red, Global.getSettings().getHullModSpec("aEP_TargetSystem").displayName)
    tooltip.addPara(aEP_DataTool.txt("aEP_ModuleTargeting02"), Color.gray, 5f)
  }
}