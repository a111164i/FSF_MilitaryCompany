package combat.util

import com.fs.starfarer.api.combat.WeaponAPI
import java.util.HashMap
import combat.util.aEP_DecoMoveController
import data.scripts.util.MagicAnim
import org.lwjgl.util.vector.Vector2f
import org.lazywizard.lazylib.FastTrig

class aEP_DecoMoveController(var weapon: WeaponAPI) {
  companion object {

    val mag: MutableMap<String, Array<Float>> = HashMap()

    init {
      //移动距离，移动时间(每秒移动多少effectiveLevel)，侧向移动距离，侧向移动速度
      mag["aEP_fga_yonglang_main"] = arrayOf(12f, 3f,  0f,0f)
      mag["aEP_fga_yonglang_scaffold"] = arrayOf(4f, 3f,  0f,0f)
      mag["aEP_fga_yonglang_glow"] = arrayOf(12f, 3f,  0f,0f)
      mag["aEP_fga_yonglang_cover"] = arrayOf(-3f, 3f,  0f,0f)
      mag["aEP_cap_duiliu_armor_l"] = arrayOf(20f, 0.5f,  0f,0f)
      mag["aEP_cap_duiliu_armor_r"] = arrayOf(20f, 0.5f,  0f,0f)
      mag["aEP_cap_duiliu_armor_l3"] = arrayOf(5f, 0.5f, -10f, 0.5f)
      mag["aEP_cap_duiliu_armor_r3"] = arrayOf(5f, 0.5f, 10f, 0.5f)
      mag["aEP_cap_duiliu_limiter"] = arrayOf(18f, 1f,  0f,0f)
      mag["aEP_cap_duiliu_limiter_glow"] = arrayOf(18f, 1f,  0f,0f)
      mag["aEP_cap_duiliu_gun_cover"] = arrayOf(-30f, 0.5f,  0f,0f)
      mag["aEP_cru_hailiang_holder_l"] = arrayOf(6f, 1.33f,  -6f, 1.33f)
      mag["aEP_cru_hailiang_holder_r"] = arrayOf(6f, 1.33f,  6f, 1.33f)
      mag["aEP_cap_shangshengliu_armor"] = arrayOf(8f, 1.3f,  0f,0f)
      mag["aEP_cap_shangshengliu_armor_dark"] = arrayOf(8f, 1.3f,  0f,0f)
      mag["aEP_cap_shangshengliu_hull"] = arrayOf(0f, 2f,  0f,0f)
      mag["aEP_cap_shangshengliu_top"] = arrayOf(9f, 2f,  0f,0f)
      mag["aEP_cap_shangshengliu_bottom"] = arrayOf(21f, 1f,  0f,0f)
      mag["aEP_des_shendu_armor"] = arrayOf(-42f, 10f, 2f,10f)
      mag["aEP_des_shendu_hold"] = arrayOf(20f, 10f, 2f,10f)
      mag["aEP_des_shendu_hold2"] = arrayOf(28f, 10f, 2f,10f)
      mag["aEP_des_shendu_fighter"] = arrayOf(-34f, 10f, 2f,10f)
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
    val slotRevLocation = weapon.ship.hullSpec.getWeaponSlotAPI(weapon.slot.id).location
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