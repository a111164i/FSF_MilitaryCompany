package data.scripts.campaign.econ.industries

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner
import com.fs.starfarer.api.campaign.listeners.FleetEventListener
import combat.util.aEP_ID
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.campaign.econ.Industry.IndustryTooltipMode
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory
import com.fs.starfarer.api.util.WeightedRandomPicker
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason
import com.fs.starfarer.api.campaign.CampaignEventListener
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.ids.Ranks
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidDangerLevel
import com.fs.starfarer.api.util.Pair
import org.lazywizard.lazylib.MathUtils

class aEP_AdvanceHqPlugin : BaseIndustry(), RouteFleetSpawner, FleetEventListener {

  //刷新间隔
  val spawnTracker = IntervalUtil(7f,9f)

  override fun isHidden(): Boolean {
    return market.factionId != aEP_ID.FACTION_ID_FSF
  }

  override fun isFunctional(): Boolean {
    return super.isFunctional() && market.factionId == aEP_ID.FACTION_ID_FSF
  }

  override fun apply() {
    super.apply(true)
    val size = market.size
    demand(Commodities.SUPPLIES, size - 2)
    demand(Commodities.FUEL, size - 2)
    demand(Commodities.SHIPS, size - 2)
    val deficit = getMaxDeficit(Commodities.HAND_WEAPONS)
    applyDeficitToProduction(1, deficit, Commodities.MARINES)
    modifyStabilityWithBaseMod()
    val memory = market.memoryWithoutUpdate
    Misc.setFlagWithReason(memory, MemFlags.MARKET_PATROL, modId, true, -1f)
    Misc.setFlagWithReason(memory, MemFlags.MARKET_MILITARY, modId, true, -1f)
    if (!isFunctional) {
      supply.clear()
      unapply()
    }
  }

  override fun unapply() {
    super.unapply()
    val memory = market.memoryWithoutUpdate
    Misc.setFlagWithReason(memory, MemFlags.MARKET_PATROL, modId, false, -1f)
    Misc.setFlagWithReason(memory, MemFlags.MARKET_MILITARY, modId, false, -1f)
    unmodifyStabilityWithBaseMod()
  }

  override fun hasPostDemandSection(hasDemand: Boolean, mode: IndustryTooltipMode): Boolean {
    return mode != IndustryTooltipMode.NORMAL || isFunctional
  }

  override fun addPostDemandSection(tooltip: TooltipMakerAPI, hasDemand: Boolean, mode: IndustryTooltipMode) {
    if (mode != IndustryTooltipMode.NORMAL || isFunctional) {
      addStabilityPostDemandSection(tooltip, hasDemand, mode)
    }
  }

  override fun getBaseStabilityMod(): Int {
    return 2
  }

  override fun getNameForModifier(): String {
    return if (getSpec().name.contains("HQ")) {
      getSpec().name
    } else Misc.ucFirst(getSpec().name)
  }

  override fun getStabilityAffectingDeficit(): Pair<String, Int> {
    return getMaxDeficit(Commodities.SUPPLIES, Commodities.FUEL, Commodities.SHIPS, Commodities.HAND_WEAPONS)
  }

  override fun getCurrentImage(): String {
    return super.getCurrentImage()
  }

  override fun isDemandLegal(com: CommodityOnMarketAPI): Boolean {
    return true
  }

  override fun isSupplyLegal(com: CommodityOnMarketAPI): Boolean {
    return true
  }


  protected var returningPatrolValue = 0f
  override fun buildingFinished() {
    super.buildingFinished()
    spawnTracker.forceIntervalElapsed()
  }

  override fun upgradeFinished(previous: Industry) {
    super.upgradeFinished(previous)
    spawnTracker.forceIntervalElapsed()
  }

