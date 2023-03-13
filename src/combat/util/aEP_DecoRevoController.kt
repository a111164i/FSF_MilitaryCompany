package combat.util

import com.fs.starfarer.api.combat.WeaponAPI
import java.util.HashMap
import data.scripts.util.MagicAnim
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.FastTrig

class aEP_DecoRevoController(var weapon: WeaponAPI) {
  companion object {
    //start,range,speed(by percent)
    val mag: MutableMap<String, Array<Float>> = HashMap()

    init {
      //+ = turn left, - = turn right
      mag["aEP_duiliu_armor_L3"] = arrayOf(0f, 70f, 0.5f)
      mag["aEP_duiliu_armor_R3"] = arrayOf(0f, -70f, 0.5f)
      mag["aEP_guardian_drone_cover_l"] = arrayOf(-5f, 75f, 4f)
      mag["aEP_guardian_drone_cover_r"] = arrayOf(5f, -75f, 4f)
    }
  }

  var effectiveLevel = 0f
  var toLevel = 0f
  var start = 0f
  var range = 0f
  var speed = 1f
  fun advance(amount: Float) {
    if (!mag.containsKey(weapon.spec.weaponId)) return
    val ship = weapon.ship
    val toMove: Float = if (effectiveLevel > toLevel) {
      -Math.min(effectiveLevel - toLevel, speed * amount)
    } else {
      Math.min(toLevel - effectiveLevel, speed * amount)
    }
    effectiveLevel += toMove
    weapon.currAngle = ship.facing + start + range * MagicAnim.smooth(effectiveLevel)
  }


  fun setToLevelSyncShipSpeed() {
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