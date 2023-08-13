package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CollisionClass
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipCommand
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Combat
import combat.util.aEP_DataTool
import combat.util.aEP_ID
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicAnim
import org.magiclib.util.MagicRender
import java.awt.Color
import kotlin.math.abs

//战地重建
open class aEP_EmergencyReconstruct() : aEP_BaseHullMod(), HullDamageAboutToBeTakenListener, AdvanceableListener {
  companion object{
    const val ID = "aEP_EmergencyReconstruct"
    const val ACTIVE_KEY = "aEP_EmergencyReconstructActive"
    const val ACTIVE_FRIENDLY_RANGE = 1600f

    val CHANGE_DROP_HULLSIZE = HashMap<ShipAPI.HullSize, Float>()
    init {
      CHANGE_DROP_HULLSIZE.put(ShipAPI.HullSize.FIGHTER, 0.33f)
      CHANGE_DROP_HULLSIZE.put(ShipAPI.HullSize.FRIGATE, 0.15f)
      CHANGE_DROP_HULLSIZE.put(ShipAPI.HullSize.DESTROYER, 0.25f)
      CHANGE_DROP_HULLSIZE.put(ShipAPI.HullSize.CRUISER, 0.33f)
      CHANGE_DROP_HULLSIZE.put(ShipAPI.HullSize.CAPITAL_SHIP, 0.5f)
      CHANGE_DROP_HULLSIZE.withDefault { 0.5f }
    }

    val FIRST_CHANGE_HULLSIZE = HashMap<ShipAPI.HullSize, Float>()
    init {
      FIRST_CHANGE_HULLSIZE.put(ShipAPI.HullSize.FIGHTER, 1f)
      FIRST_CHANGE_HULLSIZE.put(ShipAPI.HullSize.FRIGATE, 1f)
      FIRST_CHANGE_HULLSIZE.put(ShipAPI.HullSize.DESTROYER, 1f)
      FIRST_CHANGE_HULLSIZE.put(ShipAPI.HullSize.CRUISER, 1f)
      FIRST_CHANGE_HULLSIZE.put(ShipAPI.HullSize.CAPITAL_SHIP, 1f)
      FIRST_CHANGE_HULLSIZE.withDefault { 0.5f }
    }

  }

  //这些变量都是作为listener时使用的
  var activeChance = 1f
  var activeChanceDropPerUse = 0f
  var repairTimeTotal = 20f

  val repairTimer = IntervalUtil(0.2f,0.8f)
  var reconstructTimer = 0f
  var didRepair = false
  lateinit var ship: ShipAPI

  //作为listener时使用
  constructor(ship: ShipAPI) : this() {
    this.ship = ship
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if(!ship.hasListenerOfClass(this.javaClass)){
      val listenerClass = aEP_EmergencyReconstruct(ship)
      val dp =  ship.mutableStats.dynamic.getMod(Stats.DEPLOYMENT_POINTS_MOD).computeEffective(ship.hullSpec.suppliesToRecover)
      listenerClass.repairTimeTotal = aEP_EmergencyReconstructAi.REPAIR_TIME_BASE + aEP_EmergencyReconstructAi.REPAIR_TIME_PER_DP * dp
      listenerClass.activeChance = 1f
      listenerClass.activeChanceDropPerUse =  CHANGE_DROP_HULLSIZE[ship.hullSpec.hullSize]?:0.5f
      ship.addListener(listenerClass)
    }
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

  }

  /**
   * 这里开始是listener的部分，各个船独立，改变量都要在listener里面动，hullmod的advance是全局的
   * */
  override fun advance(amount: Float) {
    reconstructTimer -= amount
    reconstructTimer.coerceAtLeast(0f).coerceAtMost(99f)
    //维修模式时
    if(reconstructTimer > 0f){
      duringRepairEffect(amount)

    }else if(didRepair ){//退出维修模式时运行一次
      onceWhenOutRepair()

    }

    if(aEP_Tool.isDead(ship)){
      ship.removeCustomData(ACTIVE_KEY)
      ship.removeListener(this)
    }

    //addDebugLog(reconstructTime.toString())
  }

