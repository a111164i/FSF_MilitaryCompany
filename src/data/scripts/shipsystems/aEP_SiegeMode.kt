package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.impl.combat.DamperFieldOmegaStats
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.api.util.JitterUtil
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Combat
import combat.util.aEP_DataTool
import combat.util.aEP_Tool
import data.scripts.ai.aEP_DroneRepairShipAI
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicAnim
import org.magiclib.util.MagicRender
import java.awt.Color
import java.util.*
import kotlin.math.pow

class aEP_SiegeMode : BaseShipSystemScript() {
  companion object{
    const val ID = "aEP_SiegeMode"

    const val ROF_BONUS_PERCENT = 100f

    const val RANGE_BONUS_PERCENT = 25f
    const val RANGE_BONUS_FLAT = 250f

    const val BREAK_RANGE = 450f
    const val FLUX_INCREASE_MULT = 0.50f

  }


  var amount = 0f

  //相对于舰船的角度
  var mainWeaponFacing = -999f
  var main:WeaponAPI? = null

  var fluxLastFrameSoft = -999f
  var fluxLastFrameHard = -999f

  val fixLoc = Vector2f(0f,0f)

  var didUse = false
  var didJitterDown = false

  val rodSpriteL = Global.getSettings().getSprite("aEP_FX","des_chongji_rod")
  val ringSpriteL = Global.getSettings().getSprite("aEP_FX","des_chongji_ring")
  val ringSpriteL2 = Global.getSettings().getSprite("aEP_FX","des_chongji_ring2")
  val rodSpriteR = Global.getSettings().getSprite("aEP_FX","des_chongji_rod")
  val ringSpriteR = Global.getSettings().getSprite("aEP_FX","des_chongji_ring")
  val ringSpriteR2 = Global.getSettings().getSprite("aEP_FX","des_chongji_ring2")

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴
    if (stats == null || stats.entity == null || stats.entity !is ShipAPI) return
    val ship = stats.entity as ShipAPI
    amount = aEP_Tool.getAmount(ship)

    //如果处于一定充能状态，开始搜寻主武器，准备旋转主武器
    if( effectLevel > 0.1f && effectLevel < 0.9f ){

      if (didUse == true)


      //第一次搜寻到时，记录当时相对船头的角度
        if(mainWeaponFacing < -361f){
          for(w in ship.allWeapons){
            if(w.slot.id.equals("WS 001")){
              mainWeaponFacing = w.currAngle
              main = w
            }
          }
        }
    }else{
      main = null
      mainWeaponFacing = -999f
    }

    //如果搜寻成功，开始旋转主武器
    ship.mutableStats.weaponTurnRateBonus.modifyFlat(ID,0f)
    ship.mutableStats.maxTurnRate.modifyFlat(ID,0f)
    main?.run {
      var rotateLevel = 0f
      rotateLevel = ((effectLevel-0.05f)/0.95f).coerceAtMost(1f)
      rotateLevel = MagicAnim.smooth(rotateLevel)
      if(rotateLevel<1f && rotateLevel>0f){
        ship.mutableStats.weaponTurnRateBonus.modifyFlat(ID,-1.0E9f)
        ship.mutableStats.maxTurnRate.modifyFlat(ID,-1.0E9f)
      }
      if(state == ShipSystemStatsScript.State.OUT) rotateLevel = (1f-rotateLevel)
      //搁置，不转了

      main!!.currAngle = (ship.facing + (mainWeaponFacing-ship.facing) * (1f-rotateLevel))
    }


    //控制装饰武器
    openDeco(ship,effectLevel)

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

        ship.mutableStats.maxTurnRate.modifyMult(ID, 1f)
        ship.mutableStats.turnAcceleration.modifyMult(ID, 1f)

        ship.mutableStats.weaponTurnRateBonus.modifyFlat(ID,0f)

        ship.mutableStats.ballisticRoFMult.modifyPercent(ID, 0f)
        ship.mutableStats.energyRoFMult.modifyPercent(ID, 0f)

