package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.combat.BreachOnHitEffect
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import com.fs.starfarer.combat.entities.DamagingExplosion
import combat.impl.VEs.*
import combat.impl.aEP_BaseCombatEffect
import combat.impl.proj.aEP_StickOnHit
import combat.plugin.aEP_CombatEffectPlugin
import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import combat.util.aEP_Combat
import combat.util.aEP_ID
import combat.util.aEP_ID.Companion.VECTOR2F_ZERO
import combat.util.aEP_Render
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.angleAdd
import combat.util.aEP_Tool.Util.computeDamageToShip
import combat.util.aEP_Tool.Util.firingSmoke
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.isDead
import combat.util.aEP_Tool.Util.isEnemy
import combat.util.aEP_Tool.Util.spawnCompositeSmoke
import combat.util.aEP_Tool.Util.spawnSingleCompositeSmoke
import combat.util.aEP_Tool.Util.speed2Velocity
import combat.util.aEP_Tool.Util.velocity2Speed
import data.scripts.hullmods.aEP_ReactiveArmor
import data.scripts.shipsystems.aEP_WeaponReset
import data.scripts.weapons.aEP_WeaponEffect.Companion.EXPLOSION_PROJ_ID_KEY
import data.scripts.weapons.aEP_ftr_bom_nuke_bomb_shot1.Companion.JITTER_COLOR
import data.scripts.weapons.aEP_ftr_bom_nuke_bomb_shot1.Companion.WEAPON_ID
import org.dark.shaders.distortion.DistortionShader
import org.dark.shaders.distortion.WaveDistortion
import org.dark.shaders.light.LightShader
import org.dark.shaders.light.StandardLight
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.MathUtils.*
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lazywizard.lazylib.combat.DefenseUtils
import org.lazywizard.lazylib.combat.WeaponUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicAnim
import org.magiclib.util.MagicLensFlare
import org.magiclib.util.MagicRender
import java.awt.Color
import java.util.*
import kotlin.math.*

/**
 * 注意，EveryFrameWeaponEffectPlugin其实和 onHit，onFire共用一个类
 * 在 EveryFrameWeaponEffectPlugin中写入onHit， onFire方法，即使不在弹丸中声明，也会调用对应方法
 * */
class aEP_WeaponEffect : OnFireEffectPlugin, OnHitEffectPlugin, ProximityExplosionEffect, BeamEffectPluginWithReset, EveryFrameWeaponEffectPluginWithAdvanceAfter {
  companion object{
    const val EXPLOSION_PROJ_ID_KEY = "aEP_projectileId"
  }


  var effect: Effect? = null
  var everyFrame: EveryFrameWeaponEffectPlugin? = null
  var beamEffect: BeamEffectPluginWithReset? = null
  var didCheckClass = false
  var didCheckBeamEffect = false

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI) {
    //根据projId进行类查找
    projectile?: return
    var weaponId = projectile.weapon?.spec?.weaponId?: ""
    //对于damagingExplosion，weapon会得到null，projectileId也是null
    //要在爆炸中使用onHit一定要同时使用onExplosion，在onExplosion中将projectileId放入customData
    if(projectile is DamagingExplosion){
      weaponId = projectile.projectileSpecId?.replace("_shot","")?:""
    }

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

    //对于damagingExplosion，weapon会得到null，projectileId也是null
    //要在爆炸中使用onHit一定要同时使用onExplosion，在onExplosion中将projectileId放入customData
    val projId = projectile.projectileSpecId?:""
    explosion.setCustomData(EXPLOSION_PROJ_ID_KEY, projId)


    effect?.onExplosion(explosion, projectile, weaponId)
  }

  //onHit/onFire/onExplosion 是可以共享的，多个不同同种武器的effect变量会指向存在customData里面的同一个类(单列模式)
  private fun getEffect(projectile: DamagingProjectileAPI): Effect? {
    //类缓存，查找过一次的类放进 map，减少调用 forName的次数
    val cache: MutableMap<String?,Effect?> = (Global.getCombatEngine().customData["aEP_WeaponEffect"] as MutableMap<String?,Effect?>?)?: HashMap()

    //在cache里面找projId对应的类，没有就classForName查一个放进cahce
    //classForName也查不到就保持null
    var projId = ""
    //对于damagingExplosion，projectileId是null
    //要在爆炸中使用onHit一定要同时使用onExplosion，在onExplosion中将projectileId放入customData
    if(projectile is DamagingExplosion){
      projId = projectile.customData[EXPLOSION_PROJ_ID_KEY] as String
    }else{
      projId = projectile.projectileSpecId
    }
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
  override fun advanceAfter(amount: Float, engine: CombatEngineAPI?, weapon: WeaponAPI?) {
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

    if(everyFrame is EveryFrameWeaponEffectPluginWithAdvanceAfter && weapon != null){
      (everyFrame as EveryFrameWeaponEffectPluginWithAdvanceAfter).advanceAfter(amount,engine,weapon)
    }
  }

  // beamEffectPlugin 会在每个beam被产生时被引擎new一个
  override fun advance(amount: Float, engine: CombatEngineAPI?, beam: BeamAPI?) {
    //只尝试读取一次
    if(beamEffect == null && !didCheckBeamEffect) {
      didCheckBeamEffect = true
      try {
        val e = Class.forName(aEP_WeaponEffect::class.java.getPackage().name + "." + beam?.weapon?.spec?.weaponId?:"")
        beamEffect = e.newInstance() as BeamEffectPluginWithReset
        //Global.getLogger(this.javaClass).info("BeamEffectLoaded :" +e.name)
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
open class Effect{

  open fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {}
  open fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {}
  open fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {}

}

/**
  继承的类名要是武器的id，在本帧武器开火前调用
 */
open class EveryFrame: EveryFrameWeaponEffectPluginWithAdvanceAfter{
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
  }

  override fun advanceAfter(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {

  }
}

//爆破锤系列
class aEP_m_m_blasthammer_shot : Effect(){
  companion object{
    const val KEY = "aEP_m_m_blasthammer_shot"
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile ?:return
    val smokeTrail =  object : aEP_SmokeTrail(projectile,
      9f,
      1.6f,
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
    //加重，防止被引力子拦截
    projectile.mass = 200f
    smokeTrail.stopSpeed = 0.96f
    smokeTrail.smokeSpreadAngleTracker.speed = 1.6f
    smokeTrail.smokeSpreadAngleTracker.max = 15f
    smokeTrail.smokeSpreadAngleTracker.min = -15f
    smokeTrail.smokeSpreadAngleTracker.randomizeTo()
    smokeTrail.flareColor = Color(255,134,86,240)
    smokeTrail.flareColor2 = Color(152,124,20,240)

    addEffect(smokeTrail)
    addEffect(ApproximatePrimer(projectile as MissileAPI, 0.75f))

    if(weapon.ship != null && !weapon.ship.customData.containsKey(KEY)){
      weapon.ship.setCustomData(KEY, 1f)
      weapon.ship.addListener(OnHitSplit())
    }
  }
}
class aEP_m_l_blasthammer_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile ?:return

    val smokeTrail =  object : aEP_SmokeTrail(projectile,
      12f,
      2f,
      24f,
      48f,
      Color(140,140,140,160)){

      val missile = entity as MissileAPI
      override fun advanceImpl(amount: Float) {
        super.advanceImpl(amount)
        if(missile.engineController.isAccelerating) return
        missile.flightTime = missile.flightTime - amount/2f
      }
    }
    //加重，防止被引力子拦截
    projectile.mass = 200f
    smokeTrail.stopSpeed = 0.965f
    //因为新的角度算法是按照生成烟的数量变化的，大导弹云更大，生成的也就越少，这里反而需要比小导大才能维持类似的角度变化
    smokeTrail.smokeSpreadAngleTracker.speed = 1.8f
    smokeTrail.smokeSpreadAngleTracker.max = 15f
    smokeTrail.smokeSpreadAngleTracker.min = -15f
    smokeTrail.flareColor = Color(255,134,86,240)
    smokeTrail.flareColor2 = Color(152,124,20,240)

    addEffect(smokeTrail)
    addEffect(ApproximatePrimer(projectile as MissileAPI, 1f))

    if(weapon.ship != null && !weapon.ship.customData.containsKey(aEP_m_m_blasthammer_shot.KEY)){
      weapon.ship.setCustomData(aEP_m_m_blasthammer_shot.KEY, 1f)
      weapon.ship.addListener(OnHitSplit())
    }

  }
}
//控制导弹击中时把能量伤害改为动能伤害，并产生爆炸特效，并在下一帧生成弹丸
class OnHitSplit() : DamageDealtModifier{
  companion object{

    var KINETIC_PERCENT = 0.25f
    var FRAG_SHOT_NUM = 2
    var FRAG_PERCENT = 0.5f
    var DAMAGE_DROP_PERCENT = 0.5f
    init {
      val hlString = Global.getSettings().getWeaponSpec(
        aEP_m_m_blasthammer_shot::class.java.simpleName.replace("_shot","")).customPrimaryHL
      var i = 0
      for(num in hlString.split("|")){
        if(i == 0) {
          KINETIC_PERCENT = num.replace("%","").toFloat().div(100f)
        }
        if(i == 1) {
          FRAG_SHOT_NUM = num.toFloat().toInt()
        }
        if(i == 2) {
          FRAG_PERCENT = num.replace("%","").toFloat().div(100f)
        }
        if(i == 3) {
          DAMAGE_DROP_PERCENT = num.replace("%","").toFloat().div(100f)
        }
        i += 1
      }
    }

    fun explode(sizeMult: Float, missile: MissileAPI){
      val point = missile.location
      val engine = Global.getCombatEngine()

      //中心点大闪光
      aEP_Tool.spawnDirectionalExplosion(
        point,missile.facing,12,
        75f * sizeMult,125f * sizeMult,
        Color(200,200,200,255),
        0.6f,1.2f,0f, 50f,
        100f * sizeMult,400f * sizeMult,
        1f,2f)


      //向前缓慢移动的爆炸
      engine.spawnExplosion(
        point,
        speed2Velocity(missile.facing, 100f * sizeMult),
        Color(255,255,255,155),
        100f * sizeMult,
        1f)


      //随机烟雾
      var numMax = 8
      for (i in 0 until  numMax) {
        val loc = MathUtils.getRandomPointInCircle(point, 80f * sizeMult)
        engine.addNebulaParticle(
          loc, Vector2f(0f,0f),50f*sizeMult, 1.5f,
          0f,0.5f, 2.5f+getRandomNumberInRange(0f,1.5f),
          Color(100, 100, 100, 55))
      }

      //横杠闪光
      MagicRender.battlespace(Global.getSettings().getSprite("graphics/fx/starburst_glow1.png"),
        point,
        Misc.ZERO,
        Vector2f(200f * sizeMult, 600f * sizeMult),
        Vector2f(-10f * sizeMult, -100f * sizeMult),
        90f,
        0f,
        Color(250, 250, 240, 250),
        true, 0f, 0.1f, 0.9f)

      //横杠烟雾左,右
      val numOfSmoke = 12
      val maxRange = 150f * sizeMult
      val maxSize = 100f * sizeMult
      val minSize = 36f * sizeMult
      val maxSpeed = 50f
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

      //声音
      Global.getSoundPlayer().playSound("aEP_m_l_blasthammer_explode", 1f, 0.5f, point, Misc.ZERO)

      //计算弹体剩余血量
      val lostLevel = (1f-missile.hullLevel).coerceAtLeast(0f).coerceAtMost(1f)
      val factor = 1f //- DAMAGE_DROP_PERCENT * lostLevel
      //生成弹丸
      for (i in 1..FRAG_SHOT_NUM){
        val cls = SpawnProjDelayed(
          Global.getCombatEngine().elapsedInLastFrame*(1f+i),
          missile, missile.location,
          aEP_m_m_blasthammer_shot::class.java.simpleName.replace("_shot","")+"3",
          DamageType.FRAGMENTATION,missile.baseDamageAmount * FRAG_PERCENT * factor)
        addEffect(cls)
      }
      //aEP_Tool.addDebugLog("${missile.damage.damage} _ ${pro1.damage.damage} _ ${pro2.damage.damage} _ ${pro3.damage.damage}" )

      engine.removeEntity(missile)
    }
  }
  //param和target有概率null
  override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
    if(param is MissileAPI && param.projectileSpecId != null){

      var sizeMult = 0f
      if(param.projectileSpecId.equals("aEP_m_m_blasthammer_shot"))  sizeMult = 0.75f
      if(param.projectileSpecId.equals("aEP_m_l_blasthammer_shot"))  sizeMult = 1f
      if(sizeMult <= 0f) return null

      //特效和刷弹丸在这个函数里面
      explode(sizeMult,param)
      damage.type = DamageType.KINETIC
      damage.modifier.modifyMult(aEP_m_m_blasthammer_shot.KEY,KINETIC_PERCENT)
      return aEP_m_m_blasthammer_shot.KEY

    }
    return null
  }
}
//这个类用于实现在导弹转向时，只消耗一半的飞行时间
class ApproximatePrimer(val missile: MissileAPI, val explodeSize : Float) : aEP_BaseCombatEffect(0f,missile) {
  override fun advanceImpl(amount: Float){
    //转向时延长飞行时间
    if(!missile.engineController.isAccelerating){
      missile.flightTime -= amount * 0.5f
    }

  }

  override fun readyToEnd() {
    super.readyToEnd()
  }
}
// 延迟一帧后生成弹丸
class SpawnProjDelayed(lifeTime: Float,val missile:DamagingProjectileAPI, val spawnPoint: Vector2f?, val toSpawnWeaponId:String,val damageType:DamageType,val damageAmount:Float): aEP_BaseCombatEffect(lifeTime){
  override fun readyToEnd() {
    //生成动能弹丸
    //注意一下BALLISTIC_AS_BEAM并不能被修改初始速度哦
    val jetWeaponId = toSpawnWeaponId
    val spawnLoc = Vector2f(missile.location)
    if(spawnPoint != null) spawnLoc.set(spawnPoint)
    //生成破片弹丸
    val pro3 = Global.getCombatEngine().spawnProjectile(
      missile.source,  //source ship
      missile.weapon,  //source weapon,
      jetWeaponId,  //whose proj to be use
      spawnLoc,  //loc
      missile.facing,  //facing
      null) as DamagingProjectileAPI
    //神奇alex，setDamage要输入baseDamage但是getDamage得到的是加成后的数（但未计入stats里面的加成）
    pro3.damage.damage = damageAmount
  }
}

//气钉枪导弹
class aEP_m_s_harpoon_shot: Effect(){
  companion object{
    val HIT_COLOR = Color(165,215,255,200)
    val FRAG_COLOR = Color(230,240,255,178)
    val FRAG_GLOW_COLOR = Color(155,175,255,100)
    var DAMAGE = 400f
    const val KEY = "aEP_m_s_harpoon_shot"

  }

  init {
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) DAMAGE = num.toFloat()
      i += 1
    }
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    engine?:return
    projectile?:return
    point?:return

    //创造一坨碎屑特效
    val facing = projectile.facing
    if (Global.getCombatEngine().viewport.isNearViewport(point, 800f)) {
      val numParticles = 16
      val minSize = 5f
      val maxSize = 30f
      val pc: Color = aEP_m_l_harpoon_shot.HIT_COLOR
      val minDur = 0.2f
      val maxDur = 0.6f
      val arc = 0f
      val scatter = 1f
      val minVel = 150f
      val maxVel = 600f
      val endSizeMin = 5f
      val endSizeMax = 10f
      val spawnPoint = Vector2f(point)
      for (i in 0 until numParticles) {
        var angleOffset = Math.random().toFloat()
        if (angleOffset > 0.2f) {
          angleOffset *= angleOffset
        }
        var speedMult = 1f - angleOffset
        speedMult = 0.5f + speedMult * 0.5f
        angleOffset *= sign((Math.random().toFloat() - 0.5f))
        angleOffset *= arc / 2f
        val theta = Math.toRadians((facing + angleOffset).toDouble()).toFloat()
        val r = (Math.random() * Math.random() * scatter).toFloat()
        val x = cos(theta.toDouble()).toFloat() * r
        val y = sin(theta.toDouble()).toFloat() * r
        val pLoc = Vector2f(spawnPoint.x + x, spawnPoint.y + y)
        var speed = minVel + (maxVel - minVel) * Math.random().toFloat()
        speed *= speedMult
        val pVel = Misc.getUnitVectorAtDegreeAngle(Math.toDegrees(theta.toDouble()).toFloat())
        pVel.scale(speed)
        val pSize = minSize + (maxSize - minSize) * Math.random().toFloat()
        val pDur = minDur + (maxDur - minDur) * Math.random().toFloat()
        val endSize = endSizeMin + (endSizeMax - endSizeMin) * Math.random().toFloat()
        Global.getCombatEngine().addNebulaParticle(pLoc, pVel, pSize, endSize, 0.1f, 0.5f, pDur, pc)
      }
    }
    for(i in 0 until 30){
      val randomSize = getRandomNumberInRange(1f,3f)
      val randomAngle = getRandomNumberInRange(-15f,15f) + facing
      val randomVel = speed2Velocity(randomAngle,400f)
      randomVel.scale(getRandomNumberInRange(0.25f,1f))
      val ms = aEP_MovingSprite(
        point,
        Vector2f(randomSize,randomSize),
        getRandomNumberInRange(0f,360f),
        "graphics/weapons/aEP_b_l_aa40/shell.png")
      ms.lifeTime = 1.2f + getRandomNumberInRange(0f,0.6f)
      ms.fadeOut = 0.35f
      ms.color = FRAG_COLOR
      ms.setInitVel(randomVel)
      ms.stopSpeed = 0.925f
      addEffect(ms)
      //addEffect(Glow(ms,FRAG_GLOW_COLOR))
    }

    val test = getRandomNumberInRange(0,100)
    if(test < 50){
      //断杆2
      MagicRender.battlespace(Global.getSettings().getSprite("aEP_FX","harpoon_empty2"),
        point,
        MathUtils.getRandomPointInCone(VECTOR2F_ZERO,220f, projectile.facing-15f, projectile.facing+15f),
        Vector2f(11f,34f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-120f,120f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(2f,3f), 0.5f)

      //断杆2
      MagicRender.battlespace(Global.getSettings().getSprite("aEP_FX","harpoon_empty3"),
        point,
        MathUtils.getRandomPointInCone(VECTOR2F_ZERO,220f, projectile.facing-15f, projectile.facing+15f),
        Vector2f(11f,34f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-120f,120f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(2f,3f), 0.5f)
    }else{
      //断杆2
      MagicRender.battlespace(Global.getSettings().getSprite("aEP_FX","harpoon_empty3"),
        point,
        MathUtils.getRandomPointInCone(VECTOR2F_ZERO,220f, projectile.facing-15f, projectile.facing+15f),
        Vector2f(11f,34f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-120f,120f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(2f,3f), 0.5f)
    }

    if(shieldHit && target is ShipAPI){
      //施加伤害，只造成软幅能
      engine.applyDamage(
        target,point, DAMAGE, DamageType.KINETIC, 0f, false,true, projectile.source)
      //声音
      Global.getSoundPlayer().playSound("aEP_m_l_harpoon_hit_shield", 1f, 1f, point, Misc.ZERO)
    }else{
      //声音
      Global.getSoundPlayer().playSound("aEP_m_l_harpoon_hit_armor", 1f, 1f, point, Misc.ZERO)
    }
  }

}
class aEP_m_l_harpoon_shot : Effect(){
  companion object{
    val HIT_COLOR = Color(165,215,255,200)
    val FRAG_COLOR = Color(230,240,255,178)
    val FRAG_GLOW_COLOR = Color(155,175,255,100)
    var DAMAGE = 750f
    const val KEY = "aEP_m_l_harpoon_shot"

  }

  init {
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) DAMAGE = num.toFloat()
      i += 1
    }
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    engine?:return
    projectile?:return
    point?:return
    //创造一坨碎屑特效
    val facing = projectile.facing
    if (Global.getCombatEngine().viewport.isNearViewport(point, 800f)) {
      val numParticles = 16
      val minSize = 5f
      val maxSize = 40f
      val pc: Color = HIT_COLOR
      val minDur = 0.2f
      val maxDur = 0.8f
      val arc = 0f
      val scatter = 1f
      val minVel = 200f
      val maxVel = 800f
      val endSizeMin = 5f
      val endSizeMax = 10f
      val spawnPoint = Vector2f(point)
      for (i in 0 until numParticles) {
        var angleOffset = Math.random().toFloat()
        if (angleOffset > 0.2f) {
          angleOffset *= angleOffset
        }
        var speedMult = 1f - angleOffset
        speedMult = 0.5f + speedMult * 0.5f
        angleOffset *= sign((Math.random().toFloat() - 0.5f))
        angleOffset *= arc / 2f
        val theta = Math.toRadians((facing + angleOffset).toDouble()).toFloat()
        val r = (Math.random() * Math.random() * scatter).toFloat()
        val x = cos(theta.toDouble()).toFloat() * r
        val y = sin(theta.toDouble()).toFloat() * r
        val pLoc = Vector2f(spawnPoint.x + x, spawnPoint.y + y)
        var speed = minVel + (maxVel - minVel) * Math.random().toFloat()
        speed *= speedMult
        val pVel = Misc.getUnitVectorAtDegreeAngle(Math.toDegrees(theta.toDouble()).toFloat())
        pVel.scale(speed)
        val pSize = minSize + (maxSize - minSize) * Math.random().toFloat()
        val pDur = minDur + (maxDur - minDur) * Math.random().toFloat()
        val endSize = endSizeMin + (endSizeMax - endSizeMin) * Math.random().toFloat()
        Global.getCombatEngine().addNebulaParticle(pLoc, pVel, pSize, endSize, 0.1f, 0.5f, pDur, pc)
      }
    }
    for(i in 0 until 48){
      val randomSize = getRandomNumberInRange(2f,4f)
      val randomAngle = getRandomNumberInRange(-15f,15f) + facing
      val randomVel = speed2Velocity(randomAngle,500f)
      randomVel.scale(getRandomNumberInRange(0.25f,1f))
      val ms = aEP_MovingSprite(
        point,
        Vector2f(randomSize,randomSize),
        getRandomNumberInRange(0f,360f),
        "graphics/weapons/aEP_b_l_aa40/shell.png")
      ms.lifeTime = 1.2f + getRandomNumberInRange(0f,0.6f)
      ms.fadeOut = 0.35f
      ms.color = FRAG_COLOR
      ms.setInitVel(randomVel)
      ms.stopSpeed = 0.925f
      addEffect(ms)
      //addEffect(Glow(ms, FRAG_GLOW_COLOR))
    }
    val test = getRandomNumberInRange(0,100)
    if(test < 35){
      //杆子
      MagicRender.battlespace(Global.getSettings().getSprite("aEP_FX","harpoon_empty"),
        getExtendedLocationFromPoint(projectile.location,projectile.facing,0f),
        MathUtils.getRandomPointInCone(VECTOR2F_ZERO,220f, projectile.facing-15f, projectile.facing+15f),
        Vector2f(11f,34f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-120f,120f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(3f,4f), 0.5f)
    }else if (test < 70){
      //断杆上
      MagicRender.battlespace(Global.getSettings().getSprite("aEP_FX","harpoon_empty2"),
        getExtendedLocationFromPoint(projectile.location,projectile.facing,20f),
        MathUtils.getRandomPointInCone(VECTOR2F_ZERO,220f, projectile.facing-15f, projectile.facing+15f),
        Vector2f(11f,34f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-120f,120f),
        Color(250, 250, 250, 250),
        false, 0f, MathUtils.getRandomNumberInRange(3f,4f), 0.5f)
      //断杆下
      MagicRender.battlespace(Global.getSettings().getSprite("aEP_FX","harpoon_empty3"),
        getExtendedLocationFromPoint(projectile.location,projectile.facing,-20f),
        MathUtils.getRandomPointInCone(VECTOR2F_ZERO,220f, projectile.facing-15f, projectile.facing+15f),
        Vector2f(11f,34f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-120f,120f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(3f,4f), 0.5f)
    }else{
      //断杆上
      MagicRender.battlespace(Global.getSettings().getSprite("aEP_FX","harpoon_empty4"),
        getExtendedLocationFromPoint(projectile.location,projectile.facing,0f),
        MathUtils.getRandomPointInCone(VECTOR2F_ZERO,220f, projectile.facing-15f, projectile.facing+15f),
        Vector2f(11f,34f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-120f,120f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(3f,4f), 0.5f)
    }

    if(shieldHit && target is ShipAPI){
      //施加伤害，只造成软幅能
      engine.applyDamage(
        target,point, DAMAGE, DamageType.KINETIC, 0f, false,true, projectile.source)
      //声音
      Global.getSoundPlayer().playSound("aEP_m_l_harpoon_hit_shield", 1f, 1f, point, Misc.ZERO)
    }else{
      //声音
      Global.getSoundPlayer().playSound("aEP_m_l_harpoon_hit_armor", 1f, 1f, point, Misc.ZERO)
    }
  }

}
class Glow(val ms:aEP_MovingSprite, val color : Color):aEP_BaseCombatEffect(){
  var size = 10f
  var minSize = 5f
  override fun advanceImpl(amount: Float) {
    if(ms.time>=ms.lifeTime){
      shouldEnd = true
      return
    }
    val  level = 1f - ms.time/ms.lifeTime
    Global.getCombatEngine().addSmoothParticle(
      ms.loc,
      aEP_ID.VECTOR2F_ZERO,
      size*level+minSize,
      0.5f + 0.5f*level,
      Global.getCombatEngine().elapsedInLastFrame * 2f,
      color)
  }
}

//荡平反应炸弹1,2
//1已经作废
class aEP_ftr_bom_nuke_bomb_shot1 : Effect(), DamageDealtModifier{
  companion object{
    val WEAPON_ID = aEP_ftr_bom_nuke_bomb_shot1::class.java.simpleName.toString().replace("_shot1","")
    val JITTER_COLOR = Color(195,146,25)
  }
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val ship = weapon.ship
    if (!ship.hasListenerOfClass(this::class.java)) {
      ship.addListener(this)
    }
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    val mine = engine.spawnProjectile(projectile.source,
      projectile.weapon,
      "aEP_ftr_bom_nuke_bomb2",
      projectile.location,
      projectile.facing,
      projectile.velocity) as MissileAPI
    mine.hitpoints = projectile.hitpoints
  }

  override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
    if (param is MissileAPI) {
      if (param.weaponSpec != null && param.weaponSpec.weaponId == WEAPON_ID) {
        val base = damage.baseDamage
        damage.damage = 100f
        return WEAPON_ID
      }
    }
    return null
  }
}
//目前只用2
class aEP_ftr_bom_nuke_bomb_shot2 : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    if(projectile is MissileAPI){
      addEffect(object : ShipProximityTrigger(projectile, 25f){
        var timeEclipsedPrimed = 0f
        var didPrime = false
        var level = 0f
        override fun advanceImpl(amount: Float) {

          val closestShip : ShipAPI?= aEP_Tool.getNearestEnemyCombatShip(missile)
          if(closestShip != null){
            val dist = MathUtils.getDistance(closestShip, missile.location)
            if(dist <= fuseRange && ! didPrime){
              didPrime = true
              missile.flightTime = missile.maxFlightTime
            }
          }

          if(missile.isMinePrimed ){
            timeEclipsedPrimed += amount
            level = (timeEclipsedPrimed/(missile.behaviorSpecParams["delay"].toString().toFloat())).coerceAtMost(1f)
            missile.setJitter(WEAPON_ID,Misc.setAlpha(JITTER_COLOR,(100+150*level).toInt()),level,10,25f)
            missile.weaponSpec.setProjectileSpeed(20f)

            val renderLevel = level
            //这里初始点-50f是为了保证最后一刻2个环相互对准，不要乱动
            val startAngle = MagicAnim.smooth(renderLevel * 1.1f) * -360f + 90f
            for(i in 0..3){
              val indicatorAngle = startAngle + i * 90f - 25f
              val indicatorPoint = getExtendedLocationFromPoint(missile.location, indicatorAngle, 200f - 125f * renderLevel)
              val sprite = Global.getSettings().getSprite("aEP_FX","loading_ring")
              val ringSize = Vector2f(128f,128f)
              MagicRender.singleframe(
                sprite, indicatorPoint, ringSize, indicatorAngle - 90f,
                Color(1f,1f * (1f-renderLevel),0f,0.4f * renderLevel),
                false, CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
            }

          }
        }

        override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
          if(missile.isMinePrimed){
            drawCircle(level, missile.location, timeEclipsedPrimed)
          }
        }
      })
    }

  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {

    val ring = aEP_SpreadRing(
      1200f,
      100f,
      Color(235,120,80,100),
      100f,600f,explosion.location)
    ring.layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER)
    ring.initColor.setToColor(235f,120f,80f,10f,0.35f)
    ring.endColor.setColor(255f,90f,20f,0f)
    addEffect(ring)

    val SIZE_MULT = 0.75f //default = 300;

    //初始闪光
    Global.getCombatEngine().addHitParticle(
      explosion.location, Misc.ZERO,
      1500 * SIZE_MULT, 1f, 0.2f, 0.3f, Color.white)


    val point = explosion?.location?: Vector2f(999999f,999999f)
    val vel = Vector2f(0f, 0f)
    val yellowSmoke = Color(250, 250, 220)
    //add red center explosion smoke
    Global.getCombatEngine().spawnExplosion(
      point,
      vel,
      yellowSmoke,
      300f * SIZE_MULT,
      5f)

    //add white center glow
    Global.getCombatEngine().addSmoothParticle(point, vel, 300 * SIZE_MULT, 1f, 0.25f, 1f, Color.white)
    Global.getCombatEngine().addSmoothParticle(point, vel, 300 * SIZE_MULT, 1f, 0.25f, 1f, Color.white)
    Global.getCombatEngine().addSmoothParticle(point, vel, 300 * SIZE_MULT, 1f, 0.25f, 1f, Color.white)
    Global.getCombatEngine().addSmoothParticle(point, vel, 300 * SIZE_MULT, 1f, 0.25f, 1f, Color.white)


    //add white center hit glow
    Global.getCombatEngine().addHitParticle(
      point,
      vel,
      300 * SIZE_MULT,
      2f,
      6f,
      Color.white)

    //add yellow around glow
    Global.getCombatEngine().addSmoothParticle(
      point,
      vel,
      450 * SIZE_MULT,
      0.8f,
      5f,
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
    var numMax = 24
    var angle = 0f
    for (i in 0 until numMax) {
      val loc = getExtendedLocationFromPoint(point, angle, 150f * SIZE_MULT)
      val sizeGrowth = 60 * SIZE_MULT
      val sizeAtMin = 175 * SIZE_MULT
      val moveSpeed = 60f * SIZE_MULT
      val smoke = aEP_MovingSmoke(loc)
      smoke.setInitVel(aEP_Tool.speed2Velocity(VectorUtils.getAngle(point, loc), moveSpeed))
      smoke.fadeIn = 0.1f
      smoke.fadeOut = 0.4f
      smoke.lifeTime = 3f
      smoke.stopSpeed = 0.95f
      smoke.sizeChangeSpeed = sizeGrowth
      smoke.size = sizeAtMin
      smoke.color = Color(200, 200, 200, 71)
      aEP_CombatEffectPlugin.addEffect(smoke)
      angle += 360f / numMax
    }

    numMax = 10
    angle = 0f
    for (i in 0 until numMax) {
      val size = 400f * SIZE_MULT
      Global.getCombatEngine().addNebulaSmokeParticle(
        point, aEP_ID.VECTOR2F_ZERO,
        size,1.5f,
        0.2f, 0.5f,4f,Color(250, 250, 215, 121))
      angle += 360f / numMax
    }
  }

  fun drawCircle(level: Float, point: Vector2f, timeEclipsedPrimed:Float){
    val renderLevel = level

    //begin
    aEP_Render.openGL11CombatLayerRendering()

    val center = Vector2f(point)

    val width = 10f
    val largeRad = 50f + 100f * level
    val smallRad = largeRad - width

    //画间隔条纹
    val angleStripe = 45
    val numOfVertex = 5
    var angle = 0f

    val startAngle = MagicAnim.smooth(renderLevel* 1.1f) * 360f
    while (angle < 360f){
      GL11.glBegin(GL11.GL_QUAD_STRIP)
      for (i in 0 until numOfVertex) {
        val toDrawAngle = startAngle + angle
        val pointFar = getExtendedLocationFromPoint(center, toDrawAngle, largeRad)
        GL11.glColor4f(1f,1f * (1f-level),0f, 0.4f * renderLevel)
        GL11.glVertex2f(pointFar.x, pointFar.y)

        val pointNear = getExtendedLocationFromPoint(center, toDrawAngle, smallRad)
        GL11.glColor4f(1f,1f * (1f-level),0f, 0.1f * renderLevel)
        GL11.glVertex2f(pointNear.x, pointNear.y)
        angle += angleStripe/numOfVertex
      }
      angle += angleStripe
      GL11.glEnd()
    }

    aEP_Render.closeGL11()
  }
}

//破门锥
class aEP_m_s_breachdoor_shot : Effect(){
  companion object{
    var MAX_OVERLOAD_TIME = 2.5f
  }

  init {
    val hlString = Global.getSettings().getWeaponSpec(aEP_m_s_breachdoor_shot::class.simpleName?.replace("_shot",""))?.customPrimaryHL
    MAX_OVERLOAD_TIME = hlString?.split("|")?.get(0)?.toFloat() ?: MAX_OVERLOAD_TIME
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile.mass = 1000f
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {

    point?: return
    val vel = Vector2f(0f, 0f)
    if (target !is ShipAPI) return
    if (shieldHit) {
      engine.applyDamage(
        target,  //target
        point,  // where to apply damage
        projectile.damage.fluxComponent,  // amount of damage
        DamageType.KINETIC,  // damage type
        0f,  // amount of EMP damage (none)
        false,  // does this bypass shields? (no)
        true,  // does this deal soft flux? (no)
        projectile.source)
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
    CombatUtils.applyForce(target, projectile.velocity, 100f)
  }

}

//异象导弹 暖池
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

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile ?: return
    for( i in 0 until 8 ){
      val angle = i * 45f
      val loc = aEP_Tool.getExtendedLocationFromPoint(projectile.location,angle, EMP_CLOUD_RADIUS/2f)
      val cloudEntity = engine?.spawnProjectile(projectile.source,projectile.weapon,EMP_WARHEAD_WEAPON_ID,loc,angle,null) as MissileAPI
      cloudEntity.source = projectile.source
      addEffect(EmpArcCloud(cloudEntity as MissileAPI,4f))
    }

  }

}
class EmpArcCloud : aEP_BaseCombatEffect {
  val arcTracker = IntervalUtil(0.25f,0.25f)
  val smokeTracker = IntervalUtil(0.125f,0.125f)

  constructor(entity: CombatEntityAPI, lifeTime: Float){
    init(entity)
    this.lifeTime = lifeTime

    val engine = Global.getCombatEngine()
    val spec = DamagingExplosionSpec(1f, aEP_cap_nuanchi_missile_shot2.EMP_CLOUD_RADIUS, aEP_cap_nuanchi_missile_shot2.EMP_CLOUD_RADIUS /2f,
      100f,50f,
      CollisionClass.HITS_SHIPS_AND_ASTEROIDS, CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
      20f,10f,3f,0, aEP_cap_nuanchi_missile_shot2.CLOUD_COLOR, aEP_cap_nuanchi_missile_shot2.CLOUD_COLOR
    )
    spec.isUseDetailedExplosion = true
    spec.detailedExplosionFlashDuration = 0.3f
    spec.detailedExplosionFlashRadius = 200f
    spec.detailedExplosionRadius = aEP_cap_nuanchi_missile_shot2.EMP_CLOUD_RADIUS
    engine.spawnDamagingExplosion(spec,(entity as MissileAPI).source,entity.location)
  }

  override fun advanceImpl(amount: Float) {
    val engine = Global.getCombatEngine()
    val mine = entity as MissileAPI
    val effectiveLevel = time/lifeTime;
    val nowSize = aEP_cap_nuanchi_missile_shot2.EMP_CLOUD_RADIUS * aEP_cap_nuanchi_missile_shot2.EMP_CLOUD_END_RADIUS_MULT * effectiveLevel

    smokeTracker.advance(amount)
    arcTracker.advance(amount)
    if(smokeTracker.intervalElapsed()){
      engine.addNebulaParticle(
        mine.location, aEP_ID.VECTOR2F_ZERO,
        nowSize, 2f,
        0.25f,0.25f,1.5f, aEP_cap_nuanchi_missile_shot2.CLOUD_COLOR
      )
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
        DamageType.ENERGY, 10f, aEP_cap_nuanchi_missile_shot2.EMP_PER_HIT,
        target.collisionRadius + nowSize/2f,//这是电弧的最大长度，如果电弧发出点在船头，想电船尾电不到就会点空气
        "tachyon_lance_emp_impact",12f,
        aEP_cap_nuanchi_missile_shot2.ARC_COLOR_FRINGE,
        aEP_cap_nuanchi_missile_shot2.ARC_COLOR)
    }

  }
}
//暖池装饰武器
class aEP_cap_nuanchi_glow : aEP_DecoAnimation() {
  var ring1: aEP_DecoAnimation? = null
  var ring2: aEP_DecoAnimation? = null
  var ring3: aEP_DecoAnimation? = null
  var ring4: aEP_DecoAnimation? = null

