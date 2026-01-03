package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_AnchorStandardLight
import data.scripts.utils.aEP_BaseCombatEffect
import data.scripts.aEP_CombatEffectPlugin
import data.scripts.utils.aEP_DataTool.txt
import data.scripts.utils.aEP_ID
import data.scripts.utils.aEP_Tool
import data.scripts.shipsystems.aEP_FighterLaunch
import data.scripts.weapons.aEP_DecoAnimation
import org.dark.shaders.light.LightShader
import org.dark.shaders.light.StandardLight
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_AttackCarrier:aEP_BaseHullMod() {

  companion object{
    const val RANGE_INCREASE_PERCENT = 0f
    const val SPEED_BONUS_RANGE = 1000f
    const val SPEED_THRESHOLD = 200f
    const val SPEED_GAP_BONUS = 0.7f

    const val TURNRATE_THRESHOLD = 90
    const val TURNRATE_GAP_BONUS = 0.7f

    const val FORGE_ACTIVE_SOUND_ID = "system_ammo_feeder"

    //在FORGE_ACTIVE_TIME里面每秒产生多少幅能
    const val FLUX_CREATION_PER_SECOND = 600f
    //锻炉产生的幅能不会使舰船幅能超过多少，防止舰船一直灌满幅能过于脆弱
    const val MAX_INCREASE_FLUX_LEVEL = 0.9f
    //低于多少水平幅能才会启用锻炉，对舰船幅散提出要求才能无限产生飞机
    const val MAX_ACTIVE_FLUX_LEVEL = 0.4f
    const val FORGE_ACTIVE_TIME = 15f
    const val FORGE_TOTAL_TIME = 20f
    const val FORGE_THRESHOLD = 0.35f

    //如果安装超级战机，会受到冷却时间的惩罚
    const val PUNISH_START_OP = 15f
    const val PUNISH_PER_OP = 0.4f

    fun computePunish(ship: ShipAPI?):Float{
      ship ?: return 0f
      var punish = 0f
      for(wingId in ship.variant.fittedWings){
        val op = Global.getSettings().getFighterWingSpec(wingId).getOpCost(null)
        if(op > PUNISH_START_OP){
          punish += (op - PUNISH_START_OP) * PUNISH_PER_OP
        }
      }
      return punish
    }
  }
  val ID = "aEP_AttackCarrier"
  val ID_FORGE = "aEP_Forge"


  init {
    notCompatibleList.add(HullMods.UNSTABLE_INJECTOR)
  }


  override fun applyEffectsToFighterSpawnedByShip(fighter: ShipAPI, ship: ShipAPI, ids: String) {
//    val speed = fighter.mutableStats.maxSpeed.baseValue
//    if(speed >= SPEED_THRESHOLD) return
//    fighter.mutableStats.maxSpeed.modifyFlat(ID,(SPEED_THRESHOLD - speed) * SPEED_GAP_BONUS )
//
//    val turnRate = fighter.mutableStats.maxTurnRate.baseValue
//    if(turnRate >= TURNRATE_THRESHOLD) return
//    fighter.mutableStats.maxTurnRate.modifyFlat(ID,(TURNRATE_THRESHOLD - turnRate) * TURNRATE_GAP_BONUS )
  }

  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, idd: String?) {
    stats?: return
    //延长战机的作战半径
    stats.fighterWingRange.modifyPercent(ID, RANGE_INCREASE_PERCENT.toFloat())
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if(!ship.isAlive || ship.isHulk)return

    //检测战机是否出界，取消航速buff
//    for(bay in ship.launchBaysCopy){
//      bay.wing?:continue
//      val wing = bay.wing
//      for(fighter in wing.wingMembers){
//        fighter?: continue
//        //距离太远，或者母舰不再存活时移除buff
//        if(MathUtils.getDistance(ship.location,fighter.location) > SPEED_BONUS_RANGE || !ship.isAlive || ship.isHulk){
//          fighter.mutableStats.maxSpeed.unmodify(ID)
//          fighter.mutableStats.maxTurnRate.unmodify(ID)
//        }else{
//          applyEffectsToFighterSpawnedByShip(fighter,ship,ID)
//        }
//      }
//    }


    //检测是否需要激活锻炉(如果锻炉就绪的话)
    //舰船刚生成的时候，战机还没有生成，会里启用锻炉并且打断生成过程，加一个cd避免这个情况
    if(ship.fullTimeDeployed < 5f) {
      updateIndicators(ship, 1f)
      return
    }
    if(!ship.customData.containsKey(ID)){
      //不装战机时默认不激活，所以分子为1，造成比例虚高
      var fighterOpAround = 1f
      var totalFighterOp = 1f

      //计算多少比例的战机还存活
      for(bay in ship.launchBaysCopy){
        bay?: continue
        val wing = bay.wing?:continue
        if(!aEP_FighterLaunch.isValidWing(bay.wing)) continue
        for(fighter in bay.wing?.wingMembers?:continue){
          val fighterOp = Global.getSettings().getFighterWingSpec(fighter.wing.wingId).getOpCost(null)/fighter.wing.spec.numFighters
          fighterOpAround +=fighterOp
        }
        totalFighterOp += bay.wing?.spec?.getOpCost(null)?:1f
      }

      //如果总战机装配点少于threshold，且不在冷却，且幅能水平低于阈值，触发锻炉
      if(fighterOpAround/totalFighterOp < FORGE_THRESHOLD && !ship.customData.contains(ID_FORGE) && ship.fluxLevel < MAX_ACTIVE_FLUX_LEVEL){
        ship.setCustomData(ID_FORGE,1f)
        //快速补充战机
        for(bay in ship.launchBaysCopy) {
          if(!aEP_FighterLaunch.isValidWing(bay.wing)) continue
          bay.fastReplacements = bay.numLost
        }
        //放音效
        val p = computePunish(ship)
        Global.getSoundPlayer().playSound(FORGE_ACTIVE_SOUND_ID,0.25f,0.8f,ship.location,aEP_ID.VECTOR2F_ZERO)
        aEP_CombatEffectPlugin.addEffect(ForgeOn(FORGE_TOTAL_TIME + p,ship))
      }
    }

  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {

    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highlight = Misc.getHighlightColor()
    val negativeHighlight = Misc.getNegativeHighlightColor()

    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()

    val titleTextColor: Color = faction.color
    val factionColor: Color = faction.baseUIColor
    val factionDarkColor = faction.darkUIColor
    val factionBrightColor = faction.brightUIColor

    //主效果
    tooltip.addSectionHeading(txt("effect"), Alignment.MID, PARAGRAPH_PADDING_SMALL)

    // 正面
//    addPositivePara(tooltip, "aEP_AttackCarrier01", arrayOf(
//      String.format("%.0f", SPEED_BONUS_RANGE),
//      String.format("%.0f", SPEED_THRESHOLD)))
//
//    //第二列只用显示速度加成，写一个固定的列宽度，
//    val col2W = 80f
//    //第一列显示战机联队的名称，尽可能可能的长
//    val col1W = (width - col2W - PARAGRAPH_PADDING_BIG)
//
//    tooltip.beginTable(
//      factionColor, factionDarkColor, factionBrightColor,
//      TEXT_HEIGHT_SMALL, true, true,
//      *arrayOf<Any>("Wing Spec", col1W, "Bonus", col2W)
//    )
//
//    for(wings in ship?.variant?.fittedWings?:ArrayList()) {
//      val spec = Global.getSettings().getFighterWingSpec(wings)
//      val name = spec.wingName
//      val speed = spec.variant.hullSpec.engineSpec.maxSpeed
//      //速度大于200或者不存在的联队就跳过
//      if(speed >= SPEED_THRESHOLD) continue
//      tooltip.addRow(
//        Alignment.MID, txtColor, name,
//        Alignment.MID, highlight, String.format("+%.0f", SPEED_THRESHOLD - speed),
//      )
//    }
//    tooltip.addTable("", 0, PARAGRAPH_PADDING_SMALL)

    val fluxLevelString= (100f* MAX_ACTIVE_FLUX_LEVEL).toInt().toString()+"%"
    addPositivePara(tooltip, "aEP_AttackCarrier02", arrayOf(
      fluxLevelString,
      FORGE_TOTAL_TIME.toInt().toString()))


    // 负面
    val p = computePunish(ship)
    addNegativePara(tooltip, "aEP_AttackCarrier05", arrayOf(
      String.format("%.0f", PUNISH_START_OP),
      String.format("+%.1f", PUNISH_PER_OP),
      String.format("+%.1f", p)))

    //显示不兼容插件
    showIncompatible(tooltip)
  }

  inner class ForgeOn : aEP_BaseCombatEffect{

    val smokeTracker = IntervalUtil(0.05f,0.05f)
    var plugin: aEP_DecoAnimation? = null
    var weapon: WeaponAPI? = null
    constructor(lifeTime:Float, ship:ShipAPI) : super(lifeTime, ship){
      //构造时找对发光装饰武器和对应的plugin
      for(w in ship.allWeapons){
        if(w.type != WeaponAPI.WeaponType.DECORATIVE) continue
        if(!w.slot.id.equals("GLOW_1")) continue
        weapon = w
        plugin = w.effectPlugin as aEP_DecoAnimation
      }
      //以防万一没有得到武器
      if(weapon == null || plugin == null){
        shouldEnd = true
        return
      }
    }

    override fun advanceImpl(amount: Float) {
      //以防万一没有得到武器
      if(weapon == null || plugin == null){
        shouldEnd = true
        return
      }
      val weapon = weapon as WeaponAPI
      val plugin = weapon.effectPlugin as aEP_DecoAnimation

      //调整装饰武器发光
      val glowLevel = 0.25f + 0.75f* (time/lifeTime)
      plugin.decoGlowController.toLevel = glowLevel

      //维持左下角状态栏
      if(Global.getCombatEngine().playerShip == weapon.ship){
        Global.getCombatEngine().maintainStatusForPlayerShip(ID,
         Global.getSettings().getSpriteName("aEP_ui","heavy_fighter_carrier"),
          spec.displayName,
          String.format(txt("cooldown") +": %.1f",lifeTime-time),
          true)
      }


      //产生视觉特效，只在激活的前几秒内运行
      //使用2个计时器来防止单计时器以0.1为间隔频闪的现象
      //通过延长rampUp也能实现，但是这里要保证喷口上面持续烟雾弥散所以不行
      smokeTracker.advance(amount)
      if(smokeTracker.intervalElapsed() && time <= FORGE_ACTIVE_TIME ){
        //产生幅能
        val fluxToAdd = FLUX_CREATION_PER_SECOND * smokeTracker.elapsed
        if(weapon.ship.fluxTracker.maxFlux - weapon.ship.fluxTracker.currFlux >  fluxToAdd+1f
          && weapon.ship.fluxLevel < MAX_INCREASE_FLUX_LEVEL){
          weapon.ship.fluxTracker.increaseFlux(fluxToAdd,false)
        }

        //产生视觉特效
        createVe(time)
      }

      //产生大范围的jitter提示玩家触发了锻炉
      if(time < 3f){
        val ship = weapon.ship
        val jitterColor =  Color(100, 50, 50, 60)
        ship.setJitterUnder(ship, jitterColor, 1f, 24,  40f)
      }

      //控制灯
      updateIndicators(weapon.ship, time/lifeTime)
    }

    override fun readyToEnd() {
      entity?.removeCustomData(ID_FORGE)
      plugin?.decoGlowController?.toLevel = 0f
    }

    fun createVe(time: Float){
      //以防万一没有得到武器
      if(weapon == null || plugin == null){
        shouldEnd = true
        return
      }
      val weapon = weapon as WeaponAPI


      //输入的是time
      //处理一下，刚开始时fadeIn，然后慢慢fadeOut到保底值
      var useLevel = 0f
      if(time < 2f){
        useLevel = time/2f
      }else if (time >= 2f && time < FORGE_ACTIVE_TIME){
        useLevel = 1f
      }else{
        useLevel = 0.75f*(lifeTime - time)/(lifeTime-FORGE_ACTIVE_TIME)  +0.25f
      }
      useLevel = MathUtils.clamp(useLevel,0f,1f)

      var initColor = Color(250,50,50)
      var alpha = 0.3f* useLevel
      var lifeTime = 3f* useLevel
      var size = 35f
      var endSizeMult = 1.5f
      var vel = aEP_Tool.speed2Velocity(weapon.currAngle,20f)
      Global.getCombatEngine().addNebulaParticle(
        MathUtils.getRandomPointInCircle(weapon.location,20f),
        vel,
        size, endSizeMult,
        0.1f, 0.4f,
        lifeTime * MathUtils.getRandomNumberInRange(0.5f,1f),
        aEP_Tool.getColorWithAlpha(initColor,alpha))

      //必须要求的数据是Color，先设置lifetime后设置fadeIn，autoFadeOut，intenseLevel，location，add进shader
      //注意StandardLight自带的anchor只能绑在entity正中心，这里用不到
      val light = StandardLight(weapon.location, Misc.ZERO, Misc.ZERO,null)
      val lightLifetime = 0.1f
      light.setColor(Color(255,50,50,25))
      light.intensity = 1f * useLevel
      light.size = 20f * useLevel
      light.setLifetime(lightLifetime)
      light.fadeIn(0.05f)
      light.autoFadeOutTime = 0.05f
      LightShader.addLight(light)

      val anchored = aEP_AnchorStandardLight(light,weapon.ship,lightLifetime)
      aEP_CombatEffectPlugin.addEffect(anchored)
    }

  }

  fun updateIndicators(ship: ShipAPI, level: Float){
    var l1 : aEP_DecoAnimation? = null
    var l2 : aEP_DecoAnimation? = null
    var l3 : aEP_DecoAnimation? = null
    var l4 : aEP_DecoAnimation? = null

    for(w in ship.allWeapons){
      if(w.slot.id.equals("ID_L1")) l1 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_L2")) l2 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_L3")) l3 = w.effectPlugin as aEP_DecoAnimation
      if(w.slot.id.equals("ID_L4")) l4 = w.effectPlugin as aEP_DecoAnimation
    }

    l1?:return
    l2?:return
    l3?:return
    l4?:return

    //左侧的弹药数量指示灯
    if(level <= 0){
      l1.setGlowToLevel(0f)
      l2.setGlowToLevel(0f)
      l3.setGlowToLevel(0f)
      l4.setGlowToLevel(0f)
    }
    else if(level <= 0.25f){
      l1.setGlowToLevel(level/0.25f)
      l2.setGlowToLevel(0f)
      l3.setGlowToLevel(0f)
      l4.setGlowToLevel(0f)
    }
    else if(level <= 0.5f){
      l1.setGlowToLevel(1f)
      l2.setGlowToLevel((level-0.25f)/0.25f)
      l3.setGlowToLevel(0f)
      l4.setGlowToLevel(0f)
    }
    else if(level <=0.75f){
      l1.setGlowToLevel(1f)
      l2.setGlowToLevel(1f)
      l3.setGlowToLevel((level-0.5f)/0.25f)
      l4.setGlowToLevel(0f)
    }
    else if(level <= 1f){
      l1.setGlowToLevel(1f)
      l2.setGlowToLevel(1f)
      l3.setGlowToLevel(1f)
      l4.setGlowToLevel((level-0.75f)/0.25f)
    }

  }

}