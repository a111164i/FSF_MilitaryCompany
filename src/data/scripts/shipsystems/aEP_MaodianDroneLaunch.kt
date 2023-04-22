package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import data.scripts.weapons.aEP_cru_maodian_missile
import org.lwjgl.util.vector.Vector2f

class aEP_MaodianDroneLaunch: BaseShipSystemScript() {
  companion object{
    const val SYSTEM_RANGE = 1600f
  }

  var targetLoc:Vector2f? = null
  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI

    //每帧运行
    //检测系统是否就绪，如果否就不显示模拟无人机的导弹
    var weapon : WeaponAPI? = null
    for(w in ship.allWeapons){
      if(!w.spec.weaponId.equals("aEP_cru_maodian_missile")) continue
      weapon = w
      if(state != ShipSystemStatsScript.State.IDLE && state != ShipSystemStatsScript.State.IN){
        weapon.animation.frame = 0
      }else{
        weapon.animation.frame = 1
      }
    }
    weapon?: return


    //运行一帧
    if(effectLevel >= 1){
      Global.getCombatEngine().spawnMuzzleFlashOrSmoke(ship,weapon.slot,weapon.spec,0,weapon.currAngle)
      val proj = Global.getCombatEngine().spawnProjectile(ship,weapon,weapon.spec.weaponId,weapon.location,weapon.currAngle,ship.velocity)
      aEP_cru_maodian_missile().onFire(proj as DamagingProjectileAPI,weapon,Global.getCombatEngine(),weapon.spec.weaponId)
    }
  }

}