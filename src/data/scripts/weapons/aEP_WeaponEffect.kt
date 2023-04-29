package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import combat.impl.VEs.aEP_AnchorStandardLight
import combat.impl.VEs.aEP_MovingSmoke
import combat.impl.VEs.aEP_MovingSprite
import combat.impl.VEs.aEP_SmokeTrail
import combat.impl.aEP_BaseCombatEffect
import combat.impl.proj.aEP_StickOnHit
import combat.plugin.aEP_CombatEffectPlugin
import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import combat.util.aEP_ID
import combat.util.aEP_ID.Companion.VECTOR2F_ZERO
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.angleAdd
import combat.util.aEP_Tool.Util.computeDamageToShip
import combat.util.aEP_Tool.Util.firingSmoke
import combat.util.aEP_Tool.Util.firingSmokeNebula
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.spawnSingleCompositeSmoke
import combat.util.aEP_Tool.Util.speed2Velocity
import data.scripts.a111164ModPlugin.MaoDianDrone_ID
import data.scripts.ai.aEP_CruiseMissileAI
import data.scripts.ai.aEP_MaoDianDroneAI
import data.scripts.campaign.intel.aEP_CruiseMissileLoadIntel
import data.scripts.campaign.intel.aEP_CruiseMissileLoadIntel.Companion.LOADING_MAP
import data.scripts.util.MagicAnim
import data.scripts.util.MagicLensFlare
import data.scripts.util.MagicRender
import data.scripts.shipsystems.aEP_MaodianDroneLaunch
import data.scripts.ai.shipsystemai.aEP_MDDroneLaunchAI
import org.dark.shaders.distortion.DistortionShader
import org.dark.shaders.distortion.WaveDistortion
import org.dark.shaders.light.LightShader
import org.dark.shaders.light.StandardLight
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.MathUtils.getRandomNumberInRange
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lazywizard.lazylib.combat.DefenseUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

/**
 * 注意，EveryFrameWeaponEffectPlugin其实和 onHit，onFire共用一个类
 * 在 EveryFrameWeaponEffectPlugin中写入onHit， onFire方法，即使不在弹丸中声明，也会调用对应方法
 * */
class aEP_WeaponEffect : OnFireEffectPlugin, OnHitEffectPlugin, ProximityExplosionEffect, BeamEffectPluginWithReset, EveryFrameWeaponEffectPlugin {
  var effect: Effect? = null
  var everyFrame: EveryFrameWeaponEffectPlugin? = null
  var beamEffect: BeamEffectPluginWithReset? = null
  var didCheckClass = false
  var didCheckBeamEffect = false

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI) {
    //根据projId进行类查找
    projectile?: return
    var weaponId: String? = ""
    if (projectile.weapon != null) weaponId = projectile.weapon.spec.weaponId

    //查找流程结束以后返回非null，就运行对应的方法
    if(effect == null) effect = getEffect(projectile)
    effect?.onHit(projectile, target, point, shieldHit, damageResult, engine, weaponId)
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI) {
    //根据 projId进行类查找
    projectile?: return
    var weaponId = ""
    if (projectile.weapon != null) weaponId = projectile.weapon.spec.weaponId

    //查找流程结束以后返回非 null，就运行对应的方法
    if(effect == null) effect = getEffect(projectile)
    effect?.onFire(projectile, weapon, engine, weaponId)
  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI) {
    //根据 projId进行类查找
    val projectile = originalProjectile?:return
    var weaponId = ""
    if (projectile.weapon != null) weaponId = projectile.weapon.spec.weaponId
    //查找流程结束以后返回非 null，就运行对应的方法
    if(effect == null) effect = getEffect(projectile)
    effect?.onExplosion(explosion, projectile, weaponId)
  }

  //onHit/onFire/onExplosion 是可以共享的，多个不同同种武器的effect变量会指向存在customData里面的同一个类(单列模式)
  private fun getEffect(projectile: DamagingProjectileAPI): Effect? {
    //类缓存，查找过一次的类放进 map，减少调用 forName的次数
    val cache: MutableMap<String?,Effect?> = (Global.getCombatEngine().customData["aEP_WeaponEffect"] as MutableMap<String?,Effect?>?)?: HashMap()

    //在cache里面找projId对应的类，没有就classForName查一个放进cahce
    //classForName也查不到就保持null
    val projId = projectile.projectileSpecId
    var effect: Effect? = null
    if (!cache.containsKey(projId)) {
      try {
        val e = Class.forName(aEP_WeaponEffect::class.java.getPackage().name + "." + projId)
        effect = e.newInstance() as Effect
        cache[projId] = effect
        Global.getLogger(this.javaClass).info("WeaponEffectLoaded :" +e.name)
      } catch (e: ClassNotFoundException) {
        e.printStackTrace()
      } catch (e: InstantiationException) {
        e.printStackTrace()
      } catch (e: IllegalAccessException) {
        e.printStackTrace()
      }
    } else {
      effect = cache[projId]
    }
    Global.getCombatEngine().customData["aEP_WeaponEffect"] = cache
    return effect
  }

  // everyFrameWeaponPlugin 不可以多个武器共享，每个武器everyFrame变量指向的都要是一个独立的类
  override fun advance(amount: Float, engine: CombatEngineAPI?, weapon: WeaponAPI?) {
    //只尝试读取一次
    if(everyFrame == null && !didCheckClass) {
      didCheckClass = true
      try {
        val e = Class.forName(aEP_WeaponEffect::class.java.getPackage().name + "." + weapon?.spec?.weaponId)
        everyFrame = e.newInstance() as EveryFrameWeaponEffectPlugin
        Global.getLogger(this.javaClass).info("WeaponEveryFrameEffectLoaded :" +e.name)
      } catch (e: ClassNotFoundException) {
        e.printStackTrace()
      } catch (e: InstantiationException) {
        e.printStackTrace()
      } catch (e: IllegalAccessException) {
        e.printStackTrace()
      }
    }

    if(everyFrame != null && weapon != null){
      everyFrame?.advance(amount,engine,weapon)
    }
  }

  // beamEffectPlugin 同上
  override fun advance(amount: Float, engine: CombatEngineAPI?, beam: BeamAPI?) {
    //只尝试读取一次
    if(beamEffect == null && !didCheckBeamEffect) {
      didCheckBeamEffect = true
      try {
        val e = Class.forName(aEP_WeaponEffect::class.java.getPackage().name + "." + beam?.weapon?.spec?.weaponId?:"")
        beamEffect = e.newInstance() as BeamEffectPluginWithReset
        Global.getLogger(this.javaClass).info("WeaponEveryFrameEffectLoaded :" +e.name)
      } catch (e: ClassNotFoundException) {
        e.printStackTrace()
      } catch (e: InstantiationException) {
        e.printStackTrace()
      } catch (e: IllegalAccessException) {
        e.printStackTrace()
      }
    }


    if(beamEffect != null && beam != null){
      beamEffect?.advance(amount,engine,beam)
    }
  }

  // beamEffectPlugin with reset
  override fun reset() {
    beamEffect?.reset()
  }
}

/**
  继承的类名要是弹丸的id
  onHit不会被PROXIMITY_FUSE的弹丸触发
  onHit触发在弹丸造成伤害以后
 */
open class Effect {
  constructor()
  open fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {}
  open fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {}
  open fun onExplosion(explosion: DamagingProjectileAPI?, originalProjectile: DamagingProjectileAPI?, weaponId: String?) {}
}

/**
  继承的类名要是武器的id，在本帧武器开火前调用
 */
open class EveryFrame:EveryFrameWeaponEffectPlugin{
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
  }
}

//爆破锤系列
class aEP_as_missile : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile ?:return
    val smokeTrail =  object : aEP_SmokeTrail(projectile,
      9f,
      2.4f,
      18f,
      36f,
      Color(140,140,140,160)){
      val missile = entity as MissileAPI

      override fun advanceImpl(amount: Float) {
        super.advanceImpl(amount)
        if(missile.engineController.isAccelerating) return
        missile.flightTime = missile.flightTime - amount/2f
      }
    }
    smokeTrail.stopSpeed = 0.96f
    smokeTrail.smokeSpreadAngleTracker.speed = 1.6f
    smokeTrail.smokeSpreadAngleTracker.max = 15f
    smokeTrail.smokeSpreadAngleTracker.min = -15f
    smokeTrail.smokeSpreadAngleTracker.randomizeTo()
    smokeTrail.flareColor = Color(255,134,86,240)
    smokeTrail.flareColor2 = Color(152,124,20,240)

    aEP_CombatEffectPlugin.Mod.addEffect(smokeTrail)
    aEP_CombatEffectPlugin.Mod.addEffect(ApproximatePrimer(projectile as MissileAPI))
  }

}
class aEP_as_missile_large : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile ?:return

    val smokeTrail =  object : aEP_SmokeTrail(projectile,
      11f,
      2.8f,
      22f,
      44f,
      Color(140,140,140,160)){

      val missile = entity as MissileAPI
      override fun advanceImpl(amount: Float) {
        super.advanceImpl(amount)
        if(missile.engineController.isAccelerating) return
        missile.flightTime = missile.flightTime - amount/2f
      }
    }
    smokeTrail.stopSpeed = 0.965f
    //因为新的角度算法是按照生成烟的数量变化的，大导弹云更大，生成的也就越少，这里反而需要比小导大才能维持类似的角度变化
    smokeTrail.smokeSpreadAngleTracker.speed = 1.8f
    smokeTrail.smokeSpreadAngleTracker.max = 15f
    smokeTrail.smokeSpreadAngleTracker.min = -15f
    smokeTrail.flareColor = Color(255,134,86,240)
    smokeTrail.flareColor2 = Color(152,124,20,240)

    aEP_CombatEffectPlugin.Mod.addEffect(smokeTrail)
    aEP_CombatEffectPlugin.Mod.addEffect(ApproximatePrimer(projectile as MissileAPI))

  }
}
class ApproximatePrimer(val missile: MissileAPI) : aEP_BaseCombatEffect(0f,missile) {
  companion object {
    val FUSE_RANGE = 100f
  }
  override fun advanceImpl(amount: Float){
    //转向时延长飞行时间
    if(!missile.engineController.isAccelerating){
      missile.flightTime -= amount * 0.5f
    }

    val detectPoint = aEP_Tool.getExtendedLocationFromPoint(missile.location,missile.facing,FUSE_RANGE )
    val sprite = Global.getSettings().getSprite("aEP_FX","noise")
    MagicRender.singleframe(sprite,
      detectPoint,
      Vector2f(20f,20f),
      missile.flightTime*60f, Color.red,false)
    for (s in AIUtils.getNearbyEnemies(missile,400f)) {
      if (s.owner != missile.owner && !s.isFighter && !s.isDrone && !s.isShuttlePod) {
        if(aEP_Tool.getDistForLocToHitShield(detectPoint,s) < 0f){
          explode()
          break
        }
        if (CollisionUtils.getCollisionPoint(missile.location,detectPoint,s) != null) {
          explode()
          break
        }
      }
    }
  }

  private fun explode(){
    val point = missile.location
    val vel = Vector2f(0f, 0f)
    val engine = Global.getCombatEngine()

    //中心点大白光
    engine.addHitParticle(
      point,
      vel, 25f, 1f,
      Color.white
    )

    //随机烟雾
    var SIZE_MULT = 1f
    var numMax = 8
    for (i in 0 until  numMax) {
      val loc = MathUtils.getRandomPointInCircle(point, 60f * SIZE_MULT)
      engine.addNebulaParticle(
        loc, Vector2f(0f,0f),40f*SIZE_MULT, 1.5f,
        0f,0.5f, 2.5f+1.5f*MathUtils.getRandomNumberInRange(0f,1f),
        Color(100, 100, 100, 55))
    }

    //横杠闪光
    MagicLensFlare.createSmoothFlare(Global.getCombatEngine(),
      missile.source,
      missile.location,
      30f,200f, 0f,
      Color(50,50,240),Color(250,100,170))

    //横杠烟雾左,右
    val numOfSmoke = 8
    val maxRange = 80f
    val maxSize = 50f
    val minSize = 25f
    val maxSpeed = 20f
    for (i in 0 until numOfSmoke){
      val angle = missile.facing-90f
      var level = 1f- (i.toFloat())/(numOfSmoke.toFloat())
      val reversedLevel = (1f-level)
      val loc = aEP_Tool.getExtendedLocationFromPoint(missile.location,angle,reversedLevel*maxRange)
      val vel = aEP_Tool.speed2Velocity(angle, reversedLevel*maxSpeed)
      engine.addNebulaParticle(
        loc, vel,maxSize*level+minSize, 1f,
        0f,0.5f, 1f + 1f*level,
        Color(100, 100, 100, 155))
    }
    for (i in 0 until numOfSmoke){
      val angle = missile.facing+90f
      var level = 1f- (i.toFloat())/(numOfSmoke.toFloat())
      val reversedLevel = (1f-level)
      val loc = aEP_Tool.getExtendedLocationFromPoint(missile.location,angle,reversedLevel*maxRange)
      val vel = aEP_Tool.speed2Velocity(angle, reversedLevel*maxSpeed)
      engine.addNebulaParticle(
        loc, vel,maxSize*level+minSize, 1f,
        0f,0.5f, 1f + 1f*level,
        Color(100, 100, 100, 155))
    }

    //生成动能弹丸
    //注意一下BALLISTIC_AS_BEAM并不能被修改初始速度哦
    val pro1 = engine.spawnProjectile(
      missile.source,  //source ship
      missile.weapon,  //source weapon,
      "aEP_as_shot",  //whose proj to be use
      missile.location,  //loc
      missile.facing,  //facing
      null) as DamagingProjectileAPI
    //神奇alex，setDamage要输入baseDamage但是getDamage得到的是加成后的数（但未计入stats里面的加成）
    pro1.damage.damage = missile.damage.baseDamage/3f

    //生成高爆弹丸
    val pro2 = engine.spawnProjectile(
      missile.source,  //source ship
      missile.weapon,  //source weapon,
      "aEP_as_shot2",  //whose proj to be use
      missile.location,  //loc
      missile.facing,  //facing
      null) as DamagingProjectileAPI
    //神奇alex，setDamage要输入baseDamage但是getDamage得到的是加成后的数（但未计入stats里面的加成）
    pro2.damage.damage = missile.damage.baseDamage/3f

    //生成破片弹丸
    val pro3 = engine.spawnProjectile(
      missile.source,  //source ship
      missile.weapon,  //source weapon,
      "aEP_as_shot3",  //whose proj to be use
      missile.location,  //loc
      missile.facing,  //facing
      null) as DamagingProjectileAPI
    //神奇alex，setDamage要输入baseDamage但是getDamage得到的是加成后的数（但未计入stats里面的加成）
    pro3.damage.damage = missile.damage.baseDamage/2f
    //aEP_Tool.addDebugLog("${missile.damage.damage} _ ${pro1.damage.damage} _ ${pro2.damage.damage} _ ${pro3.damage.damage}" )

    engine.spawnExplosion(missile.location,aEP_ID.VECTOR2F_ZERO, Color.white, 50f,1f)
    engine.removeEntity(missile)
  }
}