        ship.mutableStats.ballisticWeaponRangeBonus.modifyFlat(ID, 0f)
        ship.mutableStats.energyWeaponRangeBonus.modifyFlat(ID, 0f)
        ship.mutableStats.ballisticWeaponRangeBonus.modifyPercent(ID, 0f)
        ship.mutableStats.energyWeaponRangeBonus.modifyPercent(ID, 0f)

        ship.setWeaponGlow(
          0f,
          aEP_CoordinatedCombat.WEAPON_BOOST,
          EnumSet.of(WeaponAPI.WeaponType.BALLISTIC,WeaponAPI.WeaponType.ENERGY))
      }


    }else if(effectLevel <= 1f){
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

        //加一个抖动，表示开启系统
        val jitter = aEP_Combat.AddJitterBlink(0.1f,0.2f, 0.5f,ship)
        jitter.color = aEP_CoordinatedCombat.WEAPON_BOOST
        jitter.maxRange = 10f
        jitter.maxRangePercent = 0f
        jitter.copyNum = 2
        jitter.jitterShield = false
      }

      if(!didJitterDown && state == ShipSystemStatsScript.State.OUT){
        didJitterDown = true

        //加一个抖动，表示关闭系统
        val jitter = aEP_Combat.AddJitterBlink(0.1f,0.2f, 0.5f,ship)
        jitter.color = aEP_CoordinatedCombat.WEAPON_BOOST
        jitter.maxRange = 10f
        jitter.maxRangePercent = 0f
        jitter.copyNum = 2
        jitter.jitterShield = false
      }

      //检测距离，如果太远强制关闭
      //如果玩家在手操，显示落点
      val distSq = MathUtils.getDistanceSquared(ship.location, fixLoc)
      val breakRange = BREAK_RANGE.pow(2)

