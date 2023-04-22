package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.impl.VEs.aEP_AnchorStandardLight
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_ID.Companion.HULLMOD_BULLET
import combat.util.aEP_ID.Companion.HULLMOD_POINT
import combat.util.aEP_Tool
import data.scripts.weapons.aEP_DecoAnimation
import data.scripts.shipsystems.aEP_FighterLaunch
import org.dark.shaders.light.LightShader
import org.dark.shaders.light.StandardLight
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_AttackCarrier:aEP_BaseHullMod() {

  companion object{
    const val RANGE_INCREASE_PERCENT = 0
    const val SPEED_BONUS_RANGE = 1000
    const val SPEED_THRESHOLD = 200
    const val SPEED_GAP_BONUS = 0.7f

    const val TURNRATE_THRESHOLD = 90
    const val TURNRATE_GAP_BONUS = 0.7f

    const val FORGE_ACTIVE_SOUND_ID = "system_ammo_feeder"

    //在FORGE_ACTIVE_TIME里面每秒产生多少幅能
    const val FLUX_CREATION_PER_SECOND = 600f
    //锻炉产生的幅能不会使舰船幅能超过多少，防止舰船一直灌满幅能过于脆弱
    const val MAX_INCREASE_FLUX_LEVEL = 0.8f
    //低于多少水平幅能才会启用锻炉，对舰船幅散提出要求才能无限产生飞机
    const val MAX_ACTIVE_FLUX_LEVEL = 0.4f
    const val FORGE_ACTIVE_TIME = 15f
    const val FORGE_TOTAL_TIME = 18f
    const val FORGE_THRESHOLD = 0.35f
  }
  val id = "aEP_AttackCarrier"
  val id2 = "aEP_Forge"


  override fun applyEffectsToFighterSpawnedByShip(fighter: ShipAPI, ship: ShipAPI, ids: String) {
    val speed = fighter.mutableStats.maxSpeed.baseValue
    if(speed >= SPEED_THRESHOLD) return
    fighter.mutableStats.maxSpeed.modifyFlat(id,(SPEED_THRESHOLD - speed) * SPEED_GAP_BONUS )

    val turnRate = fighter.mutableStats.maxTurnRate.baseValue
    if(turnRate >= TURNRATE_THRESHOLD) return
    fighter.mutableStats.maxTurnRate.modifyFlat(id,(TURNRATE_THRESHOLD - turnRate) * TURNRATE_GAP_BONUS )
  }



  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, idd: String?) {
    stats?: return
    //延长战机的作战半径
    stats.fighterWingRange.modifyPercent(id, RANGE_INCREASE_PERCENT.toFloat())
  }


  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if(!ship.isAlive || ship.isHulk)return

    //检测战机是否出界，取消航速buff
    for(bay in ship.launchBaysCopy){
      bay.wing?:continue
      val wing = bay.wing
      for(fighter in wing.wingMembers){
        fighter?: continue
        //距离太远，或者母舰不再存活时移除buff
        if(MathUtils.getDistance(ship.location,fighter.location) > SPEED_BONUS_RANGE || !ship.isAlive || ship.isHulk){
          fighter.mutableStats.maxSpeed.unmodify(id)
          fighter.mutableStats.maxTurnRate.unmodify(id)
        }else{
          applyEffectsToFighterSpawnedByShip(fighter,ship,id)
        }
      }
    }


    //检测是否需要激活锻炉(如果锻炉就绪的话)
    //舰船刚生成的时候，战机还没有生成，会里启用锻炉并且打断生成过程，加一个cd避免这个情况
    if(ship.fullTimeDeployed < 5f) return
    if(!ship.customData.containsKey(id)){
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
      if(fighterOpAround/totalFighterOp < FORGE_THRESHOLD && !ship.customData.contains(id2) && ship.fluxLevel < MAX_ACTIVE_FLUX_LEVEL){
        ship.setCustomData(id2,1f)
        //快速补充战机
        for(bay in ship.launchBaysCopy) {
          if(!aEP_FighterLaunch.isValidWing(bay.wing)) continue
          bay.fastReplacements = bay.numLost
        }
        //放音效
        Global.getSoundPlayer().playSound(FORGE_ACTIVE_SOUND_ID,0.25f,0.8f,ship.location,aEP_ID.VECTOR2F_ZERO)
        aEP_CombatEffectPlugin.addEffect(ForgeOn(FORGE_TOTAL_TIME,ship))
      }
    }

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



    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("{%s}"+txt("aEP_AttackCarrier01"), 5f, arrayOf(Color.green), HULLMOD_POINT, SPEED_BONUS_RANGE.toString(), SPEED_THRESHOLD.toString())

    for(wings in ship?.variant?.fittedWings?:ArrayList()) {
      val spec = Global.getSettings().getFighterWingSpec(wings)
      val name = spec.wingName
      val speed = spec.variant.hullSpec.engineSpec.maxSpeed
      //速度大于200或者不存在的联队就跳过
      if(speed >= SPEED_THRESHOLD) continue
      val string = StringBuffer()
      string.append("$HULLMOD_BULLET{$name}{%s}")
      val bonus = "+" + ((SPEED_THRESHOLD - speed) * SPEED_GAP_BONUS).toInt().toString()
      tooltip.addPara(string.toString(), 5f, txtColor, highLight, bonus)
    }

    val fluxLevelString= (100f* MAX_ACTIVE_FLUX_LEVEL).toInt().toString()+"%"
    tooltip.addPara("{%s}"+txt("aEP_AttackCarrier02"), 5f, arrayOf(Color.green), HULLMOD_POINT, fluxLevelString, FORGE_TOTAL_TIME.toInt().toString())

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
        Global.getCombatEngine().maintainStatusForPlayerShip(id,
         Global.getSettings().getSpriteName("aEP_ui","heavy_fighter_carrier"),
          txt("aEP_AttackCarrier03"),
          String.format(txt("aEP_AttackCarrier04"),(lifeTime-time).toInt().toString()),
          true)
      }


      //产生视觉特效，只在激活的前几秒内运行
      //使用2个计时器来防止单计时器以0.1为间隔频闪的现象
      //通过延长rampUp也能实现，但是这里要保证喷口上面持续烟雾弥散所以不行
      smokeTracker.advance(amount)
      if(smokeTracker.intervalElapsed() && time <= FORGE_ACTIVE_TIME ){
        //产生幅能
        val fluxToAdd = FLUX_CREATION_PER_SECOND * smokeTracker.minInterval
        if(weapon.ship.fluxTracker.maxFlux - weapon.ship.fluxTracker.currFlux >  fluxToAdd+1f && weapon.ship.fluxLevel < 0.85f)
          weapon.ship.fluxTracker.increaseFlux(fluxToAdd,false)

        //产生视觉特效
        createVe(time)
      }

      //产生大范围的jitter提示玩家触发了锻炉
      if(time < 3f){
        val ship = weapon.ship
        val jitterColor =  Color(100, 50, 50, 60)
        ship.setJitterUnder(ship, jitterColor, 1f, 24,  40f)
      }

    }

    override fun readyToEnd() {
      entity?.removeCustomData(id2)
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
      var alpha = 0.2f* useLevel
      var lifeTime = 2f* useLevel
      var size = 30f
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
      val light = StandardLight(weapon.location,aEP_ID.VECTOR2F_ZERO,aEP_ID.VECTOR2F_ZERO,null)
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
}