package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import combat.util.aEP_DataTool
import combat.util.aEP_Tool
import data.scripts.weapons.aEP_DecoAnimation
import data.scripts.weapons.aEP_fga_xiliu_main
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import java.awt.Color
import java.util.*

class aEP_SwitchPowerMode: BaseShipSystemScript(), WeaponRangeModifier {

  companion object{
    const val ID = "aEP_SwitchPowerMode"

    var DAMAGE_TO_FIGHTER_PERCENT_BONUS = 0f

    val WEAPON_RANGE_REDUCE_THRESHOLD = 450f
    var WEAPON_RANGE_REDUCE_MULT = 0.65f

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
    switchMainWeapon()
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
    }

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    //复制粘贴这行
    ship = (stats.entity?: return) as ShipAPI
    val ship = ship as ShipAPI
    switchMainWeapon()
    updateIndicators()
    //把当前装填塞入customData给ai用
    ship.setCustomData(ID, mode)
  }

  //在这里只有玩家控制时是每帧运行
  override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI?): Boolean {
    return super.isUsable(system, ship)
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): ShipSystemStatsScript.StatusData? {

    return null
  }

  override fun getInfoText(system: ShipSystemAPI?, ship: ShipAPI?): String {
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

  fun switchMainWeapon(){
    ship?:return
    if(main1 == null && main2 == null){
      for(w in ship!!.allWeapons){
        if(w.spec.weaponId.equals("aEP_fga_xiliu_main")) main1 = w
        if(w.spec.weaponId.equals("aEP_fga_xiliu_main2")) main2 = w
      }
    }
    val main1 = main1 as WeaponAPI
    val main2 = main2 as WeaponAPI

    if(mode == 0){
      //启动main1，关闭main2
      main1.animation.frame = 0
      main1.isKeepBeamTargetWhileChargingDown = true
//      if(main1.isPermanentlyDisabled){
//        main1.repair()
//      }
      main2.animation.frame = 1
      main2.setForceNoFireOneFrame(true)
      main2.currAngle = main1.currAngle
    }

    if(mode == 1){
      //启动main2，关闭main1
      main2.animation.frame = 0
      main2.isKeepBeamTargetWhileChargingDown = true
//      if(main2.isPermanentlyDisabled){
//        main2.repair()
//      }
      main1.animation.frame = 1
      main1.setForceNoFireOneFrame(true)
      main1.currAngle = main2.currAngle
      //main1.disable(true)
    }

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


