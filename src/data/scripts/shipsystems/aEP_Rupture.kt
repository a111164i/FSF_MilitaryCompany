package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.impl.combat.MineStrikeStats
import com.fs.starfarer.api.impl.combat.RecallDeviceStats
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_BaseCombatEffectWithKey
import data.scripts.aEP_CombatEffectPlugin
import data.scripts.utils.aEP_Blinker
import data.scripts.utils.aEP_Combat
import data.scripts.utils.aEP_ID
import data.scripts.utils.aEP_Tool
import data.scripts.shipsystems.aEP_Rupture.Companion.DIRECTION_COLOR
import data.scripts.shipsystems.aEP_Rupture.Companion.FLUX_PERCENT_PER_TICK
import data.scripts.shipsystems.aEP_Rupture.Companion.FLUX_PER_TICK
import data.scripts.shipsystems.aEP_Rupture.Companion.FREE_RANGE
import data.scripts.shipsystems.aEP_Rupture.Companion.JITTER_COLOR
import data.scripts.shipsystems.aEP_Rupture.Companion.SPEED_SLOW_PER_TICK
import data.scripts.shipsystems.aEP_Rupture.Companion.TICK_DIST
import data.scripts.weapons.PredictionStripe
import data.scripts.weapons.aEP_BeamRepair
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color

