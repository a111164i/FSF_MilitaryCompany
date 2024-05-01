package combat.util

import com.fs.starfarer.api.combat.WeaponAPI
import java.util.HashMap
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.FastTrig
import org.magiclib.util.MagicAnim.smooth

class aEP_DecoRevoController(var weapon: WeaponAPI) {
  companion object {
    //起始角度,旋转角度,旋转速度(每秒转百分之几)
    //在之前，确认武器槽位的角度不是0，武器怎么转都转不出槽位的限制
    val mag: MutableMap<String, Array<Float>> = HashMap()

    init {
      //+ = turn left, - = turn right
      mag["aEP_cap_neibo_fin_l"] = arrayOf(0f, 30f, 20f)
      mag["aEP_cap_neibo_fin_r"] = arrayOf(0f, -30f, 20f)

      mag["aEP_cap_duiliu_armor_l1"] = arrayOf(0f, 0f, 1f)
      mag["aEP_cap_duiliu_armor_r1"] = arrayOf(0f, 0f, 1f)
      mag["aEP_cap_duiliu_armor_l2"] = arrayOf(0f, 90f, 4f)
      mag["aEP_cap_duiliu_armor_r2"] = arrayOf(0f, -90f, 4f)
      mag["aEP_cap_duiliu_armor_l3"] = arrayOf(0f, 60f, 4f)
      mag["aEP_cap_duiliu_armor_r3"] = arrayOf(0f, -60f, 4f)

      mag["aEP_cru_pingding_armor2"] = arrayOf(0f, 50f,  0.75f)

      mag["aEP_cru_shanhu_round_red"] = arrayOf(0f, -1440f,  0.32f)
      mag["aEP_cru_shanhu_round_green"] = arrayOf(0f, -1440f,  0.32f)
      mag["aEP_cru_shanhu_rotator"] = arrayOf(0f, -1440f,  0.32f)

      mag["aEP_des_shuishi_rotator"] = arrayOf(0f, -45f,  1f)
      mag["aEP_des_shuishi_rail1"] = arrayOf(0f, -45f,  1f)
      mag["aEP_des_shuishi_rail2"] = arrayOf(0f, -45f,  1f)
      mag["aEP_des_shuishi_rail_red"] = arrayOf(0f, -45f,  1f)
      mag["aEP_des_shuishi_rail_green"] = arrayOf(0f, -45f,  1f)

      mag["aEP_des_chongji_armor_tr"] = arrayOf(0f, -35f,  1f)
      mag["aEP_des_chongji_armor_tl"] = arrayOf(0f, 35f,  1f)
      mag["aEP_des_chongji_armor_tr2"] = arrayOf(0f, -35f,  1f)
      mag["aEP_des_chongji_armor_tl2"] = arrayOf(0f, 35f,  1f)

      mag["aEP_des_chongji_armor_br"] = arrayOf(0f, 35f,  1f)
      mag["aEP_des_chongji_armor_bl"] = arrayOf(0f, -35f,  1f)
      mag["aEP_des_chongji_armor_br2"] = arrayOf(0f, 35f,  1f)
      mag["aEP_des_chongji_armor_bl2"] = arrayOf(0f, -35f,  1f)
      mag["aEP_des_chongji_armor_br3"] = arrayOf(0f, 35f,  1f)
      mag["aEP_des_chongji_armor_bl3"] = arrayOf(0f, -35f,  1f)
      mag["aEP_des_chongji_glow_l"] = arrayOf(0f, -35f,  1f)
      mag["aEP_des_chongji_glow_r"] = arrayOf(0f, 35f,  1f)

      mag["aEP_fga_xiliu_rotator"] = arrayOf(0f, 90f,  2f)
      mag["aEP_fga_xiliu_red"] = arrayOf(0f, 90f, 2f)
      mag["aEP_fga_xiliu_green"] = arrayOf(0f, 90f,  2f)

      mag["aEP_ftr_ut_shuangshen3_wing_l"] = arrayOf(0f, -30f, 3f)
      mag["aEP_ftr_ut_shuangshen3_wing_r"] = arrayOf(0f, 30f,  3f)
      mag["aEP_ftr_ut_shuangshen3_empennage_l"] = arrayOf(0f, -10f, 3f)
      mag["aEP_ftr_ut_shuangshen3_empennage_r"] = arrayOf(0f, 10f,  3f)

      mag["aEP_ftr_sup_guardian_cover_l"] = arrayOf(-5f, 75f, 4f)
      mag["aEP_ftr_sup_guardian_cover_r"] = arrayOf(5f, -75f, 4f)
    }
  }

  var effectiveLevel = 0f
  var toLevel = 0f
  var start = 0f
  var range = 0f
  var speed = 1f
  fun advance(amount: Float) {

    if (weapon.ship == null) return
    val ship = weapon.ship
    val toMove: Float = if (effectiveLevel > toLevel) {
      -Math.min(effectiveLevel - toLevel, speed * amount)
    } else {
      Math.min(toLevel - effectiveLevel, speed * amount)
    }


    effectiveLevel += toMove
    weapon.currAngle = ship.facing + start + range * smooth(effectiveLevel)
  }


  fun setToLevelSyncShipSpeed() {
    if (weapon.ship == null) return
    val ship = weapon.ship
    val angleAndSpeed = aEP_Tool.velocity2Speed(ship.velocity)
    var angleDist = Math.abs(MathUtils.getShortestRotation(ship.facing, angleAndSpeed.x))
    angleDist = MathUtils.clamp(angleDist * 1.5f, 0f, 180f)
    toLevel = FastTrig.cos(Math.toRadians(angleDist.toDouble())).toFloat() * angleAndSpeed.y / (ship.mutableStats.maxSpeed.modifiedValue * 0.75f)
    toLevel = MathUtils.clamp(toLevel, 0f, 1f)
    toLevel = 1f - toLevel
  }

  init {
    if (mag.containsKey(weapon.spec.weaponId)){
      start = mag[weapon.spec.weaponId]!![0]
      range = mag[weapon.spec.weaponId]!![1]
      speed = mag[weapon.spec.weaponId]!![2]
    }
  }
}