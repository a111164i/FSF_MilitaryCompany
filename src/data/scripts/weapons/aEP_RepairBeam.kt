package data.scripts.weapons

import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import java.awt.Color

class aEP_RepairBeam : BeamEffectPlugin {
  companion object {
    private const val REPAIR_AMOUNT = 3f
    private const val REPAIR_PERCENT= 0.003f
    private const val FSF_BONUS = 2f
    private const val REPAIR_THRESHOLD = 0.5f
    private val REPAIR_COLOR = Color(250, 250, 178, 220)
    private val REPAIR_COLOR2 = Color(250, 220, 70, 220)
  }

  private var didRepair = false
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
      var minArmorLevel = 10f
      var minX = 0
      var minY = 0

      var toRepair = REPAIR_AMOUNT + ship.armorGrid.armorRating * REPAIR_PERCENT

      var didSpark = false
      while (toRepair > 0f){
        //find the lowest armor grid
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
          if(needRepair > toRepair){
            toAddArmor = toRepair
            toRepair = 0f
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
                0.5f
              )
              engine.spawnExplosion(
                minArmorLoc,
                ship.velocity,
                REPAIR_COLOR2,
                30f,
                0.5f
              )
              didSpark = true
            }

          }

        }else{
          //如果当前最低装甲格都大于阈值，就中断修理
          engine.addFloatingText(ship.location, "No Need toRepair", 15f, REPAIR_COLOR, ship, 0.25f, 20f)
          break
        }
      }
      ship.syncWithArmorGridState()
      ship.syncWeaponDecalsWithArmorDamage()
    }
  }

}