package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.util.IntervalTracker
import combat.impl.VEs.aEP_MovingSmoke
import combat.impl.VEs.aEP_MovingSprite
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Tool
import data.scripts.weapons.aEP_DecoAnimation
import data.scripts.weapons.aEP_b_l_aa40_shot
import data.scripts.weapons.aEP_b_m_h88_shot
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color
import kotlin.math.abs
import kotlin.math.roundToInt

class aEP_YJDefenseFlak : BaseShipSystemScript() {
  companion object{
    const val ID = "aEP_YJDefenseFlak"

    val SMOKE_COLOR= Color(250, 250, 250, 45)

  }

  private lateinit var ship: ShipAPI
  var timeHaveAmmo= 0f
  var timeOutAmmo= 10f
  var timeEject = 0f
  val smokeTracker = IntervalTracker(0.05f,0.05f)
  var ammoIndicate = 0f
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴
    ship = (stats.entity?:return) as ShipAPI
    val maxSystemCharge = ship.system.maxAmmo?:1
    var currSystemCharge = ship.system.ammo?:0


    val amount = aEP_Tool.getAmount(ship)
    if(currSystemCharge > 0 || (currSystemCharge <= 0 && ship.system.ammoReloadProgress >= 0.85f)) {
      timeHaveAmmo += amount
      timeOutAmmo = 0f
    }else{
      timeHaveAmmo = 0f
      timeOutAmmo += amount
    }

    //控制发射口的伸缩旋转和淡入淡出
    //放在非hidden槽位的system武器是会坏的，每帧把血量补满
    for(w in ship.allWeapons){
      if(!w.spec.weaponId.equals("aEP_des_yangji_flak")) continue
      w.currHealth = w.maxHealth
      //装饰武器和系统武器都有自动转向船头的趋势，所以武器本身turn rate要写成0
      val plugin = w.effectPlugin as aEP_DecoAnimation

      //先淡入，再伸出炮管，再转
      if(timeHaveAmmo > 0f){
        val level = ((timeHaveAmmo)/1f).coerceAtLeast(0f).coerceAtMost(1f)
        w.barrelSpriteAPI.color = aEP_Tool.getColorWithAlpha(Color.white,level)
        w.sprite.color = aEP_Tool.getColorWithAlpha(Color.white,level)
      }
      if(timeHaveAmmo > 1f){
        w.ammo = 16
      }
      if(timeHaveAmmo > 1.5f){
        plugin.decoRevoController.start = w.slot.angle - 180f
        plugin.decoRevoController.range = 180f
        plugin.decoRevoController.speed = 1f
        plugin.decoRevoController.toLevel = 1f
      }

      //先收缩炮管，再转，再淡出
      if(timeOutAmmo > 1f){
        w.ammo = 0
      }
      if(timeOutAmmo > 1.5f){
        plugin.decoRevoController.toLevel = 0f
      }
      if(timeOutAmmo > 1.75f){
        plugin.decoRevoController.toLevel = 0f
        val level = 1f - ((timeOutAmmo - 1.75f)/1f).coerceAtLeast(0f).coerceAtMost(1f)
        w.barrelSpriteAPI.color = aEP_Tool.getColorWithAlpha(Color.white,level)
        w.sprite.color = aEP_Tool.getColorWithAlpha(Color.white,level)
      }


      //如果在装配页面中，隐藏炮管，淡化炮塔。
      //在装配页面时，会运行一帧，所以这个数不是0，而是很小的一个数
      if(ship.fullTimeDeployed <= 0.05f){
        w.barrelSpriteAPI.color = Color(0,0,0,0)
        w.sprite.color = Color(0,0,0,0)
      }

    }