  var fadeTime = 6f
  var timeAfterActive = 99f
  var glowLevel = 0f

  val smokeTracker = IntervalUtil(0.25f,0.25f)

  val bonusRingSpeed = 2f

  override fun advance(amount: Float, engine: CombatEngineAPI?, weapon: WeaponAPI) {
    super.advance(amount, engine, weapon)

    //初始化4个活塞
    if(ring1 == null && ring2 == null && ring3 == null && ring4 == null){
      for(w in weapon.ship.allWeapons){
        if(!w.spec.weaponId.equals("aEP_cap_nuanchi_ring")) continue
        if(ring1 == null) {
          ring1 = (w.effectPlugin as aEP_DecoAnimation)
          continue
        }
        if(ring2 == null) {
          ring2 = (w.effectPlugin as aEP_DecoAnimation)
          continue
        }
        if(ring3 == null) {
          ring3 = (w.effectPlugin as aEP_DecoAnimation)
          continue
        }
        if(ring4 == null) {
          ring4 = (w.effectPlugin as aEP_DecoAnimation)
          continue
        }
      }
    }

    timeAfterActive += amount

    //计算glowLevel
    glowLevel = 0f
    if(timeAfterActive < fadeTime){
      glowLevel = (fadeTime - timeAfterActive)/fadeTime
    }

    setGlowToLevel(glowLevel)


    //往返运动
    if (ring1?.getDecoMoveController()?.effectiveLevel
      ==  ring1?.getDecoMoveController()?.toLevel){
      ring1?.getDecoMoveController()?.speed = 1.9f + 3f *  glowLevel
      if(ring1?.getDecoMoveController()?.effectiveLevel == 1f){
        ring1?.setMoveToLevel(0f)
      }
      if(ring1?.getDecoMoveController()?.effectiveLevel == 0f){
        ring1?.setMoveToLevel(1f)
      }
    }

    if (ring2?.getDecoMoveController()?.effectiveLevel
      ==  ring2?.getDecoMoveController()?.toLevel){
      ring2?.getDecoMoveController()?.speed = 1.5f + 3f *  glowLevel
      if(ring2?.getDecoMoveController()?.effectiveLevel == 1f){
        ring2?.setMoveToLevel(0f)
      }
      if(ring2?.getDecoMoveController()?.effectiveLevel == 0f){
        ring2?.setMoveToLevel(1f)
      }
    }

    if (ring3?.getDecoMoveController()?.effectiveLevel
      ==  ring3?.getDecoMoveController()?.toLevel){
      ring3?.getDecoMoveController()?.speed = 1.1f + 3f *  glowLevel
      if(ring3?.getDecoMoveController()?.effectiveLevel == 1f){
        ring3?.setMoveToLevel(0f)
      }
      if(ring3?.getDecoMoveController()?.effectiveLevel == 0f){
        ring3?.setMoveToLevel(1f)
      }
    }

    if (ring4?.getDecoMoveController()?.effectiveLevel
      ==  ring4?.getDecoMoveController()?.toLevel){
      ring4?.getDecoMoveController()?.speed = 0.6f + 3f *  glowLevel
      if(ring4?.getDecoMoveController()?.effectiveLevel == 1f){
        ring4?.setMoveToLevel(0f)
      }
      if(ring4?.getDecoMoveController()?.effectiveLevel == 0f){
        ring4?.setMoveToLevel(1f)
      }
    }

    //发光
    val ring1Loc = getExtendedLocationFromPoint(weapon.location, weapon.currAngle -25f, -25f)
    val ring4Loc = getExtendedLocationFromPoint(weapon.location, weapon.currAngle +25f, -25f)
    //val mid1Loc = getExtendedLocationFromPoint(weapon.location, weapon.currAngle, 15f)

    //烟雾
    smokeTracker.advance(amount)
    if(smokeTracker.intervalElapsed()){
      val locList = ArrayList<Vector2f>()
      locList.add(ring1Loc)
      locList.add(ring4Loc)
      //locList.add(mid1Loc)
      for(location in locList){
        var initColor = Color(210,200,200)
        var alpha = 0.32f * glowLevel
        var lifeTime = 2f * glowLevel
        var size = 30f
        var endSizeMult = 1.5f
        var vel = aEP_Tool.speed2Velocity(getRandomNumberInRange(0f,360f),10f)
        Global.getCombatEngine().addNebulaParticle(
          location,
          vel,
          size, endSizeMult,
          0.1f, 0.4f,
          lifeTime * getRandomNumberInRange(1f,2f),
          aEP_Tool.getColorWithAlpha(initColor,alpha))

        //必须要求的数据是Color，先设置lifetime后设置fadeIn，autoFadeOut，intenseLevel，location，add进shader
        //注意StandardLight自带的anchor只是绑在entity的中心罢了，这里用不上
        val light = StandardLight(location, Misc.ZERO, Misc.ZERO,null)
        val lightLifetime = smokeTracker.elapsed + 0.025f
        light.setColor(Color(255,50,50,25))
        light.intensity = 0.5f * glowLevel + 0.1f
        light.size = 9f * glowLevel + 3f
        light.setLifetime(lightLifetime)
        LightShader.addLight(light)

        val anchored = aEP_AnchorStandardLight(light,weapon.ship,lightLifetime)
        aEP_CombatEffectPlugin.addEffect(anchored)
      }
    }

    //激活时强制全部收起，最高优先级，放在最后
    if(weapon.ship?.system?.isActive == true){
      timeAfterActive = 0f
      ring1?.getDecoMoveController()?.speed = 3f
      ring1?.setMoveToLevel(1f)
      ring2?.getDecoMoveController()?.speed = 3f
      ring2?.setMoveToLevel(1f)
      ring3?.getDecoMoveController()?.speed = 3f
      ring3?.setMoveToLevel(1f)
      ring4?.getDecoMoveController()?.speed = 3f
      ring4?.setMoveToLevel(1f)
      return
    }
  }
}

//对流 温跃层主炮 无光层加速器
class aEP_cap_duiliu_main_shot : Effect(){
  companion object{
    val BLINK_COLOR1 = Color(255,80,50,225)
    val BLINK_COLOR2 = Color(255,225,190,155)

    val ARC_COLOR1 = Color(225,75,12,185)
    val ARC_COLOR2 = Color(205,175,150,155)

    //战列舰能有4000质量，其实并不多
    const val IMPULSE_ON_HIT = 80000f
    //对于战列舰，每秒吸5的速度，完全被加速度抵消了
    const val IMPULSE_ON_DRAG = 32000f
  }

  var damage = 750f
  init {
    val hlString = Global.getSettings().getWeaponSpec(aEP_cap_duiliu_main_shot::class.simpleName?.replace("_shot",""))?.customPrimaryHL
    damage = hlString?.split("|")?.get(2)?.toFloat() ?: damage
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    //create bright spark at center
    engine.addSmoothParticle(
      projectile.location,
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

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    if(target !is ShipAPI) return
    projectile?:return
    point ?: return
    target ?: return
    aEP_Tool.applyImpulse(target, projectile.facing,IMPULSE_ON_HIT, 300f)

    val onHit = object :aEP_StickOnHit(
      6f,
      target,
      point,
      "weapons.DL_pike_shot_inHull",
      "weapons.DL_pike_shot",
      projectile.facing,
      shieldHit ){
      val blinkTracker = IntervalUtil(0.95f,0.95f)

      override fun advanceImpl(amount: Float) {

        if(entity != null && projectile.weapon != null && projectile.weapon.ship != null){
          val entity = entity as ShipAPI
          val facingToWeapon = VectorUtils.getAngle(entity.location, projectile.weapon.location)
          val toWeapon = getRandomPointInCone(renderLoc, entity.collisionRadius, facingToWeapon-10f,facingToWeapon +10f)

          //每帧施加拖拽速度
          val distSq = getDistanceSquared(entity,  projectile.weapon.ship)
          if(distSq > 40000f){
            aEP_Tool.applyImpulse(entity, facingToWeapon, IMPULSE_ON_DRAG * amount, 100f)
          }

          blinkTracker.advance(amount)
          if(blinkTracker.intervalElapsed()){

            Global.getCombatEngine().addSmoothParticle(renderLoc,entity?.velocity?: VECTOR2F_ZERO,200f,1f,0.25f,0.2f, BLINK_COLOR1)
            Global.getCombatEngine().addSmoothParticle(renderLoc,entity?.velocity?: VECTOR2F_ZERO,75f,1f,0.25f,0.2f, BLINK_COLOR2)
            Global.getCombatEngine().spawnEmpArcVisual(
              renderLoc, entity,
              toWeapon, null,
              30f,
              ARC_COLOR1,
              ARC_COLOR2)

            val facingToTarget = VectorUtils.getAngle(projectile.weapon.location, entity.location)
            val toTarget = MathUtils.getRandomPointInCone(projectile.weapon.location, 100f, facingToTarget-10f,facingToTarget +10f)
            Global.getCombatEngine().spawnEmpArcVisual(
              projectile.weapon.location, projectile.source,
              toTarget, null,
              30f,
              ARC_COLOR1,
              ARC_COLOR2)

          }

        }


      }

      override fun readyToEnd() {
        //如果时间几乎走完了才脱离，就爆炸
        if(time >= lifeTime-0.1f) {
          val spec = DamagingExplosionSpec(
            0.0001f,
            100f,20f,
            damage,damage/3f,
            CollisionClass.HITS_SHIPS_AND_ASTEROIDS,CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
            10f,10f,1f,
            100,Color.yellow,BLINK_COLOR1)
          spec.damageType = DamageType.HIGH_EXPLOSIVE
          Global.getCombatEngine().spawnDamagingExplosion(spec, projectile.source, renderLoc)
        }
        super.readyToEnd()
      }

    }
    onHit.sprite.setSize(10f,50f)
    addEffect(onHit)

  }
}

//内波 主炮 黑星改
class aEP_cap_neibo_main_shot : Effect(){
  var chance = 0.25f
  var damage = 100f
  init {
    //把customHL一位一位读char，计算 “|”的情况
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0){
        chance =  num.replace("%","").toFloat()
      }
      if(i == 1){
        damage =  num.toFloat()
      }

      i += 1
    }
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    if(shieldHit) return
    if(target !is ShipAPI) return
    if(MathUtils.getRandomNumberInRange(0f,1f) > chance) return
    engine.applyDamage(
      target,point,
      damage,DamageType.FRAGMENTATION,
      0f,
      false,false,
      projectile?.source,false)
    //红色爆炸特效
    engine.addSmoothParticle(point,aEP_ID.VECTOR2F_ZERO,160f,1f,0.5f,0.2f,Color.red)
    var randomVel = MathUtils.getRandomPointInCone(point,75f, angleAdd(projectile?.facing?:0f,-200f),angleAdd(projectile?.facing?:0f,160f))
    randomVel = Vector2f(randomVel.x- (point?.x?:0f),randomVel.y-(point?.y?:0f))
    engine.spawnExplosion(point, Vector2f(target.velocity.x + randomVel.x,target.velocity.y+ randomVel.y) , aEP_b_l_dg3_shot.EXPLOSION_COLOR,120f,0.8f)
  }
}

