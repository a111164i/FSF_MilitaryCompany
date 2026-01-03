package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import data.scripts.utils.aEP_DataTool.txt
import data.scripts.utils.aEP_Tool
import data.scripts.weapons.aEP_DecoAnimation
import data.scripts.weapons.aEP_fga_xiliu_main
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import java.awt.Color
import java.util.*

class aEP_SwitchPowerMode: BaseShipSystemScript(), WeaponRangeModifier {

  companion object{
    const val ID = "aEP_SwitchPowerMode"

    // mode 0
    const val ENERGY_WEAPON_DAMAGE_REDUCE_MULT = 0.25f
    const val ENERGY_WEAPON_FLUX_REDUCE_MULT = 0.5f
    const val ENERGY_WEAPON_ROF_BONUS = 100f

    // mode 1
    const val ENERGY_WEAPON_DAMAGE_BONUS = 50f
    const val ENERGY_WEAPON_RANGE_BONUS = 200f
    const val ENERGY_WEAPON_ROF_REDUCE_MULT = 0.5f
  }
  private var ship: ShipAPI? = null
  var mode = 0
  val pairs = ArrayList<LidarPair>()
  var initialed = false
  var main1: WeaponAPI? = null
  var main2: WeaponAPI? = null

  var red: aEP_DecoAnimation? = null
  var green: aEP_DecoAnimation? = null
  var rotator: aEP_DecoAnimation? = null

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    ship = (stats.entity?: return) as ShipAPI
    val amount = aEP_Tool.getAmount(ship)
    val ship = ship as ShipAPI

    updateIndicators()
    //技能不激活就不往下走了
    if(state == ShipSystemStatsScript.State.IDLE || state == ShipSystemStatsScript.State.COOLDOWN){
      return
    }

    var c = Color.red
    //由0（绿色）切换到1（红色）
    if(mode == 0) c =Color.green
    if(mode == 1) c =Color.red
    ship.isJitterShields = false
    ship.setJitterUnder(
      ID, c,
      effectLevel, 12, 2f, 2f)


