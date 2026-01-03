package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_Tool
import data.scripts.weapons.aEP_DecoAnimation
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color

class aEP_GrenadeThrower:  BaseShipSystemScript() {

  companion object{
    const val ID = "aEP_GrenadeThrower"

  }


  var decoToLevel = 0f
  var decoLevel = 0f
  var decoMoveSpeed = 1f
  var didBurst = false

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats.entity?: return) as ShipAPI
    val amount = aEP_Tool.getAmount(ship)

    updateDeco(ship,effectLevel, amount)
    if(effectLevel >= 1f){
      for(w in ship.allWeapons){
        if(w.slot.id.startsWith("SYS")){
          w.setForceFireOneFrame(true)
          if(!didBurst){
            didBurst = true
            val sizeMult = 1f
            Global.getCombatEngine().spawnExplosion(
              w.location,
              Vector2f.add(aEP_Tool.speed2Velocity(w.currAngle, 60f),Misc.ZERO,null),
              Color(240,110,20,255), 75f * sizeMult, 0.3f)
            //闪光
            Global.getCombatEngine().addSmoothParticle(
              w.location,
              Misc.ZERO,
              450f,1f,0f,0.15f,Color(255,215,50,254))
          }
        }
      }
    }else{
      didBurst = false
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

    for(w in ship.allWeapons){
      if(!w.slot.isDecorative) continue
      if(w.spec.weaponId.equals("aEP_des_lianliu_front_cover")){
        val plugin = w.effectPlugin as aEP_DecoAnimation
        val moveLevel = (level*2f).coerceAtMost(1f)
        plugin.setMoveToLevel(moveLevel)
      }
    }


    if(aEP_Tool.isDead(ship)) return
    //1f为绿，0f为红
    for(slot in ship.hullSpec.allWeaponSlotsCopy){
      if(slot.id.startsWith("ID")){
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

}