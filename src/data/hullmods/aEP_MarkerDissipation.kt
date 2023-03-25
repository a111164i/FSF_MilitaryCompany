package data.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import com.fs.starfarer.api.combat.listeners.DamageListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.floatDataRecorder
import combat.util.aEP_ID
import data.scripts.weapons.aEP_TeslaBeam
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_MarkerDissipation : aEP_BaseHullMod() {

  companion object {
    const val BUFFER_AREA = 0.1f // 幅能容量的百分之多少
    const val DECREASE_SPEED = 0.5f // 幅能耗散的百分之多少

    const val OVERLOAD_TIME_DECREASE = 0.25f
    const val MAX_OVERLOAD_TIME = 7f
    const val ID = "aEP_MarkerDissipation"
    @JvmStatic
    fun getBufferLevel(ship: ShipAPI?): Float {
      if (ship == null || !ship.isAlive || ship.isHulk || !ship.variant.hasHullMod(ID)) return 0f
      val fluxData = (ship.customData["$ID _ ${ship.id}"] ?: return 0f) as floatDataRecorder
      val buffer = fluxData.total
      val maxFlux = ship.fluxTracker.maxFlux
      val bufferLevel = buffer / ((maxFlux+1) * BUFFER_AREA)
      //次方会让曲线更快下降
      return MathUtils.clamp(  bufferLevel * bufferLevel * 2f, 0f, 1f)
    }
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    //减少过载时间
    ship.mutableStats.overloadTimeMod.modifyMult(ID, 1f - OVERLOAD_TIME_DECREASE)
    //修改护盾贴图
    if (ship.shield != null) {
      //set shield inner, outer ring
      ship.shield.setRadius(
        ship.shield.radius,
        Global.getSettings().getSpriteName("aEP_hullstyle", "aEP_shield_inner"),
        Global.getSettings().getSpriteName("aEP_hullstyle", "aEP_shield_outer")
      )
    }

    //当希望customData进行初始化时，条件一定要是customData.contains()，有的时候舰船生成到一半，custom此时还没有创建
    if (!ship.customData.containsKey("$ID _ ${ship.id}")) {
      val fluxData = floatDataRecorder()
      ship.setCustomData("$ID _ ${ship.id}",fluxData)
      ship.addListener(FluxRecorder(ship, fluxData))
    }

  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    super.advanceInCombat(ship, amount)
    if (ship.fluxTracker.isOverloaded && ship.fluxTracker.overloadTimeRemaining > MAX_OVERLOAD_TIME) {
      ship.fluxTracker.setOverloadDuration(MAX_OVERLOAD_TIME)
    }

  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String? {
    return null
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

    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("- " + aEP_DataTool.txt("overload_time_reduce") + "{%s}", 5f, Color.white, Color.green, (OVERLOAD_TIME_DECREASE * 100).toInt().toString() + "%")
    tooltip.addPara(aEP_DataTool.txt("MD_des04"), Color.gray, 5f)
  }


  internal inner class FluxRecorder(private val ship: ShipAPI, val fluxData: floatDataRecorder) : DamageListener, DamageTakenModifier, AdvanceableListener {
    private var param: Any? = null
    private var damage: DamageAPI? = null

    override fun advance(amount: Float) {
      val maxFlux = ship.fluxTracker.maxFlux
      val softFlux = ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux
      val totalDiss = ship.mutableStats.fluxDissipation.modifiedValue

      //更新上一帧幅能变化
      fluxData.addRenewData(softFlux)

      //持续消减buff区
      fluxData.total = MathUtils.clamp(fluxData.total - totalDiss * DECREASE_SPEED * amount, 0f,maxFlux * BUFFER_AREA)
      val buffer = fluxData.total

      //过载立刻清空预热状态
      if (ship.fluxTracker.isOverloadedOrVenting) fluxData.total = 0f
      var bufferLevel = MathUtils.clamp(buffer / (maxFlux * BUFFER_AREA), 0f, 1f)
      bufferLevel = MathUtils.clamp(bufferLevel * 2f, 0f, 1f)
      if (Global.getCombatEngine().playerShip === ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName,  //key
          "graphics/aEP_hullsys/marker_dissipation.png",  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("MD_des01") + (bufferLevel * 100).toInt() + "%",  //data
          false
        ) //is debuff
      }
    }

    //在 report之前被调用
    //用来记录 DamageAPI
    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      this.param = param
      this.damage = damage
      return null
    }

    override fun reportDamageApplied(source: Any?, target: CombatEntityAPI, result: ApplyDamageResultAPI) {
      //反向消除由于受到伤害导致的预热缓冲区变化
      if ((param is BeamAPI && damage?.isForceHardFlux == false) || damage?.isSoftFlux == true) {
        fluxData.setLastFrameData(fluxData.last + result.damageToShields)
      }
      return
    }
  }

}