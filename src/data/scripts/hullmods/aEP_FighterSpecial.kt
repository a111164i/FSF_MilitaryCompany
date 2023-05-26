package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.*
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Stats.EXPLOSION_DAMAGE_MULT
import com.fs.starfarer.api.impl.campaign.ids.Stats.EXPLOSION_RADIUS_MULT
import com.fs.starfarer.api.impl.combat.PhaseCloakStats.SHIP_ALPHA_MULT
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import combat.impl.VEs.aEP_MovingSmoke
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.angleAdd
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import data.scripts.util.MagicAnim
import data.scripts.util.MagicLensFlare
import data.scripts.util.MagicRender
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_FighterSpecial: BaseHullMod() {

  var hullmod : HullModEffect? = null

  override fun init(spec: HullModSpecAPI?) {
    //找到实际对应的代码存入hullmod变量
    //classForName查不到就保持null
    val id = spec?.id
    try {
      val e = Class.forName(aEP_FighterSpecial::class.java.getPackage().name + "." + id)
      hullmod = e.newInstance() as HullModEffect
      Global.getLogger(this.javaClass).info("aEP_FighterSpecialLoaded :" +e.name)
    } catch (e: ClassNotFoundException) {
      e.printStackTrace()
    } catch (e: InstantiationException) {
      e.printStackTrace()
    } catch (e: IllegalAccessException) {
      e.printStackTrace()
    }

  }

  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
    hullmod?: return
    hullmod!!.applyEffectsBeforeShipCreation(hullSize,stats,id)
  }

  override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {
    hullmod?: return
    hullmod!!.applyEffectsAfterShipCreation(ship, id)
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String {
    hullmod?: return ""
    hullmod!!.getDescriptionParam(index, hullSize)
    return ""
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?, ship: ShipAPI?): String {
    hullmod?: return ""
    hullmod!!.getDescriptionParam(index, hullSize, ship)
    return ""
  }

  override fun applyEffectsToFighterSpawnedByShip(fighter: ShipAPI?, ship: ShipAPI?, id: String?) {
    hullmod?:return
    hullmod!!.applyEffectsToFighterSpawnedByShip(fighter, ship, id)
  }

  override fun isApplicableToShip(ship: ShipAPI?): Boolean {
    hullmod?:false
    hullmod!!.isApplicableToShip(ship)
    return false
  }

  override fun getUnapplicableReason(ship: ShipAPI?): String {
    hullmod?: return ""
    hullmod!!.getUnapplicableReason(ship)
    return ""
  }

  /**
   * ship may be null from autofit.
   * @param ship
   * @param marketOrNull
   * @param mode
   * @return
   */
  override fun canBeAddedOrRemovedNow(ship: ShipAPI?, marketOrNull: MarketAPI?, mode: CampaignUIAPI.CoreUITradeMode?): Boolean {
    hullmod?: return false
    hullmod!!.canBeAddedOrRemovedNow(ship, marketOrNull, mode)
    return false
  }

  override fun getCanNotBeInstalledNowReason(ship: ShipAPI?, marketOrNull: MarketAPI?, mode: CampaignUIAPI.CoreUITradeMode?): String {
    hullmod?: return ""
    hullmod!!.getCanNotBeInstalledNowReason(ship, marketOrNull, mode)
    return ""
  }

  /**
   * Not called while paused.
   * But, called when the fleet data needs to be re-synced,
   * with amount=0 (such as if, say, a fleet member is moved around.
   * in the fleet screen.)
   * @param member
   * @param amount
   */
  override fun advanceInCampaign(member: FleetMemberAPI?, amount: Float) {
    hullmod?:return
    hullmod!!.advanceInCampaign(member, amount)
  }

  /**
   * Not called while paused.
   * @param ship
   * @param amount
   */
  override fun advanceInCombat(ship: ShipAPI?, amount: Float) {
    hullmod?:return
    hullmod!!.advanceInCombat(ship,amount)
  }

  /**
   * Hullmods that return true here should only ever be built-in, as cost changes aren't handled when
   * these mods can be added or removed to/from the variant.
   * @return
   */
  override fun affectsOPCosts(): Boolean {
    return false
    return hullmod!!.affectsOPCosts()
    return false
  }

  /**
   * ship may be null, will be for modspecs. hullsize will always be CAPITAL_SHIP for modspecs.
   * @param hullSize
   * @param ship
   * @param isForModSpec
   * @return
   */
  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize?, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return false
    return hullmod!!.shouldAddDescriptionToTooltip(hullSize, ship, isForModSpec)
    return false
  }

  /**
   * ship may be null, will be for modspecs. hullsize will always be CAPITAL_SHIP for modspecs.
   * @param tooltip
   * @param hullSize
   * @param ship
   * @param width
   * @param isForModSpec
   */
  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI?, hullSize: ShipAPI.HullSize?, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    hullmod?: return
    hullmod!!.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec)
  }

  override fun getBorderColor(): Color? {
    hullmod?: return null
    return hullmod!!.borderColor
    return Color(255,255,255)
  }

  override fun getNameColor(): Color? {
    hullmod?: return null
    return hullmod!!.nameColor
    return Color(255,255,255)
  }

  /**
   * Sort order within the mod's display category. Not used when category == 4, since then
   * the order is determined by the order in which the player added the hullmods.
   * @return
   */
  override fun getDisplaySortOrder(): Int {
    hullmod?:return 99
    return hullmod!!.displaySortOrder
    return 99
  }

  /**
   * Should return 0 to 4; -1 for "use default".
   * The default categories are:
   * 0: built-in mods in the base hull
   * 1: perma-mods that are not story point mods
   * 2: d-mods
   * 3: mods built in via story points
   * 4: regular mods
   *
   * @return
   */
  override fun getDisplayCategoryIndex(): Int {
    hullmod?:return 4
    return hullmod!!.displayCategoryIndex
  }

}

