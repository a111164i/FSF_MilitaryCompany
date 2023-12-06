package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.impl.combat.MineStrikeStats
import com.fs.starfarer.api.impl.combat.RecallDeviceStats
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.Misc
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Blinker
import combat.util.aEP_Combat
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_Rapture.Companion.DIRECTION_COLOR
import data.scripts.shipsystems.aEP_Rapture.Companion.FLUX_PERCENT_PER_TICK
import data.scripts.shipsystems.aEP_Rapture.Companion.FLUX_PER_TICK
import data.scripts.shipsystems.aEP_Rapture.Companion.FREE_RANGE
import data.scripts.shipsystems.aEP_Rapture.Companion.KEY
import data.scripts.shipsystems.aEP_Rapture.Companion.OVERLOAD_TIME
import data.scripts.shipsystems.aEP_Rapture.Companion.SPEED_SLOW_PER_TICK
import data.scripts.shipsystems.aEP_Rapture.Companion.TICK_DIST
import data.scripts.weapons.PredictionStripe
import data.scripts.weapons.aEP_BeamRepair
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color

class aEP_Rapture:  BaseShipSystemScript() {
  companion object{
    //正在受到效果的目标会持续被set这个key
    const val KEY = "aEP_Rapture"

    val JITTER_COLOR: Color = MineStrikeStats.JITTER_UNDER_COLOR
    val DIRECTION_COLOR: Color = Misc.scaleAlpha(Color.red,1f)

    const val ACTIVE_TIME = 12f
    //链子极限长度为目标的碰撞半径加FREE_RANGE
    const val FREE_RANGE = 200f
    const val SYSTEM_RANGE = 1200f

    const val TICK_DIST = 25f

    //每25距离加多少幅能
    const val FLUX_PER_TICK = 250f
    const val FLUX_PERCENT_PER_TICK = 2.5f
    //每25距离减一次速度
    const val SPEED_SLOW_PER_TICK = 0.5f

    const val OVERLOAD_TIME = 1f
  }

  private var ship: ShipAPI? = null

  override fun apply(stats: MutableShipStatsAPI, id: String?, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴这行
    ship = (stats.entity?: return) as ShipAPI
    val amount = aEP_Tool.getAmount(ship)
    val ship = ship as ShipAPI

    //抖动
    var jitterLevel = effectLevel
    if (state == ShipSystemStatsScript.State.OUT) {
      jitterLevel *= jitterLevel
    }
    val maxRangeBonus = 25f
    val jitterRangeBonus = jitterLevel * maxRangeBonus
    ship.setJitterUnder(this, JITTER_COLOR, jitterLevel, 11, 0f, 3f + jitterRangeBonus)
    ship.setJitter(this, JITTER_COLOR, jitterLevel, 4, 0f, 0 + jitterRangeBonus)


    //读取ai的目标，如果是ai在开船的话
    var toAim:ShipAPI? = ship.shipTarget
    if(ship.customData.containsKey(aEP_ID.SYSTEM_SHIP_TARGET_KEY) && ship.shipAI != null){
      toAim = ship.customData[aEP_ID.SYSTEM_SHIP_TARGET_KEY] as ShipAPI
    }
    //选中模块就等于选择本体
    if(toAim != null && toAim.isStationModule && toAim.parentStation != null){
      toAim = toAim.parentStation
    }

    //运行一帧，放置锁链
    if(effectLevel == 1f){
      if(ship.customData.containsKey(aEP_ID.SYSTEM_SHIP_TARGET_KEY) && ship.shipAI != null){
        toAim = ship.customData[aEP_ID.SYSTEM_SHIP_TARGET_KEY] as ShipAPI
      }
      if(toAim != null){
        val drag = DragBall(ACTIVE_TIME,toAim)
        aEP_CombatEffectPlugin.addEffect(drag)

        val arc = Global.getCombatEngine().spawnEmpArcVisual(
          ship.hullSpec.getWeaponSlot("ARC_POINT").computePosition(ship), ship,
          drag.ballLocation, null,
          15f,
          Color.blue, Color.white)
        arc.setSingleFlickerMode()
      }
    }
  }

  override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {
    //对于正在被起效的目标不能放第二个
    if(ship.shipTarget?.customData?.containsKey(KEY) == true){
      return false
    }

    val dist = aEP_Tool.checkTargetWithinSystemRange(ship, ship.shipTarget?.location, SYSTEM_RANGE)
    return dist <= 0f
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String {
    var string = aEP_Tool.getInfoTextWithinSystemRange(ship, ship.shipTarget?.location, SYSTEM_RANGE)
    if(ship.shipTarget?.customData?.containsKey(KEY) == true){
      string = "Not Valid"
    }
    return string
  }
}

class DragBall(lifetime:Float,val target:ShipAPI) : aEP_BaseCombatEffect(lifetime){
  //影响锚图片的大小，链子的粗细
  var sizeMult = 2f

  val sprite = Global.getSettings().getSprite("aEP_FX","yonglang_rapture_anchor")
  val spriteBase = Global.getSettings().getSprite("aEP_FX","yonglang_rapture_anchor_base")
  val spriteDirection = Global.getSettings().getSprite("aEP_FX","forward")

  var fac2Entity = 0f
  var dist2Entity = 0f
  var maxLength = FREE_RANGE + target.collisionRadius
  val ballLocation = MathUtils.getRandomPointOnCircumference(Vector2f(target.location),maxLength)

