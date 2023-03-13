package data.shipsystems.scripts

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import data.scripts.weapons.aEP_maodian_drone_missile
import javax.swing.plaf.ColorUIResource

class aEP_MaodianDroneLaunch: BaseShipSystemScript() {
  companion object{
    const val SYSTEM_RANGE = 1000f
  }

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    val ship = (stats?.entity?: return)as ShipAPI
    //每帧运行
    //检测系统是否就绪，如果否就不显示模拟无人机的导弹
    var weapon : WeaponAPI? = null
    for(w in ship.allWeapons){
      if(!w.spec.weaponId.equals("aEP_MD_drone_missile")) continue
      weapon = w
      if(state != ShipSystemStatsScript.State.IDLE && state != ShipSystemStatsScript.State.IN){
        weapon.animation.frame = 1
      }else{
        weapon.animation.frame = 0
      }
    }


    weapon?: return
    //运行一帧
    if(effectLevel >= 1){
      Global.getCombatEngine().spawnMuzzleFlashOrSmoke(ship,weapon.slot,weapon.spec,0,weapon.currAngle)
      val proj = Global.getCombatEngine().spawnProjectile(ship,weapon,weapon.spec.weaponId,weapon.location,weapon.currAngle,ship.velocity)
      aEP_maodian_drone_missile().onFire(proj as DamagingProjectileAPI?,weapon,Global.getCombatEngine(),weapon.spec.weaponId)
    }
  }


}