//锚点无人机护盾插件
class aEP_MaoDianShield : aEP_BaseHullMod() {
  val ID = "aEP_MaoDianShield"
  val TIME_TO_EXTEND = 1f
  //ship文件里的护盾半径也要改，否则护盾中心在屏幕外的时候不会渲染护盾
  val MAX_SHIELD_RADIUS = Global.getSettings().getHullSpec("aEP_ftr_ut_maodian").shieldSpec.radius
  val MAX_MOVE_TIME = 12f
  val FLUX_INCREASE = 100f
  val RADAR_SPEED = -90f
  val EXPLODSION_DAMAGE_MULT = 0.005f
  val EXPLODSION_RANGE_MULT = 0.5f

  /**
   * 使用这个
   **/
  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship.mutableStats.empDamageTakenMult.modifyMult(this.ID,0f)
    if(!ship.hasListenerOfClass(ShieldListener::class.java)){
      ship.addListener(ShieldListener(ship))
    }
    ship.mutableStats.dynamic.getStat(EXPLOSION_DAMAGE_MULT).modifyMult(id,EXPLODSION_DAMAGE_MULT)
    ship.mutableStats.dynamic.getStat(EXPLOSION_RADIUS_MULT).modifyMult(id,EXPLODSION_RANGE_MULT)
  }

  inner class ShieldListener(val ship: ShipAPI) : AdvanceableListener, DamageTakenModifier, HullDamageAboutToBeTakenListener{
    var time = 0f
    var shieldTime =0f
    var moveTime = 0f
    val empArcTracker = IntervalUtil(0.1f,0.6f)
    val particleTracker = IntervalUtil(0.1f,0.2f)

    var shouldEnd = false
    override fun advance(amount: Float) {

      time = MathUtils.clamp(time + aEP_Tool.getAmount(ship),0f,999f)
      val shieldLevel = shieldTime/TIME_TO_EXTEND
      val fluxLevel = MathUtils.clamp(ship.fluxLevel,0f,1f)

      //如果有盾，就产生效果
      if(ship.shield != null){
        //改变盾大小，和只改radius冲突，需要每帧调用，别动最好，因为盾的外圈是最先渲染的，动态调整会影响ring的贴图
        /*
        val rad = MAX_SHIELD_RADIUS * MagicAnim.smooth(shieldLevel)
        ship.shield?.setRadius(rad,
          Global.getSettings().getSpriteName("aEP_hullstyle","aEP_shield_inner03"),
          Global.getSettings().getSpriteName("aEP_hullstyle","aEP_shield_outer03"))
        //顺便改变船碰撞圈的大小
        ship.collisionRadius = MathUtils.clamp(rad,40f,MAX_SHIELD_RADIUS)
         */
        //盾颜色
        ship.shield.innerColor = Color(0.5f+0.5f*fluxLevel,
          0.5f,
          0.65f*(1f-fluxLevel),
          (0.2f * shieldLevel * shieldLevel) + MagicAnim.smooth((0.35f*(1f-fluxLevel))))

        //若开盾强制增加软幅能，增加护盾时间。若不开盾增加机动时间，减少护盾时间，机动超时后快速涨幅能
        if(ship.shield?.isOn == true){
          shieldTime = MathUtils.clamp(shieldTime + aEP_Tool.getAmount(ship),0f,TIME_TO_EXTEND)
          ship.fluxTracker.increaseFlux((FLUX_INCREASE+ship.mutableStats.fluxDissipation.modifiedValue)*amount,false)
          for(w in ship.allWeapons){
            if(w.spec.weaponId == "aEP_ftr_ut_maodian_glow"){
              //注意一下这3个存进去的类并不是初始化的时候就一起初始化的
              (w.effectPlugin as aEP_DecoAnimation).decoGlowController.toLevel = 1f
            }
          }
        }else{
          shieldTime = MathUtils.clamp(shieldTime - aEP_Tool.getAmount(ship),0f,TIME_TO_EXTEND)
          moveTime = MathUtils.clamp(moveTime + aEP_Tool.getAmount(ship),0f,MAX_MOVE_TIME)
          if(moveTime >= MAX_MOVE_TIME){
            ship.fluxTracker.increaseFlux((ship.fluxTracker.maxFlux * 0.2f + ship.mutableStats.fluxDissipation.modifiedValue)*amount,false)
          }
          for(w in ship.allWeapons){
            if(w.spec.weaponId == "aEP_ftr_ut_maodian_glow") {
              //注意一下这3个存进去的类并不是初始化的时候就一起初始化的，在开战第一帧call会报空
              (w.effectPlugin as aEP_DecoAnimation)?.decoGlowController?.toLevel = 0f
            }
          }
        }
      }

      //旋转雷达
      for(w in ship.allWeapons){
        if(w.spec.weaponId == "aEP_ftr_ut_maodian_radar"){
          w.currAngle = (w.currAngle + amount * RADAR_SPEED)
        }
      }

     /* //生成粒子
      if(ship.shield?.isOn == true && shieldLevel >= 0.35f){
        particleTracker.advance(amount)
        if(particleTracker.intervalElapsed()){
          val shieldRad = MAX_SHIELD_RADIUS*shieldLevel
          val num = 4
          var i = 0
          val angleChange = 360f/num
          while (i < num){
            val angle = MathUtils.getRandomNumberInRange(i * angleChange,i * angleChange + angleChange)
            val range = MathUtils.getRandomNumberInRange(ship.collisionRadius,shieldRad * 1f / 6f)
            val moveDist = shieldRad - range
            val point = aEP_Tool.getExtendedLocationFromPoint(ship.location,angle,range)
            Global.getCombatEngine().addSmoothParticle(point,
              aEP_Tool.Speed2Velocity(angle,moveDist * 1.5f),
              10f,
              1f,
              0.5f,
              aEP_Tool.getColorWithAlphaChange(ship.shield.innerColor,3f))
            i ++
          }
        }
      }*/

      /*//幅能快满的时候泛红
      if(fluxLevel > 0.35f){
        ship.isJitterShields = false
        var intense = (fluxLevel-0.35f)/0.65f
        intense *= intense
        ship.setJitter(id,Color(250,100,100,(255*intense).toInt()),intense,4,1f)

        //大于0.65开始漏电
        empArcTracker.advance(amount * (fluxLevel * 0.6f + 0.75f))
        if(empArcTracker.intervalElapsed() && fluxLevel > 0.65f){
          val from = MathUtils.getRandomPointInCircle(ship.location, 10f)
          val angle = VectorUtils.getAngle(ship.location,from)
          Global.getCombatEngine().spawnEmpArcVisual(
            from,
            ship,
            MathUtils.getRandomPointInCone(ship.location,10f + 70f,aEP_Tool.angleAdd(angle,-15f),aEP_Tool.angleAdd(angle,15f)),
            ship,
            1f,
            Color.magenta,
            Color.white)
        }

      }*/

      //如果本帧幅能满了，准备自毁
      if(ship.fluxLevel > 0.98f){
        shouldEnd = true
      }

      //自毁
      if(shouldEnd){
        val suppressBefore = Global.getCombatEngine()?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages
        Global.getCombatEngine()?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages = true
        ship.collisionRadius = 40f
        Global.getCombatEngine().applyDamage(
          ship,
          ship.location,
          (ship.hitpoints + ship.hullSpec.armorRating) * 5f,
          DamageType.HIGH_EXPLOSIVE,
          0f,
          true,
          false,
          ship)
        Global.getCombatEngine().removeEntity(ship)
        Global.getCombatEngine()?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages = suppressBefore?:false
      }
    }

    /**
     * Modifications to damage should ONLY be made using damage.getModifier().
     *
     * param can be:
     * null
     * DamagingProjectileAPI
     * BeamAPI
     * EmpArcEntityAPI
     * Something custom set by a script
     *
     * @return the id of the stat modification to damage.getModifier(), or null if no modification was made
     */
    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI?, point: Vector2f?, shieldHit: Boolean): String? {
      param?:return null
      if(!shieldHit) return null
      //造成软幅能的伤害，和软幅能光束，都不计入
      if(damage?.isSoftFlux?: true) return null
      if(param is BeamAPI && !damage!!.isForceHardFlux) return null
      var fluxToSheild = (damage?.damage?: 0.01f) * (damage?.modifier?.modifiedValue ?:1f)
      val damageMap = HashMap<DamageType,Float >()
      damageMap.put(DamageType.HIGH_EXPLOSIVE,1f)
      damageMap.put(DamageType.ENERGY,1f)
      damageMap.put(DamageType.KINETIC, 1f)
      damageMap.put(DamageType.FRAGMENTATION,0.25f)
      damageMap.put(DamageType.OTHER,0.5f)
      fluxToSheild *= (damageMap[damage?.type ?: DamageType.FRAGMENTATION] ?: 1f)
      fluxToSheild *= ship.mutableStats.shieldAbsorptionMult.computeMultMod() * ship.hullSpec.shieldSpec.fluxPerDamageAbsorbed
      var softFlux = ship.fluxTracker.currFlux - ship.fluxTracker.hardFlux
      val afterHitFlux = softFlux - fluxToSheild
      if(afterHitFlux >=0){
        ship.fluxTracker.decreaseFlux(fluxToSheild)
        ship.fluxTracker.increaseFlux(fluxToSheild,true)
        damage?.modifier?.modifyMult(ID,0f)
       // Global.getLogger(this.javaClass).info(fluxToSheild)
      }else{
        val percent = MathUtils.clamp((-afterHitFlux)/fluxToSheild,0f,1f)
        ship.fluxTracker.decreaseFlux(softFlux)
        ship.fluxTracker.increaseFlux(fluxToSheild,true)
        damage?.modifier?.modifyMult(ID,percent)
      }
      return ID
    }

    /**
     * if true is returned, the hull damage to be taken is negated.
     * @param param
     * @param ship
     * @param point
     * @param damageAmount
     * @return
     */
    override fun notifyAboutToTakeHullDamage(param: Any?, ship: ShipAPI?, point: Vector2f?, damageAmount: Float): Boolean {
      if(damageAmount > ship?.hitpoints?: 999999f){
        shouldEnd = true
        return true
      }
      return false
    }
  }
}

