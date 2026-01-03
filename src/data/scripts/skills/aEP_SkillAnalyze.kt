package data.scripts.skills

import com.fs.starfarer.api.characters.DescriptionSkillEffect
import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription
import com.fs.starfarer.api.characters.ShipSkillEffect
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_DataTool.txt
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.pow


object aEP_SkillAnalyze {

  const val ID = "aEP_SkillAnalyze"

  var DAMAGE_MULT_BONS_PER_LEVEL = 1.15f
  var MAX_MULT = 2.5f
  //超过该等级开始加成
  var START_LEVEL = 6

  var START_LEVEL_COMMANDER = 14
  class Level0 : DescriptionSkillEffect {
    override fun getString(): String {

      return ""

    }

    override fun getHighlightColors(): Array<Color> {
      var h = Misc.getHighlightColor()
      h = Misc.getDarkHighlightColor()
      return arrayOf(h, h, h)
    }

    override fun getHighlights(): Array<String> {
      return arrayOf("")
    }

    override fun getTextColor(): Color? {
      return null
    }
  }

  class Level1 : ShipSkillEffect, DamageDealtModifier {

    /**
      下面几个参数打问号(可空类型)是是因为真的会为null，不知道为啥
    */
    override fun apply(stats: MutableShipStatsAPI?, hullSize: HullSize?, id: String, level: Float) {
      stats?:return
      hullSize?:return

      if(stats.entity is ShipAPI){
        val ship = stats.entity as ShipAPI
        if(!ship.hasListenerOfClass(this::class.java)){
          val listener = Level1()
          ship.addListener(listener)
        }
      }
    }

    override fun unapply(stats: MutableShipStatsAPI?, hullSize: HullSize?, id: String) {
    }

    override fun getEffectDescription(level: Float): String {
      return String.format( txt("aEP_SkillAnalyze01"),
          START_LEVEL.toString(), START_LEVEL_COMMANDER.toString(),
          String.format("%.0f", (DAMAGE_MULT_BONS_PER_LEVEL-1f)*100f)+"%",
          String.format("%.0f", (MAX_MULT-1f)*100f)+"%")
    }

    override fun getEffectPerLevelDescription(): String? {
      return null
    }

    override fun getScopeDescription(): ScopeDescription {
      return ScopeDescription.PILOTED_SHIP
    }

    //---------------------------//
    //以下部分是listener
    override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      if(target is ShipAPI){
        if(target.captain?.stats == null) return null
        val capt = target.captain
        val level = capt.stats.level
        if(level <= START_LEVEL)  return null
        var levelExceeded = level - START_LEVEL
        if(target.fleetMember?.fleetCommander?.id.equals(capt.id)){
          levelExceeded = level - START_LEVEL_COMMANDER
        }
        var damageMultFlat = (DAMAGE_MULT_BONS_PER_LEVEL.pow(levelExceeded)).coerceAtMost(MAX_MULT)
        damage.modifier.modifyFlat(ID, damageMultFlat -1f)
        return ID
      }
      return null
    }
  }

  class Level1A : ShipSkillEffect {
    override fun apply(stats: MutableShipStatsAPI?, hullSize: HullSize?, id: String, level: Float) {

    }

    override fun unapply(stats: MutableShipStatsAPI?, hullSize: HullSize?, id: String) {
    }

    override fun getEffectDescription(level: Float): String {
      return ""
    }

    override fun getEffectPerLevelDescription(): String? {
      return null
    }

    override fun getScopeDescription(): ScopeDescription {
      return ScopeDescription.PILOTED_SHIP
    }
  }



}
