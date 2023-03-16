package data.scripts.weapons

import combat.util.aEP_Tool.Util.limitToTop
import com.fs.starfarer.api.combat.BeamEffectPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.lwjgl.util.vector.Vector2f
import combat.util.aEP_Tool
import data.hullmods.aEP_FlyingTank
import java.awt.Color
import java.util.*

class aEP_RepairBeam : BeamEffectPlugin {
  companion object {
    private const val REPAIR_AMOUNT = 3f
    private const val REPAIR_PERCENT= 0.003f
    private const val FSF_BONUS = 2f
    private const val REPAIR_THRESHOLD = 0.5f
    private val repairing = Color(250, 250, 200, 220)
  }

  private var timer = 0
  override fun advance(amount: Float, engine: CombatEngineAPI, beam: BeamAPI) {
    if (beam.didDamageThisFrame() && beam.damageTarget is ShipAPI) {
      timer += 1
      if (timer < 3) {
        return
      }
      timer = 0
      val ship = beam.damageTarget as ShipAPI
      val rand1 = Random()


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
          if(!didSpark){
            val minArmorLoc = ship.armorGrid.getLocation(minX,minY)
            for(i in 0 until 3){
              var randomX = (rand1.nextInt(200) - 100).toFloat()
              var randomY = (rand1.nextInt(200) - 100).toFloat()
              engine.addSmoothParticle(
                minArmorLoc,  //added loc
                Vector2f(randomX, randomY),  //random initial speed
                30f,  //size
                1f,  //brightness
                1f,  //duration
                repairing) //color
            }
            didSpark = true
          }


        }else{
          //如果当前最低装甲格都大于阈值，就中断修理
          engine.addFloatingText(ship.location, "No_Need_toRepair", 15f, repairing, ship, 0.25f, 20f)
          break
        }
      }

    }
  }

}