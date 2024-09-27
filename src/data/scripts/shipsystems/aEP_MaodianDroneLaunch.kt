package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.Misc
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.ai.aEP_MaoDianDroneAI
import data.scripts.ai.shipsystemai.aEP_MaoDianDroneLaunchAI
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.MathUtils.clamp
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color

class aEP_MaodianDroneLaunch: BaseShipSystemScript() {
  companion object{
    const val DRONE_ID = "aEP_ftr_ut_maodian"
    const val SYSTEM_RANGE = 2000f
    const val MAX_HOLD_TIME = 15f
    const val MAX_FLY_TIME = 15f
  }

  var currDrone: ShipAPI? = null


  var rotator: aEP_DecoAnimation? = null
  var red: aEP_DecoAnimation? = null
  var green: aEP_DecoAnimation? = null

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    val amount = aEP_Tool.getAmount(ship)


    //检测当前的currDrone是否活着
//    if(currDrone is ShipAPI){
//      val curr = currDrone as ShipAPI
//      if(aEP_Tool.isDead(curr)) currDrone = null
//    }

    //完全冷却为1，刚进入冷却为0
    var cooldownLevel = clamp((ship.system.cooldownRemaining/ship.system.cooldown),0f,1f)
    cooldownLevel = 1f-cooldownLevel
    //完成装填为1，刚开始装填为0
    var ammoLevel = ship.system.ammoReloadProgress
    //如果目前还剩使用次数，就直接改ammoLevel为1
    if(ship.system.ammo > 0) ammoLevel = 1f
    //取小
    var glowLevel = Math.min(cooldownLevel, ammoLevel)
    updateIndicator(ship, glowLevel)

    //运行一帧
    if(effectLevel >= 1) {

      for (slot in ship.hullSpec.allWeaponSlotsCopy) {
        if (slot.isSystemSlot) {
          val loc = slot.computePosition(ship)
          //视觉特效
          createVfxOnSpawnDrone(ship, Vector2f(loc))

          val variant = Global.getSettings().getVariant(DRONE_ID)
          val drone = Global.getCombatEngine().createFXDrone(variant)
          //并不是特效无人机，而是实体，去掉这个tag。这个tag会被createFXDrone()自动添加
          drone.tags.remove(Tags.VARIANT_FX_DRONE)
          drone.isDrone = true
          drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f, ship)
          drone.layer = CombatEngineLayers.FIGHTERS_LAYER
          drone.owner = ship.owner
          drone.location.set(loc)
          drone.facing = ship.facing
          Global.getCombatEngine().addEntity(drone)
          drone.velocity.set(ship.velocity ?: Misc.ZERO)

          //生成proj，绑定飞机
          val proj = Global.getCombatEngine().spawnProjectile(
            ship, null,"aEP_cru_pubu_main",
            loc,ship.facing,null) as DamagingProjectileAPI
          //暂时删除了结束时自动传送的功能，写null就行
          aEP_CombatEffectPlugin.addEffect(aEP_ShuishiDroneLaunch.Speeding(0.15f, drone, proj, null))

          //默认位置是当前的鼠标位置
          var targetLoc = ship.mouseTarget ?: ship.location
          //如果是ai使用，读取母舰的customData，由systemAI放入
          if (ship.customData[aEP_MaoDianDroneLaunchAI.TARGET_KEY] is Vector2f && ship.shipAI != null) {
            targetLoc = ship.customData[aEP_MaoDianDroneLaunchAI.TARGET_KEY] as Vector2f
            ship.customData[aEP_MaoDianDroneLaunchAI.TARGET_KEY] = null
          }

          //落点不可超过系统射程
          val sysRange = aEP_Tool.getSystemRange(ship, SYSTEM_RANGE)
          if (MathUtils.getDistance(targetLoc, ship.location) - ship.collisionRadius > sysRange) {
            val angle = VectorUtils.getAngle(ship.location, targetLoc);
            targetLoc =
              Vector2f(aEP_Tool.getExtendedLocationFromPoint(ship.location, angle, sysRange + ship.collisionRadius))
          }

          //原版getShipAI返回的是被set之前的ai（原版ai），并不是自定义的ai文件
          //不能通过getShipAI is xxx 来cast成自己的方法
          //这里只能setAI了，先初始化，设定好了在覆盖ai
          val ai = aEP_MaoDianDroneAI(drone)
          ai.setToTarget(Vector2f(targetLoc))
          drone.shipAI = ai
          aEP_CombatEffectPlugin.addEffect(ShowLocation(drone, ai))

          if (currDrone != null) {
            val currDrone = currDrone as ShipAPI
            currDrone.fluxTracker.hardFlux = currDrone.maxFlux
          }
          currDrone = drone


        }
      }

    }
  }

  override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {
    return true
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String {
    return aEP_Tool.getInfoTextWithinSystemRange(ship, ship.mouseTarget, SYSTEM_RANGE)
  }


  fun updateIndicator(ship: ShipAPI, level: Float){
    //找一次发光武器
    if(rotator == null ||  red == null || green == null){
      for(w in ship.allWeapons){
        if(!w.isDecorative) continue
        if(w.spec.weaponId.equals("aEP_cru_shanhu_rotator")){
          rotator = w.effectPlugin as aEP_DecoAnimation
          continue
        }
        if(w.spec.weaponId.equals("aEP_cru_shanhu_round_red")){
          red = w.effectPlugin as aEP_DecoAnimation
          continue
        }
        if(w.spec.weaponId.equals("aEP_cru_shanhu_round_green")){
          green = w.effectPlugin as aEP_DecoAnimation
          continue
        }
      }
    }
    val rotator = (rotator?:return)
    val red = (red?:return)
    val green = (green?:return)

    //灯的旋转同步圆盘的
    rotator.setRevoToLevel(1f- level)
    red.decoRevoController.toLevel = rotator.decoRevoController.toLevel
    green.decoRevoController.toLevel = rotator.decoRevoController.toLevel

    green.setGlowToLevel(level)
    red.setGlowToLevel(1f-level)
  }

  fun createVfxOnSpawnDrone(ship: ShipAPI, loc:Vector2f){
    aEP_Tool.spawnCompositeSmoke(loc, 100f, 2f,  Color(250, 250, 250, 175),ship.velocity)
    aEP_Tool.spawnCompositeSmoke(loc, 150f, 3f,  Color(150, 150, 150, 175),ship.velocity)

  }
}

class ShowLocation : aEP_BaseCombatEffect {
  val ai:aEP_MaoDianDroneAI
  constructor(drone:ShipAPI, ai: aEP_MaoDianDroneAI):super(20f,drone){
    this.ai = ai
  }

  override fun advanceImpl(amount: Float) {
    val sprite = Global.getSettings().getSprite("graphics/aEP_FX/frame02.png")

    MagicRender.singleframe(sprite,
      ai.targetLoc?: aEP_ID.VECTOR2F_ZERO,
      Vector2f(60f,60f),
      45f, Color.yellow,true)

    if(ai.stat is aEP_MaoDianDroneAI.HoldShield){
      shouldEnd = true
    }
  }

}