//平定主炮
class aEP_cru_pingding_main :EveryFrame(), WeaponRangeModifier{
  companion object{
    val id = "aEP_cru_pingding_main"
    var TAIL_SMOKE_COLOR = Color(255,255,245)
  }

  val backL1 = Global.getSettings().getSprite("weapons","aEP_PD_b")
  val backL2 = Global.getSettings().getSprite("weapons","aEP_PD_f")
  val backR1 = Global.getSettings().getSprite("weapons","aEP_PD_b")
  val backR2 = Global.getSettings().getSprite("weapons","aEP_PD_f")
  val br = Global.getSettings().getSprite("weapons","aEP_PD_br")

  val smokeTimer = IntervalUtil(0.033f,0.033f)
  var w : WeaponAPI? = null
  var didMusic = false

  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    w = weapon

    //加入调整激光测距仪listener
    if(weapon.ship != null && !weapon.ship.hasListenerOfClass(aEP_cru_pingding_main::class.java)){
      weapon.ship.addListener(this)
    }

    val useLevel = MathUtils.clamp(weapon.chargeLevel * 2f,0f,1f)
    val ship = weapon.ship?:return
    val effectLevel = weapon.chargeLevel?: 0f
    var layer = CombatEngineLayers.BELOW_SHIPS_LAYER
    if(useLevel > 0.9f){
      layer = CombatEngineLayers.CRUISERS_LAYER
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

    //关v排，给与转动机动性加成，发射测距激光
    if(effectLevel > 0.1f){
      ship.mutableStats.ventRateMult.modifyMult(id,0f)
      ship.mutableStats.turnAcceleration.modifyFlat(id,12f)
      for(w in weapon.ship.allWeapons){
        if(w.slot.isDecorative && w.spec.weaponId.equals("aEP_cru_pingding_lidardish")){
          w.setForceFireOneFrame(true)
        }
      }

    }else{
      ship.mutableStats.ventRateMult.modifyMult(id,1f)
      ship.mutableStats.turnAcceleration.unmodify(id)
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
    if (!isDead(ship)) {
      smokeTimer.advance(amount)
      for (slot in ship.hullSpec.allWeaponSlotsCopy) {
        if(slot.id.contains("FOOT_B_L")) {
          val angle = slot.computeMidArcAngle(ship)
          val slotLoc = slot.computePosition(ship)
          //1是后面的，2是前面的
          val loc1 = getExtendedLocationFromPoint(slotLoc, angle, MagicAnim.smooth(useLevel) * 20)
          val loc2 = getExtendedLocationFromPoint(slotLoc, angle , MagicAnim.smooth(useLevel) * 53)
          MagicRender.singleframe(backL2,loc2,Vector2f(44f,45f),angle,Color.white,false,layer)
          MagicRender.singleframe(backL1,loc1,Vector2f(25f,15f),angle,Color.white,false,layer)
          if(smokeTimer.intervalElapsed()){
            spraySmoke(slotLoc,angle,useLevel)
          }


        }else if(slot.id.contains("FOOT_B_R")) {
          val angle = slot.computeMidArcAngle(ship)
          val slotLoc = slot.computePosition(ship)
          //1是后面的，2是前面的
          val loc1 = getExtendedLocationFromPoint(slotLoc, angle, MagicAnim.smooth(useLevel) * 20)
          val loc2 = getExtendedLocationFromPoint(slotLoc, angle , MagicAnim.smooth(useLevel) * 53)
          MagicRender.singleframe(backR2,loc2,Vector2f(44f,45f),angle,Color.white,false,layer)
          MagicRender.singleframe(backR1,loc1,Vector2f(25f,15f),angle,Color.white,false,layer)
          if(smokeTimer.intervalElapsed()){
            spraySmoke(slotLoc,angle,useLevel)
          }

        }else if(slot.id.contains("FRONT_BR")) {
          val angle = slot.computeMidArcAngle(ship)
          val slotLoc = slot.computePosition(ship)
          //1是后面的，2是前面的
          val loc1 = getExtendedLocationFromPoint(slotLoc, angle, MagicAnim.smooth(useLevel) * 86f)
          MagicRender.singleframe(br,loc1,Vector2f(br.width,br.height),angle,Color.white,false,layer)
        }
      }
    }

    //充能
    if (weapon.isFiring && weapon.cooldownRemaining <= 0f) {

      didMusic = false

    //作为按住鼠标才会连射的武器，每次开火后cooldownRemaining会变为cooldown的值，而不是burstInterval
    } else if(weapon.isFiring && weapon.cooldownRemaining < 1f && !didMusic) {

      didMusic = true
      //放音乐
      Global.getSoundPlayer().playSound(
        "aEP_cru_pingding_main_down",
        1f,1f,
        weapon.location,
        VECTOR2F_ZERO)

    //完全冷却（不再up也不再down）
    }else {


    }

  }

  fun spraySmoke(slotLoc: Vector2f, angle:Float, useLevel:Float){
    if(useLevel <= 0.35f) return

    val point = getExtendedLocationFromPoint(
      slotLoc,
      angle,
      MagicAnim.smooth(useLevel) * 53)
    val point1 = getExtendedLocationFromPoint(
      point,
      angle+45f,
      14f)
    val smoke = aEP_MovingSmoke(point1)
    smoke.lifeTime = 0.35f * useLevel
    smoke.fadeIn = 0.5f
    smoke.fadeOut = 0.5f
    smoke.size = 10f
    smoke.sizeChangeSpeed = 60f
    smoke.color = TAIL_SMOKE_COLOR
    smoke.setInitVel(speed2Velocity(angle+45f, 300f))
    smoke.stopForceTimer.setInterval(0.05f, 0.05f)
    smoke.stopSpeed = 0.975f
    addEffect(smoke)

    val point2 = getExtendedLocationFromPoint(
      point,
      angle-45f,
      14f)
    val smoke2 = aEP_MovingSmoke(point2)
    smoke2.lifeTime = 0.35f * useLevel
    smoke2.fadeIn = 0.5f
    smoke2.fadeOut = 0.5f
    smoke2.size = 10f
    smoke2.sizeChangeSpeed = 60f
    smoke2.color = TAIL_SMOKE_COLOR
    smoke2.setInitVel(speed2Velocity(angle-45f, 300f))
    smoke2.stopForceTimer.setInterval(0.05f, 0.05f)
    smoke2.stopSpeed = 0.975f
    addEffect(smoke2)
  }

  override fun getWeaponRangePercentMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    return 0f
  }

  override fun getWeaponRangeMultMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    return 1f
  }

  override fun getWeaponRangeFlatMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    if(weapon.spec.weaponId.equals("aEP_cru_pingding_lidardish")) return w?.range?:500f
    return 0f
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

//    val smokeTrail = aEP_SmokeTrail(projectile,
//      20f,1.65f,30f,40f,Color(240,240,240,120))
//    smokeTrail.stopSpeed = 0.975f
//    smokeTrail.smokeSpreadAngleTracker.speed = 3f
//    smokeTrail.smokeSpreadAngleTracker.max = 15f
//    smokeTrail.smokeSpreadAngleTracker.min = -15f
    //addEffect(smokeTrail)

    //最中心的黄色火焰
    Global.getCombatEngine().spawnExplosion(projectile.location,aEP_ID.VECTOR2F_ZERO,Color(185,105,10),80f,1f)

    //create side SmokeFire
    createFanSmoke(projectile.getSpawnLocation(), aEP_Tool.angleAdd(weapon.currAngle, 90f), weapon!!.ship)
    createFanSmoke(projectile.getSpawnLocation(), aEP_Tool.angleAdd(weapon.currAngle, -90f), weapon!!.ship)

    ship ?: return
    val blowBack = speed2Velocity(ship.facing-180f,40f)
    ship.velocity.set(ship.velocity.x + blowBack.x, ship.velocity.y+blowBack.y)
    val c = Color(255,255,255,56)
    val vel = speed2Velocity(Vector2f(ship.facing,200f))
    for(i in 0 until 10){
      val point = MathUtils.getRandomPointInCircle(ship.location, ship.collisionRadius)
      val rad = MathUtils.getRandomNumberInRange(200f,300f)
      aEP_Tool.spawnCompositeSmoke(point,rad,2f,c, vel)
    }
    //后坐力抵消， 引擎反推
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

    //距离补偿, 绑定弹丸
    if(projectile is MissileAPI){
      projectile.isRenderGlowAbove = false
      aEP_Tool.attachMissileToProj(projectile, weapon.range, weapon.projectileSpeed)
      aEP_Tool.compensateMissileInitialSpeed(projectile)
    }

  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    explode(point, projectile.facing - 180f)

  }

  fun explode(point: Vector2f, facing: Float){
    var hitColor = Color(240, 120, 50, 200)

    //create sparks
    var num = 1
    while (num <= 8) {
      var onHitAngle = facing
      onHitAngle += MathUtils.getRandomNumberInRange(-60, 60)
      val speed = MathUtils.getRandomNumberInRange(100, 200).toFloat()
      val lifeTime = MathUtils.getRandomNumberInRange(1f, 3f)
      addEffect(Spark(point, aEP_Tool.speed2Velocity(onHitAngle, speed), aEP_Tool.speed2Velocity(onHitAngle - 180f, speed / lifeTime), lifeTime))
      num ++
    }

    //create smokes
    num = 1
    while (num <= 24) {
      val loc = MathUtils.getRandomPointInCircle(point, 200f)
      val sizeGrowth = MathUtils.getRandomNumberInRange(0, 50).toFloat()
      val sizeAtMin = MathUtils.getRandomNumberInRange(50, 200).toFloat()
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

    val engine = Global.getCombatEngine()
    //create explode
    engine.spawnExplosion(
      point,  //color
      Vector2f(0f, 0f),  //vel
      hitColor,  //color
      250f,  //size
      1f) //duration
    engine.addNegativeParticle(
      point,
      Vector2f(0f, 0f),
      150f,
      0.5f,
      0.5f,
      Color(240, 240, 240, 200))
    engine.addSmoothParticle(
      point,
      Vector2f(0f, 0f),
      200f,
      0.5f,
      0.2f,
      hitColor)

    //play sound
    Global.getSoundPlayer().playSound(
      "aEP_RW_hit",
      1f, 2f,  // pitch,volume
      point,  //location
      Vector2f(0f, 0f)
    ) //velocity

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

//猎鲸 核动力鱼雷
//在进行weaponEffect的联动时，注意武器之间是平级，同时初始化everyFrame
//如果本武器率先初始化everyFrame，运行一帧advance，在其中操控了某个尚未初始化的武器的everyFrame，会null
class aEP_cru_pingding_torpedo :EveryFrame(){
  companion object{
    val id = "aEP_cru_pingding_torpedo"
    var TAIL_SMOKE_COLOR = Color(255,255,245)
  }

  var armor1: WeaponAPI? = null
  var armor2: WeaponAPI? = null
  var didSearchDeco = false
  var didMusic = false
  //用来记录进入装好/没弹状态下的时间，控制淡入淡出
  var time = 0f

  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    val ship = weapon.ship?:return

    //找武器，只找一次
    if(!didSearchDeco){
      didSearchDeco = true
      weapon.sprite.color = Color(0,0,0,0)
      weapon.setRemainingCooldownTo(3f)
      for(w in weapon.ship.allWeapons){
        if(w.spec.weaponId.equals("aEP_cru_pingding_armor1")) armor1 = w
        if(w.spec.weaponId.equals("aEP_cru_pingding_armor2")) armor2 = w
      }
    }


    //如果找到了装饰装甲，执行动画

    if(armor1 != null && armor2 != null){
      val anime1 = armor1!!.effectPlugin as aEP_DecoAnimation
      val anime2 = armor2!!.effectPlugin as aEP_DecoAnimation
      if(weapon.cooldownRemaining <= 1f && weapon.ammo >= 1){
        time += amount
        time = time.coerceAtMost(1f)
        val alpha = (time/1f)
        weapon.sprite.color = aEP_Tool.getColorWithAlpha(Color.white,alpha)
        //armor1先侧移，在后移
        anime1.setMoveToSideLevel(1f)
        if(anime1.decoMoveController.effectiveSideLevel >= 0.9f){
          anime1.setMoveToLevel(1f)
        }
        //armor2直接转动
        anime2.setRevoToLevel(1f)
      }else{
        time = 0f
        weapon.sprite.color = Color(0,0,0,0)
        //armor1先前移，在侧移
        anime1.setMoveToLevel(0f)
        //在进行weaponEffect的联动时，注意武器之间是平级，同时初始化everyFrame
        //如果本武器率先初始化everyFrame，运行一帧advance，在其中操控了某个尚未初始化的武器的everyFrame，会null
        //此处的armor1和armor2就是这个情况，他的everyFrameEffect还没初始化
        //一定要做防空检测
        if(anime1.decoMoveController?.effectiveLevel?:0f <= 0.1f){
          anime1.setMoveToSideLevel(0f)
        }
        //armor2直接转动
        anime2.setRevoToLevel(0f)
      }

    }

  }


}
class aEP_cru_pingding_torpedo_shot : Effect(){

  companion object{
    val HEAD_GLOW = Color(255,0,0)
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val m = projectile as MissileAPI
    //移除母舰的惯性
    m.velocity.scale(0.01f)
    addEffect(SearchLight(m))
    addEffect(PredictionStripe(m))
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {

    //中央的太阳
    Global.getCombatEngine().addHitParticle(
      point,Misc.ZERO,
      2000f,
      1f,0.1f,0.75f, Color.white)

    //特效分为2段，一部分在SearchLight的readyToEnd里面，为了保证导弹被击毁也会有一部分特效
    //圈
    val ring = aEP_SpreadRing(
      100f, 200f,
      Color(251,250,255,35),
      200f, 1200f, point)
    ring.initColor.setToColor(250f,230f,210f,0f,4f)
    ring.endColor.setColor(250f,250f,250f,0f)
    aEP_CombatEffectPlugin.addEffect(ring)

    aEP_CombatEffectPlugin.addEffect(ExplosionCore(point,3f,400f,300f))

    //节约资源
    if (!Global.getCombatEngine().viewport.isNearViewport(point, 1200f)) return

    spawnSingleCompositeSmoke(
      point,
      800f,
      5f,
      Color(155,150,155,205))

    spawnSingleCompositeSmoke(
      point,
      600f,
      4f,
      Color(235,230,235,255))

  }
}
class SearchLight(val m:MissileAPI) : aEP_BaseCombatEffect(0f,m){

  override fun advanceImpl(amount: Float) {
    val head = aEP_Tool.getExtendedLocationFromPoint(m.location,m.facing, 30f)
    val radius = 200f + MathUtils.getRandomNumberInRange(0f,50f)
    Global.getCombatEngine().addHitParticle(
      head,m.velocity,radius,0.1f,amount*2f,Color(255,0,0))
  }

  override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
    //begin
    aEP_Render.openGL11CombatLayerRendering()

    val head = aEP_Tool.getExtendedLocationFromPoint(m.location,m.facing, 30f)

    val lightLevel = (time/2f).coerceAtLeast(0f).coerceAtMost(1f)
    val extendLevel = ((time)/1f).coerceAtLeast(0f).coerceAtMost(1f)

    val arcMid = 22f
    val smallRad = 100f
    val largeRad = 200f

    //画扇形
    val numOfVertex = 15
    var angleStep = (arcMid*2f)/(numOfVertex-1f) * 0.25f
    angleStep += angleStep * 3f * extendLevel
    var startAngle = m.facing - 23f

    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
    GL11.glBegin(GL11.GL_QUAD_STRIP)
    for (i in 0..numOfVertex ) {
      val a = startAngle + i * angleStep
      val levelToMid = 1f - ((i * angleStep) - arcMid).absoluteValue/arcMid
      val longRad = (smallRad + levelToMid * largeRad) * lightLevel
      val pointFar = aEP_Tool.getExtendedLocationFromPoint(head, a, longRad)
      GL11.glColor4f(0.8f,0.1f,0.1f, 0f)
      GL11.glVertex2f(pointFar.x, pointFar.y)

      val pointNear = head
      GL11.glColor4f(0.3f,0.3f,0.3f, 0.66f * levelToMid)
      GL11.glVertex2f(pointNear.x, pointNear.y)
    }
    GL11.glEnd()

    aEP_Render.closeGL11()
  }

  override fun readyToEnd() {
    val point = entity!!.location

    Global.getCombatEngine().spawnExplosion(
      point,  //loc
      Misc.ZERO,  //velocity
      Color(201,80,75,255),  //color
      1000f,  //range
      3f) //duration

  }
}
open class PredictionStripe(val m:CombatEntityAPI) : aEP_BaseCombatEffect(0f,m){

  //正数为向后，负数向前
  var scrollSpeed = 4f
  var scrolled = 0f

  //线条的头尾的百分之多少开始淡入/淡出
  var fadePercent = 0.2f
  var fadeEndSidePercent = 0.4f

  //线条的前几秒和后几秒整体淡入淡出，淡出只有在 lifeTime > (fadeIn+fadeOut) 时才使用
  var fadeIn = 0.1f
  var fadeOut = 0.1f

  var spriteTexId = Global.getSettings().getSprite("aEP_FX","forward").textureId

  var totalLength = 0f
  val linePoints = LinkedList<Vector2f>()
  val vertexPairs= LinkedList<ArrayList<Any>>()

  var texLength = 12f
  var halfWidth = 6f
  var color = Color(255,25,5,205)
  override fun advanceImpl(amount: Float) {

    linePoints.clear()
    createLineNodes()

    //滚动起始点
    scrolled += amount * scrollSpeed
    while (scrolled > texLength) scrolled -= texLength
    while (scrolled < -texLength) scrolled += texLength

    vertexPairs.clear()
    totalLength = 0f
    var i = 0
    for(i in 0 until linePoints.size){
      var sectionFacing = 0f
      val data = ArrayList<Any>()

      if(i > 0){
        val dist = MathUtils.getDistance(linePoints[i-1],linePoints[i])
        totalLength += dist
      }

      if(i >= linePoints.size-1){
        sectionFacing = VectorUtils.getAngle(linePoints[i-1],linePoints[i])
      }else{
        sectionFacing = VectorUtils.getAngle(linePoints[i],linePoints[i+1])
      }

      //sectionFacing = 90f

      val p = linePoints[i]

      //先左,后右节点
      data.add(getExtendedLocationFromPoint(p, sectionFacing + 90f, halfWidth))
      data.add(getExtendedLocationFromPoint(p, sectionFacing - 90f, halfWidth))
      val texY = totalLength/texLength
      //当前节点的材质坐标
      data.add(texY + scrolled)
      //当前节点到起始点的长度
      data.add(totalLength)

      vertexPairs.add(data)
    }

  }

  override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
    //begin
    if(vertexPairs.size <= 0 )return

    aEP_Render.openGL11CombatLayerRendering()

    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
    GL11.glEnable(GL11.GL_TEXTURE_2D)
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, spriteTexId)

    GL11.glBegin(GL11.GL_QUAD_STRIP)

    val maxLength = vertexPairs[vertexPairs.size-1][3] as Float
    val shouldFadeLength = (maxLength * fadePercent + 1f)
    val shouldFadeEndLength = (maxLength * fadeEndSidePercent + 1f)
    var i = 0
    while (i  < vertexPairs.size ) {
      val leftPoint = vertexPairs[i][0] as Vector2f
      val rightPoint = vertexPairs[i][1] as Vector2f
      val texY = vertexPairs[i][2] as Float
      val currLength = vertexPairs[i][3] as Float

      //计算尾端和头端的淡入淡出
      var fadeLevel = 1f
      if(currLength < shouldFadeLength){
        fadeLevel = currLength/shouldFadeLength
      }
      if((maxLength - currLength) < shouldFadeEndLength){
        fadeLevel = (maxLength - currLength)/shouldFadeEndLength
      }

      //计算因为时间的淡入淡出
      if(lifeTime > fadeIn + fadeOut){
        if(time < fadeIn){
          fadeLevel *= time/fadeIn
        }
        if((lifeTime - time) < fadeOut){
          fadeLevel *= (lifeTime - time)/fadeOut
        }
      }

      val c = aEP_Tool.getColorWithAlphaChange(color, fadeLevel)
      GL11.glTexCoord2f(0f, texY)
      GL11.glColor4ub(c.red.toByte(), c.green.toByte(), c.blue.toByte(), c.alpha.toByte())
      GL11.glVertex2f(leftPoint.x, leftPoint.y)
      //addDebugPoint(leftPoint)
      //addDebugPoint(rightPoint)
      GL11.glTexCoord2f(1f, texY)
      GL11.glColor4ub(c.red.toByte(), c.green.toByte(), c.blue.toByte(), c.alpha.toByte())
      GL11.glVertex2f(rightPoint.x, rightPoint.y)

      i += 1

    }
    GL11.glEnd()

    aEP_Render.closeGL11()
  }

  open fun createLineNodes(){
    //设置直线的头和尾
    linePoints.clear()
    val angleAndSpeed = aEP_Tool.velocity2Speed(m.velocity)
    val timeLevel = ((time-2f)/3f).coerceAtLeast(0f).coerceAtMost(1f)
    val startPoint= m.location
    val endPoint= getExtendedLocationFromPoint(m.location,angleAndSpeed.x, 1200f * timeLevel)
    val xDiff = endPoint.x - startPoint.x
    val yDiff = endPoint.y - startPoint.y
    val step = 0.1f
    var m = 0f
    while( m <= 1f){
      val toAdd = Vector2f(xDiff, yDiff)
      toAdd.scale(m)
      linePoints.add(Vector2f.add(startPoint,toAdd,null))
      m += step
    }
  }
}
class ExplosionCore(val point: Vector2f, lifeTime:Float,val range:Float,val size:Float) : aEP_BaseCombatEffect(lifeTime){
  val minorExplosionTimer = IntervalUtil(0.01f,0.12f)
  override fun advanceImpl(amount: Float) {

    Global.getCombatEngine().addHitParticle(
      point,Misc.ZERO,
      900f,
      1f,0.1f,amount*2f,
      Color(255,200,50))

    var level = (1f - time/lifeTime)
    level *= level
    minorExplosionTimer.advance(amount * level)
    if(minorExplosionTimer.intervalElapsed()){
      val loc = getRandomPointInCircle(point, range)
      val distLevel = 1f-getDistance(loc,point)/(range+1f)
      val size = size + getRandomNumberInRange(0f,size * 0.5f) + size * distLevel * 0.5f
      val time = 0.2f + getRandomNumberInRange(0f,0.6f)
      Global.getCombatEngine().addHitParticle(
        loc,Misc.ZERO,
        size,
        1f,0.1f,time,
        Color(255,150,50))
    }

  }
}

//古斯塔夫 海啸
class aEP_b_l_railwaygun_shot : Effect(){

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

    //val smokeTrail = aEP_SmokeTrail(projectile,20f,1f,20f,20f,Color(240,240,240,120))
    //addEffect(smokeTrail)

    //create side SmokeFire
    createFanSmoke(projectile.spawnLocation, aEP_Tool.angleAdd(weapon.currAngle, 90f), weapon.ship)
    createFanSmoke(projectile.spawnLocation, aEP_Tool.angleAdd(weapon.currAngle, -90f), weapon.ship)


    //apply impulse
    aEP_Tool.applyImpulse(weapon.ship, weapon.currAngle - 180f, 5000f,300f)

