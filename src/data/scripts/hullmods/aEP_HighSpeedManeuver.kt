package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.CollisionClass
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.hullmods.aEP_HighSpeedManeuver.Companion.DODGE_FADE_COLOR
import data.scripts.hullmods.aEP_HighSpeedManeuver.Companion.DODGE_JITTER_COLOR
import data.scripts.hullmods.aEP_HighSpeedManeuver.Companion.ID
import data.scripts.hullmods.aEP_HighSpeedManeuver.Companion.IGNORE_DAMAGE
import data.scripts.weapons.aEP_m_s_era
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_HighSpeedManeuver:aEP_BaseHullMod() {

  companion object{
    const val ID = "aEP_HighSpeedManeuver"
    val DODGE_JITTER_COLOR = Color(245,255,255,150)
    val DODGE_FADE_COLOR = Color(75,75,75,150)

    const val DODGE_TIME = 0.3334f
    const val MAX_DODGE_CHANCE = 0.9f
    const val MAX_DODGE_CHANCE_DROP = 0.1f
    const val DODGE_CHANCE_REFILL_SPEED = 0.1f
    const val REFILL_AFTER_DELAY= 1f

    const val IGNORE_DAMAGE = 20f
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship.mutableStats.combatEngineRepairTimeMult.modifyMult(ID,0.5f)

    if(!ship.hasListenerOfClass(DodgeAttack::class.java)){
      ship.addListener(DodgeAttack(ship,
        MAX_DODGE_CHANCE,MAX_DODGE_CHANCE_DROP,DODGE_CHANCE_REFILL_SPEED,REFILL_AFTER_DELAY,DODGE_TIME))
    }
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

    //aEP_Tool.ignoreSlow(ship,true)

  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {

    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highlight = Misc.getHighlightColor()
    val negativeHighlight = Misc.getNegativeHighlightColor()

    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()

    val titleTextColor: Color = faction.color
    val factionColor: Color = faction.baseUIColor
    val factionDarkColor = faction.darkUIColor
    val factionBrightColor = faction.brightUIColor

    //主效果
    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f)
    addPositivePara(tooltip, "aEP_HighSpeedManeuver01", arrayOf(
      String.format("%.0f", IGNORE_DAMAGE),
      txt("aEP_HighSpeedManeuver03"),
      String.format("%.2f", DODGE_TIME),
    ))
    addPositivePara(tooltip, "aEP_HighSpeedManeuver02", arrayOf(
      String.format("%.0f", REFILL_AFTER_DELAY),
    ))

    val col2W0 = width * 0.33f
    val col3W0 = width * 0.33f
    //第一列显示的名称，尽可能可能的长
    val col1W0 = (width - col2W0 - col3W0 - PARAGRAPH_PADDING_BIG)
    tooltip.beginTable(
      factionColor, factionDarkColor, factionBrightColor,
      TEXT_HEIGHT_SMALL, true, true,
      *arrayOf<Any>(
        txt("max")+ txt("chance"), col1W0,
        txt("chance")+ txt("decrease"), col2W0,
        txt("recover")+ txt("speed"), col3W0,
      )
    )
    tooltip.addRow(
      Alignment.MID, highlight, String.format("%.0f", MAX_DODGE_CHANCE * 100f)+"%",
      Alignment.MID, highlight, String.format("%.0f", MAX_DODGE_CHANCE_DROP * 100f)+"%",
      Alignment.MID, highlight, String.format("%.0f", DODGE_CHANCE_REFILL_SPEED * 100f)+"%/s",
    )
    tooltip.addTable("", 0, PARAGRAPH_PADDING_SMALL)



    addPositivePara(tooltip, "aEP_HighSpeedManeuver04", arrayOf(
      String.format("-%.0f", 50f )+"%"
    ))


    //显示不兼容插件
    showIncompatible(tooltip)
    //tooltip.addPara("{%s}"+ txt("not_compatible") +"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))


    //灰字额外说明
    //tooltip.addPara(aEP_DataTool.txt("aEP_EmergencyReconstruct07"), grayColor, 5f)
  }
}
class DodgeAttack(val ship: ShipAPI, val maxDodgeChance:Float, val maxDodgeChanceDrop:Float, val dodgeChanceRefill:Float, val refillAfterTime:Float, val maxDodgeTime:Float): DamageTakenModifier, AdvanceableListener{
  var dodgeTime = 0f
  var timeElapsedSinceDodge = 0f
  var dodgeChance = maxDodgeChance
  var dodgeChanceDrop = maxDodgeChanceDrop
  var didDodge = false

  override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {

    //没伤害的，比如维修光束，不需要闪避
    if(damage.baseDamage <= IGNORE_DAMAGE) return null
    if(param is BeamAPI && damage.baseDamage <= IGNORE_DAMAGE * 2f) return null

    val test = MathUtils.getRandomNumberInRange(0f,100f)
    timeElapsedSinceDodge = 0f
    //进入时运行一次
    if(test <= dodgeChance * 100f && dodgeTime <= 0f){
      didDodge = true
      dodgeChance -= dodgeChanceDrop
      ship.collisionClass = CollisionClass.NONE
      dodgeTime = maxDodgeTime
      //任何情况不要 mult 0
      damage.modifier.modifyMult(ID,0.1f)
      return ID
    }

    return null
  }

  override fun advance(amount: Float) {

    if(aEP_Tool.isDead(ship)){
      ship.removeListener(this)
    }

    //维持左下角状态栏
    if(Global.getCombatEngine().playerShip == ship){
      Global.getCombatEngine().maintainStatusForPlayerShip(ID,
        Global.getSettings().getSpriteName("aEP_ui","high_speed_maneuver"),
        Global.getSettings().getHullModSpec(ID).displayName,
        String.format("Active Chance: %.0f",(dodgeChance*100f))+"%",
        false)
    }


    dodgeTime -= amount
    dodgeTime = dodgeTime.coerceAtLeast(0f)
    //躲避状态时持续运行
    if(dodgeTime > 0){
      didDodge = true
      timeElapsedSinceDodge = 0f
      ship.collisionClass = CollisionClass.NONE
      val range = ship.collisionRadius * 0.2f + 10f
      ship.setJitter(ID,DODGE_JITTER_COLOR,1f, 3, range)
      ship.fadeToColor(ID,DODGE_FADE_COLOR, 0.1f,0.1f,1f)
    }else if (didDodge){ //退出时运行一次
      didDodge = false
      ship.collisionClass = CollisionClass.SHIP
    }else{
      timeElapsedSinceDodge += amount
    }

    if(timeElapsedSinceDodge > refillAfterTime){
      dodgeChance += dodgeChanceRefill * amount
      dodgeChance = dodgeChance.coerceAtMost(maxDodgeChance)
    }

  }
}