//巡洋导弹引信插件
open class aEP_CruiseMissile : BaseHullMod() {
  companion object{
    const val id = "aEP_CruiseMissile"
    const val ROTATE_VELOCITY_DEGREE_PER_SECOND = 5f
  }
  open class ExplodeListener : AdvanceableListener{
    companion object{
      const val CENTER_DAMAGE = 1000f
      const val FUSE_RANGE = 150f
      const val FUSE_DELAY= 1f
    }

    lateinit var ship:ShipAPI
    private var state : State

    constructor(ship: ShipAPI){
      this.ship = ship
      this.state = SetOff(ship)
    }

    open inner class State {
      lateinit var ship: ShipAPI
      var timeEclipsed = 0f
      constructor(ship: ShipAPI){
        this.ship = ship
      }
      open fun advance(amount: Float){
        advanceImpl(amount)
        timeEclipsed += amount
      }
      open fun advanceImpl(amount: Float){}
    }

    inner class Exploding(ship: ShipAPI) : State(ship) {
      override fun advanceImpl(amount: Float){
        val level = MathUtils.clamp(timeEclipsed/ FUSE_DELAY,0f,1f)
        ship.setJitter("aEP_Exploding",Color(255,20,20,(250*level).toInt()),1f,2,5f)
        ship.mutableStats.maxSpeed.modifyMult(id,0.2f)
        if(level >= 0.99f){
          explode(ship)
        }
      }

      private fun explode(ship: ShipAPI){
        val point = ship.location
        val vel = Vector2f(0f, 0f)
        val engine = Global.getCombatEngine()

        //中心点大白光
        engine.addHitParticle(
          point,
          vel, 400f, 1f,
          Color.white
        )

        //随机烟雾
        var SIZE_MULT = 3f
        var numMax = 24
        var num = 1
        while (num <= numMax) {
          val loc = MathUtils.getRandomPointInCircle(point, 150f * SIZE_MULT)
          engine.addNebulaParticle(
            loc, Vector2f(0f,0f),200f*SIZE_MULT, 1.5f,
            0f,0.5f, 4f+2f*MathUtils.getRandomNumberInRange(0f,1f),
            Color(100, 100, 100, 55))
          num++
        }

        //内圈几乎不动的环状烟雾
        SIZE_MULT = 3f
        numMax = 16
        var angle = 0f
        for (i in 0 until numMax) {
          val loc = getExtendedLocationFromPoint(point, angle, 100f * SIZE_MULT)
          val endSizeMult = 1.25f
          val sizeAtMin = 200 * SIZE_MULT
          val moveSpeed = 10f * SIZE_MULT
          val vel = aEP_Tool.speed2Velocity(angle,moveSpeed)
          engine.addNebulaParticle(
            loc,vel,sizeAtMin, endSizeMult,
            0.1f,1f, 2f,
            Color(100, 100, 100, 255))
          angle += 360f / numMax
        }

        //向外扩散的环状烟雾
        SIZE_MULT = 3f
        numMax = 24
        angle = 0f
        for (i in 0 until numMax) {
          val loc = getExtendedLocationFromPoint(point, angle, 150f * SIZE_MULT)
          val endSizeMult = 1.25f
          val sizeAtMin = 175 * SIZE_MULT
          val moveSpeed = 60f * SIZE_MULT
          val vel = aEP_Tool.speed2Velocity(angle,moveSpeed)
          engine.addNebulaParticle(
            loc,vel,sizeAtMin, endSizeMult,
            0.1f,0.1f, 1f,
            Color(100, 100, 100, 175))
          angle += 360f / numMax
        }

        //生成弹丸
        val numOfProj = 160
        val spreadPerProj = 18f
        angle = 0f
        for (i in 0 until  numOfProj) {
          angle = angleAdd(angle, MathUtils.getRandomNumberInRange(spreadPerProj*0.5f, spreadPerProj*1.5f))
          val pro1 = engine.spawnProjectile(
            ship,  //source ship
            ship.allWeapons[0],  //source weapon,
            "aEP_cruise_missile_shot",  //whose proj to be use
            getExtendedLocationFromPoint(point, angle, 100f),  //loc
            angle,  //facing
            null)
          val speedRandom = MathUtils.getRandomNumberInRange(0.5f, 1f)
          pro1.velocity.scale(speedRandom)
          num++
        }

        //play sound
        Global.getSoundPlayer().playSound(
          "aEP_RW_hit",
          0.25f, 2f,  // pitch,volume
          ship.location,  //location
          Vector2f(0f, 0f)
        ) //velocity

        //中心点爆炸
        val spec = DamagingExplosionSpec(
          1f,
          1000f,
          FUSE_RANGE,
          CENTER_DAMAGE,
          CENTER_DAMAGE/2f,
          CollisionClass.MISSILE_FF,  //by ship
          CollisionClass.MISSILE_NO_FF,  //by fighter
          0f, 0f, 0f, 0,
          Color.white, Color.white
        )
        spec.damageType = DamageType.HIGH_EXPLOSIVE
        engine.spawnDamagingExplosion(spec, ship, point)
        engine.combatNotOverFor = engine.combatNotOverFor + 2f
        engine.removeEntity(ship)

      }
    }

