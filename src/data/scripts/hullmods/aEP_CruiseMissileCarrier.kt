package data.scripts.hullmods

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc

import combat.util.aEP_DataTool
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.ai.aEP_CruiseMissileAI
import data.scripts.campaign.intel.aEP_CruiseMissileLoadIntel
import data.scripts.weapons.aEP_DecoAnimation
import java.awt.Color
import java.util.*

class aEP_CruiseMissileCarrier : BaseHullMod(), EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {
  companion object{
    const val ID = "aEP_CruiseMissileCarrier"
    const val CR_THRESHOLD = 0.4f
    const val LOAD_SPEED_PER_DAY = 0.1f
    const val id = "\$aEP_CruiseMissileCarrier"
    const val SHIP_ID = "aEP_CruiseMissile"
    const val SHIP_VARIANT_ID = "aEP_CruiseMissile"
    const val FAKE_WEAPON_ID = "aEP_des_shendu_mk2_cruise_missile"
    const val FAKE_WEAPON_SHOT_ID = "aEP_des_shendu_mk2_cruise_missile_shot"
    const val EXPLODE_SHOT_WEAPON_ID = "aEP_cruise_missile_shot"
    const val EXPLODE_SHOT_WEAPON_SHOT_ID = "aEP_cruise_missile_shot"
    const val SPECIAL_ITEM_ID = "aEP_cruise_missile"
    const val CAMPAIGN_ENTITY_ID = "aEP_CruiseMissile"
    const val STATS_LOADED_ID = "aEP_aEP_CruiseMissileCarrierLoaded"

    const val TARGET_KEY = "aEP_des_shendu_mk2_cruise_missile_shot"

  }

  val missileFleetMembers = LinkedList<FleetMemberAPI>()

  override fun advanceInCampaign(member: FleetMemberAPI, amount: Float) {

    //检出玩家舰队中的所有导弹运输舰
    missileFleetMembers.clear()
    for(m in Global.getSector().playerFleet?.membersWithFightersCopy?:return){
      if(m.variant.hasHullMod(ID)) missileFleetMembers.add(m)
    }

    //如果检出数量超过1，而且当前不存在loadingIntel，则生成一个新的loadingIntel类
    //loadingIntel会在当前舰队不存在导弹运输舰后自我移除，不需要在此插件内考虑
    if(!Global.getSector().intelManager.hasIntelOfClass(aEP_CruiseMissileLoadIntel::class.java)
      && missileFleetMembers.size > 0){

      aEP_Tool.addDebugLog("add LoadingMissileIntel in manager")
      val intel = aEP_CruiseMissileLoadIntel()
      intel.faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
      intel.getIntelTags(null).add(aEP_ID.FACTION_ID_FSF)
      intel.isImportant = true
      //放进intel
      Global.getSector().intelManager.addIntel(intel)
      //放进everyframe，否则advance方法不调用
      Global.getSector().addScript(intel)

    }


  }

  //Before和After都不能获取fleetMemberAPI
  //fleetMemberAPI里面的stats不会带入这里，是新建的
  //在这里根据advanceInCampaign()在生涯中存在memory里面的变量，修改stats
  //此方法会在生涯调用
  override fun applyEffectsBeforeShipCreation(hullSize: HullSize?, stats: MutableShipStatsAPI, id: String?) {
  }

  //舰船生成以后，修改武器弹药
  //从Before修改的stats会被带入这里
  //这里从shipAPI得到的fleetMember只是null，战斗中的shipAPI也是一样，无法用于判断是否装填
  //这里不考虑模拟战和任务，在下面的advanceInCombat里面处理
  override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {

    //把战斗部分和判定部分做一个隔离
    //进入战斗以后advanceInCombat是否给弹都依照stats里面的状态判定
    //注意，如果这里modify是0，就不会生成新的flatMod，后面ge时会null,所以设定999为空仓
    ship.mutableStats.dynamic.getStat(STATS_LOADED_ID).modifyFlat(SPECIAL_ITEM_ID,999f)
    if(aEP_CruiseMissileLoadIntel.getLoadedAmount(ship.fleetMemberId) >= 1){
      ship.mutableStats.dynamic.getStat(STATS_LOADED_ID).modifyFlat(SPECIAL_ITEM_ID,1f)
    }


  }

