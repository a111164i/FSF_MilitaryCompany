package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.combat.RecallDeviceStats
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.combat.entities.Ship.ShipAIWrapper
import com.fs.starfarer.loading.specs.HullVariantSpec
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Combat
import combat.util.aEP_DataTool
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.ai.aEP_BaseShipAI
import data.scripts.ai.aEP_MissileAI
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import org.magiclib.util.MagicUI
import java.awt.Color

class aEP_TwinFighter : aEP_BaseHullMod(), AdvanceableListener{

  companion object{
    public const val ID = "aEP_TwinFighter"
    const val LISTENER_KEY = "aEP_TwinFighter_Listener"

    const val FORCE_PULL_BACK_KEY = "aEP_TwinFighter_ForcePullBack"
    const val FORCE_SPLIT_KEY = "aEP_TwinFighter_ForceSplit"
    const val RECALL_SPEED_BONUS_ID = "aEP_TwinFighter_RecallSpeed"
    const val FIGHTER_WING_ID = "aEP_fga_shuangshen3_wing"

    const val MODULE_KEY_ID = "aEP_fga_shuangshen3_wing"

    const val REPAIR_SPEED = 100f

    const val ATTACH_DIST = 25f

    fun spawnModule(originVariant:HullVariantSpec, owner: Int, parent:ShipAPI) : ShipAPI{
      val newModule = Global.getCombatEngine().createFXDrone(originVariant)
      //并不是特效无人机，而是实体，去掉这个tag。这个tag会被createFXDrone()自动添加
      newModule.tags.remove(Tags.VARIANT_FX_DRONE)
      newModule.parentStation = parent
      newModule.isDrone = true
      newModule.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f, parent)
      newModule.layer = CombatEngineLayers.FRIGATES_LAYER
      newModule.owner = owner
      newModule.currentCR = 0.7f
      newModule.mutableStats.crewLossMult.modifyFlat(MODULE_KEY_ID, 0f)
      Global.getCombatEngine().addEntity(newModule)

      return newModule
    }

    fun getFtr(ship: ShipAPI): ShipAPI?{
      //如果没有双生战机就直接不起效
      if(!ship.hasListenerOfClass(aEP_TwinFighter::class.java)) return null
      //拿到监听器，从里面得到ftr
      val ftr = ship.listenerManager.getListeners(aEP_TwinFighter::class.java)?.get(0)?.ftr
      return ftr
    }
    fun getM(ship: ShipAPI): ShipAPI?{
      //如果没有双生战机就直接不起效
      if(!ship.hasListenerOfClass(aEP_TwinFighter::class.java)) return null
      //拿到监听器，从里面得到ftr
      val m = ship.listenerManager.getListeners(aEP_TwinFighter::class.java)?.get(0)?.m
      return m
    }

    fun isAttached(ship: ShipAPI): Boolean{
      //如果没有双生战机就直接不起效
      if(!ship.hasListenerOfClass(aEP_TwinFighter::class.java)) return false
      //拿到监听器，从里面得到ftr
      val clazz = ship.listenerManager.getListeners(aEP_TwinFighter::class.java)?.get(0)
      return clazz?.isAttached?:false
    }
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if(ship.fullTimeDeployed > 0f){
      ship.setSprite("aEP_FX","fga_shuangshen")
    }

    //前4秒强制不许拉回战机，为了等起飞动画做完
    if(ship.fullTimeDeployed < 2f){
      ship.isPullBackFighters = false
    }

    //本体如果死亡，摧毁伴生战机
    if(aEP_Tool.isDead(ship)){
      for(w in ship.allWings){
        if(w.spec.id.equals(FIGHTER_WING_ID)){
          for(f in w.wingMembers){
            Global.getCombatEngine().applyDamage(
              f,f.location, 4000f, DamageType.HIGH_EXPLOSIVE, 0f, true,true, f)
          }
        }

      }
    }