    inner class ApproximatePrimer(ship: ShipAPI) : State(ship) {
      override fun advanceImpl(amount: Float){
        val sprite = Global.getSettings().getSprite("aEP_FX","noise02")
        MagicRender.singleframe(sprite,
          ship.location,
          Vector2f(600f,600f),
          timeEclipsed*60f, Color(255,0,0,45),false)
        for (s in Global.getCombatEngine().ships) {
          if (s.owner != ship.owner && !s.isFighter && !s.isDrone && !s.isShuttlePod) {
            if (MathUtils.getDistance(ship, s) <= FUSE_RANGE) {
              state = this@ExplodeListener.Exploding(ship)
              break
            }
          }
        }
      }
    }

    inner class SetOff(ship: ShipAPI): State(ship){
      override fun advanceImpl(amount: Float) {
        if(timeEclipsed > 3f){
          ship.collisionClass = CollisionClass.SHIP
          state = this@ExplodeListener.ApproximatePrimer(ship)
        }
      }
    }

    override fun advance(amount: Float) {
      if (ship == null || !Global.getCombatEngine().isInPlay(ship) || !ship.isAlive) {
        ship?.listenerManager?.removeListenerOfClass(ExplodeListener::class.java)
        return
      }
      state.advance(amount)
    }
  }

  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, mutableStats: MutableShipStatsAPI?, id: String?) {
    mutableStats?: return
    mutableStats.combatEngineRepairTimeMult.modifyFlat(Companion.id, 0.01f)
    mutableStats.engineDamageTakenMult.modifyMult(Companion.id, 0f)
    mutableStats.acceleration.unmodify()
    mutableStats.maxSpeed.unmodify()
    mutableStats.deceleration.unmodify()
    mutableStats.maxTurnRate.unmodify()
    mutableStats.turnAcceleration.unmodify()
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if (ship == null || !Global.getCombatEngine().isInPlay(ship) || !ship.isAlive) {
      return
    }
    //如果舰船一个listener都没有，就根本没有listenerManager
    if(!ship.hasListenerOfClass(ExplodeListener::class.java )){
      createListener(ship)
    }
    ship.isInvalidTransferCommandTarget = true

    //修正船的速度方向和船头朝向
    val newVel = aEP_Tool.rotateVector(ship.velocity,ship.facing,ROTATE_VELOCITY_DEGREE_PER_SECOND,amount)
    ship.velocity.set(newVel)
  }

  open fun createListener(ship: ShipAPI){
    ship.addListener(ExplodeListener(ship))
  }

}
class aEP_CruiseMissile2 : BaseHullMod() {
  companion object{
    const val id = "aEP_CruiseMissile2"
    const val ROTATE_VELOCITY_DEGREE_PER_SECOND = 5f
  }
  open class ExplodeListener : AdvanceableListener{
    companion object{
      const val CENTER_DAMAGE = 1000f
      const val FUSE_RANGE = 600f
      const val FUSE_DELAY= 0.25f
      const val PROJ_NUM = 8
    }

