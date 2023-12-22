package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.combat.RecallDeviceStats
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.combat.entities.Ship.ShipAIWrapper
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Combat
import combat.util.aEP_DataTool
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.ai.aEP_BaseShipAI
import data.scripts.ai.aEP_MissileAI
import data.scripts.shipsystems.DragBall
import data.scripts.weapons.PredictionStripe
import data.scripts.weapons.aEP_m_s_harpoon_shot
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color

class aEP_TwinFighter : aEP_BaseHullMod(), AdvanceableListener{

  companion object{
    public const val ID = "aEP_TwinFighter"
    const val FORCE_PULL_BACK_KEY = "aEP_TwinFighter_ForcePullBack"
    const val FORCE_SPLIT_KEY = "aEP_TwinFighter_ForceSplit"
    const val RECALL_SPEED_BONUS_ID = "aEP_TwinFighter_RecallSpeed"
    const val FIGHTER_WING_ID = "aEP_fga_shuangshen3_wing"

    const val REPAIR_SPEED = 200f
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if(ship.fullTimeDeployed > 0f){
      ship.setSprite("aEP_FX","fga_shuangshen")
    }

    //前4秒强制不许拉回战机，为了等起飞动画做完
    if(ship.fullTimeDeployed < 4f){
      ship.isPullBackFighters = false
    }

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


    if(ship.childModulesCopy.isNotEmpty()){
      val module = ship.childModulesCopy[0]
      module.isRenderEngines = false
      module.collisionClass = CollisionClass.NONE
      module.isInvalidTransferCommandTarget = true
      for(b in module.launchBaysCopy){
        for(f in b.wing.wingMembers){
          Global.getCombatEngine().removeEntity(f)
        }
        b.currRate = 0f
      }
      module.mutableStats.armorDamageTakenMult.modifyMult(ID,0f)
      module.mutableStats.hullDamageTakenMult.modifyMult(ID,0f)
      module.mutableStats.shieldDamageTakenMult.modifyMult(ID,0f)
    }
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    //加入listener控制战机行动
    if (!ship.hasListener(this::class.java)) {
      val c = aEP_TwinFighter()
      c.s = ship
      ship.addListener(c)
    }

  }