  //其实装配页面生成舰船的时候也会被调用一次
  //处于生涯战斗时，根据有是否装填好的导弹判断
  //若该舰不存在生涯装填数据，把弹药归0，禁止发射
  //如果在模拟战，直接给弹药
  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if (ship == null || !Global.getCombatEngine().isInPlay(ship) || !ship.isAlive) {
      return
    }

    //得到模拟武器
    var toFind : WeaponAPI? = null
    for (w in ship.allWeapons) {
      if (w.spec.weaponId.equals(FAKE_WEAPON_ID)) {
        toFind = w
      }
    }
    val simWeapon = (toFind as WeaponAPI)

    //控制装饰武器
    for (w in ship.allWeapons) {
      if (w.spec.weaponId.equals("aEP_des_shendu_mk2_clip")) {
        val plugin = w.effectPlugin as aEP_DecoAnimation
        plugin.setMoveToLevel(simWeapon.chargeLevel)
      }
    }


    //战斗开始时运行一次，根据是否在生涯判断要不要移除弹药
    if (ship.fullTimeDeployed <= 0f) {
      if (Global.getCombatEngine().isInCampaign
        && ship.mutableStats.dynamic.getStat(STATS_LOADED_ID).getFlatStatMod(SPECIAL_ITEM_ID).value ==1f) {
        simWeapon.maxAmmo = 1
        simWeapon.ammo = 1
      } else if(Global.getCombatEngine().isInCampaignSim
        || Global.getCombatEngine().isSimulation
        || Global.getCombatEngine().isMission){
        //因为装配页面生成舰船的时候会被调用一次，可以用这个东西来过滤，防止某些内容在装配页面被调用时运行
        if(Global.getCombatEngine().isEntityInPlay(ship)){
          simWeapon.maxAmmo = 1
          simWeapon.ammo = 1
        }
      }else{
        simWeapon.maxAmmo = 0
        simWeapon.ammo = 0
      }
    }

