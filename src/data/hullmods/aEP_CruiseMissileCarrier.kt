package data.hullmods

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI

import combat.util.aEP_DataTool
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.campaign.intel.aEP_CruiseMissileLoadIntel
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.util.*
import kotlin.collections.HashMap

class aEP_CruiseMissileCarrier : BaseHullMod(), EveryFrameWeaponEffectPlugin {
  companion object{
    const val ID = "aEP_CruiseMissileCarrier"
    const val CR_THRESHOLD = 0.4f
    const val LOAD_SPEED_PER_DAY = 0.1f
    const val id = "\$aEP_CruiseMissileCarrier"
    const val SHIP_ID = "aEP_CruiseMissile"
    const val SHIP_VARIANT_ID = "aEP_CruiseMissile"
    const val FAKE_WEAPON_ID = "aEP_cruise_missile_weapon"
    const val FAKE_WEAPON_SHOT_ID = "aEP_cruise_missile_weapon_shot"
    const val EXPLODE_SHOT_WEAPON_ID = "aEP_cruise_missile_shot"
    const val EXPLODE_SHOT_WEAPON_SHOT_ID = "aEP_cruise_missile_shot"
    const val SPECIAL_ITEM_ID = "aEP_cruise_missile"
    const val CAMPAIGN_ENTITY_ID = "aEP_CruiseMissile"
    const val STATS_LOADED_ID = "aEP_aEP_CruiseMissileCarrierLoaded"
  }

  val missileFleetMembers = LinkedList<FleetMemberAPI>()

  override fun advanceInCampaign(member: FleetMemberAPI, amount: Float) {

    //检出玩家舰队中的所有导弹运输舰
    missileFleetMembers.clear()
    for(m in Global.getSector().playerFleet?.membersWithFightersCopy?:return){
      if(m.variant.hasHullMod(ID)) missileFleetMembers.add(m)
    }

    //如果检出数量超过1，而且当前不存在loadingIntel，则生成一个新的
    //loadingIntel会在当前舰队不存在导弹运输舰后自我移除，不需要在此插件内考虑
    if(!Global.getSector().intelManager.hasIntelOfClass(aEP_CruiseMissileLoadIntel::class.java) && missileFleetMembers.size > 0){
      aEP_Tool.addDebugLog("add LoadingMissileIntel in manager")
      val intel = aEP_CruiseMissileLoadIntel()
      intel.faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
      intel.getIntelTags(null).add(aEP_ID.FACTION_ID_FSF)
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

  //要在装配页面实现动态贴图变化，必须使用EveryFrameWeaponEffectPlugin
  //在hullmods里面修改weapon的frame没有用
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

    //调整贴图
    when(aEP_CruiseMissileLoadIntel.getLoadedItemId(ship.fleetMemberId)){
      "" -> simWeapon.animation.frame = 0
      aEP_CruiseMissileLoadIntel.S1_ITEM_ID -> simWeapon.animation.frame = 1
      aEP_CruiseMissileLoadIntel.S2_ITEM_ID -> simWeapon.animation.frame = 2
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
      if (w.spec.weaponId == aEP_CruiseMissileCarrier.FAKE_WEAPON_ID) {
        toFind = w
      }
    }
    val simWeapon = (toFind as WeaponAPI)?: return


    //战斗开始时运行一次，根据是否在生涯判断要不要移除弹药
    if (ship.fullTimeDeployed == 0f) {
      if (Global.getCombatEngine().isInCampaign && ship.mutableStats.dynamic.getStat(STATS_LOADED_ID).getFlatStatMod(SPECIAL_ITEM_ID).value ==1f) {
        simWeapon.maxAmmo = 1
        simWeapon.ammo = 1
      } else if(Global.getCombatEngine().isInCampaignSim || Global.getCombatEngine().isSimulation || Global.getCombatEngine().isMission){
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
        Global.getCombatEngine().customData.set("${ship.id}_did_fire)", 1)
        simWeapon.animation.frame = 0
      }
    }

  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI, width: Float, isForModSpec: Boolean) {
    if (Global.getSector() == null || Global.getCurrentState() != GameState.CAMPAIGN) {
      tooltip.addPara(aEP_DataTool.txt("CMCarrier01"), Color.green, 10f)
      return
    }
    var percent = aEP_CruiseMissileLoadIntel.getLoadedAmount(ship.fleetMemberId)
    if (ship.currentCR < CR_THRESHOLD) {
      tooltip.addPara(aEP_DataTool.txt("CMCarrier02"), Color.red, 10f)
    }
    if (percent <= 0) {
      tooltip.addPara(aEP_DataTool.txt("CMCarrier03"), Color.red, 10f)
      return
    }
    if (percent < 1) {
      tooltip.addPara(aEP_DataTool.txt("CMCarrier04") + ": {%s}", 10f, Color.white, Color.yellow, String.format("%.1f", percent * 100) + "%")
      return
    }
    if (percent >= 1) {
      tooltip.addPara(aEP_DataTool.txt("CMCarrier04"), Color.green, 10f)
      return
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