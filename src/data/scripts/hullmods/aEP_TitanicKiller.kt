package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_TitanicKiller: aEP_BaseHullMod(), DamageDealtModifier {
  companion object{
    const val ID = "aEP_TitanicKiller"

    const val DAMAGE_MULTIPLIER = 1.15f
    const val MAX_DAMAGE_MULTIPLIER = 3f

    val START_DP = LinkedHashMap<ShipAPI.HullSize, Float>()
    init {
      START_DP[ShipAPI.HullSize.CAPITAL_SHIP] = 45f
      START_DP[ShipAPI.HullSize.CRUISER] = 24f
      START_DP[ShipAPI.HullSize.DESTROYER] = 12f
      START_DP[ShipAPI.HullSize.FRIGATE] = 6f
    }

    val DP_STEP = LinkedHashMap<ShipAPI.HullSize, Float>()
    init {
      DP_STEP[ShipAPI.HullSize.CAPITAL_SHIP] = 5f
      DP_STEP[ShipAPI.HullSize.CRUISER] = 4f
      DP_STEP[ShipAPI.HullSize.DESTROYER] = 3f
      DP_STEP[ShipAPI.HullSize.FRIGATE] = 2f
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
    if (index == 0) return String.format("+%.0f", (DAMAGE_MULTIPLIER-1f)*100f) +"%"
    if (index == 1) return String.format("+%.0f", (MAX_DAMAGE_MULTIPLIER-1f) * 100f) +"%"
    return null
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

    val col2W0 = width * 0.3f
    val col3W0 = width * 0.4f
    //第一列显示的名称，尽可能可能的长
    val col1W0 = (width - col2W0 - col3W0 - PARAGRAPH_PADDING_BIG)
    tooltip.beginTable(
      factionColor, factionDarkColor, factionBrightColor,
      TEXT_HEIGHT_SMALL, true, true,
      *arrayOf<Any>(
        "Target Hullsize", col1W0,
        txt("aEP_TitanicKiller01"), col2W0,
        txt("aEP_TitanicKiller02"), col3W0,
      )
    )
    tooltip.addRow(
      Alignment.MID, highlight, Misc.getHullSizeStr(ShipAPI.HullSize.CAPITAL_SHIP),
      Alignment.MID, highlight, String.format("%.0f", START_DP[ShipAPI.HullSize.CAPITAL_SHIP]),
      Alignment.MID, highlight, String.format("%.0f", DP_STEP[ShipAPI.HullSize.CAPITAL_SHIP]),
    )
    tooltip.addRow(
      Alignment.MID, highlight, Misc.getHullSizeStr(ShipAPI.HullSize.CRUISER),
      Alignment.MID, highlight, String.format("%.0f", START_DP[ShipAPI.HullSize.CRUISER]),
      Alignment.MID, highlight, String.format("%.0f", DP_STEP[ShipAPI.HullSize.CRUISER]),
    )
    tooltip.addRow(
      Alignment.MID, highlight, Misc.getHullSizeStr(ShipAPI.HullSize.DESTROYER),
      Alignment.MID, highlight, String.format("%.0f", START_DP[ShipAPI.HullSize.DESTROYER]),
      Alignment.MID, highlight, String.format("%.0f", DP_STEP[ShipAPI.HullSize.DESTROYER]),
    )
    tooltip.addRow(
      Alignment.MID, highlight, Misc.getHullSizeStr(ShipAPI.HullSize.FRIGATE),
      Alignment.MID, highlight, String.format("%.0f", START_DP[ShipAPI.HullSize.FRIGATE]),
      Alignment.MID, highlight, String.format("%.0f", DP_STEP[ShipAPI.HullSize.FRIGATE]),
    )
    tooltip.addTable("", 0, PARAGRAPH_PADDING_SMALL)
  }

  override fun getTooltipWidth(): Float {
    return 412f
  }

  //------------------------------//
  //以下参数供listener使用

  override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String) {
    if(ship?.hasListenerOfClass(aEP_TitanicKiller::class.java) == false){
      ship.addListener(this)
    }
  }

  override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
    param?:return null
    target?: return null

    if(target is ShipAPI){
      var startDp = START_DP[target.hullSize]?:24f
      var dpStep = DP_STEP[target.hullSize]?:4f
      var dp = target.hullSpec.suppliesToRecover
      //如果打到的是模块，算本体部署点
      if(target.isStationModule && target.parentStation != null){
        dp = target.parentStation.hullSpec.suppliesToRecover
        startDp = START_DP[target.parentStation.hullSpec.hullSize]?:24f
        dpStep = DP_STEP[target.parentStation.hullSpec.hullSize]?:4f
      }

      var temp = dp
      var damageMult =1f
      while(temp > startDp){
        damageMult *= DAMAGE_MULTIPLIER
        temp -= dpStep

        if(damageMult >= MAX_DAMAGE_MULTIPLIER){
          damageMult = MAX_DAMAGE_MULTIPLIER
          break
        }

      }
      damage.modifier.modifyFlat(ID, damageMult - 1f)
      return ID
    }

    return null
  }
}