    //移除原始模块
    if(ship.childModulesCopy.isNotEmpty()){
      //加入listener控制战机行动
      if (!ship.customData.containsKey(LISTENER_KEY)) {
        ship.setCustomData(LISTENER_KEY, this)
        //移除初始模块
        val module = ship.childModulesCopy[0]
        Global.getCombatEngine().removeEntity(module)

        val c = aEP_TwinFighter()
        ship.addListener(c)
        c.originVariant = module.variant.clone() as HullVariantSpec
        c.s = ship
      }
    }
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

  }

  override fun applyEffectsToFighterSpawnedByShip(fighter: ShipAPI, ship: ShipAPI, id: String) {

    val m = ship.childModulesCopy.get(0)

    //每次双生战机生成时，同步一下本体模块的某些防御性数据
    //模块的进攻性数据也会同步战机，这部分在listener的advance里面
    if(fighter.hullSpec.baseHullId.equals(FIGHTER_WING_ID.replace("_wing","")) && m != null) {
      val mStats = m.mutableStats
      //同步一些速度加成
      //同步机动性加成，因为是ftr负责移动
      fighter.mutableStats.maxSpeed.applyMods(m.mutableStats.maxSpeed)
      fighter.mutableStats.acceleration.applyMods(m.mutableStats.acceleration)
      fighter.mutableStats.deceleration.applyMods(m.mutableStats.deceleration)
      fighter.mutableStats.maxTurnRate.applyMods(m.mutableStats.maxTurnRate)
      fighter.mutableStats.turnAcceleration.applyMods(m.mutableStats.turnAcceleration)

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
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, PARAGRAPH_PADDING_SMALL)

    // 正面
    addPositivePara(tooltip, "aEP_TwinFighter01", arrayOf("Z"))

    addPositivePara(tooltip, "aEP_TwinFighter02", arrayOf(
      String.format("%.0f", REPAIR_SPEED) +" hp")
    )

    //负面
    addNegativePara(tooltip, "aEP_TwinFighter03", arrayOf())

    //显示不兼容插件
    showIncompatible(tooltip)

    //灰字额外说明
    //tooltip.addPara(aEP_DataTool.txt("aEP_MarkerDissipation03"), grayColor, 5f)
  }

  //---------------------------------------//
  //以下是listener的部分
  lateinit var s: ShipAPI
  var m : ShipAPI? = null
  var ftr : ShipAPI? = null
  var originVariant : HullVariantSpec? = null
  var isAttached = false
  override fun advance(amount: Float) {

    //如果已经有模块，但模块被摧毁，同时归零战机和模块
    if(m != null && aEP_Tool.isDead(m!!)){
      Global.getCombatEngine().removeEntity(ftr)
      ftr = null
      Global.getCombatEngine().removeEntity(m)
      m = null
    }

    //如果没有战机或者战机被摧毁，寻找新的战机，同时归零模块
    if(ftr == null || aEP_Tool.isDead(ftr!!)){
      ftr = findFighter(s)
      Global.getCombatEngine().removeEntity(m)
      m = null
    }

    //如果找到了战机，但是没有模块（并不是被摧毁），就创造一个模块
    if(ftr != null && !aEP_Tool.isDead(ftr!!) && m == null){
      val newModule = spawnModule(originVariant as HullVariantSpec, s.owner, s)
      Global.getCombatEngine().addEntity(newModule)
      m = newModule
      m!!.resetDefaultAI()
    }

    //如果战机没找到，出去
    if(ftr == null) {

      return
    }

    //以下ftr和m一定不为null且存活
    val m = m as ShipAPI
    val ftr = ftr as ShipAPI

    //把模块绑在战机上
    m.facing = ftr.facing
    m.location.set(ftr.location)
    m.velocity.scale(0.1f)
    //m.velocity.set(ftr.velocity)

    //战机无敌，不可选中
    ftr.collisionClass = CollisionClass.NONE
    ftr.mutableStats.shieldDamageTakenMult.modifyMult(ID, 0f)
    ftr.mutableStats.armorDamageTakenMult.modifyMult(ID, 0f)
    ftr.mutableStats.hullDamageTakenMult.modifyMult(ID, 0f)
    //同步机动性加成，因为是ftr负责移动
    ftr.mutableStats.maxSpeed.applyMods(m.mutableStats.maxSpeed)
    ftr.mutableStats.acceleration.applyMods(m.mutableStats.acceleration)
    ftr.mutableStats.deceleration.applyMods(m.mutableStats.deceleration)
    ftr.mutableStats.maxTurnRate.applyMods(m.mutableStats.maxTurnRate)
    ftr.mutableStats.turnAcceleration.applyMods(m.mutableStats.turnAcceleration)

    //模块的图层在战机层
    m.layer = CombatEngineLayers.FRIGATES_LAYER
    //模块碰撞为战机
    if(m.collisionClass == CollisionClass.SHIP || m.collisionClass == CollisionClass.ASTEROID) {
      m.collisionClass = CollisionClass.FIGHTER
    }
    //模块不可人工驾驶，不可指挥
    m.isInvalidTransferCommandTarget = true
    //模块同步母舰的设定
    m.isHoldFire = s.isHoldFire
    if(s.isHoldFire){
      m.blockCommandForOneFrame(ShipCommand.FIRE)
    }

    //模块数据显示在左下角
    //不用了，这部分属于 aEP_Module
    if(Global.getCombatEngine().playerShip == s){
//      MagicUI.drawInterfaceStatusBar(s,
//        m.fluxLevel, null,null, m.hardFluxLevel, m.hullSpec.hullName, m.hitpoints.toInt() )
    }

    //修改战机光束射程，同步模块的主武器的射程，让战机会保持合适距离
    var maxRange = 0f
    for(w in m.allWeapons){
      if(w.range > maxRange) maxRange = w.range
    }
    ftr.mutableStats.beamWeaponRangeBonus.modifyFlat(ID, 50f + maxRange - Global.getSettings().getWeaponSpec("aEP_fga_shuangshen2_lidardish").maxRange)

    //在集结模式下，将战机召回到头部
    var trueAi = ftr.shipAI
    if(trueAi is ShipAIWrapper) trueAi = trueAi.ai

    var shouldPullBack = false
    if(s.isPullBackFighters) shouldPullBack = true
    if(s.customData.containsKey(FORCE_PULL_BACK_KEY)) shouldPullBack = true
    //强制分裂的优先级高于强制附着
    if(s.customData.containsKey(FORCE_SPLIT_KEY)) shouldPullBack = false

    //控制战机的装饰武器
    if(shouldPullBack){
      for(w in ftr.allWeapons){
        if(w.id.equals("aEP_fga_shuangshen3_wing_l")){
          val plg = w.effectPlugin as aEP_DecoAnimation
          plg.setRevoToLevel(0f)
          plg.setMoveToLevel(0f)
        }
        if(w.id.equals("aEP_fga_shuangshen3_wing_r")){
          val plg = w.effectPlugin as aEP_DecoAnimation
          plg.setRevoToLevel(0f)
          plg.setMoveToLevel(0f)
        }
        if(w.id.equals("aEP_fga_shuangshen3_empennage_l")){
          val plg = w.effectPlugin as aEP_DecoAnimation
          plg.setRevoToLevel(0f)
          plg.setMoveToLevel(0f)
        }
        if(w.id.equals("aEP_fga_shuangshen3_empennage_r")){
          val plg = w.effectPlugin as aEP_DecoAnimation
          plg.setRevoToLevel(0f)
          plg.setMoveToLevel(0f)
        }
      }
      if(trueAi !is SpecialAi){
        //召回以后会暂时把图层移到更低层，防止盖住本体的武器
        //在ai里面会禁用引擎渲染
        ftr.shipAI = SpecialAi()
        ftr.layer = CombatEngineLayers.FRIGATES_LAYER
        m.layer = CombatEngineLayers.FRIGATES_LAYER
      }
    }else {
      for(w in ftr.allWeapons){
        if(w.id.equals("aEP_fga_shuangshen3_wing_l")){
          val plg = w.effectPlugin as aEP_DecoAnimation
          plg.setRevoToLevel(1f)
          plg.setMoveToLevel(1f)
        }
        if(w.id.equals("aEP_fga_shuangshen3_wing_r")){
          val plg = w.effectPlugin as aEP_DecoAnimation
          plg.setRevoToLevel(1f)
          plg.setMoveToLevel(1f)
        }
        if(w.id.equals("aEP_fga_shuangshen3_empennage_l")){
          val plg = w.effectPlugin as aEP_DecoAnimation
          plg.setRevoToLevel(1f)
          plg.setMoveToLevel(1f)
        }
        if(w.id.equals("aEP_fga_shuangshen3_empennage_r")){
          val plg = w.effectPlugin as aEP_DecoAnimation
          plg.setRevoToLevel(1f)
          plg.setMoveToLevel(1f)
        }
      }
      if(trueAi is SpecialAi){
        //释放时重新回到战机层
        //重新渲染引擎
        ftr.isRenderEngines = true
        ftr.layer = CombatEngineLayers.FIGHTERS_LAYER
        m.layer = CombatEngineLayers.FIGHTERS_LAYER
        ftr.resetDefaultAI()
      }else{
        isAttached = false
      }
    }

  }

  fun findFighter(ship: ShipAPI): ShipAPI?{
    for(wing in ship.allWings){
      if(wing.wingId.equals(FIGHTER_WING_ID)){
        for (fighter in wing.wingMembers){
          if(!aEP_Tool.isDead(fighter)){
            return fighter
          }
        }
      }
    }
    return null
  }

  inner class SpecialAi(): aEP_BaseShipAI(ftr!!){

    val RECALL_TIME = 0.5f
    override fun advanceImpl(amount: Float) {

      if(stat is aEP_MissileAI.Empty){
        val parent = ship.wing.sourceShip
        val slot = parent.hullSpec.getWeaponSlot("FRONT")
        val distSq = MathUtils.getDistanceSquared(ship.location, slot.computePosition(parent))
        if(distSq <= ATTACH_DIST){
          stat = Stick(slot, parent)
        }else{
          stat = TeleportBack(slot, parent)
        }
      }

    }

    inner class TeleportBack(val slot:WeaponSlotAPI, val parent:ShipAPI): aEP_MissileAI.Status() {
      override fun advance(amount: Float) {
        //因为传送涉及到相位半透明等等一系列修改，所以jitter的实际上应该为m
        //把buff加给m，但是结束时传送ftr，途中把ftr整透明，因为ftr是盖在m上面的
        if(!m!!.customData.containsKey(aEP_Combat.RecallFighterJitter.ID)){
          val recallTime =  parent.mutableStats.dynamic.getStat(RECALL_SPEED_BONUS_ID).computeMultMod() * RECALL_TIME
          val recall = object : aEP_Combat.RecallFighterJitter(recallTime, m!!){

            override fun advanceImpl(amount: Float) {
              val effectLevel = time/lifeTime
              val toLoc = slot.computePosition(parent)
              val facing = parent.facing

              //把ftr整变色
              ftr?.run {
                val fighter = ftr as ShipAPI
                fighter.fadeToColor(this,Misc.scaleColor(color,0.1f),0.1f,0.1f,1f)
              }

              //在落点来几个残影，最后一个在正中心不抖动
              val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
              val size = Vector2f( sprite.width,sprite.height)
              val renderLoc = MathUtils.getRandomPointInCircle(toLoc, 20f * (0.1f + 0.9f*(1f-effectLevel)) )
              val c = Misc.setAlpha(color,(255 * effectLevel).toInt())
              for(i in 0 until 4){
                MagicRender.singleframe(sprite,
                  renderLoc, size,
                  //magicRender的角度开始点比游戏多90
                  facing-90f, c, true)
              }
              MagicRender.singleframe(sprite,
                toLoc, size,
                //magicRender的角度开始点比游戏多90
                facing-90f, c, true)
            }

            override fun onRecall() {
              stat = Stick(slot, parent)
              val toLoc = slot.computePosition(parent)
              val c = Misc.setAlpha(RecallDeviceStats.JITTER_COLOR,255)
              Global.getCombatEngine().addHitParticle(
                toLoc,Misc.ZERO,ship.collisionRadius+125f,
                1f,0.1f,0.2f,c)
              Global.getCombatEngine().addHitParticle(
                toLoc,Misc.ZERO,ship.collisionRadius+75f,
                1f,0.1f,0.4f,c)
            }
          }
          aEP_CombatEffectPlugin.addEffect(recall)
        }
      }
    }

    inner class Stick(val slot:WeaponSlotAPI, val parent:ShipAPI): aEP_MissileAI.Status(){
      val repairTimer = IntervalUtil(0.2f,0.8f)

      override fun advance(amount: Float) {
        m?:return
        isAttached = true
        //保护母舰防爆类
        aEP_ProjectileDenialShield.keepExplosionProtectListenerToParent(m!!, parent)

        repairTimer.advance(amount)
        if(repairTimer.intervalElapsed()){
          aEP_Tool.findToRepair(m!!, REPAIR_SPEED/2f,1f,1f,10f,1f,false)
        }

        //防止过载和熄火时，自己打自己
        //秒修引擎，这里的ship是ftr，因为控制移动的是战机，这是个战机ai
        for(en in  ship.engineController.shipEngines){
          if(en.hitpoints < en.maxHitpoints) en.hitpoints = en.maxHitpoints
        }

        //把战机粘到母舰的槽位上
        ship.facing = parent.facing
        ship.location.set(slot.computePosition(parent))
        ship.isRenderEngines = false
      }
    }

  }

}

