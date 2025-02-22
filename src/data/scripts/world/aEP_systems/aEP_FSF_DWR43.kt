package data.scripts.world.aEP_systems

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.Script
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.ids.*
import com.fs.starfarer.api.impl.campaign.ids.Entities.STABLE_LOCATION
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantThemeGenerator
import com.fs.starfarer.api.util.DelayedActionScript
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import data.scripts.FSFModPlugin
import data.scripts.campaign.econ.environment.aEP_ExtinctiveVirus
import data.scripts.campaign.econ.environment.aEP_MilitaryZone
import data.scripts.campaign.econ.environment.aEP_SpaceFarm
import data.scripts.campaign.submarkets.aEP_FSFMarketPlugin
import lunalib.lunaSettings.LunaSettings.getBoolean
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_FSF_DWR43 : SectorGeneratorPlugin {
  companion object{
    public const val ID = "DWR43"
    public const val FACTORY_STATION_MARKET_ID = "aEP_FSF_SpaceFactory"
    public const val EARTH_MARKET_ID = "aEP_FSF_Earth"
    public const val MINING_PLANET_MARKET_ID = "aEP_FSF_Stationplanet"
    public const val REMNANT_STATION_FLEET_ID = "aEP_FSF_RemnantStation"
  }

  override fun generate(sector: SectorAPI) {
    val system = sector.createStarSystem(ID)
    system.location.set(2150f +  MathUtils.getRandomNumberInRange(-10000,10000), -33480f + MathUtils.getRandomNumberInRange(-3000,3000))
    //system.location[3650f] = -22480f
    //system.location[4350f] = -20240f
    system.lightColor = Color(251, 210, 251) // 不可以有透明度，不是叠加而是覆盖
    val hyper = Global.getSector().hyperspace
    system.backgroundTextureFilename = "graphics/backgrounds/hyperspace1.jpg"

    system.setHasSystemwideNebula(true)
    system.tags.add(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
    system.tags.add(Tags.THEME_HIDDEN)

    //中心的恒星
    val FSF_EarthStar = system.initStar("FSF_EarthStar", "aEP_FSF_Earthstar", 1200f, 120f)

    //先把星系绑定在超空间，因为没有调用自带生成重力井，所以尚未绑定
    system.generateAnchorIfNeeded()


    //创造沙漠行星和环绕它的2个恒星遮罩，环绕时间同步就能做到一直背对恒星
    val FSF_StationPlanet = system.addPlanet(
      MINING_PLANET_MARKET_ID,  //Unique id for this planet (or null to have it be autogenerated)
      FSF_EarthStar,  // What the planet orbits (orbit is always circular)
      ID+ " X",  //Name
      "aEP_FSF_Stationplanet", 0f, 150f, 6000f, 500f
    ) //Days it takes to complete an orbit. 1 day = 10 seconds.
    val FSF_StationPlanetShade1 = system.addCustomEntity(
      "FSF_StationPlanetShade1",
      null,  //Name
      "stellar_shade", Factions.NEUTRAL)
    FSF_StationPlanetShade1.setCircularOrbitPointingDown(FSF_StationPlanet,150f,240f,500f)
    val FSF_StationPlanetShade2 = system.addCustomEntity(
      "FSF_StationPlanetShade2",
      null,  //Name
      "stellar_shade", Factions.NEUTRAL)
    FSF_StationPlanetShade2.setCircularOrbitPointingDown(FSF_StationPlanet,210f,240f,500f)

    //创造类地行星
    val FSF_Earth = system.addPlanet(
      EARTH_MARKET_ID,  //Unique id for this planet (or null to have it be autogenerated)
      FSF_EarthStar,  // What the planet orbits (orbit is always circular)
      "$ID Y",  //Name
      "aEP_FSF_Earth", 240f, 265f, 10000f, 800f
    ) //Days it takes to complete an orbit. 1 day = 10 seconds.
    FSF_Earth.market.addCondition(Conditions.TERRAN)
    FSF_Earth.market.addCondition(Conditions.ORGANICS_ABUNDANT)
    FSF_Earth.market.addCondition(Conditions.ORE_MODERATE)
    FSF_Earth.market.addCondition(Conditions.FARMLAND_RICH)

    FSF_Earth.market.addCondition(Conditions.INIMICAL_BIOSPHERE)
    FSF_Earth.market.addCondition(Conditions.POOR_LIGHT)
    FSF_Earth.market.addCondition(Conditions.HIGH_GRAVITY)
    FSF_Earth.market.addCondition(aEP_ExtinctiveVirus.ID)

    //创造一个非特殊的气态行星
    val FSF_GasGaint = system.addPlanet(
      null,  //Unique id for this planet (or null to have it be autogenerated)
      FSF_EarthStar,  // What the planet orbits (orbit is always circular)
      ID+ " Z",  //Name
      "ice_giant", 90f, 285f, 13000f, 1300f
    ) //Days it takes to complete an orbit. 1 day = 10 seconds.
    FSF_GasGaint.market.addCondition(Conditions.VOLATILES_ABUNDANT)


    //创造星环 内环
    system.addAsteroidBelt(FSF_EarthStar, 100, 3000f, 500f, 100f, 190f, Terrain.ASTEROID_BELT, "DWR43 Ring")
    system.addRingBand(FSF_EarthStar, "misc", "rings_asteroids0", 256f, 0, Color.white, 256f, 3000f, 201f, null, null)
    system.addRingBand(FSF_EarthStar, "misc", "rings_asteroids0", 256f, 1, Color.white, 256f, 3100f, 225f, null, null)


    //创造星环 外环
    system.addAsteroidBelt(FSF_EarthStar, 100, 14000f, 500f, 100f, 754f, Terrain.ASTEROID_BELT, "DWR43 Ring2")
    system.addRingBand(FSF_EarthStar, "misc", "rings_asteroids0", 256f, 2, Color.white, 256f, 14000f, 801f, null, null)
    system.addRingBand(FSF_EarthStar, "misc", "rings_asteroids0", 256f, 3, Color.white, 256f, 14100f, 845f, null, null)



    //创造军备空间站
    val FSF_SpaceFactory: SectorEntityToken = system.addCustomEntity(
      FACTORY_STATION_MARKET_ID,  // id
      // id
      txt("aEP_custom_entity_names", "aEP_FSF_SpaceFactory"),  // name
      "aEP_FSF_SpaceFactory",  // type id in planets.json
      aEP_ID.FACTION_ID_FSF
    ) //faction id
    FSF_SpaceFactory.sensorProfile = 1f
    //设置discoverable以后会在地图上面隐形，靠近才会触发探测器
    FSF_SpaceFactory.isDiscoverable = true
    FSF_SpaceFactory.detectedRangeMod.modifyFlat("gen", 8000f)
    FSF_SpaceFactory.setCircularOrbitPointingDown(
      FSF_EarthStar,
      120f,
      1800f,
      Float.MAX_VALUE) //不是很想它转
    val FSF_SpaceFactoryMarket = Global.getFactory().createMarket(FSF_SpaceFactory.id, FSF_SpaceFactory.getName(), 5) //id, name, size
    FSF_SpaceFactoryMarket.primaryEntity = FSF_SpaceFactory
    FSF_SpaceFactoryMarket.surveyLevel = MarketAPI.SurveyLevel.NONE
    FSF_SpaceFactoryMarket.factionId = aEP_ID.FACTION_ID_FSF
    FSF_SpaceFactoryMarket.addIndustry(Industries.POPULATION)
    FSF_SpaceFactoryMarket.addIndustry(Industries.HEAVYBATTERIES)
    FSF_SpaceFactoryMarket.addIndustry(Industries.MEGAPORT)
    FSF_SpaceFactoryMarket.addIndustry(Industries.HIGHCOMMAND)
    FSF_SpaceFactoryMarket.addIndustry(Industries.ORBITALWORKS, ArrayList(listOf(Items.PRISTINE_NANOFORGE)))
    FSF_SpaceFactoryMarket.addIndustry(Industries.FUELPROD, ArrayList(listOf(Items.SYNCHROTRON)))
    FSF_SpaceFactoryMarket.addIndustry("aEP_station_tier3")
    FSF_SpaceFactoryMarket.addIndustry("aEP_AdvanceHq")

    FSF_SpaceFactoryMarket.addSubmarket("generic_military")
    FSF_SpaceFactoryMarket.addSubmarket("storage")

    FSF_SpaceFactoryMarket.addCondition(Conditions.POPULATION_5)
    FSF_SpaceFactoryMarket.addCondition(aEP_SpaceFarm.ID)
    FSF_SpaceFactoryMarket.addCondition(aEP_MilitaryZone.ID)

    FSF_SpaceFactoryMarket.tariff.modifyFlat("default_tariff", FSF_SpaceFactory.faction.tariffFraction)
    //抄的PirateBaseIntel
    FSF_SpaceFactoryMarket.getMemoryWithoutUpdate().set(MemFlags.HIDDEN_BASE_MEM_FLAG, true)
    //隐藏的市场不会提供任务并且成为任务目标
    FSF_SpaceFactoryMarket.isHidden = true
    //设置市场组，只会与组内的人交易
    FSF_SpaceFactoryMarket.setEconGroup(aEP_ID.FACTION_ID_FSF_ADV)
    Global.getSector().economy.addMarket(FSF_SpaceFactoryMarket, false)
    //加入特殊市场
    FSF_SpaceFactoryMarket.addSubmarket(aEP_FSFMarketPlugin.ID)
    //全部设置好以后，绑定市场给实体
    FSF_SpaceFactory.market = FSF_SpaceFactoryMarket


    //创造沙漠星球殖民地
    FSF_StationPlanet.setFaction(aEP_ID.FACTION_ID_FSF)

    val FSF_MiningStationMarket = Global.getFactory().createMarket(
      FSF_StationPlanet.id,
      FSF_StationPlanet.getName(), 4) //id, name, size
    FSF_MiningStationMarket.primaryEntity = FSF_StationPlanet
    FSF_MiningStationMarket.surveyLevel = MarketAPI.SurveyLevel.NONE
    FSF_MiningStationMarket.factionId = aEP_ID.FACTION_ID_FSF
    FSF_MiningStationMarket.addIndustry(Industries.POPULATION)
    FSF_MiningStationMarket.addIndustry(Industries.HEAVYBATTERIES)
    FSF_MiningStationMarket.addIndustry(Industries.SPACEPORT)
    FSF_MiningStationMarket.addIndustry(Industries.MINING)
    FSF_MiningStationMarket.addIndustry(Industries.REFINING)
    FSF_MiningStationMarket.addIndustry(Industries.LIGHTINDUSTRY)
    FSF_MiningStationMarket.addIndustry(Industries.MILITARYBASE)
    FSF_MiningStationMarket.addIndustry("aEP_station_tier1")

    FSF_MiningStationMarket.addSubmarket(Submarkets.GENERIC_MILITARY)
    FSF_MiningStationMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE)

    //对于行星，直接在市场里面加，如果殖民地废弃会被星球剩下的空市场继承过去
    //有一些资源，狠狠加负面，反正ai不需要赚钱，使得玩家没有兴趣抢就可
    FSF_MiningStationMarket.addCondition(Conditions.DESERT)
    FSF_MiningStationMarket.addCondition(Conditions.POPULATION_3)
    FSF_MiningStationMarket.addCondition(aEP_MilitaryZone.ID)

    FSF_MiningStationMarket.addCondition(Conditions.RUINS_EXTENSIVE)

    FSF_MiningStationMarket.addCondition(Conditions.ORE_ABUNDANT)
    FSF_MiningStationMarket.addCondition(Conditions.RARE_ORE_RICH)
    FSF_MiningStationMarket.addCondition(Conditions.VOLATILES_DIFFUSE)

    FSF_MiningStationMarket.addCondition(Conditions.VERY_HOT)
    FSF_MiningStationMarket.addCondition(Conditions.TOXIC_ATMOSPHERE)
    FSF_MiningStationMarket.addCondition(Conditions.IRRADIATED)


    FSF_MiningStationMarket.tariff.modifyFlat("default_tariff", FSF_StationPlanet.faction.tariffFraction)
    //抄的PirateBaseIntel
    FSF_MiningStationMarket.getMemoryWithoutUpdate().set(MemFlags.HIDDEN_BASE_MEM_FLAG, true)
    //隐藏的市场不会提供任务并且成为任务目标
    FSF_MiningStationMarket.isHidden = true
    //设置市场组，只会与组内的人交易
    FSF_MiningStationMarket.setEconGroup(aEP_ID.FACTION_ID_FSF_ADV)
    Global.getSector().economy.addMarket(FSF_MiningStationMarket, false)
    //全部设置好以后，绑定市场给实体
    FSF_StationPlanet.market = FSF_MiningStationMarket
    FSF_StationPlanet.sensorProfile = 1f
    //设置discoverable以后会在地图上面隐形，靠近才会触发探测器
    FSF_StationPlanet.isDiscoverable = true
    FSF_StationPlanet.detectedRangeMod.modifyFlat("gen", 8000f)



    //创造余辉空间站，本质是一个舰队
    val variant = "remnant_station2_Standard"

    val fleet = FleetFactoryV3.createEmptyFleet(Factions.REMNANTS, FleetTypes.BATTLESTATION, null)
    fleet.setCircularOrbit(
      FSF_Earth,0f,500f,90f)
    fleet.id = REMNANT_STATION_FLEET_ID
    val member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant)
    fleet.fleetData.addFleetMember(member)

    fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE] = true
    fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_NO_JUMP] = true
    fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE] = true
    fleet.addTag(Tags.NEUTRINO_HIGH)

    fleet.isStationMode = true
    RemnantThemeGenerator.addRemnantStationInteractionConfig(fleet)

    fleet.clearAbilities()
    fleet.addAbility(Abilities.TRANSPONDER)
    fleet.getAbility(Abilities.TRANSPONDER).activate()
    fleet.detectedRangeMod.modifyFlat("gen", 2000f)

    fleet.ai = null
    system.addEntity(fleet)


    val coreId = Commodities.ALPHA_CORE
    val plugin = Misc.getAICoreOfficerPlugin(coreId)
    val commander = plugin.createPerson(coreId, fleet.faction.id, MathUtils.getRandom())

    fleet.commander = commander
    fleet.flagship.captain = commander
    member.repairTracker.cr = member.repairTracker.maxCR

    //自循环生成舰队，这里不希望玩家刷a核心所以注释了
   /* system.addScript(object : DelayedActionScript(1f) {
      override fun doAction() {
        val activeFleets = RemnantStationFleetManager(
          fleet, 1f, 0, 2, 30f, 2, 4
        )
        system.addScript(activeFleets)
      }
    })*/



    //加个星门
    val gate: SectorEntityToken = system.addCustomEntity(
      "DWR43_gate2",  // unique id
      "$ID Gate",  // name - if null, defaultName from custom_entities.json will be used
      "inactive_gate",  // type of object, defined in custom_entities.json
      null
    ) // faction

    gate.setCircularOrbit(FSF_EarthStar, 110f, 8000f, 800f)


    //增加稳定点/通讯器
    val stableLoc01: SectorEntityToken = system.addCustomEntity(
      "stableLoc01",  // id
      "$ID Relay",  // name
      "comm_relay",  // type id in planets.json
      aEP_ID.FACTION_ID_FSF
    ) //faction id
    stableLoc01.setCircularOrbit(FSF_StationPlanet,MathUtils.getRandomNumberInRange(0f,360f),2000f,1000f/10f)
    val stableLoc02 = system.addCustomEntity(
      "stableLoc02",
      null,
      STABLE_LOCATION,
      Factions.NEUTRAL)
    stableLoc02.setCircularOrbit(FSF_Earth,MathUtils.getRandomNumberInRange(0f,360f),4000f,4000f/10f)
    val stableLoc03 = system.addCustomEntity(
      "stableLoc03",
      null,
      STABLE_LOCATION,
      Factions.NEUTRAL)
    stableLoc03.setCircularOrbit(FSF_EarthStar,MathUtils.getRandomNumberInRange(0f,360f),10000f,10000f/10f)

    //在星系内生成一个出去的重力井
    val jumpPoint1 = Global.getFactory().createJumpPoint("$ID _jump_out", "$ID Jump-point")
    jumpPoint1.setCircularOrbit(FSF_EarthStar, 70f, 6350f, 635f)
    jumpPoint1.setStandardWormholeToHyperspaceVisual()
    jumpPoint1.addTag(Tags.NO_ENTITY_TOOLTIP)
    system.addEntity(jumpPoint1)

    //抄通灵塔任务的，绑定星系内出去的重力井，在超空间生成生成对应的半重力井
    val well = Global.getSector().createNascentGravityWell(jumpPoint1, 50f)
    //取消提示
    well.addTag(Tags.NO_ENTITY_TOOLTIP)
    well.colorOverride = Color(125, 50, 255)
    hyper.addEntity(well)
    //不知道这个半径有啥用，原版写0就写0
    well.autoUpdateHyperLocationBasedOnInSystemEntityAtRadius(jumpPoint1, 0f)

    //把星系内出去的重力井和外面相连
    jumpPoint1.addDestination(JumpPointAPI.JumpDestination(well,null))

    //添加脚本，在玩家第一次进入星系时，现形
    system.addScript(DiscoverSector(system, FSF_SpaceFactoryMarket, FSF_MiningStationMarket, well, jumpPoint1))

    //创建空间站守护者舰队，同时加入脚本捕捉玩家第一次进入星系，必须延迟一秒再生成，构建星系的时候势力文件还没完成，此时根据任何势力都刷不出东西
    //如果启用了跳过主线，不生成
    if (FSFModPlugin.isLunalibEnabled) {
      val shouldSkip = getBoolean("FSF_MilitaryCorporation", "aEP_SettingMissionSkipAwm")!!
      if (!shouldSkip) {
        system.addScript(object : DelayedActionScript(2f){
          override fun doAction() {
            val jumpPointGuardian = spawnFleet(jumpPoint1,FSF_SpaceFactoryMarket)
            jumpPointGuardian.memoryWithoutUpdate.set(MemFlags.MEMORY_KEY_NO_JUMP, true)
            jumpPointGuardian.memoryWithoutUpdate.set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true)
            jumpPointGuardian.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true)
            jumpPointGuardian.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true)
            system.addScript(GuardianCatchPlayer(jumpPointGuardian,jumpPoint1,FSF_SpaceFactoryMarket))
          }
        })
      }
    }

    aEP_FSF_Heng.cleanup(system)
  }

}
//添加脚本，在玩家第一次进入星系时，现形
class DiscoverSector : aEP_BaseEveryFrame{
  var system: StarSystemAPI
  var factoryMarket: MarketAPI
  var miningMarket: MarketAPI
  var nascentWell: NascentGravityWellAPI
  var inSpaceJumpPoint: JumpPointAPI
  constructor(system: StarSystemAPI, factoryMarket: MarketAPI, miningMarket: MarketAPI, nascentWell: NascentGravityWellAPI, inSpaceJumpPoint: JumpPointAPI) {
    this.system = system
    this.factoryMarket = factoryMarket
    this.miningMarket = miningMarket
    this.nascentWell = nascentWell
    this.inSpaceJumpPoint = inSpaceJumpPoint
  }

