package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.IntervalUtil
import combat.impl.aEP_BaseCombatEffect
import combat.util.aEP_DataTool
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.getAmount
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color

class aEP_RepulseMissileLaunch : BaseShipSystemScript() {

  companion object {
    const val ID = "aEP_RepulseMissileLaunch"
    const val WEAPON_ID = "aEP_fga_xiliu_mk2_missile"

    //每秒耗散，耗散降低和武器是否开火没关系
    const val FLUX_REDUCE_FLAT= 300f
    const val FLUX_REDUCE_CURR_PERCENT = 0.1f
  }

  var forceUse = false
  var disablePermanent = false
  var particleTimer = IntervalUtil(0.05f, 0.15f)
  val sprite = Global.getSettings().getSprite("aEP_FX","xiliu_mk2_round_glow")
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI

    //更新充能提示圈
    updateIndicator(ship, effectLevel)

    //死后强行使用一次
    if (!ship.isAlive && !disablePermanent) {
      if(!ship.system.isActive){
        //舰船死后，effectiveLevel会被锁到0，所以使用一个forceUse跳过下面的系统未激活检测
        disablePermanent = true
        forceUse = true
        ship.fluxTracker.currFlux = ship.fluxTracker.maxFlux
        ship.system.ammo = 1
        ship.system.deactivate()
        ship.system.cooldownRemaining = 0f
        ship.useSystem()
      }


    }

    val cooldownRemaining = ship.system.cooldownRemaining
    //val barrelLevel = ((3f - cooldownRemaining)/1.5f).coerceAtLeast(0f).coerceAtMost(1f)

    //改数据，激活时
    //防止在装配页面生效改动耗散
    if(Global.getCombatEngine().isEntityInPlay(ship)){
      updateStats(ship, effectLevel)
    }

    //系统没激活从这出去
    if (effectLevel < 0.9f && !forceUse) {
      for (w in ship.allWeapons) {
        if (w.spec.weaponId.equals(WEAPON_ID)) {
          //通过消除弹药来强制缩回炮管
          //w.barrelSpriteAPI.alphaMult = barrelLevel
        }
      }
      return
    }

    //set jitter
    ship.setJitter(id, Color(240,80,220, 15), effectLevel, 16, 5f)
    ship.isJitterShields = false

    val amount = getAmount(ship)
    particleTimer.advance(amount)

    for (w in ship.allWeapons) {
      if (w.spec.weaponId.equals(WEAPON_ID)) {
        w.ammo = 3
        //中途把幅能打空就强制结束系统
        if (!isUsable(ship.system,ship)) {
          ship.system.deactivate()
          forceUse = false
          break
        }
        //有时候武器被打坏了就射不出来了，这武器不在隐藏槽位里面
        aEP_Tool.keepWeaponAlive(w)
        w.setForceFireOneFrame(true)
      }
    }
  }


  override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI): Boolean {
    if(ship.fluxLevel > 0.1f && ship.fluxTracker.currFlux > 100f){
      return true
    }
    return false
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String? {
    if(!isUsable(system,ship)){
      return aEP_DataTool.txt("aEP_RepulseMissileLaunch02")
    }
    return ""
  }

  fun updateStats(ship: ShipAPI, effectLevel: Float){
    if(forceUse && isUsable(ship.system,ship)){
      val toAdd = FLUX_REDUCE_FLAT + ship.fluxTracker.currFlux * FLUX_REDUCE_CURR_PERCENT
      ship.mutableStats.fluxDissipation.modifyFlat(ID, toAdd )
      ship.mutableStats.hardFluxDissipationFraction.modifyFlat(ID, 1f)
      return
    }

    if(effectLevel <= 0.1f){
      ship.mutableStats.fluxDissipation.unmodify(ID)
      ship.mutableStats.hardFluxDissipationFraction.unmodify(ID)
    }else{
      val toAdd = FLUX_REDUCE_FLAT + ship.fluxTracker.currFlux * FLUX_REDUCE_CURR_PERCENT
      ship.mutableStats.fluxDissipation.modifyFlat(ID, toAdd * effectLevel)
      ship.mutableStats.hardFluxDissipationFraction.modifyFlat(ID, effectLevel)
    }
  }

  fun updateIndicator(ship: ShipAPI, effectLevel: Float){
    for(slot in ship.hullSpec.allWeaponSlotsCopy){
      if(slot.isDecorative && slot.id.equals("ID_01")){
        val loc = slot.computePosition(ship)
        var c = Color.red
        if(ship.system.ammo > 0 && ship.system.state == ShipSystemAPI.SystemState.IDLE){
          c = Color.green
        }

        //完全冷却为1，刚进入冷却为0
        var cooldownLevel = (1f - ship.system.cooldownRemaining/ship.system.cooldown).coerceAtLeast(0f).coerceAtMost(1f)
        //完成装填为1，刚开始装填为0
        var ammoLevel = ship.system.ammoReloadProgress
        //如果目前还剩使用次数，就直接改ammoLevel为1
        if(ship.system.ammo > 0) ammoLevel = 1f
        val glowLevel = Math.min(cooldownLevel, ammoLevel)

        if(ship.system.cooldownRemaining > 0f){

          c = Color(1f,glowLevel,0f,glowLevel*0.7f + 0.3f)
        }

        sprite.setAdditiveBlend()
        loc.set(MathUtils.getRandomPointInCircle(loc,0.25f))
        MagicRender.singleframe(
          sprite, loc, Vector2f(sprite.width,sprite.height), ship.facing + 90f,
          c,true)
        break
      }
    }


  }
}
