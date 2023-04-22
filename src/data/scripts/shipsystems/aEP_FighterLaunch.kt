package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.impl.combat.RecallDeviceStats
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.IntervalUtil
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_FighterLaunch : BaseShipSystemScript() {

  companion object{
    const val RECALL_RANGE = 900f
    const val DAMAGE_TAKEN_WHEN_SPEEDING = 0.05f
    const val MAX_BUFF_TIME = 4f
    const val RECALL_TIME =1f

    public fun isValidWing(wing: FighterWingAPI):Boolean{
      if(wing.range <2000f) return false
      if(wing.spec.isBomber) return false
      return true
    }
  }


  lateinit var ship:ShipAPI
  var didRecall = false
  var setPod = false
  var state = ShipSystemStatsScript.State.IDLE
  val recallInterval = IntervalUtil(0.2f,0.2f)

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State, effectLevel: Float) {
    ship = (stats?.entity?: return)as ShipAPI
    this.state = state
    val ship = (ship?:return)
    if (!ship.isAlive) return

    if(effectLevel == 0f){
      unapply(stats,id)
    }

    findLaunchPoint(ship)
    openDeco(ship,effectLevel)
    openDeco2(ship,effectLevel)
    openDeco3(ship,effectLevel)

    //召回战机
    if(effectLevel > 0.2f && (state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE) ){
      recallInterval.advance(aEP_Tool.getAmount(ship))

      if(recallInterval.intervalElapsed()) {
        var didSend = false
        for( bay in ship.launchBaysCopy){
          val wing = bay.wing?: continue
          for(f in wing.wingMembers){
            f?: continue
            if(!isValidWing(wing)) continue
            if(aEP_Tool.getSystemRange(ship,MathUtils.getDistance(ship.location,f.location)) > RECALL_RANGE) continue
            if(!f.isAlive || f.isHulk || f.customData.contains("aEP_RecallFighter")) continue
            aEP_CombatEffectPlugin.addEffect(RecallFighter(RECALL_TIME,f))
            didSend = true
            break
          }
          if(didSend)break
        }
      }
    }
  }


  //控制0到0.33时的移动，水平收回轨道盖板，装弹器垂直滑动
  fun openDeco(ship: ShipAPI, effectLevel: Float) {
    //本函数中使用effectiveLevel要放缩一下
    val effectLevel = effectLevel*3f
    for (weapon in ship.allWeapons) {
      if (weapon.slot.id.contains("HOLD")) {
        val plugin = (weapon.effectPlugin as aEP_DecoAnimation)
        //控制盖板收缩，4是盖板本身,5-4是离舰桥最远的一个
        //4号盖板的移动距离为30
        var maxRange = 30f
        if (weapon.slot.id.contains("1_4")) {
          val to = MathUtils.clamp(effectLevel, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("2_4")) {
          val to = MathUtils.clamp(effectLevel - 0.2f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("3_4")) {
          val to = MathUtils.clamp(effectLevel - 0.4f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("4_4")) {
          val to = MathUtils.clamp(effectLevel - 0.6f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("5_4")) {
          val to = MathUtils.clamp(effectLevel - 0.8f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)
          plugin.decoGlowController.toLevel
        }


        //3号盖板的移动距离为27
        maxRange = 27f
        if (weapon.slot.id.contains("1_3")) {
          val to = MathUtils.clamp(effectLevel, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("2_3")) {
          val to = MathUtils.clamp(effectLevel - 0.2f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("3_3")) {
          val to = MathUtils.clamp(effectLevel - 0.4f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("4_3")) {
          val to = MathUtils.clamp(effectLevel - 0.6f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("5_3")) {
          val to = MathUtils.clamp(effectLevel - 0.8f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)
        }

        //2号盖板的移动距离为16
        maxRange = 16f
        if (weapon.slot.id.contains("1_2")) {
          val to = MathUtils.clamp(effectLevel, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("2_2")) {
          val to = MathUtils.clamp(effectLevel - 0.2f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("3_2")) {
          val to = MathUtils.clamp(effectLevel - 0.4f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("4_2")) {
          val to = MathUtils.clamp(effectLevel - 0.6f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)

        } else if (weapon.slot.id.contains("5_2")) {
          val to = MathUtils.clamp(effectLevel - 0.8f, 0f, 1f)
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(to)
        }
        //所有装弹机相关部件前滑距离为50f
        //_3是胶囊本体，_2是抓住胶囊的爪子，_3是和轨道连接的滑板
      } else if (weapon.slot.id.contains("POD")) {
        val plugin = (weapon.effectPlugin as aEP_DecoAnimation)
        val maxRange = 50f
        plugin.decoMoveController.range = maxRange
        //因为此处的一个装饰武器是反装的，所有反向移动
        if(weapon.slot.id.contains("_3")){ }
        if(weapon.slot.id.contains("2"))  plugin.decoMoveController.range = -maxRange
        plugin.setMoveToLevel(effectLevel)
      }
    }
  }

  //控制0.33到0.66时的移动，垂直滑动轨道盖板，装弹器水平装弹
  //用0.33的原因是，需要实现一个接一个延迟启动，晚启动的板子，比如0.8秒时启动，匀速运动需要1.8才能达到百分之100位置
  //如果使用0.5，如果最早的板子0时启动，0.3时到位。那最晚0.5启动的板子需要到0.8才到位，远远超过0.5
  fun openDeco2(ship: ShipAPI, effectLevel: Float) {
    //本函数中使用effectiveLevel要放缩一下
    val effectLevel = (effectLevel-0.33f)*3f
    for (weapon in ship.allWeapons) {
      if (weapon.slot.id.contains("HOLD")) {
        val plugin = (weapon.effectPlugin as aEP_DecoAnimation)
        //控制盖板垂直滑动，5—4的4是指盖板,5是指离舰桥最远的一个
        //这里使用的"_1_"代表同时控制1号盖板套组里面的所有装饰武器
        //shortest是最短一块盖板需要运动的距离，用来给每个板计算速度
        val shortest = 26f
        if (weapon.slot.id.contains("_1_")) {
          //1号盖板的垂直滑动距离为104
          var maxRange = 104f
          val to = MathUtils.clamp(effectLevel, 0f, 1f)
          plugin.decoMoveController.range = maxRange
          //最远距离，最慢速度，因为控制器的速度指的是effectiveLevel的变化速度，所以长距离要保存和短距离匀速，需要慢
          plugin.decoMoveController.speed = 2f/(maxRange/shortest)
          plugin.setMoveToLevel(to)

        } else if (weapon.slot.id.contains("_2_")) {
          //2号盖板的垂直滑动距离为78
          var maxRange = 78f
          val to = MathUtils.clamp(effectLevel - 0.2f, 0f, 1f)
          plugin.decoMoveController.range = maxRange
          plugin.decoMoveController.speed = 2f/(maxRange/shortest)
          plugin.setMoveToLevel(to)

        } else if (weapon.slot.id.contains("_3_")) {
          //3号盖板的垂直滑动距离为52
          var maxRange = 52f
          val to = MathUtils.clamp(effectLevel - 0.4f, 0f, 1f)
          plugin.decoMoveController.range = maxRange
          plugin.decoMoveController.speed = 2f/(maxRange/shortest)
          plugin.setMoveToLevel(to)

        } else if (weapon.slot.id.contains("_4_")) {
          //4号盖板的垂直滑动距离为26
          var maxRange = shortest
          val to = MathUtils.clamp(effectLevel - 0.6f, 0f, 1f)
          plugin.decoMoveController.range = maxRange
          plugin.decoMoveController.speed = 2f
          plugin.setMoveToLevel(to)

        } else if (weapon.slot.id.contains("5_4")) {
          //5号盖板的垂直滑动距离为0
        }
        //装弹机相关部件横向滑动
        //_3是胶囊本体，_2是抓住胶囊的爪子，_1是和轨道连接的滑板
      } else if (weapon.slot.id.contains("POD")) {
        val plugin = (weapon.effectPlugin as aEP_DecoAnimation)
        if(weapon.slot.id.contains("_3")){
          //胶囊和爪子一起移动
          val maxRange = 56f
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(effectLevel)
          if((state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE)
            && plugin.decoMoveController.effectiveSideLevel > 0.9f){
            plugin.decoGlowController.toLevel = 1f
            plugin.decoGlowController.speed = 0.5f
            plugin.decoGlowController.c = Color(170,120,255)
            plugin.decoGlowController.range = 2f
            plugin.decoGlowController.additive = true
          }else{
            plugin.decoGlowController.toLevel = 1f
            plugin.decoGlowController.speed = 1f
            plugin.decoGlowController.c = Color(255,255,255)
            plugin.decoGlowController.range = 0f
            plugin.decoGlowController.additive = false
          }

          //因为此处的_2是反装的，所以反向移动
        } else if(weapon.slot.id.contains("_2"))  {
          val maxRange = -56f
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(effectLevel)
        } else if(weapon.slot.id.contains("_1"))  {
          //轨道滑板移动24
          val maxRange = 24f
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(effectLevel)
        }

      }
    }
  }

  //亮起轨道上的发光
  fun openDeco3(ship: ShipAPI, effectLevel: Float) {
    //本函数中使用effectiveLevel要放缩一下
    val effectLevel = effectLevel * 2f
    //1f/22f代表在1秒内逐一点亮
    val glowInterval = 1f/22f
    for (weapon in ship.allWeapons) {
      //一共22个，1号是最贴近舰桥的
      if (!weapon.slot.id.contains("RAIL_GLOW"))  continue
      val number = (weapon.slot.id.split("_")[2]).toFloat()
      val plugin = (weapon.effectPlugin as aEP_DecoAnimation)
      plugin.decoGlowController.speed = 1.5f
      plugin.decoGlowController.toLevel = effectLevel - number * glowInterval
    }
  }

  //控制0.66到0.1时的装弹器回缩
  fun openDeco4(ship: ShipAPI, effectLevel: Float){
    //本函数中使用effectiveLevel要放缩一下
    val effectLevel = (effectLevel-0.33f)*3f
    for (weapon in ship.allWeapons) {
      if (weapon.slot.id.contains("POD")) {
        if(state != ShipSystemStatsScript.State.IN) continue
        val plugin = (weapon.effectPlugin as aEP_DecoAnimation)
        if(weapon.slot.id.contains("_3")){
          //胶囊和爪子一起移动
          val maxRange = 56f
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(effectLevel)
          if(plugin.decoMoveController.effectiveSideLevel == 1f){

          }
          if(state == ShipSystemStatsScript.State.IN || state==ShipSystemStatsScript.State.IDLE){
            weapon.animation.frame = 1
          }else{
            weapon.animation.frame = 0
          }
          //因为此处的_2是反装的，所以反向移动
        } else if(weapon.slot.id.contains("_2"))  {
          val maxRange = -56f
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(effectLevel)
        } else if(weapon.slot.id.contains("_1"))  {
          //轨道滑板移动24
          val maxRange = 24f
          plugin.decoMoveController.sideRange = maxRange
          plugin.setMoveToSideLevel(effectLevel)
        }

      }
    }

  }

  //找到loc，不能和上面的合并，因为找的slot里面没武器，allWeapons不包含这个槽
  fun findLaunchPoint(ship: ShipAPI) : Vector2f{
    for(slot in ship.hullSpec.allWeaponSlotsCopy){
      if(slot.id.contains("LAUNCH")){
        return slot.computePosition(ship)
      }
    }
    return aEP_ID.VECTOR2F_ZERO
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    didRecall = false
    setPod = false
  }

  inner class RecallFighter: aEP_BaseCombatEffect{
    val id = "aEP_RecallFighter"

    constructor(lifeTime: Float, f:ShipAPI){
      init(f)
      this.lifeTime = lifeTime
      f.setCustomData(id,1f)
      f.wing.sourceShip.isPullBackFighters = true
    }

    override fun advanceImpl(amount: Float) {
      val fighter = entity as ShipAPI
      //当舰船系统因为外力取消激活时，取消召回效果
      if( state == ShipSystemStatsScript.State.IDLE || state == ShipSystemStatsScript.State.OUT || state == ShipSystemStatsScript.State.COOLDOWN){
        shouldEnd = true
        return
      }
      //当母舰不再存活时，取消召回效果
      if(!ship.isAlive|| ship.isHulk){
        shouldEnd = true
        return
      }

      val effectLevel = time/lifeTime
      val maxRangeBonus = fighter.collisionRadius * 1f
      val jitterRangeBonus: Float = 5f + effectLevel * maxRangeBonus
      fighter.setJitter(RecallDeviceStats.KEY_JITTER, RecallDeviceStats.JITTER_COLOR, effectLevel, 10, 0f, jitterRangeBonus)

      //被召回是一种相位，需要持续维持
      if (fighter.isAlive) fighter.isPhased = true
      val alpha = 1f - effectLevel * 0.5f
      fighter.extraAlphaMult = alpha

    }

    override fun readyToEnd() {
      val fighter = entity as ShipAPI

      //抄原版的检测，无论是是因为entity消失还是lifeTime走完进入结算
      //如果战机还活着就召回，失效了就取消之前的相位效果
      if (fighter.wing != null && fighter.wing.source != null) {
        //让下一个战机（没有进入读秒环节）立刻刷新
        //要后让战机回仓，如果先回仓，下一个战机就会开始读秒
        //再调用立刻刷新的函数，得直到下下个战机才会立刻刷新
        //fighter.wing.source.makeCurrentIntervalFast()
        //fighter.wing.source.land(fighter)
        fighter.isPhased = false
        fighter.extraAlphaMult = 1f

        //当舰船系统因为外力取消激活时，取消发射效果
        if( state == ShipSystemStatsScript.State.IDLE || state == ShipSystemStatsScript.State.OUT || state == ShipSystemStatsScript.State.COOLDOWN){
          fighter.removeCustomData("aEP_RecallFighter")
          return
        }
        //当母舰不再存活时，取消发射效果
        if(!ship.isAlive || ship.isHulk){
          fighter.removeCustomData("aEP_RecallFighter")
          return
        }

        //发射战机，并给与急速加成与减伤buff
        aEP_CombatEffectPlugin.addEffect(Speeding(MAX_BUFF_TIME,fighter,fighter))

        //放音乐
        Global.getSoundPlayer().playSound("aEP_EMP_pike_fire",0.5f,1f,fighter.location,aEP_ID.VECTOR2F_ZERO)

      } else {
        fighter.isPhased = false
        fighter.extraAlphaMult = 1f
        fighter.removeCustomData("aEP_RecallFighter")
      }
    }
  }

  inner class Speeding: aEP_BaseCombatEffect{
    val max_speed_flat_bonus = 600f
    val max_turn_rate_mult = 0f

    val id = "aEP_Speeding"
    var fighter:ShipAPI
    var proj: DamagingProjectileAPI
    constructor(lifeTime: Float,fighter:ShipAPI, entity: CombatEntityAPI){
      this.lifeTime = lifeTime
      this.fighter = fighter
      init(entity)

      //生成proj
      proj = Global.getCombatEngine().spawnProjectile(
        ship, null,"aEP_cru_pubu_main",
        findLaunchPoint(ship),ship.facing,null) as DamagingProjectileAPI

      fighter.mutableStats.maxSpeed.modifyFlat(id, max_speed_flat_bonus)
      fighter.mutableStats.armorDamageTakenMult.modifyMult(id , DAMAGE_TAKEN_WHEN_SPEEDING)
      fighter.mutableStats.hullDamageTakenMult.modifyMult(id , DAMAGE_TAKEN_WHEN_SPEEDING)
      fighter.mutableStats.maxTurnRate.modifyMult(id, max_turn_rate_mult)
      fighter.location.set(findLaunchPoint(ship))
      fighter.facing = ship?.facing?:0f
    }

    override fun advanceImpl(amount: Float) {
      //如果proj消失，取消这个buff
      if(!Global.getCombatEngine().isEntityInPlay(proj)) shouldEnd = true

      //同步位置和速度
      fighter.facing = proj.facing
      fighter.velocity.set(proj.velocity)
      fighter.location.set(proj.location)


      fighter.setJitter(id, Color.RED, 1f, 1, 0f)

      //注意当玩家操控时，shipAI为null
      fighter.shipAI?.setDoNotFireDelay(0.5f)
      fighter.giveCommand(ShipCommand.ACCELERATE,null,0)
      fighter.blockCommandForOneFrame(ShipCommand.FIRE)
      fighter.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT)
      fighter.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT)
      fighter.blockCommandForOneFrame(ShipCommand.DECELERATE)

    }

    override fun readyToEnd() {
      fighter.wing.sourceShip.isPullBackFighters = false
      //注意当玩家操控时，shipAI为null
      fighter.shipAI?.cancelCurrentManeuver()
      fighter.shipAI?.forceCircumstanceEvaluation()
      fighter.mutableStats.maxSpeed.unmodify(id)
      fighter.mutableStats.armorDamageTakenMult.unmodify(id)
      fighter.mutableStats.hullDamageTakenMult.unmodify(id)
      fighter.mutableStats.maxTurnRate.unmodify(id)
      fighter.removeCustomData("aEP_RecallFighter")
    }
  }


}