    //产生抛弹壳特效
    timeEject -= amount
    timeEject = timeEject.coerceAtLeast(0f)
    if(effectLevel >= 1f){
      timeEject = 2.5f
    }
    if(timeEject < 2f && timeEject > 0f){

      smokeTracker.advance(amount)
      if(smokeTracker.intervalElapsed()) {
        for (slot in ship.hullSpec.allWeaponSlotsCopy) {
          if(slot.id.contains("EJECT")){
            val smokeLoc = slot.computePosition(ship)
            val angle = slot.computeMidArcAngle(ship)
            //弹壳
            val ejectPoint = Vector2f(smokeLoc)
            val ms = aEP_MovingSprite(ejectPoint, Vector2f(2.5f,1f),angle,"graphics/weapons/aEP_b_l_aa40/shred.png")
            ms.lifeTime = 2.4f
            ms.fadeIn = 0.05f
            ms.fadeOut = 0.2f
            ms.color = Color(255,255,255,205)
            ms.angle = angle
            ms.angleSpeed = MathUtils.getRandomNumberInRange(-180f,180f)
            ms.setInitVel(aEP_Tool.speed2Velocity(angle + MathUtils.getRandomNumberInRange(-10f,10f),
              MathUtils.getRandomNumberInRange(80f,120f)))
            ms.setInitVel(ship.velocity)
            ms.stopSpeed = 0.9f
            aEP_CombatEffectPlugin.addEffect(ms)
            //aEP_CombatEffectPlugin.addEffect(aEP_b_m_h88_shot.ShellGlow(ms, aEP_b_l_aa40_shot.SHELL_GLOW))

            //先壳后烟，保证烟在壳上方
            //喷烟雾
            val smoke = aEP_MovingSmoke(smokeLoc)
            smoke.lifeTime = 0.6f
            smoke.fadeIn = 0.25f
            smoke.fadeOut = 0.25f
            smoke.size = 10f
            smoke.sizeChangeSpeed = 50f
            smoke.color = SMOKE_COLOR
            smoke.setInitVel(aEP_Tool.speed2Velocity(angle, 160f))
            smoke.setInitVel(ship.velocity)
            smoke.stopForceTimer.setInterval(0.05f, 0.05f)
            smoke.stopSpeed = 0.95f
            aEP_CombatEffectPlugin.addEffect(smoke)
          }
        }
      }
    }

    //控制弹药指示器
    updateCanisterIndicator(amount)

  }


  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    //复制粘贴
    ship = (stats.entity?:return) as ShipAPI

  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State?, effectLevel: Float): StatusData? {
    if (index == 0) {
      return null
    }
    return null
  }

  fun updateCanisterIndicator(amount: Float){
    val slotLoc = ship.hullSpec.getWeaponSlot("FEED").computePosition(ship)
    val facing = ship.facing - 180f
    var num = (ship.system.ammo).toFloat()
    //num += ship.system.ammoReloadProgress
    num = num.coerceAtMost(4f)

    var startPointOffsetDist = 10f
    if(num > ammoIndicate){
      ammoIndicate += amount
      ammoIndicate = ammoIndicate.coerceAtMost(num)
    }else{
      ammoIndicate -= amount
      ammoIndicate = ammoIndicate.coerceAtLeast(num)
    }

    startPointOffsetDist -= ammoIndicate * 6f

    val startOffset = aEP_Tool.getExtendedLocationFromPoint(slotLoc,facing, -startPointOffsetDist)
    val renderNum =  ammoIndicate.roundToInt()
    for( i in (4-renderNum)..4){
      val canisterLoc = aEP_Tool.getExtendedLocationFromPoint(startOffset, facing, i * 6f)
      val can =  Global.getSettings().getSprite("aEP_FX","yangji_canister")
      MagicRender.singleframe(
        can, canisterLoc, Vector2f(can.width, can.height),
        //magicRender的角度开始点比游戏多90
        facing+90f,
        aEP_Tool.getColorWithAlpha(Color.white,0.9f),
        false, CombatEngineLayers.ABOVE_SHIPS_LAYER)

      //渲染闪光
      val glowLoc = MathUtils.getRandomPointInCircle(canisterLoc,0.2f)
      val glow =  Global.getSettings().getSprite("aEP_FX","yangji_canister_glow")
      MagicRender.singleframe(
        glow, glowLoc, Vector2f(glow.width, glow.height),
        //magicRender的角度开始点比游戏多90
        facing+90f,
        aEP_Tool.getColorWithAlpha(Color.white,1f),
        true, CombatEngineLayers.ABOVE_SHIPS_LAYER)
    }



    //最后渲染供弹口，盖住弹丸
    val feed =  Global.getSettings().getSprite("aEP_FX","yangji_canister_feed")
    MagicRender.singleframe(
      feed, slotLoc, Vector2f(feed.width, feed.height),
      //magicRender的角度开始点比游戏多90
      facing+90f, Color.white, false, CombatEngineLayers.ABOVE_SHIPS_LAYER)

  }

}