class aEP_Rupture:  BaseShipSystemScript() {
  companion object{
    //正在受到效果的目标会持续被set这个key
    const val TARGET_KEY = "aEP_RuptureTarget"

    val JITTER_COLOR: Color = MineStrikeStats.JITTER_UNDER_COLOR
    val DIRECTION_COLOR: Color = Misc.scaleAlpha(Color.red,1f)

    val FLUX_INJECT_COLOR: Color = Color(100,165,255,255)

    const val ACTIVE_TIME = 12f
    //链子极限长度为FREE_RANGE，不应该小于系统释放范围，也受到系统射程的加成
    const val FREE_RANGE = 900f
    const val SYSTEM_RANGE = 600f

    const val TICK_DIST = 50f

    //每tick距离加多少幅能
    const val FLUX_PER_TICK = 150f
    const val FLUX_PERCENT_PER_TICK = 2f
    //每tick距离减一次速度
    const val SPEED_SLOW_PER_TICK = 0.5f
    const val OVERLOAD_TIME = 1f

    const val DRONE_HP = 3000f
    const val DRONE_ARMOR = 250f

    fun isValidTarget(t: ShipAPI?, self:ShipAPI):Boolean{
      t?:run { return false }
      if(!aEP_Tool.isShipTargetable(
          t,
          false,false,false,
          false,false)) return false

      //对于正在被起效的目标不能放第二个
      if(t.customData?.containsKey(TARGET_KEY+self.id) == true){
        return false
      }

      val dist = aEP_Tool.checkTargetWithinSystemRange(self, t.location, SYSTEM_RANGE,t.collisionRadius)
      return dist <= 0f
    }
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

        /*//用fxDrone是没有自带ai的
        val variant = Global.getSettings().createEmptyVariant(
          aEP_EliteShip.DRONE_ID,
          Global.getSettings().getHullSpec(aEP_EliteShip.DRONE_ID))
        val drone = Global.getCombatEngine().createFXDrone(variant)
        //并不是特效无人机，而是实体，去掉这个tag。这个tag会被createFXDrone()自动添加
        drone.tags.remove(Tags.VARIANT_FX_DRONE)
        drone.isDrone = true
        drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f, ship)
        drone.layer = CombatEngineLayers.FIGHTERS_LAYER
        drone.owner = ship.owner
        drone.facing = 0f
        //变更装甲
        drone.maxHitpoints = DRONE_HP
        drone.hitpoints = DRONE_HP
        drone.mutableStats.armorBonus.modifyFlat("aEP_Rupture", DRONE_ARMOR)
        val armorPerCell = DRONE_ARMOR/15f
        val xSize = drone.armorGrid.leftOf + drone.armorGrid.rightOf
        val ySize = drone.armorGrid.above + drone.armorGrid.below
        for (x in 0 until xSize) {
          for (y in 0 until ySize) {
            drone.armorGrid.setArmorValue(x, y, armorPerCell)
          }
        }
        //级别盾
        drone.addListener(object : DamageTakenModifier{
          override fun modifyDamageTaken(
            param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f?, shieldHit: Boolean): String? {
            var source: ShipAPI? = null
            if(param is DamagingProjectileAPI){
              if(param.source is ShipAPI) source = param.source
            }
            if(param is BeamAPI){
              if(param.source is ShipAPI) source = param.source
            }
            if(param is ShipAPI){
               source = param
            }

            source?.run {
              if(source.hullSize == ShipAPI.HullSize.DESTROYER) damage.modifier.modifyMult(TARGET_KEY, 0.8f)
              if(source.hullSize == ShipAPI.HullSize.CRUISER) damage.modifier.modifyMult(TARGET_KEY, 0.5f)
              if(source.hullSize == ShipAPI.HullSize.CAPITAL_SHIP) damage.modifier.modifyMult(TARGET_KEY, 0.2f)

              return aEP_Rupture.TARGET_KEY
            }
            return null
          }
        })
        Global.getCombatEngine().addEntity(drone)*/
        val drag = DragBall(ACTIVE_TIME,toAim, ship)
        aEP_CombatEffectPlugin.addEffect(drag)

        val arcPoint = ship.hullSpec.getWeaponSlot("ARC_POINT").computePosition(ship)
        val params = EmpArcParams()
        //			params.segmentLengthMult = 4f;
        //			params.zigZagReductionFactor = 0.25f;
        //			params.fadeOutDist = 200f;
        //			params.minFadeOutMult = 2f;
        params.segmentLengthMult = 8f
        params.zigZagReductionFactor = 0.15f
        params.brightSpotFullFraction = 0.5f
        params.brightSpotFadeFraction = 0.5f
        //params.nonBrrightSpotMinBrightness = 0.25f;
        val dist: Float = Misc.getDistance(arcPoint, ship.shipTarget.location)
        params.flickerRateMult = 0.6f - dist / 3000f
        if (params.flickerRateMult < 0.3f) {
          params.flickerRateMult = 0.3f
        }
        val arc = Global.getCombatEngine().spawnEmpArcVisual(
          arcPoint, ship,
          ship.shipTarget.location, ship.shipTarget,
          30f, aEP_Tool.getColorWithAlpha(JITTER_COLOR,1f), Color.white,
          params)
        //arc.setFadedOutAtStart(true);
        //arc.setRenderGlowAtStart(false);
        arc.setSingleFlickerMode(true)

        Global.getSoundPlayer().playSound("system_interdictor",1f,1f,ship.location, Misc.ZERO)
      }
    }
  }

  override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {

    return isValidTarget(ship.shipTarget,ship)
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String {
    var string = aEP_Tool.getInfoTextWithinSystemRange(ship, ship.shipTarget?.location, SYSTEM_RANGE,ship.shipTarget?.collisionRadius)
    if(ship.shipTarget?.customData?.containsKey(TARGET_KEY+ship.id) == true){
      string = "Not Valid"
    }
    return string
  }
}

class DragBall(lifetime:Float,val target:ShipAPI, val ship: ShipAPI) : aEP_BaseCombatEffectWithKey(lifetime, target){
  //影响锚图片的大小，链子的粗细
  val sizeMult = 0.75f
  //只影响粗细
  val lineWidth = 2f

  val sprite = Global.getSettings().getSprite("aEP_FX","yonglang_rapture_anchor")
  val spriteBase = Global.getSettings().getSprite("aEP_FX","yonglang_rapture_anchor_base")
  val spriteDirection = Global.getSettings().getSprite("aEP_FX","forward")

  var fac2Entity = ship.facing
  var maxLength = aEP_Tool.getSystemRange(ship,FREE_RANGE) + ship.collisionRadius + target.collisionRadius
  val ballLocation = ship.hullSpec.getWeaponSlot("ARC_POINT").computePosition(ship)
  var dist2Entity =Misc.getDistance(ballLocation, target.location)

