package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import com.fs.starfarer.api.util.IntervalUtil
import combat.util.aEP_ID
import combat.util.aEP_ID.Companion.VECTOR2F_ZERO
import data.scripts.hullmods.aEP_MarkerDissipation
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_RepairBeam : BeamEffectPlugin {
  companion object {
    private const val HULL_REPAIR_MULT = 1.5f //溢出的装甲维修点数转换成几倍的结构恢复
    private const val REPAIR_STEP_PER_CELL = 6f //单个格子一次遍历最多恢复几点，防止出现棋盘形状装甲

    private const val FSF_BONUS = 1.75f
    const val REPAIR_THRESHOLD = 0.5f
    const val HULL_REPAIR_THRESHOLD = 0.25f
    val REPAIR_COLOR = Color(250, 250, 178, 240)
    val REPAIR_COLOR2 = Color(250, 220, 70, 250)

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
      engine.spawnExplosion(
        beam.to, VECTOR2F_ZERO, REPAIR_COLOR2, 60f, 0.75f)
      engine.addHitParticle(
        beam.to, VECTOR2F_ZERO, 150f, 0.75f, 0.1f, 0.2f, REPAIR_COLOR)

      //把本船，本船的模块，本船的母舰，统统视为一个整体
      val checkShipList = ArrayList<ShipAPI>()
      checkShipList.add(ship)
      if(ship.isStationModule && ship.parentStation != null){
        checkShipList.addAll(ship.parentStation.childModulesCopy)
      }
      if(ship.isStation && ship.childModulesCopy.size > 0){
        checkShipList.addAll(ship.childModulesCopy)
      }

      //按照顺序选择修谁，前者如果完全不需要修复就轮到后一个
      val it = checkShipList.iterator()
      while (it.hasNext() && findToRepair(it.next(),engine) >= 1){}

    }
  }

  /**
   * @return 维修点数剩余百分比，0-1，0代表完全用于维修，1代表船是好的不用修
   *
  * */
  fun findToRepair(ship: ShipAPI, engine: CombatEngineAPI): Float{
    //维修装甲
    val xSize = ship.armorGrid.leftOf + ship.armorGrid.rightOf
    val ySize = ship.armorGrid.above + ship.armorGrid.below
    val cellMaxArmor = ship.armorGrid.maxArmorInCell

    val maxRepairPoint = repairAmount + ship.armorGrid.armorRating * repairPercent
    var toRepair = maxRepairPoint
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


      val armorAtMin = ship.armorGrid.getArmorValue(minX, minY)
      val threshold = cellMaxArmor * REPAIR_THRESHOLD
      val needRepair = threshold - armorAtMin

      // 如果当前最低的一块甲不满就修复
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
        //第一个修好的格子创造一个火花，每次维修最多生成一次
        val minArmorLoc = ship.armorGrid.getLocation(minX,minY)
        for(i in 0 until 12){
          if(!didSpark){
            engine.spawnExplosion(
              minArmorLoc, ship.velocity, REPAIR_COLOR, 15f, 0.5f)
            engine.spawnExplosion(
              minArmorLoc, ship.velocity, REPAIR_COLOR2, 30f, 0.5f)
            didSpark = true
          }

        }
      }else{//如果当前装甲最低的格子都不用修，break出去
        break
      }
    }
    ship.syncWithArmorGridState()
    ship.syncWeaponDecalsWithArmorDamage()

    //如果装甲修好了，维修点数还有省的就加结构
    if(toRepair > 0){
      val hullDamagedBelowThreshold = ship.maxHitpoints * HULL_REPAIR_THRESHOLD - ship.hitpoints
      if(hullDamagedBelowThreshold > 0) {
        val repairToHull = (toRepair * HULL_REPAIR_MULT).coerceAtMost(hullDamagedBelowThreshold)
        ship.hitpoints += repairToHull
        toRepair -= repairToHull/ HULL_REPAIR_MULT
      }
    }

    toRepair = toRepair.coerceAtLeast(0f)
    return toRepair/maxRepairPoint
  }
}