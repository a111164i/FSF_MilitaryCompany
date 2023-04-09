package data.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.abs
import kotlin.math.round

class aEP_RapidDissipate internal constructor() : aEP_BaseHullMod() {

  companion object {
    const val DAMAGE_CONVERTED = 0.65f
    var ID = "aEP_RapidDissipate"
    val FLOAT_TEXT_COLOR = Color(20,100,240) //每次受击都new一个颜色类有点废性能
  }

  init {
    notCompatibleList.add(aEP_SoftfluxDissipate.ID)
    notCompatibleList.add(aEP_BurstDissipate.ID)
    notCompatibleList.add(HullMods.SAFETYOVERRIDES)
    haveToBeWithMod.add("aEP_MarkerDissipation")
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if(!ship.hasListenerOfClass(DamageTaken::class.java)){
      ship.addListener(DamageTaken(ship,ship))
    }

    //根据预热程度为模块们的伤害监听器设置伤害转化率
    for(m in ship.childModulesCopy){
      if(!m.hullSpec.tags.contains("module_unselectable")) continue
      //第一次运行到某个模块时，检测是否尝试添加过监听器
      //一定要用key来判定，原因见MarkerDissipation
      if(!m.customData.containsKey(ID)){
        m.addListener(DamageTaken(m,ship))
        //无论是加上，还是原先就有，都设置为尝试过
        m.setCustomData(ID,1f)
      }
    }
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String? {
    return null
  }


  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return  true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    ship?:return
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)

    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor,Alignment.MID, 5f)
    val image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec(ID).spriteName, 48f)
    image.addPara("{%s}"+txt("aEP_RapidDissipate01"), 5f, arrayOf(Color.green), aEP_ID.HULLMOD_POINT, String.format("%.0f",DAMAGE_CONVERTED*100f)+"%")
    tooltip.addImageWithText(5f)

    //额外灰色说明
    tooltip.addPara(aEP_DataTool.txt("aEP_RapidDissipate02"), Color.gray, 5f)

  }

  class DamageTaken(val ship: ShipAPI, val benefited: ShipAPI) : DamageTakenModifier, AdvanceableListener{
    val checkTracker = IntervalUtil(0.25f,0.25f)
    var heatingLevel = 0f

    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI?, point: Vector2f?, shieldHit: Boolean): String? {
      damage ?: return null
      if(!ship.isAlive) return null
      if(!benefited.isAlive) return null
      if(shieldHit) return null
      //modifier基础值是1
      var d = damage.modifier.modifiedValue  * damage.damage
      if(damage.type == DamageType.FRAGMENTATION){
        d /= 4f
      } else if(damage.type == DamageType.HIGH_EXPLOSIVE){
        d *= 2f
      } else if(damage.type == DamageType.KINETIC){
        d /= 2f
      }
      d *= heatingLevel
      Global.getCombatEngine().addFloatingDamageText(point,d ,FLOAT_TEXT_COLOR,benefited,null)
      benefited.fluxTracker.increaseFlux(-d,true)
      //Global.getLogger(this.javaClass).info(d)
      return  null
    }

    override fun advance(amount: Float) {

      checkTracker.advance(amount)
      if(!checkTracker.intervalElapsed()) return
      //根据受益者的预热程度，更新转换率
      heatingLevel = aEP_MarkerDissipation.getBufferLevel(benefited)
    }
  }

}