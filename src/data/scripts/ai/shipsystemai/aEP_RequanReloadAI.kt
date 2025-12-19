package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.combat.WeaponAPI
import combat.impl.VEs.aEP_MovingSmoke
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Tool
import data.scripts.hullmods.aEP_MissilePlatform
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_RequanReloadAI: aEP_BaseSystemAI() {

  var loadingMap : aEP_MissilePlatform.LoadingMap? = null

  override fun initImpl() {
    thinkTracker.setInterval(0.5f,1.5f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    //录入 loadingMap
    loadingMap?: run {
      if (ship.customData.containsKey(aEP_MissilePlatform.ID)) {
        loadingMap = ship.customData[aEP_MissilePlatform.ID] as aEP_MissilePlatform.LoadingMap
      }
      return
    }

    //用不起就不需要思考了
    if(ship.fluxTracker.maxFlux - ship.fluxTracker.currFlux < ship.system.fluxPerSecond) {
      shouldActive = false
      return
    }


    var willing = 0f
    var reloadLevelTotal = 0f
    var totalOp = 0f
    val maxRate = loadingMap?.maxRate?:1f
    val currRate = loadingMap?.currRate?:1f

    var needReloadRqMissile = false
    for(w in ship.allWeapons){
      //对于不需要弹药的导弹，跳过
      if(w.type != WeaponAPI.WeaponType.MISSILE) continue
      if(!w.usesAmmo() || w.ammo == Int.MAX_VALUE) continue

      //计算所有非内置武器目前的冷却时间，看看按一下能装多少导弹
      if( w.slot.weaponType == WeaponAPI.WeaponType.MISSILE || w.slot.weaponType == WeaponAPI.WeaponType.COMPOSITE) {
        val op = w.spec.getOrdnancePointCost(null) + 0.01f
        //小心有些武器的只有delay，没用cooldown
        val coolDown = w.cooldown + 0.1f
        val coolDownRemaining = w.cooldownRemaining
        reloadLevelTotal += (op * (coolDownRemaining / coolDown))
        totalOp += op

      //计算内置的热泉导弹的情况
      }else if(w.spec.weaponId.contains("requan_missile")){
        if(w.ammo <= 1 ){
          needReloadRqMissile = true
        }
      }
    }

    //0f-1f，冷却百分比
    reloadLevelTotal /= totalOp

    //当热泉导弹需要填装时填
    if(needReloadRqMissile){
      willing += 0.05f
    }

    //总装填率目前下降了多少
    val emptyRate = (maxRate - currRate)/maxRate

    //平衡一下两者的权重，默认情况下reloadLevel和emptyRate均为0-1，满了都会导致willing大于1直接使用系统
    willing += (reloadLevelTotal * 0.8f)
    willing += (emptyRate * 1.5f)

    //保证幅能小于0.25时，光是用完内置导弹就会使用f
    //保证正常幅能不要超过0.5
    val highThreshold = 0.5f
    val lowThreshold = 0.1f
    val lowFluxBonus = (willing * 0.5f + 0.15f)

    //低幅能无条件刷满
    if(ship.fluxLevel < 0.1f){
      if(emptyRate > 0.15f) willing += 1f
    } else if (ship.fluxLevel < 0.25f){
      if(emptyRate > 0.3f) willing += 1f
    } else if (ship.fluxLevel < 0.5f){
      willing += 0.3f * (0.5f - ship.fluxLevel)/0.25f
    }

    //高幅能不愿意刷
    if(ship.fluxLevel > 0.7f){
      willing *= 0.5f
    }else if (ship.fluxLevel > 0.5f){
      willing *= 0.8f
    }

    //根据母舰的aiFlag修正willing
    if(flags.hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_AUTOFIRE_NON_ESSENTIAL_GROUPS)) willing -= 0.25f
    if(flags.hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_FLUX)) willing -= 0.5f


    willing *= MathUtils.getRandomNumberInRange(0.8f,1.2f)
    //aEP_Tool.addDebugLog(willing.toString())
    shouldActive = false
    if(willing >= 1f){
      shouldActive = true
    }
  }
}