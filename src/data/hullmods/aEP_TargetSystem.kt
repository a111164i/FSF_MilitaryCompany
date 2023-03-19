package data.hullmods

import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import java.util.HashMap
import data.hullmods.aEP_TargetSystem
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier
import com.fs.starfarer.api.ui.Alignment
import org.lazywizard.lazylib.MathUtils
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import combat.util.aEP_DataTool
import org.lazywizard.lazylib.combat.AIUtils
import java.awt.Color

class aEP_TargetSystem : BaseHullMod() {
  companion object {
    private val BONUS = HashMap<HullSize,Array<Float>>()
    private val PUNISH = HashMap<HullSize, Float>()
    init {
      BONUS[HullSize.FRIGATE] = arrayOf(5f,10f,15f,25f)
      BONUS[HullSize.DESTROYER] = arrayOf(0f,5f,10f,20f)
      BONUS[HullSize.CRUISER] = arrayOf(0f,0f,5f,10f)
      BONUS[HullSize.CAPITAL_SHIP] = arrayOf(0f,0f,0f,5f)
    }

    init {
      PUNISH[HullSize.FRIGATE] = 45f
      PUNISH[HullSize.DESTROYER] = 25f
      PUNISH[HullSize.CRUISER] = 10f
      PUNISH[HullSize.CAPITAL_SHIP] = 5f
    }
  }

  var id = "aEP_TargetSystem"

  override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String?) {
    ship?:return
    if(!ship.hasListenerOfClass(RangeListener::class.java)) ship.addListener(RangeListener(ship))
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize?, ship: ShipAPI?): String? {
    if (index == 0) return String.format("%.0f", BONUS[HullSize.FRIGATE]?.get(3)?:0f)
    else if (index == 1) return String.format("%.0f", BONUS[HullSize.DESTROYER]?.get(3)?:0f)
    else if (index == 2) return String.format("%.0f", BONUS[HullSize.CRUISER]?.get(3)?:0f)
    else if (index == 3) return String.format("%.0f", BONUS[HullSize.CAPITAL_SHIP]?.get(3)?:0f)
    else if (index == 4) return String.format("%.0f", PUNISH[HullSize.FRIGATE]?:0f)
    else if (index == 5) return String.format("%.0f", PUNISH[HullSize.DESTROYER]?:0f)
    else if (index == 6) return String.format("%.0f", PUNISH[HullSize.CRUISER]?:0f)
    else if (index == 7) return String.format("%.0f", PUNISH[HullSize.CAPITAL_SHIP]?:0f)
    else return null
  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    //tooltip.addGrid( 5 * 5f + 10f);

    tooltip.addPara("- " + aEP_DataTool.txt("weapon_range_up") + "{%s}", 5f, Color.white, Color.green, String.format("%.0f", BONUS[hullSize]?.get(3)?:0f) + "%")
    tooltip.addPara("- " + aEP_DataTool.txt("weapon_range_up2") + "{%s}", 5f, Color.white, Color.green, String.format("%.0f", BONUS[hullSize]?.get(2)?:0f) + "%")
    tooltip.addPara("- " + aEP_DataTool.txt("weapon_range_up3") + "{%s}", 5f, Color.white, Color.green, String.format("%.0f", BONUS[hullSize]?.get(1)?:0f) + "%")
    tooltip.addPara("- " + aEP_DataTool.txt("weapon_range_up4") + "{%s}", 5f, Color.white, Color.green, String.format("%.0f", BONUS[hullSize]?.get(0)?:0f) + "%")
    tooltip.addPara("- " + aEP_DataTool.txt("max_speed_down") + "{%s}", 5f, Color.white, Color.red, String.format("%.2f", PUNISH[hullSize]))
  }

  override fun isApplicableToShip(ship: ShipAPI): Boolean {
    if (ship.hullSpec.manufacturer == aEP_DataTool.txt("manufacturer")){
      if (ship.isFrigate) return true
      else if (ship.isDestroyer) return true
    }
    return false
  }

  override fun getUnapplicableReason(ship: ShipAPI): String {
    if (ship.hullSpec.manufacturer != aEP_DataTool.txt("manufacturer")) return aEP_DataTool.txt("only_on_FSF")
    else if (ship.isCapital) return aEP_DataTool.txt("not_capital")
    else if (ship.isCruiser) return aEP_DataTool.txt("not_cruiser")
    else return ""

  }

  inner class RangeListener(val ship: ShipAPI) : AdvanceableListener, WeaponRangeModifier{

    val thinkTracker = IntervalUtil(0.5f,0.5f)
    var rangePercent = 0f
    var maxSpeedPunish = 0f

    override fun getWeaponRangePercentMod(ship: ShipAPI?, weapon: WeaponAPI?): Float {
      if(weapon?.hasAIHint(WeaponAPI.AIHints.PD) == false){
        return rangePercent/100f
      }
      return 0f
    }

    override fun getWeaponRangeMultMod(ship: ShipAPI?, weapon: WeaponAPI?): Float {
      return 1f
    }

    override fun getWeaponRangeFlatMod(ship: ShipAPI?, weapon: WeaponAPI?): Float {
      return 0f
    }

    override fun advance(amount: Float) {
      thinkTracker.advance(amount)
      if(thinkTracker.intervalElapsed()) {
        //找附近最大的友军，改变射程加成
        rangePercent = 0f
        //默认没用速度惩罚
        maxSpeedPunish = 0f
        for(f in AIUtils.getNearbyAllies( ship,400f)){
          f?:continue
          if(f.isFighter) continue
          var bonus = 0f
          val list = BONUS[ship.hullSize]?: arrayOf()
          if(f.isFrigate) bonus = list[0]?:0f
          else if(f.isDestroyer) bonus = list[1]?:0f
          else if(f.isCruiser) bonus = list[2]?:0f
          else if(f.isCapital) bonus = list[3]?:0f
          //如果400f内存在友军，施加速度惩罚
          if(bonus > rangePercent) {
            rangePercent = bonus
            maxSpeedPunish = PUNISH[ship.hullSize]?: 0f
          }
        }

        ship.mutableStats.maxSpeed.modifyFlat(id,-maxSpeedPunish)
      }

    }
  }
}