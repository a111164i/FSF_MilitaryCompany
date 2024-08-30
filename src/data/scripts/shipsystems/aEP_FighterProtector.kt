package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.api.util.IntervalUtil
import combat.impl.aEP_BaseCombatEffect
import combat.impl.aEP_BaseCombatEffectWithKey
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Combat
import combat.util.aEP_DataTool
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_DamperBoost.Companion.LARGE_FOLD_ARMOR
import data.scripts.shipsystems.aEP_DamperBoost.Companion.LARGE_FOLD_BELOW
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*
import kotlin.collections.ArrayList

class aEP_FighterProtector : BaseShipSystemScript() {

  companion object {
    private const val DAMAGE_TAKEN_REDUCE_MULT = 0.6f
    private const val DAMAGE_DEALT_REDUCE_MULT = 0.9f

    private const val FULL_TIME = 7.5f
    private const val FADE_TIME = 0.5f
    private const val DAMPER_CLASS_KEY = "aEP_FighterProtector"
    private const val ID = "aEP_FighterProtector"

    private val JITTER_COLOR_BLINK = Color(255,210,135,255)

  }

  private var ship: ShipAPI? = null

  private val checkTimer = IntervalUtil(0.05f,0.05f)

  private val blinkTimer = IntervalUtil(0.025f,0.025f)
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    ship = (stats?.entity?: return)as ShipAPI
    val ship = stats.entity as ShipAPI
    val amount = aEP_Tool.getAmount(ship)


    //modify here
    checkTimer.advance(amount)
    if(checkTimer.intervalElapsed() && effectLevel >= 0.5f){
      for(wing in ship.allWings){
        for(ftr in wing.wingMembers){
          if(!aEP_Tool.isDead(ftr)){
            //modify here
            aEP_BaseCombatEffect.addOrRefreshEffect(ftr,DAMPER_CLASS_KEY,
              {
                c -> c.time = 0f
              },
              {
                val c = DamperField(ftr, DAMAGE_TAKEN_REDUCE_MULT,DAMAGE_DEALT_REDUCE_MULT, FADE_TIME)
                c.setKeyAndPutInData(DAMPER_CLASS_KEY)
                c.lifeTime = FULL_TIME
                aEP_CombatEffectPlugin.addEffect(c)

                //加一个抖动，表示开启系统
                val jitter = aEP_Combat.AddJitterBlink(0.1f,0.2f, 0.2f,ftr)
                jitter.color = JITTER_COLOR_BLINK
                jitter.maxRange = 30f
                jitter.maxRangePercent = 0.5f
                jitter.copyNum = 9
                jitter.jitterShield = false
              })
          }
        }
      }
    }

    blinkTimer.advance(amount)
    if(blinkTimer.intervalElapsed()){
      for(slot in ship.hullSpec.allWeaponSlotsCopy){
        if(slot.isSystemSlot){
          if(MathUtils.getRandomNumberInRange(0f,1f) < 0.5f){

            val loc = slot.computePosition(ship)
            Global.getCombatEngine().addSmoothParticle(
              loc,
              Vector2f(0f, 0f),
              (25f + MathUtils.getRandomNumberInRange(0f,10f)) * effectLevel,
              0.1f + 0.2f* effectLevel,
              0.05f,
              JITTER_COLOR_BLINK)


          }

        }
      }
    }

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    ship = (stats.entity?: return)as ShipAPI

  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): StatusData? {
    if (index == 0) {

      return StatusData(aEP_DataTool.txt("aEP_FighterProtector01") + "-"+ (DAMAGE_TAKEN_REDUCE_MULT * 100f ).toInt() +"%", false)
    } else if(index == 1) {

      return StatusData(aEP_DataTool.txt("aEP_FighterProtector02") + "-"+(DAMAGE_DEALT_REDUCE_MULT * 100f ).toInt() +"%", true)
    }
    return null
  }

  //控制
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

  class DamperField(val ftr: ShipAPI, val takenReduceMult:Float, val dealtReduceMult:Float, val fadeTime:Float): aEP_BaseCombatEffectWithKey(ftr){

    companion object{
      val JITTER_COLOR_ABOVE = Color(255,165,90,45)
      val JITTER_COLOR_UNDER = Color(255,165,90,185)
    }

    override fun advanceImpl(amount: Float) {


      var level = 1f
      if(lifeTime-time < fadeTime) level = ((lifeTime-time)/fadeTime).coerceAtLeast(0f)


      ftr.mutableStats.damageToTargetHullMult.modifyMult(key, 1f-dealtReduceMult )
      ftr.mutableStats.damageToTargetShieldsMult.modifyMult(key, 1f-dealtReduceMult)

      ftr.mutableStats.armorDamageTakenMult.modifyMult(key, 1f - takenReduceMult*level)
      ftr.mutableStats.hullDamageTakenMult.modifyMult(key, 1f - takenReduceMult*level)


      //产生jitter镀膜
      ftr.setJitter(key, JITTER_COLOR_ABOVE, 1f,1,0f)
      ftr.setJitterUnder(key, JITTER_COLOR_UNDER, 1f,16,5f)

    }

    override fun readyToEndImpl()
    {
      ftr.mutableStats.damageToTargetHullMult.modifyMult(key, 1f)
      ftr.mutableStats.damageToTargetShieldsMult.modifyMult(key, 1f)

      ftr.mutableStats.armorDamageTakenMult.modifyMult(key, 1f)
      ftr.mutableStats.hullDamageTakenMult.modifyMult(key, 1f)
      ftr.customData.remove(key)
    }
  }

}