    //调整贴图
    when(simWeapon.ammo){
      0 -> {
        Global.getCombatEngine().customData.set("${ship.id}_did_fire", 1)
        simWeapon.animation.frame = 0
      }
    }

  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI, width: Float, isForModSpec: Boolean) {
    if (Global.getSector() == null || Global.getCurrentState() != GameState.CAMPAIGN) {
      tooltip.addPara(aEP_DataTool.txt("aEP_CruiseMissileCarrier01"), Color.green, 10f)
      return
    }
    var percent = aEP_CruiseMissileLoadIntel.getLoadedAmount(ship.fleetMemberId)
    if (ship.currentCR < CR_THRESHOLD) {
      tooltip.addPara(aEP_DataTool.txt("aEP_CruiseMissileCarrier02"), Color.red, 10f)
    }
    if (percent <= 0) {
      tooltip.addPara(aEP_DataTool.txt("aEP_CruiseMissileCarrier03"), Color.red, 10f)
      return
    }
    if (percent < 1) {
      tooltip.addPara(aEP_DataTool.txt("aEP_CruiseMissileCarrier04") , 10f, Color.white, Color.yellow, String.format("%.1f", percent * 100) + "%")
      return
    }
    if (percent >= 1) {
      tooltip.addPara(aEP_DataTool.txt("aEP_CruiseMissileCarrier01"), Color.green, 10f)
      return
    }
  }

  //要在装配页面实现动态贴图变化，必须使用EveryFrameWeaponEffectPlugin
  //在hullmods的everyFrame里面修改weapon的frame没有用
  override fun advance(amount: Float, engine: CombatEngineAPI?, weapon: WeaponAPI?) {
    weapon?.animation?.frame = 0
    val ship = weapon?.ship?: return

    //得到模拟武器
    var toFind : WeaponAPI? = null
    for (w in ship.allWeapons) {
      if (w.spec.weaponId == aEP_CruiseMissileCarrier.FAKE_WEAPON_ID) {
        toFind = w
      }
    }
    val simWeapon = (toFind as WeaponAPI)?: return

    //调整装填数据调整贴图
    val loadedId = aEP_CruiseMissileLoadIntel.getLoadedItemId(ship.fleetMemberId)
    when(loadedId){
      "" -> simWeapon.animation.frame = 0
      aEP_CruiseMissileLoadIntel.S1_ITEM_ID -> simWeapon.animation.frame = 1
      aEP_CruiseMissileLoadIntel.S2_ITEM_ID -> simWeapon.animation.frame = 2
    }

    //针对战役的情况
    //如果在战役中，或者战役模拟战中
    //生涯模拟战时，isInCampaignSim和isSimulation都是true 这里只考虑非生涯的模拟战，
    if((Global.getCombatEngine().isMission
          || (Global.getCombatEngine().isSimulation && !Global.getCombatEngine().isInCampaignSim))
      && simWeapon.ammo>=1){
      simWeapon.animation.frame = 1
    }

  }

  //s1 s2 巡航导弹发射装置
  //控制如何爆炸在FighterSpecial里面
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?) {
    engine?: return
    projectile?: return
    weapon?: return

    //刷点烟雾
    aEP_Tool.spawnCompositeSmoke(weapon.location, 150f, 3f,  Color(250, 250, 250, 175),weapon.ship.velocity)
    aEP_Tool.spawnCompositeSmoke(weapon.location, 250f, 4f,  Color(150, 150, 150, 175),weapon.ship.velocity)
    //闪光
    Global.getCombatEngine().addSmoothParticle(
      weapon.location,
      Misc.ZERO,
      300f,1f,0.1f,0.3f,Color.yellow)

    //先读取本舰目前在intel里面装填的道具id
    val itemId = aEP_CruiseMissileLoadIntel.getLoadedItemId(weapon.ship.fleetMemberId)
    //把道具id转换成刷出来船（导弹）的id
    var hullId = aEP_CruiseMissileLoadIntel.ITEM_TO_SHIP_ID[itemId]?:""
    //如果读不到（可能是由于本舰没有装填，或者根本不在生涯中）
    if(hullId.equals("")){
      //如果在生涯非模拟战中
      if( Global.getCombatEngine().isInCampaign){
        Global.getCombatEngine().removeEntity(projectile)
        return
      }
      //如果在生涯模拟战中
      if( Global.getCombatEngine().isInCampaignSim){
        Global.getCombatEngine().removeEntity(projectile)
        return
      }
      //如果在战役中
      if( Global.getCombatEngine().isMission){
        hullId = aEP_CruiseMissileLoadIntel.S1_VAR_ID
      }
      //如果在战役模拟战中
      if( Global.getCombatEngine().isSimulation){
        hullId = aEP_CruiseMissileLoadIntel.S1_VAR_ID
      }
    }

    weapon.ammo = 0
    val manager = if (weapon.ship == null) {
      engine.getFleetManager(1)
    } else {
      engine.getFleetManager(weapon.ship.owner)
    }
    //保证hull和variant使用相同ids
    val ship = manager.spawnShipOrWing(hullId, projectile.location, projectile.facing, 0f)
    //把碰撞暂时改为战机，在引信插件里面发射后一段时间改回舰船
    ship.collisionClass = CollisionClass.FIGHTER
    ship.velocity.set(projectile.velocity)
    engine.removeEntity(projectile)
    //同步舰船的目标，将舰船目标塞入导弹的customData，由aEP_CruiseMissileAI读取
    ship.shipAI = aEP_CruiseMissileAI(ship,weapon.ship)

    if (engine.isInCampaign) {
      aEP_CruiseMissileLoadIntel.LOADING_MAP.keys.remove(weapon.ship.fleetMemberId)
    }
  }

  class LoadingMissile : EveryFrameScript {
    var isEnd = false
    var loadedNum = 0f
    var lifeTime = 10f
    var fleetMember = ""
    override fun isDone(): Boolean {
      return if (lifeTime <= 0) {
        true
      } else isEnd
    }

    override fun runWhilePaused(): Boolean {
      return false
    }

    override fun advance(amount: Float) {
      if (loadedNum > 1) {
        loadedNum = 1f
      }

      //加入的EveryFrame会每帧自减，如果没有每帧在船插里面每帧续上lifeTime，一段时间会自己结束
      if (!Global.getSector().isPaused) lifeTime -= amount
    }
  }

}