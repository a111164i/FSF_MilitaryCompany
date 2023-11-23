package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.combat.listeners.FighterOPCostModifier
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_DataTool.floatDataRecorder
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.weapons.PredictionStripe
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.absoluteValue

class aEP_SpecialHull : aEP_BaseHullMod(), FighterOPCostModifier {

  companion object {

    //距离小于此，无论前进还是后退都获得加速
    const val ACTIVE_RANGE_MIN = 200f
    //距离到超过这里，完全无加成
    const val ACTIVE_RANGE_MAX = 600f

    const val SPEED_BONUS = 15f
    const val ACC_BONUS = 45f

    const val TURN_BONUS = 6f
    const val TURN_ACC_BONUS = 18f

    const val ZERO_FLUX_EXTRA_THRESHOLD = 4f //百分之几以下触发加速装填，航母派出飞机是1%，所以这里给2%

    val BOOST_COLOR1 = Color(255,0,0)
    val BOOST_COLOR2 = Color(225,125,75,100)

    const val MAX_OVERLOAD_TIME = 8f
    const val ID = "aEP_SpecialHull"

  }

  init {
    notCompatibleList.add( HullMods.SAFETYOVERRIDES)
  }

  override fun applyEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
    stats.dynamic.getStat(aEP_EliteShip.INSV_ID).baseValue = 0f
    //必须加入stats
    if(!stats.hasListenerOfClass(this::class.java)){
      stats.addListener(aEP_SpecialHull())
    }
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    //改变0幅能加速的起始
    ship.mutableStats.zeroFluxMinimumFluxLevel.modifyFlat(ID, ZERO_FLUX_EXTRA_THRESHOLD/100f)

    //修改护盾贴图
    if (ship.shield != null) {
      //set shield inner, outer ring
      ship.hullStyleId
      ship.shield.setRadius(
        ship.shield.radius,
        Global.getSettings().getSpriteName("aEP_hullstyle", "aEP_shield_inner"),
        Global.getSettings().getSpriteName("aEP_hullstyle", "aEP_shield_outer")
      )
    }

