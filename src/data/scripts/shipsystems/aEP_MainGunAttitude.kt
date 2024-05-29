package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.impl.combat.DamperFieldOmegaStats
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.JitterUtil
import combat.impl.VEs.aEP_MovingSmoke
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Combat
import combat.util.aEP_DataTool
import combat.util.aEP_Tool
import data.scripts.ai.aEP_DroneRepairShipAI
import data.scripts.weapons.aEP_DecoAnimation
import data.scripts.weapons.aEP_cru_pingding_main
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicAnim
import org.magiclib.util.MagicRender
import java.awt.Color
import java.util.*
import kotlin.math.pow

class aEP_MainGunAttitude : BaseShipSystemScript() {
  companion object{
    const val ID = "aEP_MainGunAttitude"

    const val ROF_BONUS_PERCENT = 100f

    const val RANGE_BONUS_PERCENT = 25f
    const val RANGE_BONUS_FLAT = 250f

    const val BREAK_RANGE = 450f
    const val FLUX_INCREASE_MULT = 0.50f

    val TAIL_SMOKE_COLOR = Color(255,255,255)

  }

  val smokeTimer = IntervalUtil(0.03f,0.03f)

  var leftLevel = 0f;
  var rightLevel = 0f;

  var amount = 0f

  //相对于舰船的角度
  var mainWeaponFacing = -999f
  var main:WeaponAPI? = null

  var fluxLastFrameSoft = -999f
  var fluxLastFrameHard = -999f

  val fixLoc = Vector2f(0f,0f)

  var didUse = false
  var didJitterDown = false

  val br1 = Global.getSettings().getSprite("aEP_FX","cru_pingding_br_a_1")
  val br2 = Global.getSettings().getSprite("aEP_FX","cru_pingding_br_a_2")

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴
    if (stats == null || stats.entity == null || stats.entity !is ShipAPI) return
    val ship = stats.entity as ShipAPI
    amount = aEP_Tool.getAmount(ship)

    //控制装饰武器
    openDeco(ship,effectLevel)

    //静止主武器开火
    if(effectLevel < 1f){
      for(w in ship.allWeapons){
        if(w.slot.id.equals("MAIN")){
          w.setForceNoFireOneFrame(true)
        }
      }
    }

    //关闭引擎
    val engineLevel = ((effectLevel)/0.2f).coerceAtMost(1f)
    ship.engineController.fadeToOtherColor(
      ID,
      Color(0, 0, 0, 0),
      Color(0, 0, 0, 0),
      engineLevel, 1f * engineLevel)

    //修改数值
    if(effectLevel <= 0f){

      if(didUse){
        //完全关闭时运行一帧
        didUse = false

        ship.mutableStats.maxSpeed.modifyMult(ID, 1f)
        ship.mutableStats.acceleration.modifyMult(ID, 1f)
        ship.mutableStats.deceleration.modifyMult(ID, 1f)

        ship.mutableStats.maxTurnRate.modifyPercent(ID, 1f)
        ship.mutableStats.turnAcceleration.modifyPercent(ID, 1f)

      }


    }else if(effectLevel <= 1f) {
      if(!didUse){
        //刚刚开启时的第一帧
        didUse = true
        didJitterDown = false

        ship.velocity.scale(0.1f)
        ship.angularVelocity *= 0.1f
        //记录固定的位置
        fixLoc.set(ship.location)
        //重置幅能记录
        fluxLastFrameSoft = ship.fluxTracker.currFlux-ship.fluxTracker.hardFlux
        fluxLastFrameHard = ship.fluxTracker.hardFlux

        //如果舰船系统正在启用，关闭
        if(ship.system.isActive) ship.system.deactivate()
      }

      if(!didJitterDown && state == ShipSystemStatsScript.State.OUT){
        didJitterDown = true

      }

      //开始激活时，持续激活的效果
      ship.mutableStats.maxSpeed.modifyMult(ID, 0.2f)
      ship.mutableStats.acceleration.modifyMult(ID, 0.5f)
      ship.mutableStats.deceleration.modifyMult(ID, 0.5f)
      if(effectLevel >= 1f){
        //只有在完全激活才增加的效果
        ship.mutableStats.maxTurnRate.modifyPercent(ID, 50f)
        ship.mutableStats.turnAcceleration.modifyPercent(ID, 100f)

      }

    }

  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    // never called due to runScriptWhileIdle:true in the .system file