    lateinit var ship:ShipAPI
    private var state : State
    constructor(ship: ShipAPI){
      this.ship = ship
      this.state = SetOff(ship)
    }

    open inner class State {
      lateinit var ship: ShipAPI
      var timeEclipsed = 0f
      constructor(ship: ShipAPI){
        this.ship = ship
      }
      open fun advance(amount: Float){
        advanceImpl(amount)
        timeEclipsed += amount
      }
      open fun advanceImpl(amount: Float){}
    }

    inner class Exploding(ship: ShipAPI) : State(ship) {
      var backBlowSmokeTimer = 0f
      override fun advanceImpl(amount: Float){
        val level = MathUtils.clamp(timeEclipsed/ FUSE_DELAY,0f,1f)
        ship.setJitter("aEP_Exploding",Color(255,20,20,(250*level).toInt()),1f,2,5f)
        ship.mutableStats.maxSpeed.modifyMult(id,0.1f)

        //后坐力烟尘
        backBlowSmokeTimer += amount
        if(backBlowSmokeTimer > 0.05f){
          backBlowSmokeTimer -= 0.05f

          var maxVel = aEP_Tool.speed2Velocity(-ship.facing,400f)
          val sm = aEP_MovingSmoke(ship.location)
          sm.size = 40f
          sm.sizeChangeSpeed = 40f
          sm.color = Color(155,155,155,165)
          sm.fadeIn = 0.25f
          sm.fadeOut = 0.75f
          sm.lifeTime = 1f
          sm.velocity = maxVel
          sm.setInitVel(ship.velocity)
          sm.stopForceTimer.setInterval(0.05f,0.05f)
          sm.stopSpeed = 0.9f
          aEP_CombatEffectPlugin.addEffect(sm)


          maxVel = aEP_Tool.speed2Velocity(-ship.facing-60f,400f)

          val smL = aEP_MovingSmoke(ship.location)
          smL.size = 40f
          smL.sizeChangeSpeed = 40f
          smL.color = Color(155, 155, 155, 165)
          smL.fadeIn = 0.25f
          smL.fadeOut = 0.75f
          smL.lifeTime = 1f
          smL.velocity = maxVel
          smL.stopForceTimer.setInterval(0.05f,0.05f)
          smL.stopSpeed = 0.9f
          aEP_CombatEffectPlugin.addEffect(smL)


          maxVel = aEP_Tool.speed2Velocity(-ship.facing+60f,400f)
          val smR = aEP_MovingSmoke(ship.location)
          smR.size = 40f
          smR.sizeChangeSpeed = 40f
          smR.color = Color(155, 155, 155, 165)
          smR.fadeIn = 0.25f
          smR.fadeOut = 0.75f
          smR.lifeTime = 1f
          smR.velocity = maxVel
          smR.stopForceTimer.setInterval(0.05f,0.05f)
          smR.stopSpeed = 0.9f
          aEP_CombatEffectPlugin.addEffect(smR)

        }

        if(level >= 0.99f){
          explode(ship)
        }
      }

      private fun explode(ship: ShipAPI){
        val point = ship.location
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
        var numMax = 24
        for (i in 0 until  numMax) {
          val loc = MathUtils.getRandomPointInCircle(point, 150f * SIZE_MULT)
          engine.addNebulaParticle(
            loc, Vector2f(0f,0f),200f*SIZE_MULT, 1.5f,
            0f,0.5f, 2.5f+1.5f*MathUtils.getRandomNumberInRange(0f,1f),
            Color(100, 100, 100, 60))
        }

        //横杠闪光
        MagicLensFlare.createSharpFlare(Global.getCombatEngine(),
        ship,
        ship.location,
        40f,1600f, 0f,
        Color(50,50,240),Color(250,100,170))

        //横杠烟雾左,右
        val numOfSmoke = 20
        val maxRange = 300f
        val maxSize = 150f
        val minSize = 50f
        val maxSpeed = 40f
        for (i in 0 until numOfSmoke){
          val angle = ship.facing-90f
          var level = 1f- (i.toFloat())/(numOfSmoke.toFloat())
          val reversedLevel = (1f-level)
          val loc = aEP_Tool.getExtendedLocationFromPoint(ship.location,angle,reversedLevel*maxRange)
          val vel = aEP_Tool.speed2Velocity(angle, reversedLevel*maxSpeed)
          engine.addNebulaParticle(
            loc, vel,maxSize*level+minSize, 1f,
            0f,0.5f, 1f + 1f*level,
            Color(100, 100, 100, 155))
        }
        for (i in 0 until numOfSmoke){
          val angle = ship.facing+90f
          var level = 1f- (i.toFloat())/(numOfSmoke.toFloat())
          val reversedLevel = (1f-level)
          val loc = aEP_Tool.getExtendedLocationFromPoint(ship.location,angle,reversedLevel*maxRange)
          val vel = aEP_Tool.speed2Velocity(angle, reversedLevel*maxSpeed)
          engine.addNebulaParticle(
            loc, vel,maxSize*level+minSize, 1f,
            0f,0.5f, 1f + 1f*level,
            Color(100, 100, 100, 155))
        }

        //生成弹丸
        val numOfProj = PROJ_NUM
        val rangeCreepPerProj = -30f
        for(i in 0 until numOfProj) {
          val pro = engine.spawnProjectile(
            ship,  //source ship
            ship.allWeapons[0],  //source weapon,
            "aEP_cruise_missile_shot2",  //whose proj to be use
            ship.location,  //loc
            ship.facing,  //facing
            null)
          pro.location.set(aEP_Tool.getExtendedLocationFromPoint(ship.location,ship.facing,rangeCreepPerProj*i))
        }

        //play sound
        Global.getSoundPlayer().playSound(
          "aEP_RW_fire",
          0.75f, 6f,  // pitch,volume
          ship.location,  //location
          Vector2f(0f, 0f)
        ) //velocity


        //中心点爆炸
        val spec = DamagingExplosionSpec(
          1f,
          100f,
          50f,
          CENTER_DAMAGE,
          CENTER_DAMAGE/2f,
          CollisionClass.MISSILE_FF,  //by ship
          CollisionClass.MISSILE_NO_FF,  //by fighter
          0f, 0f, 0f, 0,
          Color.white, Color.white
        )
        spec.damageType = DamageType.HIGH_EXPLOSIVE
        engine.spawnDamagingExplosion(spec, ship, point)
        engine.combatNotOverFor = engine.combatNotOverFor + 2f
        engine.removeEntity(ship)
      }
    }