  var minDist = Misc.getDistance(ballLocation, target.location)
  var cumulatedDist = 0f

  var chain = Chain(target, this)
  val directionBlinker = aEP_Blinker(8f,0f)

  var moved = false

  //出场特效
  init {
    setKeyAndPutInData("aEP_RuptureTarget")

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

    if(aEP_Tool.isDead(target) || aEP_Tool.isDead(ship)){
      shouldEnd = true
      return
    }

    moved = false
    target.setCustomData(aEP_Rupture.TARGET_KEY+ship.id,1f)
    //把锚放在船的特殊点位上
    ballLocation.set(ship.hullSpec.getWeaponSlot("ARC_POINT").computePosition(ship))
    dist2Entity = Misc.getDistance(ballLocation, target.location)
    if(dist2Entity > maxLength){
      shouldEnd = true
    }

    if(dist2Entity > minDist){
      cumulatedDist += (dist2Entity-minDist)
      minDist = dist2Entity
      moved = true
    }
    minDist = dist2Entity

    //灌幅能
    while(cumulatedDist > TICK_DIST){
      cumulatedDist -= TICK_DIST
      fac2Entity = VectorUtils.getAngle(ballLocation, target.location)

      val fluxLeft = (target.maxFlux - target.currFlux -1f).coerceAtLeast(0f)
      val toAdd = (FLUX_PER_TICK + target.maxFlux * FLUX_PERCENT_PER_TICK/100f).coerceAtMost(fluxLeft)
      //电弧

      val params = EmpArcParams()
      params.brightSpotFullFraction = 0.5f
      params.brightSpotFadeFraction = 0.5f
      params.glowAlphaMult = 0.1f
      // flickerRate 频闪频率，越小，闪电存在时间越久。通灵塔写的挺好的，别动
      val dist: Float = Misc.getDistance(ballLocation, target.location)
      params.flickerRateMult = 0.6f - dist / 3000f
      if (params.flickerRateMult < 0.3f) {
        params.flickerRateMult = 0.3f
      }
      // movementDurMax 闪电向前移动的时间，太慢会导致闪电本身都消失了，还没有移动的目标。闪电本身的持续时间是跟着粗度来的，默认0.1f挺好的别动
      params.movementDurMax = (0.05f + 0.05f * dist/2000f).coerceAtMost(0.15f)
      params.movementDurMin = params.movementDurMax
      val arc = Global.getCombatEngine().spawnEmpArc(
        ship, ballLocation,
        ship, target,
        DamageType.ENERGY, 0f,0f,9999f,
        "tachyon_lance_emp_impact",
        16f, Color(255,255,255,1),
        Color(255,255,255,1),
        params)
      arc.setSingleFlickerMode(true)

      target.fluxTracker.increaseFlux(toAdd,false)
      aEP_Combat.AddStandardSlow(1.1f,SPEED_SLOW_PER_TICK,0f,target)
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

  }

  override fun readyToEndImpl() {
    chain.lifeTime = 100f
    chain.time = 99.9f
    //闪光
    Global.getCombatEngine().addSmoothParticle(
      ballLocation,
      Misc.ZERO,
      300f,1f,0.1f,0.15f,JITTER_COLOR)

    Global.getCombatEngine().spawnExplosion(ballLocation, Misc.ZERO, aEP_BeamRepair.REPAIR_COLOR2,200f*sizeMult,0.5f)
    Global.getSoundPlayer().playSound("phase_anchor_vanish",1f,1f,ship.location, Misc.ZERO)
    target.removeCustomData(aEP_Rupture.TARGET_KEY+ship.id)
  }
}

class Chain(val target: ShipAPI,val ballDrag:DragBall): PredictionStripe(target){

  init {
    scrollSpeed = 2f
    texLength = 20f * ballDrag.sizeMult * ballDrag.lineWidth
    halfWidth = 10f * ballDrag.sizeMult * ballDrag.lineWidth
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
      25,155)
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

