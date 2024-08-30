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
import combat.impl.VEs.aEP_SpreadRing
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


    private const val DECO_WEAPON_ID = "aEP_cru_baojiao_piston"
    private const val GLOW_WEAPON_ID = "aEP_cru_baojiao_glow"
    private val JITTER_COLOR_BLINK = Color(255,150,55,255)

  }

  private var ship: ShipAPI? = null

  private val checkTimer = IntervalUtil(0.05f,0.05f)
  private val blinkTimer = IntervalUtil(0.025f,0.025f)
  private val smokeTimer = IntervalUtil(0.12f,0.08f)

  private val maxHeat = 8f
  private var heat = 0f
  private var didPulse = false
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    ship = (stats?.entity?: return)as ShipAPI
    val ship = stats.entity as ShipAPI
    val amount = aEP_Tool.getAmount(ship)

    if(effectLevel > 0.5f) heat = maxHeat
    heat -= amount
    heat = heat.coerceAtLeast(0f)

    val heatLevel = heat/maxHeat

    //移动活塞
    //控制发光贴图
    smokeTimer.advance(amount)
    for(w in ship.allWeapons){
      //活塞
      if(w.id.equals(DECO_WEAPON_ID)){
        val anim = w.effectPlugin as aEP_DecoAnimation
        if(anim.decoMoveController.effectiveLevel == 0f) anim.setMoveToLevel(1f)
        if(anim.decoMoveController.effectiveLevel == 1f) anim.setMoveToLevel(0f)
        anim.decoMoveController.speed = 0.5f + 1f * heatLevel

      }
      //发光
      if(w.id.equals(GLOW_WEAPON_ID)){
        val anim = w.effectPlugin as aEP_DecoAnimation
        if(anim.decoGlowController.effectiveLevel == anim.decoGlowController.toLevel){
          anim.decoGlowController.toLevel = heatLevel * MathUtils.getRandomNumberInRange(0.8f,1f)
        }
        if(smokeTimer.intervalElapsed()){
          if (Global.getCombatEngine().viewport.isNearViewport(w.location, 500f)) {
            val initColor = Color(220, 220, 220)
            val alpha = 0.3f * heatLevel
            val lifeTime = 3f * heatLevel
            val size = 40f
            val endSizeMult = 1.35f
            val vel = aEP_Tool.speed2Velocity(w.currAngle-180f, 80f)
            Vector2f.add(vel, ship.velocity, vel)
            vel.scale(0.5f)
            val loc = aEP_Tool.getExtendedLocationFromPoint(w.location, w.currAngle-180f, 0f)
            Global.getCombatEngine().addNebulaParticle(
              MathUtils.getRandomPointInCircle(loc, 20f),
              vel,
              size, endSizeMult,
              0.1f, 0.4f,
              lifeTime * MathUtils.getRandomNumberInRange(0.5f, 0.75f),
              aEP_Tool.getColorWithAlpha(initColor, alpha)
            )
          }
        }

      }
    }

    //modify here
    checkTimer.advance(amount)
    if(checkTimer.intervalElapsed() && effectLevel > 0.5f){
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

    //闪光
    blinkTimer.advance(amount)
    if(blinkTimer.intervalElapsed() && effectLevel > 0.1f){
      for(slot in ship.hullSpec.allWeaponSlotsCopy){
        if(slot.isSystemSlot){
          if(MathUtils.getRandomNumberInRange(0f,1f) < 0.5f){

            val loc = slot.computePosition(ship)
            Global.getCombatEngine().addHitParticle(
              loc,
              Vector2f(0f, 0f),
              (25f + MathUtils.getRandomNumberInRange(0f,10f)) * effectLevel,
              0.2f + 0.6f* effectLevel,
              0.05f,
              JITTER_COLOR_BLINK)

          }
        }
      }
    }

    //光圈
    if(effectLevel >= 1f && !didPulse){
      didPulse = true
      val ring = aEP_SpreadRing(
        400f,
        200f,
        Color(255,150,55,30),
        100f,
        800f,
        ship.location)
      ring.initColor.setToColor(255f, 165f, 90f,0.1f,2f)
      ring.endColor.setColor(255f, 165f, 90f,0f)
      aEP_CombatEffectPlugin.addEffect(ring)
    }

    if(effectLevel <= 0f) didPulse = false
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