    //消除初速度
    // projectile.velocity.set(aEP_Tool.ignoreShipInitialVel(projectile, weapon.ship.velocity))
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {

    //Global.getCombatEngine().addFloatingText(projectile.getLocation(),projectile.getDamageAmount() + "", 20f ,new Color(100,100,100,100),projectile, 0.25f, 120f);
    var hitColor = Color(240, 120, 50, 200)

      if (shieldHit && target is ShipAPI) {
      //击中护盾并造成过载，不生成音效特效，原地刷新一个新弹丸，然后从这里出去
      if(target.fluxTracker.isOverloaded){
        addEffect(SpawnProjDelayed(
          Global.getCombatEngine().elapsedInLastFrame*1f,
          projectile,
          Vector2f(projectile.location),
          aEP_b_l_railwaygun_shot::class.java.simpleName.replace("_shot",""),
          projectile.damage.type,
          projectile.damage.baseDamage))

        return
      }
    }

    spawnVisualEffect(point, hitColor, target)

    //play sound
    Global.getSoundPlayer().playSound(
      "aEP_RW_hit",
      1f, 2f,  // pitch,volume
      projectile.location,  //location
      Vector2f(0f, 0f)) //velocity

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

  fun spawnVisualEffect(point: Vector2f, hitColor:Color, target: CombatEntityAPI){
    val engine = Global.getCombatEngine()

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
    engine.spawnExplosion(
      point,  //color
      Vector2f(0f, 0f),  //vel
      hitColor,  //color
      500f,  //size
      1f
    ) //duration
    engine.addNegativeParticle(
      point,
      Vector2f(0f, 0f),
      300f,
      0.25f,
      1.5f,
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
      aEP_Tool.getColorWithAlpha(sparkColor, 1f - 0.75f * percent))
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

//空间主炮 台风
class aEP_cap_sta_main : EveryFrame(), WeaponRangeModifier{
  companion object{
    const val SIDE_EXPLODE_SOUND_ID = "aEP_m_l_blasthammer_explode"
    const val FIGHTER_RANDOM_RANGE = 200f

    fun createSideSmoke(loc: Vector2f, facing: Float, ship: ShipAPI) {

      val param = aEP_Tool.FiringSmokeParam()
      param.smokeSize = 20f
      param.smokeEndSizeMult = 2f
      param.smokeSpread = 10f
      param.maxSpreadRange = 50f

      param.smokeInitSpeed = 100f
      param.smokeStopSpeed = 0.95f

      param.smokeTime = 2f
      param.smokeNum = 30
      param.smokeAlpha = 0.1f
      firingSmoke(loc,facing,param, ship)


      //炮口焰
      Global.getCombatEngine().spawnExplosion(
        loc,
        speed2Velocity(facing, 60f),
        Color(240,110,20), 100f, 0.3f)

      //大范围低亮度闪光，炮口部分的高亮用muzzle flash实现
      Global.getCombatEngine().addHitParticle(
        loc,
        Misc.ZERO,
        400f,0.35f,
        0.2f, 0.5f,
        Color(255,240,125))

      //大范围低亮度闪光，炮口部分的高亮用muzzle flash实现
      Global.getCombatEngine().addHitParticle(
        loc,
        Misc.ZERO,
        300f,0.25f,
        0.1f, 0.6f,
        Color(255,180,60))

      val numParticles  = 20
      val minSize = 10f
      val maxSize = 50f
      val pc = Color(240,110,20,150)
      val minDur = 0.2f
      val maxDur = 1f
      val arc = 10f
      val scatter = 50f
      val minVel = 10f
      val maxVel = 50f
      val endSizeMin = 1f
      val endSizeMax = 2f
      val spawnPoint = Vector2f(loc)
      for (i in 0 until numParticles) {
        var angleOffset = Math.random().toFloat()
        if (angleOffset > 0.2f) {
          angleOffset *= angleOffset
        }
        var speedMult = 1f - angleOffset
        speedMult = 0.5f + speedMult * 0.5f
        angleOffset *= sign((Math.random().toFloat() - 0.5f))
        angleOffset *= arc / 2f
        val theta = Math.toRadians((facing + angleOffset).toDouble()).toFloat()
        val r = (Math.random() * Math.random() * scatter).toFloat()
        val x = cos(theta.toDouble()).toFloat() * r
        val y = sin(theta.toDouble()).toFloat() * r
        val pLoc = Vector2f(spawnPoint.x + x, spawnPoint.y + y)
        var speed = minVel + (maxVel - minVel) * Math.random().toFloat()
        speed *= speedMult
        val pVel = Misc.getUnitVectorAtDegreeAngle(Math.toDegrees(theta.toDouble()).toFloat())
        pVel.scale(speed)
        Vector2f.add(ship.velocity?:Misc.ZERO, pVel, pVel)
        val pSize = minSize + (maxSize - minSize) * Math.random().toFloat()
        val pDur = minDur + (maxDur - minDur) * Math.random().toFloat()
        val endSize = endSizeMin + (endSizeMax - endSizeMin) * Math.random().toFloat()
        Global.getCombatEngine().addNebulaParticle(pLoc, pVel, pSize, endSize, 0.1f, 0.5f, pDur, pc)
      }

    }
  }

  //---------------------------------//
  //这些变量都不作为listener使用，listener部分不存在变量
  val side1RelAngle = 30f
  val side2RelAngle = 20f
  val side3RelAngle = 16f

  val side1RelRange = 60f
  val side2RelRange = 90f
  val side3RelRange = 120f

  var didSide1 = false
  var didSide2 = false
  var didSide3 = false

  var aimLine : WarningLine? = null

  var randomList = WeightedRandomPicker<Int>()

  val droneTimer = IntervalUtil(0.1f,0.1f)

  //------------------------------------//
  //listener和everyframe共享的变量
  //测距仪的部分，调整lidardish的射程，在weaponEveryFrame的advance中设置
  var mainRange = 1000f

  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    val level = weapon.chargeLevel
    mainRange = weapon.range

    //把自己当成weaponListener加入ship
    if(!weapon.ship.hasListenerOfClass(this::class.java)){
      weapon.ship.addListener(this)
    }

    //定期把已经失效的瞄准线移除
    if(aimLine != null && aimLine?.shouldEnd == true){
      aimLine = null
    }

    //充能时开始生成飞机，绘制预瞄线，控制lidar
    if(level > 0.1f){
      //绘制预瞄线
      if(aimLine == null) {
        aimLine = WarningLine(weapon)
        addEffect(aimLine as WarningLine)
      }

      //控制lidar
      for(w in weapon.ship.allWeapons){
        if(w.spec.weaponId.equals("lidardish")){
          WeaponUtils.aimTowardsPoint(
            w,
            getExtendedLocationFromPoint(weapon.location,weapon.currAngle,weapon.range),
            amount)

          if(level > 0.5f && weapon.cooldownRemaining <= 0f){
            //充能到0.5怎么都该转到了
            //并且此时刚好射完一个轮次
            w.setForceFireOneFrame(true)
          }
        }
      }

      droneTimer.advance(amount)
      if(droneTimer.intervalElapsed() && !randomList.items.isEmpty()){
        //电磁轨道飞机成对的刷新
        val num = randomList.pickAndRemove()
        spawnFighter(num, weapon, weapon.ship)
      }

    }

    if(level > 0.79f && !didSide1) {
      didSide1 = true
      val angle = side1RelAngle
      val dist =  side1RelRange
      val loc1 = getExtendedLocationFromPoint(weapon.location, weapon.currAngle+angle, dist)
      createSideSmoke(loc1, weapon.currAngle + 120f, weapon.ship)
      val loc2 = getExtendedLocationFromPoint(weapon.location, weapon.currAngle-angle, dist)
      createSideSmoke(loc2, weapon.currAngle - 120f, weapon.ship)
      //声音
      Global.getSoundPlayer().playSound(SIDE_EXPLODE_SOUND_ID, 1f, 0.5f, weapon.location, Misc.ZERO)

    }
    if(level > 0.86f && !didSide2){
      didSide2 = true
      val angle = side2RelAngle
      val dist =  side2RelRange
      val loc1 = getExtendedLocationFromPoint(weapon.location, weapon.currAngle+angle, dist)
      createSideSmoke(loc1, weapon.currAngle + 120f, weapon.ship)
      val loc2 = getExtendedLocationFromPoint(weapon.location, weapon.currAngle-angle, dist)
      createSideSmoke(loc2, weapon.currAngle - 120f, weapon.ship)
      //声音
      Global.getSoundPlayer().playSound(SIDE_EXPLODE_SOUND_ID, 1f, 0.5f, weapon.location, Misc.ZERO)

    }
    if(level > 0.93f && !didSide3){
      didSide3 = true
      val angle = side3RelAngle
      val dist =  side3RelRange
      val loc1 = getExtendedLocationFromPoint(weapon.location, weapon.currAngle+angle, dist)
      createSideSmoke(loc1, weapon.currAngle + 120f, weapon.ship)
      val loc2 = getExtendedLocationFromPoint(weapon.location, weapon.currAngle-angle, dist)
      createSideSmoke(loc2, weapon.currAngle - 120f, weapon.ship)
      //声音
      Global.getSoundPlayer().playSound(SIDE_EXPLODE_SOUND_ID, 1f, 0.5f, weapon.location, Misc.ZERO)

    }

    //当武器冷却时，重置所有变量
    if(level <= 0f){
      didSide1 = false
      didSide2 = false
      didSide3 = false
      if(aimLine != null){
        (aimLine as WarningLine).shouldEnd = true
      }
      aimLine = null
      droneTimer.elapsed = 0f
      if(randomList.items.size != 12){
        randomList.clear()
        //num==135时，一次刷一对
        randomList.add(1)
        randomList.add(3)
        randomList.add(5)

        randomList.add(7)
        randomList.add(8)
        randomList.add(9)
        randomList.add(10)
        randomList.add(11)
        randomList.add(12)
      }
    }
  }

  fun spawnFighter(num:Int, weapon: WeaponAPI, source: ShipAPI){
    var variant_base_id = "aEP_ut_sta_main"
    if(num <= 6){
      variant_base_id += "_rail"
      variant_base_id += "_l"
    }else{
      variant_base_id += "_side"
      if(num % 2 == 1){
        variant_base_id += "_l"
      }else{
        variant_base_id += "_r"
      }
    }
    variant_base_id += "_Standard"

    var relAngle = 16f
    var relDist = 120f
    var ftrRelFacing = 0f
    when(num){
      1 -> {relAngle = 7.5f; relDist = 145.25f}
      2 -> {relAngle = -7.5f; relDist = 143.28f}
      3 -> {relAngle = 6.1f; relDist = 180.01f}
      4 -> {relAngle = -6.1f; relDist =  180.01f}
      5 -> {relAngle = 5.1f; relDist = 214.84f}
      6 -> {relAngle = -5.1f; relDist = 214.84f}

      7 -> {relAngle = side3RelAngle; relDist = side3RelRange; ftrRelFacing = -60f}
      8 -> {relAngle = -side3RelAngle; relDist = side3RelRange; ftrRelFacing = 60f}
      9 -> {relAngle = side2RelAngle; relDist = side2RelRange; ftrRelFacing = -60f}
      10 -> {relAngle = -side2RelAngle; relDist = side2RelRange; ftrRelFacing = 60f}
      11 -> {relAngle = side1RelAngle; relDist = side1RelRange; ftrRelFacing = -60f}
      12 -> {relAngle = -side1RelAngle; relDist = side1RelRange; ftrRelFacing = 60f}
    }

    //aEP_Tool.addDebugPoint(absPoint)

    //用fxDrone是没有自带ai的
    val variant = Global.getSettings().getVariant(variant_base_id)
    val drone = Global.getCombatEngine().createFXDrone(variant)
    //并不是特效无人机，而是实体，去掉这个tag。这个tag会被createFXDrone()自动添加
    drone.tags.remove(Tags.VARIANT_FX_DRONE)

    drone.isDrone = true
    drone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f,source)
    drone.layer = CombatEngineLayers.FIGHTERS_LAYER
    drone.owner = source.owner
    drone.location.set(Vector2f(0f, Global.getCombatEngine().mapHeight + 1000f))
    if(drone.owner == 100){
      drone.location.set(Vector2f(0f, -Global.getCombatEngine().mapHeight - 1000f))
    }

    drone.facing = MathUtils.getRandomNumberInRange(0f,360f)
    Global.getCombatEngine().addEntity(drone)
    //drone.shipAI = aEP_DroneShenduShipAI(drone.fleetMember, drone)


    addEffect(aEP_Combat.StandardTeleport(
      0.45f,
      drone,
      getRandomPointInCircle(weapon.location, FIGHTER_RANDOM_RANGE),
      getRandomNumberInRange(0f,360f)))
    addEffect(FighterToLocation(
      drone,ftrRelFacing,
      Vector2f(relAngle, relDist),
      weapon))

    //如果是轨道，同时刷一对
    if(num == 1 || num == 3 || num == 5){
      //用fxDrone是没有自带ai的
      val variant2 = Global.getSettings().getVariant(variant_base_id.replace("_l_","_r_"))
      val drone2 = Global.getCombatEngine().createFXDrone(variant2)
      //并不是特效无人机，而是实体，去掉这个tag。这个tag会被createFXDrone()自动添加
      drone2.tags.remove(Tags.VARIANT_FX_DRONE)

      drone2.isDrone = true
      drone2.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f,source)
      drone2.layer = CombatEngineLayers.FIGHTERS_LAYER
      drone2.owner = source.owner
      drone2.location.set(Vector2f(0f, Global.getCombatEngine().mapHeight + 1000f))
      if(drone2.owner == 100){
        drone2.location.set(Vector2f(0f, -Global.getCombatEngine().mapHeight - 1000f))
      }

      drone2.facing = getRandomNumberInRange(0f,360f)
      Global.getCombatEngine().addEntity(drone2)
      //drone.shipAI = aEP_DroneShenduShipAI(drone.fleetMember, drone)

      addEffect(aEP_Combat.StandardTeleport(
        0.45f,
        drone2,
        getRandomPointInCircle(weapon.location, 300f),
        getRandomNumberInRange(0f,360f)))
      addEffect(FighterToLocation(
        drone2,ftrRelFacing,
        Vector2f(-relAngle, relDist),
        weapon))

      addEffect(ArcBetween(drone, drone2))
    }

  }

  inner class FighterToLocation(val fighter:ShipAPI, val fighterRelFacing:Float, val relLocData:Vector2f, val weapon:WeaponAPI): aEP_BaseCombatEffect(15f,fighter){
    override fun advanceImpl(amount: Float) {
      if((weapon.chargeLevel <= 0f || weapon.cooldownRemaining > 0f) && lifeTime-time >1.1f){
        lifeTime = (time + getRandomNumberInRange(0.35f,1f))
      }

      val absLoc = aEP_Tool.getAbsPos(relLocData, weapon.location, weapon.currAngle)
      aEP_Tool.setToPositionHighAccuracy(fighter, absLoc)
      aEP_Tool.moveToAngle(fighter, fighterRelFacing + weapon.currAngle)
    }

    override fun readyToEnd() {

      // Ensure a kill
      fighter.mutableStats.hullDamageTakenMult.unmodify()
      fighter.mutableStats.armorDamageTakenMult.unmodify()
      fighter.hitpoints = 1f
      fighter.explosionScale = 0.25f
      Global.getCombatEngine().applyDamage(
        fighter, fighter.location, fighter.armorGrid.armorRating*10f,
        DamageType.OTHER, 0f,
        true, false, null)
      fighter.velocity.set(MathUtils.getRandomPointInCircle(VECTOR2F_ZERO,60f))
      fighter.collisionClass = CollisionClass.NONE
    }
  }
  inner class ArcBetween(val fighter1:ShipAPI, val fighter2:ShipAPI?): aEP_BaseCombatEffect(6f,fighter1){

    val ARC_CORE_COLOR = Color(155,200,255,150)
    val ARC_FRINGE_COLOR = Color(75,150,255,255)

    val ARC_KEY = "aEP_cap_sta_main_drone_emp"
    val ARC_DIST = 50f
    val arcTimer = IntervalUtil(0.1f,0.4f)

    var arcTime = 0f
    var maxArcTime = 4f
    init {
      fighter1.setCustomData(ARC_KEY,1f)
    }

    override fun advance(amount: Float) {
      if(fighter2 != null && isDead(fighter2)){
        shouldEnd = true
      }
      super.advance(amount)
    }

    override fun advanceImpl(amount: Float) {
      //如果fighter2不存在，就搜索最近的对侧轨道建立联系
      //建立联系后打上KEY
      if(fighter2 != null){
        if(getDistanceSquared(fighter1.location, fighter2.location) < ARC_DIST.pow(2)){
          arcTime = MathUtils.clamp(arcTime + amount, 0f, maxArcTime)
          val level = (arcTime/(maxArcTime - 2f)).coerceAtLeast(0.2f)
          arcTimer.advance(amount * level)
          if(arcTimer.intervalElapsed()){
            Global.getCombatEngine().spawnEmpArcVisual(
              fighter1.location,
              fighter1,
              fighter2.location,
              fighter2,
              5f,
              ARC_FRINGE_COLOR,
              ARC_CORE_COLOR)
          }
        }
      }

    }


  }

  inner class WarningLine(val weapon: WeaponAPI) : PredictionStripe(weapon.ship){

    var spriteId1 = Global.getSettings().getSprite("aEP_FX","forward").textureId
    var spriteId2 = Global.getSettings().getSprite("aEP_FX","hold").textureId

    val startPoint = weapon.getFirePoint(0)
    val endPoint = aEP_Tool.getExtendedLocationFromPoint(startPoint,weapon.currAngle,10f)

    var lengthLevel = 0f
    var glowLevel = 0.2f
    init {
      scrollSpeed = -5f
      halfWidth = 20f
      texLength = 40f
    }
    override fun advanceImpl(amount: Float) {

      if(weapon.chargeLevel <= 0f || glowLevel <= 0.05f){
        shouldEnd = true
      }

      if(weapon.chargeLevel > 0.1f && weapon.cooldownRemaining <= 0f){
        lengthLevel = clamp(lengthLevel+amount/2f,0f,1f)
        glowLevel = clamp(glowLevel+amount/4f,0.2f,1f)
      }else{
        glowLevel = clamp(glowLevel - amount * 4f,0f,1f)
      }


      color = aEP_Tool.getColorWithAlpha(color,glowLevel)

      super.advanceImpl(amount)

    }

    override fun createLineNodes(){
      //设置直线的头和尾
      linePoints.clear()

      startPoint.set(weapon.getFirePoint(0))
      endPoint.set(getExtendedLocationFromPoint(startPoint, weapon.currAngle,(weapon.range + 100f) * lengthLevel))

      val xDiff = endPoint.x - startPoint.x
      val yDiff = endPoint.y - startPoint.y
      val step = 0.1f
      var m = 0f
      while( m <= 1f){
        val toAdd = Vector2f(xDiff, yDiff)
        toAdd.scale(m)
        linePoints.add(Vector2f.add(startPoint,toAdd,null))
        m += step
      }
    }
  }

  //------------------------------------//
  //测距仪的部分，调整lidardish的射程
  override fun getWeaponRangePercentMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    return 0f
  }

  override fun getWeaponRangeMultMod(ship: ShipAPI?, weapon: WeaponAPI): Float {
    return 1f
  }

  override fun getWeaponRangeFlatMod(ship: ShipAPI?, w: WeaponAPI): Float {
    ship?:return 0f
    //只单独修改lidar武器的距离
    if(!w.spec.hasTag(Tags.LIDAR)) return 0f
    return mainRange
  }
}
class aEP_cap_sta_main_shot : Effect(){
  companion object{

  }
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val color = Color(240, 240, 240, 240)
    projectile?:return
    weapon?: return

    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 80f
    param.smokeEndSizeMult = 2f
    param.smokeSpread = 30f
    param.maxSpreadRange = 200f

    param.smokeInitSpeed = 400f
    param.smokeStopSpeed = 0.95f

    param.smokeTime = 2.25f
    param.smokeNum = 50
    param.smokeAlpha = 0.1f
    firingSmoke(weapon.getFirePoint(0),weapon.currAngle,param, weapon.ship)

    //炮口焰
    Global.getCombatEngine().spawnExplosion(
      projectile.location,
      speed2Velocity(weapon.currAngle, 60f),
      Color(240,110,20), 600f, 0.35f)

    //大范围低亮度闪光，炮口部分的高亮用muzzle flash实现
    Global.getCombatEngine().addHitParticle(
      projectile.location,
      Misc.ZERO,
      4000f,0.35f,
      0.2f, 0.6f,
      Color(255,240,125))

    //大范围低亮度闪光，炮口部分的高亮用muzzle flash实现
    Global.getCombatEngine().addHitParticle(
      projectile.location,
      Misc.ZERO,
      6000f,0.25f,
      0.1f, 1f,
      Color(255,180,60))

    val numParticles  = 40
    val minSize = 100f
    val maxSize = 200f
    val pc = Color(240,110,20,150)
    val minDur = 0.2f
    val maxDur = 1f
    val arc = 10f
    val scatter = 50f
    val minVel = 50f
    val maxVel = 300f
    val endSizeMin = 1f
    val endSizeMax = 2f
    val spawnPoint = Vector2f(projectile.location)
    for (i in 0 until numParticles) {
      var angleOffset = Math.random().toFloat()
      if (angleOffset > 0.2f) {
        angleOffset *= angleOffset
      }
      var speedMult = 1f - angleOffset
      speedMult = 0.5f + speedMult * 0.5f
      angleOffset *= sign((Math.random().toFloat() - 0.5f))
      angleOffset *= arc / 2f
      val theta = Math.toRadians((projectile.facing + angleOffset).toDouble()).toFloat()
      val r = (Math.random() * Math.random() * scatter).toFloat()
      val x = cos(theta.toDouble()).toFloat() * r
      val y = sin(theta.toDouble()).toFloat() * r
      val pLoc = Vector2f(spawnPoint.x + x, spawnPoint.y + y)
      var speed = minVel + (maxVel - minVel) * Math.random().toFloat()
      speed *= speedMult
      val pVel = Misc.getUnitVectorAtDegreeAngle(Math.toDegrees(theta.toDouble()).toFloat())
      pVel.scale(speed)
      Vector2f.add(weapon?.ship?.velocity?:Misc.ZERO, pVel, pVel)
      val pSize = minSize + (maxSize - minSize) * Math.random().toFloat()
      val pDur = minDur + (maxDur - minDur) * Math.random().toFloat()
      val endSize = endSizeMin + (endSizeMax - endSizeMin) * Math.random().toFloat()
      Global.getCombatEngine().addNebulaParticle(pLoc, pVel, pSize, endSize, 0.1f, 0.5f, pDur, pc)
    }

    //消除初速度
    projectile.velocity.set(aEP_Tool.ignoreShipInitialVel(projectile, weapon.ship.velocity))

    //增加亡语
    addEffect(OnDeathEffect(projectile))

  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {

    //explode1(point, VectorUtils.getAngle(point, target.location) - 180f, projectile)
  }

  //这段是击中了才会产生
  fun explode1(point:Vector2f){
    val engine = Global.getCombatEngine()

    //创造拖烟火花
    var num = 1
    while (num <= 40) {
      var onHitAngle = 0f
      onHitAngle += getRandomNumberInRange(-180, 180)
      val speed = getRandomNumberInRange(25f, 225f)
      val lifeTime = getRandomNumberInRange(2f, 7f)
      addEffect(Spark(point, speed2Velocity(onHitAngle, speed), speed2Velocity(onHitAngle - 180f, speed / lifeTime), lifeTime))
      num++
    }

    //随机烟环
    num = 1
    while (num <= 32) {
      val loc = MathUtils.getRandomPointInCircle(point, 400f)
      val sizeGrowth = getRandomNumberInRange(0f, 100f)
      val sizeAtMin = getRandomNumberInRange(150f, 600f)
      val moveSpeed = getRandomNumberInRange(50f, 150f)
      val ms = aEP_MovingSmoke(loc)
      ms.setInitVel(aEP_Tool.speed2Velocity(VectorUtils.getAngle(point, loc), moveSpeed))
      ms.lifeTime = 4f
      ms.fadeIn = 0f
      ms.fadeOut = 1f
      ms.size = sizeAtMin
      ms.sizeChangeSpeed = sizeGrowth
      ms.color = Color(100, 100, 100, MathUtils.getRandomNumberInRange(80, 180))
      addEffect(ms)
      num++
    }

    //缓慢圈
    val ring = aEP_SpreadRing(
      200f, 200f,
      Color(251,100,25,25),
      300f, 4000f, point)
    ring.initColor.setToColor(250f,230f,210f,0f,4f)
    ring.endColor.setColor(250f,250f,250f,0f)
    addEffect(ring)

    //高速圈
    val ring2 = aEP_SpreadRing(
      4000f, 300f,
      Color(251,200,155,25),
      300f, 4000f, point)
    ring2.initColor.setToColor(250f,230f,210f,0f,0.5f)
    ring2.endColor.setColor(250f,250f,250f,0f)
    addEffect(ring2)

    addEffect(ExplosionCore(point,4f,600f,300f))


  }

