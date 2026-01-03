package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import data.scripts.utils.aEP_ID.Companion.VECTOR2F_ZERO
import data.scripts.utils.aEP_Tool
import data.scripts.hullmods.aEP_SpecialHull
import java.awt.Color

class aEP_BeamRepair : BeamEffectPlugin {
  companion object {
    private const val HULL_REPAIR_MULT = 2f //溢出的装甲维修点数转换成几倍的结构恢复
    private const val REPAIR_STEP_PER_CELL = 10f //单个格子一次遍历最多恢复几点，防止出现棋盘形状装甲

    const val FSF_BONUS = 1f
    const val REPAIR_THRESHOLD = 0.4f
    const val HULL_REPAIR_THRESHOLD = 0.25f
    val REPAIR_COLOR = Color(250, 250, 178, 240)
    val REPAIR_COLOR2 = Color(250, 220, 70, 250)

  }

  var repairAmount = 15f
  var repairPercent = 0.01f
  private var didRepair = false

  init {
    val hlString = Global.getSettings().getWeaponSpec("aEP_ftr_ut_repair_beam").customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) repairAmount = num.toFloat()
      if(i == 1) repairPercent = num.replace("%","").toFloat()/100f
      i += 1
    }
  }


  override fun advance(amount: Float, engine: CombatEngineAPI, beam: BeamAPI) {
    if (beam.didDamageThisFrame() && beam.damageTarget is ShipAPI) {

      //只修一次
      if (didRepair ) return
      didRepair = true

      //修的不是舰船，return
      if(beam.damageTarget !is ShipAPI) return
      val ship = beam.damageTarget as ShipAPI

      //先在光束落点加一个特效
      engine.spawnExplosion(
        beam.to, VECTOR2F_ZERO, REPAIR_COLOR2, 60f, 0.75f)
      engine.addHitParticle(
        beam.to, VECTOR2F_ZERO, 150f, 0.75f, 0.1f, 0.2f, REPAIR_COLOR)

      //把本船，本船的模块，本船的母舰，统统视为一个整体
      //按照顺序选择修谁
      val target = aEP_BeamFlux.pickTarget(ship, ::findRepairNeedPercent)

      var maxRepairPoint = repairAmount + ship.armorGrid.armorRating * repairPercent
      //计算fsf加成
      if(ship.variant?.hasHullMod(aEP_SpecialHull.ID) == true){
        maxRepairPoint *= FSF_BONUS
      }

      aEP_Tool.findToRepair(target, maxRepairPoint,
        REPAIR_THRESHOLD, HULL_REPAIR_THRESHOLD, REPAIR_STEP_PER_CELL, HULL_REPAIR_MULT,
        true)

    }
  }

  /**
   * @return 需要维修部分的多少，越高代表损失的装甲和结构越多
   *
  * */
  fun findRepairNeedPercent(ship: ShipAPI): Float{
    //维修装甲
    val xSize = ship.armorGrid.leftOf + ship.armorGrid.rightOf
    val ySize = ship.armorGrid.above + ship.armorGrid.below
    val cellMaxArmor = ship.armorGrid.maxArmorInCell

    val maxArmorCellVal = cellMaxArmor * REPAIR_THRESHOLD
    val maxHullVal = ship.maxHitpoints * HULL_REPAIR_THRESHOLD

    var armorLostBelowThres = 0f
    var hullLostBelowThres = 0f

    for (x in 0 until xSize) {
      for (y in 0 until ySize) {
        val armorNow = ship.armorGrid.getArmorValue(x, y)
        armorLostBelowThres += (maxArmorCellVal - armorNow).coerceAtLeast(0f)
      }
    }

    hullLostBelowThres += (maxHullVal - ship.hitpoints).coerceAtLeast(0f)
    //血线紧急的优先度高于装甲损失
    hullLostBelowThres * 2f


    return  armorLostBelowThres + hullLostBelowThres
  }
}