//      if(Global.getCombatEngine().playerShip == ship && state != ShipSystemStatsScript.State.OUT) {
//        val sprite = Global.getSettings().getSprite("graphics/aEP_FX/frame.png")
//        val farLevel = MathUtils.clamp(distSq/(breakRange*0.7f),0f,1f)
//        val size = 40f + 40f * farLevel
//        MagicRender.singleframe(
//          sprite, fixLoc,
//          Vector2f(size, size),  -90f + 720f * farLevel,
//          aEP_Tool.getColorWithAlpha(Color(farLevel,1f-farLevel,0.1f), farLevel * 0.6f),
//          true)
//
//      }
//      if(distSq > breakRange){
//        aEP_Tool.toggleSystemControl(ship,false)
//      }

      //读取记录，减少幅能
      val soft = ship.fluxTracker.currFlux-ship.fluxTracker.hardFlux
      val hard = ship.fluxTracker.hardFlux
      if(soft > fluxLastFrameSoft){
        val increase = soft - fluxLastFrameSoft
        ship.fluxTracker.decreaseFlux(increase* FLUX_INCREASE_MULT)
      }
      if(hard > fluxLastFrameHard){
        val increase = hard - fluxLastFrameHard
        ship.fluxTracker.decreaseFlux(increase* FLUX_INCREASE_MULT)
      }
      fluxLastFrameSoft = ship.fluxTracker.currFlux-ship.fluxTracker.hardFlux
      fluxLastFrameHard = ship.fluxTracker.hardFlux


      ship.mutableStats.maxSpeed.modifyMult(ID, 0.2f)
      ship.mutableStats.acceleration.modifyMult(ID, 0.5f)
      ship.mutableStats.deceleration.modifyMult(ID, 0.5f)

      ship.mutableStats.maxTurnRate.modifyMult(ID, 0.2f)
      ship.mutableStats.turnAcceleration.modifyMult(ID, 0.5f)

      //只有在完全激活才增加的效果
      if(effectLevel >= 1f){
        ship.mutableStats.weaponTurnRateBonus.modifyFlat(ID,10f)

        ship.mutableStats.ballisticRoFMult.modifyPercent(ID, ROF_BONUS_PERCENT)
        ship.mutableStats.energyRoFMult.modifyPercent(ID, ROF_BONUS_PERCENT)

        ship.mutableStats.ballisticWeaponRangeBonus.modifyFlat(ID, RANGE_BONUS_FLAT)
        ship.mutableStats.energyWeaponRangeBonus.modifyFlat(ID, RANGE_BONUS_FLAT)
        ship.mutableStats.ballisticWeaponRangeBonus.modifyPercent(ID, RANGE_BONUS_PERCENT)
        ship.mutableStats.energyWeaponRangeBonus.modifyPercent(ID, RANGE_BONUS_PERCENT)

        ship.setWeaponGlow(
          1f,
          aEP_CoordinatedCombat.WEAPON_BOOST,
          EnumSet.of(WeaponAPI.WeaponType.BALLISTIC,WeaponAPI.WeaponType.ENERGY))

      }else{
        //开始关闭时，立刻关闭武器发光
        ship.setWeaponGlow(
          0f,
          aEP_CoordinatedCombat.WEAPON_BOOST,
          EnumSet.of(WeaponAPI.WeaponType.BALLISTIC,WeaponAPI.WeaponType.ENERGY))

        ship.mutableStats.ballisticRoFMult.modifyPercent(ID, 0f)
        ship.mutableStats.energyRoFMult.modifyPercent(ID, 0f)

        ship.mutableStats.ballisticWeaponRangeBonus.modifyFlat(ID, 0f)
        ship.mutableStats.energyWeaponRangeBonus.modifyFlat(ID, 0f)
        ship.mutableStats.ballisticWeaponRangeBonus.modifyPercent(ID, 0f)
        ship.mutableStats.energyWeaponRangeBonus.modifyPercent(ID, 0f)
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
    //move deco weapon
    for (weapon in ship.allWeapons) {
      //旋转独立控制，一次控制所有的武器
      if (weapon.spec.weaponId.equals("aEP_des_chongji_armor_tl") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_tr") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_tl2") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_tr2") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_bl") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_br") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_bl2") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_br2") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_bl3") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_br3") ) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)
        if(effectLevel <= 0.3f){
          controller.setRevoToLevel(0f)
        }else if(effectLevel <= 0.7f){
          val convertedLevel =  ((effectLevel-0.3f)/0.4f).coerceAtMost(1f)
          controller.setRevoToLevel(convertedLevel)
        } else if(effectLevel <= 1f){
          controller.setRevoToLevel(1f)
        }
      }

      //上部的2根突起
      if (weapon.spec.weaponId.equals("aEP_des_chongji_armor_tl2") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_tr2")) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)
        if(effectLevel <= 0f){
          controller.setMoveToLevel(0f)
        }else if(effectLevel <= 0.1f){
          val convertedLevel = ((effectLevel-0f)/0.1f).coerceAtMost(1f)
          controller.setMoveToLevel(convertedLevel)
        } else if(effectLevel <= 1f){
          controller.setMoveToLevel(1f)
        }
      }

      //下部的 侧面开裂侧移
      if (weapon.spec.weaponId.equals("aEP_des_chongji_armor_bl2") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_br2")) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)
        if(effectLevel <= 0.1f){
          controller.setMoveToSideLevel(0f)
        }else if(effectLevel <= 0.3f){
          val convertedLevel = ((effectLevel-0.1f)/0.2f).coerceAtMost(1f)
          controller.setMoveToSideLevel(convertedLevel)
        } else if(effectLevel <= 1f){
          controller.setMoveToSideLevel(1f)
        }
      }

      //下部的后方排气管
      if (weapon.spec.weaponId.equals("aEP_des_chongji_armor_bl3") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_armor_br3")) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)
        if(effectLevel <= 0.8f){
          controller.setMoveToLevel(0f)
        }else if(effectLevel <= 0.9f){
          val convertedLevel = ((effectLevel-0.8f)/0.1f).coerceAtMost(1f)
          controller.setMoveToLevel(convertedLevel)
        } else if(effectLevel <= 1f){
          controller.setMoveToLevel(1f)
        }
      }

      //顶部发光
      if (weapon.spec.weaponId.equals("aEP_des_chongji_glow_f") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_glow_l") ||
        weapon.spec.weaponId.equals("aEP_des_chongji_glow_r") ) {
        val controller = (weapon.effectPlugin as aEP_DecoAnimation)
        if(effectLevel <= 0.2f){
          controller.setGlowToLevel(0f)
        }else if(effectLevel <= 0.7f){
          controller.setGlowToLevel(0f)
        } else if(effectLevel <= 1f){
          controller.setGlowToLevel(1f)
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

    //控制 侧面支架
    if(effectLevel <= 0.6f){
      renderSideSupport(ship, 0f)
    }else if(effectLevel <= 0.9f){
      val convertedLevel =  ((effectLevel-0.6f)/0.3f).coerceAtMost(1f)
      renderSideSupport(ship, convertedLevel)
    } else if(effectLevel <= 1f){
      renderSideSupport(ship, 1f)
    }

  }

  fun renderSideSupport(ship: ShipAPI, effectLevel: Float){

    val sizeMult = 0.75f

    val baseLocL = ship.hullSpec.getWeaponSlotAPI("SPL 000").computePosition(ship)
    val baseLocR = ship.hullSpec.getWeaponSlotAPI("SPR 000").computePosition(ship)


    // 控制柱子
    var range = 20f * sizeMult
    var startOffset = 4f * sizeMult
    var size = 30f * sizeMult
    val rodLevel = MagicAnim.smooth(((effectLevel)/0.6f).coerceAtLeast(0f).coerceAtMost(1f))
    var locL = (aEP_Tool.getExtendedLocationFromPoint(baseLocL,ship.facing +90f, range * rodLevel + startOffset))
    var locR = (aEP_Tool.getExtendedLocationFromPoint(baseLocR,ship.facing -90f, range * rodLevel + startOffset))
    //渲染
    MagicRender.singleframe(rodSpriteL,locL,
      Vector2f(size,size),
      ship.facing,
      Color.white,
      false, CombatEngineLayers.UNDER_SHIPS_LAYER)
    MagicRender.singleframe(rodSpriteR,locR,
      Vector2f(size,size),
      ship.facing + 180f,
      Color.white,
      false, CombatEngineLayers.UNDER_SHIPS_LAYER)

    // 控制第一段环
    range = 16f * sizeMult
    startOffset = 10f * sizeMult
    size = 20f * sizeMult
    val lv1Level = MagicAnim.smooth(((effectLevel-0.3f)/0.5f).coerceAtLeast(0f).coerceAtMost(1f))
    locL = (aEP_Tool.getExtendedLocationFromPoint(baseLocL,ship.facing +90f, range * lv1Level + startOffset))
    locR = (aEP_Tool.getExtendedLocationFromPoint(baseLocR,ship.facing -90f, range * lv1Level + startOffset))
    //渲染
    MagicRender.singleframe(ringSpriteL,locL,
      Vector2f(size,size),
      ship.facing ,
      Color.white,
      false, CombatEngineLayers.UNDER_SHIPS_LAYER)
    MagicRender.singleframe(ringSpriteR,locR,
      Vector2f(size,size),
      ship.facing + 180f,
      Color.white,
      false, CombatEngineLayers.UNDER_SHIPS_LAYER)

    // 控制第二段环
    range = 16f * sizeMult
    startOffset = 4f *sizeMult
    val lv2Level =  MagicAnim.smooth( ((effectLevel-0.5f)/0.5f).coerceAtLeast(0f).coerceAtMost(1f))
    locL = (aEP_Tool.getExtendedLocationFromPoint(baseLocL,ship.facing +90f, range * lv2Level + startOffset))
    locR = (aEP_Tool.getExtendedLocationFromPoint(baseLocR,ship.facing -90f, range * lv2Level + startOffset))
    //渲染
    MagicRender.singleframe(ringSpriteL2,locL,
      Vector2f(size,size),
      ship.facing,
      Color.white,
      false, CombatEngineLayers.UNDER_SHIPS_LAYER)
    MagicRender.singleframe(ringSpriteR2,locR,
      Vector2f(size,size),
      ship.facing + 180f,
      Color.white,
      false, CombatEngineLayers.UNDER_SHIPS_LAYER)

  }
}