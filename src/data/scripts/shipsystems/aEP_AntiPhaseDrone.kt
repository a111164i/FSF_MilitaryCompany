package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import data.scripts.utils.aEP_BaseCombatEffect
import data.scripts.aEP_CombatEffectPlugin
import data.scripts.utils.aEP_Tool
import data.scripts.ai.aEP_BaseShipAI
import data.scripts.ai.aEP_DroneShenduShipAI
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicAnim
import org.magiclib.util.MagicRender
import java.awt.Color
import kotlin.math.pow

class aEP_AntiPhaseDrone : BaseShipSystemScript() {

  companion object{
    const val VARIANT_ID = "aEP_ftr_ut_shendu"
    const val WING_SPEC_ID = "aEP_ftr_ut_shendu_wing"

    const val FIGHTER_LIFETIME = 24f
    const val MAX_DISTANCE = 9999f
  }

  val bracket = Global.getSettings().getSprite("aEP_FX","shendu_bracket")
  val platform = Global.getSettings().getSprite("aEP_FX","shendu_platform")
  val fighter = Global.getSettings().getSprite("aEP_FX","shendu_fighter")

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    val amount = aEP_Tool.getAmount(ship)

    updateDecos(ship,effectLevel, amount)
    updateIndicator(ship)

  }


  //这个最先调用，所以在这里初始化这几个装饰武器
  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    val amount = aEP_Tool.getAmount(ship)

    updateDecos(ship,0f, amount)

  }


  fun updateDecos(ship:ShipAPI, level:Float, amount: Float){
    var doorSideLevel = 0f
    var doorVerticalLevel = 0f


    doorVerticalLevel = (level/0.2f).coerceAtMost(1f)

    if(level > 0.2f){
      doorSideLevel = ((level-0.2f)/0.3f).coerceAtMost(1f)
    }

    //控制2个装饰门打开
    for(w in ship.allWeapons){
      if(w.isDecorative == false) continue
      if(w.spec.weaponId.equals("aEP_des_shendu_door_l")){
        val plugin = w.effectPlugin as aEP_DecoAnimation
        plugin.setMoveToLevel(doorVerticalLevel)
        plugin.setMoveToSideLevel(doorSideLevel)
        continue
      }
      if(w.spec.weaponId.equals("aEP_des_shendu_door_r")){
        val plugin = w.effectPlugin as aEP_DecoAnimation
        plugin.setMoveToLevel(doorVerticalLevel)
        plugin.setMoveToSideLevel(doorSideLevel)
        continue
      }
    }

    //渲染支架和飞机
    for(slot in ship.hullSpec.allWeaponSlotsCopy) {
      if(slot.isDecorative == false) continue
      if(slot.id.equals("BAY")){
        val loc = slot.computePosition(ship)
        val facing = slot.computeMidArcAngle(ship)

        var  baseLevel = ((level-0.3f)/0.5f).coerceAtLeast(0f).coerceAtMost(1f)
        baseLevel = MagicAnim.smooth(baseLevel)

        val bracketLoc = aEP_Tool.getExtendedLocationFromPoint(loc,facing, baseLevel * 50f)
        MagicRender.singleframe(
          bracket, bracketLoc, Vector2f(bracket.width,bracket.height), facing - 90f,
          Color.white,false, CombatEngineLayers.UNDER_SHIPS_LAYER)

        val platformLoc = aEP_Tool.getExtendedLocationFromPoint(loc,facing, baseLevel * 75f - 25f)
        MagicRender.singleframe(
          platform, platformLoc, Vector2f(platform.width,platform.height), facing - 90f,
          Color.white,false, CombatEngineLayers.UNDER_SHIPS_LAYER)

        //战机只会在in和active的时候渲染
        val fighterLoc = aEP_Tool.getExtendedLocationFromPoint(loc,facing, baseLevel * 75f - 25f)
        var fighterTurnLevel = ((level-0.7f)/0.2f).coerceAtLeast(0f).coerceAtMost(1f)
        fighterTurnLevel = MagicAnim.smooth(fighterTurnLevel)
        val fighterFacing = facing - 180f + fighterTurnLevel * -90f
        if(ship.system.state == ShipSystemAPI.SystemState.IN
          || ship.system.state == ShipSystemAPI.SystemState.ACTIVE
          || ship.system.state == ShipSystemAPI.SystemState.IDLE){

          MagicRender.singleframe(
            fighter, fighterLoc, Vector2f(fighter.width,fighter.height), fighterFacing - 90f,
            Color.white,false, CombatEngineLayers.UNDER_SHIPS_LAYER)

        }else{
          if(level >= 1f){
            spawnFighter(ship,fighterLoc, fighterFacing)
          }

        }
        continue
      }

    }

  }

  fun spawnFighter(ship: ShipAPI, loc: Vector2f, facing:Float){

    //烟雾特效
    aEP_Tool.spawnCompositeSmoke(loc, 40f, 2f,  Color(250, 250, 250, 175),ship.velocity)
    aEP_Tool.spawnCompositeSmoke(loc, 80f, 3f,  Color(150, 150, 150, 175),ship.velocity)


    val engine = Global.getCombatEngine()
//    //用这种方法会导致战役损失刷屏
//    //屏蔽左上角入场提升
//    val suppressBefore = engine?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages
//    engine?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages = true
//    //生成无人机
//    val drone = engine?.getFleetManager(ship.owner)?.spawnShipOrWing(
//      WING_SPEC_ID,
//      loc,
//      facing)
//    //恢复左上角入场提示
//    engine?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages = suppressBefore?: false

    //用fxDrone是没有自带ai的
    val variant = Global.getSettings().getVariant(VARIANT_ID)
    val drone = Global.getCombatEngine().createFXDrone(variant)
    //并不是特效无人机，而是实体，去掉这个tag。这个tag会被createFXDrone()自动添加
    drone.tags.remove(Tags.VARIANT_FX_DRONE)
    drone.isDrone = true
    drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f, ship)
    drone.layer = CombatEngineLayers.FIGHTERS_LAYER
    drone.owner = ship.owner
    drone.location.set(loc)
    drone.facing = facing
    Global.getCombatEngine().addEntity(drone)
    drone.shipAI = aEP_DroneShenduShipAI(drone.fleetMember, drone)
    aEP_CombatEffectPlugin.addEffect(SelfDestruct(drone as ShipAPI,ship, FIGHTER_LIFETIME))
  }

  fun updateIndicator(ship: ShipAPI){

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


    val currCharge = (ship.system?.ammo?:1).coerceAtMost(3)
    val currChargeLevel = ship.system?.ammoReloadProgress?:0f

    l1.decoGlowController.speed = 10f
    l2.decoGlowController.speed = 10f
    l3.decoGlowController.speed = 10f
    l4.decoGlowController.speed = 10f

    if(currCharge <= 0){
      l1.setGlowToLevel(0f)
      l2.setGlowToLevel(0f)
      l3.setGlowToLevel(0f)
      l4.setGlowToLevel(0f)
    }else if (currCharge <= 1){
      l1.setGlowToLevel(1f)
      l2.setGlowToLevel(0f)
      l3.setGlowToLevel(0f)
      l4.setGlowToLevel(0f)
    }else if (currCharge <= 2){
      l1.setGlowToLevel(1f)
      l2.setGlowToLevel(1f)
      l3.setGlowToLevel(0f)
      l4.setGlowToLevel(0f)
    }else if (currCharge <= 3){
      l1.setGlowToLevel(1f)
      l2.setGlowToLevel(1f)
      l3.setGlowToLevel(1f)
      l4.setGlowToLevel(0f)
    }else if (currCharge <= 4){
      l1.setGlowToLevel(1f)
      l2.setGlowToLevel(1f)
      l3.setGlowToLevel(1f)
      l4.setGlowToLevel(1f)
    }

  }

  inner class SelfDestruct : aEP_BaseCombatEffect{
    var f:ShipAPI
    var parent:ShipAPI
    lateinit var moveAi: aEP_BaseShipAI
    var target:ShipAPI? = null
    val id = "aEP_SelfDestruct"
    val jitterColor = Color(255,50,50)

    constructor(f: ShipAPI, parent: ShipAPI, lifeTime: Float):super(lifeTime,f) {
      this.f = f
      this.parent = parent
      target = parent.shipTarget
    }

    override fun advanceImpl(amount: Float) {
      //如果母舰被毁，立刻自爆
      if(aEP_Tool.isDead(parent)) { shouldEnd = true }

      //如果距离母舰过远，性能下降
      if(MathUtils.getDistanceSquared(f,parent) > aEP_Tool.getSystemRange(parent, MAX_DISTANCE).pow(2)){
        f.mutableStats.armorDamageTakenMult.modifyMult(id,1.5f)
        f.mutableStats.hullDamageTakenMult.modifyMult(id,1.5f)
        f.mutableStats.maxSpeed.modifyMult(id,0.65f)
        f.mutableStats.acceleration.modifyMult(id,0.65f)
      }else{
        f.mutableStats.armorDamageTakenMult.unmodify(id)
        f.mutableStats.hullDamageTakenMult.unmodify(id)
        f.mutableStats.maxSpeed.unmodify(id)
        f.mutableStats.acceleration.unmodify(id)
      }

      //快炸了的时候给提示
      val jitterThreshold = 4f
      if(lifeTime - time < jitterThreshold){
        val level = 1f - (lifeTime - time)/jitterThreshold
        f.setJitter(id,aEP_Tool.getColorWithAlpha(jitterColor,level),level,2,1f*level)
      }
    }

    //自爆
    override fun readyToEnd() {
      Global.getCombatEngine().applyDamage(
        f,f.location,
        (f.maxHitpoints + f.armorGrid.armorRating) * 5f, DamageType.HIGH_EXPLOSIVE,0f,
        true,false,f,false)
    }
  }

}