  var cumulatedDist = 0f

  var chain = Chain(target, this)
  val directionBlinker = aEP_Blinker(8f,0f)

  var moved = false

  //出场特效
  init {
    aEP_CombatEffectPlugin.addEffect(chain)

    sprite.setSize(sprite.width * sizeMult, sprite.height * sizeMult)
    spriteBase.setSize(spriteBase.width * sizeMult, spriteBase.height * sizeMult)
    spriteDirection.setSize(40f * sizeMult, 40f * sizeMult)

    Global.getCombatEngine().addHitParticle(
      ballLocation, Misc.ZERO,400f,
      1f,0.1f,0.25f, RecallDeviceStats.JITTER_COLOR)
    Global.getCombatEngine().addHitParticle(
      ballLocation, Misc.ZERO,200f,
      1f,0.1f,0.5f, RecallDeviceStats.JITTER_COLOR)
  }

  override fun advanceImpl(amount: Float) {

    if(aEP_Tool.isDead(target)){
      shouldEnd = true
      return
    }

    target.setCustomData(KEY,1f)

    moved = false
    dist2Entity = MathUtils.getDistance(target.location, ballLocation)
    //fac2Entity = VectorUtils.getAngle(ballLocation, target.location)
    if(dist2Entity > maxLength){
      moved = true
      fac2Entity = VectorUtils.getAngle(ballLocation, target.location)
      ballLocation.set(aEP_Tool.getExtendedLocationFromPoint(target.location, fac2Entity-180f, maxLength))
      cumulatedDist += (dist2Entity - maxLength)
    }

    //渲染底座，注意渲染顺序
    val baseAngle = aEP_Tool.angleAdd(fac2Entity - 90f,  time * 180f)
    MagicRender.singleframe(
      spriteBase, ballLocation, Vector2f(spriteBase.width,spriteBase.height),baseAngle,
      Color.white,false, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
    //渲染锚
    MagicRender.singleframe(
      sprite, ballLocation, Vector2f(sprite.width ,sprite.height ),fac2Entity - 90f,
      Color.white,false, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)

    if(moved){
      directionBlinker.advance(amount)
      //渲染锚的运动方向指示器
      MagicRender.singleframe(
        spriteDirection, ballLocation, Vector2f(spriteDirection.width,spriteDirection.height),fac2Entity - 90f,
        aEP_Tool.getColorWithAlpha(DIRECTION_COLOR, directionBlinker.blinkLevel),true, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
    }

    if (cumulatedDist > TICK_DIST){
      val fluxLeft = (target.maxFlux - target.currFlux -1f).coerceAtLeast(0f)
      val toAdd = FLUX_PER_TICK + target.maxFlux * FLUX_PERCENT_PER_TICK/100f
      if(fluxLeft >= toAdd){
        //电弧
        Global.getCombatEngine().spawnEmpArcPierceShields(
          target, ballLocation, null,
          target, DamageType.ENERGY,
          1f,
          1f,
          Float.MAX_VALUE,
          "system_emp_emitter_impact",
          MathUtils.getRandomNumberInRange(0f, 10f) + 10f,
          Color.blue,
          Color.white)

        target.fluxTracker.increaseFlux(FLUX_PER_TICK,false)
        aEP_Combat.AddStandardSlow(1.1f,SPEED_SLOW_PER_TICK,0f,target)
        cumulatedDist -= TICK_DIST
      }else{
        if(target.fluxTracker?.isOverloaded == false){
          target.fluxTracker?.forceOverload(OVERLOAD_TIME)
        }
      }
    }
  }

  override fun readyToEnd() {
    target.removeCustomData(KEY)
    chain.lifeTime = 100f
    chain.time = 99.9f
    Global.getCombatEngine().spawnExplosion(ballLocation, Misc.ZERO, aEP_BeamRepair.REPAIR_COLOR2,100f*sizeMult,0.5f)
  }
}

class Chain(val target: ShipAPI,val ballDrag:DragBall): PredictionStripe(target){

  init {
    scrollSpeed = 2f
    texLength = 20f * ballDrag.sizeMult
    halfWidth = 10f * ballDrag.sizeMult
    spriteTexId = Global.getSettings().getSprite("aEP_FX","chain").textureId
    layers.clear()
    layers.add(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
  }
  val startPoint = Vector2f(ballDrag.ballLocation)
  val endPoint = Vector2f(ballDrag.target.location)

  override fun advanceImpl(amount: Float) {
    startPoint.set(ballDrag.ballLocation)
    endPoint.set(Vector2f(ballDrag.target.location))

    var redLevel = 1f
    redLevel = ((ballDrag.dist2Entity-50f)/(ballDrag.maxLength-50f)).coerceAtLeast(0f).coerceAtMost(1f)
    color = Color(
      50 + (200*redLevel).toInt(),
      250 - (200*redLevel).toInt(),
      25,175)
    super.advanceImpl(amount)
  }

  override fun createLineNodes() {
    //设置直线的头和尾
    fadePercent = 0.2f
    fadeEndSidePercent = 0.2f
    val xDiff = endPoint.x - startPoint.x
    val yDiff = endPoint.y - startPoint.y
    val step = 0.1f
    var m = 0f
    while( m <= 1f){
      val toAdd = Vector2f(xDiff, yDiff)
      toAdd.scale(m)
      linePoints.add(Vector2f.add(startPoint,toAdd,null))
      m += step
    }
  }
}