  override fun advanceImpl(amount: Float) {
    //Global.getSector().hyperspace.addHitParticle(nascentWell.location,aEP_ID.VECTOR2F_ZERO,1000f,1f,1f, Color.white)
    if(Global.getSector().playerFleet.containingLocation == system){
      shouldEnd = true
    }

    if (FSFModPlugin.isLunalibEnabled) {
      val shouldSkip = getBoolean("FSF_MilitaryCorporation", "aEP_SettingMissionSkipAwm")?:false
      if(shouldSkip) shouldEnd = true
    }
  }

  override fun readyToEnd() {
    val hyper = Global.getSector().hyperspace
    for(planet in system.planets){
      planet.isSkipForJumpPointAutoGen = true;
      if(planet.id.equals("FSF_EarthStar")) planet.isSkipForJumpPointAutoGen = false
    }

    //超空间新建一个跳跃点
    val jumpPoint1 = Global.getFactory().createJumpPoint("${aEP_FSF_DWR43.ID} _jump_in", "${aEP_FSF_DWR43.ID} Gravity Well")
    hyper.addEntity(jumpPoint1)
    //相互连通
    jumpPoint1.addDestination(JumpPointAPI.JumpDestination(inSpaceJumpPoint,inSpaceJumpPoint.name))
    inSpaceJumpPoint.addDestination(JumpPointAPI.JumpDestination(jumpPoint1,jumpPoint1.name))
    //断开并移除老的超空间跳跃点
    inSpaceJumpPoint.removeDestination(nascentWell)
    hyper.removeEntity(nascentWell)


    //设置在超空间的位置
    system.autogenerateHyperspaceJumpPoints(false,false,false)


    //设置在超空间的位置，这个半径是星系内环绕半径的等比例缩小，实际obj算法会更复杂，越远的地方缩的越多
    jumpPoint1.autoUpdateHyperJumpPointLocationBasedOnInSystemEntityAtRadius(inSpaceJumpPoint,inSpaceJumpPoint.circularOrbitRadius/10f)


    //之前设置为NONE，但是这个殖民地已经被占领了，玩家又不能去扫描，故手动改为FULL
    factoryMarket.surveyLevel = MarketAPI.SurveyLevel.FULL
    factoryMarket.isHidden = false
    factoryMarket.memoryWithoutUpdate.removeAllRequired(MemFlags.HIDDEN_BASE_MEM_FLAG)
    //只要不和大经济联通，就不会出现在大地图，但是也不会卢左渗透之类的干扰
    factoryMarket.econGroup = null

    miningMarket.surveyLevel = MarketAPI.SurveyLevel.FULL
    miningMarket.isHidden = false
    miningMarket.memoryWithoutUpdate.removeAllRequired(MemFlags.HIDDEN_BASE_MEM_FLAG)
    miningMarket.econGroup = null

    system.tags.remove(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
    //system.tags.remove(Tags.THEME_HIDDEN)

    //重新修正外围的星云，星系变大了
    aEP_FSF_Heng.cleanup(system)
  }
}

/**
 * 在本星系外时，一秒才调用一次
 * */
open class aEP_BaseEveryFrame : EveryFrameScript{

