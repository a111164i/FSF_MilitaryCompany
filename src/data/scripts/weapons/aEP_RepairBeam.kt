package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import com.fs.starfarer.api.util.IntervalUtil
import data.scripts.hullmods.aEP_MarkerDissipation
import java.awt.Color

class aEP_RepairBeam : BeamEffectPlugin {
  companion object {
    private const val HULL_REPAIR_MULT = 1.5f //溢出的装甲维修点数转换成几倍的结构恢复
    private const val REPAIR_STEP_PER_CELL = 6f //单个格子一次遍历最多恢复几点，防止出现棋盘形状装甲

    private const val FSF_BONUS = 1.75f
    const val REPAIR_THRESHOLD = 0.5f
    const val HULL_REPAIR_THRESHOLD = 0.25f
    private val REPAIR_COLOR = Color(250, 250, 178, 220)
    private val REPAIR_COLOR2 = Color(250, 220, 70, 220)

  }

  var repairAmount = 4f
  var repairPercent = 0.004f
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
      val spec = DamagingExplosionSpec(1f, 70f, 35f,
        0f,0f,
        CollisionClass.NONE, CollisionClass.NONE,
        5f,5f,0.8f,30,
        REPAIR_COLOR, REPAIR_COLOR2
      )
      spec.isUseDetailedExplosion = true
      spec.detailedExplosionFlashDuration = 0.12f
      spec.detailedExplosionFlashRadius = 70f
      spec.detailedExplosionRadius = 70f
      spec.detailedExplosionFlashColorFringe = REPAIR_COLOR2
      spec.detailedExplosionFlashColorCore = REPAIR_COLOR
      spec.detailedExplosionRadius = 70f
      engine.spawnDamagingExplosion(spec,beam.source,beam.to)


      //维修装甲
      val xSize = ship.armorGrid.leftOf + ship.armorGrid.rightOf
      val ySize = ship.armorGrid.above + ship.armorGrid.below
      val cellMaxArmor = ship.armorGrid.maxArmorInCell

      var toRepair = repairAmount + ship.armorGrid.armorRating * repairPercent
      if(ship.variant?.hasHullMod(aEP_MarkerDissipation.ID) == true) toRepair *= FSF_BONUS
      toRepair *= FSF_BONUS
      var didSpark = false
      while (toRepair > 0f){
        //find the lowest armor grid
        var minArmorLevel = 10f
        var minX = 0
        var minY = 0
        for (x in 0 until xSize) {
          for (y in 0 until ySize) {
            val armorNow = ship.armorGrid.getArmorValue(x, y)
            val armorLevel = armorNow / cellMaxArmor
            if (armorLevel <= minArmorLevel) {
              minArmorLevel = armorLevel
              minX = x
              minY = y
            }
          }
        }

        // 如果当前最低的一块甲不满就修复，否则不用修直接break
        val armorAtMin = ship.armorGrid.getArmorValue(minX, minY)
        val threshold = cellMaxArmor * REPAIR_THRESHOLD
        val needRepair = threshold - armorAtMin
        //做不到完全回复
        if ( needRepair > 0f) {
          var toAddArmor = 0f

          if(needRepair > REPAIR_STEP_PER_CELL){
            toAddArmor = REPAIR_STEP_PER_CELL
            toRepair -= REPAIR_STEP_PER_CELL
          }else{
            toAddArmor = needRepair
            toRepair -= toAddArmor
          }

          ship.armorGrid.setArmorValue(minX, minY, armorAtMin + toAddArmor)

          //每一个修好的格子创造一个火花，每次维修最多生成一次

          val minArmorLoc = ship.armorGrid.getLocation(minX,minY)
          for(i in 0 until 12){
            if(!didSpark){
              engine.spawnExplosion(
                minArmorLoc,
                ship.velocity,
                REPAIR_COLOR,
                15f,
                0.5f)
              engine.spawnExplosion(
                minArmorLoc,
                ship.velocity,
                REPAIR_COLOR2,
                30f,
                0.5f)
              didSpark = true
            }

          }

        }else{
          break
        }
      }
      ship.syncWithArmorGridState()
      ship.syncWeaponDecalsWithArmorDamage()

      if(toRepair > 0){
        val hullDamagedBelowThreshold = ship.maxHitpoints * HULL_REPAIR_THRESHOLD - ship.hitpoints
        if(hullDamagedBelowThreshold > 0f){
          ship.hitpoints += (toRepair * HULL_REPAIR_MULT).coerceAtMost(hullDamagedBelowThreshold)
        }
      }else{
        //如果维修完装甲就不剩了，就显示修好了
        if(!didSpark){
          engine.addFloatingText(ship.location, "No Need Repair", 15f, REPAIR_COLOR, ship, 0.5f, 1.5f)
        }
      }
    }
  }

}