  //只要弹丸消失就会触发
  fun explode2(point:Vector2f){
    val engine = Global.getCombatEngine()
    explode1(point)

    //create explode
    //创造粒子蔟
    val particleColor = Color(240, 180, 90)
    val particleCoreColor = Color(245, 120, 50)

    engine.spawnExplosion(point,Misc.ZERO, particleCoreColor, 600f,6f)
    aEP_Tool.addExplosionParticleCloud(point,1000f,300f,10f,30f,200,5f,particleColor)

    //半个闪光弹
    for ( i in 0..3){
      val light = Color(255,255,175)
      Global.getCombatEngine().addHitParticle(
        point,Misc.ZERO,
        1600f,
        0.5f,
        0.1f,2f,light)
    }

    //淡淡的环境光
    val light = Color(255,125,75)
    aEP_Tool.addSmoothParticle(
      point,Misc.ZERO,
      6000f,
      0.15f,0.1f,5f,light)


    spawnCompositeSmoke(
      point,
      800f,
      10f,
      Color(175,175,165))

    //play sound
    Global.getSoundPlayer().playSound(
      "aEP_cap_sta_main_explode",
      1f, 2f,  // pitch,volume
      point,  //location
      Vector2f(0f, 0f)
    ) //velocity


    if(engine.viewport.isNearViewport(point,0f)){
      addEffect( ScreenDark(4f))
    }

  }

  inner class OnDeathEffect(val proj:DamagingProjectileAPI):aEP_BaseCombatEffect(0f,proj){
    override fun readyToEnd() {
      explode2(proj.location)
    }
  }
}
class ScreenDark(lifeTime: Float):aEP_BaseCombatEffect(lifeTime){
  init {
    layers.clear()
    layers.add(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
  }

  override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
    aEP_Render.openGL11CombatLayerRendering()
    val viewport = Global.getCombatEngine().viewport

    val width = viewport.visibleWidth
    val height = viewport.visibleHeight

    val ll = Vector2f(viewport.llx,viewport.lly)
    val lr = Vector2f(ll.x + width, ll.y)
    val ul = Vector2f(ll.x ,ll.y + height)
    val ur = Vector2f(ll.x + width ,ll.y + height)
    //开始画实际渲染的圆环
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    //GL11.glDisable(GL11.GL_TEXTURE_2D)
    GL11.glBegin(GL11.GL_QUAD_STRIP)

    val al = ((1f - time/lifeTime) * 205f).toInt()

    GL11.glColor4ub(Color.black.red.toByte(), Color.black.green.toByte(), Color.black.blue.toByte(), al.toByte())

    GL11.glVertex2f(ll.x, ll.y)
    GL11.glVertex2f(lr.x, lr.y)
    GL11.glVertex2f(ul.x, ul.y)
    GL11.glVertex2f(ur.x, ur.y)

    GL11.glEnd()
    aEP_Render.closeGL11()
  }
}

//d100t MBC
class aEP_b_l_d100t_shot : Effect(), DamageDealtModifier{
  companion object{
    var DAMAGE_REDUCTION_HULL_HIT = 0.5f
    var PURE_ARMOR_DAMAGE = 90f
    private var TRIGGER_CHANCE = 100f
    private var PERCENT_PER_TRIGGER = 2f
    private var AMOUNT_PER_TRIGGER = 200f

    init {
      val hlString = Global.getSettings().getWeaponSpec(aEP_b_l_d100t_shot::class.java.simpleName.replace("_shot","")).customPrimaryHL
      var i = 0
      for(num in hlString.split("|")){
        if(i == 0) {
          TRIGGER_CHANCE = num.replace("%","").toFloat()
        }
        if(i == 1) {
          PERCENT_PER_TRIGGER = num.replace("%","").toFloat()
        }
        if(i == 2) {
          PURE_ARMOR_DAMAGE = num.toFloat()
        }
        if(i == 3) {
          DAMAGE_REDUCTION_HULL_HIT = num.replace("%","").toFloat().div(100f)
        }
        if(i == 4) {
          PURE_ARMOR_DAMAGE = num.toFloat()
        }
        i += 1
      }
    }
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {

    if (weapon.ship?.hasListenerOfClass(this::class.java) == false) {
      weapon.ship.addListener(this)
    }

    //节约资源
    if (!Global.getCombatEngine().viewport.isNearViewport(projectile.location, 800f)) return

    //加入弹托分离
    addEffect(DiscardSabot(0.35f, projectile))

    engine.addHitParticle(
      projectile.location, Misc.ZERO,
      300f, 1f, 0.1f, 0.25f, Color(255,128,41))

    //create side SmokeFire
    createFanSmoke(projectile.spawnLocation, angleAdd(weapon.currAngle, 90f), weapon.ship)
    createFanSmoke(projectile.spawnLocation, angleAdd(weapon.currAngle, -90f), weapon.ship)

    //炮口焰
    Global.getCombatEngine().spawnExplosion(
      projectile.location,
      speed2Velocity(weapon.currAngle, 20f),
      Color(240,215,20), 100f, 0.3f)

    //apply impulse
    aEP_Tool.applyImpulse(weapon.ship, weapon.currAngle - 180f, 1000f,300f)

    //消除初速度
    // aEP_Tool.ignoreShipInitialVel(projectile, weapon.ship?.velocity?:Misc.ZERO )

  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {

    //声音
    if(shieldHit){
      Global.getSoundPlayer().playSound("aEP_m_l_harpoon_hit_shield", 0.25f, 2f, point, Misc.ZERO)
      if(target is ShipAPI){
        val rand = getRandomNumberInRange(0f, 100f)
        if(rand <= TRIGGER_CHANCE){
          aEP_b_l_aa40_shot.applyPureShipDamage(target, point, AMOUNT_PER_TRIGGER, PERCENT_PER_TRIGGER)
        }
      }
    }else{
      Global.getSoundPlayer().playSound("aEP_m_l_harpoon_hit_armor", 0.5f, 2f, point, Misc.ZERO)
    }
  }

  fun createFanSmoke(loc: Vector2f, facing: Float, ship: ShipAPI) {

    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 20f
    param.smokeEndSizeMult = 2f
    param.smokeSpread = 30f
    param.maxSpreadRange = 50f

    param.smokeInitSpeed = 100f
    param.smokeStopSpeed = 0.95f

    param.smokeTime = 1.5f
    param.smokeNum = 20
    param.smokeAlpha = 0.15f
    firingSmoke(loc, facing, param, ship)


  }

  override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {

    if(!shieldHit){
      if(param is DamagingProjectileAPI && param.projectileSpecId?.equals(this.javaClass.simpleName) == true) {
        damage.damage = damage.baseDamage * (1f - DAMAGE_REDUCTION_HULL_HIT)
        return this.javaClass.simpleName
      }
    }

    return null
  }
}
class DiscardSabot(lifeTime: Float, entity: CombatEntityAPI) : aEP_BaseCombatEffect(lifeTime, entity){
  val l = Global.getSettings().getSprite("aEP_FX","d100t_sabot_l")
  val r = Global.getSettings().getSprite("aEP_FX","d100t_sabot_r")
  val center = Vector2f(entity.location)

  init {
    layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
  }
  override fun advanceImpl(amount: Float) {
    val entity = entity as DamagingProjectileAPI
    val p = entity.location
    l.angle = entity.facing - 90f
    r.angle = entity.facing - 90f
    center.set(aEP_Tool.getExtendedLocationFromPoint(p, entity.facing, 40f))
  }

  override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {

    l.renderAtCenter(center.x, center.y)
    r.renderAtCenter(center.x,center.y)
  }

  override fun readyToEnd() {
    val projectile = entity as DamagingProjectileAPI
    //弹托左
    MagicRender.battlespace(l,
      center,
      MathUtils.getRandomPointInCone(VECTOR2F_ZERO,200f, projectile.facing+10f, projectile.facing+30f),
      Vector2f(l.width,l.height),
      VECTOR2F_ZERO,
      //magicRender的角度开始点比游戏多90
      projectile.facing - 90f,MathUtils.getRandomNumberInRange(0f,90f),
      Color.white,
      false, 0f, MathUtils.getRandomNumberInRange(2f,4f), 0.5f)

    //弹托右
    MagicRender.battlespace(r,
      center,
      MathUtils.getRandomPointInCone(VECTOR2F_ZERO,200f, projectile.facing-30f, projectile.facing-10f),
      Vector2f(l.width,l.height),
      VECTOR2F_ZERO,
      //magicRender的角度开始点比游戏多90
      projectile.facing - 90f,MathUtils.getRandomNumberInRange(-90f,0f),
      Color.white,
      false, 0f, MathUtils.getRandomNumberInRange(2f,4f), 0.5f)
  }
}

//DG-3巨型长管链炮
class aEP_b_l_dg3_shot : Effect(){
  companion object{
    val EXPLOSION_COLOR = Color(250,25,25,254)
    private var DAMAGE_PER_TRIGGER = 40f
  }

  init {
    val fullString = StringBuffer()
    //把customHL一位一位读char，计算 “|”的情况
    for(hlString in Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL){
      fullString.append(hlString)
    }
    DAMAGE_PER_TRIGGER = fullString.toString().toFloat()
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    if(shieldHit) return
    if(target is ShipAPI){
      val damage = computeDamageToShip(projectile?.source,target, null, DAMAGE_PER_TRIGGER, null,false)
      val armorLevel = DefenseUtils.getArmorLevel(target,point)
      if(armorLevel <= 0.05f){
        //不能通过此种方式将hp减到负数或者0
        if(target.hitpoints > damage + 1f) target.hitpoints -= damage
        //跳红字代码伤害
        engine.addFloatingDamageText(point,damage,EXPLOSION_COLOR,target,projectile?.source)

        //百分之25的概率触发一次爆炸特效，这样特效能做夸张一点
        if(MathUtils.getRandomNumberInRange(0f,1f) > 0.25f) return
        //红色爆炸特效
        engine.addSmoothParticle(point,aEP_ID.VECTOR2F_ZERO,160f,1f,0.5f,0.2f,Color.red)
        var randomVel = MathUtils.getRandomPointInCone(point,75f, angleAdd(projectile?.facing?:0f,-200f),angleAdd(projectile?.facing?:0f,160f))
        randomVel = Vector2f(randomVel.x- (point?.x?:0f),randomVel.y-(point?.y?:0f))
        engine.spawnExplosion(point, Vector2f(target.velocity.x + randomVel.x,target.velocity.y+ randomVel.y) , EXPLOSION_COLOR,120f,0.8f)
      }
    }
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    //create shell
    var side = 1f
    if (MathUtils.getShortestRotation(weapon.currAngle, VectorUtils.getAngle(weapon.location, projectile.spawnLocation)) > 0) side = -1f

    //闪光，
    Global.getCombatEngine().addHitParticle(
      projectile.location,
      speed2Velocity(weapon.currAngle, 60f),
      MathUtils.getRandomNumberInRange(25f,125f),0.75f ,0.05f, 0.1f,
      Color(255,125,60))

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

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
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
      val target = entity as CombatEntityAPI
      val speedMult = 1 - MathUtils.clamp((SPEED_REDUCE_BY_MASS * SPEED_REDUCE_BY_MASS )/ (target.mass + SPEED_REDUCE_BY_MASS), MIN_SPEED_REDUCE, MAX_SPEED_REDUCE)
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
    val SMOKE_RING_COLOR =  Color(240,240,240,244)
    val SHELL_GLOW =   Color(255, 218, 188, 35)

    val TRIGGER_COLOR = Color(155, 168, 188, 255)
    private var TRIGGER_CHANCE = 100f
    private var PERCENT_PER_TRIGGER = 2f
    private var AMOUNT_PER_TRIGGER = 200f
  }
  init {
    //把customHL一位一位读char，计算 “|”的情况
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0){
        TRIGGER_CHANCE =  num.replace("%","").toFloat()
      }
      if(i == 1){
        PERCENT_PER_TRIGGER =  num.replace("%","").toFloat()
      }
      if(i == 2){
        AMOUNT_PER_TRIGGER =  num.toFloat()
      }
      i += 1
    }

  }
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    weapon?:return
    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 20f
    param.smokeEndSizeMult = 1.5f
    param.smokeSpread = 20f
    param.maxSpreadRange = 40f

    param.smokeInitSpeed = 100f
    param.smokeStopSpeed = 0.9f

    param.smokeTime = 1.5f
    param.smokeNum = 4
    param.smokeAlpha = 0.35f

    firingSmoke(projectile?.location?: Vector2f(0f,0f),weapon.currAngle,param, weapon.ship)

    val sizeMult = 0.5f

    //炮口焰
    Global.getCombatEngine().spawnExplosion(
      projectile.location,
      speed2Velocity(weapon.currAngle, 60f),
      Color(240,110,20), 60f * sizeMult, 0.35f)

//    //闪光，使用muzzle flash实现
//    Global.getCombatEngine().addHitParticle(
//      projectile.location,
//      speed2Velocity(weapon.currAngle, 60f),
//      300f * sizeMult,0.35f ,0.33f, 0.12f,
//      Color(255,240,160))


    //创造蛋壳
    var onLeft = false
    if(MathUtils.getShortestRotation(VectorUtils.getAngle(weapon.location,projectile.location),weapon.currAngle) > 0){
      onLeft = true
    }
    if(onLeft){
      val ejectPoint = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle-135f,16f)
      val ms = aEP_MovingSprite(ejectPoint, Vector2f(6f,3f),weapon.currAngle,"graphics/weapons/aEP_b_l_aa40/shred.png")
      ms.lifeTime = 2f
      ms.fadeIn = 0.05f
      ms.fadeOut = 0.2f
      ms.color = Color(255,255,255,255)
      ms.angle = weapon.currAngle
      ms.angleSpeed = MathUtils.getRandomNumberInRange(-180f,180f)
      ms.setInitVel(
        speed2Velocity(weapon.currAngle-90f +getRandomNumberInRange(-10f,10f),
          MathUtils.getRandomNumberInRange(40f,80f)))
      ms.setInitVel(weapon.ship.velocity)
      ms.stopSpeed = 0.9f
      addEffect(ms)
      addEffect(aEP_b_m_h88_shot.ShellGlow(ms, SHELL_GLOW))
    }else{
      val ejectPoint = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle+135f,16f)
      val ms = aEP_MovingSprite(ejectPoint, Vector2f(6f,3f),weapon.currAngle,"graphics/weapons/aEP_b_l_aa40/shred.png")
      ms.lifeTime = 2f
      ms.fadeIn = 0.05f
      ms.fadeOut = 0.2f
      ms.color = Color(255,255,255,255)
      ms.angle = weapon.currAngle
      ms.angleSpeed = MathUtils.getRandomNumberInRange(-180f,180f)
      ms.setInitVel(
        speed2Velocity(weapon.currAngle+90f +MathUtils.getRandomNumberInRange(-10f,10f),
          getRandomNumberInRange(40f,80f)))
      ms.setInitVel(weapon.ship.velocity)
      ms.stopSpeed = 0.9f
      addEffect(ms)
      addEffect(aEP_b_m_h88_shot.ShellGlow(ms, SHELL_GLOW))
    }

  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    point?:return
    explode(point)
    if(shieldHit && target is ShipAPI){
      val rand = getRandomNumberInRange(0f,100f)
      if(rand <= TRIGGER_CHANCE){
        aEP_b_l_aa40_shot.applyPureShipDamage(target, point, AMOUNT_PER_TRIGGER, PERCENT_PER_TRIGGER)
      }
    }

  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {
    explosion?:return
    explode(explosion.location)
  }

  fun explode(loc: Vector2f){
    spawnSingleCompositeSmoke(loc,125f,1f,SMOKE_RING_COLOR)
  }
}
class aEP_b_l_aa40_shot : Effect(){
  companion object{
    val SMOKE_RING_COLOR =  Color(205, 205, 205, 30)
    val SMOKE_RING_COLOR2 =   Color(40, 40, 40, 70)

    val SHELL_GLOW = Color(255, 168, 58, 80)
    val TRIGGER_COLOR = Color(155, 168, 188, 255)
    private var TRIGGER_CHANCE = 100f
    var PERCENT_PER_TRIGGER = 2f
    var AMOUNT_PER_TRIGGER = 200f

    fun applyPureShipDamage(target:ShipAPI, point: Vector2f, damageFlat:Float, damagePercent:Float){
      val fluxLeft = target.fluxTracker.maxFlux - target.fluxTracker.currFlux - 1f

      val percentAmount = target.fluxTracker.maxFlux * damagePercent / 100f
      var toAdd = (damageFlat.coerceAtLeast(percentAmount)).coerceAtMost(fluxLeft)
      val shieldDamageTakenMult = target.mutableStats.shieldDamageTakenMult.modified
      val fluxPerDam = (target.shield?.fluxPerPointOfDamage?:1f) * shieldDamageTakenMult
      if(fluxPerDam > 0f){
        toAdd /= fluxPerDam
        //toAdd /= energyTakenMult
      }
      Global.getCombatEngine().applyDamage(
        target,point,
        toAdd,DamageType.OTHER,0f,
        false,false,null)

      //先在光束落点加一个特效
      Global.getCombatEngine().addHitParticle(
        point,Misc.ZERO,300f,
        1f,0f,0.5f,TRIGGER_COLOR)

      //横杠闪光
      MagicRender.battlespace(Global.getSettings().getSprite("graphics/fx/starburst_glow1.png"),
        point,
        VECTOR2F_ZERO,
        Vector2f(100f , 900f ),
        Vector2f(-10f, -400f ),
        90f,
        0f,
        TRIGGER_COLOR,
        true, 0f, 0.1f, 0.6f)
    }
  }

  init {
    //把customHL一位一位读char，计算 “|”的情况
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0){
        TRIGGER_CHANCE =  num.replace("%","").toFloat()
      }
      if(i == 1){
        PERCENT_PER_TRIGGER =  num.replace("%","").toFloat()
      }
      if(i == 2){
        AMOUNT_PER_TRIGGER =  num.toFloat()
      }
      i += 1
    }

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

    //炮口焰
    Global.getCombatEngine().spawnExplosion(
      projectile.location,
      speed2Velocity(weapon.currAngle, 60f),
      Color(240,110,20), 60f, 0.35f)

//    //闪光，使用muzzle flash实现
//    Global.getCombatEngine().addHitParticle(
//      projectile.location,
//      speed2Velocity(weapon.currAngle, 60f),
//      300f,0.35f,0.33f, 0.15f,
//      Color(255,240,160))


    //创造蛋壳
    projectile?: return
    var onLeft = false
    if(MathUtils.getShortestRotation(VectorUtils.getAngle(weapon.location,projectile.location),weapon.currAngle) > 0){
      onLeft = true
    }
    if(onLeft){
      val ejectPoint = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle-100f,20f)
      val ms = aEP_MovingSprite(ejectPoint, Vector2f(6f,3f),weapon.currAngle,"graphics/weapons/aEP_b_l_aa40/shred.png")
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
      val ms = aEP_MovingSprite(ejectPoint, Vector2f(6f,3f),weapon.currAngle,"graphics/weapons/aEP_b_l_aa40/shred.png")
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

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    if(shieldHit && target is ShipAPI){
      val rand = getRandomNumberInRange(0f,100f)
      if(rand <= TRIGGER_CHANCE){
        applyPureShipDamage(target, point, AMOUNT_PER_TRIGGER, PERCENT_PER_TRIGGER)

      }
    }

    //explode(point)
  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {
    explode(explosion.location)
  }

  fun explode(loc: Vector2f){
    //节约资源
    if (!Global.getCombatEngine().viewport.isNearViewport(loc, 800f)) return

    var time = 0.8f

    //弹片
    var angle = 0f
    var rad = 100f
    var num = 5f
    var size = 2f
    var moveSpeed = 50f
    while (angle < 360){
      val p = aEP_Tool.getExtendedLocationFromPoint(loc,angle,MathUtils.getRandomNumberInRange(0f,rad/3f))
      val randomSize = MathUtils.getRandomNumberInRange(0f,2f) + size
      val vel = VectorUtils.getDirectionalVector(loc, p)
      vel.scale(moveSpeed)
      val ms = aEP_MovingSprite(p, Vector2f(randomSize,randomSize),MathUtils.getRandomNumberInRange(0f,360f),"graphics/weapons/aEP_large_kinetic_flak/shell.png")
      ms.lifeTime = time
      ms.fadeOut = 0.8f
      ms.color = Color(240,240,200,150)
      ms.setInitVel(vel)
      ms.stopSpeed = 0.9f
      addEffect(ms)
      angle += 360f/num
    }

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
        time,
        SMOKE_RING_COLOR)
      Global.getCombatEngine().addSwirlyNebulaParticle(
        outPoint,
        vel,
        size,
        1.2f,
        0.1f,
        0f,
        time,
        SMOKE_RING_COLOR2,
        true)
      angle += spread
    }

    val ring = aEP_SpreadRing(
      40f,
      40f,
      Color(251,250,255,25),
      40f,
      1000f,
      loc)
    ring.initColor.setToColor(250f,250f,250f,0f,0.6f)
    ring.endColor.setColor(250f,250f,250f,0f)
    aEP_CombatEffectPlugin.addEffect(ring)

  }

}

//智能榴弹炮 hv7
class aEP_b_m_hv7_shot : Effect(){
  companion object{
    var SPLIT_NUM = 4f
    var WEAPON_RANGE = 800f
  }

  init {
    //把customHL一位一位读char，计算 “|”的情况
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0){
        SPLIT_NUM =  num.toFloat()
      }
      i += 1
    }

  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile?: return

    val sizeMult = 1f

    //中心火焰
    Global.getCombatEngine().spawnExplosion(
      projectile.location,
      speed2Velocity(weapon.currAngle, 50f),
      Color(240,110,20), 30f * sizeMult, 0.6f)

    //向前移动的火焰1
    Global.getCombatEngine().spawnExplosion(
      projectile.location,
      speed2Velocity(weapon.currAngle, 120f),
      Color(140,120,70), 25f * sizeMult, 0.5f)

    //向前移动的火焰2
    Global.getCombatEngine().spawnExplosion(
      projectile.location,
      speed2Velocity(weapon.currAngle, 180f),
      Color(120,120,120), 20f * sizeMult, 0.4f)

    addEffect(SplitTrigger(projectile))

    //创造蛋壳
    val ejectPoint = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle-110f,10f)
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
    val glow = aEP_b_m_h88_shot.ShellGlow(ms,Color(140,120,70,100) )
    glow.endSize = 10f
    glow.extraSize = 10f
    addEffect(glow)

  }

}
class SplitTrigger : aEP_BaseCombatEffect{

  val fuseRange = 300f

  ///向右，向前为正
  val triggerOffset = Vector2f(0f,0f)
  val earliestSplitTime = 0.15f
  val SMOKE_COLOR = Color(240,240,240,234)

  val projectile: DamagingProjectileAPI
  var timer = 0f
  constructor(projectile: DamagingProjectileAPI){
    this.projectile = projectile
    init(projectile)
  }

  override fun advanceImpl(amount: Float) {
    if(projectile.isFading){
      shouldEnd = true
      return
    }

    timer += amount
    if(timer < earliestSplitTime){
      return
    }

    var shouldSplit = false
    val triggerPoint = getExtendedLocationFromPoint(projectile.location, projectile.facing ,triggerOffset.y)
    triggerPoint.set(getExtendedLocationFromPoint(triggerPoint, projectile.facing - 90f,triggerOffset.x))

    //aEP_Tool.addDebugPoint(triggerPoint)
    //检测距离为弹体前方一圆形，任何一个船体碰撞点处于圆内时触发引信
    var triggered:ShipAPI? = null
    for(t in CombatUtils.getShipsWithinRange(triggerPoint, fuseRange)){

      //不会被非驱逐/护卫触发
      if(t.hullSize != ShipAPI.HullSize.FRIGATE &&
        t.hullSize != ShipAPI.HullSize.DESTROYER) return

      if(t.collisionClass == CollisionClass.NONE) continue
      if(t.exactBounds == null) continue
      if(t == projectile.source) continue
      if(t.owner == projectile.source.owner) continue
      //aEP_Tool.addDebugText("did", ship.location)

      if(t.shield != null && t.shield.isOn){
        //如果目标护盾启动，检测triggerCircle是否与护盾向交
        val triggerPoint2targetCenterDistSq = getDistanceSquared(triggerPoint, t.location)
        if(triggerPoint2targetCenterDistSq < (fuseRange + t.shield.radius).pow(2)){
          val angleShield2Proj = VectorUtils.getAngle(t.shield.location, triggerPoint)
          val angleDistAbs =Math.abs(getShortestRotation(angleShield2Proj, t.shield.facing))

          if(angleDistAbs <= t.shield.activeArc/2f + 5f){
            triggered = t
            shouldSplit = true
            //aEP_Tool.addDebugText("in", triggerPoint)
            break
          }
        }
      }else{
        //如果目标护盾启动，检测triggerCircle是否与任何一根exactBounds中的边界线相交
        for(seg in t.exactBounds.segments){
          if(CollisionUtils.getCollides(seg.p1, seg.p2, triggerPoint, fuseRange)){
            triggered = t
            shouldSplit = true
            //aEP_Tool.addDebugText("in", triggerPoint)
            break
          }
        }
      }
    }

    //控制shouldEnd在split()里面
    if(shouldSplit){
      split(triggered as ShipAPI)
    }

  }