    //复制粘贴
    if (stats == null || stats.entity == null || stats.entity !is ShipAPI) return
    val ship = stats.entity as ShipAPI
    amount = aEP_Tool.getAmount(ship)
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): StatusData? {
    if(effectLevel >= 0.25f){
      if (index == 0 && effectLevel >= 1f) {
        return ShipSystemStatsScript.StatusData(String.format(
          aEP_DataTool.txt("aEP_SiegeMode01"), String.format("-%.0f", (FLUX_INCREASE_MULT)*100f)+"%"),
          false)
      } else if (index == 1 && effectLevel >= 1f) {
        return ShipSystemStatsScript.StatusData(String.format(
          aEP_DataTool.txt("aEP_SiegeMode02") , String.format("+%.0f", ROF_BONUS_PERCENT) + "%"),
          false)
      } else if (index == 2 && effectLevel >= 1f) {
        return ShipSystemStatsScript.StatusData(String.format(
          aEP_DataTool.txt("aEP_SiegeMode03") , String.format("+%.0f +%.0f", RANGE_BONUS_FLAT, RANGE_BONUS_PERCENT) + "%"),
          false)
      } else if (index == 3) {
        return ShipSystemStatsScript.StatusData(
          aEP_DataTool.txt("aEP_SiegeMode04") ,
          true)
      }
    }


    return null
  }

  fun openDeco(ship: ShipAPI, effectLevel: Float) {
    val realLevel = effectLevel
    val effectLevel = (effectLevel*1.2f).coerceAtMost(1f)
    var layer = CombatEngineLayers.CRUISERS_LAYER

    //move deco weapon
    for (weapon in ship.allWeapons) {

      //控制大套筒
      if (weapon.spec.weaponId.equals("aEP_cru_pingding_barrel_sleeve_l1") ||
        weapon.spec.weaponId.equals("aEP_cru_pingding_barrel_sleeve_r1") ) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)

        if(effectLevel <= 0.3f){
          val convertedLevel =  ((effectLevel-0.15f)/0.15f).coerceAtMost(1f)
          controller.setMoveToSideLevel(convertedLevel)
        } else if(effectLevel <= 0.6f){
          var convertedLevel =  ((effectLevel-0.3f)/0.3f).coerceAtMost(1f)
          controller.setMoveToLevel(convertedLevel)

          convertedLevel =  ((effectLevel-0.5f)/0.3f).coerceAtMost(1f)
          controller.setRevoToLevel(convertedLevel)
        }else{
          controller.setMoveToLevel(1f)
          controller.setMoveToSideLevel(1f)
          controller.setRevoToLevel(1f)
        }
      }

      //控制小套筒
      if (weapon.spec.weaponId.equals("aEP_cru_pingding_barrel_sleeve_l2") ||
        weapon.spec.weaponId.equals("aEP_cru_pingding_barrel_sleeve_r2") ) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)

        if(effectLevel <= 0.2f){
          val convertedLevel =  ((effectLevel)/0.2f).coerceAtMost(1f)
          controller.setMoveToSideLevel(convertedLevel)
        }else if(effectLevel <= 0.6f){
          val convertedLevel =  ((effectLevel-0.2f)/0.4f).coerceAtMost(1f)
          controller.setMoveToLevel(convertedLevel)
        }else{
          controller.setMoveToLevel(1f)
          controller.setMoveToSideLevel(1f)
        }
      }

      //控制前方装甲
      if (weapon.spec.weaponId.equals("aEP_cru_pingding_armor_l1") ||
        weapon.spec.weaponId.equals("aEP_cru_pingding_armor_r1") ) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)

        if(effectLevel <= 0.1f){

        }else if(effectLevel <= 0.5f){
          val convertedLevel =  ((effectLevel-0.1f)/0.4f).coerceAtMost(1f)
          controller.setMoveToLevel(convertedLevel)
        }else if(effectLevel <= 1f){
          val convertedLevel =  ((effectLevel-0.5f)/0.3f).coerceAtMost(1f)
          controller.setMoveToSideLevel(convertedLevel)
          controller.setMoveToLevel(1f)
        }
      }

      //控制侧面装甲（辅助引擎）
      if (weapon.spec.weaponId.equals("aEP_cru_pingding_armor_l2") ||
        weapon.spec.weaponId.equals("aEP_cru_pingding_armor_r2") ) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)

        if(effectLevel <= 0.1f){

        }else if(effectLevel <= 0.5f){
          val convertedLevel =  ((effectLevel-0.1f)/0.4f).coerceAtMost(1f)
          controller.setMoveToSideLevel(convertedLevel)
          controller.setMoveToLevel(convertedLevel)
        }else if(effectLevel <= 1f){
          val convertedLevel =  ((effectLevel-0.5f)/0.3f).coerceAtMost(1f)
          controller.setRevoToLevel(convertedLevel)
          controller.setMoveToSideLevel(1f)
          controller.setMoveToLevel(1f)
        }

      }

      //控制尾部装甲收回
      if (weapon.spec.weaponId.equals("aEP_cru_pingding_tail_l1") ||
        weapon.spec.weaponId.equals("aEP_cru_pingding_tail_r1") ||
        weapon.spec.weaponId.equals("aEP_cru_pingding_tail_l2") ||
        weapon.spec.weaponId.equals("aEP_cru_pingding_tail_r2")) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)
        if(effectLevel <= 0.15f){
          val convertedLevel = MathUtils.clamp((effectLevel) / 0.15f, 0f, 1f)
          controller.setMoveToLevel(convertedLevel)
        }else if(effectLevel <= 0.3f){
          val convertedLevel =  ((effectLevel-0.15f)/0.15f).coerceAtMost(1f)
          controller.setMoveToSideLevel(convertedLevel)
        }else if(effectLevel <= 1f){
          val convertedLevel =  ((effectLevel-0.3f)/0.3f).coerceAtMost(1f)
          controller.setRevoToLevel(convertedLevel)
          controller.setMoveToSideLevel(1f)
        }

      }

      //尾部发光
      if (weapon.spec.weaponId.equals("aEP_des_chongji_glow_l") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_glow_r")) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)

        //控制旋转
        if(effectLevel <= 0.3f){
          controller.setRevoToLevel(0f)
        }else if(effectLevel <= 0.7f){
          val convertedLevel =  ((effectLevel-0.3f)/0.4f).coerceAtMost(1f)
          controller.setRevoToLevel(convertedLevel)
        } else if(effectLevel <= 1f){
          controller.setRevoToLevel(1f)
        }

        //控制侧移
        if(effectLevel <= 0.2f){
          controller.setMoveToSideLevel(0f)
        }else if(effectLevel <= 0.5f){
          val convertedLevel = ((effectLevel-0.2f)/0.3f).coerceAtMost(1f)
          controller.setMoveToSideLevel(convertedLevel)
        } else if(effectLevel <= 1f){
          controller.setMoveToSideLevel(1f)
        }

        //控制发光
        if(effectLevel <= 0.2f){
          controller.setGlowToLevel(0f)
        }else if(effectLevel <= 0.7f){
          controller.setGlowToLevel(0f)
        } else if(effectLevel <= 1f){
          controller.setGlowToLevel(1f)
        }
      }

    }

    //渲染脚架以及炮管
    val amount = aEP_Tool.getAmount(ship)
    var extendLevel = 0f
    extendLevel = MathUtils.clamp((effectLevel - 0.6f) / 0.4f, 0f, 1f)
    if (!aEP_Tool.isDead(ship)) {

      //计算左右喷气量
      if(ship.engineController.isTurningLeft) leftLevel += 4f * amount
      if(ship.engineController.isTurningRight) rightLevel += 4f * amount
      leftLevel -= 0.5f * amount
      rightLevel -= 0.5f * amount
      leftLevel = MathUtils.clamp(leftLevel,0f,1f)
      rightLevel = MathUtils.clamp(rightLevel,0f,1f)
      if(extendLevel < 0.9f){
        leftLevel = 0f
        rightLevel = 0f
      }else{
        smokeTimer.advance(amount)
        if(smokeTimer.intervalElapsed()){
          spraySmoke(ship)
        }
      }

      for (slot in ship.hullSpec.allWeaponSlotsCopy) {
        //寻找左右脚架位置并渲染
        if(slot.id.contains("FOOT_B_")) {
          val backL1 = Global.getSettings().getSprite("aEP_FX","cru_pingding_leg_b")
          val backL2 = Global.getSettings().getSprite("aEP_FX","cru_pingding_leg_f")
          var angle = slot.computeMidArcAngle(ship)
          val slotLoc = slot.computePosition(ship)
          //1是后面的，2是前面的
          val loc1 = aEP_Tool.getExtendedLocationFromPoint(slotLoc, angle, MagicAnim.smooth(extendLevel) * 24f)
          val loc2 = aEP_Tool.getExtendedLocationFromPoint(slotLoc, angle, MagicAnim.smooth(extendLevel) * 45f)
          //渲染的时候反过来，保证2先渲染在下面
          MagicRender.singleframe(backL2,loc2,Vector2f(backL2.width,backL2.height),angle,Color.white,false,layer)
          MagicRender.singleframe(backL1,loc1,Vector2f(backL1.width,backL1.height),angle,Color.white,false,layer)

          val headLoc = aEP_Tool.getExtendedLocationFromPoint(loc2, angle, backL2.width / 2f - 10f)
          val head = Global.getSettings().getSprite("aEP_FX","cru_pingding_leg_head")
          MagicRender.singleframe(head,headLoc,Vector2f(head.width,head.height),angle,Color.white,false,layer)
        }

      }


    }
  }

  fun spraySmoke(ship: ShipAPI){
    lateinit var locL1:Vector2f;
    var angleL1 =0f
    lateinit var locR1:Vector2f;
    var angleR1 =0f
    lateinit var locL2:Vector2f;
    var angleL2 =0f
    lateinit var locR2:Vector2f;
    var angleR2 =0f

    val smokeLifeTime = 0.3f
    val alpha = 0.6f

    for (slot in ship.hullSpec.allWeaponSlotsCopy) {
      if (slot.id.contains("FOOT_B_L")) {
        val angle = slot.computeMidArcAngle(ship)
        val slotLoc = slot.computePosition(ship)
        val loc2 = aEP_Tool.getExtendedLocationFromPoint(slotLoc, angle, 45f)

        angleL1 = angle-30f
        angleR1 = angle+30f
        locL1 = aEP_Tool.getExtendedLocationFromPoint(loc2, angleL1, 25f)
        locR1 = aEP_Tool.getExtendedLocationFromPoint(loc2, angleR1, 25f)
      } else if (slot.id.contains("FOOT_B_R")) {
        val angle = slot.computeMidArcAngle(ship)
        val slotLoc = slot.computePosition(ship)
        val loc2 = aEP_Tool.getExtendedLocationFromPoint(slotLoc, angle, 45f)

        angleL2 = angle-30f
        angleR2 = angle+30f
        locL2 = aEP_Tool.getExtendedLocationFromPoint(loc2, angleL2, 25f)
        locR2 = aEP_Tool.getExtendedLocationFromPoint(loc2, angleR2, 25f)
      }
    }

    //千万不能生成一个 lifeTime = 0的烟雾，会无限存在的
    //向左喷射
    if(leftLevel > 0f){
      var smoke = aEP_MovingSmoke(locL1)
      smoke.lifeTime = smokeLifeTime * leftLevel
      smoke.fadeIn = 0.5f
      smoke.fadeOut = 0.5f
      smoke.size = 10f
      smoke.sizeChangeSpeed = 60f
      smoke.color = aEP_Tool.getColorWithAlphaChange(TAIL_SMOKE_COLOR,leftLevel * alpha)
      smoke.setInitVel(aEP_Tool.speed2Velocity(angleL1, 300f))
      smoke.stopForceTimer.setInterval(0.05f, 0.05f)
      smoke.stopSpeed = 0.975f
      aEP_CombatEffectPlugin.addEffect(smoke)

      smoke = aEP_MovingSmoke(locL2)
      smoke.lifeTime = smokeLifeTime * leftLevel
      smoke.fadeIn = 0.5f
      smoke.fadeOut = 0.5f
      smoke.size = 10f
      smoke.sizeChangeSpeed = 60f
      smoke.color = aEP_Tool.getColorWithAlphaChange(TAIL_SMOKE_COLOR,leftLevel * alpha)
      smoke.setInitVel(aEP_Tool.speed2Velocity(angleL2, 300f))
      smoke.stopForceTimer.setInterval(0.05f, 0.05f)
      smoke.stopSpeed = 0.975f
      aEP_CombatEffectPlugin.addEffect(smoke)
    }
    if(rightLevel > 0f){
      //向右喷射
      var smoke = aEP_MovingSmoke(locR1)
      smoke.lifeTime = smokeLifeTime * rightLevel
      smoke.fadeIn = 0.5f
      smoke.fadeOut = 0.5f
      smoke.size = 10f
      smoke.sizeChangeSpeed = 60f
      smoke.color = aEP_Tool.getColorWithAlphaChange(TAIL_SMOKE_COLOR,rightLevel * alpha)
      smoke.setInitVel(aEP_Tool.speed2Velocity(angleR1, 300f))
      smoke.stopForceTimer.setInterval(0.05f, 0.05f)
      smoke.stopSpeed = 0.975f
      aEP_CombatEffectPlugin.addEffect(smoke)

      smoke = aEP_MovingSmoke(locR2)
      smoke.lifeTime = smokeLifeTime * rightLevel
      smoke.fadeIn = 0.5f
      smoke.fadeOut = 0.5f
      smoke.size = 10f
      smoke.sizeChangeSpeed = 60f
      smoke.color = aEP_Tool.getColorWithAlphaChange(TAIL_SMOKE_COLOR,rightLevel * alpha)
      smoke.setInitVel(aEP_Tool.speed2Velocity(angleR2, 300f))
      smoke.stopForceTimer.setInterval(0.05f, 0.05f)
      smoke.stopSpeed = 0.975f
      aEP_CombatEffectPlugin.addEffect(smoke)
    }

  }


}