    inner class ApproximatePrimer(ship: ShipAPI) : State(ship) {
      override fun advanceImpl(amount: Float){
        val detectPoint = aEP_Tool.getExtendedLocationFromPoint(ship.location,ship.facing, FUSE_RANGE)
        val sprite = Global.getSettings().getSprite("aEP_FX","noise")
        MagicRender.singleframe(sprite,
          detectPoint,
          Vector2f(60f,60f),
          timeEclipsed*60f, Color(255,0,0,225),false)
        for (s in AIUtils.getNearbyEnemies(ship,1000f)) {
          if (s.owner != ship.owner && !s.isFighter && !s.isDrone && !s.isShuttlePod) {
            if (CollisionUtils.getCollisionPoint(ship.location,detectPoint,s) == null) continue
            state = this@ExplodeListener.Exploding(ship)
            break
          }
        }
      }
    }

    inner class SetOff(ship: ShipAPI): State(ship){
      override fun advanceImpl(amount: Float) {
        if(timeEclipsed > 3f){
          ship.collisionClass = CollisionClass.SHIP
          state = this@ExplodeListener.ApproximatePrimer(ship)
        }
      }
    }

    override fun advance(amount: Float) {
      if (ship == null || !Global.getCombatEngine().isInPlay(ship) || !ship.isAlive) {
        ship?.listenerManager?.removeListenerOfClass(ExplodeListener::class.java)
        return
      }
      state.advance(amount)
    }
  }

  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, mutableStats: MutableShipStatsAPI?, id: String?) {
    mutableStats?: return
    mutableStats.combatEngineRepairTimeMult.modifyFlat(aEP_CruiseMissile.id, 0.01f)
    mutableStats.engineDamageTakenMult.modifyMult(aEP_CruiseMissile.id, 0f)
    mutableStats.acceleration.unmodify()
    mutableStats.maxSpeed.unmodify()
    mutableStats.deceleration.unmodify()
    mutableStats.maxTurnRate.unmodify()
    mutableStats.turnAcceleration.unmodify()
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if (ship == null || !Global.getCombatEngine().isInPlay(ship) || !ship.isAlive) {
      return
    }
    //如果舰船一个listener都没有，就根本没有listenerManager
    if(!ship.hasListenerOfClass(ExplodeListener::class.java )){
      createListener(ship)
    }
    ship.isInvalidTransferCommandTarget = true
    //修正船的速度方向和船头朝向
    val newVel = aEP_Tool.rotateVector(ship.velocity,ship.facing, ROTATE_VELOCITY_DEGREE_PER_SECOND,amount)
    ship.velocity.set(newVel)
  }

  open fun createListener(ship: ShipAPI){
    ship.addListener(ExplodeListener(ship))
  }
}