    //当希望customData进行初始化时，条件一定要是customData.contains()，有的时候舰船生成到一半，custom此时还没有创建
    if (!ship.customData.containsKey("$ID _ ${ship.id}")) {
      val fluxData = floatDataRecorder()
      ship.setCustomData("$ID _ ${ship.id}",fluxData)
      aEP_CombatEffectPlugin.addEffect(FriendlyAttraction(ship))
      //ship.addListener(FluxRecorder(ship, fluxData))
    }

  }

  override fun applySmodEffectsAfterShipCreationImpl(ship: ShipAPI, stats: MutableShipStatsAPI, id: String) {

  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    super.advanceInCombat(ship, amount)
    if (ship.fluxTracker.isOverloaded && ship.fluxTracker.overloadTimeRemaining > MAX_OVERLOAD_TIME+0.1f) {
      ship.fluxTracker.setOverloadDuration(MAX_OVERLOAD_TIME)
    }

  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String? {
    return null
  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    //主效果
    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("{%s}"+txt("aEP_MarkerDissipation01"), 5f, arrayOf(Color.green,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", SPEED_BONUS),
      String.format("%.0f", ACC_BONUS))
    tooltip.addPara("{%s}"+txt("aEP_MarkerDissipation02"), 5f, arrayOf(Color.green,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", ZERO_FLUX_EXTRA_THRESHOLD) +"%")


    //负面
    //显示不兼容插件
    tooltip.addPara("{%s}"+txt("not_compatible")+"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))


    //灰字额外说明
    //tooltip.addPara(aEP_DataTool.txt("aEP_MarkerDissipation03"), grayColor, 5f)

  }

  //-------------------------------------//
  //现在开始是FighterOPCostModifier，所有舰船共用一个实例，不要写变量
  override fun getFighterOPCost(stats: MutableShipStatsAPI, fighter: FighterWingSpecAPI, currCost: Int): Int {
    stats?:return currCost
    //这里的stats的entity一定是null的，在被舰船被生成之前就已经被调用了
    val variant = stats.variant
    //助手和改机库冲突
    if(variant.hasHullMod(HullMods.CONVERTED_HANGAR)){
      if(fighter.hasTag("aEP_no_ch")){
        return (currCost + 5)
      }
    }

    return currCost
  }

  override fun affectsOPCosts(): Boolean {
    return true
  }

  internal inner class FriendlyAttraction(private val ship: ShipAPI) : aEP_BaseCombatEffect(0f,ship){

    var currentIndicator : PredictionStripe? = null
    var activeTime = 0f
    var level = 0f
    var activatingShip : ShipAPI? = null
    val checkTimer = IntervalUtil(0.15f,0.25f)
    var zeroBuffTime = 0f

    override fun advanceImpl(amount: Float) {
      //对模块不生效
      if(ship.isStationModule) return

      activeTime -= amount
      checkTimer.advance(amount)

      val acceleratingDir = aEP_Tool.computeCurrentManeuveringDir(ship)
      if(checkTimer.intervalElapsed()){
        val aroundShips = CombatUtils.getShipsWithinRange(ship.location,ship.collisionRadius + ACTIVE_RANGE_MAX)
        //val currVelDir = aEP_Tool.
        activatingShip = null
        level = 0f
        var currAngleDist = 360f
        for(s in aroundShips){
          if(s.isFighter) continue
          //自己根本不在移动，就不需要加速
          if(!ship.engineController.isAccelerating
            && !ship.engineController.isDecelerating
            && !ship.engineController.isAcceleratingBackwards
            && !ship.engineController.isStrafingLeft
            && !ship.engineController.isStrafingRight) continue
          //不能把一个比自己小的友军作为目标
          if(s.hullSize < ship.hullSize) continue
          if(s.owner != ship.owner) continue
          if(!s.variant.hasHullMod(ID)) continue
          if(aEP_Tool.isDead(s)) continue
          if(s == ship) continue
          val dist = MathUtils.getDistance(s,ship)
          val angleToFriendly = VectorUtils.getAngle(ship.location, s.location)
          var angleDistAbs = MathUtils.getShortestRotation(acceleratingDir, angleToFriendly).absoluteValue

          //如果处于最小距离内，无论前进后退都获得加成
          if(dist < ACTIVE_RANGE_MIN){
            angleDistAbs = 0f
          }

          if(angleDistAbs < currAngleDist){
            currAngleDist = angleDistAbs
            activatingShip = s
            if(angleDistAbs <= 0f) break
          }
        }

        //如果最小角度大于90度，重新把activatingShip改回null
        if(currAngleDist < 90f){
          level = 1f - ((currAngleDist-20f)/90f).coerceAtLeast(0f).coerceAtMost(1f)
        }else{
          activatingShip = null
        }

        //更新加成
        updateShipStats(amount)
        //更新视觉效果
        updateVisual()
      }

      //根据当前是否有生效舰船控制激活时间
      if(activatingShip != null ){
        activeTime += amount
      }else{
        activeTime -= amount * 2f
      }
      activeTime = activeTime.coerceAtLeast(0f).coerceAtMost(0.25f)

      //如果当前特效已经完成淡出，移除掉
      if(currentIndicator != null && currentIndicator!!.radius == -1f){
        currentIndicator = null
      }

      //消除惯性

    }


    fun updateShipStats(amount: Float){
      ship.mutableStats.maxSpeed.modifyFlat(ID, SPEED_BONUS * level)
      ship.mutableStats.acceleration.modifyFlat(ID, ACC_BONUS * level)
      ship.mutableStats.deceleration.modifyFlat(ID, ACC_BONUS * level)

      ship.mutableStats.maxTurnRate.modifyFlat(ID, TURN_BONUS* level)
      ship.mutableStats.turnAcceleration.modifyFlat(ID, TURN_ACC_BONUS * level)
    }

    fun updateVisual(){
      //找到了可以提供加速的目标，在之间创造一根链子
      if(activatingShip != null){
        //如果当前和该目标不存在链子，加一根
        if(currentIndicator == null){
          val vxf = Indicator(ship, activatingShip!!)
          currentIndicator = vxf
          aEP_CombatEffectPlugin.addEffect(vxf)
        //如果当前和目标存在链子，把持续视时间改为无限
        } else {
          currentIndicator!!.lifeTime = 0f
        }

      //如果没有找到目标
      }else{

        if(currentIndicator == null){

        //如果当前存在和其他舰船的链子，把lifetime设定好，让链子开始淡出
        } else {
          //当前时间设置为5，结束时间设置为5.2，淡出时间设置为0.2
          //因为链子可能有淡入效果，所以设置成5保证淡入部分完成
          currentIndicator!!.time = 5f
          currentIndicator!!.lifeTime = 5.2f
          currentIndicator!!.fadeOut = 0.2f
        }

      }
    }

    inner class Indicator(ship: ShipAPI,val friendly: ShipAPI) : PredictionStripe(ship){

      var spriteId1 = Global.getSettings().getSprite("aEP_FX","forward").textureId
      var spriteId2 = Global.getSettings().getSprite("aEP_FX","hold").textureId

      var dist = MathUtils.getDistance(ship,friendly)
      val startPoint = Vector2f(ship.location)
      val endPoint = Vector2f(friendly.location)


      init {
        scrollSpeed = -5f
        halfWidth = 20f
        texLength = 40f
      }
      override fun advanceImpl(amount: Float) {
        dist = MathUtils.getDistance(ship,friendly)
        if(dist > 0f){
          val selfToFriendly =  VectorUtils.getAngle(m.location, friendly.location)
          startPoint.set(aEP_Tool.getExtendedLocationFromPoint(m.location, selfToFriendly, m.collisionRadius))
          endPoint.set(aEP_Tool.getExtendedLocationFromPoint(m.location, selfToFriendly, m.collisionRadius + dist))

        }else{
          val selfToFriendly =  VectorUtils.getAngle(m.location, friendly.location)
          startPoint.set(aEP_Tool.getExtendedLocationFromPoint(m.location, selfToFriendly, m.collisionRadius/2f))
          endPoint.set(aEP_Tool.getExtendedLocationFromPoint(m.location, selfToFriendly, m.collisionRadius/2f +  friendly.collisionRadius/2f))
        }

        if(dist < ACTIVE_RANGE_MIN ){
          spriteTexId = spriteId2
          color = aEP_Tool.getColorWithAlpha(Color.green,0.275f)
          scrollSpeed = 2f
        }else{
          spriteTexId = spriteId1
          color = aEP_Tool.getColorWithAlpha(Color.red,0.275f)
          scrollSpeed = -5f
        }

        super.advanceImpl(amount)

        if(aEP_Tool.isDead(friendly) || aEP_Tool.isDead(ship)){
          shouldEnd = true
        }
      }

      override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
        super.renderImpl(layer, viewport)
      }

      override fun createLineNodes() {
        //设置直线的头和尾
        val facingToFriendly = VectorUtils.getAngle(m.location, friendly.location)
        val timeLevel = ((time-0f)/0.25f).coerceAtLeast(0f).coerceAtMost(1f)
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
  }

}