package data.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.abs
import kotlin.math.round

class aEP_RapidDissipate internal constructor() : aEP_BaseHullMod() {

  companion object {
    const val REVERSE_PERCENT = 0.4f
    const val CONVERT_SPEED_PER_CAP = 8f
    const val DAMAGE_CONVERTED = 0.5f
    var id = "aEP_RapidDissipate"
  }

  init {
    notCompatibleList.add("aEP_SoftfluxDissipate")
    haveToBeWithMod.add("aEP_MarkerDissipation")
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship ?: return
    if(!ship.hasListenerOfClass(DamageTaken::class.java)){
      ship.addListener(DamageTaken(ship,ship))
    }
    //这个阶段模块都还没生成
    //这里写的都没用
    for(m in ship.childModulesCopy){
      aEP_Tool.addDebugLog(m.hullSpec.tags.toString())
      if(!m.hullSpec.tags.contains("module_unselectable")) continue
      if(!m.hasListenerOfClass(DamageTaken::class.java)){
        m.addListener(DamageTaken(m,ship))
      }
    }
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    val bufferLevel = aEP_MarkerDissipation.getBufferLevel(ship)
    //根据预热程度为本体的伤害监听器设置伤害转化率
    if(!ship.customData.containsKey(id)){
      if(!ship.hasListenerOfClass(DamageTaken::class.java)) ship.addListener(DamageTaken(ship,ship))
    }else{
      val listener = ship.getListeners(DamageTaken::class.java)[0]
      listener.convertPercnet = DAMAGE_CONVERTED * bufferLevel
    }

    //根据预热程度为模块们的伤害监听器设置伤害转化率
    for(m in ship.childModulesCopy){
      if(!m.hullSpec.tags.contains("module_unselectable")) continue
      //第一次运行到某个模块时，检测是否尝试添加过监听器
      //使用key来判定是否加过监听器，减少调用hasClass的次数，减少计算量
      if(!m.customData.containsKey(id)){
        if(!m.hasListenerOfClass(DamageTaken::class.java)) m.addListener(DamageTaken(m,ship))
        //无论是加上，还是原先就有，都设置为尝试过
        m.setCustomData(id,1f)
      }else{
        val listener = m.getListeners(DamageTaken::class.java)[0]
        listener.convertPercnet = DAMAGE_CONVERTED * bufferLevel
      }
    }


    //没加任何寄存器，本身的效果不生效
    if (ship.variant.numFluxCapacitors <= 0 && ship.variant.numFluxVents <= 0) return
    val fluxVent = (ship.variant.numFluxCapacitors + ship.variant.numFluxVents) * CONVERT_SPEED_PER_CAP
    var addOrReduce = "不变"
    var isDebuff = false
    var useLevel = 0f
    //先取消加成，用于计算修改前剩余的幅散，防止减到负数
    ship.mutableStats.fluxDissipation.modifyFlat(id, 0f)
    //过载不计入
    if (ship.fluxTracker.isOverloadedOrVenting) {
      ship.mutableStats.fluxDissipation.unmodify(id)
      return
    }
    //先归零加成
    ship.mutableStats.fluxDissipation.modifyFlat(id, 0f)
    //鉴于某些特效颜色出界的问题，某些情况下可能超出[0,1]
    val fluxLevel = MathUtils.clamp(ship.fluxLevel,0f,1f)
    useLevel = computeUseLevel(fluxLevel)
    if (useLevel <= 0f) {
      //保证在装配页面（幅能水平为0时），显示正值，所以当useLevel为-1时，不能计算减值而是计算加值
      if(useLevel == -1f) useLevel = 1f
      ship.mutableStats.fluxDissipation.modifyFlat(id, -Math.min(-useLevel * fluxVent, aEP_Tool.getRealDissipation(ship)))
      isDebuff = true
      addOrReduce = aEP_DataTool.txt("reduce")
    } else {
      ship.mutableStats.fluxDissipation.modifyFlat(id, useLevel * fluxVent)
      isDebuff = false
      addOrReduce = aEP_DataTool.txt("add")
    }

    //维持左下角提示
    if (Global.getCombatEngine().playerShip === ship) {
      Global.getCombatEngine().maintainStatusForPlayerShip(
        this.javaClass.simpleName,  //key
        Global.getSettings().getHullModSpec(id).spriteName,  //sprite name,full, must be registed in setting first
        Global.getSettings().getHullModSpec("aEP_RapidDissipate").displayName,  //title
        aEP_DataTool.txt("flux_diss") + addOrReduce + ": " + (useLevel * fluxVent).toInt(),  //data
        isDebuff
      ) //is debuff
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String? {
    return if (index == 0) CONVERT_SPEED_PER_CAP.toInt().toString() + "" else null
  }


  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return  true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    ship?:return
    val h = Misc.getHighlightColor()
    val fluxVent = (ship.variant.numFluxCapacitors + ship.variant.numFluxVents) * CONVERT_SPEED_PER_CAP
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("- " + aEP_DataTool.txt("flux_diss") + aEP_DataTool.txt("alter") + "{%s}", 5f, Color.white,h , fluxVent.toInt().toString())
    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"), Alignment.MID, 5f)
    val image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec("aEP_RapidDissipate").spriteName, 48f)
    image.addPara("- " + aEP_DataTool.txt("aEP_RapidDissipate01") , 5f, Color.white, Color.green, round(DAMAGE_CONVERTED*100).toString()+"%")
    tooltip.addImageWithText(5f)
    tooltip.addPara(aEP_DataTool.txt("aEP_RapidDissipate02"), Color.gray, 5f)

  }

  class DamageTaken(val ship: ShipAPI, val benefited: ShipAPI) : DamageTakenModifier{
    var convertPercnet = 0f

    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI?, point: Vector2f?, shieldHit: Boolean): String? {
      if (param == null) return null
      damage ?: return null
      if(!ship.isAlive) return null
      if(!benefited.isAlive) return null
      if(shieldHit) return null
      var d = (damage.modifier?.modifiedValue?:1f) * damage.damage
      if(damage.type == DamageType.FRAGMENTATION){
        d /= 4f
      } else if(damage.type == DamageType.HIGH_EXPLOSIVE){
        d *= 2f
      } else if(damage.type == DamageType.KINETIC){
        d /= 2f
      }
      Global.getCombatEngine().addFloatingDamageText(point,d*convertPercnet,Color(20,100,240),benefited,null)
      benefited.fluxTracker.increaseFlux(-d*convertPercnet,true)
      //Global.getLogger(this.javaClass).info(d)
      return  null
    }
  }

  fun computeUseLevel(level: Float):Float{
    if(level < REVERSE_PERCENT){
      return -(REVERSE_PERCENT-level)/ REVERSE_PERCENT
    }else if( level >= REVERSE_PERCENT && level < REVERSE_PERCENT * 2f){
      return ((level - REVERSE_PERCENT)/ REVERSE_PERCENT)
    }else{
      return 1f
    }
    return 0f
  }

}