//战机装甲插件
class aEP_FighterArmor : BaseHullMod() {
  companion object {
    const val ARMOR_COMPUTE_PERCENT_BONUS = 10f
    const val DAMAGE_TAKEN_REDUCE_MULT = 0.10f


    const val EMP_TAKEN_REDUCE_MULT = 0.5f
    const val WEAPON_DAMAGE_TAKEN_REDUCE_MULT = 0.5f

  }

  override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {
    var modifier = 1f
    when(ship.hullSpec.hullId){
      "aEP_ftr_bom_nuke"-> modifier = 2.5f
      "aEP_ftr_icp_gunship"-> modifier = 2.5f
      "aEP_ftr_ftr_hvfighter"-> modifier = 1.5f
      "aEP_ftr_ftr_helicop"-> modifier = 1.5f
    }


    ship.mutableStats.effectiveArmorBonus.modifyPercent(id, ARMOR_COMPUTE_PERCENT_BONUS * modifier)
    ship.mutableStats.armorDamageTakenMult.modifyMult(id, 1f - DAMAGE_TAKEN_REDUCE_MULT* modifier)
    ship.mutableStats.hullDamageTakenMult.modifyMult(id, 1f - DAMAGE_TAKEN_REDUCE_MULT * modifier)

    ship.mutableStats.empDamageTakenMult.modifyMult(id,1f - EMP_TAKEN_REDUCE_MULT)
    ship.mutableStats.engineDamageTakenMult.modifyMult(id,1f - WEAPON_DAMAGE_TAKEN_REDUCE_MULT)
    ship.mutableStats.weaponDamageTakenMult.modifyMult(id,1f - WEAPON_DAMAGE_TAKEN_REDUCE_MULT)

  }


}

//type28护盾插件
class aEP_Type28Shield : BaseHullMod() {
  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize, stats: MutableShipStatsAPI, id: String) {
    Companion.id = id
    stats.hardFluxDissipationFraction.modifyFlat(id, 0.1f)
    stats.shieldUnfoldRateMult.modifyPercent(id, 600f)

  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

    if (ship.shield == null || ship.shield.type == ShieldAPI.ShieldType.NONE) return

    //阻拦穿盾伤害
    if(ship.shield.isOn && ship.shield.activeArc > 60f){
      ship.mutableStats.armorDamageTakenMult.modifyMult(id,0f)
      ship.mutableStats.hullDamageTakenMult.modifyMult(id,0f)
    }else{
      ship.mutableStats.armorDamageTakenMult.modifyMult(id,1f)
      ship.mutableStats.hullDamageTakenMult.modifyMult(id,1f)
    }

    val fluxLevel = ship.fluxLevel
    val shieldColor = Color((COLOR_SHIFT_VALUE * fluxLevel).toInt() + 250 - COLOR_SHIFT_VALUE, 50, (COLOR_SHIFT_VALUE * (1f - fluxLevel)).toInt() + 250 - COLOR_SHIFT_VALUE, 120)
    ship.shield.innerColor = shieldColor
    if (fluxLevel > JITTER_THRESHOLD) {
      ship.setCircularJitter(true)
      ship.isJitterShields = true
      ship.setJitter(
        ship,
        shieldColor,
        20f,  //range
        3,  //copies
        1f * (fluxLevel - 0.5f) * 2f
      )
      if (MathUtils.getRandomNumberInRange(0, 100) < (fluxLevel - 0.5f) * 2f * 100f * amount * MIN_EMP_ARC_INTERVAL_PER_SEC) {
        val from = MathUtils.getRandomPointInCircle(ship.location, ship.shield.radius / 2f)
        val to = getExtendedLocationFromPoint(ship.location, MathUtils.getRandomNumberInRange(0, 360).toFloat(), ship.shield.radius)
        Global.getCombatEngine().spawnEmpArcVisual(
          from,
          ship,
          to,
          null,
          MathUtils.getRandomNumberInRange(2f, 6f),
          Color(100, 100, 200, 80),
          Color(200, 200, 250, 200)
        )
      }
    }
    if (ship.parentStation == null) {
      return
    }
    for (modular in ship.parentStation.childModulesCopy) {
      if (ship.parentStation.engineController.isAccelerating) {
        modular.giveCommand(ShipCommand.ACCELERATE, null, 0)
      } else if (ship.parentStation.engineController.isTurningRight) {
        if (modular.hullSpec.hullId == "aEP_typeL28") {
          modular.giveCommand(ShipCommand.ACCELERATE, null, 0)
        }
      } else if (ship.parentStation.engineController.isTurningLeft) {
        if (modular.hullSpec.hullId == "aEP_typeR28") {
          modular.giveCommand(ShipCommand.ACCELERATE, null, 0)
        }
      }
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize): String? {
    return null
  }

  override fun isApplicableToShip(ship: ShipAPI): Boolean {
    return false
  }

  override fun getUnapplicableReason(ship: ShipAPI): String {
    return "无法安装任何插件"
  }

  override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {
    val toRomvelist: MutableList<String> = ArrayList()
    for (hullmodId in ship.variant.hullMods) {
      if (!ship.variant.permaMods.contains(hullmodId)) {
        toRomvelist.add(hullmodId)
      }
    }
    for (toRemove in toRomvelist) {
      ship.variant.removeMod(toRemove)
    }
  }

  companion object {
    private const val COLOR_SHIFT_VALUE = 200
    private const val MIN_EMP_ARC_INTERVAL_PER_SEC = 0.25f
    private const val JITTER_THRESHOLD = 0.7f
    private val JITTER_COLOR = Color(200, 200, 250, 200)
    private var id = "aEP_Type28Shield"
  }
}

