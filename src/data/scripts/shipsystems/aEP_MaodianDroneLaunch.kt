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
    const val DECO_WEAPON_DRONE_ID = "aEP_ftr_ut_maodian_full_cover"
    const val DRONE_ID = "aEP_ftr_ut_maodian"
    const val SYSTEM_RANGE = 2250f
  }

  var currDrone: ShipAPI? = null

  var decoWeaponDrone: WeaponAPI? = null
  var decoToAlpha = 1f
  var decoCurrAlpha = 1f

  var rotator: aEP_DecoAnimation? = null
  var red: aEP_DecoAnimation? = null
  var green: aEP_DecoAnimation? = null

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    val amount = aEP_Tool.getAmount(ship)
    //找一次盖板武器
    if(decoWeaponDrone == null){
      for(w in ship.allWeapons){
        if(w.spec.weaponId.equals(DECO_WEAPON_DRONE_ID)){
          decoWeaponDrone = w
          break
        }
      }
    }
    val deco = decoWeaponDrone as WeaponAPI

    //检测当前的currDrone是否活着
    if(currDrone is ShipAPI){
      val curr = currDrone as ShipAPI
      if(aEP_Tool.isDead(curr)) currDrone = null
    }

    //完全冷却为1，刚进入冷却为0
    var cooldownLevel = clamp((ship.system.cooldownRemaining/ship.system.cooldown),0f,1f)
    cooldownLevel = 1f-cooldownLevel
    //完成装填为1，刚开始装填为0
    var ammoLevel = ship.system.ammoReloadProgress
    //如果目前还剩使用次数，就直接改ammoLevel为1
    if(ship.system.ammo > 0) ammoLevel = 1f
    //取小
    var glowLevel = Math.min(cooldownLevel, ammoLevel)
    updateDecoCover(ship,amount, glowLevel)
    updateIndicator(ship, glowLevel)

    //运行一帧
    if(effectLevel >= 1){
      //视觉特效
      createVfxOnSpawnDrone(ship,Vector2f(deco.location))

      val variant = Global.getSettings().getVariant(DRONE_ID)
      val drone = Global.getCombatEngine().createFXDrone(variant)
      //并不是特效无人机，而是实体，去掉这个tag。这个tag会被createFXDrone()自动添加
      drone.tags.remove(Tags.VARIANT_FX_DRONE)
      drone.isDrone = true
      drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f, ship)
      drone.layer = CombatEngineLayers.FIGHTERS_LAYER
      drone.owner = ship.owner
      drone.location.set(deco.location)
      drone.facing = deco.currAngle
      Global.getCombatEngine().addEntity(drone)

      //把武器转头藏好，不要生成以后穿模
      for( w in drone.allWeapons ){
        if(w.slot.id.equals("WS0003")){
          w.currAngle = (drone.facing-10)
        }
        if(w.slot.id.equals("WS0004")){
          w.currAngle = (drone.facing+10)
        }
        if(w.slot.id.equals("WS0005")){
          w.currAngle = (drone.facing+120)
        }
      }
      drone.velocity.set( ship.velocity?: Misc.ZERO)

      //默认位置是当前的鼠标位置
      var targetLoc = ship.mouseTarget?: ship.location
      //如果是ai使用，读取母舰的customData，由systemAI放入
      if(ship.customData[aEP_MaoDianDroneLaunchAI.TARGET_KEY] is Vector2f && ship.shipAI != null) {
        targetLoc = ship.customData[aEP_MaoDianDroneLaunchAI.TARGET_KEY] as Vector2f
        ship.customData[aEP_MaoDianDroneLaunchAI.TARGET_KEY] = null
      }

      //落点不可超过系统射程
      val sysRange = aEP_Tool.getSystemRange(ship, SYSTEM_RANGE)
      if(MathUtils.getDistance(targetLoc,ship.location) - ship.collisionRadius > sysRange){
        val angle = VectorUtils.getAngle(ship.location,targetLoc);
        targetLoc = Vector2f(aEP_Tool.getExtendedLocationFromPoint(ship.location,angle,sysRange+ship.collisionRadius))
      }

      //原版getShipAI返回的是被set之前的ai（原版ai），并不是自定义的ai文件
      //不能通过getShipAI is xxx 来cast成自己的方法
      //这里只能setAI了，先初始化，设定好了在覆盖ai
      val ai = aEP_MaoDianDroneAI(drone)
      ai.setToTarget(Vector2f(targetLoc))
      drone.shipAI = ai
      aEP_CombatEffectPlugin.addEffect(ShowLocation(drone, ai))

      if(currDrone != null){
        val currDrone = currDrone as ShipAPI
        currDrone.fluxTracker.hardFlux = currDrone.maxFlux
      }
      currDrone = drone

    }
  }

  override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {
    return true
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String {
    return aEP_Tool.getInfoTextWithinSystemRange(ship, ship.mouseTarget, SYSTEM_RANGE)
  }

  fun updateDecoCover(ship: ShipAPI, amount: Float, level: Float){
    decoWeaponDrone?:return
    val deco = decoWeaponDrone as WeaponAPI
    decoCurrAlpha = clamp(decoCurrAlpha,0f,1f)
    decoToAlpha = clamp(decoToAlpha,0f,1f)

    //变换alpha
    if(decoToAlpha > decoCurrAlpha){
      val toChange = (decoToAlpha-decoCurrAlpha).coerceAtMost(5f*amount)
      decoCurrAlpha += toChange
    }else{
      val toChange = (decoCurrAlpha-decoToAlpha).coerceAtMost(5f*amount)
      decoCurrAlpha -= toChange
    }

    val glowLevel = (level-0.8f)/0.2f
    //马上准备好时，淡入
    if(glowLevel >= 0.8f){
      decoToAlpha = glowLevel
    }else{
      //1为空贴图，0为正常贴图
      decoToAlpha = 0f
    }

    //根据当前alpha改变贴图
    if(decoCurrAlpha <= 0.1f){
      deco.animation.frame = 1
    }else{
      deco.animation.frame = 0
    }


    //setAlphaMult没有用,直接setColor
    //前35%维持满透明度，后百分之65%才开始变淡
    deco.sprite.color = aEP_Tool.getColorWithAlpha(Color.white,(decoCurrAlpha/0.65f).coerceAtMost(1f))

  }

  fun updateIndicator(ship: ShipAPI, level: Float){
    //找一次盖板武器
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