  fun split(triggered:ShipAPI){

    //分裂
    var spawned = 0

    val weaponRange = aEP_b_m_hv7_shot.WEAPON_RANGE

    val picker = WeightedRandomPicker<ShipAPI>()
    picker.add(triggered, 1000f)
    val searchDistSq = (weaponRange - 100f).pow(2f)
    for(s in Global.getCombatEngine().ships){
      //不锁残骸和友军
      //不锁触发引信的那艘舰船
      if(triggered == s) continue
      if(!isEnemy(projectile, s)) continue
      if(isDead(s)) continue

      if(!aEP_Tool.isShipTargetable(
          s, false,
          false,true,
          false,false)) continue

      val distSq = getDistanceSquared(s,projectile.location)
      if(distSq > searchDistSq) continue

      var weight = 0.01f
      when (s.hullSpec.hullSize){
        ShipAPI.HullSize.CAPITAL_SHIP ->{weight = 0.1f}
        ShipAPI.HullSize.CRUISER ->{weight = 0.1f}
        ShipAPI.HullSize.DESTROYER ->{weight = 1f}
        ShipAPI.HullSize.FRIGATE ->{weight = 2f}
      }
      picker.add(s, weight)
    }

    //检测范围中多于1艘敌人才会开始分裂
    var shouldSplit = false
    if(picker.items.size >= 1){
      shouldSplit = true
    }

    //弱智alex，getDamage是加成后damage，setDamage实际是baseDamage
    //目前不使用
    val damAmount = (projectile.damage.baseDamage * 2f)/aEP_b_m_hv7_shot.SPLIT_NUM

    if(shouldSplit){
      //把picker里面的抽完，每抽一个，降低weight放回去，所以一定抽的完
      while (spawned < aEP_b_m_hv7_shot.SPLIT_NUM && !picker.isEmpty){
        spawned += 1
        val tgt = picker.pickAndRemove()
        val facing = VectorUtils.getAngle(projectile.location, tgt.location)
        val newProj = Global.getCombatEngine().spawnProjectile(
          projectile.source,
          null,
          aEP_b_m_hv7_shot::class.java.simpleName.replace("_shot","_2"),
          projectile.location,
          facing,
          null) as DamagingProjectileAPI
        val incpP = AIUtils.getBestInterceptPoint(projectile.location,newProj.moveSpeed,tgt.location, tgt.velocity)
       if(incpP != null){
         val newFacing = VectorUtils.getAngle(projectile.location, incpP)
         val newVel = speed2Velocity(Vector2f(newFacing, newProj.moveSpeed))
         newProj.facing = newFacing
         newProj.velocity.set(newVel)
       }

        //newProj.damage.damage = damAmount
        //一个目标只会被分裂一次，不再重新加回picker
        picker.add(tgt,0.01f)
      }

      //把剩下的随机分裂了
      while (spawned < aEP_b_m_hv7_shot.SPLIT_NUM ){
        spawned += 1
        val facing = MathUtils.getRandomNumberInRange(0f,360f)
        val newProj = Global.getCombatEngine().spawnProjectile(
          projectile.source,
          null,
          aEP_b_m_hv7_shot::class.java.simpleName.replace("_shot","_2"),
          projectile.location,
          facing,
          null) as DamagingProjectileAPI
      }

      //分裂的特效
      spawnSingleCompositeSmoke(projectile.location,100f,1.5f, SMOKE_COLOR)

      //音效
      Global.getSoundPlayer().playSound("devastator_explosion",1f,1.2f,projectile.location,Misc.ZERO)
      Global.getCombatEngine().removeEntity(projectile)

      shouldEnd = true
    }

  }

}

//h88 创伤炮
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

//锚点无人机ads
class aEP_ftr_ut_maodian_ads_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    addEffect(AdsDamageListener(projectile as MissileAPI))
    //addEffect(debug(projectile as MissileAPI))
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {

  }

}
class AdsDamageListener(val proj: MissileAPI) : aEP_BaseCombatEffect(0f, proj){
  companion object{
    var EXPLOSION_C = Color(165, 215, 255, 255)
    var FRAG_C = Color(215, 225, 255, 210)
    var FRAG_GLOW_C = Color(155, 175, 255, 120)
  }

  var didIntercet = false

  override fun advanceImpl(amount: Float) {
    //找到触发了碰撞，并且用于穿越导弹能力的所有弹丸
    val projs: MutableList<DamagingProjectileAPI> = java.util.ArrayList()
    for (hit in Global.getCombatEngine().projectiles) {

      if(hit.owner == proj.owner) continue
      if(hit.collisionClass == CollisionClass.NONE) continue
      //检测到可以穿越导弹的弹丸
      if(hit.projectileSpec?.isPassThroughMissiles != true) continue

      if (isWithinRange(proj, hit, proj.collisionRadius)) {
        projs.add(hit)
      }
    }

    //触发ads效果，手动降低弹丸基础伤害，如果弹丸基础伤害够低就消除
    val keyDamage = proj.maxHitpoints
    if(projs.size >= 1){
      val toErase = projs[0]
      if(toErase.damage.baseDamage < keyDamage){
        Global.getCombatEngine().spawnExplosion(
          toErase.location,
          aEP_ID.VECTOR2F_ZERO,
          proj.spec.glowColor,
          (toErase.damage.baseDamage/8f).coerceAtMost(120f),
        0.35f)
        Global.getCombatEngine().removeEntity(toErase)
      }else{
        toErase.damage.damage -= keyDamage
      }

      Global.getCombatEngine().spawnEmpArcVisual(
        proj.location,
        proj,
        toErase.location,
        toErase,
        5f,
        proj.spec.glowColor,
        Color.white)

      didIntercet = true

      //proj.explode()
      Global.getCombatEngine().removeEntity(proj)
    }

  }

  override fun readyToEnd() {
    if(!didIntercet && proj.hitpoints >= proj.maxHitpoints) return

    //创造一坨碎屑特效
    val facing = proj.facing
    for(i in 0 until 6) {
      val randomSize = getRandomNumberInRange(3f, 5f)
      val randomAngle = getRandomNumberInRange(-30f, 30f) + facing
      val randomVel = speed2Velocity(randomAngle, 400f)
      randomVel.scale(getRandomNumberInRange(0.25f, 1f))
      val ms = aEP_MovingSprite(
        proj.location,
        Vector2f(randomSize, randomSize),
        getRandomNumberInRange(0f, 360f),
        "graphics/weapons/aEP_b_l_aa40/shell.png")
      ms.lifeTime = 0.8f + getRandomNumberInRange(0f, 0.4f)
      ms.fadeOut = 0.4f
      ms.color = AdsDamageListener.FRAG_C
      ms.setInitVel(randomVel)
      ms.stopSpeed = 0.875f
      addEffect(ms)
      addEffect(Glow(ms, FRAG_GLOW_C))
    }
    if (Global.getCombatEngine().viewport.isNearViewport(proj.location, 800f)) {
      val numParticles  = 8
      val minSize = 5f
      val maxSize = 20f
      val pc: Color = EXPLOSION_C
      val minDur = 0.2f
      val maxDur = 0.6f
      val arc = 0f
      val scatter = 1f
      val minVel = 100f
      val maxVel = 400f
      val endSizeMin = 5f
      val endSizeMax = 10f
      val spawnPoint = Vector2f(proj.location)
      for (i in 0 until numParticles) {
        var angleOffset = Math.random().toFloat()
        if (angleOffset > 0.2f) {
          angleOffset *= angleOffset
        }
        var speedMult = 1f - angleOffset
        speedMult = 0.5f + speedMult * 0.5f
        angleOffset *= sign((Math.random().toFloat() - 0.5f))
        angleOffset *= arc / 2f
        val theta = Math.toRadians((facing + angleOffset).toDouble()).toFloat()
        val r = (Math.random() * Math.random() * scatter).toFloat()
        val x = cos(theta.toDouble()).toFloat() * r
        val y = sin(theta.toDouble()).toFloat() * r
        val pLoc = Vector2f(spawnPoint.x + x, spawnPoint.y + y)
        var speed = minVel + (maxVel - minVel) * Math.random().toFloat()
        speed *= speedMult
        val pVel = Misc.getUnitVectorAtDegreeAngle(Math.toDegrees(theta.toDouble()).toFloat())
        pVel.scale(speed)
        val pSize = minSize + (maxSize - minSize) * Math.random().toFloat()
        val pDur = minDur + (maxDur - minDur) * Math.random().toFloat()
        val endSize = endSizeMin + (endSizeMax - endSizeMin) * Math.random().toFloat()
        Global.getCombatEngine().addNebulaParticle(pLoc, pVel, pSize, endSize, 0.1f, 0.5f, pDur, pc)
      }
    }

  }

}

//闪电机炮
class aEP_b_m_lighting_shot : Effect(){
  companion object{
    var DAMAGE_TO_UPKEEP_INCREASE = 20f
    var BUFF_LIFETIME = 8f
    const val KEY = "aEP_b_m_lighting_shot"

  }

  init{
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) BUFF_LIFETIME = num.toFloat()
      if(i == 1) DAMAGE_TO_UPKEEP_INCREASE = num.toFloat()
      i += 1
    }
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    if (shieldHit && target is ShipAPI) {
      addEffect(IncreaseUpKeep(BUFF_LIFETIME,target, DAMAGE_TO_UPKEEP_INCREASE))
    }

  }

  class IncreaseUpKeep(time:Float, val s:ShipAPI, val addPerSec:Float) : aEP_BaseCombatEffect(time,s){
    val tracker = IntervalUtil(0.5f,5f)
    override fun advanceImpl(amount: Float) {
      super.advanceImpl(amount)
      tracker.advance(amount)
      if(tracker.intervalElapsed() ){
        //无护盾，或者护盾尚未开启，本次tick不起效
        if(s.shield == null || s.shield?.isOn == false) return
        //限制增长不会导致过载
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
}
class DriftToTarget : aEP_BaseCombatEffect{
  companion object{
    const val MAGNETIC_ATTRACTION_RANGE = 500f
    const val MAGNETIC_ATTRACTION_ACCELERATION = 625f
    const val STOP_SPEED = 0.8f

  }

  val magneticTracker = IntervalUtil(0.1f,0.1f)
  constructor(projectile: DamagingProjectileAPI){
    init(projectile)
  }
  var isAttracted = false

  override fun advanceImpl(amount: Float) {
    magneticTracker.advance(amount)
    if(!magneticTracker.intervalElapsed()) return

    //寻找最近的敌方非战机舰船
    isAttracted = false
    val proj = entity as DamagingProjectileAPI
    var nearestTarget = aEP_Tool.getNearestEnemyCombatShip(proj)

    //找到了，如果在400范围内，向目标飞去
    if(nearestTarget != null ){
      val dist = MathUtils.getDistance(nearestTarget,proj.location)
      if(dist <= MAGNETIC_ATTRACTION_RANGE){
        val distLevel = (MAGNETIC_ATTRACTION_RANGE -dist)/ MAGNETIC_ATTRACTION_RANGE
        val distPercent = 0.25f + 0.75f*distLevel*distLevel
        val angleAndSpeed = velocity2Speed(proj.velocity)
        val facingToTarget = VectorUtils.getAngle(proj.location, nearestTarget.location)
        val angleDistAbs = MathUtils.getShortestRotation(angleAndSpeed.x, facingToTarget).absoluteValue
        val angleDistPercent = angleDistAbs/181f
        //如果反向，先快速停下来
        if(angleDistPercent > 0.35f){
          proj.velocity.scale(STOP_SPEED)
        }
        aEP_Tool.forceSetThroughPosition(
          proj,nearestTarget.location,
          magneticTracker.elapsed,
          MAGNETIC_ATTRACTION_ACCELERATION * distPercent,
          (proj as MissileAPI).maxSpeed)
        isAttracted = true

      } else{ //否则减速原地禁止
        proj.velocity.scale(STOP_SPEED)
      }
    }
    //否则减速原地禁止
    else
      proj.velocity.scale(STOP_SPEED)
  }

  override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
    //begin
//    aEP_Render.openGL11Abs()
//
//    val center = Vector2f(entity?.location?: VECTOR2F_ZERO)
//    val rad = MAGNETIC_ATTRACTION_RANGE
//    val width = 3f
//
//    //控制弧线的精度，越多越贴近真弧形
//    val  numOfVertex = 3
//    //条纹的间隔多少度
//    val angleStripe = 10
//    var angle = 0f
//    val largeRad = rad
//    val smallRad = largeRad - width
//    while (angle < 360){
//      GL11.glBegin(GL11.GL_QUAD_STRIP)
//      for (i in 0 until numOfVertex) {
//        val pointFar = aEP_Tool.getExtendedLocationFromPoint(center, angle, largeRad)
//        //没有敌人为白，有人为红
//        if(!isAttracted) GL11.glColor4f(0.75f,0.8f,0.8f, 0.05f)
//        else GL11.glColor4f(1f,0.25f,0.15f, 0.2f)
//        GL11.glVertex2f(pointFar.x, pointFar.y)
//
//        val pointNear = aEP_Tool.getExtendedLocationFromPoint(center, angle, smallRad)
//        GL11.glColor4f(1f,1f,1f,0f)
//        GL11.glVertex2f(pointNear.x, pointNear.y)
//        angle += angleStripe/numOfVertex
//      }
//      GL11.glEnd()
//      angle += angleStripe
//    }
//    aEP_Render.closeGL11()
  }

  override fun getRenderRadius(): Float {
    return MAGNETIC_ATTRACTION_RANGE
  }
}
//深度收缩天线
class aEP_des_shendu_antenna_blinker : EveryFrame(){

  private val frameChangeInterval = 0.1f
  private var timer = 0f

  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    val ship = weapon.ship
    if (engine.isPaused || weapon.slot.isHidden) {
      return
    }
    timer += amount
    timer = if (timer > frameChangeInterval) {
      0f
    } else {
      return
    }
    val anime = weapon.animation
    val effectLevel = weapon.ship.system.effectLevel
    if (effectLevel > 0.5) {
      Global.getCombatEngine().addSmoothParticle(
        getExtendedLocationFromPoint(weapon.slot.computePosition(ship), weapon.currAngle, -8f),  //Vector2f loc,
        Vector2f(0f, 0f),  //Vector2f vel,
        25f,  //float size,
        0.2f,  //float brightness
        0.2f,  //float duration,
        Color(220, 120, 120, (240 * effectLevel).toInt())) //java.awt.Color color)
      Global.getCombatEngine().addSmoothParticle(
        getExtendedLocationFromPoint(weapon.slot.computePosition(ship), weapon.currAngle, -8f),  //Vector2f loc,
        Vector2f(0f, 0f),  //Vector2f vel,
        100f,  //float size,
        0.2f,  //float brightness
        0.2f,  //float duration,
        Color(220, 120, 120, (80 * effectLevel).toInt())) //java.awt.Color color)
      if (anime.frame < anime.numFrames - 1) {
        anime.frame += 1
      }
    }
    if (effectLevel < 0.5 && anime.frame > 0) {
      anime.frame -= 1
    }
  }
}

//水蚀 罐式热诱弹 热诱弹空雷突袭
class aEP_des_shuishi_flare_shot : Effect(){
  companion object{
    const val PASSIVE_FLARE_WEAPON_ID = "aEP_m_s_flare"
    const val JAMMER_FLARE_WEAPON_ID = "aEP_m_s_flare2"
  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {
    //被动吸引导弹，飞的远
    for (i in 1..8){
      val m = Global.getCombatEngine().spawnProjectile(
        originalProjectile.source,
        null,
        PASSIVE_FLARE_WEAPON_ID,
        getRandomPointInCircle(explosion.location, 50f),
        getRandomNumberInRange(0f,360f),
        null) as MissileAPI
      m.source = originalProjectile.source
      m.maxFlightTime *= getRandomNumberInRange(0.5f,1f)
      m.velocity.scale(getRandomNumberInRange(0.2f,1f))
    }

    //释放后只有发起者和目标非常近才会进入环绕制导模式，不好用
//    for (i in 1..4){
//      val m = Global.getCombatEngine().spawnProjectile(
//        originalProjectile.source,
//        null,
//        JAMMER_FLARE_WEAPON_ID,
//        getRandomPointInCircle(explosion.location, 50f),
//        getRandomNumberInRange(0f,360f),
//        null) as MissileAPI
//    }


    val upper = aEP_MovingSprite(
      explosion.location,
      Vector2f(30f,40f),
      originalProjectile.facing,
      "aEP_FX.flare_mine_upper")
    upper.angleSpeed = getRandomNumberInRange(-30f,-60f)
    upper.velocity = getRandomPointInCircle(VECTOR2F_ZERO,50f)
    upper.fadeIn = 0f
    upper.fadeOut = 0.2f
    upper.lifeTime = 4f
    addEffect(upper)
    val glow = aEP_b_m_h88_shot.ShellGlow(upper, Misc.setAlpha((originalProjectile as MissileAPI).spec.explosionColor,50))
    glow.endSize = 30f
    glow.extraSize = 30f
    addEffect(glow)
    val lower = aEP_MovingSprite(
      explosion.location,
      Vector2f(30f,80f),
      originalProjectile.facing,
      "aEP_FX.flare_mine_lower")
    lower.angleSpeed = getRandomNumberInRange(30f,60f)
    lower.velocity = getRandomPointInCircle(VECTOR2F_ZERO,50f)
    lower.fadeIn = 0f
    lower.fadeOut = 0.2f
    lower.lifeTime = 4f
    addEffect(lower)
    val glow2 = aEP_b_m_h88_shot.ShellGlow(lower, Misc.setAlpha(originalProjectile.spec.explosionColor,50))
    glow2.endSize = 30f
    glow2.extraSize = 30f
    addEffect(glow2)
  }
}

//crossout铲车头，长矛
class aEP_des_yonglang_mk2_cover : EveryFrame(){
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    weapon.ship?:return
    for(m in weapon.ship.childModulesCopy){
      m?: continue
      //注意，模块死亡脱离以后，stationSlot会改为null
      if(m.hullSpec.baseHullId.equals("aEP_des_yonglang_mk2_m")){
        weapon.animation.frame = 0
        if(m.isAlive){
          weapon.animation.frame = 1
          return
        }
      }
    }
  }
}
class aEP_m_s_bomblance : EveryFrame(){
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    weapon.ship?:return

    //不会下线
    aEP_Tool.keepWeaponAlive(weapon)

    val triggerPoint = getExtendedLocationFromPoint(weapon.getFirePoint(0), weapon.currAngle, 0f)
    //aEP_Tool.addDebugPoint(triggerPoint)
    val ships = Global.getCombatEngine().ships
    for (s in ships) {
      if (s.owner == weapon.ship.owner) continue
      if (!s.isFrigate && !s.isDestroyer && !s.isCruiser && !s.isCapital) continue

      val dist = MathUtils.getDistance(s.location, triggerPoint)
      if (dist <= s.collisionRadius * 0.9f && weapon.ammo >= 0 && weapon.cooldownRemaining <= 0) {
        //如果矛头戳进了碰撞圈的0.9倍内，检测实际碰撞点距离，防止碰撞圈虚高的情况
        val collision = CollisionUtils.getNearestPointOnBounds(triggerPoint, s)
        if(collision != null && MathUtils.getDistance(collision, triggerPoint) <= 75f){
          weapon.setForceFireOneFrame(true)
          return
        }
      }
    }
  }
}
class aEP_m_s_bomblance_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile?: return
    engine?:return
    val missile = projectile as MissileAPI
    //aEP_Tool.addDebugLog(missile.damage.damage.toString())
    missile.explode()

    val test = MathUtils.getRandomNumberInRange(0,100)
    if(test < 35){
      //杆子
      MagicRender.battlespace(Global.getSettings().getSprite("graphics/weapons/aEP_FCL/lance_empty.png"),
        getExtendedLocationFromPoint(projectile.location,projectile.facing,0f),
        MathUtils.getRandomPointInCone(Vector2f(0f,0f),100f, projectile.facing-30f, projectile.facing+30f),
        Vector2f(12f,200f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-20f,20f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(4f,7f), 0.5f)
    }else if (test < 70){
      //断杆上
      MagicRender.battlespace(Global.getSettings().getSprite("graphics/weapons/aEP_FCL/lance_empty3.png"),
        getExtendedLocationFromPoint(projectile.location,projectile.facing,50f),
        MathUtils.getRandomPointInCone(Vector2f(0f,0f),100f, projectile.facing-30f, projectile.facing+30f),
        Vector2f(4f,43f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-20f,20f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(4f,7f), 0.5f)
      //断杆下
      MagicRender.battlespace(Global.getSettings().getSprite("graphics/weapons/aEP_FCL/lance_empty2.png"),
        getExtendedLocationFromPoint(projectile.location,projectile.facing,-50f),
        MathUtils.getRandomPointInCone(Vector2f(0f,0f),100f, projectile.facing-30f, projectile.facing+30f),
        Vector2f(4f,140f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-20f,20f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(4f,7f), 0.5f)
    }else{
      //断杆上
      MagicRender.battlespace(Global.getSettings().getSprite("graphics/weapons/aEP_FCL/lance_empty3.png"),
        getExtendedLocationFromPoint(projectile.location,projectile.facing,50f),
        MathUtils.getRandomPointInCone(Vector2f(0f,0f),100f, projectile.facing-30f, projectile.facing+30f),
        Vector2f(4f,43f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-20f,20f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(4f,7f), 0.5f)
      //断杆中
      MagicRender.battlespace(Global.getSettings().getSprite("graphics/weapons/aEP_FCL/lance_empty4.png"),
        getExtendedLocationFromPoint(projectile.location,projectile.facing,0f),
        MathUtils.getRandomPointInCone(Vector2f(0f,0f),100f, projectile.facing-30f, projectile.facing+30f),
        Vector2f(4f,100f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-20f,20f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(4f,7f), 0.5f)
      //断杆下
      MagicRender.battlespace(Global.getSettings().getSprite("graphics/weapons/aEP_FCL/lance_empty5.png"),
        getExtendedLocationFromPoint(projectile.location,projectile.facing,-50f),
        MathUtils.getRandomPointInCone(Vector2f(0f,0f),100f, projectile.facing-30f, projectile.facing+30f),
        Vector2f(4f,40f),
        VECTOR2F_ZERO,
        //magicRender的角度开始点比游戏多90
        projectile.facing - 90f,MathUtils.getRandomNumberInRange(-20f,20f),
        Color.white,
        false, 0f, MathUtils.getRandomNumberInRange(4f,7f), 0.5f)
    }

    Global.getCombatEngine().removeEntity(missile)
  }

}

//德雷克 喷火
class aEP_e_s_flamer_shot : Effect(){

  companion object{
    val CORE_FLAME_COLOR = Color(255,194,86,175)
    val CORE_FLAME_COLOR2= Color(252,164,50,95)
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    projectile?:return

    val vel = Vector2f(projectile.velocity)
    val lifeTime = (projectile.weapon.range / (projectile.moveSpeed + 0.1f)) + 0.35f

    val sizeMult = 0.35f

    val vel1 = Vector2f(projectile.velocity)
    //vel1.scale(getRandomNumberInRange(0.9f,1.1f))
    val loc1 = getExtendedLocationFromPoint(projectile.location,projectile.facing,40f);
    Global.getCombatEngine().addNebulaParticle(
      loc1,
      vel1,
      20f * sizeMult,
      6f,
      0.25f,0.5f,lifeTime,
      CORE_FLAME_COLOR,true)

    val vel2 = Vector2f(projectile.velocity)
    //vel2.scale(getRandomNumberInRange(0.85f,1.15f))
    val loc2 = getExtendedLocationFromPoint(projectile.location,projectile.facing,20f);
    Global.getCombatEngine().addNebulaParticle(
      loc2,
      vel2,
      20f * sizeMult,
      4.5f,
      0.25f,0.35f,lifeTime,
      CORE_FLAME_COLOR,true)

    val vel3 = Vector2f(projectile.velocity)
    vel3.scale(getRandomNumberInRange(0.8f,1.1f))
    val loc3 = getExtendedLocationFromPoint(projectile.location,projectile.facing,10f);
    Global.getCombatEngine().addNebulaParticle(
      loc3,
      vel3,
      40f * sizeMult,
      3f,
      0.25f,0.25f,lifeTime,
      CORE_FLAME_COLOR2,true)

    vel.scale(0.5f)
    Global.getCombatEngine().addNebulaParticle(
      loc1,
      vel,
      20f,
      3f,
      0.25f,0.1f,0.5f,
      CORE_FLAME_COLOR
    )

  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
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

//洋脊防空弹幕分裂
class aEP_des_yangji_flak_shot : Effect() {

  companion object{
    var EXTRA_DAMAGE = 100f

  }

  init{
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) EXTRA_DAMAGE = num.toFloat()
      i += 1
    }
  }


  var num = 1
  var speedVariant = 20f

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
      val speedChange = 1f + MathUtils.getRandomNumberInRange(-5f, speedVariant) / 100f
      newProj.velocity[newProj.velocity.x * speedChange] = newProj.velocity.y * speedChange
    }
    engine.removeEntity(projectile)

  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {

  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    Global.getCombatEngine().applyDamage(
      target,
      point,
      EXTRA_DAMAGE,
      DamageType.FRAGMENTATION,
      0f, false, false, projectile.source)
  }
}
//洋脊gp80舰队防空
class aEP_b_m_flak_shot : Effect(){
  companion object{
    var EXTRA_DAMAGE = 100f
  }

  init{
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) EXTRA_DAMAGE = num.toFloat()
      i += 1
    }
  }
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    addEffect(Blinking(projectile))
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
    if(target is ShipAPI){
      if(target.isFighter){
        dealExtraDamage(target, EXTRA_DAMAGE, projectile.source?:null, point)
      }
    }

    if(target is MissileAPI){
      dealExtraDamage(target, EXTRA_DAMAGE, projectile.source?:null, point)
    }
  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {
    explosion.setCustomData(aEP_WeaponEffect.EXPLOSION_PROJ_ID_KEY, originalProjectile.projectileSpecId)
  }

  fun dealExtraDamage(target: CombatEntityAPI, damage:Float, source: ShipAPI?, point: Vector2f){
    addEffect(DelayDamage(target, getRandomNumberInRange(0.4f,0.8f), damage, source, point))
  }
}
//弹丸拖尾+消失后的视觉特效
class Blinking(val proj: DamagingProjectileAPI) : aEP_BaseCombatEffect(5f,proj){
  val blinkTimer = IntervalUtil(0.05f,0.05f)
  override fun advanceImpl(amount: Float) {
    blinkTimer.advance(amount)
    if(blinkTimer.intervalElapsed()){
      for(i in 0..1){
        val loc = getRandomPointInCircle(proj.location,15f)
        val vel = Vector2f(proj.velocity)
        vel.scale(getRandomNumberInRange(0.05f,0.15f))
        Global.getCombatEngine().addSmoothParticle(
          loc,
          vel,
          getRandomNumberInRange(8f,30f),
          1f,
          0.3f+getRandomNumberInRange(0f,0.1f),
          Color(221,100,50,255))
      }

    }
  }