//气钉枪导弹
class aEP_m_s_harpoon_shot: Effect{
  companion object{
    val HIT_COLOR = Color(165,215,255,200)
    val FRAG_COLOR = Color(230,240,255,178)
    val FRAG_GLOW_COLOR = Color(155,175,255,100)
    var DAMAGE = 200f
    const val KEY = "aEP_m_s_harpoon_shot"

  }

  constructor(){
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) DAMAGE = num.toFloat()
      i += 1
    }
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    engine?:return
    projectile?:return
    point?:return

    Global.getCombatEngine().spawnExplosion(
      point,
      VECTOR2F_ZERO,
      HIT_COLOR,
      170f,
      1.1f)

    //创造一坨碎屑特效
    val facing = projectile.facing
    for(i in 0 until 12){
      val randomSize = getRandomNumberInRange(4f,6f)
      val randomAngle = getRandomNumberInRange(-15f,15f) + facing
      val randomVel = speed2Velocity(randomAngle,400f)
      randomVel.scale(getRandomNumberInRange(0.25f,1f))
      val ms = aEP_MovingSprite(
        point,
        Vector2f(randomSize,randomSize),
        getRandomNumberInRange(0f,360f),
        "graphics/weapons/aEP_large_kinetic_flak/shell.png")
      ms.lifeTime = 1.2f + getRandomNumberInRange(0f,0.6f)
      ms.fadeOut = 0.35f
      ms.color = FRAG_COLOR
      ms.setInitVel(randomVel)
      ms.stopSpeed = 0.875f
      addEffect(ms)
      addEffect(Glow(ms))
    }

    if(shieldHit && target is ShipAPI){
      //施加伤害，只造成软幅能
      engine.applyDamage(
        target,point,
        DAMAGE,
        DamageType.KINETIC,
        0f,
        false,true,
        projectile.source)

    }
  }

  inner class Glow(val ms:aEP_MovingSprite):aEP_BaseCombatEffect(){

    override fun advanceImpl(amount: Float) {
      if(ms.time>=ms.lifeTime){
        shouldEnd = true
        return
      }
      val  level = 1f - ms.time/ms.lifeTime
      Global.getCombatEngine().addSmoothParticle(
        ms.loc,
        aEP_ID.VECTOR2F_ZERO,
        10f*level+5f,
        0.5f + 0.5f*level,
        Global.getCombatEngine().elapsedInLastFrame * 2f,
        FRAG_GLOW_COLOR
      )
    }
  }
}
class aEP_m_l_harpoon_shot : Effect{
  companion object{
    val HIT_COLOR = Color(165,215,255,200)
    val FRAG_COLOR = Color(230,240,255,178)
    val FRAG_GLOW_COLOR = Color(155,175,255,100)
    var DAMAGE = 500f
    const val KEY = "aEP_m_l_harpoon_shot"

  }

  constructor(){
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) DAMAGE = num.toFloat()
      i += 1
    }
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    engine?:return
    projectile?:return
    point?:return
    Global.getCombatEngine().spawnExplosion(
      point,
      VECTOR2F_ZERO,
      HIT_COLOR,
      220f,
      1.1f)

    //创造一坨碎屑特效
    val facing = projectile.facing
    for(i in 0 until 18){
      val randomSize = getRandomNumberInRange(4f,6f)
      val randomAngle = getRandomNumberInRange(-15f,15f) + facing
      val randomVel = speed2Velocity(randomAngle,500f)
      randomVel.scale(getRandomNumberInRange(0.25f,1f))
      val ms = aEP_MovingSprite(
        point,
        Vector2f(randomSize,randomSize),
        getRandomNumberInRange(0f,360f),
        "graphics/weapons/aEP_large_kinetic_flak/shell.png")
      ms.lifeTime = 1.2f + getRandomNumberInRange(0f,0.6f)
      ms.fadeOut = 0.35f
      ms.color = FRAG_COLOR
      ms.setInitVel(randomVel)
      ms.stopSpeed = 0.875f
      addEffect(ms)
      addEffect(Glow(ms))
    }

    if(shieldHit && target is ShipAPI){
      //施加伤害，只造成软幅能
      engine.applyDamage(
        target,point,
        DAMAGE,
        DamageType.KINETIC,
        0f,
        false,true,
        projectile.source)
    }
  }

  inner class Glow(val ms:aEP_MovingSprite):aEP_BaseCombatEffect(){

    override fun advanceImpl(amount: Float) {
      if(ms.time>=ms.lifeTime){
        shouldEnd = true
        return
      }
      val  level = 1f - ms.time/ms.lifeTime
      Global.getCombatEngine().addSmoothParticle(
        ms.loc,
        aEP_ID.VECTOR2F_ZERO,
        10f*level+5f,
        0.5f + 0.5f*level,
        Global.getCombatEngine().elapsedInLastFrame * 2f,
        FRAG_GLOW_COLOR
      )
    }
  }
}

//荡平反应炸弹1,2
class aEP_ftr_bom_nuke_bomb_shot1 : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    engine?: return
    projectile?: return

    projectile.damage.modifier.modifyMult(projectile.projectileSpecId, 0.05f)
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    engine?: return
    projectile?: return
    val mine = engine.spawnProjectile(projectile.source,
      projectile.weapon,
      "aEP_ftr_bom_nuke_bomb2",
      projectile.location,
      projectile.facing,
      projectile.velocity) as MissileAPI
    mine.hitpoints = projectile.hitpoints
  }

}
class aEP_ftr_bom_nuke_bomb_shot2 : Effect(){
  override fun onExplosion(explosion: DamagingProjectileAPI?, originalProjectile: DamagingProjectileAPI?, weaponId: String?) {

    val SIZE_MULT = 0.75f //default = 300;

    val point = explosion?.location?: Vector2f(999999f,999999f)
    val vel = Vector2f(0f, 0f)
    val yellowSmoke = Color(250, 250, 220)
    //add red center smoke
    Global.getCombatEngine().spawnExplosion(
      point,
      vel,
      yellowSmoke,
      300f * SIZE_MULT,
      4f)

    //add white center glow
    Global.getCombatEngine().addHitParticle(
      point,
      vel,
      300 * SIZE_MULT,
      2f,
      5f,
      Color.white)

    //add yellow around glow
    Global.getCombatEngine().addSmoothParticle(
      point,
      vel,
      450 * SIZE_MULT,
      0.8f,
      4f,
      Color.yellow)

    //create distortion
    val wave = WaveDistortion(point, Vector2f(0f, 0f))
    wave.setLifetime(1f)
    wave.size = 300f * SIZE_MULT
    wave.fadeInSize(1.5f)
    wave.intensity = 160f
    wave.fadeOutIntensity(1.5f)
    DistortionShader.addDistortion(wave)

    //create smokes
    val numMax = 24
    var angle = 0f
    for (i in 0 until numMax) {
      val loc = aEP_Tool.getExtendedLocationFromPoint(point, angle, 150f * SIZE_MULT)
      val sizeGrowth = 60 * SIZE_MULT
      val sizeAtMin = 175 * SIZE_MULT
      val moveSpeed = 60f * SIZE_MULT
      val smoke = aEP_MovingSmoke(loc)
      smoke.setInitVel(aEP_Tool.speed2Velocity(VectorUtils.getAngle(point, loc), moveSpeed))
      smoke.fadeIn = 0.1f
      smoke.fadeOut = 0.3f
      smoke.lifeTime = 3f
      smoke.sizeChangeSpeed = sizeGrowth
      smoke.size = sizeAtMin
      smoke.color = Color(200, 200, 200, MathUtils.getRandomNumberInRange(50, 100))
      aEP_CombatEffectPlugin.addEffect(smoke)
      angle += 360f / numMax
    }
  }
}

//破门锥
class aEP_breakin_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    val MAX_OVERLOAD_TIME = 2f

    point?: return
    val vel = Vector2f(0f, 0f)
    if (target !is ShipAPI) return
    if (shieldHit) {
      engine!!.applyDamage(
        target,  //target
        point,  // where to apply damage
        projectile!!.damage.fluxComponent,  // amount of damage
        DamageType.KINETIC,  // damage type
        0f,  // amount of EMP damage (none)
        false,  // does this bypass shields? (no)
        true,  // does this deal soft flux? (no)
        projectile.source
      )
      if (target.fluxTracker.isOverloaded) {
        target.fluxTracker.stopOverload()
        target.fluxTracker.beginOverloadWithTotalBaseDuration(MAX_OVERLOAD_TIME)
      }
    }


    var wave = WaveDistortion(point, vel)
    wave.size = 200f
    wave.intensity = 50f
    wave.fadeInSize(0.75f)

    wave.setLifetime(0f)
    DistortionShader.addDistortion(wave)
    wave = WaveDistortion(point, vel)
    wave.size = 200f
    wave.intensity = 20f
    wave.fadeInSize(1f)
    wave.setLifetime(0f)
    DistortionShader.addDistortion(wave)


    val color = Color(240, 240, 240, 240)
    val ring = aEP_MovingSprite(point,
      Vector2f(0f, 0f),
      0f,
      VectorUtils.getAngle(point, target.getLocation()),
      0f,
      0f,
      1f,
      Vector2f(450f, 900f),
      Vector2f(8f, 24f),
      "aEP_FX.ring",
      color)
    addEffect(ring)
    CombatUtils.applyForce(target, projectile!!.velocity, 100f)
  }



}

//异象导弹
class aEP_cap_nuanchi_missile_shot2 : Effect(){
  companion object{
    const val EMP_CLOUD_RADIUS = 200f
    const val EMP_CLOUD_END_RADIUS_MULT = 3f
    const val EMP_PER_HIT = 500f //一秒电四次

    const val EMP_WARHEAD_WEAPON_ID = "aEP_cap_nuanchi_missile3"

    val CLOUD_COLOR = Color(99,79,233,96)
    val ARC_COLOR = Color(102,143,197,87)
    val ARC_COLOR_FRINGE = Color(255,255,255,45)
  }

  fun onFire2(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    /*addEffect(object : aEP_BaseCombatEffect(){
      init { init(projectile) }
      val m = projectile as MissileAPI
      var FUSE_RANGE = 125f
      var MAX_RANGE = 1600f
      var ACCELERATE_MOD = -50f
      var SLOW_PERCENT = 0.5f // speed - speed * SLOW_PERCENT (per seccond)
      var fused = false
      var EXPLOSION = Color(40, 40, 150, 100)
      override fun advanceImpl(amount: Float) {
        //get nearest enemy
        val target = AIUtils.getNearestEnemy(m) ?: return
        if(MathUtils.getDistance(target,m) > FUSE_RANGE) return
        fused = true
        cleanup()
      }

      //因为 entity已经被初始化，导弹消失，或者引信触发，都会触发这个
      override fun readyToEnd() {
        //既非引信，又非撞爆，不触发效果
        if(!fused && !m.didDamage()) return

        //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(f),dist + "",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,5f);
        //create visual effect
        //create ring
        val ring = aEP_SpreadRing(1600f,
          MAX_RANGE,
          Color(40, 40, 150, 180),
          0f,
          MAX_RANGE,
          m.location)
        ring.initColor.setToColor(40f, 40f, 40f, 10f, 1f)
        ring.layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER)
        addEffect(ring)

        //create distortion
        val wave = WaveDistortion(m.location, Vector2f(0f, 0f))
        wave.size = MAX_RANGE
        wave.setLifetime(1f)
        wave.fadeInSize(1f)
        wave.intensity = 200f
        wave.fadeOutIntensity(1f)
        DistortionShader.addDistortion(wave)
        //create distortion2
        val wave2 = WaveDistortion(m.location, Vector2f(0f, 0f))
        wave2.size = MAX_RANGE - FUSE_RANGE
        wave2.setLifetime(1f)
        wave2.fadeInSize(1f)
        wave2.intensity = -300f
        wave2.fadeOutIntensity(1f)
        DistortionShader.addDistortion(wave2)

        //add slow buff
        for (s in CombatUtils.getShipsWithinRange(m.location, MAX_RANGE)) {
          //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(),s.getName() + "",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,5f);
          if (s.isAlive && s.owner != m.owner) {
            //create debuff effect
            aEP_BuffEffect.addThisBuff(s, YXOnHit(3f, 1f, s, true, 1f, ACCELERATE_MOD, SLOW_PERCENT, m.location, MAX_RANGE))
          }
        }
        //loc, vel, color, size, duration
        //loc, vel, color, size, duration
        engine!!.spawnExplosion(m.location, Vector2f(0f, 0f), EXPLOSION, 500f, 0.5f)
        val spec = DamagingExplosionSpec(
          0.2f,
          FUSE_RANGE + 300f,
          FUSE_RANGE,
          m.damageAmount,
          m.damageAmount / 4,
          CollisionClass.MISSILE_NO_FF, CollisionClass.MISSILE_NO_FF,
          0f, 0f, 0f, 0,
          EXPLOSION, EXPLOSION
        )
        spec.damageType = m.damage.type
        engine!!.spawnDamagingExplosion(spec, m.weapon.ship, m.location)
        aEP_Tool.killMissile(m, engine)
      }
    })*/
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    projectile ?: return
    for( i in 0 until 8 ){
      val angle = i * 45f
      val loc = aEP_Tool.getExtendedLocationFromPoint(projectile.location,angle, EMP_CLOUD_RADIUS/2f)
      val cloudEntity = engine?.spawnProjectile(projectile.source,projectile.weapon,EMP_WARHEAD_WEAPON_ID,loc,angle,null) as MissileAPI
      cloudEntity.source = projectile.source
      aEP_CombatEffectPlugin.addEffect(EmpArcCloud(cloudEntity as MissileAPI,4f))
    }

  }

