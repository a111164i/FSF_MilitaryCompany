package data.scripts.shipsystems

import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import combat.util.aEP_DataTool
import data.scripts.shipsystems.aEP_DamperBoost.Companion.LARGE_FOLD_ARMOR
import data.scripts.shipsystems.aEP_DamperBoost.Companion.LARGE_FOLD_BELOW
import java.awt.Color

class aEP_DamperTanker : BaseShipSystemScript() {

  companion object {
    private const val EFFECT_ARMOR_FLAT_BONUS = 800f
    private const val EFFECT_ARMOR_PERCENT_BONUS = 0.1f
    private const val ARMOR_DAMAGE_REDUCE = 0.5f //by mult
    private const val HULL_DAMAGE_REDUCE = 0.5f
  }

  private var ship: ShipAPI? = null
  private val id = "aEP_DamperTanker"
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    ship = (stats?.entity?: return)as ShipAPI
    val ship = stats.entity as ShipAPI


    var convertedLevel = effectLevel
    if (state == ShipSystemStatsScript.State.ACTIVE) convertedLevel = 1f
    //折叠侧面的的装甲
    for (w in ship.allWeapons) {
      if(!w.isDecorative) continue
      if (!w.slot.id.startsWith("TOP_LV") && !w.slot.id.startsWith("RL_DECO")) continue
      val anima = w.effectPlugin as aEP_DecoAnimation

      //控制侧面甲同时展开
      if (convertedLevel < 0.5f) {
        val level = MathUtils.clamp(convertedLevel * 2f, 0f, 1f)
        if (w.spec.weaponId == LARGE_FOLD_ARMOR) {
          w.sprite.color = Color(0, 0, 0, 0)
          anima.setMoveToLevel(level)
        }
        if (w.spec.weaponId == LARGE_FOLD_BELOW) {
          w.sprite.color = Color(255, 255, 255)
          anima.setMoveToLevel(level)
        }
      } else {
        val level = MathUtils.clamp(2f - convertedLevel * 2f, 0f, 1f)
        if (w.spec.weaponId == LARGE_FOLD_ARMOR) {
          val black = (255 * effectLevel).toInt()
          w.sprite.color = Color(black, black, black)
          anima.setMoveToLevel(level)
        }
        if (w.spec.weaponId == LARGE_FOLD_BELOW) {
          anima.setMoveToLevel(level)
        }
      }

      //折叠顶部上层甲
      if (w.slot.id.startsWith("TOP_LV01")) {
        controlLv01(w, convertedLevel, state, anima)
      }
      //折叠顶部下层甲
      if (w.slot.id.startsWith("TOP_LV02")) {
        controlLv02(w, convertedLevel, state, anima)
      }
    }

    //modify here
    val toAdd = EFFECT_ARMOR_FLAT_BONUS + (ship?.hullSpec?.armorRating?:100f) * EFFECT_ARMOR_PERCENT_BONUS
    stats.effectiveArmorBonus.modifyFlat(id, toAdd * effectLevel)
    stats.armorDamageTakenMult.modifyMult(id, (1f- ARMOR_DAMAGE_REDUCE * effectLevel))
    stats.hullDamageTakenMult.modifyMult(id, (1f- HULL_DAMAGE_REDUCE * effectLevel))
    stats.weaponDamageTakenMult.modifyMult(id, (1f- ARMOR_DAMAGE_REDUCE * effectLevel))
    stats.engineDamageTakenMult.modifyMult(id, (1f- ARMOR_DAMAGE_REDUCE * effectLevel))
    //ship.getExactBounds().getSegments()
  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    ship = (stats?.entity?: return)as ShipAPI
    for (w in ship?.allWeapons?:ArrayList()) {
      if (!w.slot.id.startsWith("TOP_LV") && !w.slot.id.startsWith("RL_DECO")) continue
      val anima = w.effectPlugin as aEP_DecoAnimation
      if (w.spec.weaponId == LARGE_FOLD_ARMOR) {
        w.sprite.color = Color(0, 0, 0, 0)
        anima.setMoveToLevel(0f)
      }
      if ( w.spec.weaponId == LARGE_FOLD_BELOW) {
        w.sprite.color = Color(0, 0, 0, 0)
        anima.setMoveToLevel(0f)
      }

      //以防万一被打断，强制收进去
      if (w.slot.id.startsWith("TOP_LV01")) {
        anima.setMoveToLevel(0f)
      }
      if (w.slot.id.startsWith("TOP_LV02")) {
        anima.setMoveToLevel(0f)
      }

    }

    //modify here
    stats.effectiveArmorBonus.unmodify(id)
    stats.armorDamageTakenMult.unmodify(id)
    stats.hullDamageTakenMult.unmodify(id)
    stats.weaponDamageTakenMult.unmodify(id)
    stats.engineDamageTakenMult.unmodify(id)
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): StatusData? {
    if (index == 0) {
      val armorFlat = EFFECT_ARMOR_FLAT_BONUS
      val armorPercent = EFFECT_ARMOR_PERCENT_BONUS
      val toAdd = armorFlat + (ship?.hullSpec?.armorRating?:0f) * armorPercent
      return StatusData(aEP_DataTool.txt("aEP_LADamper01") + (toAdd * effectLevel).toInt(), false)
    }
    return null
  }

  //控制上层装甲全程
  fun controlLv01(w : WeaponAPI, effectLevel: Float, state: ShipSystemStatsScript.State, anima:aEP_DecoAnimation){
    //保证在一层和二层之间顿一下，所以设置3倍增速，同时留给延迟展开的板子时间
    val delay = 0.2f
    var useLevel = 0f
    if(effectLevel <= 0.5f) useLevel = effectLevel * 4f
    else useLevel = (effectLevel-0.5f) * 4f + 2f
    //一共12个板子，越大越靠船头
    val num = w.slot.id.replace("TOP_LV01_","").toInt()
    //反一下，越靠船头越先展开
    useLevel -= (12-num)*delay
    useLevel = MathUtils.clamp(useLevel,0f,1f)

    anima.setMoveToLevel(useLevel)
  }

  //控制下层装甲
  fun controlLv02(w : WeaponAPI, effectLevel: Float, state: ShipSystemStatsScript.State, anima:aEP_DecoAnimation){
    val delay = 0.2f
    val num = w.slot.id.replace("TOP_LV02_","").toInt()
    var useLevel = 0f
    if(effectLevel <= 0.5f) useLevel = effectLevel * 4f
    else useLevel = (effectLevel-0.5f) * 4f + 2f
    useLevel -= ((12-num)*delay)
    //因为下板运动距离是2倍，要同速度和上板子运动，展开距离就得变成二分之一
    useLevel = MathUtils.clamp(useLevel*0.5f,0f,1f)

    anima.setMoveToLevel(useLevel)
    return
  }


}