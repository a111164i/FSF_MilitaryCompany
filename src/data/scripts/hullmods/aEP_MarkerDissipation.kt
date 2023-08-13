package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import com.fs.starfarer.api.combat.listeners.DamageListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.floatDataRecorder
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.reflect.jvm.internal.impl.renderer.DescriptorRenderer

class aEP_MarkerDissipation : aEP_BaseHullMod() {

  companion object {
    const val BUFFER_AREA = 0.2f // 缓冲区总大小是幅能容量的百分之多少
    const val INCREASE_SPEED = 1f // 产生的幅能百分之多少能多少计入缓冲区
    const val DECREASE_SPEED = 0.5f // 缓冲区每秒下降幅能耗散的百分之多少
    const val DECREASE_PERCENT= 0.5f // 缓冲区每秒下降实际幅能降低的多少
    const val OVERLOAD_TIME_DECREASE = 0.25f

    val ZERO_FLUX_SPEED_BONUS = LinkedHashMap<String, Float>()
    const val DEFAULT_BOOST_BONUS = 60f
    init {
      ZERO_FLUX_SPEED_BONUS["aEP_cap_nuanchi"] = 75f

      ZERO_FLUX_SPEED_BONUS["aEP_cap_hailiang"] = 70f
      ZERO_FLUX_SPEED_BONUS["aEP_cru_pubu"] = 65f
      ZERO_FLUX_SPEED_BONUS["aEP_cru_requan"] = 65f

      ZERO_FLUX_SPEED_BONUS["aEP_des_shuishi"] = 50f
      ZERO_FLUX_SPEED_BONUS["aEP_des_shendu"] = 50f


    }
    const val ZERO_FLUX_EXTRA_THREHOLD = 4f //百分之几以下触发加速装填，航母派出飞机是1%，所以这里给2%
    const val TIME_TO_BUFF = 7f //几秒达到满速加成

    val BOOST_COLOR1 = Color(255,0,0)
    val BOOST_COLOR2 = Color(225,125,75,100)

    const val MAX_OVERLOAD_TIME = 8f
    const val ID = "aEP_MarkerDissipation"
    const val ID_LEVEL = "aEP_MarkerDissipationThreshold"
    const val ID_BONUS = "aEP_MarkerDissipationBoost"

    @JvmStatic
    fun getBufferLevel(ship: ShipAPI?): Float {
      if (ship == null || !ship.isAlive || ship.isHulk || !ship.variant.hasHullMod(ID)) return 0f
      val fluxData = (ship.customData["$ID _ ${ship.id}"] ?: return 0f) as floatDataRecorder
      val buffer = fluxData.total
      val maxDissi = aEP_Tool.getRealDissipation(ship)
      val maxCap = ship.maxFlux
      val bufferLevel = buffer / (maxCap * BUFFER_AREA + 1f)
      //次方会让曲线更快下降
      return MathUtils.clamp(   bufferLevel * 2f, 0f, 1f)
    }
  }

  init {
    notCompatibleList.add( HullMods.SAFETYOVERRIDES)
  }

  override fun applyEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
    stats.dynamic.getStat(aEP_EliteShip.INSV_ID).baseValue = 0f
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    //减少过载时间
    ship.mutableStats.overloadTimeMod.modifyMult(ID, 1f - OVERLOAD_TIME_DECREASE)

    //改变0幅能加速的基础值
    ship.mutableStats.zeroFluxSpeedBoost.baseValue = ZERO_FLUX_SPEED_BONUS[ship.hullSpec.baseHullId]?: DEFAULT_BOOST_BONUS
    ship.mutableStats.zeroFluxMinimumFluxLevel.modifyFlat(ID, ZERO_FLUX_EXTRA_THREHOLD/100f)