  class EmpArcCloud : aEP_BaseCombatEffect {
    val arcTracker = IntervalUtil(0.25f,0.25f)
    val smokeTracker = IntervalUtil(0.125f,0.125f)

    constructor(entity: CombatEntityAPI, lifeTime: Float){
      init(entity)
      this.lifeTime = lifeTime

      val engine = Global.getCombatEngine()
      val spec = DamagingExplosionSpec(1f, EMP_CLOUD_RADIUS, EMP_CLOUD_RADIUS/2f,
        100f,50f,
        CollisionClass.HITS_SHIPS_AND_ASTEROIDS, CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
        20f,10f,3f,0, CLOUD_COLOR, CLOUD_COLOR)
      spec.isUseDetailedExplosion = true
      spec.detailedExplosionFlashDuration = 0.3f
      spec.detailedExplosionFlashRadius = 200f
      spec.detailedExplosionRadius = EMP_CLOUD_RADIUS
      engine.spawnDamagingExplosion(spec,(entity as MissileAPI).source,entity.location)
    }

    override fun advanceImpl(amount: Float) {
      val engine = Global.getCombatEngine()
      val mine = entity as MissileAPI
      val effectiveLevel = time/lifeTime;
      val nowSize = EMP_CLOUD_RADIUS * EMP_CLOUD_END_RADIUS_MULT * effectiveLevel

      smokeTracker.advance(amount)
      arcTracker.advance(amount)
      if(smokeTracker.intervalElapsed()){
        engine.addNebulaParticle(
          mine.location, aEP_ID.VECTOR2F_ZERO,
          nowSize, 2f,
          0.25f,0.25f,1.5f, CLOUD_COLOR)
      }

      if(arcTracker.intervalElapsed()){
        val weightedPicker = WeightedRandomPicker<CombatEntityAPI>()

        for(e in CombatUtils.getEntitiesWithinRange(mine.location,nowSize/2f)){
          if(e.collisionClass == CollisionClass.NONE) continue
          if(e is MissileAPI){
            weightedPicker.add(e,1f)
            continue
          } else if (e is ShipAPI){
            if(!e.isAlive || e.isHulk) continue
            if(e.isFighter) weightedPicker.add(e,10f)
            else weightedPicker.add(e,100f)
          }
        }

        val target = weightedPicker.pickAndRemove()?: return
        mine.source?: return

        //穿盾emp的source不可为空
        engine.spawnEmpArcPierceShields(mine.source,
          MathUtils.getRandomPointInCircle(Vector2f(mine.location), nowSize/2f ),
          null,
          target,
          DamageType.ENERGY, 10f,EMP_PER_HIT,
          target.collisionRadius + nowSize/2f,//这是电弧的最大长度，如果电弧发出点在船头，想电船尾电不到就会点空气
          "tachyon_lance_emp_impact",12f,
          ARC_COLOR_FRINGE,
          ARC_COLOR)
      }

    }
  }
}

//对流 温跃层主炮 无光层加速器
class aEP_cap_duiliu_main_shot : Effect(){
  companion object{
    val BLINK_COLOR = Color(255,50,50,145)
  }

  var damage = 750f
  init {
    val hlString = Global.getSettings().getWeaponSpec(aEP_cap_duiliu_main_shot::class.simpleName?.replace("_shot",""))?.customPrimaryHL
    damage = hlString?.split("|")?.get(2)?.toFloat() ?: damage
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    //create bright spark at center
    engine!!.addSmoothParticle(
      projectile!!.location,
      Vector2f(0f, 0f),  //velocity
      400f,
      2f,  // brightness
      0.12f,  //particle live time
      Color(250, 200, 200, 250)
    )

    //create bright spark at center
    engine.addSmoothParticle(
      projectile.location,
      Vector2f(0f, 0f),  //velocity
      200f,
      2f,  // brightness
      0.25f,  //particle live time
      Color(255, 255, 255, 255)
    )

    //create spark at center
    engine.addSmoothParticle(
      projectile.location,
      Vector2f(0f, 0f),  //velocity
      1000f,
      1f,  // brightness
      0.15f,  //particle live time
      Color(250, 100, 100, 150)
    )

    val ship = weapon?.ship?: return
    //move deco weapon
    for (w in ship.getAllWeapons()) {
      if (w.slot.id.contains("RW")) {
        if (w.slot.id.contains("01")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.51f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 8f, 450f, w.currAngle - 90, Color(250, 50, 50, 31), Color(250, 100, 100, 1))
        } else if (w.slot.id.contains("02")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.58f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 12f, 480f, w.currAngle - 90, Color(250, 50, 50, 40), Color(250, 100, 100, 1))
        } else if (w.slot.id.contains("03")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.65f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 15f, 500f, w.currAngle - 90, Color(250, 50, 50, 50), Color(250, 100, 100, 1))
        } else if (w.slot.id.contains("04")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.72f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 18f, 600f, w.currAngle - 90, Color(250, 50, 50, 60), Color(250, 100, 100, 1))
        } else if (w.slot.id.contains("05")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.79f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 21f, 600f, w.currAngle - 90, Color(250, 50, 50, 71), Color(250, 100, 100, 41))
        } else if (w.slot.id.contains("06")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.86f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 24f, 720f, w.currAngle - 90, Color(250, 50, 50, 90), Color(250, 100, 100, 51))
        } else if (w.slot.id.contains("07")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.93f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 27f, 750f, w.currAngle - 90, Color(250, 50, 50, 90), Color(250, 100, 100, 60))
        } else if (w.slot.id.contains("08")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(1f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 30f, 800f, w.currAngle - 90, Color(250, 50, 50, 100), Color(250, 100, 100, 80))
        }
        val glowLevel = (w.effectPlugin as aEP_DecoAnimation).getDecoGlowController().effectiveLevel
        if (glowLevel < 0.1) continue
        //add glow
        val light = StandardLight(w.slot.computePosition(ship), Vector2f(0f, 0f), Vector2f(0f, 0f), null)
        light.intensity = MathUtils.clamp(glowLevel, 0f, 1f)
        light.size = 40f
        light.setColor(Color(250, 50, 50, (250 * glowLevel).toInt()))
        light.fadeIn(0f)
        light.setLifetime(1f)
        light.autoFadeOutTime = 1f
        LightShader.addLight(light)
        addEffect(aEP_AnchorStandardLight(light, ship, 1f))
      }
    }
  }
  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    if(target !is ShipAPI) return
    val IMPULSE = 30000f
    projectile?:return
    point ?: return
    target ?: return
    aEP_Tool.applyImpulse(target, projectile.facing,IMPULSE)
    if (shieldHit) {
      val onHit = object :aEP_StickOnHit(
        6.1f,
        target,
        point,
        "weapons.DL_pike_shot_inShield",
        "weapons.DL_pike_shot",
        projectile.facing,
        true){
        val blinkTracker = IntervalUtil(1f,1f)

        override fun advanceImpl(amount: Float) {
          super.advanceImpl(amount)
          blinkTracker.advance(amount)
          if(blinkTracker.intervalElapsed()){
            Global.getCombatEngine().addSmoothParticle(loc,aEP_ID.VECTOR2F_ZERO,200f,1f,0.5f,0.5f, BLINK_COLOR)
          }
        }

        override fun readyToEnd() {
          super.readyToEnd()
          if(time < lifeTime) return
          val spec = DamagingExplosionSpec(
            0.0001f,
            100f,20f,
            damage,damage/3f,
            CollisionClass.HITS_SHIPS_AND_ASTEROIDS,CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
            10f,10f,1f,
            100,Color.yellow,Color(240,124,20,200)
          )
          spec.damageType = DamageType.HIGH_EXPLOSIVE
          Global.getCombatEngine().spawnDamagingExplosion(spec,projectile.source,loc,true)
        }
      }
      onHit.sprite.setSize(10f,50f)
      addEffect(onHit)
    } else {
      val onHit = object :aEP_StickOnHit(
        6.1f,
        target,
        point,
        "weapons.DL_pike_shot_inHull",
        "weapons.DL_pike_shot",
        projectile.facing,
        false){
        val blinkTracker = IntervalUtil(1f,1f)

        override fun advanceImpl(amount: Float) {
          super.advanceImpl(amount)
          blinkTracker.advance(amount)
          if(blinkTracker.intervalElapsed()){
            Global.getCombatEngine().addSmoothParticle(loc,aEP_ID.VECTOR2F_ZERO,200f,1f,0.5f,0.5f, BLINK_COLOR)
          }
        }

        override fun readyToEnd() {
          super.readyToEnd()
          //时间到达前由于关盾导致的脱离，则不爆炸
          if(time < lifeTime-0.1f) return
          val spec = DamagingExplosionSpec(
            0.0001f,
            100f,20f,
            damage,damage/3f,
            CollisionClass.HITS_SHIPS_AND_ASTEROIDS,CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
            10f,10f,1f,
            100,Color.yellow,Color(240,124,20,200)
          )
          spec.damageType = DamageType.HIGH_EXPLOSIVE
          Global.getCombatEngine().spawnDamagingExplosion(spec,projectile.source,loc,true)
        }
      }
      onHit.sprite.setSize(10f,50f)
      addEffect(onHit)
    }

  }
}

//内波 主炮 黑星改
class aEP_cap_neibo_main_shot : Effect(){
  var chance = 0.25f
  var damage = 100f
  init {
    val spec = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.replace("_shot",""))
    chance = spec?.customPrimaryHL?.split("|")?.get(0)?.toFloat()?.div(100f) ?: 0.25f
    damage = spec?.customPrimaryHL?.split("|")?.get(1)?.toFloat() ?: 100f
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    if(shieldHit) return
    if(target !is ShipAPI) return
    if(MathUtils.getRandomNumberInRange(0f,1f) > chance) return
    engine?.applyDamage(
      target,point,
      damage,DamageType.FRAGMENTATION,
      0f,
      true,false,
      projectile?.source,false)
    //红色爆炸特效
    engine?.addSmoothParticle(point,aEP_ID.VECTOR2F_ZERO,160f,1f,0.5f,0.2f,Color.red)
    var randomVel = MathUtils.getRandomPointInCone(point,75f, angleAdd(projectile?.facing?:0f,-200f),angleAdd(projectile?.facing?:0f,160f))
    randomVel = Vector2f(randomVel.x- (point?.x?:0f),randomVel.y-(point?.y?:0f))
    engine?.spawnExplosion(point, Vector2f(target.velocity.x + randomVel.x,target.velocity.y+ randomVel.y) , aEP_b_l_dg3_shot.EXPLOSION_COLOR,120f,0.8f)
  }
}

//平定主炮
class aEP_cru_pingding_main :EveryFrame(){
  val frontL1 = Global.getSettings().getSprite("weapons","aEP_PD_b")
  val frontL2 = Global.getSettings().getSprite("weapons","aEP_PD_f")
  val frontR1 = Global.getSettings().getSprite("weapons","aEP_PD_b")
  val frontR2 = Global.getSettings().getSprite("weapons","aEP_PD_f")
  val backL1 = Global.getSettings().getSprite("weapons","aEP_PD_b")
  val backL2 = Global.getSettings().getSprite("weapons","aEP_PD_f")
  val backR1 = Global.getSettings().getSprite("weapons","aEP_PD_b")
  val backR2 = Global.getSettings().getSprite("weapons","aEP_PD_f")
  val br = Global.getSettings().getSprite("weapons","aEP_PD_br")
  val id = "aEP_cru_pingding_main"

  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    val useLevel = MathUtils.clamp(weapon.chargeLevel * 2f,0f,1f)
    val ship = weapon?.ship?:return
    val effectLevel = weapon.chargeLevel?: 0f
    var layer = CombatEngineLayers.BELOW_SHIPS_LAYER
    if(useLevel > 0.9f){
      layer = CombatEngineLayers.CRUISERS_LAYER
    }

