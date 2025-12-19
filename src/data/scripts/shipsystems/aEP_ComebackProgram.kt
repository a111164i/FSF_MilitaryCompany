package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.impl.combat.RecallDeviceStats
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Combat
import combat.util.aEP_DataTool.txt
import combat.util.aEP_Tool
import data.scripts.hullmods.*
import data.scripts.shipsystems.aEP_Rupture.Companion.JITTER_COLOR
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

class aEP_ComebackProgram:  BaseShipSystemScript(){

  companion object{
    const val ID = "aEP_ComebackProgram"
    const val SLOT_ID = "CENTER"

    const val TEL_TIME = 0.5f
    const val SYSTEM_RANGE = 6000f

    const val TEL_KEY = "aEP_ComebackProgramTelTarget"

    const val EXTRA_COOLDOWN = 20f

    const val REPAIR_TIME_BASE = 10f
    const val REPAIR_TIME_PER_DP = 1f

    private val VALID_TARGET_LIST: MutableMap<String, Float> = HashMap()
    init {
      VALID_TARGET_LIST["aEP_fga_xiliu"] = 1f
      VALID_TARGET_LIST["aEP_fga_xiliu_mk2"] = 1f
      VALID_TARGET_LIST["aEP_fga_raoliu"] = 1f
      VALID_TARGET_LIST["aEP_fga_wanliu"] = 1f
      VALID_TARGET_LIST["aEP_fga_yonglang"] = 1f
    }

    fun checkIsShipValidLite(target:ShipAPI?, ship: ShipAPI): Boolean{
      //非空，是活着，是友军，CR足够，距离足够，是护卫，属于VALID_LIST，当前并不在被其他人传送或者维修
      if(target is ShipAPI
        && !aEP_Tool.isDead(target)
        && target.owner == ship.owner
        && target.currentCR > 0.4f
        && ship.currentCR > 0.4f
        && MathUtils.getDistanceSquared(ship, target.location) <= (aEP_Tool.getSystemRange(ship,SYSTEM_RANGE)).pow(2)
        && target.isFrigate
        && !target.isPhased
        && target.collisionClass != CollisionClass.NONE
        && !target.isStation
        && !target.isDrone
        && target.variant.hasHullMod(aEP_SpecialHull.ID)
        && VALID_TARGET_LIST.containsKey(target.hullSpec.baseHullId)
        && !target.customData.containsKey(TEL_KEY)
        && !target.customData.containsKey(aEP_EmergencyReconstruct.ACTIVE_KEY)){
        return true
      }

      return false
    }

    fun computeRepairTime(target: ShipAPI): Float{
      val dp =  target.mutableStats.dynamic.getMod(Stats.DEPLOYMENT_POINTS_MOD).computeEffective(target.hullSpec.suppliesToRecover)
      val repairTime = REPAIR_TIME_BASE + REPAIR_TIME_PER_DP * dp
      return repairTime
    }
  }

  var target: ShipAPI? = null
  //这2个handle用来指示系统左下角的信息，及其在当前目标维修结束以前，不可以激活系统
  var repairClassHandle : Reconstruct? = null
  var telClassHandle : Tel? = null

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴
    val ship = (stats.entity?:return) as ShipAPI

    updateIndicators(ship)

    target = (ship.shipTarget)
    //考虑模块的情况
    if(target is ShipAPI && !aEP_Tool.isDead(ship.shipTarget)){
      //进到这里面说明目标首先是个存活的舰船
      //如果目标是个模块，记录下模块为m，然后把target重新改为null
      val m = target as ShipAPI
      if(m.isStationModule){
        target = null
        //如果目标模块拥有存活的母舰，把母舰改为新目标
        if(m.parentStation is ShipAPI && !aEP_Tool.isDead(m.parentStation)){
          target = m.parentStation
        }
      }
    }