  var runWhilePaused = false
  var shouldEnd = false
  var time = 0f  //用天数计时
  var lifeTime = 0f  //用天数计时
  var entity : SectorEntityToken? = null

  constructor(){

  }

  constructor(lifeTime: Float){
    this.lifeTime = lifeTime
  }

  constructor(lifeTime: Float, entity: SectorEntityToken?){
    this.lifeTime = lifeTime
    this.entity = entity
  }

  /**
   * 结算函数
   * */
  open fun readyToEnd(){}

  open fun advanceImpl(amount: Float){}

  override fun isDone(): Boolean {
    if(shouldEnd){
      readyToEnd()
      return true
    }
    return false
  }

  override fun runWhilePaused(): Boolean {
    return runWhilePaused
  }

  override fun advance(amount: Float) {
    //若 entity不为空，则进行 entity检测，不过就直接结束
    if(entity != null) {
      if(!entity!!.isAlive){
        shouldEnd = true
      }
    }

    if(shouldEnd) return

    time += Global.getSector().clock.convertToDays(amount)
    time = MathUtils.clamp(time,0f,lifeTime)
    advanceImpl(amount)
    if(time >= lifeTime && lifeTime > 0){
      shouldEnd = true
    }
  }
}

fun spawnFleet(jumpPoint:JumpPointAPI, market:MarketAPI ) : CampaignFleetAPI{
  val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF_ADV)