    //修改护盾贴图
    if (ship.shield != null) {
      //set shield inner, outer ring
      ship.hullStyleId
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

  override fun applySmodEffectsAfterShipCreationImpl(ship: ShipAPI, stats: MutableShipStatsAPI, id: String) {

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

    //主效果
    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("{%s}"+txt("aEP_MarkerDissipation03"), 5f, arrayOf(Color.green,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", (OVERLOAD_TIME_DECREASE * 100f))+ "%")
    tooltip.addPara("{%s}"+txt("aEP_MarkerDissipation01"), 5f, arrayOf(Color.green,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", MAX_OVERLOAD_TIME))
    tooltip.addPara("{%s}"+txt("aEP_MarkerDissipation02"), 5f, arrayOf(Color.green,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      txt("base"),
      String.format("%.0f", ZERO_FLUX_SPEED_BONUS[ship?.hullSpec?.hullId]?: DEFAULT_BOOST_BONUS))
    tooltip.addPara("{%s}"+txt("aEP_MarkerDissipation05"), 5f, arrayOf(Color.green,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f",ZERO_FLUX_EXTRA_THREHOLD ) +"%")

    //负面
    tooltip.addPara("{%s}"+txt("aEP_MarkerDissipation06"), 5f, arrayOf(Color.red,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", TIME_TO_BUFF))
    //显示不兼容插件
    tooltip.addPara("{%s}"+txt("not_compatible")+"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))


    //灰字额外说明
    tooltip.addPara(aEP_DataTool.txt("MD_des04"), grayColor, 5f)

  }

  internal inner class FluxRecorder(private val ship: ShipAPI, val fluxData: floatDataRecorder) : DamageListener, DamageTakenModifier, AdvanceableListener {
    private var param: Any? = null
    private var damage: DamageAPI? = null
    private var zeroBuffTime = 0f

    override fun advance(amount: Float) {
      val maxFlux = ship.fluxTracker.maxFlux
      val softFlux = ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux
      val totalDiss = aEP_Tool.getRealDissipation(ship)
      val speedBonus = ship.mutableStats.zeroFluxSpeedBoost.modified

      //0幅能加成
      if(ship.isEngineBoostActive){
        zeroBuffTime += amount

      }else{
        zeroBuffTime -= amount * TIME_TO_BUFF
      }

      zeroBuffTime = zeroBuffTime.coerceAtMost(TIME_TO_BUFF).coerceAtLeast(0f)
      var level = zeroBuffTime/ TIME_TO_BUFF
      level *= (level * level * level)
      if(level > 0f){
        if(ship.mutableStats.zeroFluxSpeedBoost.multMods.get(ID)?.value != (level) ){
          ship.mutableStats.zeroFluxSpeedBoost.modifyMult(ID, level)
          ship.mutableStats.acceleration.modifyFlat(ID, speedBonus/2f * level)
          ship.mutableStats.deceleration.modifyFlat(ID, speedBonus/2f * level)
        }
      }else{
        if(ship.mutableStats.zeroFluxSpeedBoost.multMods.containsKey(ID) ){
          ship.mutableStats.zeroFluxSpeedBoost.unmodify(ID)
          ship.mutableStats.acceleration.unmodify(ID)
          ship.mutableStats.deceleration.unmodify(ID)
        }
      }
      ship.engineController.fadeToOtherColor(ID, BOOST_COLOR1, BOOST_COLOR2, level, 0.4f )
      ship.engineController.extendFlame(ID,0.3f * level,0.3f * level,0.5f *level)


      //更新上一帧幅能变化
      var fluxDecreaseCompareToLastFrame = 0f
      if(softFlux < fluxData.last){
        fluxDecreaseCompareToLastFrame = fluxData.last - softFlux
      }
      fluxData.addRenewData(softFlux * INCREASE_SPEED)

      //修正由于循环幅散和先进幅散带来的问题
      //先进幅散想停在预热状态，但是加成的幅散会更快退出预热
      val bonusFromSoft = (ship.mutableStats.fluxDissipation.getFlatStatMod(aEP_SoftfluxDissipate.ID_B)?.value?:0f) +
          (ship.mutableStats.fluxDissipation.getFlatStatMod(aEP_SoftfluxDissipate.ID_P)?.value?:0f)
      //循环幅散不想停在预热状态，但减少的幅散又会更慢退出预热
      val bonusFromBurst=(ship.mutableStats.fluxDissipation.getFlatStatMod(aEP_BurstDissipate.ID_B)?.value?:0f) +
          (ship.mutableStats.fluxDissipation.getFlatStatMod(aEP_BurstDissipate.ID_P)?.value?:0f)

      //计算缓冲区最大容量和下降速度
      val decrease =  (totalDiss * DECREASE_SPEED  - bonusFromSoft - bonusFromBurst)* amount
      val max = maxFlux * BUFFER_AREA;

      //如果幅能开始下降，持续消减buff区
      fluxData.total = MathUtils.clamp(fluxData.total - fluxDecreaseCompareToLastFrame * DECREASE_PERCENT - decrease, 0f,max)
      val buffer = fluxData.total

      //过载立刻清空预热状态
      if (ship.fluxTracker.isOverloadedOrVenting) fluxData.total = 0f
      var bufferLevel = MathUtils.clamp(buffer * 2f / (max + 1f), 0f, 1f)
      if (Global.getCombatEngine().playerShip === ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName,  //key
          "graphics/aEP_hullsys/marker_dissipation.png",  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("MD_des01") + (bufferLevel * 100).toInt() + "%",  //data
          false) //is debuff
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