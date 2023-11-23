package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color

class aEP_ManeuverThruster:  BaseShipSystemScript() {

  companion object{
    const val MAX_SPEED_FLAT_BONUS = 250f
    const val MAX_TURN_FLAT_BONUS = 30f
  }


  val allEngineList = ArrayList<engineController>()
  var didRollBack = false
  override fun apply(stats: MutableShipStatsAPI?, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    val amount = aEP_Tool.getAmount(ship)
    //初始化一次
    if(allEngineList.isEmpty()){
      //找到每个装饰槽位
      for(slot in ship.hullSpec.allWeaponSlotsCopy){
        if(!slot.isDecorative) continue
        if(slot.id.startsWith("L") ||slot.id.startsWith("R") ){
          val loc = slot.computePosition(ship)
          var closestDistSq = Float.MAX_VALUE
          var closestEngine : ShipEngineAPI? = null
          //找到距离该槽位最近的引擎
          for(e in ship.engineController.shipEngines){
            val distSq = MathUtils.getDistanceSquared(loc,e.location)
            if(distSq < closestDistSq){
              closestDistSq = distSq
              closestEngine = e
            }
          }
          //找到了，加入list
          if(closestEngine != null){
            val clz = engineController(slot,ship,closestEngine)
            if(slot.id.startsWith("R") ){
              clz.isRight = true
              clz.sprite = Global.getSettings().getSprite("aEP_FX","shangshengliu_mk3_thruster_r")
            }
            allEngineList.add(clz)
          }
        }
      }
    }

    for(c in allEngineList){
      c.advance(amount)
      if(effectLevel > 0f){
        ship.getEngineController().forceShowAccelerating()
        c.toAngle = aEP_Tool.computeCurrentManeuveringDir(ship)
      }else{
        c.toAngle = ship.facing
      }

      c.render()
    }


    //修改数据
    if (state == ShipSystemStatsScript.State.OUT) {
      stats.maxSpeed.unmodify(id) // to slow down ship to its regular top speed while powering drive down
      stats.maxTurnRate.unmodify(id)
    }
    else {
      didRollBack = false
      stats.maxSpeed.modifyFlat(id, MAX_SPEED_FLAT_BONUS)
      stats.acceleration.modifyFlat(id, MAX_SPEED_FLAT_BONUS * 2f * effectLevel)
      stats.deceleration.modifyFlat(id, MAX_SPEED_FLAT_BONUS * 2f * effectLevel)

      stats.maxTurnRate.modifyFlat(id, MAX_TURN_FLAT_BONUS)
      stats.maxTurnRate.modifyPercent(id, 100f)
      stats.turnAcceleration.modifyFlat(id, MAX_TURN_FLAT_BONUS * 2f * effectLevel)
      stats.turnAcceleration.modifyPercent(id, 200f * effectLevel)
    }
    //结束时修改回来
    if(effectLevel <= 0f && !didRollBack){
      didRollBack = true
      stats.maxSpeed.unmodify(id)
      stats.maxTurnRate.unmodify(id)
      stats.turnAcceleration.unmodify(id)
      stats.acceleration.unmodify(id)
      stats.deceleration.unmodify(id)
    }

//    //修改引擎颜色
//    if (stats.entity is ShipAPI) {
//      val ship = stats.entity as ShipAPI
//      ship.engineController.fadeToOtherColor(this, this.color, Color(0, 0, 0, 0), effectLevel, 0.67f)
//      ship.engineController.extendFlame(this, 2.0f * effectLevel, 0.0f * effectLevel, 0.0f * effectLevel)
//    }
  }

  class engineController(val slot: WeaponSlotAPI, val ship:ShipAPI, val engine:ShipEngineAPI){

    var isRight = false
    var sprite = Global.getSettings().getSprite("aEP_FX","shangshengliu_mk3_thruster_l")

    //curr是头朝向的绝对角度
    var currAngle = ship.facing
    var toAngle = ship.facing
    var speed = 180f
    //左右限制是相对角度
    var maxLeft = 90f
    var maxRight = -90f

    fun advance(amount: Float){
      val moveMost = speed*amount
      var toMove = MathUtils.getShortestRotation(currAngle, toAngle)
      //在右边的引擎，刚好180度旋转，不使用默认的左旋，而是右旋
      if(isRight && toMove < -179f){
        toMove = 180f
      }else if(!isRight && toMove > 179f){
        toMove = -180f
      }
      toMove = MathUtils.clamp(toMove,-moveMost, moveMost)
      currAngle += toMove
      currAngle = MathUtils.clamp(currAngle,ship.facing+maxLeft, ship.facing+maxRight)

      val slotLoc = slot.computePosition(ship)
      if(isRight){
        val flameFacing = currAngle - ship.facing - 135f
        //engine.location.set(aEP_Tool.getExtendedLocationFromPoint(slotLoc, flameFacing,100f))
        engine.engineSlot.angle = flameFacing
        ship.engineController.forceShowAccelerating()
        if(!ship.engineController.isAccelerating){
        }
      }else{
        val flameFacing = currAngle - ship.facing + 135f
        //engine.location.set(aEP_Tool.getExtendedLocationFromPoint(slotLoc, flameFacing,100f))
        engine.engineSlot.angle = flameFacing
      }

    }

    fun render(){
      val loc = slot.computePosition(ship)
      //渲染原图
      MagicRender.singleframe(sprite,loc,
        Vector2f(sprite.width,sprite.height),
        currAngle - 90f,
        Color.white,
        false,CombatEngineLayers.BELOW_SHIPS_LAYER)
    }
  }
}