    //开关矢量引擎
    for(w in ship.allWeapons){
      if(!w.slot.isDecorative) continue
      if(!w.spec.weaponId.equals("aEP_thruster")) continue
      val plugin = w.effectPlugin as aEP_ThrusterAnimation
      plugin.enable = false
      if(effectLevel >= 0.6f){
        plugin.enable = true
      }
    }

    //禁系统
    if(effectLevel > 0.1f && ship.system != null){
      if(ship.system.cooldownRemaining < 0.25f){
        ship.system.cooldownRemaining = 0.25f
      }
    }

    //夹最大速度
    if(weapon.cooldownRemaining <= 0.1f){
      ship.mutableStats.maxSpeed.modifyMult(id,(1f - useLevel)*0.75f + 0.25f)
    }else{
      ship.mutableStats.maxSpeed.modifyMult(id,1f)
    }

    //关盾
    if(ship.shield != null && effectLevel > 0.25f){
      ship.shield.toggleOff()
    }

    //关v排
    if(effectLevel > 0.1f){
      ship.mutableStats.ventRateMult.modifyMult(id,0f)
    }else{
      ship.mutableStats.ventRateMult.modifyMult(id,1f)
    }

    //引擎渲染反推
    if(effectLevel > 0.5f){
      //比较神奇，按加速，会覆盖掉setFlameLevel，同时setFlameLevel的尾焰会莫名的粗短
      for(e in ship.engineController.shipEngines){
        //shift * to是最终量，durIn和Out决定变化速度
        //但是这里每帧都会调用然后刷新,每帧都重新开始淡入，结果是0.5f左右，不是1f
        //in不能为0
        if(ship.engineController.isStrafingLeft || ship.engineController.isStrafingRight || ship.engineController.isAcceleratingBackwards){
          ship.engineController.extendWidthFraction.shift(this,-0.7f*effectLevel,0.000001f,0f,1f)
          ship.engineController.extendGlowFraction.shift(this,-0.7f*effectLevel,0.000001f,0f,1f)
          ship.engineController.extendLengthFraction.shift(this,1.2f*effectLevel,0.000001f,0f,1f)
          ship.engineController.setFlameLevel(e.engineSlot,1f)
        }else if(ship.engineController.isDecelerating){
          ship.engineController.extendWidthFraction.shift(this,-0.7f*effectLevel,0.000001f,0f,1f)
          ship.engineController.extendGlowFraction.shift(this,-0.7f*effectLevel,0.000001f,0f,1f)
          ship.engineController.extendLengthFraction.shift(this,1.2f*effectLevel,0.000001f,0f,1f)

          ship.engineController.setFlameLevel(e.engineSlot,1f)
        }else{
          ship.engineController.extendLengthFraction.shift(this,1.2f*effectLevel,0.000001f,0f,1f)
          ship.engineController.extendGlowFraction.shift(this,1.2f*effectLevel,0.000001f,0f,1f)
          ship.engineController.setFlameLevel(e.engineSlot,1f)
        }

      }
    }

    //控制动画
    if (ship.isAlive() && !ship.isHulk) {
      for (slot in ship.getHullSpec().getAllWeaponSlotsCopy()) {
        if (slot.id.contains("FOOT_F_L")) {
          val angle = slot.computeMidArcAngle(ship)
          val slotLoc = slot.computePosition(ship)
          //1是后面的，2是前面的
          val loc1 = getExtendedLocationFromPoint(slotLoc, angle, MagicAnim.smooth(useLevel) * 20)
          val loc2 = getExtendedLocationFromPoint(slotLoc, angle , MagicAnim.smooth(useLevel) * 53)
          //magicLib的渲染，默认正向是朝左不是朝上
          MagicRender.singleframe(frontL2,loc2,Vector2f(44f,45f),angle,Color.white,false,layer)
          MagicRender.singleframe(frontL1,loc1,Vector2f(25f,15f),angle,Color.white,false,layer)
        }else if(slot.id.contains("FOOT_F_R")) {
          val angle = slot.computeMidArcAngle(ship)
          val slotLoc = slot.computePosition(ship)
          //1是后面的，2是前面的
          val loc1 = getExtendedLocationFromPoint(slotLoc, angle, MagicAnim.smooth(useLevel) * 20)
          val loc2 = getExtendedLocationFromPoint(slotLoc, angle , MagicAnim.smooth(useLevel) * 53)
          //先渲染远端的
          MagicRender.singleframe(frontR2,loc2,Vector2f(44f,45f),angle,Color.white,false,layer)
          MagicRender.singleframe(frontR1,loc1,Vector2f(25f,15f),angle,Color.white,false,layer)
        }else if(slot.id.contains("FOOT_B_L")) {
          val angle = slot.computeMidArcAngle(ship)
          val slotLoc = slot.computePosition(ship)
          //1是后面的，2是前面的
          val loc1 = getExtendedLocationFromPoint(slotLoc, angle, MagicAnim.smooth(useLevel) * 20)
          val loc2 = getExtendedLocationFromPoint(slotLoc, angle , MagicAnim.smooth(useLevel) * 53)
          MagicRender.singleframe(backL2,loc2,Vector2f(44f,45f),angle,Color.white,false,layer)
          MagicRender.singleframe(backL1,loc1,Vector2f(25f,15f),angle,Color.white,false,layer)
        }else if(slot.id.contains("FOOT_B_R")) {
          val angle = slot.computeMidArcAngle(ship)
          val slotLoc = slot.computePosition(ship)
          //1是后面的，2是前面的
          val loc1 = getExtendedLocationFromPoint(slotLoc, angle, MagicAnim.smooth(useLevel) * 20)
          val loc2 = getExtendedLocationFromPoint(slotLoc, angle , MagicAnim.smooth(useLevel) * 53)
          MagicRender.singleframe(backR2,loc2,Vector2f(44f,45f),angle,Color.white,false,layer)
          MagicRender.singleframe(backR1,loc1,Vector2f(25f,15f),angle,Color.white,false,layer)
        }else if(slot.id.contains("FRONT_BR")) {
          val angle = slot.computeMidArcAngle(ship)
          val slotLoc = slot.computePosition(ship)
          //1是后面的，2是前面的
          val loc1 = getExtendedLocationFromPoint(slotLoc, angle, MagicAnim.smooth(useLevel) * 100)
          MagicRender.singleframe(br,loc1,Vector2f(130f,30f),angle,Color.white,false,layer)
        }
      }
    }
  }
}
class aEP_cru_pingding_main_shot : Effect(){

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val color = Color(240, 240, 240, 255)
    projectile?:return
    weapon?: return
    val ship:ShipAPI? = weapon.ship

    //add hit glow if it pass through missiles
    engine.addHitParticle(
      projectile.location,
      Vector2f(0f, 0f),
      800f,
      1f,
      0.33f,
      0.2f,
      Color.white)

    var ms = aEP_MovingSprite(
      projectile.location,
      aEP_Tool.speed2Velocity(weapon.currAngle, 150f),
      0f,
      weapon.currAngle - 90f,
      0f,
      0f,
      0.42f,
      Vector2f(1600f, 400f),
      Vector2f(24f, 6f),
      "aEP_FX.ring",
      color)
    ms.setInitVel(weapon.ship.velocity )
    addEffect(ms)

    ms = aEP_MovingSprite(
      projectile.getLocation(),
      aEP_Tool.speed2Velocity(weapon.currAngle, 150f),
      0f,
      weapon.currAngle - 90f,
      0f,
      0f,
      0.35f,
      Vector2f(2400f, 600f),
      Vector2f(24f, 6f),
      "aEP_FX.ring",
      color)
    ms.setInitVel(weapon.ship.velocity)
    addEffect(ms)

    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 30f
    param.smokeEndSizeMult = 2f
    param.smokeSpread = 30f
    param.maxSpreadRange = 45f

    param.smokeInitSpeed = 300f
    param.smokeStopSpeed = 0.85f

    param.smokeTime = 2.25f
    param.smokeNum = 50
    param.smokeAlpha = 0.1f
    firingSmoke(weapon.getFirePoint(0),weapon.currAngle,param, weapon.ship)

    val smokeTrail = aEP_SmokeTrail(projectile,
      20f,1.65f,30f,40f,Color(240,240,240,120))
    smokeTrail.stopSpeed = 0.975f
    smokeTrail.smokeSpreadAngleTracker.speed = 3f
    smokeTrail.smokeSpreadAngleTracker.max = 15f
    smokeTrail.smokeSpreadAngleTracker.min = -15f
    addEffect(smokeTrail)

    //最中心的黄色火焰
    Global.getCombatEngine().spawnExplosion(projectile.location,aEP_ID.VECTOR2F_ZERO,Color(185,105,10),80f,1f)

    //create side SmokeFire
    createFanSmoke(projectile.getSpawnLocation(), aEP_Tool.angleAdd(weapon.currAngle, 90f), weapon!!.ship)
    createFanSmoke(projectile.getSpawnLocation(), aEP_Tool.angleAdd(weapon.currAngle, -90f), weapon!!.ship)

    ship ?: return
    val blowBack = speed2Velocity(ship.facing-180f,20f)
    ship.velocity.set(ship.velocity.x + blowBack.x, ship.velocity.y+blowBack.y)
    //后坐力抵消
    for(e in ship?.engineController?.shipEngines?:return){
      if(e.isSystemActivated) continue
      if(e.engineSlot.width <= 1f) continue
      val loc = e.engineSlot.computePosition(ship.location, ship.facing)
      val vel = aEP_Tool.speed2Velocity(e.engineSlot.computeMidArcAngle(ship.facing), 40f)
      Global.getCombatEngine().addSmoothParticle(
        loc,
        Vector2f(0f, 0f),
        300f,  //size
        1f,  //brightness
        0.35f,
        0.3f,
        Color(255, 120, 120, 255)
      )
      val ms = aEP_MovingSmoke(loc)
      ms.setInitVel(vel)
      ms.stopSpeed = 1f
      ms.lifeTime = 1f
      ms.fadeIn = 0.15f
      ms.fadeOut = 0.45f
      ms.size = 40f
      ms.sizeChangeSpeed = 10f
      ms.color = Color(255, 120, 120, 155)
      addEffect(ms)

      Global.getCombatEngine().spawnExplosion(loc,vel,Color(255, 255, 255, 120),80f,0.8f)
    }

