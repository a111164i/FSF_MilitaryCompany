package combat.util

import com.fs.starfarer.api.combat.WeaponAPI
import java.util.HashMap
import combat.util.aEP_DecoMoveController
import org.lwjgl.util.vector.Vector2f
import org.lazywizard.lazylib.FastTrig
import org.magiclib.util.MagicAnim

class aEP_DecoMoveController(var weapon: WeaponAPI) {
  companion object {

    val mag: MutableMap<String, Array<Float>> = HashMap()

    init {
      //移动距离(正为向前)，移动时间(每秒移动多少effectiveLevel)，侧向移动距离(正为向右)，侧向移动速度
      mag["aEP_cap_nuanchi_ring"] = arrayOf(8f, 0.5f,  0f,1f)

      mag["aEP_cap_duiliu_armor_l1"] = arrayOf(20f, 4f, -4f,10f)
      mag["aEP_cap_duiliu_armor_r1"] = arrayOf(20f, 4f, 4f,10f)
      mag["aEP_cap_duiliu_armor_l2"] = arrayOf(-40f, 2f,  0f,1f)
      mag["aEP_cap_duiliu_armor_r2"] = arrayOf(-40f, 2f,  0f,1f)
      mag["aEP_cap_duiliu_armor_l3"] = arrayOf(0f, 0f, -2f, 2f)
      mag["aEP_cap_duiliu_armor_r3"] = arrayOf(0f, 0f, 2f, 2f)
      mag["aEP_cap_duiliu_limiter1"] = arrayOf(0f, 0f,  0f,0f)
      mag["aEP_cap_duiliu_limiter2"] = arrayOf(10f, 1f, 0f,0f)
      mag["aEP_cap_duiliu_limiter3"] = arrayOf(18f, 1f, 0f,0f)
      mag["aEP_cap_duiliu_gun_cover"] = arrayOf(-30f, 0.5f,  0f,0f)

      mag["aEP_cap_shangshengliu_armor"] = arrayOf(8f, 1.3f,  0f,0f)
      mag["aEP_cap_shangshengliu_armor_dark"] = arrayOf(8f, 1.3f,  0f,0f)
      mag["aEP_cap_shangshengliu_hull"] = arrayOf(0f, 2f,  0f,0f)

      mag["aEP_cap_shangshengliu_top"] = arrayOf(8f, 2f,  0f,0f)
      mag["aEP_cap_shangshengliu_bottom"] = arrayOf(20f, 1f,  0f,0f)

      mag["aEP_cru_hailiang_holder_l"] = arrayOf(6f, 1.33f,  -6f, 1.33f)
      mag["aEP_cru_hailiang_holder_r"] = arrayOf(6f, 1.33f,  6f, 1.33f)

      mag["aEP_cru_pingding_barrel_sleeve_r1"] = arrayOf(-10f, 2f, 3f,2f)
      mag["aEP_cru_pingding_barrel_sleeve_l1"] = arrayOf(-10f, 2f, -3f,2f)
      mag["aEP_cru_pingding_barrel_sleeve_r2"] = arrayOf(-40f, 2f, 6f,2f)
      mag["aEP_cru_pingding_barrel_sleeve_l2"] = arrayOf(-40f, 2f, -6f,2f)

      mag["aEP_cru_pingding_armor_r1"] = arrayOf(-30f, 2f, 8f,2f)
      mag["aEP_cru_pingding_armor_l1"] = arrayOf(-30f, 2f, -8f,2f)
      mag["aEP_cru_pingding_armor_r2"] = arrayOf(-15f, 2f, 2f,2f)
      mag["aEP_cru_pingding_armor_l2"] = arrayOf(-15f, 2f, -2f,2f)

      mag["aEP_cru_pingding_tail_r1"] = arrayOf(-3f, 2f, 3f,2f)
      mag["aEP_cru_pingding_tail_l1"] = arrayOf(-3f, 2f, -3f,2f)
      mag["aEP_cru_pingding_tail_r2"] = arrayOf(10f, 2f, 5f,2f)
      mag["aEP_cru_pingding_tail_l2"] = arrayOf(10f, 2f, -5f,2f)

      mag["aEP_cru_zhongliu_head_r"] = arrayOf(0f, 1f,  6f, 1.33f)
      mag["aEP_cru_zhongliu_head_l"] = arrayOf(0f, 1f,  -6f, 1.33f)
      mag["aEP_cru_zhongliu_slide_r"] = arrayOf(18f, 0.75f,  -4f, 2f)
      mag["aEP_cru_zhongliu_slide_l"] = arrayOf(18f, 1.25f,  4f, 2f)

      mag["aEP_cru_baojiao_piston"] = arrayOf(-22f, 0.5f,  0f, 0f)

      mag["aEP_des_shuishi_rail1"] = arrayOf(0f, 2f, 0f,0f)
      mag["aEP_des_shuishi_rail2"] = arrayOf(14f, 2f, 0f,0f)
      mag["aEP_des_shuishi_base"] = arrayOf(11f, 1f, 0f,0f)

      mag["aEP_des_shendu_door_l"] = arrayOf(5f, 2f, -16f,2f)
      mag["aEP_des_shendu_door_r"] = arrayOf(5f, 2f, 16f,2f)

      mag["aEP_des_shendu_mk2_clip"] = arrayOf(-6f, 2f, 0f,2f)

      mag["aEP_des_lianliu_front_cover"] = arrayOf(6f, 3f, 0f,0f)

      mag["aEP_des_chongji_armor_tr2"] = arrayOf(10f, 2f, 0f,2f)
      mag["aEP_des_chongji_armor_tl2"] = arrayOf(10f, 2f, 0f,2f)

      mag["aEP_des_chongji_armor_br2"] = arrayOf(0f, 2f, 8f,2f)
      mag["aEP_des_chongji_armor_bl2"] = arrayOf(0f, 2f, -8f,2f)
      mag["aEP_des_chongji_glow_r"] = arrayOf(0f, 2f, 8f,2f)
      mag["aEP_des_chongji_glow_l"] = arrayOf(0f, 2f, -8f,2f)
      mag["aEP_des_chongji_armor_br3"] = arrayOf(-8f, 2f, 0f,2f)
      mag["aEP_des_chongji_armor_bl3"] = arrayOf(-8f, 2f, 0f,2f)

      mag["aEP_fga_wanliu_arm_l"] = arrayOf(-15f, 1f,  -4f, 1f)
      mag["aEP_fga_wanliu_arm_r"] = arrayOf(-15f, 1f,  4f, 1f)
      mag["aEP_fga_wanliu_cover"] = arrayOf(21f, 1f,  0f,0f)

      mag["aEP_fga_yonglang_main_cover_l"] = arrayOf(-6f, 3f,  0f,0f)
      mag["aEP_fga_yonglang_main_cover_r"] = arrayOf(-6f, 3f,  0f,0f)
      mag["aEP_fga_yonglang_scaffold"] = arrayOf(6f, 3f,  0f,0f)
      mag["aEP_fga_yonglang_glow"] = arrayOf(10f, 3f,  0f,0f)
      mag["aEP_fga_yonglang_main_br"] = arrayOf(10f, 3f,  0f,0f)

      mag["aEP_ftr_ut_shuangshen3_wing_l"] = arrayOf(-5f, 4f, 0f, 0f)
      mag["aEP_ftr_ut_shuangshen3_wing_r"] = arrayOf(-5f, 4f, 0f, 0f)
      mag["aEP_ftr_ut_shuangshen3_empennage_l"] = arrayOf(-18f, 4f, 0f, 0f)
      mag["aEP_ftr_ut_shuangshen3_empennage_r"] = arrayOf(-18f, 4f, 0f, 0f)

      mag["aEP_ftr_sup_guardian_cover_l"] = arrayOf(-6f, 4f, 12f,4f)
      mag["aEP_ftr_sup_guardian_cover_r"] = arrayOf(-6f, 4f, -12f,4f)
    }
  }

