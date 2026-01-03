package data.scripts.hullmods

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.util.IntervalUtil
import data.scripts.utils.aEP_Tool

class aEP_HotLoader : aEP_BaseHullMod() {

  companion object {
    //private final static float RELOAD_THRESHOLD = 2;//by seconds
    const val BASE_BONUS = 50f

    const val SMOD_BONUS = 50f

    const val ID = "aEP_HotLoader"

  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.MAGAZINES)
  }

  override fun applyEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
    stats.ballisticAmmoRegenMult.modifyPercent(ID, BASE_BONUS)
    stats.energyAmmoRegenMult.modifyPercent(ID, BASE_BONUS)
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {


  }

  override fun applySmodEffectsAfterShipCreationImpl(ship: ShipAPI, stats:MutableShipStatsAPI, id: String) {
    stats.ballisticAmmoRegenMult.modifyPercent(ID+"_smod", SMOD_BONUS)
    stats.energyAmmoRegenMult.modifyPercent(ID+"_smod", SMOD_BONUS)
  }

  internal class AmmoReloadFaster(var ship: ShipAPI) : AdvanceableListener {
    val checkTracker = IntervalUtil(0.2f,0.2f)
    var reloadingMap: MutableMap<WeaponAPI, Float> = HashMap()

    override fun advance(amount: Float) {
      checkTracker.advance(amount)
      //每0.25秒计算一次全武器
      if(!checkTracker.intervalElapsed()) return
      val timePassed = checkTracker.elapsed
      for (w in ship.allWeapons) {
        //排除不用子弹的，系统武器，内置武器，和系统槽位上面的普通武器
        if(!aEP_Tool.isNormalWeaponSlotType(w.slot, false)) continue
        if(!w.usesAmmo() || w.ammo == Int.MAX_VALUE) continue
        //排除目前已经装满的
        if(w.ammo >= w.maxAmmo) continue

        //获取当前这个武器装了多少
        var weaponTimer = 0f
        reloadingMap[w]?: run { reloadingMap[w] = weaponTimer }
        weaponTimer = reloadingMap[w]!!

        //根据不同的武器类型计算弹药恢复速度
        var ammoPerSecond = w.spec.ammoPerSecond
        weaponTimer += ammoPerSecond * (BASE_BONUS/100f) * timePassed

        //获取该武器一轮装填的量，cap到当前最大弹药数
        val needToReload = (w.maxAmmo - w.ammo).coerceAtLeast(0).coerceAtMost(w.ammoTracker.reloadSize.toInt())
        while (weaponTimer >= needToReload && needToReload > 0) {
          weaponTimer -= needToReload
          w.ammo += needToReload
        }
        reloadingMap[w] = weaponTimer
        w.ammoTracker.reloadProgress

      }
    }

  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String? {
    if (index == 0) return "" + String.format("+%.0f",BASE_BONUS ) + "%"
    return null
  }

  override fun getSModDescriptionParam(index: Int, hullSize: HullSize): String? {
    if (index == 0) return "" +  String.format("+%.0f",(BASE_BONUS+ SMOD_BONUS))+ "%"
    return null
  }
}