  override fun applyEffectsToFighterSpawnedByShip(fighter: ShipAPI, ship: ShipAPI, id: String) {

    val m = ship.childModulesCopy.get(0)

    //每次双生战机生成时，同步一下本体模块的某些防御性数据
    //模块的进攻性数据也会同步战机，这部分在listener的advance里面
    if(fighter.hullSpec.baseHullId.equals(FIGHTER_WING_ID.replace("_wing","")) && m != null) {
      val mStats = m.mutableStats
      //这里的armorBonus，shieldArc之类的不起效，战机已经生成好了，手动同步
      //同步装甲加成
      val newArmorRating = mStats.armorBonus.computeEffective(fighter.armorGrid.armorRating)
      val xSize = fighter.armorGrid.grid.size
      val ySize = fighter.armorGrid.grid[0].size
      for(x in 0 until xSize){
        for(y in 0 until ySize){
          fighter.armorGrid.setArmorValue(x,y,newArmorRating/15f)
        }
      }
      //同步机动性加成
      fighter.mutableStats.maxSpeed.applyMods(mStats.maxSpeed)
      fighter.mutableStats.acceleration.applyMods(mStats.acceleration)
      fighter.mutableStats.deceleration.applyMods(mStats.deceleration)
      fighter.mutableStats.maxTurnRate.applyMods(mStats.maxTurnRate)
      fighter.mutableStats.turnAcceleration.applyMods(mStats.turnAcceleration)
      //同步伤害类型减伤
      //动能，没错，动能就是高贵，多了一项
      fighter.mutableStats.kineticDamageTakenMult.applyMods(mStats.kineticDamageTakenMult)
      fighter.mutableStats.kineticShieldDamageTakenMult.applyMods(mStats.kineticShieldDamageTakenMult)
      fighter.mutableStats.kineticArmorDamageTakenMult.applyMods(mStats.kineticArmorDamageTakenMult)
      //高爆
      fighter.mutableStats.highExplosiveDamageTakenMult.applyMods(mStats.highExplosiveDamageTakenMult)
      fighter.mutableStats.highExplosiveShieldDamageTakenMult.applyMods(mStats.highExplosiveShieldDamageTakenMult)
      //能量
      fighter.mutableStats.energyDamageTakenMult.applyMods(mStats.energyDamageTakenMult)
      fighter.mutableStats.energyShieldDamageTakenMult.applyMods(mStats.energyShieldDamageTakenMult)
      //总减伤
      fighter.mutableStats.shieldDamageTakenMult.applyMods(mStats.shieldDamageTakenMult)
      fighter.mutableStats.armorDamageTakenMult.applyMods(mStats.armorDamageTakenMult)
      fighter.mutableStats.hullDamageTakenMult.applyMods(mStats.hullDamageTakenMult)
      //同步引擎武器的生命值加成
      m.mutableStats.weaponHealthBonus.applyMods(mStats.weaponHealthBonus)
      m.mutableStats.engineHealthBonus.applyMods(mStats.engineHealthBonus)
      //同步维修速度加成
      m.mutableStats.combatWeaponRepairTimeMult.applyMods(mStats.combatWeaponRepairTimeMult)
      m.mutableStats.combatEngineRepairTimeMult.applyMods(mStats.combatEngineRepairTimeMult)
      //同步幅能数据
      m.mutableStats.fluxCapacity.applyMods(mStats.fluxCapacity)
      m.mutableStats.fluxDissipation.applyMods(mStats.fluxDissipation)

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
  override fun advance(amount: Float) {
    if(m == null || aEP_Tool.isDead(m!!)){
      if(!s.childModulesCopy.isNullOrEmpty()) {
        m = s.childModulesCopy[0]
      }
      return
    }
    //如果找到了模块，开始找战机，如果战机被摧毁了找不到战机，在这里禁用模块开火并隐形
    if(ftr == null || aEP_Tool.isDead(ftr!!)){
      ftr = findFighter(s)
      if(ftr == null){
        //如果找不到战机，禁止模块开火，并设置为透明，强制关闭护盾
        m!!.shield?.toggleOff()
        m!!.alphaMult = 0f
        m!!.setApplyExtraAlphaToEngines(true)
        m!!.mutableStats.ballisticWeaponFluxCostMod.modifyFlat(ID, Float.MAX_VALUE)
        m!!.mutableStats.energyWeaponFluxCostMod.modifyFlat(ID, Float.MAX_VALUE)
        m!!.mutableStats.missileWeaponFluxCostMod.modifyFlat(ID, Float.MAX_VALUE)
      }else{
        //找到了就取消模块的透明
        m!!.alphaMult = 1f
        m!!.setApplyExtraAlphaToEngines(false)
        m!!.mutableStats.ballisticWeaponFluxCostMod.modifyFlat(ID, 0f)
        m!!.mutableStats.energyWeaponFluxCostMod.modifyFlat(ID, 0f)
        m!!.mutableStats.missileWeaponFluxCostMod.modifyFlat(ID, 0f)
      }
      return
    }

    //以下ftr和m一定不为null且存活
    val m = m as ShipAPI
    val ftr = ftr as ShipAPI

    //把模块绑在战机上，同步一些战机的攻击性数据（通常用于限制）
    if(!aEP_Tool.isDead(m)){
      m.facing = ftr.facing
      m.location.set(ftr.location)
      //同步射速
      m.mutableStats.ballisticRoFMult.applyMods(ftr.mutableStats.ballisticRoFMult)
      m.mutableStats.energyRoFMult.applyMods(ftr.mutableStats.energyRoFMult)
      m.mutableStats.missileRoFMult.applyMods(ftr.mutableStats.missileRoFMult)
      //同步目标尺寸加成
      m.mutableStats.damageToMissiles.applyMods(ftr.mutableStats.damageToMissiles)
      m.mutableStats.damageToFighters.applyMods(ftr.mutableStats.damageToFighters)
      m.mutableStats.damageToFrigates.applyMods(ftr.mutableStats.damageToFrigates)
      m.mutableStats.damageToDestroyers.applyMods(ftr.mutableStats.damageToDestroyers)
      m.mutableStats.damageToCruisers.applyMods(ftr.mutableStats.damageToCruisers)
      m.mutableStats.damageToCapital.applyMods(ftr.mutableStats.damageToCapital)
      //同步对引擎武器加成
      m.mutableStats.damageToTargetShieldsMult.applyMods(ftr.mutableStats.damageToTargetShieldsMult)
      m.mutableStats.damageToTargetWeaponsMult.applyMods(ftr.mutableStats.damageToTargetWeaponsMult)
      m.mutableStats.damageToTargetEnginesMult.applyMods(ftr.mutableStats.damageToTargetEnginesMult)

      m.hitpoints = ftr.hitpoints.coerceAtLeast(1f)
    }

    //找到模块的主武器的射程，给与战机光束相同射程，让战机会保持合适距离
    var maxRange = 0f
    for(w in m.allWeapons){
      if(w.range > maxRange) maxRange = w.range
    }
    ftr.mutableStats.beamWeaponRangeBonus.modifyFlat(ID, maxRange)

    //在集结模式下，将战机召回到头部
    var trueAi = ftr.shipAI
    if(trueAi is ShipAIWrapper) trueAi = trueAi.ai

    var shouldPullBack = false
    if(s.isPullBackFighters) shouldPullBack = true
    if(s.customData.containsKey(FORCE_PULL_BACK_KEY)) shouldPullBack = true
    //强制分裂的优先级高于强制附着
    if(s.customData.containsKey(FORCE_SPLIT_KEY)) shouldPullBack = false


    if(shouldPullBack){
      if(trueAi !is SpecialAi){
        //召回以后会暂时把图层移到更低层，防止盖住本体的武器
        //在ai里面会禁用引擎渲染
        ftr.shipAI = SpecialAi(ftr)
        ftr.layer = CombatEngineLayers.FRIGATES_LAYER
        m.layer = CombatEngineLayers.FRIGATES_LAYER
      }
    }else {
      if(trueAi is SpecialAi){
        //释放时重新回到战机层
        //重新渲染引擎
        ftr.isRenderEngines = true
        ftr.layer = CombatEngineLayers.FIGHTERS_LAYER
        m.layer = CombatEngineLayers.FIGHTERS_LAYER
        ftr.resetDefaultAI()
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

}

class SpecialAi(fighter: ShipAPI): aEP_BaseShipAI(fighter){

  val RECALL_TIME = 0.75f
  override fun advanceImpl(amount: Float) {
    if(stat is aEP_MissileAI.Empty){
      val parent = ship.wing.sourceShip
      val slot = parent.hullSpec.getWeaponSlot("FRONT")
      val distSq = MathUtils.getDistanceSquared(ship.location, slot.computePosition(parent))
      if(distSq <= 25f){
        stat = Stick(slot, parent)
      }else{
        stat = TeleportBack(slot, parent)
      }
    }

  }

  inner class TeleportBack(val slot:WeaponSlotAPI, val parent:ShipAPI): aEP_MissileAI.Status() {
    override fun advance(amount: Float) {
      if(!ship.customData.containsKey(aEP_Combat.RecallFighterJitter.ID)){
        val recallTime =  ship.mutableStats.dynamic.getStat(aEP_TwinFighter.RECALL_SPEED_BONUS_ID).computeMultMod() * RECALL_TIME
        val recall = object : aEP_Combat.RecallFighterJitter(recallTime, ship){

          override fun advanceImpl(amount: Float) {
            val effectLevel = time/lifeTime
            val toLoc = slot.computePosition(parent)
            val facing = parent.facing
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
    val repairTimer = IntervalUtil(0.25f,0.75f)

    override fun advance(amount: Float) {
      //保护母舰防爆类
      aEP_ProjectileDenialShield.keepExplosionProtectListenerToParent(ship, parent)

      repairTimer.advance(amount)
      if(repairTimer.intervalElapsed()){
        aEP_Tool.findToRepair(ship, aEP_TwinFighter.REPAIR_SPEED/2f,1f,1f,10f,1f,false)
      }

      ship.facing = parent.facing
      ship.location.set(slot.computePosition(parent))
      ship.isRenderEngines = false
    }
  }

}