  //add Fleet
  val para = FleetParamsV3(
    null,
    aEP_ID.FACTION_ID_FSF_ADV,
    99f,  // qualityMod
    FleetTypes.TASK_FORCE,
    80f,  // combatPts
    0f,  // freighterPts
    0f,  // tankerPts
    0f,  // transportPts
    0f,  // linerPts
    0f,  // utilityPts
    1f
  )
  para.maxShipSize = 2
  para.treatCombatFreighterSettingAsFraction = true
  para.averageSMods = 1
  para.ignoreMarketFleetSizeMult = true
  val fleet = FleetFactoryV3.createFleet(para)

  //加入舰队必须调用这个
  fleet.setFaction(aEP_ID.FACTION_ID_FSF,true)
  //这个用于rules里面openCommLink的id检测
  fleet.id = "aEP_DWR43_JumpPointGuard"

  //手动添加敌人
  //加一艘内波
  var s: FleetMemberAPI = fleet.fleetData.addFleetMember("aEP_cap_neibo_Standard")
  var p: PersonAPI = faction.createRandomPerson()
  p.rankId = Ranks.SPACE_COMMANDER
  p.setPersonality(Personalities.STEADY)
  //0-未学习，1-普通，2-专精
  p.stats.setSkillLevel(Skills.COMBAT_ENDURANCE, 2f)
  p.stats.setSkillLevel(Skills.HELMSMANSHIP, 2f)
  p.stats.setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2f)
  p.stats.setSkillLevel(Skills.ORDNANCE_EXPERTISE, 2f)
  p.stats.setSkillLevel(Skills.IMPACT_MITIGATION, 2f)
  p.stats.setSkillLevel(Skills.DAMAGE_CONTROL, 2f)
  p.stats.setSkillLevel(Skills.FIELD_MODULATION, 2f)
  p.stats.setSkillLevel(Skills.BALLISTIC_MASTERY, 2f)
  p.stats.setSkillLevel(Skills.GUNNERY_IMPLANTS, 2f)
  p.stats.setSkillLevel(Skills.TARGET_ANALYSIS, 2f)
  p.stats.setSkillLevel(Skills.POINT_DEFENSE, 2f)
  fleet.fleetData.addOfficer(p)
  s.captain = p
  s.variant.addPermaMod("ecm", true)

  //加入2个分解者
  for(i in 0 until 2){
    s = fleet.fleetData.addFleetMember("aEP_cap_decomposer_Standard")
    p = faction.createRandomPerson()
    p.rankId = Ranks.SPACE_COMMANDER
    p.setPersonality(Personalities.CAUTIOUS)
    p.stats.setSkillLevel(Skills.COMBAT_ENDURANCE, 2f)
    p.stats.setSkillLevel(Skills.HELMSMANSHIP, 2f)
    p.stats.setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2f)
    p.stats.setSkillLevel(Skills.POLARIZED_ARMOR, 2f)
    p.stats.setSkillLevel(Skills.IMPACT_MITIGATION, 2f)
    p.stats.setSkillLevel(Skills.DAMAGE_CONTROL, 2f)
    p.stats.setSkillLevel(Skills.POINT_DEFENSE, 2f)
    fleet.fleetData.addOfficer(p)
    s.captain = p
    s.variant.addPermaMod("ecm", true)
  }

  //加两艘瀑布级
  for(i in 0 until 2){
    s = fleet.fleetData.addFleetMember("aEP_cru_pubu_Standard")
    p = faction.createRandomPerson()
    p.rankId = Ranks.SPACE_LIEUTENANT
    p.setPersonality(Personalities.CAUTIOUS)
    p.stats.setSkillLevel(Skills.COMBAT_ENDURANCE, 2f)
    p.stats.setSkillLevel(Skills.HELMSMANSHIP, 2f)
    p.stats.setSkillLevel(Skills.IMPACT_MITIGATION, 2f)
    p.stats.setSkillLevel(Skills.FIELD_MODULATION, 2f)
    p.stats.setSkillLevel(Skills.POINT_DEFENSE, 2f)
    p.stats.setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2f)
    fleet.fleetData.addOfficer(p)
    s.captain = p
    s.variant.addPermaMod("ecm", true)

  }

  //第一艘 平定级
  s = fleet.getFleetData().addFleetMember("aEP_cru_pingding_Standard")
  p = faction.createRandomPerson()
  p.rankId = Ranks.SPACE_LIEUTENANT
  p.setPersonality(Personalities.STEADY)
  p.stats.setSkillLevel(Skills.COMBAT_ENDURANCE, 2f)
  p.stats.setSkillLevel(Skills.HELMSMANSHIP, 2f)
  p.stats.setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2f)
  p.stats.setSkillLevel(Skills.ORDNANCE_EXPERTISE, 2f)
  p.stats.setSkillLevel(Skills.IMPACT_MITIGATION, 2f)
  p.stats.setSkillLevel(Skills.DAMAGE_CONTROL, 2f)
  p.stats.setSkillLevel(Skills.FIELD_MODULATION, 2f)
  p.stats.setSkillLevel(Skills.BALLISTIC_MASTERY, 2f)
  p.stats.setSkillLevel(Skills.GUNNERY_IMPLANTS, 2f)
  p.stats.setSkillLevel(Skills.TARGET_ANALYSIS, 2f)
  p.stats.setSkillLevel(Skills.POINT_DEFENSE, 2f)
  fleet.fleetData.addOfficer(p)
  s.captain = p

  s = fleet.getFleetData().addFleetMember("aEP_cru_pingding_Standard")
  p = faction.createRandomPerson()
  p.rankId = Ranks.SPACE_LIEUTENANT
  p.setPersonality(Personalities.STEADY)
  p.stats.setSkillLevel(Skills.COMBAT_ENDURANCE, 2f)
  p.stats.setSkillLevel(Skills.HELMSMANSHIP, 2f)
  p.stats.setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2f)
  p.stats.setSkillLevel(Skills.ORDNANCE_EXPERTISE, 2f)
  p.stats.setSkillLevel(Skills.IMPACT_MITIGATION, 2f)
  p.stats.setSkillLevel(Skills.DAMAGE_CONTROL, 2f)
  p.stats.setSkillLevel(Skills.FIELD_MODULATION, 2f)
  p.stats.setSkillLevel(Skills.BALLISTIC_MASTERY, 2f)
  p.stats.setSkillLevel(Skills.GUNNERY_IMPLANTS, 2f)
  p.stats.setSkillLevel(Skills.TARGET_ANALYSIS, 2f)
  p.stats.setSkillLevel(Skills.POINT_DEFENSE, 2f)
  fleet.fleetData.addOfficer(p)
  s.captain = p
  //第一艘 深度级 荡平联队
  s = fleet.fleetData.addFleetMember("aEP_des_shendu_Standard")
  p = faction.createRandomPerson()
  p.rankId = Ranks.SPACE_ENSIGN
  p.setPersonality(Personalities.CAUTIOUS)
  p.stats.setSkillLevel(Skills.COMBAT_ENDURANCE, 2f)
  p.stats.setSkillLevel(Skills.HELMSMANSHIP, 2f)
  p.stats.setSkillLevel(Skills.IMPACT_MITIGATION, 2f)
  p.stats.setSkillLevel(Skills.FIELD_MODULATION, 2f)
  p.stats.setSkillLevel(Skills.POINT_DEFENSE, 2f)
  p.stats.setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2f)
  fleet.getFleetData().addOfficer(p)
  s.captain = p
  //2 进军联队
  s = fleet.fleetData.addFleetMember("aEP_des_shendu_Strike")
  p = faction.createRandomPerson()
  p.rankId = Ranks.SPACE_ENSIGN
  p.setPersonality(Personalities.CAUTIOUS)
  p.stats.setSkillLevel(Skills.COMBAT_ENDURANCE, 2f)
  p.stats.setSkillLevel(Skills.HELMSMANSHIP, 2f)
  p.stats.setSkillLevel(Skills.IMPACT_MITIGATION, 2f)
  p.stats.setSkillLevel(Skills.FIELD_MODULATION, 2f)
  p.stats.setSkillLevel(Skills.POINT_DEFENSE, 2f)
  p.stats.setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2f)
  fleet.fleetData.addOfficer(p)
  s.captain = p
  //3 进军联队
  s = fleet.fleetData.addFleetMember("aEP_des_shendu_Strike")
  p = faction.createRandomPerson()
  p.rankId = Ranks.SPACE_ENSIGN
  p.setPersonality(Personalities.CAUTIOUS)
  p.stats.setSkillLevel(Skills.COMBAT_ENDURANCE, 2f)
  p.stats.setSkillLevel(Skills.HELMSMANSHIP, 2f)
  p.stats.setSkillLevel(Skills.IMPACT_MITIGATION, 2f)
  p.stats.setSkillLevel(Skills.FIELD_MODULATION, 2f)
  p.stats.setSkillLevel(Skills.POINT_DEFENSE, 2f)
  p.stats.setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2f)
  fleet.fleetData.addOfficer(p)
  s.captain = p
  //加入4艘涌浪级
  for(i in 0 until 4){
    s = fleet.fleetData.addFleetMember("aEP_fga_yonglang_Mixed")
    p = faction.createRandomPerson()
    p.rankId = Ranks.SPACE_CHIEF
    p.setPersonality(Personalities.STEADY)
    p.stats.setSkillLevel(Skills.COMBAT_ENDURANCE, 2f)
    p.stats.setSkillLevel(Skills.HELMSMANSHIP, 2f)
    p.stats.setSkillLevel(Skills.SYSTEMS_EXPERTISE, 2f)
    p.stats.setSkillLevel(Skills.ORDNANCE_EXPERTISE, 2f)
    p.stats.setSkillLevel(Skills.IMPACT_MITIGATION, 2f)
    p.stats.setSkillLevel(Skills.DAMAGE_CONTROL, 2f)
    p.stats.setSkillLevel(Skills.FIELD_MODULATION, 2f)
    p.stats.setSkillLevel(Skills.BALLISTIC_MASTERY, 2f)
    p.stats.setSkillLevel(Skills.GUNNERY_IMPLANTS, 2f)
    p.stats.setSkillLevel(Skills.TARGET_ANALYSIS, 2f)
    p.stats.setSkillLevel(Skills.POINT_DEFENSE, 2f)
    fleet.fleetData.addOfficer(p)
    s.captain = p
  }
  //自动sync舰队指挥官
  fleet.forceSync()
  //全局buff
  fleet.commander.stats.setSkillLevel(Skills.TACTICAL_DRILLS,1f)
  fleet.commander.stats.setSkillLevel(Skills.COORDINATED_MANEUVERS,1f)
  fleet.commander.stats.setSkillLevel(Skills.WOLFPACK_TACTICS,1f)
  fleet.commander.stats.setSkillLevel(Skills.CREW_TRAINING,1f)
  fleet.commander.stats.setSkillLevel(Skills.CARRIER_GROUP,1f)
  fleet.commander.stats.setSkillLevel(Skills.FIGHTER_UPLINK,1f)
  fleet.commander.stats.setSkillLevel(Skills.SUPPORT_DOCTRINE,1f)
  fleet.commander.stats.setSkillLevel(Skills.ELECTRONIC_WARFARE,1f)
  fleet.commander.stats.setSkillLevel(Skills.HULL_RESTORATION,1f)

  //要把舰队刷新到生涯地图，调用这个

  market.containingLocation.spawnFleet(jumpPoint,0f,0f,fleet)
  return fleet
}