    //距离补偿
    if(projectile is MissileAPI){
      val speedNow = projectile.spec.launchSpeed
      val maxSpeed = projectile.maxSpeed
      val range = projectile.maxRange
      var acc = projectile.acceleration
      if(maxSpeed <= speedNow) return
      if(acc <= 0) return
      if(range <= 0) return
      val speedDiff = maxSpeed - speedNow
      val timeToAcc = speedDiff/acc
      val timeCompensation = timeToAcc/2f
      projectile.maxFlightTime = range/maxSpeed
      projectile.maxFlightTime += timeCompensation
    }

  }

  override fun onExplosion(explosion: DamagingProjectileAPI?, originalProjectile: DamagingProjectileAPI?, weaponId: String?) {
    val point = explosion?.location?: return
    val weapon = originalProjectile?.weapon?: return
    var hitColor = Color(240, 120, 50, 200)

    //create sparks
    var num = 1
    while (num <= 8) {
      var onHitAngle = angleAdd(originalProjectile.facing,-180f)
      onHitAngle += MathUtils.getRandomNumberInRange(-60, 60)
      val speed = MathUtils.getRandomNumberInRange(100, 200).toFloat()
      val lifeTime = MathUtils.getRandomNumberInRange(1f, 3f)
      addEffect(Spark(point, aEP_Tool.speed2Velocity(onHitAngle, speed), aEP_Tool.speed2Velocity(onHitAngle - 180f, speed / lifeTime), lifeTime))
      num ++
    }

    //create smokes
    num = 1
    while (num <= 28) {
      val loc = MathUtils.getRandomPointInCircle(point, 300f)
      val sizeGrowth = MathUtils.getRandomNumberInRange(0, 100).toFloat()
      val sizeAtMin = MathUtils.getRandomNumberInRange(100, 400).toFloat()
      val moveSpeed = MathUtils.getRandomNumberInRange(50, 100).toFloat()
      val ms = aEP_MovingSmoke(loc)
      ms.setInitVel(aEP_Tool.speed2Velocity(VectorUtils.getAngle(point, loc), moveSpeed))
      ms.lifeTime = 3f
      ms.fadeIn = 0f
      ms.fadeOut = 1f
      ms.size = sizeAtMin
      ms.sizeChangeSpeed = sizeGrowth
      ms.color = Color(100, 100, 100, MathUtils.getRandomNumberInRange(80, 180))
      addEffect(ms)
      num ++
    }

    //create explode
    Global.getCombatEngine().spawnExplosion(
      point,  //color
      Vector2f(0f, 0f),  //vel
      hitColor,  //color
      400f,  //size
      1f) //duration
    Global.getCombatEngine().addNegativeParticle(
      point,
      Vector2f(0f, 0f),
      200f,
      0.5f,
      0.5f,
      Color(240, 240, 240, 200))
    Global.getCombatEngine().addSmoothParticle(
      point,
      Vector2f(0f, 0f),
      300f,
      0.5f,
      0.2f,
      hitColor)

    //play sound
    Global.getSoundPlayer().playSound(
      "aEP_RW_hit",
      1f, 2f,  // pitch,volume
      weapon.location,  //location
      Vector2f(0f, 0f)
    ) //velocity

    //add hit glow if it pass through missiles
    Global.getCombatEngine().addHitParticle(
      point,
      Vector2f(0f, 0f),
      400f,
      1f,
      0.15f,
      0.6f,
      hitColor)
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    engine?: return
    point?: return
    val projId = projectile?.projectileSpecId?: return
    val ship = projectile.source?: return
    val target = target?:return
    val weapon = projectile.weapon?: return
    var hitColor = Color(240, 120, 50, 200)


    //create sparks
    var num = 1
    while (num <= 8) {
      var onHitAngle = VectorUtils.getAngle(target.getLocation(), point)
      onHitAngle += MathUtils.getRandomNumberInRange(-60, 60)
      val speed = MathUtils.getRandomNumberInRange(100, 200).toFloat()
      val lifeTime = MathUtils.getRandomNumberInRange(1f, 3f)
      addEffect(Spark(point, aEP_Tool.speed2Velocity(onHitAngle, speed), aEP_Tool.speed2Velocity(onHitAngle - 180f, speed / lifeTime), lifeTime))
      num ++
    }

    //create smokes
    num = 1
    while (num <= 28) {
      val loc = MathUtils.getRandomPointInCircle(point, 300f)
      val sizeGrowth = MathUtils.getRandomNumberInRange(0, 100).toFloat()
      val sizeAtMin = MathUtils.getRandomNumberInRange(100, 400).toFloat()
      val moveSpeed = MathUtils.getRandomNumberInRange(50, 100).toFloat()
      val ms = aEP_MovingSmoke(loc)
      ms.setInitVel(aEP_Tool.speed2Velocity(VectorUtils.getAngle(point, loc), moveSpeed))
      ms.lifeTime = 3f
      ms.fadeIn = 0f
      ms.fadeOut = 1f
      ms.size = sizeAtMin
      ms.sizeChangeSpeed = sizeGrowth
      ms.color = Color(100, 100, 100, MathUtils.getRandomNumberInRange(80, 180))
      addEffect(ms)
      num ++
    }

    //create explode
    engine!!.spawnExplosion(
      point,  //color
      Vector2f(0f, 0f),  //vel
      hitColor,  //color
      400f,  //size
      1f) //duration
    engine.addNegativeParticle(
      point,
      Vector2f(0f, 0f),
      200f,
      0.5f,
      0.5f,
      Color(240, 240, 240, 200))
    engine.addSmoothParticle(
      point,
      Vector2f(0f, 0f),
      300f,
      0.5f,
      0.2f,
      hitColor)

    //play sound
    Global.getSoundPlayer().playSound(
      "aEP_RW_hit",
      1f, 2f,  // pitch,volume
      weapon.location,  //location
      Vector2f(0f, 0f)
    ) //velocity

    //add hit glow if it pass through missiles
    engine.addHitParticle(
      point,
      Vector2f(0f, 0f),
      400f,
      1f,
      0.1f,
      0.3f,
      hitColor
    )
  }

  fun createFanSmoke(loc: Vector2f, facing: Float, ship: ShipAPI) {
    val glow = Color(250, 250, 250, 250)
    val glowSize = 300f
    val centerGlow = Color(250, 250, 250, 250)
    val centerGlowSize = 100f
    val glowTime = 0.5f

    //add glow
    val light = StandardLight(loc, Vector2f(0f, 0f), Vector2f(0f, 0f), null)
    light.intensity = MathUtils.clamp(glow.alpha / 250f, 0f, 1f)
    light.size = glowSize
    light.setColor(glow)
    light.fadeIn(0f)
    light.setLifetime(glowTime / 2f)
    light.autoFadeOutTime = glowTime / 2f
    LightShader.addLight(light)

    //add center glow
    Global.getCombatEngine().addSmoothParticle(
      loc,  //location
      Vector2f(0f, 0f),  //velocity
      (centerGlowSize + MathUtils.getRandomNumberInRange(0f, centerGlowSize)) * 2f / 3f,  //size
      1f,  //brightness
      glowTime,  //lifetime
      centerGlow
    ) //color


    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 30f
    param.smokeEndSizeMult = 1.5f
    param.smokeSpread = 45f
    param.maxSpreadRange = 50f
    param.smokeInitSpeed = 160f
    param.smokeStopSpeed = 0.6f
    param.smokeTime = 2f
    param.smokeNum = 20
    param.smokeAlpha = 0.2f
    firingSmoke(loc,facing,param, ship)
  }
}

//古斯塔夫
class aEP_RW_shot : Effect(){
  val MAX_STACK = 2f
  val DAMAGE_TO_UPKEEP_INCREASE = 500f
  val BUFF_LIFETIME = 3f

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val color = Color(240, 240, 240, 240)
    projectile?:return
    weapon?: return
    var ms = aEP_MovingSprite(
      projectile.location,
      aEP_Tool.speed2Velocity(weapon.currAngle, 150f),
      0f,
      weapon.currAngle - 90f,
      0f,
      0f,
      0.42f,
      Vector2f(1600f, 400f),
      Vector2f(24f, 6f),
      "aEP_FX.ring",
      color)
    ms.setInitVel(weapon.ship.velocity )
    addEffect(ms)

    ms = aEP_MovingSprite(
      projectile.getLocation(),
      aEP_Tool.speed2Velocity(weapon.currAngle, 150f),
      0f,
      weapon.currAngle - 90f,
      0f,
      0f,
      0.35f,
      Vector2f(2400f, 600f),
      Vector2f(24f, 6f),
      "aEP_FX.ring",
      color)
    ms.setInitVel(weapon.ship.velocity)
    addEffect(ms)

    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 30f
    param.smokeEndSizeMult = 2f
    param.smokeSpread = 30f
    param.maxSpreadRange = 45f

    param.smokeInitSpeed = 300f
    param.smokeStopSpeed = 0.85f

    param.smokeTime = 2.25f
    param.smokeNum = 50
    param.smokeAlpha = 0.1f
    firingSmoke(weapon.getFirePoint(0),weapon.currAngle,param, weapon.ship)

    val smokeTrail = aEP_SmokeTrail(projectile,20f,1f,20f,20f,Color(240,240,240,120))
    addEffect(smokeTrail)

    //create side SmokeFire
    createFanSmoke(projectile.spawnLocation, aEP_Tool.angleAdd(weapon.currAngle, 90f), weapon.ship)
    createFanSmoke(projectile.spawnLocation, aEP_Tool.angleAdd(weapon.currAngle, -90f), weapon.ship)

    //弱智alex，getDamage是加成后damage，setDamage实际是baseDamage
    projectile.damage.damage = projectile.damage.baseDamage / 2f
    projectile.damage.type = DamageType.KINETIC

    val toUseWeaponId = "aEP_railway_gun_shot"
    val newProj = engine.spawnProjectile(
      weapon.ship,  //source ship
      weapon,  //source weapon,
      toUseWeaponId,  //whose proj to be use
      aEP_Tool.getExtendedLocationFromPoint(projectile.location, projectile.facing, -26f),  //loc
      projectile.facing,  //facing
      null
    )
    val proj2 = (newProj as DamagingProjectileAPI)
    //之前已经减半过了
    proj2.damage.damage = projectile.damage.baseDamage
    proj2.damage.type = DamageType.HIGH_EXPLOSIVE
    proj2.velocity.set(projectile.velocity)

    //apply impulse
    aEP_Tool.applyImpulse(weapon.ship, weapon.currAngle - 180f, 5000f)
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    engine?: return
    point?: return
    val projId = projectile?.projectileSpecId?: return
    val ship = projectile.source?: return
    val weapon = projectile.weapon?: return
    //Global.getCombatEngine().addFloatingText(projectile.getLocation(),projectile.getDamageAmount() + "", 20f ,new Color(100,100,100,100),projectile, 0.25f, 120f);
    var hitColor = Color(240, 120, 50, 200)

    if (target is ShipAPI) {
      //create sparks
      var num = 1
      while (num <= 8) {
        var onHitAngle = VectorUtils.getAngle(target.getLocation(), point)
        onHitAngle += MathUtils.getRandomNumberInRange(-60, 60)
        val speed = MathUtils.getRandomNumberInRange(100, 200).toFloat()
        val lifeTime = MathUtils.getRandomNumberInRange(1f, 3f)
        addEffect(Spark(point, aEP_Tool.speed2Velocity(onHitAngle, speed), aEP_Tool.speed2Velocity(onHitAngle - 180f, speed / lifeTime), lifeTime))
        num ++
      }

      //create smokes
      num = 1
      while (num <= 28) {
        val loc = MathUtils.getRandomPointInCircle(point, 300f)
        val sizeGrowth = MathUtils.getRandomNumberInRange(0, 100).toFloat()
        val sizeAtMin = MathUtils.getRandomNumberInRange(100, 400).toFloat()
        val moveSpeed = MathUtils.getRandomNumberInRange(50, 100).toFloat()
        val ms = aEP_MovingSmoke(loc)
        ms.setInitVel(aEP_Tool.speed2Velocity(VectorUtils.getAngle(point, loc), moveSpeed))
        ms.lifeTime = 3f
        ms.fadeIn = 0f
        ms.fadeOut = 1f
        ms.size = sizeAtMin
        ms.sizeChangeSpeed = sizeGrowth
        ms.color = Color(100, 100, 100, MathUtils.getRandomNumberInRange(80, 180))
        addEffect(ms)
        num ++
      }

      //create explode
      engine!!.spawnExplosion(
        point,  //color
        Vector2f(0f, 0f),  //vel
        hitColor,  //color
        400f,  //size
        1f
      ) //duration
      engine.addNegativeParticle(
        point,
        Vector2f(0f, 0f),
        200f,
        0.5f,
        0.5f,
        Color(240, 240, 240, 200)
      )
      engine.addSmoothParticle(
        point,
        Vector2f(0f, 0f),
        300f,
        0.5f,
        0.2f,
        hitColor
      )
    }


    //play sound
    Global.getSoundPlayer().playSound(
      "aEP_RW_hit",
      1f, 2f,  // pitch,volume
      weapon.location,  //location
      Vector2f(0f, 0f)
    ) //velocity

    //add hit glow if it pass through missiles
    engine.addHitParticle(
      point,
      Vector2f(0f, 0f),
      400f,
      1f,
      1f,
      hitColor
    )
  }

  fun createFanSmoke(loc: Vector2f, facing: Float, ship: ShipAPI) {
    val glow = Color(250, 250, 250, 250)
    val glowSize = 300f
    val centerGlow = Color(250, 250, 250, 250)
    val centerGlowSize = 100f
    val glowTime = 0.5f

    //add glow
    val light = StandardLight(loc, Vector2f(0f, 0f), Vector2f(0f, 0f), null)
    light.intensity = MathUtils.clamp(glow.alpha / 250f, 0f, 1f)
    light.size = glowSize
    light.setColor(glow)
    light.fadeIn(0f)
    light.setLifetime(glowTime / 2f)
    light.autoFadeOutTime = glowTime / 2f
    LightShader.addLight(light)

    //add center glow
    Global.getCombatEngine().addSmoothParticle(
      loc,  //location
      Vector2f(0f, 0f),  //velocity
      (centerGlowSize + MathUtils.getRandomNumberInRange(0f, centerGlowSize)) * 2f / 3f,  //size
      1f,  //brightness
      glowTime,  //lifetime
      centerGlow
    ) //color


    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 30f
    param.smokeEndSizeMult = 1.5f
    param.smokeSpread = 45f
    param.maxSpreadRange = 50f
    param.smokeInitSpeed = 160f
    param.smokeStopSpeed = 0.6f
    param.smokeTime = 2f
    param.smokeNum = 20
    param.smokeAlpha = 0.2f
    firingSmoke(loc,facing,param, ship)
  }
}
class Spark(var location: Vector2f,var velocity: Vector2f,var acceleration: Vector2f, lifeTime: Float) : aEP_BaseCombatEffect(lifeTime) {
  var size = 30f
  var sparkColor = Color(200, 200, 50, 200)
  var smokeTracker = IntervalUtil(0.2f,0.2f)

  override fun advance(amount: Float) {
    super.advance(amount)
    val percent = time/lifeTime
    Global.getCombatEngine().addSmoothParticle(
      location,
      Vector2f(0f, 0f),
      size * percent,
      1f,
      amount * 2f,
      Color(sparkColor.red, sparkColor.green, sparkColor.blue, (-200 * percent).toInt() + 250)
    )
    location = Vector2f(location.x + velocity.x * amount, location.y + velocity.y * amount)
    velocity = Vector2f(velocity.x + acceleration.x * amount, velocity.y + acceleration.y * amount)
    smokeTracker.advance(amount)
    if (smokeTracker.intervalElapsed()) {
      val moveDist = 30f
      val lifeTime = 1f
      val smokeMinSize = 15f
      val endSmokeSize = 30f

      val sizeChange = (endSmokeSize - smokeMinSize) / lifeTime
      val engineFacing = aEP_Tool.angleAdd(aEP_Tool.velocity2Speed(velocity).x, 180f)
      val smoke = aEP_MovingSmoke(location) // spawn location
      smoke.setInitVel(aEP_Tool.speed2Velocity(engineFacing + MathUtils.getRandomNumberInRange(-30f, 30f), moveDist / lifeTime))
      smoke.fadeIn = 0.2f
      smoke.fadeOut = 0.35f
      smoke.lifeTime = lifeTime
      smoke.size = smokeMinSize
      smoke.sizeChangeSpeed = sizeChange
      smoke.color = Color(120, 120, 120, 150)
      addEffect(smoke)
    }
  }
}

//DG-3巨型长管链炮
class aEP_b_l_dg3_shot : Effect{
  companion object{
    private val STRUCTURE_DAMAGE = 50f
    val EXPLOSION_COLOR = Color(250,25,25,254)
    private var DAMAGE_PER_TRIGGER = 0f
    private val flames: MutableList<String> = ArrayList()
  }