  //if true is returned, the hull damage to be taken is negated.
  override fun notifyAboutToTakeHullDamage(param: Any?, ship: ShipAPI, point: Vector2f, damageAmount: Float): Boolean {
    //进入维修模式的那一刻运行一次
    if(damageAmount >= ship.hitpoints && reconstructTimer <= 0f){

//      aEP_CombatEffectPlugin.addEffect(aEP_Combat.StandardTeleport(1f,ship,
//        Vector2f(Global.getCombatEngine().playerShip.mouseTarget),ship.facing))
      val test = MathUtils.getRandomNumberInRange(0f,100f)
      val threshold = (activeChance * 100f)
      if(test <= threshold){

        onceWhenInRepair()
        //这里存在即使过了检测，但周围没有队友导致无法复活的情况，多一道检测
        if(reconstructTimer > 0f){
          var txt = String.format("Reconstruct Test: %.0f",test)
          if(threshold < 100) txt += String.format(" + %.0f",100-threshold)
          Global.getCombatEngine().addFloatingText(ship.location, txt, 30f, Color.green, ship, 1f,5f)
        }

      }else{

        onceWhenFailTest()
        var txt = String.format("Reconstruct Test: %.0f",test)
        if(threshold < 100) txt += String.format(" + %.0f",100-threshold)
        Global.getCombatEngine().addFloatingText(ship.location, txt, 30f, Color.red, ship, 1f,5f)

      }
    }

    if(reconstructTimer > 0f) return true
    return false
  }

