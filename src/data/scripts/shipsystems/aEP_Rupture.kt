package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.impl.combat.MineStrikeStats
import com.fs.starfarer.api.impl.combat.RecallDeviceStats
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.Misc
import combat.impl.aEP_BaseCombatEffect
import combat.impl.aEP_BaseCombatEffectWithKey
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Blinker
import combat.util.aEP_Combat
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.hullmods.aEP_EliteShip
import data.scripts.shipsystems.aEP_Rupture.Companion.DIRECTION_COLOR
import data.scripts.shipsystems.aEP_Rupture.Companion.FLUX_PERCENT_PER_TICK
import data.scripts.shipsystems.aEP_Rupture.Companion.FLUX_PER_TICK
import data.scripts.shipsystems.aEP_Rupture.Companion.FREE_RANGE
import data.scripts.shipsystems.aEP_Rupture.Companion.OVERLOAD_TIME
import data.scripts.shipsystems.aEP_Rupture.Companion.SPEED_SLOW_PER_TICK
import data.scripts.shipsystems.aEP_Rupture.Companion.TICK_DIST
import data.scripts.weapons.PredictionStripe
import data.scripts.weapons.aEP_BeamRepair
import org.lazywizard.lazylib.MathUtils
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

    const val ACTIVE_TIME = 12f
    //链子极限长度为目标的碰撞半径加FREE_RANGE
    const val FREE_RANGE = 200f
    const val SYSTEM_RANGE = 900f

    const val TICK_DIST = 25f

    //每25距离加多少幅能
    const val FLUX_PER_TICK = 250f
    const val FLUX_PERCENT_PER_TICK = 2.5f
    //每25距离减一次速度
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
      if(t.customData?.containsKey(TARGET_KEY) == true){
        return false
      }

      val dist = aEP_Tool.checkTargetWithinSystemRange(self, t.location, SYSTEM_RANGE)
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

        //用fxDrone是没有自带ai的
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
          /**
           * Modifications to damage should ONLY be made using damage.getModifier().
           *
           * param can be:
           * null
           * DamagingProjectileAPI
           * BeamAPI
           * EmpArcEntityAPI
           * Something custom set by a script
           *
           * @return the id of the stat modification to damage.getModifier(), or null if no modification was made
           */
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
        Global.getCombatEngine().addEntity(drone)

        val drag = DragBall(ACTIVE_TIME,toAim, drone)
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

    return isValidTarget(ship.shipTarget,ship)
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String {
    var string = aEP_Tool.getInfoTextWithinSystemRange(ship, ship.shipTarget?.location, SYSTEM_RANGE)
    if(ship.shipTarget?.customData?.containsKey(TARGET_KEY) == true){
      string = "Not Valid"
    }
    return string
  }
}

class DragBall(lifetime:Float,val target:ShipAPI, val anchor: ShipAPI) : aEP_BaseCombatEffectWithKey(lifetime, target){
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

    if(aEP_Tool.isDead(target) || aEP_Tool.isDead(anchor)){
      shouldEnd = true
      return
    }

    target.setCustomData(aEP_Rupture.TARGET_KEY,1f)

    moved = false
    dist2Entity = MathUtils.getDistance(target.location, ballLocation)
    //fac2Entity = VectorUtils.getAngle(ballLocation, target.location)
    if(dist2Entity > maxLength){
      moved = true
      fac2Entity = VectorUtils.getAngle(ballLocation, target.location)
      ballLocation.set(aEP_Tool.getExtendedLocationFromPoint(target.location, fac2Entity-180f, maxLength))
      cumulatedDist += (dist2Entity - maxLength)
    }

    //把无人机锁到球上
    anchor.velocity.set(Vector2f(0f,0f))
    anchor.location.set(ballLocation)
    anchor.facing = fac2Entity

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

  override fun readyToEndImpl() {
    chain.lifeTime = 100f
    chain.time = 99.9f
    Global.getCombatEngine().spawnExplosion(ballLocation, Misc.ZERO, aEP_BeamRepair.REPAIR_COLOR2,100f*sizeMult,0.5f)
    Global.getCombatEngine().removeEntity(anchor)
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