  var range = 0f
  var speed = 0f
  var sideRange = 0f
  var sideSpeed = 0f
  var effectiveLevel = 0f
  var effectiveSideLevel = 0f
  var toLevel = 0f
  var toSideLevel = 0f
  public var originalX = 0f
  public var originalY = 0f

  init {
    originalX = weapon.sprite.centerX
    originalY = weapon.sprite.centerY

    range = mag[weapon.spec.weaponId]?.get(0)?: 0f
    speed = mag[weapon.spec.weaponId]?.get(1)?: 1f
    sideRange = mag[weapon.spec.weaponId]?.get(2)?: 0f
    sideSpeed = mag[weapon.spec.weaponId]?.get(3)?: 1f

  }

  fun advance(amount: Float) {
    if (weapon.ship == null) return

    var toMove = 0f
    toMove = if (effectiveLevel > toLevel) {
      -Math.min(effectiveLevel - toLevel, speed * amount)
    } else {
      Math.min(toLevel - effectiveLevel, speed * amount)
    }
    effectiveLevel += toMove


    var toSideMove = 0f
    toSideMove = if (effectiveSideLevel > toSideLevel) {
      -Math.min(effectiveSideLevel - toSideLevel, sideSpeed * amount)
    } else {
      Math.min(toSideLevel - effectiveSideLevel, sideSpeed * amount)
    }
    effectiveSideLevel += toSideMove

    val spriteCenter = computeSpriteCenter()
    weapon.sprite.setCenter(spriteCenter.x, spriteCenter.y)
  }

  fun computeSpriteCenter():Vector2f{
    val angle = weapon.spec.turretAngleOffsets[0]
    val sideAngle = aEP_Tool.angleAdd(angle,90f)
    val forwardShift = Vector2f(FastTrig.sin(Math.toRadians(angle.toDouble())).toFloat() * range,FastTrig.cos(Math.toRadians(angle.toDouble())).toFloat()*range)
    forwardShift.scale(MagicAnim.smooth(effectiveLevel))
    val sideShift = Vector2f(FastTrig.sin(Math.toRadians(sideAngle.toDouble())).toFloat() * sideRange,FastTrig.cos(Math.toRadians(sideAngle.toDouble())).toFloat()*sideRange)
    sideShift.scale(MagicAnim.smooth(effectiveSideLevel))
    return Vector2f(originalX -  forwardShift.x - sideShift.x
      , originalY - forwardShift.y - sideShift.y)
  }

}