//模块爆炸插件
class aEP_Module : aEP_BaseHullMod() {
  companion object {
    const val DAMAGE_MULT = 0.001f
    const val DAMAGE_RANGE_MULT = 0.25f
  }
  var ID: String = "aEP_Module"
  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String) {
    stats?:return
    stats.dynamic.getStat(EXPLOSION_DAMAGE_MULT)?.modifyMult(ID, DAMAGE_MULT)
    stats.dynamic.getStat(EXPLOSION_RADIUS_MULT)?.modifyMult(ID, DAMAGE_RANGE_MULT)

    stats.ventRateMult.modifyMult(ID,0f)
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    super.advanceInCombat(ship, amount)

    ship.isPhased = false
    ship.extraAlphaMult = 1f
    ship.setApplyExtraAlphaToEngines(true)
    if(ship.parentStation != null){
      val parent = ship.parentStation
      val stats = ship.mutableStats
      val parentStats = parent.mutableStats
      if(parent.isPhased){
        ship.setApplyExtraAlphaToEngines(true)
        ship.extraAlphaMult = 1f - (1f - SHIP_ALPHA_MULT)
        ship.isPhased = true

        if(ship.shield != null){
          ship.shield.toggleOff()
        }

        //禁止自动开火，禁止手动开火
        ship.isHoldFireOneFrame = true
        ship.blockCommandForOneFrame(ShipCommand.FIRE)
      }
    }

  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if(!ship.customData.containsKey(ID)){
      ship.addListener(DamageListener(ship))
      ship.setCustomData(ID,1f)
    }
  }



  /**
   * 返回true表示保持舰船不死
   * 通过这个死前把模块的幅能清空，防止殉爆高闪
   * */
  inner class DamageListener(val ship: ShipAPI) : HullDamageAboutToBeTakenListener, AdvanceableListener{
    var shouldEnd = false

    override fun notifyAboutToTakeHullDamage(param: Any?, ship: ShipAPI?, point: Vector2f?, damageAmount: Float): Boolean {
      ship?:return false
      if(damageAmount >= ship.hitpoints && !shouldEnd){
        ship.mutableStats.fluxCapacity.modifyMult(ID,0.05f)
        ship.fluxTracker.currFlux = 0f
        return false
      }
      return false
    }

    override fun advance(amount: Float) {
    }
  }
}

//crossout结构减伤
class aEP_Structure : aEP_BaseHullMod() {
  companion object {
    const val DAMAGE_MULT = 0.5f
    const val MIN_ARMOR_FRACTION_FLAT = 0.25f
  }

  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String) {
    stats?.hullDamageTakenMult?.modifyMult(id, DAMAGE_MULT)
    stats?.minArmorFraction?.modifyFlat(id, MIN_ARMOR_FRACTION_FLAT)
  }
}

//飞行坦克系统
class aEP_FlyingTank : aEP_BaseHullMod(){
  companion object{
    const val REPAIR_AMOUNT_PER_SECOND = 20f
  }


  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship?:return
    if(!ship.hasListenerOfClass(RangeListener::class.java)) ship.addListener(RangeListener(ship))
  }

  class RangeListener(val ship: ShipAPI) : AdvanceableListener{
    val armorTracker = IntervalUtil(0.1f,0.1f)

    override fun advance(amount: Float) {

      armorTracker.advance(amount)
      if(armorTracker.intervalElapsed()){
        //维修装甲
        val xSize = ship.armorGrid.leftOf + ship.armorGrid.rightOf
        val ySize = ship.armorGrid.above + ship.armorGrid.below
        val cellMaxArmor = ship.armorGrid.maxArmorInCell
        var minArmorLevel = 10f
        var minX = 0
        var minY = 0

        var toRepair = armorTracker.minInterval * REPAIR_AMOUNT_PER_SECOND
        while (toRepair > 0f){
          //find the lowest armor grid
          for (x in 0..xSize - 1) {
            for (y in 0..ySize - 1) {
              val armorNow = ship.armorGrid.getArmorValue(x, y)
              val armorLevel = armorNow / cellMaxArmor
              if (armorLevel <= minArmorLevel) {
                minArmorLevel = armorLevel
                minX = x
                minY = y
              }
            }
          }

          // 如果当前最低的一块甲不满就修复，否则不用修直接break
          val armorAtMin = ship.armorGrid.getArmorValue(minX, minY)
          val needRepair = cellMaxArmor - armorAtMin
          //做不到完全回复
          if ( needRepair > 0.1f) {
            var toAddArmor = 0f
            if(needRepair > toRepair){
              toAddArmor = toRepair
              toRepair = 0f
            }else{
              toAddArmor = needRepair
              toRepair -= toAddArmor
            }
            ship.armorGrid.setArmorValue(minX, minY, armorAtMin + toAddArmor)
          }else{
            break
          }
        }
      }

    }
  }
}