class GuardianCatchPlayer(val fleet:CampaignFleetAPI, val jumpPoint: SectorEntityToken, val market: MarketAPI) : aEP_BaseEveryFrame(0f,fleet){

  var didChase = false

  override fun advanceImpl(amount: Float) {

    //当舰队捉到了玩家，在rules里面触发了对话，在那里面把这个改成true
    if(fleet.memoryWithoutUpdate.contains("\$have_permission")){
      shouldEnd = true
      return
    }
    if(Global.getSector().playerFleet.containingLocation == fleet.containingLocation && !didChase){
      didChase = true
      if(fleet.currentAssignment != null) fleet.currentAssignment.expire()
      fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_PURSUE_PLAYER] = true
      fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE] = true
      fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_HOSTILE] = true
      //注意追击assignment如果追到了就会被打断，并不算作完成
      fleet.addAssignment(FleetAssignment.INTERCEPT,Global.getSector().playerFleet,2f,
        txt("aEP_ApproachingTo")+Global.getSector().playerPerson.nameString,InterceptOnComplete())
    }
    //追击被打断后要重置ai
    if(fleet.currentAssignment == null){
      fleet.memoryWithoutUpdate.unset(MemFlags.MEMORY_KEY_PURSUE_PLAYER)
      fleet.memoryWithoutUpdate.unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE)
      fleet.memoryWithoutUpdate.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE)
      fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE,jumpPoint,Float.MAX_VALUE)
      didChase = false
    }

  }
  //捉到过一次玩家，就返回市场
  override fun readyToEnd() {
    if(fleet.currentAssignment != null) fleet.currentAssignment.expire()
    fleet.memoryWithoutUpdate.unset(MemFlags.MEMORY_KEY_PURSUE_PLAYER)
    fleet.memoryWithoutUpdate.unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE)
    fleet.memoryWithoutUpdate.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE)
    fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, market.primaryEntity,Float.MAX_VALUE)
  }

  inner class InterceptOnComplete : Script{
    override fun run() {
      if(fleet.currentAssignment != null) fleet.currentAssignment.expire()
      fleet.memoryWithoutUpdate.unset(MemFlags.MEMORY_KEY_PURSUE_PLAYER)
      fleet.memoryWithoutUpdate.unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE)
      fleet.memoryWithoutUpdate.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE)
      fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE,jumpPoint,Float.MAX_VALUE)
      didChase = false
    }
  }

}