  override fun readyToEnd() {
    //节约资源
    if (!Global.getCombatEngine().viewport.isNearViewport(proj.location, 800f)) return

    Global.getCombatEngine().addHitParticle(
      loc,
      Misc.ZERO,
      getRandomNumberInRange(250f,350f),
      1f,
      0.1f,
      0.2f,
      Color(221,150,50,255))

    spawnCompositeSmoke(
      loc,
      200f,
      0.75f,
      Color(101,80,75,255))

    spawnSingleCompositeSmoke(
      loc,
      200f,
      0.75f,
      Color(155,150,155,205))

    spawnSingleCompositeSmoke(
      loc,
      200f,
      0.75f,
      Color(235,230,235,205))


    val ring = aEP_SpreadRing(
      75f,
      75f,
      Color(251,250,255,25),
      75f,
      1000f,
      loc)
    ring.initColor.setToColor(250f,250f,250f,0f,1f)
    ring.endColor.setColor(250f,250f,250f,0f)
    aEP_CombatEffectPlugin.addEffect(ring)
  }
}
class DelayDamage(val target: CombatEntityAPI, lifeTime: Float, val damage : Float, val source: ShipAPI?, val point:Vector2f) :  aEP_BaseCombatEffect(lifeTime, target) {
  override fun advanceImpl(amount: Float) {
    if(time >= lifeTime){
      Global.getCombatEngine().applyDamage(
        target, target.location, damage, DamageType.ENERGY,
        0f, true, false, source, false)

      val explodePoint: Vector2f = CollisionUtils.getCollisionPoint(point, target.location,target) ?: return
      val spec = DamagingExplosionSpec(
        0.45f, 50f, 1f, 0f, 0f,
        CollisionClass.HITS_SHIPS_AND_ASTEROIDS,CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
        6f,6f, 0.5f, 8,
        Color(105,105,205,255),
        Color(165,175,225,255))
      spec.isUseDetailedExplosion = true
      spec.detailedExplosionRadius = 75f
      spec.detailedExplosionFlashDuration = 0.25f
      spec.detailedExplosionFlashRadius = 150f
      spec.detailedExplosionFlashColorCore =  Color(165,175,225,255)
      spec.detailedExplosionFlashColorFringe = Color(105,105,205,175)
      Global.getCombatEngine().spawnDamagingExplosion(
        spec, source, explodePoint, false)
    }
  }

}

//反冲力炮 涌浪
class aEP_fga_yonglang_main : EveryFrame(){
  companion object{
    const val ID = "aEP_fga_yonglang_main"
    var RELOAD_TIME = 12f
    var FLUX_GEN = 3000f
    var FLUX_RETURN_TIME= 6f
  }

  var coverPlugin : aEP_DecoAnimation? = null
  var glowPlugin : aEP_DecoAnimation? = null
  var brPlugin : aEP_DecoAnimation? = null
  var scaffoldPlugin : aEP_DecoAnimation? = null

  init{
    //val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString()).customPrimaryHL
    //var i = 0
    //for(num in hlString.split("|")){
      //if(i == 0) RELOAD_TIME = num.toFloat()
      //if(i == 1) FLUX_RETURN_TIME = num.toFloat()
      //if(i == 2) FLUX_GEN = num.toFloat()
      //i += 1
    //}
  }

  var smokeTimer = IntervalUtil(0.05f, 0.05f)
  var ammoLoadTimer = IntervalUtil(RELOAD_TIME, RELOAD_TIME)
  var fluxToAdd = 0f

  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    val effectLevel = weapon.chargeLevel
    val ship = weapon.ship?: return


    //以下是动画控制部分
    if(coverPlugin == null || scaffoldPlugin == null || glowPlugin == null){
      findNearComboDecos(weapon)
      return
    }
    val coverPlugin = (coverPlugin?:return)
    val scaffoldPlugin = (scaffoldPlugin?:return)
    val glowPlugin = (glowPlugin?:return)
    val brPlugin = (brPlugin?:return)


    if (weapon.isFiring && weapon.cooldownRemaining <= 0f) {
      //准备开火，in时
      coverPlugin.setMoveToLevel(1f)
      scaffoldPlugin.setMoveToLevel(1f)
      glowPlugin.setMoveToLevel(1f)
      brPlugin.setMoveToLevel(1f)
      scaffoldPlugin.setMoveToLevel(1f)
    }
    else if(weapon.isFiring && weapon.cooldownRemaining > 0f) {
      //开火以后，cooldown中
      if (effectLevel > 0.5f) {
        //热炮管拖烟
        smokeTimer.advance(amount)
        if (smokeTimer.intervalElapsed()) {
          val toSpawn = glowPlugin.weapon.location
          Global.getCombatEngine().addNebulaParticle(toSpawn, Vector2f(0f,0f),
            40f,2f,
            0.1f,0.4f,2f,
            Color(210,190,180,65))
        }

        coverPlugin.setMoveToLevel(1f)
        scaffoldPlugin.setMoveToLevel(1f)
        glowPlugin.setMoveToLevel(1f)
        brPlugin.setMoveToLevel(1f)
        scaffoldPlugin.setMoveToLevel(1f)

        //调整发光贴图
        val glowLevel = (effectLevel - 0.5f) * 2f
        glowPlugin.setGlowEffectiveLevel(glowLevel)
        //ship.mutableStats.maxSpeed.modifyFlat(ID, SPEED_BACKWARD * glowLevel)
      }

    }
    else{
      //idle状态
      coverPlugin.setMoveToLevel(0f)
      scaffoldPlugin.setMoveToLevel(0f)
      glowPlugin.setMoveToLevel(0f)
      brPlugin.setMoveToLevel(0f)
      scaffoldPlugin.setMoveToLevel(0f)

      //调整发光贴图
      glowPlugin.setGlowEffectiveLevel(0f)
      if(ship.mutableStats.maxSpeed.flatMods.contains(ID)){
        //ship.mutableStats.maxSpeed.unmodify(ID)
      }

    }



  }

  fun findNearComboDecos(main:WeaponAPI){
    val ship = main.ship?:return
    var coverDistSq = 999999f
    var scaffoldDistSq = 999999f
    var glowDistSq = 999999f
    var brDistSq = 999999f
    for(w in ship.allWeapons){
      if(!w.slot.isDecorative) continue
      if(w.spec.weaponId.equals("aEP_fga_yonglang_main_cover_l")
        || w.spec.weaponId.equals("aEP_fga_yonglang_main_cover_r")){
        var d = getDistanceSquared(w.location, main.location)
        if(d < coverDistSq){
          coverDistSq = d
          coverPlugin = w.effectPlugin as aEP_DecoAnimation
        }
        continue
      }
      if(w.spec.weaponId.equals("aEP_fga_yonglang_glow")){
        var d = getDistanceSquared(w.location, main.location)
        if(d < glowDistSq){
          glowDistSq = d
          glowPlugin = w.effectPlugin as aEP_DecoAnimation
        }
        continue
      }
      if(w.spec.weaponId.equals("aEP_fga_yonglang_main_br")){
        var d = getDistanceSquared(w.location, main.location)
        if(d < brDistSq){
          brDistSq = d
          brPlugin = w.effectPlugin as aEP_DecoAnimation
        }
        continue
      }
      if(w.spec.weaponId.equals("aEP_fga_yonglang_scaffold")){
        var d = getDistanceSquared(w.location, main.location)
        if(d < scaffoldDistSq){
          scaffoldDistSq = d
          scaffoldPlugin = w.effectPlugin as aEP_DecoAnimation
        }
        continue
      }
    }
  }

}
class aEP_fga_yonglang_main_shot : Effect(){
  companion object{
    const val SPEED_BACKWARD = 300f
  }


  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val toSpawn = getExtendedLocationFromPoint(weapon.location, weapon.currAngle, 24f)

    //addEffect(Blink(pro as DamagingProjectileAPI))
    engine.addSmoothParticle(
      toSpawn,  //Vector2f loc,
      Vector2f(0f, 0f),  //Vector2f vel,
      250f,  //float size,
      1f,  //float brightness,
      0.4f,  //float duration,
      Color(200, 200, 200, 250))

    Global.getCombatEngine().addSmoothParticle(
      toSpawn,
      aEP_ID.VECTOR2F_ZERO,
      300f,1f,0.33f,0.15f,
      Color.white)


    val ship = weapon?.ship?: return
    ship.velocity.scale(0.5f)
    val velToAdd = aEP_Tool.speed2Velocity(Vector2f(ship.facing,-SPEED_BACKWARD))

    val x = ship.velocity.x + velToAdd.x
    val y = ship.velocity.y + velToAdd.y
    ship.velocity.set(x, y)

//    Global.getSoundPlayer().playSound(
//      "heavy_mortar_fire",
//      1f, 1.2f,  // pitch,volume
//      weapon.ship?.location?: VECTOR2F_ZERO,
//      weapon.ship?.velocity?: VECTOR2F_ZERO)


  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {
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
class aEP_b_m_rk107_shot : Effect(), DamageDealtModifier{
  companion object{
    private var DAMAGE_BONUS = 4f
    private var MAX_BONUS_DAMAGE = 450f
    const val WEAPON_ID = "aEP_b_m_rk107"
  }

  init{
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) DAMAGE_BONUS = num.toFloat()
      if(i == 1) MAX_BONUS_DAMAGE = num.toFloat()
      i += 1
    }
  }


  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val ship = weapon.ship
    if (!ship.hasListenerOfClass(this::class.java)) {
      ship.addListener(this)
    }
//    if(projectile is MissileAPI){
//      val speedNow = projectile.spec.launchSpeed
//      val maxSpeed = projectile.maxSpeed
//      val range = projectile.maxRange
//      var acc = projectile.acceleration
//      if(maxSpeed <= speedNow) return
//      if(acc <= 0) return
//      if(range <= 0) return
//      val speedDiff = maxSpeed - speedNow
//      val timeToAcc = speedDiff/acc
//      val timeCompensation = timeToAcc/2f
//      projectile.maxFlightTime = range/maxSpeed
//      projectile.maxFlightTime += timeCompensation
//    }
  }

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI, weaponId: String) {

    val sizeMult = 0.65f

    //中心点大闪光
    val spec = DamagingExplosionSpec(0.0001f, 150f * sizeMult, 150f * sizeMult,
      0f,0f,
      CollisionClass.NONE, CollisionClass.NONE,
      4f,4f,1.2f,10,
      Color.white,
      Color(255,255,235,125))
    spec.isUseDetailedExplosion = true
    spec.detailedExplosionFlashDuration = 0.2f
    spec.detailedExplosionFlashRadius = 200f * sizeMult
    spec.detailedExplosionRadius = 100f * sizeMult
    spec.detailedExplosionFlashColorFringe = Color(255,255,205,75)
    spec.detailedExplosionFlashColorCore = Color(255,255,235,125)
    engine.spawnDamagingExplosion(spec,projectile.source, point)

    //向前缓慢移动的爆炸
    engine.spawnExplosion(
      point,
      Misc.ZERO,
      Color(255,255,255,155),
      100f * sizeMult,
      0.6f)
  }

  override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {

    //击中舰船的装甲，提升伤害
    if(target !is ShipAPI) return null
    if(param is DamagingProjectileAPI){
      if (param.weapon != null && param.projectileSpecId?.equals(aEP_b_m_rk107_shot::class.java.simpleName) == true) {
        //击中护盾直接造成2倍软幅能
        if(shieldHit) {
          damage.modifier.modifyFlat(WEAPON_ID,1f)
          damage.isSoftFlux = true
          damage.isForceHardFlux = false
          return WEAPON_ID
        }else{    //击中舰船的装甲，提升伤害
          val armorBonus = (target.armorGrid.armorRating
              * target.mutableStats.minArmorFraction.modifiedValue
              * DAMAGE_BONUS).coerceAtMost(MAX_BONUS_DAMAGE)
          //基础值是1哦
          //如果基础伤害是0就没法计算了，不加成
          if(damage.baseDamage <= 0f) return null
          damage.modifier.modifyFlat(WEAPON_ID,armorBonus/damage.baseDamage)
          return WEAPON_ID
        }
      }
    }
    return null
  }

}

//era df1 爆反 爆炸反应装甲
open class aEP_m_s_era :EveryFrame(){
  companion object{
    const val KEY = "aEP_m_s_era"
    const val RANGE = 75f
    var DAMAGE_THRESHOLD = 600f
    var ARMOR_THRESHOLD = 0.8f
  }

  init {
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) DAMAGE_THRESHOLD = num.toFloat()
      if(i == 1) ARMOR_THRESHOLD = num.toFloat()
      i += 1
    }
  }

  //------------------------------//
  // everyFrame每个武器一个
  var inList = false

  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    weapon.ship?:return
    val ship = weapon.ship

    //装饰的爆反不起效
    if(weapon.slot != null && weapon.slot.weaponType == WeaponAPI.WeaponType.DECORATIVE) return

    weapon.setForceNoFireOneFrame(true)

    //第一个被读取的武器把listener放进customData
    if (!ship.customData.containsKey(KEY)) {
      val listr = EraDamageTakenModifier()
      listr.ship = ship
      ship.setCustomData(KEY,1f)
    }

    //不会下线
    aEP_Tool.keepWeaponAlive(weapon)

    //大概显示一下触发半径
    //addDebugPoint(getExtendedLocationFromPoint(weapon.location,ship.fullTimeDeployed * 180f,70f))

    //如果自己不在listener的list里面，塞进去
    if(!inList){
      if(ship.hasListenerOfClass(EraDamageTakenModifier::class.java)){
        ship.listenerManager.getListeners(EraDamageTakenModifier::class.java)[0].allEra.add(weapon)
        inList = true
      }
    }
  }

  class EraDamageTakenModifier: com.fs.starfarer.api.combat.listeners.DamageTakenModifier{
    val allEra = ArrayList<WeaponAPI>()
    var ship:ShipAPI? = null
    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      ship?:return null
      val ship = ship as ShipAPI
      if(shieldHit) return null
      val engine = Global.getCombatEngine()
      for(w in allEra){
        //检测距离先，检测不过直接continue
        val dist = MathUtils.getDistance(w.location,point)
        if(w.ammo < 1) continue
        if(dist > RANGE ) continue

        val didMod = aEP_ReactiveArmor.damageModify(damage, ship, param, DAMAGE_THRESHOLD, ARMOR_THRESHOLD)
        if(didMod > 0f){
          //文字提示
          Global.getCombatEngine().addFloatingText(
            point,
            "Reactive Armor !", 10f,
            Color.magenta, target, 1f, 1f)

          //产生炮口烟，刷出弹丸，立刻引爆
          engine.spawnMuzzleFlashOrSmoke(
            w.ship, w.slot, w.spec, 0,
            w.currAngle)
          val proj = engine.spawnProjectile(
            w.ship, w, w.spec.weaponId,
            w.getFirePoint(0),  //FirePoint得到的是绝对位置
            w.currAngle,
            w.ship?.velocity?: VECTOR2F_ZERO) as MissileAPI
          proj.explode()
          w.ammo -= 1

          return KEY
        }
      }
      return null
    }
  }
}
open class aEP_m_s_era_shot : Effect(){
  companion object{
    const val WARHEAD_WEAPON_ID = "aEP_m_s_era2"
  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {
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
//era df1 爆反 爆炸反应装甲
class aEP_m_s_era3 :aEP_m_s_era(){}
class aEP_m_s_era3_shot : aEP_m_s_era_shot(){}

//可抛 幅容管
class aEP_m_s_fluxtube : aEP_m_m_fluxtube(){}
open class aEP_m_m_fluxtube :EveryFrame(){
  companion object{
    const val KEY = "aEP_m_m_fluxtube"
    var USE_THTRESHOLD = 0.5f
    var FLUX_REDUCE = 1000f
  }

  init {
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.toString().replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) USE_THTRESHOLD = num.replace("%","").toFloat()/100f
      if(i == 1) FLUX_REDUCE = num.toFloat()
      i += 1
    }
  }

  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    if (weapon.ship == null) {
      return
    }
    val ship = weapon.ship

    //不会下线
    aEP_Tool.keepWeaponAlive(weapon)


    //Global.getCombatEngine().addFloatingText(ship.getLocation(),  weapon.getCooldown()+"", 20f ,new Color(0, 100, 200, 240),ship, 0.25f, 120f);

    if (ship.fluxTracker.fluxLevel >= USE_THTRESHOLD && weapon.cooldownRemaining <= 0 && weapon.ammo > 0) {
      //aEP_m_s_fluxtube.simulateFireWeapon(weapon)
      weapon.setForceFireOneFrame(true)
    }else{
      weapon.setForceNoFireOneFrame(true)
    }
  }
}

//图钉 磁吸 集束 抛射架
class aEP_m_s_magnetmine_shot : Effect(){

  companion object{
    const val ID = "aEP_m_s_magnetmine_shot"
    const val WEAPON_ID = "aEP_m_s_magnetmine"
    var NUM = 6
    var DAMAGE = 500f
  }

  init{
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      //因为toInt()碰到空格会炸，toFloat()不会
      if(i == 0) NUM = num.toFloat().toInt()
      if(i == 1) DAMAGE = num.toFloat()
      i += 1
    }
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val missile = projectile as MissileAPI
    missile.angularVelocity = getRandomNumberInRange(-60f,60f)
    addEffect(ShipProximityTrigger(missile, 175f))
  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {
    val weaponId = aEP_ftr_ut_shendu_mine_shot::class.java.simpleName.replace("_shot","")
    val fakeWeapon = Global.getCombatEngine().createFakeWeapon(
      originalProjectile.source,
      weaponId)
    for ( i in 0 until NUM){
      val initialVel = getRandomPointInCircle(Vector2f(0f,0f), 500f)
      val mine = Global.getCombatEngine().spawnProjectile(
        originalProjectile.source,
        fakeWeapon,
        weaponId,
        explosion.location,
        MathUtils.getRandomNumberInRange(0f,360f),
        null) as MissileAPI
      aEP_ftr_ut_shendu_mine_shot().onFire(mine, fakeWeapon, Global.getCombatEngine(), weaponId)
      mine.velocity.set(initialVel)
      mine.damage.damage = DAMAGE
      mine.maxFlightTime = mine.maxFlightTime * MathUtils.getRandomNumberInRange(0.9f,1f)

    }

    val upper = aEP_MovingSprite(
      explosion.location,
      Vector2f(64f,64f),
      originalProjectile.facing - 90f,
      "aEP_FX.magnetmine_shell")
    upper.angleSpeed = getRandomNumberInRange(-30f,30f)
    upper.velocity = getRandomPointInCircle(VECTOR2F_ZERO,30f)
    upper.fadeIn = 0f
    upper.fadeOut = 0.25f
    upper.lifeTime = 4f
    addEffect(upper)

  }
}
class aEP_m_m_magnetmine_shot : Effect(){

  companion object{
    const val ID = "aEP_m_m_magnetmine_shot"
    const val WEAPON_ID = "aEP_m_m_magnetmine"
    var NUM = 6
    var DAMAGE = 500f
  }