  override fun advance(amount: Float) {
    super.advance(amount)
    if (Global.getSector().economy.isSimMode) return
    if (!isFunctional) return
    val days = Global.getSector().clock.convertToDays(amount)
    var spawnRate = 1f
    val rateMult = market.stats.dynamic.getStat(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).modifiedValue
    spawnRate *= rateMult
    var extraTime = 0f
    if (returningPatrolValue > 0) {
      // apply "returned patrols" to spawn rate, at a maximum rate of 1 interval per day
      val interval = spawnTracker.intervalDuration
      extraTime = interval * days
      returningPatrolValue -= days
      if (returningPatrolValue < 0) returningPatrolValue = 0f
    }
    spawnTracker.advance(days * spawnRate + extraTime)

    //tracker.advance(days * spawnRate * 100f);
    if (spawnTracker.intervalElapsed()) {
      val sid = routeSourceId
      val maxLight = 4
      val maxMedium = 2
      val maxHeavy = 2
      val light = getCount(PatrolType.FAST)
      val medium = getCount(PatrolType.COMBAT)
      val heavy = getCount(PatrolType.HEAVY)
      val picker = WeightedRandomPicker<PatrolType>()
      //统计周围的巡逻舰队数量和最大生成数量，看看缺哪些巡逻队，加入picker
      picker.add(PatrolType.HEAVY, (maxHeavy - heavy).toFloat())
      picker.add(PatrolType.COMBAT, (maxMedium - medium).toFloat())
      picker.add(PatrolType.FAST, (maxLight - light).toFloat())
      if (picker.isEmpty) return
      //从picker中选一个当前缺少的巡逻队类型进行生成
      val type = picker.pick()
      //PatrolFleetData包含巡逻队类型和fp数量
      //使用从picker中调出来的那个类型
      val custom = MilitaryBase.PatrolFleetData(type)
      val extra = OptionalFleetData(market)
      extra.fleetType = type.fleetType
      //从全局类中获取RouteManager的全局单例，增加一条使用sid的巡逻路线
      //输入的参数spawner是this，所以调用的是本类spawnFleet()方法，本类同时也实现了RouteFleetSpawner类
      val route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, this, custom)
      val patrolDays = 30f + MathUtils.getRandomNumberInRange(-5f,5f)
      //巡逻路线只有一个节点，就是本空间站
      route.addSegment(RouteSegment(patrolDays, market.primaryEntity))
    }
  }

  override fun reportAboutToBeDespawnedByRouteManager(route: RouteData?) {}

  override fun shouldRepeat(route: RouteData): Boolean {
    return false
  }

  fun getCount(vararg types: PatrolType): Int {
    var count = 0
    //搜寻附近所有的相同生成id的巡逻队，用于计数某种类型的巡逻队当前存在几个
    for (data in RouteManager.getInstance().getRoutesForSource(routeSourceId)) {
      if (data.custom is MilitaryBase.PatrolFleetData) {
        val custom = data.custom as MilitaryBase.PatrolFleetData
        for (type in types) {
          if (type == custom.type) {
            count++
            break
          }
        }
      }
    }
    return count
  }

  override fun shouldCancelRouteAfterDelayCheck(route: RouteData): Boolean {
    return false
  }

  override fun reportBattleOccurred(fleet: CampaignFleetAPI, primaryWinner: CampaignFleetAPI, battle: BattleAPI) {}
  override fun reportFleetDespawnedToListener(fleet: CampaignFleetAPI, reason: FleetDespawnReason, param: Any?) {
    if (!isFunctional) return
    if (reason == FleetDespawnReason.REACHED_DESTINATION) {
      val route = RouteManager.getInstance().getRoute(routeSourceId, fleet)
      if (route.custom is MilitaryBase.PatrolFleetData) {
        val custom = route.custom as MilitaryBase.PatrolFleetData
        if (custom.spawnFP > 0) {
          val fraction = (fleet.fleetPoints / custom.spawnFP).toFloat()
          returningPatrolValue += fraction
        }
      }
    }
  }

  override fun spawnFleet(route: RouteData): CampaignFleetAPI? {
    val custom = route.custom as MilitaryBase.PatrolFleetData
    val type = custom.type
    val random = route.random
    var combat = 0f
    var tanker = 0f
    var freighter = 0f
    val fleetType = type.fleetType
    when (type) {
      PatrolType.FAST -> combat = MathUtils.getRandomNumberInRange(40f,60f)
      PatrolType.COMBAT -> {
        combat = MathUtils.getRandomNumberInRange(80f,120f)
        tanker = MathUtils.getRandomNumberInRange(5f,10f)
      }
      PatrolType.HEAVY -> {
        combat = MathUtils.getRandomNumberInRange(120f,160f)
        tanker = MathUtils.getRandomNumberInRange(5f,15f)
        freighter = MathUtils.getRandomNumberInRange(5f,15f)
      }
    }
    val params = FleetParamsV3(
      market,
      null,  // loc in hyper; don't need if have market
      aEP_ID.FACTION_ID_FSF_ADV,
      route.qualityOverride,  // quality override
      fleetType,
      combat,  // combatPts
      freighter,  // freighterPts
      tanker,  // tankerPts
      0f,  // transportPts
      0f,  // linerPts
      0f,  // utilityPts
      0f // qualityMod - since the Lion's Guard is in a different-faction market, counter that penalty
    )
    params.timestamp = route.timestamp
    params.random = random
    params.modeOverride = Misc.getShipPickMode(market)
    params.modeOverride = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL
    val fleet = FleetFactoryV3.createFleet(params)
    if (fleet == null || fleet.isEmpty) return null
    fleet.setFaction(market.factionId, true)
    fleet.isNoFactionInName = true
    fleet.addEventListener(this)

//		PatrolAssignmentAIV2 ai = new PatrolAssignmentAIV2(fleet, custom);
//		fleet.addScript(ai);
    fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_PATROL_FLEET] = true
    fleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORES_OTHER_FLEETS, true] = 0.3f
    if (type == PatrolType.FAST || type == PatrolType.COMBAT) {
      fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_CUSTOMS_INSPECTOR] = true
    }
    val postId = Ranks.POST_PATROL_COMMANDER
    var rankId = Ranks.SPACE_COMMANDER
    rankId = when (type) {
      PatrolType.FAST -> Ranks.SPACE_LIEUTENANT
      PatrolType.COMBAT -> Ranks.SPACE_COMMANDER
      PatrolType.HEAVY -> Ranks.SPACE_CAPTAIN
    }
    fleet.commander.postId = postId
    fleet.commander.rankId = rankId
    market.containingLocation.addEntity(fleet)
    fleet.facing = MathUtils.getRandomNumberInRange(0f,360f)
    // this will get overridden by the patrol assignment AI, depending on route-time elapsed etc
    fleet.setLocation(market.primaryEntity.location.x, market.primaryEntity.location.y)
    fleet.addScript(PatrolAssignmentAIV4(fleet, route))

    //market.getContainingLocation().addEntity(fleet);
    //fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);
    if (custom.spawnFP <= 0) {
      custom.spawnFP = fleet.fleetPoints
    }
    return fleet
  }

  val routeSourceId: String
    get() = getMarket().id + "_" + "aEP_FSF_adv"

  override fun isAvailableToBuild(): Boolean {
    return false
  }

  override fun showWhenUnavailable(): Boolean {
    return false
  }

  override fun canImprove(): Boolean {
    return false
  }

  override fun adjustCommodityDangerLevel(commodityId: String, level: RaidDangerLevel): RaidDangerLevel {
    return level.next()
  }

  override fun adjustItemDangerLevel(itemId: String, data: String, level: RaidDangerLevel): RaidDangerLevel {
    return level.next()
  }
}