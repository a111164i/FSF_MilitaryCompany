package data.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShieldAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_Tool
import java.awt.Color

class aEP_SoftfluxDissipate internal constructor() : aEP_BaseHullMod() {
  companion object const {
    const val REVERSE_PERCENT = 0.5f
    const val MAX_PERCENT = 1f
    const val BASE_BUFF_PER_VENT = 8f

    //幅能耗散降低
    const val DISSI_DECREASE = 0.15f

    //武器消耗减少
    const val CONVERT_PERCENT = 0.15f
    const val id = "aEP_SoftfluxDissipate"
  }

  init {
    notCompatibleList.add("aEP_RapidDissipate")
    haveToBeWithMod.add("aEP_MarkerDissipation")
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

    //根据预热程度，减少武器消耗和舰船幅散
    var weaponLevel = aEP_MarkerDissipation.getBufferLevel(ship)
    ship.mutableStats.ballisticWeaponFluxCostMod.modifyMult(id, 1f - CONVERT_PERCENT * weaponLevel)
    ship.mutableStats.energyWeaponRangeBonus.modifyMult(id, 1f - CONVERT_PERCENT * weaponLevel)

    //没点耗散通道，本身的效果不生效
    if (ship.variant.numFluxVents <= 0 && ship.variant.numFluxCapacitors <= 0) return
    val fluxVent = (ship.variant.numFluxVents + ship.variant.numFluxCapacitors) * BASE_BUFF_PER_VENT
    var hardPercent = aEP_Tool.limitToTop(ship.fluxTracker.hardFlux / ship.fluxTracker.maxFlux * MAX_PERCENT, 1f, 0f)
    var addOrReduce = "不变"
    var isDebuff = false
    //先把加成归零用来防止减到负数
    ship.mutableStats.fluxDissipation.modifyFlat(id, 0f)
    //过载不计入
    if (ship.fluxTracker.isOverloadedOrVenting) {
      ship.mutableStats.fluxDissipation.unmodify(id)
      return
    }

    //如果没护盾，不提供加成
    var modified = 0f
    if(ship.shield == null || ship.shield?.type == ShieldAPI.ShieldType.NONE) hardPercent = REVERSE_PERCENT
    if (hardPercent <= REVERSE_PERCENT) {
      modified = fluxVent * (REVERSE_PERCENT - hardPercent) / REVERSE_PERCENT
      ship.mutableStats.fluxDissipation.modifyFlat(id, modified)
      isDebuff = false
      addOrReduce = aEP_DataTool.txt("add")
    } else {
      modified = -Math.min(fluxVent * (hardPercent - REVERSE_PERCENT) / (1f - REVERSE_PERCENT), ship.mutableStats.fluxDissipation.modifiedValue)
      ship.mutableStats.fluxDissipation.modifyFlat(id, modified)
      isDebuff = true
      addOrReduce = aEP_DataTool.txt("reduce")
    }

    if (Global.getCombatEngine().playerShip === ship) {
      Global.getCombatEngine().maintainStatusForPlayerShip(
        this.javaClass.simpleName,  //key
        Global.getSettings().getHullModSpec(id).spriteName,  //sprite name,full, must be registed in setting first
        Global.getSettings().getHullModSpec("aEP_SoftfluxDissipate").displayName,  //title
        aEP_DataTool.txt("flux_diss") + addOrReduce + ": " + modified.toInt(),  //data
        isDebuff
      ) //is debuff
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String? {
    return if (index == 0) BASE_BUFF_PER_VENT.toInt().toString() + "" else null
  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    ship?:return
    val h = Misc.getHighlightColor()
    val fluxVent = (ship.variant.numFluxVents + ship.variant.numFluxCapacitors) * BASE_BUFF_PER_VENT
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("- " + aEP_DataTool.txt("flux_diss") + aEP_DataTool.txt("alter") + "{%s}", 5f, Color.white, h, fluxVent.toInt().toString())
    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"), Alignment.MID, 5f)
    val image = tooltip!!.beginImageWithText(Global.getSettings().getHullModSpec("aEP_SoftfluxDissipate").spriteName, 48f)
    image.addPara("- " + aEP_DataTool.txt("flux_gen_reduce") + "{%s}", 5f, Color.white, Color.green, (CONVERT_PERCENT * 100).toInt().toString() + "%")
    image.addPara("- " + aEP_DataTool.txt("flux_diss") + aEP_DataTool.txt("reduce") + "{%s}", 5f, Color.white, Color.red, (DISSI_DECREASE * 100).toInt().toString() + "%")
    tooltip!!.addImageWithText(5f)
  }

}