  var spriteNum = 0
  constructor() {
    flames.add("weapons.LBCG_flame")
    flames.add("weapons.LBCG_flame_core")
    flames.add("weapons.LBCG_flame2")
    flames.add("weapons.LBCG_flame2_core")
    val fullString = StringBuffer()
    //把customHL一位一位读char，计算 “|”的情况
    for(hlString in Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL){
      fullString.append(hlString)
    }
    DAMAGE_PER_TRIGGER = fullString.toString().toFloat()
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    if(shieldHit) return
    if(target is ShipAPI){
      val damage = computeDamageToShip(projectile?.source,null, projectile?.weapon, DAMAGE_PER_TRIGGER, null,false)
      val armorLevel = DefenseUtils.getArmorLevel(target,point)
      if(armorLevel <= 0.05f){
        //不能通过此种方式将hp减到负数或者0
        if(target.hitpoints > damage) target.hitpoints -= damage
        //跳红字代码伤害
        engine?.addFloatingDamageText(point,damage,EXPLOSION_COLOR,target,projectile?.source)


        //百分之25的概率触发一次爆炸特效，这样特效能做夸张一点
        if(MathUtils.getRandomNumberInRange(0f,1f) > 0.25f) return
        //红色爆炸特效
        engine?.addSmoothParticle(point,aEP_ID.VECTOR2F_ZERO,160f,1f,0.5f,0.2f,Color.red)
        var randomVel = MathUtils.getRandomPointInCone(point,75f, angleAdd(projectile?.facing?:0f,-200f),angleAdd(projectile?.facing?:0f,160f))
        randomVel = Vector2f(randomVel.x- (point?.x?:0f),randomVel.y-(point?.y?:0f))
        engine?.spawnExplosion(point, Vector2f(target.velocity.x + randomVel.x,target.velocity.y+ randomVel.y) , EXPLOSION_COLOR,120f,0.8f)
      }
    }
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    //create shell
    var side = 1f
    if (MathUtils.getShortestRotation(weapon.currAngle, VectorUtils.getAngle(weapon.location, projectile.spawnLocation)) > 0) side = -1f

    //create flame
    spriteNum = spriteNum + 2
    if (spriteNum >= flames.size) {
      spriteNum = 0
    }
    val offset = projectile.spawnLocation
    addEffect(
      aEP_MovingSprite(
        offset,  //position
        Vector2f(0f, 0f),
        0f,
        weapon.currAngle - 90f,
        0f,
        0.075f,
        0.025f,
        Vector2f(-56f, -48f),
        Vector2f(28f, 24f),
        flames[spriteNum],
        Color(200, 200, 50, 120)
      )
    )
    val color = (180 * Math.random()).toInt() + 40
    addEffect(
      aEP_MovingSprite(
        offset,  //position
        Vector2f(0f, 0f),
        0f,
        weapon.currAngle - 90f,
        0f,
        0.075f,
        0.025f,
        Vector2f(-56f, -48f),
        Vector2f(28f, 24f),
        flames[spriteNum + 1],
        Color(220, 220, color, 220)
      )
    )
    engine!!.addSmoothParticle(
      offset,
      Vector2f(0f, 0f),
      40f + MathUtils.getRandomNumberInRange(0, 40),
      1f,
      0.1f,
      Color(200, 200, 50, 60 + MathUtils.getRandomNumberInRange(0, 60))
    )
    var distFromOffset = 0f
    while (distFromOffset < 15f) {
      engine.addSmoothParticle(
        getExtendedLocationFromPoint(offset, weapon.currAngle, distFromOffset),
        Vector2f(0f, 0f),
        0.6f * (21 - distFromOffset),  //size
        1f,  //brightness
        0.075f,  //duration
        Color(200, 200, 50, 180)
      )
      distFromOffset = distFromOffset + 2f
    }


    //VEs.createCustomVisualEffect(new SmokeTrail(proj));
  }
}

//EMP钉矛
class aEP_EMP_pike_shot : Effect(){
  companion object{
    //质量参考
    //驱逐400-500
    //巡洋1200-2200
    //战列2500-4000
    val MAX_SPEED_REDUCE = 0.35f //
    val MIN_SPEED_REDUCE = 0.05f //
    val SPEED_REDUCE_BY_MASS = 100f // (SPEED_REDUCE_BY_MASS * SPEED_REDUCE_BY_MASS) / (mass * SPEED_REDUCE_BY_MASS) = speed reduce
    val EMP_DAMAGE = 30f
    val DEBUFF_TIME = 1f //
    val ACCELERATE_MOD = 0.75f //by mult
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    point?: return
    projectile?: return
    target?: return
    if (shieldHit) {
      addEffect(EMPEffect(projectile.weapon, 4.5f, target, point, "weapons.EMP_pike_shot_inShield", projectile.facing, true))
    } else {
      addEffect(EMPEffect(projectile.weapon, 4.5f, target, point, "weapons.EMP_pike_shot_inHull", projectile.facing, false))
    }
  }

  internal class EMPEffect : aEP_StickOnHit {

    var source: WeaponAPI
    var empTracker = IntervalUtil(1f,1f)
    constructor(
      weapon: WeaponAPI,
      duration: Float,
      target: CombatEntityAPI,
      point: Vector2f,
      spriteId: String,
      OnHitAngle: Float,
      hitShield: Boolean) : super(duration, target, point, spriteId,"weapons.EMP_pike_shot",OnHitAngle, hitShield) {
      this.source = weapon
    }

    override fun advanceImpl(amount: Float) {
      empTracker.advance(amount)
      if (!empTracker.intervalElapsed()) return
      val speedMult = 1 - aEP_Tool.limitToTop((SPEED_REDUCE_BY_MASS * SPEED_REDUCE_BY_MASS )/ (target.mass + SPEED_REDUCE_BY_MASS), MAX_SPEED_REDUCE, MIN_SPEED_REDUCE)
      target.velocity.scale(speedMult)
      target.angularVelocity *= speedMult
      //aEP_BuffEffect.addThisBuff(target, EMPPikeOnHit(DEBUFF_TIME, 1f, target, true, 1f, ACCELERATE_MOD))
      Global.getCombatEngine().spawnEmpArcPierceShields(
        source.ship,  //ShipAPI damageSource,
        loc,  // Vector2f point,
        target,  // CombatEntityAPI pointAnchor,
        target,  // CombatEntityAPI empTargetEntity,
        DamageType.ENERGY,  // DamageType damageType,
        0f,  // float damAmount,
        EMP_DAMAGE,  // float empDamAmount,
        2000f,  // float maxRange,
        null,  // java.lang.String impactSoundId,
        15f,  // float thickness,
        Color(100, 100, 100, 150),  // java.awt.Color fringe,
        Color(100, 50, 50, 100)
      ) // java.awt.Color core)
    }

  }


}

//aa40 125 转膛炮系列
class aEP_b_m_k125_shot : Effect(){
  companion object{
    val SMOKE_RING_COLOR =  Color(240,240,240,200)
    val SHELL_GLOW =   Color(255, 218, 188, 35)
  }
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    weapon?:return
    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 25f
    param.smokeEndSizeMult = 1.5f
    param.smokeSpread = 20f
    param.maxSpreadRange = 40f

    param.smokeInitSpeed = 100f
    param.smokeStopSpeed = 0.9f

    param.smokeTime = 1.5f
    param.smokeNum = 5
    param.smokeAlpha = 0.35f

    firingSmoke(projectile?.location?: Vector2f(0f,0f),weapon.currAngle,param, weapon.ship)

    val param2 = aEP_Tool.FiringSmokeParam()
    param2.smokeSize = 25f
    param2.smokeEndSizeMult = 1.5f

    param2.smokeSpread = 10f
    param2.maxSpreadRange = 20f

    param2.smokeInitSpeed = 100f
    param2.smokeStopSpeed = 0.9f

    param2.smokeTime = 0.5f
    param2.smokeNum = 5
    param2.smokeAlpha = 0.1f

    param2.smokeColor = Color(240,110,20)

    firingSmoke(projectile?.location?: Vector2f(0f,0f),weapon.currAngle,param2, weapon.ship)

    val param3 = aEP_Tool.FiringSmokeParam()
    param3.smokeSize = 25f
    param3.smokeEndSizeMult = 1.5f

    param3.smokeSpread = 5f
    param3.maxSpreadRange = 10f

    param3.smokeInitSpeed = 100f
    param3.smokeStopSpeed = 0.9f

    param3.smokeTime = 0.3f
    param3.smokeNum = 5
    param3.smokeAlpha = 0.2f

    param3.smokeColor = Color(255,240,160)

    firingSmokeNebula(projectile?.location?: Vector2f(0f,0f),weapon.currAngle,param3, weapon.ship)

    //创造蛋壳
    val ejectPoint = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle-135f,16f)
    val ms = aEP_MovingSprite(ejectPoint, Vector2f(6f,3f),weapon.currAngle,"graphics/weapons/aEP_large_kinetic_flak/shred.png")
    ms.lifeTime = 2f
    ms.fadeIn = 0.05f
    ms.fadeOut = 0.2f
    ms.color = Color(255,255,255,255)
    ms.angle = weapon.currAngle
    ms.angleSpeed = MathUtils.getRandomNumberInRange(-180f,180f)
    ms.setInitVel(aEP_Tool.speed2Velocity(weapon.currAngle-90f +MathUtils.getRandomNumberInRange(-10f,10f),MathUtils.getRandomNumberInRange(40f,80f)))
    ms.setInitVel(weapon.ship.velocity)
    ms.stopSpeed = 0.9f
    addEffect(ms)
    addEffect(aEP_b_m_h88_shot.ShellGlow(ms, SHELL_GLOW))

  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    point?:return
    explode(point)
  }

  override fun onExplosion(explosion: DamagingProjectileAPI?, originalProjectile: DamagingProjectileAPI?, weaponId: String?) {
    explosion?:return
    explode(explosion.location)
  }

  fun explode(loc: Vector2f){
    spawnSingleCompositeSmoke(loc,125f,1.2f,SMOKE_RING_COLOR)
  }
}
class aEP_b_l_aa40_shot : Effect(){
  companion object{
    val SMOKE_RING_COLOR =  Color(205, 205, 205, 30)
    val SMOKE_RING_COLOR2 =   Color(40, 40, 40, 70)
    val CENTER_SMOKE_COLOR =   Color(240, 180, 120, 60)
    val CENTER_SMOKE_COLOR2 =   Color(100, 100, 100, 30)

    val SHELL_GLOW =   Color(255, 168, 58, 80)
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 25f
    param.smokeEndSizeMult = 1.5f
    param.smokeSpread = 20f
    param.maxSpreadRange = 40f

    param.smokeInitSpeed = 100f
    param.smokeStopSpeed = 0.9f

    param.smokeTime = 1.5f
    param.smokeNum = 5
    param.smokeAlpha = 0.35f

    firingSmoke(projectile.location?: Vector2f(0f,0f),weapon.currAngle,param, weapon.ship)

    val param2 = aEP_Tool.FiringSmokeParam()
    param2.smokeSize = 25f
    param2.smokeEndSizeMult = 1.5f

    param2.smokeSpread = 10f
    param2.maxSpreadRange = 20f

    param2.smokeInitSpeed = 100f
    param2.smokeStopSpeed = 0.9f

    param2.smokeTime = 0.5f
    param2.smokeNum = 5
    param2.smokeAlpha = 0.1f

    param2.smokeColor = Color(240,110,20)

    firingSmoke(projectile.location?: Vector2f(0f,0f),weapon.currAngle,param2, weapon.ship)

    val param3 = aEP_Tool.FiringSmokeParam()
    param3.smokeSize = 25f
    param3.smokeEndSizeMult = 1.5f

    param3.smokeSpread = 5f
    param3.maxSpreadRange = 10f

    param3.smokeInitSpeed = 100f
    param3.smokeStopSpeed = 0.9f

    param3.smokeTime = 0.3f
    param3.smokeNum = 5
    param3.smokeAlpha = 0.2f

    param3.smokeColor = Color(255,240,160)

    firingSmokeNebula(projectile.location?: Vector2f(0f,0f),weapon.currAngle,param3, weapon.ship)

    //创造蛋壳
    projectile?: return
    var onLeft = false
    if(MathUtils.getShortestRotation(VectorUtils.getAngle(weapon.location,projectile.location),weapon.currAngle) > 0){
      onLeft = true
    }
    if(onLeft){
      val ejectPoint = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle-100f,20f)
      val ms = aEP_MovingSprite(ejectPoint, Vector2f(6f,3f),weapon.currAngle,"graphics/weapons/aEP_large_kinetic_flak/shred.png")
      ms.lifeTime = 2f
      ms.fadeIn = 0.05f
      ms.fadeOut = 0.2f
      ms.color = Color(255,255,255,255)
      ms.angle = weapon.currAngle
      ms.angleSpeed = MathUtils.getRandomNumberInRange(-180f,180f)
      ms.setInitVel(aEP_Tool.speed2Velocity(weapon.currAngle-90f +MathUtils.getRandomNumberInRange(-10f,10f),MathUtils.getRandomNumberInRange(40f,80f)))
      ms.setInitVel(weapon.ship.velocity)
      ms.stopSpeed = 0.9f
      addEffect(ms)
      addEffect(aEP_b_m_h88_shot.ShellGlow(ms, SHELL_GLOW))
    }else{
      val ejectPoint = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle+100f,20f)
      val ms = aEP_MovingSprite(ejectPoint, Vector2f(6f,3f),weapon.currAngle,"graphics/weapons/aEP_large_kinetic_flak/shred.png")
      ms.lifeTime = 2f
      ms.fadeIn = 0.05f
      ms.fadeOut = 0.2f
      ms.color = Color(255,255,255,255)
      ms.angle = weapon.currAngle
      ms.angleSpeed = MathUtils.getRandomNumberInRange(-180f,180f)
      ms.setInitVel(aEP_Tool.speed2Velocity(weapon.currAngle+90f + MathUtils.getRandomNumberInRange(-10f,10f),MathUtils.getRandomNumberInRange(40f,80f)))
      ms.setInitVel(weapon.ship.velocity)
      ms.stopSpeed = 0.9f
      addEffect(ms)
      addEffect(aEP_b_m_h88_shot.ShellGlow(ms, SHELL_GLOW))
    }

  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    point?:return
    explode(point)
  }

  override fun onExplosion(explosion: DamagingProjectileAPI?, originalProjectile: DamagingProjectileAPI?, weaponId: String?) {
    explosion?:return
    explode(explosion.location)
  }

  fun explode(loc: Vector2f){
    //弹片
    var angle = 0f
    var rad = 100f
    var num = 6f
    var size = 2f
    var moveSpeed = 50f
    while (angle < 360){
      val p = aEP_Tool.getExtendedLocationFromPoint(loc,angle,MathUtils.getRandomNumberInRange(0f,rad/3f))
      val randomSize = MathUtils.getRandomNumberInRange(0f,2f) + size
      val vel = VectorUtils.getDirectionalVector(loc, p)
      vel.scale(moveSpeed)
      val ms = aEP_MovingSprite(p, Vector2f(randomSize,randomSize),MathUtils.getRandomNumberInRange(0f,360f),"graphics/weapons/aEP_large_kinetic_flak/shell.png")
      ms.lifeTime = 2f
      ms.fadeOut = 0.8f
      ms.color = Color(240,240,200,150)
      ms.setInitVel(vel)
      ms.stopSpeed = 0.85f
      addEffect(ms)
      angle += 360f/num
    }


    //中心白色范围光
    Global.getCombatEngine().addSmoothParticle(
      loc,
      Vector2f(0f, 0f),
      100f,
      1f,
      0f,
      1f,
      Color(255,255,255,120),
    )
    //中心白色爆炸正中
    Global.getCombatEngine().addSmoothParticle(
      loc,
      Vector2f(0f, 0f),
      50f,
      1f,
      0f,
      0.5f,
      Color.white
    )

    //扩散烟圈
    angle = 0f
    rad = 40f
    num = 8f
    size = 50f
    moveSpeed = 10f
    val spread = 360f / num
    while (angle < 360) {
      val outPoint = MathUtils.getPoint(loc, rad, angle)
      val vel = VectorUtils.getDirectionalVector(loc, outPoint)
      vel.scale(moveSpeed)
      Global.getCombatEngine().addNebulaSmokeParticle(
        outPoint,
        vel,
        size,
        1.2f,
        0.1f,
        0f,
        2f,
        SMOKE_RING_COLOR
      )
      Global.getCombatEngine().addSwirlyNebulaParticle(
        outPoint,
        vel,
        size,
        1.2f,
        0.1f,
        0f,
        2f,
        SMOKE_RING_COLOR2,
        true
      )
      angle += spread
    }
    val centerSmoke = aEP_MovingSmoke(loc)
    centerSmoke.size = 100f
    centerSmoke.lifeTime = 0.6f
    centerSmoke.fadeOut = 0.4f
    centerSmoke.color =CENTER_SMOKE_COLOR
    aEP_CombatEffectPlugin.addEffect(centerSmoke)

    val centerSmoke2 = aEP_MovingSmoke(loc)
    centerSmoke2.size = 80f
    centerSmoke2.lifeTime = 2.5f
    centerSmoke2.fadeOut = 0.9f
    centerSmoke2.color =CENTER_SMOKE_COLOR2
    aEP_CombatEffectPlugin.addEffect(centerSmoke2)
  }

}