    //运行一次
    if(effectLevel == 1f){
      mode += 1
      if(mode > 1) mode = 0
      //把当前装填塞入customData给ai用
      ship.setCustomData(ID, mode)

      // 重置所有状态修改
      ship.mutableStats.energyWeaponDamageMult.unmodify(ID )
      ship.mutableStats.energyWeaponFluxCostMod.unmodify(ID )
      ship.mutableStats.energyWeaponRangeBonus.unmodify(ID)
      ship.mutableStats.energyRoFMult.unmodify(ID)
      ship.mutableStats.energyWeaponDamageMult.unmodify(ID )
      ship.mutableStats.energyWeaponFluxCostMod.unmodify(ID )
      ship.mutableStats.energyRoFMult.unmodify(ID)


      if(mode == 0){
        ship.mutableStats.energyWeaponDamageMult.modifyMult(ID,1f-ENERGY_WEAPON_DAMAGE_REDUCE_MULT )
        ship.mutableStats.energyWeaponFluxCostMod.modifyMult(ID,1f-ENERGY_WEAPON_FLUX_REDUCE_MULT )
        ship.mutableStats.energyRoFMult.modifyPercent(ID,ENERGY_WEAPON_ROF_BONUS)

      }else if (mode == 1){
        ship.mutableStats.energyWeaponDamageMult.modifyPercent(ID,ENERGY_WEAPON_DAMAGE_BONUS )
        ship.mutableStats.energyWeaponFluxCostMod.modifyPercent(ID,ENERGY_WEAPON_DAMAGE_BONUS )
        ship.mutableStats.energyWeaponRangeBonus.modifyFlat(ID,ENERGY_WEAPON_RANGE_BONUS)
        ship.mutableStats.energyRoFMult.modifyMult(ID,1f-ENERGY_WEAPON_ROF_REDUCE_MULT)

      }
    }

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    //复制粘贴这行
    ship = (stats.entity?: return) as ShipAPI
    val ship = ship as ShipAPI
    updateIndicators()
    //把当前装填塞入customData给ai用
    ship.setCustomData(ID, mode)
  }

  //在这里只有玩家控制时是每帧运行
  override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI?): Boolean {
    return super.isUsable(system, ship)
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): ShipSystemStatsScript.StatusData? {
    var debuff = false
    if (index == 0) {
      debuff = if(mode == 0) true else false
      val num = if(mode == 0) "-" + (100f*ENERGY_WEAPON_DAMAGE_REDUCE_MULT).toInt() + "%" else "+" + ENERGY_WEAPON_DAMAGE_BONUS.toInt() + "%"
      return StatusData(txt("aEP_SwitchPowerMode03") + num, debuff)

    } else if (index == 1) {
      debuff = if(mode == 0) false else true
      val num = if(mode == 0) "-" + (100f*ENERGY_WEAPON_FLUX_REDUCE_MULT).toInt() + "%" else "+" + ENERGY_WEAPON_DAMAGE_BONUS.toInt() + "%"
      return StatusData(txt("aEP_SwitchPowerMode04") + num, debuff)

    } else if (index == 2) {
      debuff = if(mode == 0) false else true
      val num = if(mode == 0) "+" + ENERGY_WEAPON_ROF_BONUS.toInt() + "%" else "-" + (100f*ENERGY_WEAPON_ROF_REDUCE_MULT).toInt() + "%"
      return StatusData(txt("aEP_SwitchPowerMode05") +num, debuff)
    }
    return null
  }

  override fun getInfoText(system: ShipSystemAPI?, ship: ShipAPI?): String {
    if(mode == 0){
      return txt("aEP_SwitchPowerMode01")
    }else if (mode == 1){
      return txt("aEP_SwitchPowerMode02")
    }
    return "Mode: "+(mode+1)
  }

  override fun getWeaponRangePercentMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    return 0f
  }

  override fun getWeaponRangeMultMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    if(!weapon.hasAIHint(WeaponAPI.AIHints.PD)){
      if(mode == 0){
        if(weapon.spec.weaponId.equals(aEP_fga_xiliu_main.WEAPON_ID+"2")){
          return 0f
        }


      }else if (mode == 1){
        if(weapon.spec.weaponId.equals(aEP_fga_xiliu_main.WEAPON_ID)){
          return 0f
        }
      }
    }

    return 1f
  }

  override fun getWeaponRangeFlatMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    return 0f
  }

  class LidarPair(val weapon:WeaponAPI?, val lidar:WeaponAPI){
    var target: CombatEntityAPI? = null
    var timeElapsedSinceSearch = 1f
    var timeElapsedSinceTargeting = 0f
  }

  fun findLidarPair(ship: ShipAPI){

    pairs.clear()
    for(lidar in ship.allWeapons){
      if(!lidar.spec.weaponId.equals(aEP_fga_xiliu_main.WEAPON_ID)) continue
      var distNow = Float.MAX_VALUE
      var nearestWeapon : WeaponAPI? = null
      for(otherSlot in ship.hullSpec.allWeaponSlotsCopy) {
        if(!aEP_Tool.isNormalWeaponSlotType(otherSlot,false)) continue
        val dist = MathUtils.getDistance(lidar.location, otherSlot.computePosition(ship))
        val weaponInSlot = aEP_Tool.getWeaponInSlot(otherSlot.id, ship)?:continue
        if(dist <= distNow ){
          distNow = dist
          nearestWeapon = weaponInSlot
        }
      }

      if(nearestWeapon != null){
        pairs.add(LidarPair(nearestWeapon, lidar))
      }else{
        pairs.add(LidarPair(null, lidar))
      }
    }

  }

  fun findTarget(pair: LidarPair){
    for (s in Global.getCombatEngine().ships) {
      if(!aEP_Tool.isEnemy(pair.lidar.ship, s)) continue
      if(!s.isFighter) continue
      if(s.isPhased || s.collisionClass == CollisionClass.NONE) continue
      if(!s.isTargetable) continue
      if(isAlreadyTargeted(s)) continue
      if(MathUtils.getDistance(s.location, pair.lidar.location) > pair.lidar.range) continue
      pair.target = s
      return
    }

    //前面如果找不到战机，就找导弹目标
    for (m in Global.getCombatEngine().missiles) {
      if(!aEP_Tool.isEnemy(pair.lidar.ship, m)) continue
      if(m.collisionClass == CollisionClass.NONE) continue
      val isIgnoreFlare = ((pair.lidar.hasAIHint(WeaponAPI.AIHints.IGNORES_FLARES)
          || (ship?.mutableStats?.dynamic?.getMod(Stats.PD_IGNORES_FLARES)?.computeEffective(0f) ?: 0f) >= 1f) )
      if(isIgnoreFlare && m.isFlare) continue
      if(aEP_Tool.isDead(m)) continue
      if(isAlreadyTargeted(m)) continue
      if(MathUtils.getDistance(m.location, pair.lidar.location) > pair.lidar.range) continue
      pair.target = m
      return
    }
  }

  fun isAlreadyTargeted(target:CombatEntityAPI): Boolean{
    for(pair in pairs){
      if(pair.target == target) return true
    }
    return false
  }

  fun isTargetInvalidated(pair: LidarPair): Boolean{
    if(pair.target == null) return true
    if(aEP_Tool.isDead(pair.target!!)) return true
    if(MathUtils.getDistance(pair.target, pair.lidar.location) > pair.lidar.range) return true
    val facing2target = VectorUtils.getAngle(pair.lidar.location, pair.target!!.location)
    if(MathUtils.getShortestRotation(pair.lidar.slot.computeMidArcAngle(ship),facing2target) > pair.lidar.arc/2f) return true
    return false
  }


  fun updateIndicators(){
    if(red == null || green == null || rotator == null){
      for(w in ship!!.allWeapons){
        if(w.spec.weaponId.equals("aEP_fga_xiliu_red")) red = w.effectPlugin as aEP_DecoAnimation
        if(w.spec.weaponId.equals("aEP_fga_xiliu_green")) green = w.effectPlugin as aEP_DecoAnimation
        if(w.spec.weaponId.equals("aEP_fga_xiliu_rotator")) rotator = w.effectPlugin as aEP_DecoAnimation
      }
    }
    red?:return
    green?:return
    rotator?:return

    //2个灯的转动直接跟随rotator
//    red!!.decoRevoController.toLevel = rotator!!.decoRevoController.toLevel
//    red!!.decoRevoController.effectiveLevel = rotator!!.decoRevoController.effectiveLevel
//    green!!.decoRevoController.toLevel = rotator!!.decoRevoController.toLevel
//    green!!.decoRevoController.effectiveLevel = rotator!!.decoRevoController.effectiveLevel

    if(mode == 0){
      red!!.setGlowToLevel(0f)
      green!!.setGlowToLevel(1f)

      red!!.setRevoToLevel(1f)
      green!!.setRevoToLevel(1f)
      rotator!!.setRevoToLevel(1f)
    }else if (mode == 1){
      red!!.setGlowToLevel(1f)
      green!!.setGlowToLevel(0f)

      red!!.setRevoToLevel(0f)
      green!!.setRevoToLevel(0f)
      rotator!!.setRevoToLevel(0f)
    }
  }
}


