package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import combat.util.aEP_Tool
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color

class aEP_MineLayer:  BaseShipSystemScript() {

  companion object{
    const val ID = "aEP_MineLayer"

  }


  var decoToLevel = 0f
  var decoLevel = 0f
  var decoMoveSpeed = 1f

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats.entity?: return) as ShipAPI
    val amount = aEP_Tool.getAmount(ship)

    updateDeco(ship,effectLevel, amount)
    if(effectLevel >= 1f){
      for(w in ship.allWeapons){
        if(w.slot.id.startsWith("SYS")){
          //w.setForceFireOneFrame(true)
        }
      }
    }

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    //复制粘贴这行
    val ship = (stats.entity?: return) as ShipAPI
    val amount = aEP_Tool.getAmount(ship)
  }

  fun updateDeco(ship: ShipAPI, level:Float, amount:Float){

    if(ship.system.ammo <= 0f || ship.system.state != ShipSystemAPI.SystemState.IDLE){
      decoToLevel = 1f
    }else{
      decoToLevel = 0f
    }


    //变换level
    if(decoToLevel > decoLevel){
      val toChange = (decoToLevel-decoLevel).coerceAtMost(decoMoveSpeed*amount)
      decoLevel += toChange
    }else{
      val toChange = (decoLevel-decoToLevel).coerceAtMost(decoMoveSpeed*amount)
      decoLevel -= toChange
    }

    if(aEP_Tool.isDead(ship)) return
    //1f为绿，0f为红
    for(slot in ship.hullSpec.allWeaponSlotsCopy){
      if(!slot.id.startsWith("SYS")) continue
      val slotLoc = slot.computePosition(ship)
      val facing = slot.computeMidArcAngle(ship)
      val sprite = Global.getSettings().getSprite("aEP_FX","raoliu_launch_port")

      val renderLoc = aEP_Tool.getExtendedLocationFromPoint(slotLoc, facing, -5f + 6f*decoLevel)
      MagicRender.singleframe(
        sprite, renderLoc, Vector2f(sprite.width,sprite.height), facing- 90f,
        Color.white,
        false, CombatEngineLayers.BELOW_SHIPS_LAYER)

      val glow = Global.getSettings().getSprite("aEP_FX","raoliu_launch_port_glow")
      val glowColorLevel = ((decoLevel-0.901f)*10f).coerceAtLeast(0f)
      var c = Color(glowColorLevel,(1f-glowColorLevel),0f)
      if(decoToLevel > decoLevel){
        c = Color.red
      }
      MagicRender.singleframe(
        glow, renderLoc, Vector2f(sprite.width,sprite.height), facing- 90f,
        c,
        false, CombatEngineLayers.BELOW_SHIPS_LAYER)
    }
  }

}