//高速榴弹炮
class aEP_high_speed_HE_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile?: return
    //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(), "OK", 20f, new Color(100, 100, 100, 100), engine.getPlayerShip(), 1f, 5f);
    addEffect(SplitTrigger(projectile))
  }

  class SplitTrigger : aEP_BaseCombatEffect{
    companion object{
      private val fuseRange = 60f
      private val triggerPointRange = 80f
      private val spreadAngle = 15f
      private val splitNum = 4
      private val earliestSplitTime = 0.35f
      private val SMOKE_COLOR = Color(240,240,240,214)
    }

    val projectile: DamagingProjectileAPI
    var timer = 0f
    constructor(projectile: DamagingProjectileAPI){
      this.projectile = projectile
      init(projectile)
    }

    override fun advanceImpl(amount: Float) {
      if(projectile.isFading){
        cleanup()
        return
      }

      timer += amount
      if(timer < earliestSplitTime){
        return
      }

      var shouldSplit = false
      val triggerPoint = aEP_Tool.getExtendedLocationFromPoint(projectile.location, projectile.facing ,triggerPointRange)
      //aEP_Tool.addDebugPoint(triggerPoint)
      //检测距离为弹体前方一圆形，任何一个船体碰撞点处于圆内时触发引信
      for(ship in CombatUtils.getShipsWithinRange(triggerPoint, fuseRange)){
        if(ship.collisionClass == CollisionClass.NONE) continue
        if(ship.exactBounds == null) continue
        if(ship == projectile.source) continue
        if(ship.owner == projectile.source.owner) continue
        //aEP_Tool.addDebugText("did", ship.location)
        if(ship.shield != null && ship.shield.isOn){
          if(MathUtils.getDistance(triggerPoint, ship.location) < triggerPointRange + ship.shield.radius){
            if(Math.abs(MathUtils.getShortestRotation(VectorUtils.getAngle(ship.shield.location, triggerPoint), ship.shield.facing)) < ship.shield.activeArc/2f){
              shouldSplit = true
              //aEP_Tool.addDebugText("in", triggerPoint)
              break
            }
          }
        }else{
          for(seg in ship.exactBounds.segments){
            if(CollisionUtils.getCollides(seg.p1, seg.p2, triggerPoint, triggerPointRange)){
              shouldSplit = true
              //aEP_Tool.addDebugText("in", triggerPoint)
              break
            }
          }
        }
      }

      if(shouldSplit){
        //分裂
        var i = 0
        var angle = -spreadAngle/2f
        var angleIncreasePerLoop = spreadAngle/(splitNum.toFloat())
        while (i < splitNum){
          val newProj = Global.getCombatEngine().spawnProjectile(projectile.source,
            projectile.weapon,
            "aEP_high_speed_HE2",
            projectile.location,
            projectile.facing + angle,
            null)
          newProj.velocity.set(VectorUtils.rotate(newProj.velocity,angle))
          newProj.velocity.scale(MathUtils.getRandomNumberInRange(1f,1.5f))
          i ++
          angle += angleIncreasePerLoop
        }

        //分裂的特效
        aEP_Tool.spawnSingleCompositeSmoke(projectile.location,90f,1.5f, SMOKE_COLOR)

        //音效
        Global.getSoundPlayer().playSound("aEP_high_speed_HE_flak",1f,0.75f,projectile.location,Vector2f(0f,0f))
        Global.getCombatEngine().removeEntity(projectile)
        cleanup()
      }

    }
  }
}

//88 创伤炮
class aEP_b_m_h88_shot : Effect() {
  companion object{
    val EJECT_GLOW_COLOR = Color(255,44,11,100)
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    //创造蛋壳
    val ejectPoint = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle-140f,5f)
    val ms = aEP_MovingSprite(ejectPoint, Vector2f(6f,4f),weapon.currAngle,"weapons.aEP_b_m_h88_eject")
    ms.lifeTime = 3f
    ms.fadeIn = 0.1f
    ms.fadeOut = 0.2f
    ms.color = Color.white
    ms.angle = weapon.currAngle
    ms.angleSpeed = MathUtils.getRandomNumberInRange(-180f,180f)
    ms.setInitVel(aEP_Tool.speed2Velocity(weapon.currAngle-90f + MathUtils.getRandomNumberInRange(-10f,10f),MathUtils.getRandomNumberInRange(40f,80f)))
    ms.setInitVel(weapon.ship.velocity)
    ms.stopSpeed = 0.9f
    addEffect(ms)
    addEffect(ShellGlow(ms,EJECT_GLOW_COLOR))
  }

  open class ShellGlow(val ms:aEP_MovingSprite, val color: Color):aEP_BaseCombatEffect(){
    var endSize = 10f
    var extraSize = 30f
    override fun advanceImpl(amount: Float) {
      if(ms.time>=ms.lifeTime){
        shouldEnd = true
        return
      }
      val  level = 1f - ms.time/ms.lifeTime
      Global.getCombatEngine().addSmoothParticle(
        ms.loc,
        aEP_ID.VECTOR2F_ZERO,
        extraSize*level + endSize,
        0.5f + 0.5f*level,
        Global.getCombatEngine().elapsedInLastFrame * 2f,
        color
      )
    }
  }

}

//锚点无人机模拟导弹
class aEP_cru_maodian_missile : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile?.weapon?.ship?: return

    val ship = projectile.weapon.ship
    //屏蔽左上角入场提示
    val suppressBefore = engine?.getFleetManager(projectile.owner?:100)?.isSuppressDeploymentMessages
    engine?.getFleetManager(projectile.owner?:100)?.isSuppressDeploymentMessages = true
    //生成无人机
    val drone = engine?.getFleetManager(projectile.owner?:100)?.spawnShipOrWing(
      MaoDianDrone_ID,
      projectile.location?: VECTOR2F_ZERO,
      projectile.facing
    )
    //恢复左上角入场提示
    engine?.getFleetManager(projectile.owner?:100)?.isSuppressDeploymentMessages = suppressBefore?: false

    //旋转雷达
    if(drone != null){
      for(w in drone.allWeapons){
        if(w.spec.weaponId == "aEP_ftr_ut_maodian_radar"){
          w.currAngle = ship.facing
        }
      }
    }
    drone?: return

    //把武器转头藏好，不要生成以后穿模
    for( w in drone.allWeapons ){
      if(w.slot.id.equals("WS0003")) w.currAngle = (drone.facing-10)
      if(w.slot.id.equals("WS0004")) w.currAngle = (drone.facing+10)
      if(w.slot.id.equals("WS0005")) w.currAngle = (drone.facing+120)
    }
    drone.velocity.set( projectile.velocity?:VECTOR2F_ZERO)

    //默认位置是当前的鼠标位置
    var targetLoc = ship.mouseTarget?: VECTOR2F_ZERO
    //如果是ai使用，读取母舰的customData，由systemAI放入
    if(ship.customData[aEP_MDDroneLaunchAI.TARGET_KEY] is Vector2f && ship.shipAI != null) {
      targetLoc = ship.customData[aEP_MDDroneLaunchAI.TARGET_KEY] as Vector2f
      ship.customData[aEP_MDDroneLaunchAI.TARGET_KEY] = null
    }

    //不可位置越界
    val sysRange = aEP_Tool.getSystemRange(ship, aEP_MaodianDroneLaunch.SYSTEM_RANGE)
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
    addEffect(ShowLocation(drone,ai))

    //移除proj
    engine?.removeEntity(projectile)
  }

  class ShowLocation : aEP_BaseCombatEffect{
    val ai:aEP_MaoDianDroneAI
    constructor(drone:ShipAPI, ai: aEP_MaoDianDroneAI):super(){
      init(drone)
      this.ai = ai
    }

    override fun advanceImpl(amount: Float) {
      val sprite = Global.getSettings().getSprite("graphics/aEP_FX/noise.png")
      MagicRender.singleframe(sprite,
        ai.targetLoc?: VECTOR2F_ZERO,
        Vector2f(40f,40f),
        0f, Color(255,0,0,250),false)

      if(ai.stat is aEP_MaoDianDroneAI.HoldShield){
        cleanup()
      }
    }

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
    }
  }
}

//巡洋导弹发射装置
//控制如何爆炸在FighterSpecial里面
class aEP_cruise_missile_weapon_shot : Effect(){
  companion object{
    const val TARGET_KEY = "aEP_cruise_missile_weapon_shot"
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    engine?: return
    projectile?: return
    val itemId = aEP_CruiseMissileLoadIntel.getLoadedItemId(weapon.ship.fleetMemberId)
    val hullId = aEP_CruiseMissileLoadIntel.ITEM_TO_SHIP_ID[itemId]?:""
    if(hullId.equals(""))return

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
      LOADING_MAP.keys.remove(ship.fleetMemberId)
    }
  }

}

//闪电机炮
class aEP_b_m_lighting_shot : Effect{
  companion object{
    var DAMAGE_TO_UPKEEP_INCREASE = 10f
    var BUFF_LIFETIME = 8f
    const val KEY = "aEP_b_m_lighting_shot"

  }

  constructor(){
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) BUFF_LIFETIME = num.toFloat()
      if(i == 1) DAMAGE_TO_UPKEEP_INCREASE = num.toFloat()
      i += 1
    }
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    if (shieldHit && target is ShipAPI) {
      addEffect(IncreaseUpKeep(BUFF_LIFETIME,target, DAMAGE_TO_UPKEEP_INCREASE))
    }

  }

  class IncreaseUpKeep(time:Float, val s:ShipAPI, val addPerSec:Float) : aEP_BaseCombatEffect(time,s){
    val tracker = IntervalUtil(0.1f,0.1f)
    override fun advanceImpl(amount: Float) {
      super.advanceImpl(amount)
      tracker.advance(amount)
      if(tracker.intervalElapsed() ){
        val fluxToAdd = (addPerSec * tracker.elapsed).coerceAtMost(s.maxFlux - s.currFlux -1f)
        if(fluxToAdd > 0){
          s.fluxTracker.increaseFlux(fluxToAdd,false)
        }
      }
    }
  }
}

