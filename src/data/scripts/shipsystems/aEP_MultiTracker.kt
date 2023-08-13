package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import combat.util.aEP_DataTool
import combat.util.aEP_Tool
import data.scripts.weapons.aEP_fga_xiliu_lidar
import data.scripts.weapons.aEP_m_s_era
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*
import kotlin.math.absoluteValue

class aEP_MultiTracker: BaseShipSystemScript(), WeaponRangeModifier {

  companion object{
    const val ID = "aEP_MultiTracker"
    var PROJECTILE_SPEED_BONUS = 50f
    var SPREAD_REDUCE_MULT = 0.6667f
    var WEAPON_TURNRATE_PERCENT_BONUS = 100f

    var WEAPON_RANGE_REDUCE_MULT = 0.5f

    var DAMAGE_TO_FIGHTER_PERCENT_BONUS = 0f
  }
  private var ship: ShipAPI? = null
  var level = 0f
  val pairs = ArrayList<LidarPair>()
  var initialed = false

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    ship = (stats.entity?: return) as ShipAPI
    val amount = aEP_Tool.getAmount(ship)
    val ship = ship as ShipAPI
    level = effectLevel

    //初始化雷达-武器的pair数据
    if(!initialed){
      initialed = true
      findLidarPair(ship)
    }

    stats.ballisticProjectileSpeedMult.modifyPercent(ID,PROJECTILE_SPEED_BONUS)
    stats.energyProjectileSpeedMult.modifyPercent(ID,PROJECTILE_SPEED_BONUS)

    stats.recoilPerShotMult.modifyMult(ID,1f - SPREAD_REDUCE_MULT)
    stats.maxRecoilMult.modifyMult(ID,1f - SPREAD_REDUCE_MULT)

    stats.weaponTurnRateBonus.modifyPercent(ID, WEAPON_TURNRATE_PERCENT_BONUS)

    stats.damageToFrigates.modifyPercent(ID, DAMAGE_TO_FIGHTER_PERCENT_BONUS)
    stats.damageToMissiles.modifyPercent(ID, DAMAGE_TO_FIGHTER_PERCENT_BONUS)


    stats.dynamic.getMod(Stats.PD_IGNORES_FLARES).modifyFlat(ID, 1f)
    stats.dynamic.getMod(Stats.PD_BEST_TARGET_LEADING).modifyFlat(ID, 1f)

    stats.autofireAimAccuracy.modifyFlat(ID,1f)

    //给武器打光
    for(weapon in ship.allWeapons){
      if(weapon.spec.weaponId.equals(aEP_fga_xiliu_lidar.WEAPON_ID)){
        weapon.setGlowAmount(effectLevel, Color(125,162,254,248))
      }
    }
    ship.setWeaponGlow(
      effectLevel,
      Color(255,72,44,248),
      EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.ENERGY))


    for(pair in pairs){

      if(pair.target == null){
        pair.timeElapsedSinceSearch += amount
        pair.timeElapsedSinceTargeting = 0f
        aEP_Tool.aimToAngle(pair.lidar, ship.facing)
        if(pair.timeElapsedSinceSearch > 0.1f){
          pair.timeElapsedSinceSearch = 0f
          findTarget(pair)
        }
      }

      if(pair.target != null){
        pair.timeElapsedSinceSearch = 0f
        pair.timeElapsedSinceTargeting += amount
        val target = pair.target as CombatEntityAPI

        if(isTargetInvalidated(pair)){
          pair.target = null
          return
        }

        val facing2target = VectorUtils.getAngle(pair.lidar.location, target.location)
        aEP_Tool.aimToAngle(pair.lidar, facing2target)
        if(MathUtils.getShortestRotation(pair.lidar.currAngle, facing2target).absoluteValue < 1f){
          pair.lidar.setForceFireOneFrame(true)
        }
      }
    }

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    //复制粘贴这行
    ship = (stats.entity?: return) as ShipAPI
    val ship = ship as ShipAPI
    level = 0f
    stats.ballisticProjectileSpeedMult.unmodify(ID)
    stats.energyProjectileSpeedMult.unmodify(ID)

    stats.recoilPerShotMult.unmodify(ID)
    stats.maxRecoilMult.unmodify(ID)

    stats.weaponTurnRateBonus.unmodify(ID)

    stats.damageToFrigates.unmodify(ID)
    stats.damageToMissiles.unmodify(ID)

    stats.autofireAimAccuracy.unmodify(ID)

    stats.dynamic.getMod(Stats.PD_IGNORES_FLARES).unmodify(ID)
    stats.dynamic.getMod(Stats.PD_BEST_TARGET_LEADING).unmodify(ID)

    //给武器关闭打光
    ship.setWeaponGlow(
      0f,
      Color.red,
      EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.ENERGY))

    if(!ship.hasListenerOfClass(this.javaClass)){
      ship.addListener(this)
    }

  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): ShipSystemStatsScript.StatusData? {

    if (index == 0) {
      return ShipSystemStatsScript.StatusData(String.format(
        aEP_DataTool.txt("aEP_MultiTracker01"),
        String.format("%.0f",  PROJECTILE_SPEED_BONUS) + "%"),
        false)
    } else if (index == 1) {
      return ShipSystemStatsScript.StatusData(String.format(
        aEP_DataTool.txt("aEP_MultiTracker02") ,
        String.format("%.0f", 100f/(1f - SPREAD_REDUCE_MULT) - 100f) + "%"),
        false)
    } else if (index == 2) {
      return ShipSystemStatsScript.StatusData(String.format(
        aEP_DataTool.txt("aEP_MultiTracker03") ,
        String.format("%.0f", WEAPON_TURNRATE_PERCENT_BONUS) + "%"),
        false)
    } else if (index == 3) {
      return ShipSystemStatsScript.StatusData(String.format(
        aEP_DataTool.txt("aEP_MultiTracker06") ,
        String.format("%.0f", WEAPON_RANGE_REDUCE_MULT * 100f) + "%"),
        true)
    }

    return null
  }

  override fun getWeaponRangePercentMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    return 0f
  }

  override fun getWeaponRangeMultMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    if(!weapon.hasAIHint(WeaponAPI.AIHints.PD)){
      return 1f - WEAPON_RANGE_REDUCE_MULT * level
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
      if(!lidar.spec.weaponId.equals(aEP_fga_xiliu_lidar.WEAPON_ID)) continue
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
}