  open fun duringRepairEffect(amount: Float){
    didRepair = true
    ship.mutableStats.hullDamageTakenMult.modifyMult(ID, 0f)
    ship.mutableStats.armorDamageTakenMult.modifyMult(ID, 0f)
    ship.mutableStats.combatWeaponRepairTimeMult.modifyFlat(ID, Float.MAX_VALUE)
    ship.mutableStats.combatEngineRepairTimeMult.modifyFlat(ID, Float.MAX_VALUE)
    ship.mutableStats.engineDamageTakenMult.modifyMult(ID, 0f)

    for(e in ship.engineController.shipEngines){
      if(!e.isDisabled){
        e.applyDamage(999f, ship)
        e.disable()
      }
    }
    for(w in ship.allWeapons){
      if(!w.isDisabled) {
        w.disable()
      }
    }

    //禁止一切操作
    ship.blockCommandForOneFrame(ShipCommand.FIRE)
    ship.blockCommandForOneFrame(ShipCommand.VENT_FLUX)
    ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM)
    ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK)
    ship.blockCommandForOneFrame(ShipCommand.ACCELERATE)
    ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS)
    ship.blockCommandForOneFrame(ShipCommand.PULL_BACK_FIGHTERS)

    var level = 1f
    val fadeTime = 3f
    if(reconstructTimer > repairTimeTotal-fadeTime) level =  (repairTimeTotal - reconstructTimer)/ fadeTime
    ship.fadeToColor(ID, Color(75, 75, 75, 255), 1f, 1f, level)
    ship.isJitterShields = false
    ship.setCircularJitter(true)
    ship.setJitterUnder(ID, aEP_Tool.REPAIR_COLOR2, level, 36, 8f+ship.collisionRadius*0.1f)

    //相位效果
    ship.extraAlphaMult = level * 0.5f + 0.5f
    ship.extraAlphaMult2 = level * 0.5f + 0.5f
    ship.setApplyExtraAlphaToEngines(true)
    if(level >= 1f){
      if(ship.phaseCloak != null && ship.phaseCloak.isActive) ship.phaseCloak.deactivate()
      if(ship.shield != null && ship.shield.isOn) ship.shield.toggleOff()
      ship.isPhased = true
      ship.collisionClass = CollisionClass.NONE
    }

    repairTimer.advance(amount)
    if(repairTimer.intervalElapsed()){
      ship.velocity.scale(0.75f)
      ship.angularVelocity *= 0.75f
      val repaired = aEP_Tool.findToRepair(
        ship,
        ((ship.armorGrid.maxArmorInCell * (ship.armorGrid.grid.size * ship.armorGrid.grid[0].size) + ship.maxHitpoints) * 0.5f)/ repairTimeTotal,
        1f, 1f, 100f, 1f
      )
      if(repaired >= 1f){
        //reconstructTime = 0.1f
      }
    }
  }

  open fun onceWhenOutRepair(){

    ship.mutableStats.hullDamageTakenMult.unmodify(ID)
    ship.mutableStats.armorDamageTakenMult.unmodify(ID)
    ship.mutableStats.combatWeaponRepairTimeMult.unmodify(ID)
    ship.mutableStats.combatEngineRepairTimeMult.unmodify(ID)
    ship.mutableStats.engineDamageTakenMult.unmodify(ID)

    ship.removeCustomData(ACTIVE_KEY)

    for(e in ship.engineController.shipEngines){
      if(e.isDisabled) e.repair()
    }
    for(w in ship.allWeapons){
      if(w.isDisabled) w.repair()
    }

    //取消相位效果
    ship.isPhased = false
    ship.collisionClass = CollisionClass.SHIP
    ship.extraAlphaMult = 1f
    ship.extraAlphaMult2 = 1f

    //如果是因为舰船被摧毁导致的运行，不需要清除损伤贴图
    if(didRepair ){
      didRepair = false
      ship.syncWeaponDecalsWithArmorDamage()
      ship.clearDamageDecals()
    }
  }

  open fun onceWhenInRepair(){

    activeChance -= activeChanceDropPerUse
    activeChance.coerceAtLeast(0f).coerceAtMost(1f)

    ship.setCustomData(ACTIVE_KEY,true)

    var distNow = 99999f
    var nearest : ShipAPI? = null
    for(target in Global.getCombatEngine().ships){
      if(target.owner != ship.owner) continue
      if(target == ship) continue
      if(target.isPhased || target.collisionClass == CollisionClass.NONE) continue
      if(target.isFighter || target.collisionClass == CollisionClass.FIGHTER) continue
      if(target.isFrigate) continue
      val dist = MathUtils.getDistance(ship,target.location)
      if(dist < distNow){
        distNow = dist
        nearest = target
      }
    }

    if(nearest != null){
      val telePortLoc = aEP_Tool.findEmptyLocationAroundShip(nearest,ship.collisionRadius)
      aEP_CombatEffectPlugin.addEffect(aEP_Combat.StandardTeleport(3.1f,ship,telePortLoc, ship.facing))

    }else{
      reconstructTimer = 0f
      ship.removeListener(this)
      return
    }
    ship.hitpoints = 1f
    reconstructTimer = repairTimeTotal
    ship.engineController.forceFlameout(true)
    for(s in Global.getCombatEngine().ships){
      if(s.shipTarget == ship){
        s.shipTarget = null
      }
    }


    val scaffoldSize = 100f
    val cellSize = ship.armorGrid.cellSize
    val x = ship.armorGrid.leftOf * cellSize
    val y = ship.armorGrid.above * cellSize
    val leftTopAbs = Vector2f.add(VectorUtils.rotate(Vector2f(-x,y),ship.facing-90f), ship.location,null)

    val leftTopRel = aEP_Tool.getRelativeLocationData(leftTopAbs, ship, false)
    val centerRel = aEP_Tool.getRelativeLocationData(ship.location, ship, false)

    val xLength = (ship.armorGrid.leftOf + ship.armorGrid.rightOf)*cellSize
    val xNum =   (xLength/scaffoldSize).toInt() + 1
    val xStep = xLength/xNum

    val yLength = (ship.armorGrid.above + ship.armorGrid.below)*cellSize
    val yNum =   (yLength/scaffoldSize).toInt() + 1
    val yStep = yLength/yNum

    for(i in 0..yNum){
      val yMoveOffset = (yNum/2f - i) * yStep
      val yTime = abs(yMoveOffset/yStep)
      for(j in 0..xNum){
        val xOffset = (xNum/2f - j) * xStep
        val xTime = abs(xOffset/xStep) + yTime
        //渲染x支架
        aEP_CombatEffectPlugin.addEffect(RepairScaffoldUp(
          xTime,1f, reconstructTimer - i * 0.5f,
          ship, xOffset, yMoveOffset, centerRel))
      }
      //渲染水平支架，后于x支架，要盖在上面
      aEP_CombatEffectPlugin.addEffect(RepairSideFrameUp(
        yTime,  xNum/2f, reconstructTimer - i * 0.5f,ship,
        0f,yMoveOffset,(xLength+scaffoldSize)/2f,centerRel))
    }

    var i = xLength * yLength
    while (i > 0){
      i -= scaffoldSize * scaffoldSize * 10
      aEP_CombatEffectPlugin.addEffect(RepairSideMoving(1f,reconstructTimer - yNum * 0.5f,ship,centerRel))
    }
  }

  open fun onceWhenFailTest(){

    ship.removeListener(this)
  }

  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    //主效果
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)

    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_EmergencyReconstruct01"), 5f, arrayOf(Color.green,highLight),
      aEP_ID.HULLMOD_POINT)
    tooltip.addPara(
      aEP_ID.HULLMOD_BULLET + aEP_DataTool.txt("aEP_EmergencyReconstruct02"), 5f, highLight,
      ACTIVE_FRIENDLY_RANGE.toInt().toString())
    val firstChance = FIRST_CHANGE_HULLSIZE[hullSize]?:1f
    tooltip.addPara(
      aEP_ID.HULLMOD_BULLET + aEP_DataTool.txt("aEP_EmergencyReconstruct03"), 5f, highLight,
      aEP_DataTool.txt("aEP_EmergencyReconstruct04"),
      String.format("%.0f",firstChance*100f))

    //中立
    tooltip.addPara("{%s}" + aEP_DataTool.txt("aEP_EmergencyReconstruct05"), 5f, arrayOf(highLight, highLight),
      aEP_ID.HULLMOD_POINT)
    val dp =  ship?.mutableStats?.dynamic?.getMod(Stats.DEPLOYMENT_POINTS_MOD)?.computeEffective(ship.hullSpec.suppliesToRecover)?:20f
    val time = aEP_EmergencyReconstructAi.REPAIR_TIME_BASE + aEP_EmergencyReconstructAi.REPAIR_TIME_PER_DP * dp
    tooltip.addPara("{%s}" + aEP_DataTool.txt("aEP_EmergencyReconstruct08"), 5f, arrayOf(highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", time))

    //负面
    val chanceDrop = CHANGE_DROP_HULLSIZE[hullSize]?:0.5f
    tooltip.addPara("{%s}" + aEP_DataTool.txt("aEP_EmergencyReconstruct06"), 5f, arrayOf(Color.red,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      aEP_DataTool.txt("aEP_EmergencyReconstruct04"),
      String.format("+%.0f",chanceDrop*100f))

    //显示不兼容插件
    //tooltip.addPara("{%s}"+ txt("not_compatible") +"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))

    //预热完全后额外效果
    //tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor, Alignment.MID, 5f)

    //灰字额外说明
    tooltip.addPara(aEP_DataTool.txt("aEP_EmergencyReconstruct07"), grayColor, 5f)
  }
}

class RepairScaffoldUp(val moveTime : Float,val expandTime: Float, val stayTime: Float, val ship:ShipAPI, val xOffset: Float , val yOffset: Float, val startLocRelativeData: Vector2f) : aEP_BaseCombatEffect((stayTime), ship){
  val smokeTimer = IntervalUtil(1f,5f)

  override fun advanceImpl(amount: Float) {
    val level = (time/moveTime).coerceAtMost(1f)
    val fadeLevel = (time/1f).coerceAtMost(1f)
    val startLoc = aEP_Tool.getAbsoluteLocation(startLocRelativeData, ship,false)
    val shipFacingOffset = ship.facing - 90f
    val blockCenter = startLoc


    //先在0-0.5时竖直向下移动，0.5-1时再水平向右移动
    val verticalMoveLevel = MagicAnim.smooth(level * 2f).coerceAtMost(1f)
    val horizontalMoveLevel = MagicAnim.smooth((level-0.5f) * 2f).coerceAtLeast(0f)
    val expandLevel = MagicAnim.smooth((time-moveTime)/expandTime).coerceAtLeast(0f).coerceAtMost(1f)
    if(level < 0.5f){
      blockCenter.set(aEP_Tool.getExtendedLocationFromPoint(startLoc, -90f + shipFacingOffset, verticalMoveLevel * yOffset))
    }else{
      blockCenter.set(aEP_Tool.getExtendedLocationFromPoint(startLoc, -90f + shipFacingOffset, yOffset))
      blockCenter.set(aEP_Tool.getExtendedLocationFromPoint(startLoc, 0f + shipFacingOffset, horizontalMoveLevel * xOffset))
    }

    createSmoke(amount,blockCenter)

    val cross1 = Global.getSettings().getSprite("aEP_FX","repair_scaffold_x")
    val crossPosition1 = aEP_Tool.getExtendedLocationFromPoint(blockCenter, 0f + shipFacingOffset, 0f)
    MagicRender.singleframe(
      cross1,crossPosition1, Vector2f(20f,100f + expandLevel*14f),45f * expandLevel + shipFacingOffset,
      Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)

    val cross2 = Global.getSettings().getSprite("aEP_FX","repair_scaffold_x")
    val crossPosition2 = aEP_Tool.getExtendedLocationFromPoint(blockCenter, 0f + shipFacingOffset, 0f)
    MagicRender.singleframe(
      cross2,crossPosition2,Vector2f(20f,100f + expandLevel*14f),-45f * expandLevel + shipFacingOffset,
      Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)

//    //左右支架得压住x支架，后生成贴图
//    val left = Global.getSettings().getSprite("aEP_FX","repair_scaffold")
//    val leftPosition = getExtendedLocationFromPoint(blockCenter, 180f + shipFacingOffset, 20f)
//    MagicRender.singleframe(
//      left,leftPosition, Vector2f(20f,80f),0f + shipFacingOffset,
//      Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)
//
//    val right = Global.getSettings().getSprite("aEP_FX","repair_scaffold")
//    val rightPosition = getExtendedLocationFromPoint(blockCenter, 0f + shipFacingOffset, -20f + 40f * expandLevel)
//    MagicRender.singleframe(
//      right,rightPosition, Vector2f(20f,80f),180f + shipFacingOffset,
//      Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)

    //结束时散落
    if(time >= lifeTime){
      var randomVel= MathUtils.getRandomPointInCircle(Misc.ZERO, 50f)
      var randomAngleSpeed= MathUtils.getRandomNumberInRange(-30f,30f)
      var randomLifeTimeLeft = MathUtils.getRandomNumberInRange(0.5f,1.5f)
      MagicRender.battlespace(
        cross1,crossPosition1,randomVel, Vector2f(20f,100f),Misc.ZERO,45f + shipFacingOffset,randomAngleSpeed,
        Color.white,false,
        0f,randomLifeTimeLeft,0.5f)

      randomVel= MathUtils.getRandomPointInCircle(Misc.ZERO, 50f)
      randomAngleSpeed= MathUtils.getRandomNumberInRange(-30f,30f)
      randomLifeTimeLeft = MathUtils.getRandomNumberInRange(0.5f,1.5f)
      MagicRender.battlespace(
        cross2,crossPosition2,randomVel, Vector2f(20f,100f),Misc.ZERO,-45f + shipFacingOffset,randomAngleSpeed,
        Color.white,false,
        0f,randomLifeTimeLeft,0.5f)
    }

  }

  fun createSmoke(amount: Float, center:Vector2f){
    if(time < moveTime) return
    smokeTimer.advance(amount)
    if(smokeTimer.intervalElapsed()){
      val pos = MathUtils.getRandomPointInCircle(ship.location, ship.collisionRadius)
      aEP_Tool.spawnCompositeSmoke(pos,80f,5f,Color(125,125,125))
      aEP_Tool.spawnCompositeSmoke(pos,100f,4f,Color(205,205,205))
    }
  }

}
class RepairSideFrameUp(val moveTime : Float,val expandTime: Float, val stayTime: Float, val ship:ShipAPI, val xOffset: Float , val yOffset: Float, val expandRange:Float, val startLocRelativeData: Vector2f) : aEP_BaseCombatEffect((stayTime), ship){
  val sparkTimer = IntervalUtil(2f,6f)

  override fun advanceImpl(amount: Float) {
    val level = (time/moveTime).coerceAtMost(1f)
    val fadeLevel = (time/1f).coerceAtMost(1f)
    val startLoc = aEP_Tool.getAbsoluteLocation(startLocRelativeData, ship,false)
    val shipFacingOffset = ship.facing - 90f
    val blockCenter = startLoc


    //先在0-0.5时竖直向下移动，0.5-1时再水平向右移动
    val verticalMoveLevel = MagicAnim.smooth(level * 2f).coerceAtMost(1f)
    val horizontalMoveLevel = MagicAnim.smooth((level-0.5f) * 2f).coerceAtLeast(0f)
    val expandLevel = MagicAnim.smooth((time-moveTime)/expandTime).coerceAtLeast(0f).coerceAtMost(1f)
    if(level < 0.5f){
      blockCenter.set(aEP_Tool.getExtendedLocationFromPoint(startLoc, -90f + shipFacingOffset, verticalMoveLevel * yOffset))
    }else{
      blockCenter.set(aEP_Tool.getExtendedLocationFromPoint(startLoc, -90f + shipFacingOffset, yOffset))
      blockCenter.set(aEP_Tool.getExtendedLocationFromPoint(startLoc, 0f + shipFacingOffset, horizontalMoveLevel * xOffset))
    }


    val expandDist = expandRange * expandLevel


    //生成横杠
    val numOfRail = (expandRange/60f).toInt() + 1
    val stepPerRail = expandRange/numOfRail
    val topRailStartPoint = aEP_Tool.getExtendedLocationFromPoint(blockCenter, 90f + shipFacingOffset, 40f)
    for (i in 0 until numOfRail){

      val railL = Global.getSettings().getSprite("aEP_FX","repair_scaffold_rail")
      val moveDist = i * 60f - 30f
      val railPositionL = aEP_Tool.getExtendedLocationFromPoint(
        topRailStartPoint, 180f + shipFacingOffset, expandLevel * moveDist
      )
      MagicRender.singleframe(
        railL,railPositionL, Vector2f(20f,60f),-90f + shipFacingOffset,
        Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)

      val railPositionR = aEP_Tool.getExtendedLocationFromPoint(
        topRailStartPoint, 0f + shipFacingOffset, expandLevel * moveDist
      )
      MagicRender.singleframe(
        railL,railPositionR, Vector2f(20f,60f),90f + shipFacingOffset,
        Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)
    }

    val botRailStartPoint = aEP_Tool.getExtendedLocationFromPoint(blockCenter, -90f + shipFacingOffset, 40f)
    for (i in 0 until numOfRail){

      val railL = Global.getSettings().getSprite("aEP_FX","repair_scaffold_rail")
      val moveDist = i * 60f - 30f
      val railPositionL = aEP_Tool.getExtendedLocationFromPoint(
        botRailStartPoint, 180f + shipFacingOffset, expandLevel * moveDist
      )
      MagicRender.singleframe(
        railL,railPositionL, Vector2f(20f,60f),-90f + shipFacingOffset,
        Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)

      val railPositionR = aEP_Tool.getExtendedLocationFromPoint(
        botRailStartPoint, 0f + shipFacingOffset, expandLevel * moveDist
      )
      MagicRender.singleframe(
        railL,railPositionR, Vector2f(20f,60f),90f + shipFacingOffset,
        Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)
    }

    //生成竖杠
    val left = Global.getSettings().getSprite("aEP_FX","repair_scaffold")
    val leftPosition = aEP_Tool.getExtendedLocationFromPoint(blockCenter, 180f + shipFacingOffset, expandDist)
    MagicRender.singleframe(
      left,leftPosition, Vector2f(20f,100f),0f + shipFacingOffset,
      Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)

    val right = Global.getSettings().getSprite("aEP_FX","repair_scaffold")
    val rightPosition = aEP_Tool.getExtendedLocationFromPoint(blockCenter, 0f + shipFacingOffset, expandDist)
    MagicRender.singleframe(
      right,rightPosition, Vector2f(20f,100f),180f + shipFacingOffset,
      Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)


    //结束时散落
    if(time >= lifeTime){

      var randomVel= MathUtils.getRandomPointInCircle(Misc.ZERO, 50f)
      var randomAngleSpeed= MathUtils.getRandomNumberInRange(-30f,30f)
      var randomLifeTimeLeft = MathUtils.getRandomNumberInRange(0.5f,1.5f)

      val topRailStartPoint = aEP_Tool.getExtendedLocationFromPoint(blockCenter, 90f + shipFacingOffset, 40f)
      for (i in 0 until numOfRail){

        val railL = Global.getSettings().getSprite("aEP_FX","repair_scaffold_rail")
        val moveDist = i * 60f - 30f
        randomVel= MathUtils.getRandomPointInCircle(Misc.ZERO, 50f)
        randomAngleSpeed= MathUtils.getRandomNumberInRange(-30f,30f)
        randomLifeTimeLeft = MathUtils.getRandomNumberInRange(0.5f,1.5f)
        val railPositionL = aEP_Tool.getExtendedLocationFromPoint(
          topRailStartPoint, 180f + shipFacingOffset, expandLevel * moveDist
        )
        MagicRender.battlespace(
          railL,railPositionL,randomVel, Vector2f(20f,60f),Misc.ZERO,-90f + shipFacingOffset,randomAngleSpeed,
          Color.white,false,
          0f,randomLifeTimeLeft,0.5f)

        val railPositionR = aEP_Tool.getExtendedLocationFromPoint(
          topRailStartPoint, 0f + shipFacingOffset, expandLevel * moveDist
        )
        randomVel= MathUtils.getRandomPointInCircle(Misc.ZERO, 50f)
        randomAngleSpeed= MathUtils.getRandomNumberInRange(-30f,30f)
        randomLifeTimeLeft = MathUtils.getRandomNumberInRange(0.5f,1.5f)
        MagicRender.battlespace(
          railL,railPositionR,randomVel, Vector2f(20f,60f),Misc.ZERO,-90f + shipFacingOffset,randomAngleSpeed,
          Color.white,false,
          0f,randomLifeTimeLeft,0.5f)
      }

      val botRailStartPoint = aEP_Tool.getExtendedLocationFromPoint(blockCenter, -90f + shipFacingOffset, 40f)
      for (i in 0 until numOfRail){

        val railL = Global.getSettings().getSprite("aEP_FX","repair_scaffold_rail")
        val moveDist = i * 60f - 30f

        randomVel= MathUtils.getRandomPointInCircle(Misc.ZERO, 50f)
        randomAngleSpeed= MathUtils.getRandomNumberInRange(-30f,30f)
        randomLifeTimeLeft = MathUtils.getRandomNumberInRange(0.5f,1.5f)
        val railPositionL = aEP_Tool.getExtendedLocationFromPoint(
          botRailStartPoint, 180f + shipFacingOffset, expandLevel * moveDist
        )
        MagicRender.battlespace(
          railL,railPositionL,randomVel, Vector2f(20f,60f),Misc.ZERO,-90f + shipFacingOffset,randomAngleSpeed,
          Color.white,false,
          0f,randomLifeTimeLeft,0.5f)




        randomVel= MathUtils.getRandomPointInCircle(Misc.ZERO, 50f)
        randomAngleSpeed= MathUtils.getRandomNumberInRange(-30f,30f)
        randomLifeTimeLeft = MathUtils.getRandomNumberInRange(0.5f,1.5f)
        val railPositionR = aEP_Tool.getExtendedLocationFromPoint(
          botRailStartPoint, 0f + shipFacingOffset, expandLevel * moveDist
        )
        MagicRender.battlespace(
          railL,railPositionR,randomVel, Vector2f(20f,60f),Misc.ZERO,-90f + shipFacingOffset,randomAngleSpeed,
          Color.white,false,
          0f,randomLifeTimeLeft,0.5f)
      }


      randomVel= MathUtils.getRandomPointInCircle(Misc.ZERO, 50f)
      randomAngleSpeed= MathUtils.getRandomNumberInRange(-30f,30f)
      randomLifeTimeLeft = MathUtils.getRandomNumberInRange(0.5f,1.5f)
      MagicRender.battlespace(
        left,leftPosition, randomVel, Vector2f(20f,100f),Misc.ZERO,0f + shipFacingOffset,randomAngleSpeed,
        Color.white,false,
        0f,randomLifeTimeLeft,1f)

      randomVel= MathUtils.getRandomPointInCircle(Misc.ZERO, 50f)
      randomAngleSpeed= MathUtils.getRandomNumberInRange(-30f,30f)
      randomLifeTimeLeft = MathUtils.getRandomNumberInRange(0.5f,1.5f)
      MagicRender.battlespace(
        right,rightPosition,randomVel, Vector2f(20f,100f),Misc.ZERO,180f + shipFacingOffset,randomAngleSpeed,
        Color.white,false,
        0f,randomLifeTimeLeft,1f)

    }

  }

  fun createSpark(amount: Float, center:Vector2f){
    if(time < moveTime) return
    sparkTimer.advance(amount)
    if(sparkTimer.intervalElapsed()){
      val pos = MathUtils.getRandomPointInCircle(ship.location, ship.collisionRadius)
      aEP_Tool.spawnRepairSpark(ship.location,Misc.ZERO)
    }
  }

}
class RepairSideMoving(val moveTime : Float, val stayTime: Float, val ship:ShipAPI, var startLocRelativeData: Vector2f) : aEP_BaseCombatEffect((stayTime), ship){
  val sparkTimer = IntervalUtil(2f,6f)
  var xOffset = 0f
  var yOffset = 0f
  var shouldMove = moveTime
  var moved = 0f
  init {

  }

  override fun advanceImpl(amount: Float) {

    val level = (moved/shouldMove).coerceAtLeast(0f).coerceAtMost(1f)
    val fadeLevel = (time/3f).coerceAtMost(1f)
    val startLoc = aEP_Tool.getAbsoluteLocation(startLocRelativeData, ship,false)

    if(fadeLevel >= 1f){
      moved += amount
    }

    //addDebugPoint(startLoc)

    val shipFacingOffset = ship.facing - 90f
    val blockCenter = startLoc


    //先在0-0.5时竖直向下移动，0.5-1时再水平向右移动
    val verticalMoveLevel = MagicAnim.smooth(level * 2f).coerceAtMost(1f)
    val horizontalMoveLevel = MagicAnim.smooth((level-0.5f) * 2f).coerceAtLeast(0f)
    if(level < 0.5f){
      blockCenter.set(aEP_Tool.getExtendedLocationFromPoint(startLoc, -90f + shipFacingOffset, verticalMoveLevel * yOffset))
    }else{
      blockCenter.set(aEP_Tool.getExtendedLocationFromPoint(startLoc, -90f + shipFacingOffset, yOffset))
      blockCenter.set(aEP_Tool.getExtendedLocationFromPoint(startLoc, 0f + shipFacingOffset, horizontalMoveLevel * xOffset))
    }

    //生成移动贴图
    val drone = Global.getSettings().getSprite("aEP_FX","repair_scaffold_drone")
    MagicRender.singleframe(
      drone,blockCenter, Vector2f(100f,100f),0f + shipFacingOffset,
      Misc.setAlpha(Color.white, (255*fadeLevel).toInt()),false)


    //测试----------------------/


    //测试----------------------/

    if(moved >= shouldMove){
      val cellSize = ship.armorGrid.cellSize
      val x = ship.armorGrid.leftOf * cellSize
      val y = ship.armorGrid.above * cellSize

      val xLength = (ship.armorGrid.leftOf + ship.armorGrid.rightOf) * cellSize
      val yLength = (ship.armorGrid.above + ship.armorGrid.below) * cellSize

      startLocRelativeData = aEP_Tool.getRelativeLocationData(blockCenter, ship, false)
      val leftTopAbs = Vector2f.add(VectorUtils.rotate(Vector2f(-x,y),ship.facing-90f), ship.location,null)
      val blockCenter2leftTop = Vector2f.sub(leftTopAbs,blockCenter,null)
      VectorUtils.rotate(blockCenter2leftTop, -ship.facing+90f)

      val blockX2xOffset = abs(blockCenter2leftTop.x)
      val blockY2yOffset = abs(blockCenter2leftTop.y)
      val rightRange = xLength - blockX2xOffset
      val belowRange = yLength - blockY2yOffset
      xOffset = MathUtils.getRandomNumberInRange(-blockX2xOffset, rightRange)
      yOffset = MathUtils.getRandomNumberInRange(-blockY2yOffset, belowRange)

      aEP_Tool.spawnRepairSpark(blockCenter, Misc.ZERO)

      moved = 0f
      shouldMove = moveTime * MathUtils.getRandomNumberInRange(0.5f,1f)
    }


    //结束时散落
    if(time >= lifeTime){
      val drone = Global.getSettings().getSprite("aEP_FX","repair_scaffold_drone")
      var randomVel= MathUtils.getRandomPointInCircle(Misc.ZERO, 50f)
      var randomAngleSpeed= MathUtils.getRandomNumberInRange(-30f,30f)
      var randomLifeTimeLeft = MathUtils.getRandomNumberInRange(0.5f,2f)
      MagicRender.battlespace(
        drone,blockCenter,randomVel, Vector2f(100f,100f),Misc.ZERO,180f + shipFacingOffset,randomAngleSpeed,
        Color.white,false,
        0f,randomLifeTimeLeft,1f)
    }
  }

}