//深度遥控战机磁吸炸弹
class aEP_ftr_ut_shendu_mine_shot : Effect(){
  companion object{
    const val MAGNETIC_ATTRACTION_RANGE = 600f
    const val MAGNETIC_ATTRACTION_ACCELERATION = 1000f
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile?:return
    //先把速度停下来，防止初速度太高飞走了
    projectile.velocity.scale(0.25f)
    //尝试寻找发射源（飞机）
    val fighter =  weapon?.ship
    aEP_CombatEffectPlugin.addEffect(DriftToTarget(projectile))
    //给一个随机的转动初速度，不然太呆了
    projectile.angularVelocity = MathUtils.getRandomNumberInRange(-15f,15f)
  }
  class DriftToTarget : aEP_BaseCombatEffect{
    val magneticTracker = IntervalUtil(0.2f,0.2f)
    constructor(projectile: DamagingProjectileAPI){
      init(projectile)
    }

    override fun advanceImpl(amount: Float) {
      magneticTracker.advance(amount)
      if(!magneticTracker.intervalElapsed()) return

      //寻找最近的敌方非战机舰船
      val proj = entity as DamagingProjectileAPI
      var nearestTarget = aEP_Tool.getNearestEnemyCombatShip(proj)

      //找到了，如果在400范围内，向目标飞去
      if(nearestTarget != null ){
        val dist = MathUtils.getDistance(proj.location,nearestTarget.location)
        if(dist <= MAGNETIC_ATTRACTION_RANGE){
          val distLevel = (MAGNETIC_ATTRACTION_RANGE-dist)/ MAGNETIC_ATTRACTION_RANGE
          val distPercent = 0.25f + 0.75f*distLevel*distLevel
          aEP_Tool.forceSetThroughPosition(proj,nearestTarget.location,magneticTracker.minInterval,1000f * distPercent,(proj as MissileAPI).maxSpeed)

        }
      }

      //否则减速原地禁止
      else
        proj.velocity.scale(0.8f)
    }
  }
}

//crossout铲车头，长矛，喷火
class aEP_fga_yonglang_mk2_cover : EveryFrame(){
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    weapon.ship?:return
    for(m in weapon.ship.childModulesCopy){
      m?: continue
      //注意，模块死亡脱离以后，stationSlot会改为null
      if(m.hullSpec.hullId.equals("aEP_des_yonglang_mk2_m")){
        weapon.animation.frame = 0
        if(m.isAlive){
          weapon.animation.frame = 1
          return
        }
      }
    }
  }
}
class aEP_bomb_lance : EveryFrame(){
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    weapon.ship?:return
    weapon.currHealth = weapon.maxHealth
    weapon.maxAmmo = 1
  }
}
class aEP_bomb_lance_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile?: return
    engine?:return
    val missile = projectile as MissileAPI
    //aEP_Tool.addDebugLog(missile.damage.damage.toString())
    missile.explode()
    Global.getCombatEngine().removeEntity(missile)
  }

}
class aEP_flamer_shot : Effect(){
  companion object{
    val CORE_FLAME_COLOR = Color(255,194,86,240)
    val CORE_FLAME_COLOR2= Color(252,164,50,150)
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile?:return


    val vel = Vector2f(projectile.velocity)
    val loc = aEP_Tool.getExtendedLocationFromPoint(projectile.location,projectile.facing,40f);
    val lifeTime = (projectile.weapon.range / (projectile.moveSpeed + 0.1f)) + 0.3f
    Global.getCombatEngine().addNebulaParticle(
      loc,
      projectile.velocity,
      20f,
      6f,
      0.25f,0.5f,lifeTime,
      CORE_FLAME_COLOR,true
    )
    Global.getCombatEngine().addNebulaParticle(
      loc,
      projectile.velocity,
      40f,
      3f,
      0.25f,0.25f,lifeTime,
      CORE_FLAME_COLOR2,true
    )
    vel.scale(0.5f)
    Global.getCombatEngine().addNebulaParticle(
      loc,
      vel,
      20f,
      3f,
      0.25f,0.1f,lifeTime,
      CORE_FLAME_COLOR
    )

  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    projectile?:return
    target?:return
    point?: return
    var color = projectile.projectileSpec.fringeColor
    color = Misc.setAlpha(color, 100)
    val vel = Vector2f()
    if (target is ShipAPI) {
      vel.set(target.getVelocity())
    }
    val endSizeMult = 4f
    for (i in 0..2) {
      val size = 20f + MathUtils.getRandomNumberInRange(0f,20f)
      val dur = 1f
      val rampUp = 0f
      engine?.addNebulaParticle(
        point, vel, size, endSizeMult,
        rampUp, 0f, dur, color, true
      )
    }

  }
}

//防空弹幕分裂
class aEP_des_yangji_flak_shot : Effect() {

  var num = 1
  var speedVariant = 20

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    var i = 0
    while (i < num) {
      i += 1
      val weaponSpreadNow = weapon.currSpread
      val newProj = engine.spawnProjectile(
        weapon.ship,
        weapon,
        weapon.spec.weaponId,
        projectile.location,
        weapon.currAngle + MathUtils.getRandomNumberInRange(-weaponSpreadNow / 2f, weaponSpreadNow / 2f),
        weapon.ship.velocity
      ) as DamagingProjectileAPI
      newProj.damageAmount = projectile.damageAmount / num
      val speedChange = 1f - MathUtils.getRandomNumberInRange(-speedVariant, speedVariant) / 100f
      newProj.velocity[newProj.velocity.x * speedChange] = newProj.velocity.y * speedChange
    }
    engine.removeEntity(projectile)
  }
}

//反冲力
class aEP_fga_yonglang_main_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    val MAX_OVERLOAD_TIME = 2f

    point?: return
    projectile?: return
    val vel = Vector2f(0f, 0f)
    if (target !is ShipAPI) return
    if (shieldHit) {
      engine!!.applyDamage(
        target,  //target
        point,  // where to apply damage
        projectile.damage.fluxComponent,  // amount of damage
        DamageType.KINETIC,  // damage type
        0f,  // amount of EMP damage (none)
        false,  // does this bypass shields? (no)
        true,  // does this deal soft flux? (no)
        projectile.source
      )
      if (target.fluxTracker.isOverloaded) {
        target.fluxTracker.setOverloadDuration(MAX_OVERLOAD_TIME)
      }
    }

    val sizeMult = 0.5f
    var wave = WaveDistortion(point, vel)
    wave.size = 200f * sizeMult
    wave.intensity = 50f
    wave.fadeInSize(0.75f)

    wave.setLifetime(0f)
    DistortionShader.addDistortion(wave)

    wave = WaveDistortion(point, vel)
    wave.size = 200f * sizeMult
    wave.intensity = 20f
    wave.fadeInSize(1f)
    wave.setLifetime(0f)
    DistortionShader.addDistortion(wave)


    val color = Color(240, 240, 240, 240)
    val size =  Vector2f(450f, 900f)
    val sizeChange = Vector2f(8f, 24f)
    size.scale(sizeMult)
    sizeChange.scale(sizeMult)
    val ring = aEP_MovingSprite(point,
      Vector2f(0f, 0f),
      0f,
      VectorUtils.getAngle(point, target.getLocation()),
      0f,
      0f,
      1f,
      size,
      sizeChange,
      "aEP_FX.ring",
      color)
    addEffect(ring)
  }



}

//107火
class aEP_b_m_rk107_shot : Effect{
  companion object{
    private var DAMAGE_BONUS = 0.2f
    const val MAX_BONUS_DAMAGE = 400f
    const val CUSTOM_KEY = "aEP_b_m_rk107_shot"
  }

  constructor(){
    val fullString = StringBuffer()
    //把customHL一位一位读char，计算 “|”的情况
    for(hlString in Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL){
      fullString.append(hlString)
    }
    DAMAGE_BONUS = fullString.toString().toFloat()
  }


  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    if(projectile is MissileAPI){
      val speedNow = projectile.spec.launchSpeed
      val maxSpeed = projectile.maxSpeed
      val range = projectile.maxRange
      var acc = projectile.acceleration
      if(maxSpeed <= speedNow) return
      if(acc <= 0) return
      if(range <= 0) return
      val speedDiff = maxSpeed - speedNow
      val timeToAcc = speedDiff/acc
      val timeCompensation = timeToAcc/2f
      projectile.maxFlightTime = range/maxSpeed
      projectile.maxFlightTime += timeCompensation
    }

    //第一次开火时给自己加个listener
    if(weapon.ship != null && !weapon.ship.customData.containsKey(CUSTOM_KEY)){
      weapon.ship.customData[CUSTOM_KEY] = 1f
      weapon.ship.addListener(DamageDealt())
    }
  }

  inner class Time(val m:MissileAPI) : aEP_BaseCombatEffect(0f, m){
    override fun advanceImpl(amount: Float) {
      aEP_Tool.addDebugLog(m.flightTime.toString())
      aEP_Tool.addDebugLog(aEP_Tool.velocity2Speed(m.velocity).y.toString())
    }
  }

  inner class DamageDealt: DamageDealtModifier{

    //在伤害计算前调用
    override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI?, point: Vector2f?, shieldHit: Boolean): String? {
      damage?:return null
      if(shieldHit) return null
      if(target !is ShipAPI) return null

      if(param is MissileAPI){
        if(param.projectileSpecId.equals(CUSTOM_KEY)){
          val armorBonus = (target.armorGrid.armorRating * DAMAGE_BONUS).coerceAtMost(MAX_BONUS_DAMAGE)
          //基础值是1哦
          //如果基础伤害是0就没法计算了，不加成
          if(damage.baseDamage <= 0f) return null
          damage.modifier.modifyFlat(CUSTOM_KEY,armorBonus/damage.baseDamage)
          damage.modifier.modifiedValue
          return CUSTOM_KEY
        }
      }
      return null
    }
  }
}

//era df1 爆反 爆炸反应装甲
class aEP_m_s_era :EveryFrame{
  companion object{
    const val KEY = "aEP_m_s_era"
    const val RANGE = 20f
    var DAMAGE_THRESHOLD = 749f
    var ARMOR_THRESHOLD = 1f
  }

  constructor(){
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) DAMAGE_THRESHOLD = num.toFloat()
      if(i == 1) ARMOR_THRESHOLD = num.toFloat()
      i += 1
    }
  }

  var inList = false
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    weapon.ship?:return
    val ship = weapon.ship

    //第一个被读取的武器把listener放进customData
    if (!ship.customData.containsKey(KEY)) {
      ship.addListener(DamageTakenModifier())
      //ship.removeWeaponFromGroups(weapon)
      ship.setCustomData(KEY,1f)
    }

    //addDebugPoint(getExtendedLocationFromPoint(weapon.location,weapon.arcFacing,15f))

    //如果自己不在listener的list里面，塞进去
    if(!inList){
      if(ship.hasListenerOfClass(DamageTakenModifier::class.java)){
        ship.listenerManager.getListeners(DamageTakenModifier::class.java)[0].allEra.add(weapon)
        inList = true
      }
    }
  }

  inner class DamageTakenModifier: com.fs.starfarer.api.combat.listeners.DamageTakenModifier{
    val allEra = ArrayList<WeaponAPI>()
    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI?, point: Vector2f?, shieldHit: Boolean): String? {
      point?:return null
      damage?:return null
      val engine = Global.getCombatEngine()
      for(w in allEra){
        val dist = MathUtils.getDistance(w.location,point)
        val realDamage = damage.damage * damage.modifier.modifiedValue

        if(w.ammo < 1) continue
        if(dist > RANGE ) continue
        if(realDamage  < DAMAGE_THRESHOLD
          && realDamage < (w.ship?.armorGrid?.armorRating?:1000f) * ARMOR_THRESHOLD) continue

        //产生炮口烟，刷出弹丸，立刻引爆
        engine.spawnMuzzleFlashOrSmoke(
          w.ship,
          w.slot,
          w.spec,
          0,
          w.currAngle
        )
        val proj = engine.spawnProjectile(
          w.ship,
          w,
          w.spec.weaponId,
          w.getFirePoint(0),  //FirePoint得到的是绝对位置
          w.currAngle,
          w.ship?.velocity?: VECTOR2F_ZERO
        ) as MissileAPI
        proj.explode()
        w.ammo -= 1
        //降低90%的伤害
        damage.modifier.modifyMult(KEY,0.1f)
      }
      return null
    }
  }
}
class aEP_m_s_era_shot : Effect(){
  companion object{
    const val WARHEAD_WEAPON_ID = "aEP_m_s_era2"
  }

  override fun onExplosion(explosion: DamagingProjectileAPI?, originalProjectile: DamagingProjectileAPI?, weaponId: String?) {
    explosion?:return
    originalProjectile?: return
    for(i in 0 until 10){
      val proj = Global.getCombatEngine().spawnProjectile(
        originalProjectile.source,
        originalProjectile.weapon,
        WARHEAD_WEAPON_ID,
        explosion.location,
        originalProjectile.facing + getRandomNumberInRange(-30,30),
        originalProjectile.source?.velocity
      )
      proj.velocity.scale(getRandomNumberInRange(0.75f,1.25f))
    }

  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    if(projectile is MissileAPI){
      projectile.explode()
    }
  }


}