    //检测当前的2个handle是否还有效，如果已经超时自动移除
    //如果不为null，说明有一个船正在进行维修，把cooldown一直加满，此时不允许系统冷却，出去
    //如果碰到刚好移除handle的一帧，可以根据不同目标，在基础冷却上再增加额外的cooldown
    if(telClassHandle != null){
      if(telClassHandle?.shouldEnd == true){
        telClassHandle = null
      }else{
        ship.system.cooldownRemaining = ship.system.cooldown
        return
      }
    }
    if(repairClassHandle != null){
      if(repairClassHandle?.didEnd == true){
        repairClassHandle = null
        ship.system.cooldownRemaining += 1f
      }else{
        ship.system.cooldownRemaining = ship.system.cooldown
        return
      }
    }


    if(effectLevel >= 1f){
      //检测目标是否有效
      if(!checkIsShipValid(target, ship)) target = null
      //如果检测之后是null，不继续运行
      target?: return

      //在之前的checkIsShipValid()时已经检测过是否拥有TEL_KEY，所以这里不需要检测第二次
      //在初始化时会自动添加移除TEL_KEY
      val arcPoint = ship.hullSpec.getWeaponSlot("CENTER").computePosition(ship)
      val params = EmpArcParams()
      //			params.segmentLengthMult = 4f;
      //			params.zigZagReductionFactor = 0.25f;
      //			params.fadeOutDist = 200f;
      //			params.minFadeOutMult = 2f;
      params.segmentLengthMult = 16f
      params.zigZagReductionFactor = 0.15f
      params.brightSpotFullFraction = 0.5f
      params.brightSpotFadeFraction = 0.5f
      //params.nonBrrightSpotMinBrightness = 0.25f;
      val dist: Float = Misc.getDistance(arcPoint, ship.shipTarget.location)
      params.flickerRateMult = 0.2f -  0.1f * dist / 4000f
      if (params.flickerRateMult < 0.1f) {
        params.flickerRateMult = 0.1f
      }
      val arc = Global.getCombatEngine().spawnEmpArcVisual(
        ship.shipTarget.location, ship.shipTarget ,
        arcPoint, ship,
        80f,RecallDeviceStats.JITTER_COLOR, Color.white,
        params)
      //arc.setFadedOutAtStart(true);
      //arc.setRenderGlowAtStart(false);
      arc.setSingleFlickerMode(true)

      aEP_CombatEffectPlugin.addEffect(Tel(TEL_TIME, target as ShipAPI, ship.hullSpec.getWeaponSlot(SLOT_ID), ship))
    }
  }

  override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {


    if(checkIsShipValid(ship.shipTarget, ship)){
      return true
    }

    return false
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String {

    if (telClassHandle != null){
      return "Teleporting: " + String.format("%.2f",telClassHandle!!.lifeTime-telClassHandle!!.time)
    }
    if (repairClassHandle != null){
      return "Repairing: " + String.format("%.0f",repairClassHandle!!.reconstructTimer)
    }
    //当前无目标的情况可以先划出去
    if(target == null){
      return "Need Ship Target"
    }
    val target = target as ShipAPI

    //检测距离够不够
    val dist = aEP_Tool.checkTargetWithinSystemRange(ship,target.location, SYSTEM_RANGE, target.collisionRadius)
    if (dist > 0f){
      val rounded =  ((dist / 50f).roundToInt() + 1 ) * 50
      return "Out of Range: $rounded"
    }

    if (!target.isFrigate){
      return txt("aEP_ComebackProgram01")
    }
    if (!target.variant.hasHullMod(aEP_SpecialHull.ID)){
      return txt("aEP_ComebackProgram02")
    }
    if (!VALID_TARGET_LIST.containsKey(target.hullSpec.baseHullId)){
      return txt("aEP_ComebackProgram03")
    }
    if (target.currentCR <= 0.4f || ship.currentCR <= 0.4f){
      return txt("aEP_ComebackProgram04")
    }

    if(checkIsShipValid(target, ship)){
      return "Ready"
    } else{
      return "Invalid Ship Target"
    }

  }

  fun checkIsShipValid(target:ShipAPI?, ship: ShipAPI): Boolean{
    //非空，是活着，是友军，CR足够，距离足够，是护卫，属于VALID_LIST，当前并不在被其他人传送或者维修
    if(target is ShipAPI
      && !aEP_Tool.isDead(target)
      && target.owner == ship.owner
      && target.currentCR > 0.4f
      && ship.currentCR > 0.4f
      && MathUtils.getDistanceSquared(ship, target.location) <= (aEP_Tool.getSystemRange(ship,SYSTEM_RANGE)).pow(2)
      && target.isFrigate
      && !target.isPhased
      && target.collisionClass != CollisionClass.NONE
      && !target.isStation
      && !target.isDrone
      && target.variant.hasHullMod(aEP_SpecialHull.ID)
      && VALID_TARGET_LIST.containsKey(target.hullSpec.baseHullId)
      && telClassHandle == null
      && repairClassHandle == null
      && !target.customData.containsKey(TEL_KEY)
      && !target.customData.containsKey(aEP_EmergencyReconstruct.ACTIVE_KEY)){
      return true
    }

    return false
  }

  fun updateIndicators(ship: ShipAPI){
    var l1 : aEP_DecoAnimation? = null
    var l2 : aEP_DecoAnimation? = null
    var l3 : aEP_DecoAnimation? = null
    var l4 : aEP_DecoAnimation? = null
    var l5 : aEP_DecoAnimation? = null
    var l6 : aEP_DecoAnimation? = null

    for(w in ship.allWeapons){
      if(w.slot.id.equals("ID_L1")) l1 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_L2")) l2 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_L3")) l3 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_L4")) l4 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_L5")) l5 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_L6")) l6 = w.effectPlugin as aEP_DecoAnimation
    }

    l1?:return
    l2?:return
    l3?:return
    l4?:return
    l5?:return
    l6?:return

    //0是全红，1是全绿
    var level = MathUtils.clamp(ship.system.cooldownRemaining/ship.system.cooldown, 0f,1f)
    level = 1f - level
    if(level < 0.5f){
      val converted = (0.5f-level) * 2f
      //12绿，34红
      l1.setGlowToLevel(1f-converted)
      l3.setGlowToLevel(converted)

      l2.setGlowToLevel(0f)
      l4.setGlowToLevel(1f)
    }else{
      val converted = (level - 0.5f) * 2f
      //12绿，34红
      l1.setGlowToLevel(1f)
      l3.setGlowToLevel(0f)

      l2.setGlowToLevel(converted)
      l4.setGlowToLevel(1f-converted)
    }

    //5是绿，6是红
    if(level < 1f){
      l5.setGlowToLevel(level * 0.5f)
      l6.setGlowToLevel(1f - level * 0.5f)
    }else{
      l5.setGlowToLevel(1f)
      l6.setGlowToLevel(0f)
    }




  }

  inner class Tel(lifeTime:Float, val ship:ShipAPI,val slot:WeaponSlotAPI,val parent: ShipAPI)
    : aEP_Combat.StandardTeleport(lifeTime, ship, slot.computePosition(parent),parent.facing){

    init {
      ship.setCustomData(aEP_ComebackProgram.TEL_KEY,1f)
      telClassHandle = this
    }
    override fun advanceImpl(amount: Float) {
      if(aEP_Tool.isDead(parent)){
        shouldEnd = true
        return
      }
      toLoc.set(Vector2f(slot.computePosition(parent)))
    }

    override fun readyToEnd() {
      super.readyToEnd()
      ship.removeCustomData(aEP_ComebackProgram.TEL_KEY)
    }

    override fun onTel() {
      if(!aEP_Tool.isDead(parent)){
        val repairTime = computeRepairTime(ship)
        val e = Reconstruct(repairTime,ship, parent, slot)

        ship.addListener(e)
      }
    }
  }
  inner class Reconstruct(val repairTimeTotal: Float, val ship: ShipAPI, val parent: ShipAPI, val attachToSlot: WeaponSlotAPI): HullDamageAboutToBeTakenListener, AdvanceableListener {

    //这些变量都是作为listener时使用的
    val repairTimer = IntervalUtil(0.2f,0.8f)
    var reconstructTimer = repairTimeTotal
    var didRepair = false
    //用于给handle指示应该移除自己了
    var didEnd = false

    init {
      //不会重复施加给在已经通过其他途径激活重建的船
      if(!ship.customData.containsKey(aEP_EmergencyReconstruct.ACTIVE_KEY)){
        ship.setCustomData(aEP_EmergencyReconstruct.ACTIVE_KEY,true)
        onceWhenInRepair()
        repairClassHandle = this
      }else{
        //因为直接把这个设置为0了，从来没跑过duringRepairEffect()，那么didRepair也是false，所以onceWhenOutRepair()也不会跑
        //第一次运行advance的时候直接自我终结
        reconstructTimer = 0f
      }

    }

    override fun advance(amount: Float) {

      reconstructTimer -= amount
      reconstructTimer.coerceAtLeast(0f).coerceAtMost(99f)
      //维修模式时
      if(reconstructTimer > 0f){
        duringRepairEffect(amount)
      }else if( didRepair ){
        //退出维修模式时运行一次
        onceWhenOutRepair()
        didRepair = false
      }


      //如果中途本舰阵亡，或者走完了维修流程，立刻解除listener，类似readyToEnd()，在这里移除ACTIVE_KEY
      //流程一旦开始，母舰炸了也无法停止，只是不在锁在母舰的装饰武器的位置
      //如果母舰还存活，同步位置和朝向
      if(!aEP_Tool.isDead(parent) && !aEP_Tool.isDead(ship)){
        ship.facing = parent.facing
        ship.location.set(attachToSlot.computePosition(parent))
        ship.velocity.set(Misc.ZERO)
      }
      //这里是唯一自我终结的出口，严格把控，所有情况都要从这里出去
      //如果中途本舰阵亡，或者走完了维修流程，立刻解除listener，类似readyToEnd()，在这里移除ACTIVE_KEY
      //流程一旦开始，母舰炸了也无法停止，只是不在锁在母舰的装饰武器的位置
      if(aEP_Tool.isDead(ship) || reconstructTimer <= 0f ){
        //用于走出队友的碰撞圈
        ship.velocity.set(aEP_Tool.speed2Velocity(ship.facing,120f))
        aEP_CombatEffectPlugin.addEffect(NoCollision(ship))
        ship.removeCustomData(aEP_EmergencyReconstruct.ACTIVE_KEY)
        ship.removeListener(this)
        didEnd = true
      }

      //addDebugLog(reconstructTime.toString())
    }

    //if true is returned, the hull damage to be taken is negated.
    override fun notifyAboutToTakeHullDamage(param: Any?, ship: ShipAPI, point: Vector2f, damageAmount: Float): Boolean {
      if(reconstructTimer > 0f) return true
      return false
    }

    open fun duringRepairEffect(amount: Float){
      didRepair = true
      ship.mutableStats.hullDamageTakenMult.modifyMult(aEP_EmergencyReconstruct.ID, 0f)
      ship.mutableStats.armorDamageTakenMult.modifyMult(aEP_EmergencyReconstruct.ID, 0f)
      ship.mutableStats.combatWeaponRepairTimeMult.modifyFlat(aEP_EmergencyReconstruct.ID, Float.MAX_VALUE)
      ship.mutableStats.combatEngineRepairTimeMult.modifyFlat(aEP_EmergencyReconstruct.ID, Float.MAX_VALUE)
      ship.mutableStats.engineDamageTakenMult.modifyMult(aEP_EmergencyReconstruct.ID, 0f)

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
      ship.fadeToColor(aEP_EmergencyReconstruct.ID, Color(75, 75, 75, 255), 1f, 1f, level)
      ship.isJitterShields = false
      ship.setCircularJitter(true)
      ship.setJitterUnder(aEP_EmergencyReconstruct.ID, aEP_Tool.REPAIR_COLOR2, level, 36, 8f+ship.collisionRadius*0.1f)

      //相位效果
      ship.extraAlphaMult = level * 0.5f + 0.5f
      ship.extraAlphaMult2 = level * 0.5f + 0.5f
      ship.setApplyExtraAlphaToEngines(true)

      if(ship.phaseCloak != null && ship.phaseCloak.isActive) ship.phaseCloak.deactivate()
      if(ship.shield != null && ship.shield.isOn) ship.shield.toggleOff()
      ship.isPhased = true
      ship.collisionClass = CollisionClass.NONE


      repairTimer.advance(amount)
      if(repairTimer.intervalElapsed()){
        ship.velocity.scale(0.75f)
        ship.angularVelocity *= 0.75f
        val repaired = aEP_Tool.findToRepair(
          ship,
          ((ship.armorGrid.maxArmorInCell * (ship.armorGrid.grid.size * ship.armorGrid.grid[0].size) + ship.maxHitpoints) * 0.5f)/ repairTimeTotal,
          1f, 1f, 100f, 1f, true
        )
        if(repaired >= 1f){
          //reconstructTime = 0.1f
        }
      }
    }

    open fun onceWhenOutRepair(){

      ship.mutableStats.hullDamageTakenMult.unmodify(aEP_EmergencyReconstruct.ID)
      ship.mutableStats.armorDamageTakenMult.unmodify(aEP_EmergencyReconstruct.ID)
      ship.mutableStats.combatWeaponRepairTimeMult.unmodify(aEP_EmergencyReconstruct.ID)
      ship.mutableStats.combatEngineRepairTimeMult.unmodify(aEP_EmergencyReconstruct.ID)
      ship.mutableStats.engineDamageTakenMult.unmodify(aEP_EmergencyReconstruct.ID)

      ship.removeCustomData(aEP_EmergencyReconstruct.ACTIVE_KEY)

      for(e in ship.engineController.shipEngines){
        if(e.isDisabled){
          //e.disable(false)
          e.repair()
        }
      }
      for(w in ship.allWeapons){
        if(w.isDisabled) {
        //  w.disable(false)
          w.repair()
        }
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

      val xLength = 20f
      val xNum =   1
      val xStep = 50f

      val yNum =   1
      val yStep = 80f


      for(i in 0..yNum){
        val yMoveOffset = (yNum/2f - i) * yStep
        val yTime = abs(yMoveOffset/yStep)
        for(j in 0..xNum){
          val xOffset = (xNum/2f - j) * xStep
          val xTime = abs(xOffset/xStep) + yTime
          //渲染x支架
          aEP_CombatEffectPlugin.addEffect(
            RepairScaffoldUp(
              xTime,1f, reconstructTimer - i * 0.5f,
              ship, xOffset, yMoveOffset, centerRel)
          )
        }
        //渲染水平支架，后于x支架，要盖在上面
        aEP_CombatEffectPlugin.addEffect(
          RepairSideFrameUp(
            yTime,  xNum/2f, reconstructTimer - i * 0.5f,ship,
            0f,yMoveOffset,(xLength+scaffoldSize)/2f,centerRel)
        )
      }

      //var i = xLength * yLength
      //while (i > 0){
      //i -= scaffoldSize * scaffoldSize * 10
      //aEP_CombatEffectPlugin.addEffect(RepairSideMoving(1f,reconstructTimer - yNum * 0.5f,ship,centerRel))
      //}
    }


  }

}

class NoCollision(val ship: ShipAPI) : aEP_BaseCombatEffect(5f,ship){
  init {
    ship.collisionClass = CollisionClass.NONE
  }

  override fun advanceImpl(amount: Float) {
    ship.collisionClass = CollisionClass.NONE
    ship.blockCommandForOneFrame(ShipCommand.DECELERATE)
  }

  override fun readyToEnd() {
    ship.collisionClass = CollisionClass.SHIP
  }
}