  init{
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      //因为toInt()碰到空格会炸，toFloat()不会
      if(i == 0) NUM = num.toFloat().toInt()
      if(i == 1) DAMAGE = num.toFloat()
      i += 1
    }
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val missile = projectile as MissileAPI
    missile.angularVelocity = getRandomNumberInRange(-60f,60f)
    //val angleAndSpeed = aEP_Tool.velocity2Speed(projectile.velocity)
    //projectile.velocity.set(aEP_Tool.speed2Velocity(projectile.maxSpeed, angleAndSpeed.y))
    addEffect(ShipProximityTrigger(missile, 150f))
    if(missile.weapon?.spec?.weaponId?.equals("aEP_m_m_magnetmine") == true) {
      var flightTime = projectile.maxFlightTime
      var dist = 99999999999f
      if(Global.getCombatEngine().playerShip?.mouseTarget != null){
        dist = MathUtils.getDistance(Global.getCombatEngine().playerShip.mouseTarget, projectile.location)
      }
      val estimatedTime = (dist/missile.maxSpeed.coerceAtLeast(1f)).coerceAtLeast(0.8f)
      if(estimatedTime < flightTime){
        projectile.maxFlightTime = estimatedTime
      }

      addEffect(ShowHitLocation(missile, missile.maxFlightTime))
    }


  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {
    val weaponId = aEP_ftr_ut_shendu_mine_shot::class.java.simpleName.replace("_shot","")
    val fakeWeapon = Global.getCombatEngine().createFakeWeapon(
      originalProjectile.source,
      weaponId)
    for ( i in 0 until NUM){
      val initialVel = getRandomPointInCircle(Vector2f(0f,0f), 500f)
      val mine = Global.getCombatEngine().spawnProjectile(
        originalProjectile.source,
        fakeWeapon,
        weaponId,
        explosion.location,
        MathUtils.getRandomNumberInRange(0f,360f),
        null) as MissileAPI
      aEP_ftr_ut_shendu_mine_shot().onFire(mine, fakeWeapon, Global.getCombatEngine(), weaponId)
      mine.velocity.set(initialVel)
      mine.damage.damage = DAMAGE
      mine.maxFlightTime = mine.maxFlightTime * MathUtils.getRandomNumberInRange(0.9f,1f)

    }

    val upper = aEP_MovingSprite(
      explosion.location,
      Vector2f(64f,64f),
      originalProjectile.facing - 90f,
      "aEP_FX.magnetmine_shell")
    upper.angleSpeed = getRandomNumberInRange(-30f,30f)
    upper.velocity = getRandomPointInCircle(VECTOR2F_ZERO,30f)
    upper.fadeIn = 0f
    upper.fadeOut = 0.25f
    upper.lifeTime = 4f
    addEffect(upper)

  }
}
//用于导弹弹丸显示落点
class ShowHitLocation(val mine:MissileAPI, lifetime:Float):
  aEP_BaseCombatEffect(lifetime, mine){

  val estimatedHitLocation = Vector2f(0f,0f)

  override fun advanceImpl(amount: Float) {

    val angleAndSpeed = aEP_Tool.velocity2Speed(mine.velocity)
    val timeLeft = mine.maxFlightTime - mine.flightTime
    estimatedHitLocation.set(
      aEP_Tool.getExtendedLocationFromPoint(
        mine.location, angleAndSpeed.x, angleAndSpeed.y * timeLeft + 50f) )
  }

  override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
    val level = ((mine.maxFlightTime - mine.flightTime)/ (mine.maxFlightTime+0.1f)).coerceAtLeast(0f).coerceAtMost(1f)
    val rad = 80f

    //begin
    aEP_Render.openGL11CombatLayerRendering()

    val center = Vector2f(estimatedHitLocation)

    val width = 5f

    //控制弧线的精度，越多越贴近真弧形
    val  numOfVertex = 3
    //条纹的间隔多少度
    val angleStripe = 30
    var angle = 0f
    val minRad = 20f
    val largeRad = (rad - minRad) * level + minRad
    val smallRad = largeRad - width
    while (angle < 360){
      GL11.glBegin(GL11.GL_QUAD_STRIP)
      for (i in 0 until numOfVertex) {
        val pointFar = aEP_Tool.getExtendedLocationFromPoint(center, angle, largeRad)
        GL11.glColor4f(1f,0.2f,0.2f, 0.33f)
        GL11.glVertex2f(pointFar.x, pointFar.y)

        val pointNear = aEP_Tool.getExtendedLocationFromPoint(center, angle, smallRad)
        GL11.glColor4f(1f,1f,1f,0f)
        GL11.glVertex2f(pointNear.x, pointNear.y)
        angle += angleStripe/numOfVertex
      }
      GL11.glEnd()
      angle += angleStripe
    }
    aEP_Render.closeGL11()

  }
}

//相位空雷 水雷 破胎
class aEP_m_m_phasemine_shot : Effect(), AdvanceableListener{

  companion object{
    var MAX_PER_WEAPON = 50
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val mine = projectile as MissileAPI
    mine.spriteAlphaOverride = 0.5f
    mine.velocity.scale(getRandomNumberInRange(0.75f,1f))
    addEffect(ShipProximityTrigger(mine, 100f))

    //带的船越多，参与统计的listener越多，移除溢出导弹的速度越快
    if(!weapon.ship.hasListenerOfClass(this::class.java)){
      weapon.ship.addListener(aEP_m_m_phasemine_shot())
    }
  }

  //--------------------------------------------//
  // 以下为listener变量
  val checkTimer = IntervalUtil(1f,1f)
  override fun advance(amount: Float) {
    checkTimer.advance(amount)
    if(!checkTimer.intervalElapsed()) return

    var maxP = 100
    var maxE = 100
    for(s in Global.getCombatEngine().ships){
      for(w in s.allWeapons){
        if(w.spec.weaponId.equals(this.javaClass.simpleName.replace("_shot",""))){
          if(s.owner == 0 || s.isAlly) maxP += MAX_PER_WEAPON
          if(s.owner == 100) maxE += MAX_PER_WEAPON
        }
      }
    }

    var oldestP : MissileAPI? = null
    var oldestTimeP = 0f
    var numP = 0
    var oldestE : MissileAPI? = null
    var oldestTimeE = 0f
    var numE = 0
    for(m in Global.getCombatEngine().missiles){
      if(m.maxFlightTime - m.flightTime < 5f) continue
      if(m.projectileSpecId?.equals(this.javaClass.simpleName) == true){
        if(m.owner == 100){
          numE += 1
          if(m.flightTime > oldestTimeE){
            oldestE = m
            oldestTimeE = m.flightTime
          }
        }else{
          numP += 1
          if(m.flightTime > oldestTimeP){
            oldestP = m
            oldestTimeP = m.flightTime
          }
        }
      }
    }
    if(numE > maxE && oldestE != null){
      Global.getCombatEngine().removeEntity(oldestE)
    }
    if(numP > maxP && oldestP != null){
      Global.getCombatEngine().removeEntity(oldestP)
    }
  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI, weaponId: String) {
    val mine = Global.getCombatEngine().spawnProjectile(
      originalProjectile.source, originalProjectile.weapon,
      this.javaClass.simpleName.replace("_shot","") +"2",
      explosion.location,
      originalProjectile.facing,
      originalProjectile.velocity) as MissileAPI

    mine.angularVelocity = originalProjectile.angularVelocity
    mine.hitpoints = originalProjectile.hitpoints
  }

}
// 手动近炸引信，被最近的非战机激活
open class ShipProximityTrigger(val missile: MissileAPI, val fuseRange:Float): aEP_BaseCombatEffect(0f,missile) {

  val checkTimer = IntervalUtil(0.08f,0.12f)

  override fun advanceImpl(amount: Float) {
    checkTimer.advance(amount)
    if(!checkTimer.intervalElapsed()) return

    val closestShip : ShipAPI?= aEP_Tool.getNearestEnemyCombatShip(missile)
    if(closestShip != null){
      val dist = MathUtils.getDistance(closestShip, missile.location)
      if(dist <= fuseRange){
        missile.flightTime = missile.maxFlightTime
        shouldEnd = true
      }
    }

  }
}

//多重目标追踪 细流 光束 主炮
//对战机和导弹增加伤害的监听器写在1里面了，省的放2个监听器进去
class aEP_fga_xiliu_main : EveryFrame(), BeamEffectPluginWithReset, DamageDealtModifier{
  companion object{
    const val WEAPON_ID = "aEP_fga_xiliu_main"
    var DAMAGE = 100f
    var JUMP_TIME = 6f
    var JUMP_RANGE = 450f
  }

  init{
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      //因为toInt()碰到空格会炸，toFloat()不会
      if(i == 0) JUMP_RANGE = num.toFloat()
      if(i == 1) JUMP_TIME = num.toFloat()
      if(i == 2) DAMAGE = num.replace("%","").toFloat()
      i += 1
    }
  }

  var didEffect = false
  var didFire = false

  /**
   * beam的advance
   * */
  override fun advance(amount: Float, engine: CombatEngineAPI, beam: BeamAPI) {
    //第一次对某个目标造成伤害时，触发特效
    if(!beam.didDamageThisFrame() || beam.damageTarget == null) return

    if(!didEffect){
      didEffect = true
      onBeamHit(beam, engine)
    }

    val hitMissile = beam.damageTarget is MissileAPI
    val hitFighter = beam.damageTarget is ShipAPI && (beam.damageTarget as ShipAPI).isFighter
    if(beam.didDamageThisFrame() && (hitMissile || hitFighter)){

      //aEP_Combat.AddDamageReduction(0.25f, 0.5f, beam.damageTarget)
    }
  }

  override fun reset() {
    didEffect = false
  }

  /**
  * everyFrame的advance
  * */
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
    //刚部署时，把自己加入listener
    if(weapon.ship.fullTimeDeployed <= 0.1f){
      if(!weapon.ship.hasListenerOfClass(aEP_fga_xiliu_main::class.java)){
        weapon.ship.addListener(this)
      }
    }

    //不会下线
    aEP_Tool.keepWeaponAlive(weapon)

    if(weapon.isFiring){
      if(!didFire){
        onFire(weapon, engine)
        didFire = true
      }
    }else{
      didFire = false
    }

  }

  fun onFire( weapon: WeaponAPI, engine: CombatEngineAPI){
    val firePoint = weapon.getFirePoint(0)
    val facing = weapon.currAngle
    if (Global.getCombatEngine().viewport.isNearViewport(firePoint, 600f)) {

      val numParticles  = 20
      val minSize = 20f
      val maxSize = 50f
      val pc = Color(175,255,25,155)
      val minDur = 0.1f
      val maxDur = 0.5f
      val arc = 10f
      val scatter = 50f
      val minVel = 10f
      val maxVel = 100f
      val endSizeMin = 1f
      val endSizeMax = 2f
      val spawnPoint = Vector2f(firePoint)
      for (i in 0 until numParticles) {
        var angleOffset = Math.random().toFloat()
        if (angleOffset > 0.2f) {
          angleOffset *= angleOffset
        }
        var speedMult = 1f - angleOffset
        speedMult = 0.5f + speedMult * 0.5f
        angleOffset *= sign((Math.random().toFloat() - 0.5f))
        angleOffset *= arc / 2f
        val theta = Math.toRadians((facing + angleOffset).toDouble()).toFloat()
        val r = (Math.random() * Math.random() * scatter).toFloat()
        val x = cos(theta.toDouble()).toFloat() * r
        val y = sin(theta.toDouble()).toFloat() * r
        val pLoc = Vector2f(spawnPoint.x + x, spawnPoint.y + y)
        var speed = minVel + (maxVel - minVel) * Math.random().toFloat()
        speed *= speedMult
        val pVel = Misc.getUnitVectorAtDegreeAngle(Math.toDegrees(theta.toDouble()).toFloat())
        pVel.scale(speed)
        Vector2f.add(weapon?.ship?.velocity?:Misc.ZERO, pVel, pVel)
        val pSize = minSize + (maxSize - minSize) * Math.random().toFloat()
        val pDur = minDur + (maxDur - minDur) * Math.random().toFloat()
        val endSize = endSizeMin + (endSizeMax - endSizeMin) * Math.random().toFloat()
        Global.getCombatEngine().addNebulaParticle(pLoc, pVel, pSize, endSize, 0.1f, 0.5f, pDur, pc)
      }

      //开火的瞬间爆闪
      Global.getCombatEngine().addSmoothParticle(
        firePoint,
        weapon.ship?.velocity?:Misc.ZERO,
        200f,1f,0f,0.2f,Color(245,215,126))

      //闪光
      Global.getCombatEngine().addSmoothParticle(
        firePoint,
        weapon.ship?.velocity?:Misc.ZERO,
        200f,1f,0.1f,0.5f,Color.green)

      val angleL = weapon.currAngle + 165f
      val angleR = weapon.currAngle - 165f
      val bpl = getExtendedLocationFromPoint(firePoint, angleL, 50f)
      val bpr = getExtendedLocationFromPoint(firePoint, angleR, 50f)

      //后座焰L
      Global.getCombatEngine().spawnExplosion(
        bpl,
        Vector2f.add(speed2Velocity(angleL, 10f),weapon.ship?.velocity?:Misc.ZERO,null),
        Color(240,160,20), 20f, 0.6f)
      //闪光
      Global.getCombatEngine().addSmoothParticle(
        bpl,
        weapon.ship?.velocity?:Misc.ZERO,
        150f,1f,0.1f,0.1f,Color(255,215,50))

      //后座焰R
      Global.getCombatEngine().spawnExplosion(
        bpr,
        Vector2f.add(speed2Velocity(angleR, 10f),weapon.ship?.velocity?:Misc.ZERO,null),
        Color(240,160,20), 20f, 0.6f)
      //闪光
      Global.getCombatEngine().addSmoothParticle(
        bpr,
        weapon.ship?.velocity?:Misc.ZERO,
        150f,1f,0f,0.1f,Color(255,215,50))
    }

  }

  fun onBeamHit( beam: BeamAPI, engine: CombatEngineAPI){
    val point = beam.to

    val candidate = LinkedList<CombatEntityAPI>()
    val currPoint = Vector2f(point)
    val picker = WeightedRandomPicker<CombatEntityAPI>()
    while (candidate.size < JUMP_TIME){
      var foundAny = false
      picker.clear()
      for(s in engine.ships){
        if(aEP_Tool.isShipTargetable(s,
            false,
            true,
            true,
            false,
            true))
        if(aEP_Tool.isDead(s)) continue
        if(!aEP_Tool.isEnemy(beam.source, s)) continue
        if(beam.damageTarget == s) continue
        if(candidate.contains(s)) continue
        val distSq = getDistanceSquared(s,currPoint)
        if(distSq > JUMP_RANGE.pow(2)) continue
        if(!s.isFighter){
          picker.add(s,0.000001f)
          foundAny = true
        }else{
          picker.add(s,1000000f)
          foundAny = true
        }
      }
      for(m in engine.missiles){
        if(m.collisionClass == CollisionClass.NONE) continue
        if(!aEP_Tool.isEnemy(beam.source, m)) continue
        if(candidate.contains(m)) continue
        if(beam.damageTarget == m) continue
        val distSq = getDistanceSquared(m,currPoint)
        if(distSq > JUMP_RANGE.pow(2)) continue
        picker.add(m,1000000f)
        foundAny = true
      }
      if(!foundAny) break
      val toAdd = picker.pick()
      currPoint.set(toAdd.location)
      candidate.add(toAdd)
    }

    val damage = DAMAGE
    for( i in 0 until candidate.size){
      val target = candidate[i]
      if(i == 0){
        val effect = empArcDelayed(
          beam.to, target,
          beam.source, target,
          0.2f*i + 0.1f,
          beam.coreColor, beam.fringeColor, damage)
        addEffect(effect)
      }
      else{
        val effect = empArcDelayed(
          candidate[i-1].location, candidate[i-1],
          beam.source, target,
          0.2f*i + 0.1f,
          beam.coreColor, beam.fringeColor, damage)
        addEffect(effect)
      }
    }

  }

  override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI?, point: Vector2f?, shieldHit: Boolean): String? {
    if(param is BeamAPI){
      if(param.weapon.spec.weaponId.contains(WEAPON_ID)){
        param.damage.isForceHardFlux = true
        //模式2时对战机导弹提高伤害
        if (param.weapon.spec.weaponId.contains(WEAPON_ID+"2")){
          if((target is ShipAPI && target.isFighter) || target is MissileAPI ){
            param.damage.modifier.modifyPercent(WEAPON_ID, aEP_fga_xiliu_main2.DAMAGE_BONUS_PERCENT)
            return WEAPON_ID
          }
        }
      }
    }
    return null
  }
}
class empArcDelayed(val startPoint: Vector2f,
                    val start: CombatEntityAPI,
                    val source: ShipAPI ,
                    val end: CombatEntityAPI,
                    lifeTime: Float,
                    val coreColor:Color,
                    val fringeColor: Color,
                    val damageAmout: Float): aEP_BaseCombatEffect(lifeTime, end){
  override fun advanceImpl(amount: Float) {

    if(time >= lifeTime){
      var dist = MathUtils.getDistance(start.location, end.location)
      dist = dist.coerceAtMost(aEP_fga_xiliu_main.JUMP_RANGE + end.collisionRadius)
      val end2StartFacing = VectorUtils.getAngle(end.location, start.location)
      val startPoint = aEP_Tool.getExtendedLocationFromPoint(end.location,end2StartFacing,dist)


      Global.getCombatEngine().spawnEmpArc(
        source,
        startPoint,
        null,
        end,
        DamageType.ENERGY,
        damageAmout,
        1f,
        9999f,
        "tachyon_lance_emp_impact",
        40f,
        fringeColor,
        coreColor)

      Global.getCombatEngine().spawnEmpArcVisual(
        startPoint,
        null,
        end.location,
        end,
        36f,
        fringeColor,
        coreColor)
    }
  }

  override fun readyToEnd() {

  }
}
//对战机和导弹增加伤害的监听器写在1里面了，省的放2个监听器进去
class aEP_fga_xiliu_main2 : EveryFrame(), BeamEffectPluginWithReset{
  companion object{
    const val WEAPON_ID = "aEP_fga_xiliu_main2"
    var DAMAGE_BONUS_PERCENT = 100f
  }
  init{
    val hlString = Global.getSettings().getWeaponSpec(this.javaClass.simpleName.replace("_shot","")).customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      //因为toInt()碰到空格会炸，toFloat()不会
      if(i == 0) DAMAGE_BONUS_PERCENT = num.replace("%","").toFloat()
      i += 1
    }
  }

  var didEffect = false
  var didFire = false
  var smokeTime = 0f
  var smokeTracker = IntervalUtil(0.05f,0.05f)

  /**
   * beam的advance
   * */
  override fun advance(amount: Float, engine: CombatEngineAPI, beam: BeamAPI) {
    //第一次对某个目标造成伤害时，触发特效
    if(!beam.didDamageThisFrame() || beam.damageTarget == null) return

    if(!didEffect){
      didEffect = true
      onBeamHit(beam, engine)
    }

    val hitMissile = beam.damageTarget is MissileAPI
    val hitFighter = beam.damageTarget is ShipAPI && (beam.damageTarget as ShipAPI).isFighter
    if(beam.didDamageThisFrame() && (hitMissile || hitFighter)){

      //aEP_Combat.AddDamageReduction(0.25f, 0.5f, beam.damageTarget)
    }
  }

  override fun reset() {
    didEffect = false
  }

  /**
   * everyFrame的advance
   * */
  override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {

    //不会下线
    aEP_Tool.keepWeaponAlive(weapon)

    if(weapon.isFiring){
      smokeTime += amount
      if(!didFire){
        onFire(weapon, engine)
        didFire = true
      }
    }else{
      smokeTime -= amount*0.5f
      didFire = false
    }

    smokeTime -= amount
    smokeTime = smokeTime.coerceAtLeast(0f).coerceAtMost(2f)
    val smokeLevel = (smokeTime/1f).coerceAtMost(1f)

    if (Global.getCombatEngine().viewport.isNearViewport(weapon.location, 800f) && smokeLevel > 0f) {
      val angleL = weapon.currAngle + 90f
      val angleR = weapon.currAngle - 90f
      val bpl = getExtendedLocationFromPoint(weapon.location, angleL, 12f)
      val bpr = getExtendedLocationFromPoint(weapon.location, angleR, 12f)

      val glowC = aEP_Tool.getColorWithAlpha(Color(255,90,50),smokeLevel)
      //闪光
      Global.getCombatEngine().addSmoothParticle(
        bpl,
        weapon.ship?.velocity?:Misc.ZERO,
        30f,1f,0.1f,amount*2f, glowC)
      //闪光
      Global.getCombatEngine().addSmoothParticle(
        bpr,
        weapon.ship?.velocity?:Misc.ZERO,
        30f,1f,0.1f,amount*2f, glowC)


      //喷烟
      smokeTracker.advance(amount)
      if(smokeTracker.intervalElapsed()){
        val smokeC = aEP_Tool.getColorWithAlpha(aEP_WeaponReset.SMOKE_EMIT_COLOR,smokeLevel * 0.8f)

        //在激活时，从排幅口喷出短的烟雾
        val smokeL = aEP_MovingSmoke(bpl)
        smokeL.lifeTime = 0.25f
        smokeL.fadeIn = 0.5f
        smokeL.fadeOut = 0.5f
        smokeL.size = 25f
        smokeL.sizeChangeSpeed = 25f
        smokeL.color = smokeC
        smokeL.setInitVel(speed2Velocity(angleL, 200f))
        smokeL.setInitVel(weapon.ship?.velocity?:Misc.ZERO)
        smokeL.stopForceTimer.setInterval(0.05f, 0.05f)
        smokeL.stopSpeed = 0.975f
        addEffect(smokeL)

        val smokeR = aEP_MovingSmoke(bpr)
        smokeR.lifeTime = 0.25f
        smokeR.fadeIn = 0.5f
        smokeR.fadeOut = 0.5f
        smokeR.size = 25f
        smokeR.sizeChangeSpeed = 25f
        smokeR.color = smokeC
        smokeR.setInitVel(speed2Velocity(angleR, 200f))
        smokeR.setInitVel(weapon.ship?.velocity?:Misc.ZERO)
        smokeR.stopForceTimer.setInterval(0.05f, 0.05f)
        smokeR.stopSpeed = 0.975f
        addEffect(smokeR)


      }
    }



  }

  fun onFire( weapon: WeaponAPI, engine: CombatEngineAPI){
    val firePoint = weapon.getFirePoint(0)
    val facing = weapon.currAngle
    smokeTime += 0.5f
  }

  fun onBeamHit( beam: BeamAPI, engine: CombatEngineAPI){

  }
}

//离岸流 榴弹抛射 炸弹 bt
class aEP_des_lianliu_grenade_thrower_shot: Effect(), AdvanceableListener, DamageDealtModifier{
  companion object{
    const val ID = "aEP_des_lianliu_grenade_thrower_shot"
    const val DAMAGE_STREAK_PUNISH = 0.96f
    const val PUNISH_DECREASE_SPEED = 12f
  }

  val hitMap = LinkedHashMap<ShipAPI, Float>()
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    val missile = projectile as MissileAPI
    missile.maxFlightTime *= getRandomNumberInRange(0.5f,1f)
    missile.velocity.scale(getRandomNumberInRange(0.75f,1f))

//    if(weapon.ship is ShipAPI){
//      weapon.ship.addListener(this)
//    }

    val sizeMult = 1f
    val shipVel = weapon.ship?.velocity?:Misc.ZERO
    //考虑到这是6个口齐发，每次闪光的亮度调小
    //炮口焰
    Global.getCombatEngine().spawnExplosion(
      projectile.location,
      Vector2f.add(speed2Velocity(projectile.facing, 60f),shipVel,null),
      Color(240,110,20,25), 75f * sizeMult, 0.3f)
    //闪光
    Global.getCombatEngine().addSmoothParticle(
      projectile.location,
      shipVel,
      600f,1f,0f,0.6f,Color(255,215,50,4))

  }

  //缓慢降低hitMap
  override fun advance(amount: Float) {
    val shouldRemove = ArrayList<ShipAPI>()
    val it = hitMap.iterator()
    while (it.hasNext()){
      val t = it.next()
      if(t.value > 0f){
        t.setValue(t.value - amount * PUNISH_DECREASE_SPEED)
      }

      //如果递减一次以后value小于0了，准备移除
      if(t.value <= 0.1f){
        shouldRemove.add(t.key)
      }
    }

    //移除value小于0的key
    for(s in shouldRemove){
      hitMap.remove(s)
    }
  }

  //每次某个舰船被本舰船的弹丸命中，在hitMap上记1，短时间内连续中弹hitMap会快速增加
  override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
    if(target is ShipAPI){
      if(param is DamagingProjectileAPI && param.projectileSpecId?.equals(ID) == true
        || param is DamagingExplosion && param.customData[EXPLOSION_PROJ_ID_KEY]?.equals(ID) == true){
        if(hitMap.contains(target)){
          hitMap[target] = hitMap[target] as Float  + 1f
        }else{
          hitMap[target] = 1f
        }
        val hitted = ((hitMap[target]?: 1f) - 1f).coerceAtLeast(0f)

        val damageMult =  DAMAGE_STREAK_PUNISH.pow(hitted.toInt())
        damage.modifier.modifyMult(ID, damageMult)
        return ID
      }
    }
    return null
  }
}
//用来实时追踪missile的flightTime和maxFlightTime
class debug(val m:MissileAPI ): aEP_BaseCombatEffect(0f,m){
  override fun advanceImpl(amount: Float) {
    aEP_Tool.addDebugLog(m.flightTime.toString())
    aEP_Tool.addDebugLog(m.maxFlightTime.toString())

  }
}


//战机武器无视初速度，部分战机太快同时安装的武器弹道太慢
//铅垂
class aEP_ftr_ftr_hvfighter_gun_shot: Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    //消除初速度
    projectile.velocity.set(aEP_Tool.ignoreShipInitialVel(projectile, weapon.ship.velocity))
  }
}
//进军
class aEP_ftr_bom_attacker_gun_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI, weaponId: String) {
    weapon.ship?:return
    projectile.velocity.set(aEP_Tool.ignoreShipInitialVel(projectile, weapon.ship.velocity))
  }

}