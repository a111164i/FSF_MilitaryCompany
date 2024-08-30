package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.impl.campaign.ids.Stats.EXPLOSION_DAMAGE_MULT
import com.fs.starfarer.api.impl.campaign.ids.Stats.EXPLOSION_RADIUS_MULT
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.impl.VEs.aEP_MovingSmoke
import combat.impl.VEs.aEP_SpreadRing
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Combat
import combat.util.aEP_ID
import combat.util.aEP_Render
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.angleAdd
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.isDead
import data.scripts.ai.aEP_DroneShieldShipAI
import data.scripts.shipsystems.aEP_DroneGuard
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lazywizard.lazylib.ui.LazyFont
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicAnim
import org.magiclib.util.MagicLensFlare
import org.magiclib.util.MagicRender
import org.magiclib.util.MagicUI
import java.awt.Color
import kotlin.math.pow

/**
* 默认的showDescription返回false，这样不会显示插件说明
* */
class aEP_FighterSpecial: BaseHullMod() {

  var hullmod : BaseHullMod? = null

  override fun init(spec: HullModSpecAPI?) {

    //找到实际对应的代码存入hullmod变量
    //classForName查不到就保持null
    val id = spec?.id
    try {
      val e = Class.forName(aEP_FighterSpecial::class.java.getPackage().name + "." + id)
      hullmod = e.newInstance() as BaseHullMod
      super.init(spec)
      hullmod?.init(spec)
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
    return hullmod?.getDescriptionParam(index, hullSize)?:""
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?, ship: ShipAPI?): String {
    return hullmod?.getDescriptionParam(index, hullSize, ship)?:""
  }

  override fun applyEffectsToFighterSpawnedByShip(fighter: ShipAPI?, ship: ShipAPI?, id: String?) {
    hullmod?:return
    hullmod!!.applyEffectsToFighterSpawnedByShip(fighter, ship, id)
  }

  override fun isApplicableToShip(ship: ShipAPI?): Boolean {
    return hullmod?.isApplicableToShip(ship)?:false
  }

  override fun getUnapplicableReason(ship: ShipAPI?): String {
    return hullmod?.getUnapplicableReason(ship)?:""
  }

  /**
   * ship may be null from autofit.
   * @param ship
   * @param marketOrNull
   * @param mode
   * @return
   */
  override fun canBeAddedOrRemovedNow(ship: ShipAPI?, marketOrNull: MarketAPI?, mode: CampaignUIAPI.CoreUITradeMode?): Boolean {
    return hullmod?.canBeAddedOrRemovedNow(ship, marketOrNull, mode)?:false
  }

  override fun getCanNotBeInstalledNowReason(ship: ShipAPI?, marketOrNull: MarketAPI?, mode: CampaignUIAPI.CoreUITradeMode?): String {
    return hullmod?.getCanNotBeInstalledNowReason(ship, marketOrNull, mode)?:""
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
    return hullmod?.affectsOPCosts()?:false
  }

  /**
   * ship may be null, will be for modspecs. hullsize will always be CAPITAL_SHIP for modspecs.
   * @param hullSize
   * @param ship
   * @param isForModSpec
   * @return
   */
  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  /**
   * ship may be null, will be for modspecs. hullsize will always be CAPITAL_SHIP for modspecs.
   * @param tooltip
   * @param hullSize
   * @param ship
   * @param width
   * @param isForModSpec
   */
  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI?, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    hullmod?: return
    hullmod!!.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec)
  }

  override fun getBorderColor(): Color? {
    hullmod?: return null
    return hullmod!!.borderColor
  }

  override fun getNameColor(): Color? {
    hullmod?: return null
    return hullmod!!.nameColor
  }

  /**
   * Sort order within the mod's display category. Not used when category == 4, since then
   * the order is determined by the order in which the player added the hullmods.
   * @return
   */
  override fun getDisplaySortOrder(): Int {
    return hullmod?.displaySortOrder ?: 99
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
    return hullmod?.displayCategoryIndex?:-1
  }

  override fun hasSModEffectSection(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return hullmod?.hasSModEffectSection(hullSize, ship, isForModSpec)?:false
  }

  override fun addSModSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean, isForBuildInList: Boolean) {
    hullmod?:return
    hullmod!!.addSModSection(tooltip, hullSize, ship, width, isForModSpec, isForBuildInList)
  }

  override fun addSModEffectSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean, isForBuildInList: Boolean) {
    hullmod?:return
    hullmod!!.addSModEffectSection(tooltip, hullSize, ship, width, isForModSpec, isForBuildInList)
  }

  override fun hasSModEffect(): Boolean {
    return hullmod?.hasSModEffect()?:false
  }

  override fun getSModDescriptionParam(index: Int, hullSize: ShipAPI.HullSize): String {
    return hullmod?.getSModDescriptionParam(index, hullSize)?:""
  }

  override fun getSModDescriptionParam(index: Int, hullSize: ShipAPI.HullSize, ship: ShipAPI?): String {
    return hullmod?.getSModDescriptionParam(index, hullSize, ship)?:""
  }

  override fun getTooltipWidth(): Float {
    return hullmod?.tooltipWidth?:369f
  }

  override fun isSModEffectAPenalty(): Boolean {
    return hullmod?.isSModEffectAPenalty?:false
  }

  override fun showInRefitScreenModPickerFor(ship: ShipAPI?): Boolean {
    return hullmod?.showInRefitScreenModPickerFor(ship)?:true
  }

  override fun isSMod(stats: MutableShipStatsAPI?): Boolean {
    return hullmod?.isSMod(stats)?:false
  }

  override fun isSMod(ship: ShipAPI?): Boolean {
    return hullmod?.isSMod(ship)?:false
  }

  override fun isBuiltIn(ship: ShipAPI?): Boolean {
    return hullmod?.isBuiltIn(ship)?:false
  }

  override fun shipHasOtherModInCategory(ship: ShipAPI?, currMod: String?, category: String?): Boolean {
    return hullmod?.shipHasOtherModInCategory(ship, currMod, category)?:false
  }

  override fun isInPlayerFleet(stats: MutableShipStatsAPI?): Boolean {
    return hullmod?.isInPlayerFleet(stats)?:false
  }

  override fun isInPlayerFleet(ship: ShipAPI?): Boolean {
    return hullmod?.isInPlayerFleet(ship)?:false
  }


}

//锚点无人机护盾插件
class aEP_MaoDianShield : aEP_BaseHullMod() {
  companion object{
    val ID = "aEP_MaoDianShield"
    val TIME_TO_EXTEND = 1f
    //ship文件里的护盾半径也要改，否则护盾中心在屏幕外的时候不会渲染护盾
    val MAX_SHIELD_RADIUS = Global.getSettings().getHullSpec("aEP_ftr_ut_maodian").shieldSpec.radius
    val MAX_MOVE_TIME = 30f
    val MAX_SHIELD_TIME = 12f
    val RADAR_SPEED = -90f
    val TIME_MULT_BNUS =  3f
  }

  //强行加速到更高境界
  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    val angAndSpdSq = aEP_Tool.velocity2SpeedSq(ship.velocity)
    val accAngle = aEP_Tool.computeCurrentManeuveringDir(ship)
    val baseMaxSpeedSq = ship.maxSpeed.pow(2)
    val angleDistAbs = Math.abs(MathUtils.getShortestRotation(angAndSpdSq.x, accAngle))

    //加速度方向和速度方向相同，速度已经加速到最大速度
    //满足条件给一个小时流
    if(angleDistAbs < 15f && angAndSpdSq.y > baseMaxSpeedSq - 25f){
      ship.mutableStats.timeMult.modifyMult(ID, TIME_MULT_BNUS)
      ship.mutableStats.ballisticRoFMult.modifyMult(ID, 1f/TIME_MULT_BNUS)
    }else{
      ship.mutableStats.timeMult.modifyMult(ID, 1f)
      ship.mutableStats.ballisticRoFMult.modifyMult(ID, 1f)
    }
  }

  /**
   * 使用这个
   **/
  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship.collisionClass = CollisionClass.FIGHTER

    ship.mutableStats.damageToFighters.modifyMult(ID, 0.33f)
    ship.mutableStats.damageToFrigates.modifyMult(ID, 0.33f)
    ship.mutableStats.damageToDestroyers.modifyMult(ID, 0.33f)
    ship.mutableStats.damageToCruisers.modifyMult(ID, 0.33f)
    ship.mutableStats.damageToCapital.modifyMult(ID, 0.33f)
    if(!ship.hasListenerOfClass(ShieldListener::class.java)){
      ship.addListener(ShieldListener(ship))
    }

    ship.mutableStats.dynamic.getStat(EXPLOSION_DAMAGE_MULT).modifyMult(id,0.01f)

    ship.mutableStats.shieldUnfoldRateMult.modifyFlat(ID, 1f)
  }

}
//锚点自爆监听器
class ShieldListener(val ship: ShipAPI) : AdvanceableListener, DamageTakenModifier {
  var time = 0f
  var shieldTime = 0f
  var moveTime = 0f
  val empArcTracker = IntervalUtil(0.1f, 0.6f)
  val particleTracker = IntervalUtil(0.1f, 0.2f)

  var shouldEnd = false
  override fun advance(amount: Float) {

    if(isDead(ship)){
      ship.removeListener(this)
    }

    val moveLevel = (moveTime / aEP_MaoDianShield.MAX_MOVE_TIME).coerceAtMost(1f)
    val shieldLevel = (shieldTime / aEP_MaoDianShield.MAX_SHIELD_TIME).coerceAtMost(1f)
    val fluxLevel = MathUtils.clamp(ship.fluxLevel, 0f, 1f)
    val shieldColorLevel = Math.max(fluxLevel,shieldLevel)

    time = MathUtils.clamp(time + aEP_Tool.getAmount(ship), 0f, 999f)

    //这里根据是否开盾，更新护盾颜色和指示deco的颜色
    if (ship.shield != null) {
      //如果有盾，就产生特效，并且增加硬幅能，靠幅能爆表来终结自身
      //改变盾大小，和只改radius冲突，需要每帧调用，别动最好，因为盾的外圈是最先渲染的，动态调整会影响ring的贴图
      /*
      val rad = MAX_SHIELD_RADIUS * MagicAnim.smooth(shieldLevel)
      ship.shield?.setRadius(rad,
        Global.getSettings().getSpriteName("aEP_hullstyle","aEP_shield_inner03"),
        Global.getSettings().getSpriteName("aEP_hullstyle","aEP_shield_outer03"))
      //顺便改变船碰撞圈的大小
      ship.collisionRadius = MathUtils.clamp(rad,40f,MAX_SHIELD_RADIUS)
       */

      updateStatsWhenShieldIsOn(ship, amount, shieldColorLevel)
      if(ship.shield.isOn){
        //开盾时，无视moveLevel
        //根据部署时间/幅能水平，渲染中间光圈的颜色
        updateIndicator(ship,1f - shieldColorLevel)
      }else{
        //不开盾时，3种限制level取大
        val decoLevel = Math.max(Math.max(fluxLevel,shieldLevel), moveLevel)
        updateIndicator(ship,1f - decoLevel)
      }
    } else {
      //如果无盾，无事发生，依靠总定时器来终结自身
    }

    //旋转雷达
    for (w in ship.allWeapons) {
      if (w.spec.weaponId == "aEP_ftr_ut_maodian_radar") {
        w.currAngle = (w.currAngle + amount * aEP_MaoDianShield.RADAR_SPEED)
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

    //如果开盾时间或者机动时间任意超过了限制，强制增加幅能，快速自爆
    if(shieldTime >= aEP_MaoDianShield.MAX_SHIELD_TIME
      ||moveTime >= aEP_MaoDianShield.MAX_MOVE_TIME){

      //移除耗散能力，每秒增加百分之100%的最大幅能，1秒后触发幅能上限，对自己造成伤害自爆
      ship.mutableStats.fluxDissipation.modifyMult(aEP_MaoDianShield.ID, 0f)
      ship.fluxTracker.increaseFlux(
        (ship.fluxTracker.maxFlux) * amount,
        true)
    }

    //如果任何形式幅能满了，准备自毁
    if (ship.fluxLevel > 0.99f || ship.fluxTracker.isOverloaded) {
      shouldEnd = true
    }

    //自毁
    if (shouldEnd) {
      val suppressBefore = Global.getCombatEngine()?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages
      Global.getCombatEngine()?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages = true
      val spec = DamagingExplosionSpec(
        4f,  //float duration,
        200f,  //float radius,
        100f,  //float coreRadius,
        200f,  //float maxDamage,
        100f,  //float minDamage,
        CollisionClass.MISSILE_NO_FF,  //collisionClass,
        CollisionClass.PROJECTILE_FIGHTER,  //collisionClassByFighter,
        0.5f,  //float particleSizeMin,
        1f,  //float particleSizeRange,
        0.5f,  //float particleDuration,
        6,  //int particleCount,
        Color(250, 250, 100, 255),
        Color(255, 200, 120, 255))
      spec.damageType = DamageType.HIGH_EXPLOSIVE
      spec.isUseDetailedExplosion = true
      spec.detailedExplosionRadius = 300f
      spec.detailedExplosionFlashDuration = 1.5f
      spec.detailedExplosionFlashRadius = 500f
      spec.detailedExplosionFlashColorCore =  Color(250, 250, 175, 255)
      spec.detailedExplosionFlashColorFringe = Color(255, 150, 100, 255)
      Global.getCombatEngine().spawnDamagingExplosion(spec,ship,ship.location)

      val ring = aEP_SpreadRing(
        100f,
        100f,
        Color(251,250,255,55),
        100f,
        1000f,
        ship.location)
      ring.initColor.setToColor(50f, 250f, 100f,0f,0.75f)
      ring.endColor.setColor(255f, 120f, 50f,0f)
      aEP_CombatEffectPlugin.addEffect(ring)

      Global.getCombatEngine().removeEntity(ship)
      Global.getCombatEngine()?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages = suppressBefore ?: false
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
  override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f?, shieldHit: Boolean): String? {
    return null
  }

  fun updateStatsWhenShieldIsOn(ship:ShipAPI, amount:Float, renderLevel:Float){

    //盾颜色
    val color = Color(
      0.3f + 0.65f * renderLevel,
      0.5f,
      0.3f + 0.65f * (1f - renderLevel),
      (0.1f * renderLevel * renderLevel) + MagicAnim.smooth((0.1f * (1f - renderLevel))))
    ship.shield.innerColor = color
    ship.shield.ringColor = color
    //若开盾强制增加硬幅能，若不开盾增加机动时间，机动时间超时后快速涨幅能（防止有人控制飞机一直不开盾）
    if (ship.shield?.isOn == true) {
      shieldTime = MathUtils.clamp(shieldTime +amount, 0f, aEP_MaoDianShield.MAX_SHIELD_TIME)
    } else {
      moveTime = MathUtils.clamp(moveTime + amount, 0f, aEP_MaoDianShield.MAX_MOVE_TIME)
    }
  }

  fun updateIndicator(ship: ShipAPI, level:Float){

    //找一次盖板武器
    var rotator :aEP_DecoAnimation? = null
    var red :aEP_DecoAnimation? = null
    var green :aEP_DecoAnimation? = null


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

    rotator?:return
    red?:return
    green?:return

    //灯的旋转同步圆盘的
    //因为飞机是半路刷出来的，第一帧listener插入时会调用到这里，此时不知道为什么weaponEffect还没有生成，所有要加个?.
    rotator.setRevoToLevel(1f- level)
    red.decoRevoController?.toLevel = rotator.decoRevoController?.toLevel?:0f
    green.decoRevoController?.toLevel = rotator.decoRevoController?.toLevel?:0f

    green.setGlowToLevel(level)
    red.setGlowToLevel(1f-level)
  }

}

//
// 吞弹护盾
class aEP_ProjectileDenialShield : aEP_BaseHullMod(){
  companion object{
    const val ID = "aEP_ProjectileDenialShield"

    const val KE_DAMAGE_TAKEN_MULT = 0.5f
    const val HE_DAMAGE_TAKEN_MULT = 1.5f
    const val EN_DAMAGE_TAKEN_MULT = 0.75f
    //破片后续会被自动×0.33f
    const val FRAG_DAMAGE_TAKEN_MULT = 0.75f

    const val HARD_DISSI = 50f

    const val BEAM_TAKEN_MULT = 0.5f

    /**
     * @param divider 伤害会被除以这个数
     * */
    fun eatDamage(ship: ShipAPI, damage: DamageAPI, divider:Float){

      var damageAmount = damage.damage * damage.modifier.modified
      if(damage.type == DamageType.KINETIC) damageAmount *= KE_DAMAGE_TAKEN_MULT
      if(damage.type == DamageType.ENERGY) damageAmount *= EN_DAMAGE_TAKEN_MULT
      if(damage.type == DamageType.HIGH_EXPLOSIVE) damageAmount *= HE_DAMAGE_TAKEN_MULT
      if(damage.type == DamageType.FRAGMENTATION) damageAmount *= (FRAG_DAMAGE_TAKEN_MULT *0.25f)
      if(divider > 1f) damageAmount /= divider

      val absorbRate = (ship.shield.fluxPerPointOfDamage * ship.mutableStats.shieldDamageTakenMult.modified).coerceAtMost(1f)
      damageAmount *= absorbRate

      if(ship.maxFlux - ship.currFlux > damageAmount){
        ship.fluxTracker.increaseFlux(damageAmount, true)
      }else{
        ship.fluxTracker.beginOverloadWithTotalBaseDuration(10f)
      }

    }

    fun keepExplosionProtectListenerToParent(fighter: ShipAPI, toProtect: ShipAPI){
      if(isDead(fighter) || isDead(toProtect)) return

      //给toProtect施加防爆listener
      //看看fighter和parent的距离够不够
      val distSq = MathUtils.getDistanceSquared(toProtect, fighter)
      if(distSq < (aEP_DroneShieldShipAI.FAR_FROM_PARENT + 100f).pow(2)){
        //先看看无人机的customData里面有没有handle，不存在就创建一个listener塞进飞机
        //给toProtect塞一个防爆炸监听器
        val key = GlobalExplosionDetect.ID_KEY
        var protectTargetListener = fighter.customData[key] as GlobalExplosionDetect?
        if(protectTargetListener == null){
          protectTargetListener = GlobalExplosionDetect(toProtect, fighter, 0.5f)
          if(!toProtect.hasListener(protectTargetListener)){
            toProtect.addListener(protectTargetListener)
          }
        }

        //检查listener里面的ship是不是正在保护的toProtected
        //如果目前这个战机防爆类保护的母舰不是当前ai保护的母舰，立刻结束这个类，并把新的listener塞进飞机
        if(protectTargetListener.parent != toProtect){
          //结束老的，赋值新的
          protectTargetListener.time = protectTargetListener.lifetime
          protectTargetListener = GlobalExplosionDetect(toProtect, fighter, 0.5f)
          //计算完成后，把新listener的引用放入customData，顶掉老的listener
          //老的listener会在lifeTime后自动终结，不需要关心了
          fighter.setCustomData(key, protectTargetListener)
          if(!toProtect.hasListener(protectTargetListener)){
            toProtect.addListener(protectTargetListener)
          }
        }else{
          //如果一切正常，延长lifetime
          //延长
          protectTargetListener.time = 0f
        }
      }


    }

  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if(isDead(ship)) return

    //每次过载，给与自己一定伤害的惩罚
    var punish = 0f
    var maxOverloadTime = 4f
    //检测飞机是否有特殊tag，更改过载时间或者每次过载给自己施加一定伤害作为惩罚
    val tags = ship.hullSpec.tags
    for (tag in tags){
      if(tag.startsWith("aEP_ProjectileDenialShield_")){
        val param = tag.split("_")
        maxOverloadTime = param[2].toFloat()
        punish = param[3].toFloat()
      }
    }

    if (ship.fluxTracker.isOverloaded && ship.fluxTracker.overloadTimeRemaining > maxOverloadTime) {
      ship.fluxTracker.setOverloadDuration(maxOverloadTime)
      if(punish > 0f){
        Global.getCombatEngine().applyDamage(
          ship,ship.location,
          punish,DamageType.OTHER,0f,
          true,false,ship,false)
      }
    }

    if(ship.isFighter){
      val newHard = (ship.fluxTracker.hardFlux - HARD_DISSI * amount).coerceAtLeast(0f)
      ship.fluxTracker.hardFlux = newHard
    }

  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship.mutableStats.empDamageTakenMult.modifyMult(aEP_MaoDianShield.ID,0f)

    ship.mutableStats.kineticShieldDamageTakenMult.modifyMult(id, KE_DAMAGE_TAKEN_MULT)
    ship.mutableStats.highExplosiveShieldDamageTakenMult.modifyMult(id, HE_DAMAGE_TAKEN_MULT)
    ship.mutableStats.energyShieldDamageTakenMult.modifyMult(id, EN_DAMAGE_TAKEN_MULT)
    ship.mutableStats.fragmentationShieldDamageTakenMult.modifyMult(id, FRAG_DAMAGE_TAKEN_MULT)

    ship.mutableStats.beamShieldDamageTakenMult.modifyMult(id, BEAM_TAKEN_MULT)


    if(!ship.customData.containsKey(ID) ){
      ship.setCustomData(ID,1f)
      ship.addListener(ProjectileRemove(ship))
    }


  }

  inner class ProjectileRemove(val ship: ShipAPI) :  DamageTakenModifier{

    //先于实际施加伤害，对于同一帧的伤害，先全部过一遍这个修改函数，再逐一施加于船体上
    //param是造成伤害的发射物，DamagingProjectileAPI，beamAPI, EmpArcEntityAPI 等
    //进行装甲格移除也会进监听器，如果report的话
    //return修改项的id
    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      if(!shieldHit) return null

      //伤害会被除以这个数，sourceModifier越大实际伤害越小
      var sourceModifier = 1f

      //对弹丸-----------------------------------------------------------------//
      if(param is DamagingProjectileAPI ){
        //这里抵消掉对战机的伤害加成
        if (param.source is ShipAPI){
          sourceModifier = param.source.mutableStats.damageToFighters.modifiedValue
        }

        //吞弹然后手动增加幅能（如果这个弹还存在与战场上）
        if(Global.getCombatEngine().isEntityInPlay(param)){
          eatDamage(ship, param.damage,sourceModifier)
          Global.getCombatEngine().removeEntity(param)
          //吞了弹，但是这颗弹对舰船造成的伤害并没有归零，sourceModifier跟着增加
          sourceModifier += 10000f
        }

        //防止大aoe直接炸到后面的船体，类似模块护盾的问题，没办法
        //已经在后面的爆炸监听器中修正这个问题
        if(param is MissileAPI){
          //param.spec?.explosionSpec?.minDamage?.div(10f)
          //param.spec?.explosionSpec?.maxDamage?.div(10f)
        }

        //能抓到近炸，每炸到任何一个其他拥有吞弹护盾的舰船，自己对该爆炸的伤害降低
        if(param.damagedAlready?.size?:0 >= 1){
          var numOfDroneDamaged = 1
          for(e in param.damagedAlready){
            if(e is ShipAPI && e.variant.hasHullMod(ID) && e != ship){
              numOfDroneDamaged += 1
            }
          }

          //如果当前有超过一个其他无人机被炸，增加sourceModifier，减少伤害
          var epdDamMult = 3f
          if(numOfDroneDamaged > 1){
            epdDamMult.pow(numOfDroneDamaged)
          }
          sourceModifier += epdDamMult

        }

        if(sourceModifier > 1f){
          damage.modifier.modifyMult(ID, (1f/sourceModifier))
          return ID
        }else{
          return null
        }
      }

      //对光束----------------------------------------------------------------//
      if(param is BeamAPI){
        //抵消掉敌方对战机增伤
        if (param.source is ShipAPI){
          sourceModifier = param.source.mutableStats.damageToFighters.modifiedValue
        }

        //如果光束可以穿透，最终目标不是自己但是对自己造成了伤害
        //当触发时，降低光束本帧的伤害，同时给自己涨幅能
        if(param.damageTarget is ShipAPI && param.damageTarget != target){
          //注意光束的damage是只有在造成伤害的那一帧才会赋予一个新的，其他时间为0，数值等同于面板
          //每当战机承受了伤害，同时beam的最终落点不是战机（代表可以穿透战机）时，给beam的最终落点的舰船一个持续0.33秒的减伤，但是战机没有减伤
          val beamTarget = param.damageTarget as ShipAPI
          //一切动作，比如把listener加入beamTarget都在BeamDamageReduce类的init里面，这里只管new
          BeamDamageReduce(param, beamTarget, 0.5f)

        }

        if(sourceModifier > 1f){
          damage.modifier.modifyMult(ID, 1f/sourceModifier)
          return ID
        }else{
          return null
        }
      }

      //对emp电弧-------------------------------------------------------------//
//      if(param is EmpArcEntity && param.source is ShipAPI){
//        val sourceShip = param.source as ShipAPI
//        sourceModifier = sourceShip.mutableStats.damageToFighters.modifiedValue
//
//        if(sourceModifier > 1f){
//          damage.modifier.modifyMult(ID, 1f/sourceModifier)
//          return ID
//        }else{
//          return null
//        }
//      }


      return null
    }
  }

  //这个用于保护大范围aoe的类只适用于无人机，于无人机的ai中施加/维持，而不启用于锚点护盾，锚点护盾不应该可以抵挡爆炸
  //同时也保护电弧
  class GlobalExplosionDetect(val parent: ShipAPI, val fighter: ShipAPI, val lifetime: Float):  DamageTakenModifier, AdvanceableListener{
    companion object{
      const val ID_KEY = "aEP_ExplosionDetect"
    }

    var time = 0f
    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      if(damage.damage < 10f) return null

      //同一个伤害不会反复受到不同来源战机listener的减伤
      if(damage.modifier.multMods.containsKey(ID_KEY)) return null

      //防护爆炸，相同来源的友军伤害不抓
      //这里抓母舰受到的爆炸伤害，然后判断受攻击点和爆心是否划过飞机(或者爆炸就处于飞机碰撞半径中)，如果划过，减伤
      if(param is DamagingProjectileAPI && param.damagedAlready?.size?:0 >= 1 ){
        val isCrossFighterPoint = CollisionUtils.getCollides(param.location, point, fighter.location,fighter.collisionRadius)
        if(isCrossFighterPoint || MathUtils.getDistanceSquared(fighter,param.location) < 25f){
          //如果飞机划过爆炸圈，给予母舰对炸弹的减伤
          damage.modifier.modifyMult(ID_KEY, 0.1f)
          return ID_KEY
        }
      }

      //防护电弧，相同来源的友军伤害不抓，会导致跨平台问题
//      if(param is EmpArcEntity && !isDead(fighter)){
//        //施加一个等量伤害透明电弧对飞机的惩罚
//        if(param.source is ShipAPI){
//          Global.getCombatEngine().spawnEmpArc(
//            (param.source as ShipAPI),
//            fighter.location,fighter,
//            fighter,damage.type,damage.baseDamage,damage.fluxComponent,
//            100f,null,1f,
//            Color(1,1,1,1), Color(1,1,1,1))
//
//        }
//        //转移目标，消除对母舰的伤害
//        param.setTargetToShipCenter(fighter.location,fighter)
//        damage.modifier.modifyMult(ID_KEY, 0.1f)
//        return ID_KEY
//      }

      return null
    }

    override fun advance(amount: Float) {
      time = (time+amount).coerceAtMost(lifetime)

      if(isDead(parent) || isDead(fighter) || time>=lifetime){
        parent.removeListener(this)
      }
    }
  }

}
class BeamDamageReduce(val beam: BeamAPI, val beamTarget: ShipAPI, val lifetime: Float): DamageTakenModifier, AdvanceableListener{
  var time = 0f
  init {
    //如果已经存在，刷新持续时间
    if(beamTarget.customData.containsKey(beam.toString())){
      val listener = (beamTarget.customData[beam.toString()]) as BeamDamageReduce
      listener.time = 0f
    }else{ //不存在就加入自己
      beamTarget.setCustomData(beam.toString(), this)
      beamTarget.addListener(this)
    }
  }

  override fun advance(amount: Float) {
    time += amount
    if(time > lifetime){
      beamTarget.customData.remove(beam.toString())
      beamTarget.listenerManager.removeListener(this)
    }
  }

  override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
    if(param == beam){
      damage.modifier.modifyMult(aEP_ProjectileDenialShield.ID, 0.025f)
      return aEP_ProjectileDenialShield.ID
    }

    return null
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
      const val FUSE_RANGE = 200f
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
          point, vel, 400f, 1f, 0.2f, 4f, Color.white)

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
            0.1f,1f, 3f,
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
            0.1f,0.1f, 2f,
            Color(100, 100, 100, 175))
          angle += 360f / numMax
        }

        //生成弹丸
        val numOfProj = 180
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
          Color.white, Color.white)
        spec.damageType = DamageType.HIGH_EXPLOSIVE
        engine.spawnDamagingExplosion(spec, ship, point)
        engine.combatNotOverFor = engine.combatNotOverFor + 2f
        engine.removeEntity(ship)

      }
    }

    inner class ApproximatePrimer(ship: ShipAPI) : State(ship) {
      override fun advanceImpl(amount: Float){
        //begin
        aEP_Render.openGL11CombatLayerRendering()

        val sprite = Global.getSettings().getSprite("aEP_FX","frame02")
        MagicRender.singleframe(sprite,
          ship.location,
          Vector2f(FUSE_RANGE*2.3f,FUSE_RANGE*2.3f),
          timeEclipsed*60f, Color(255,0,0,105),true)


        for (s in Global.getCombatEngine().ships) {
          if (s.owner != ship.owner && !s.isFighter && !s.isDrone && !s.isShuttlePod) {
            if (MathUtils.getDistance(s, ship.location) <= FUSE_RANGE) {
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
        val sprite = Global.getSettings().getSprite("aEP_FX","frame")
        MagicRender.singleframe(sprite,
          detectPoint,
          Vector2f(80f,80f),
          timeEclipsed*60f, Color(255,0,0,225),true)
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
    const val REPAIR_TIME_REDUCE_MULT = 0.5f

    val MAP = HashMap<String, Float>()
    init {
      MAP["aEP_ftr_bom_nuke"] = 2.5f
      MAP["aEP_ftr_icp_gunship"] = 2f
      MAP["aEP_ftr_ftr_hvfighter"] = 1.5f
      MAP["aEP_ftr_ftr_helicop"] = 1.5f
    }
  }

  override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {
    var modifier = 1f
    //默认10%减伤，乘一个系数
    modifier *= MAP[ship.hullSpec.baseHullId]?: 1f

    ship.mutableStats.effectiveArmorBonus.modifyPercent(id, ARMOR_COMPUTE_PERCENT_BONUS * modifier)
    ship.mutableStats.armorDamageTakenMult.modifyMult(id, 1f - DAMAGE_TAKEN_REDUCE_MULT* modifier)
    ship.mutableStats.hullDamageTakenMult.modifyMult(id, 1f - DAMAGE_TAKEN_REDUCE_MULT * modifier)

    ship.mutableStats.empDamageTakenMult.modifyMult(id,1f - EMP_TAKEN_REDUCE_MULT)
    ship.mutableStats.engineDamageTakenMult.modifyMult(id,1f - WEAPON_DAMAGE_TAKEN_REDUCE_MULT)
    ship.mutableStats.weaponDamageTakenMult.modifyMult(id,1f - WEAPON_DAMAGE_TAKEN_REDUCE_MULT)

    ship.mutableStats.combatEngineRepairTimeMult.modifyMult(id,1f - REPAIR_TIME_REDUCE_MULT)
    ship.mutableStats.combatWeaponRepairTimeMult.modifyMult(id,1f - REPAIR_TIME_REDUCE_MULT)
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?, ship: ShipAPI?): String {
    var reduction = 10f
    ship?.run {
      reduction *= MAP[ship.hullSpec.baseHullId]?: 1f
    }

    return String.format("%.0f", reduction)+"%"
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
        1f * (fluxLevel - 0.5f) * 2f)
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
        if (modular.hullSpec.baseHullId == "aEP_typeL28") {
          modular.giveCommand(ShipCommand.ACCELERATE, null, 0)
        }
      } else if (ship.parentStation.engineController.isTurningLeft) {
        if (modular.hullSpec.baseHullId == "aEP_typeR28") {
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
    return "No hullmod allowed"
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
    const val COLOR_SHIFT_VALUE = 200
    private const val MIN_EMP_ARC_INTERVAL_PER_SEC = 0.25f
    private const val JITTER_THRESHOLD = 0.7f
    //val JITTER_COLOR = Color(200, 200, 250, 200)
    private var id = "aEP_Type28Shield"
  }
}

//模块插件，包括减伤爆炸伤害,禁止v排，同步相位状态，
// 当玩家操控模块的母舰，或者鼠标指向时，显示模块的容量状态
class aEP_Module : aEP_BaseHullMod() {
  companion object {
    const val ID = "aEP_Module"
    const val DAMAGE_MULT = 0.0001f
    const val DAMAGE_RANGE_MULT = 0.1f
  }

  init {
    notCompatibleList.add(HullMods.CONVERTED_HANGAR)
    notCompatibleList.add(HullMods.MAKESHIFT_GENERATOR)
    notCompatibleList.add(HullMods.SHIELD_SHUNT)
    notCompatibleList.add(HullMods.FRONT_SHIELD_CONVERSION)
    notCompatibleList.add(HullMods.OMNI_SHIELD_CONVERSION)
    notCompatibleList.add(HullMods.SAFETYOVERRIDES)
    notCompatibleList.add("escort_package")
  }

  var ID: String = "aEP_Module"
  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String) {
    stats?:return
    stats.dynamic.getStat(EXPLOSION_DAMAGE_MULT)?.modifyMult(ID, DAMAGE_MULT)
    stats.dynamic.getStat(EXPLOSION_RADIUS_MULT)?.modifyMult(ID, DAMAGE_RANGE_MULT)

    stats.ventRateMult.modifyMult(ID,0f)
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {


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
    var shieldCheckTimer = IntervalUtil(1f,1f)

    override fun notifyAboutToTakeHullDamage(param: Any?, ship: ShipAPI?, point: Vector2f?, damageAmount: Float): Boolean {
      ship?:return false
      if(damageAmount >= ship.hitpoints && !shouldEnd){
        //不留残骸
        ship.explosionScale = 0.5f
        ship.explosionFlashColorOverride = Color(1f,1f,1f,0.25f)
        Global.getCombatEngine().removeEntity(ship)
        return false
      }
      return false
    }

    override fun advance(amount: Float) {
      //如果本模块不拥有parent，重置一次状态，并从这里出去
      if(ship.parentStation == null || aEP_Tool.isDead(ship) || aEP_Tool.isDead(ship.parentStation)) {
        ship.setApplyExtraAlphaToEngines(false)
        ship.extraAlphaMult = 1f
        ship.extraAlphaMult2 = 1f
        ship.isPhased = false
        ship.removeListenerOfClass(this::class.java)
        return
      }

      val parent = ship.parentStation
      val stats = ship.mutableStats
      val parentStats = parent.mutableStats

      //模块同步parent的相位和透明度状态
      ship.setApplyExtraAlphaToEngines(true)
      ship.extraAlphaMult = parent.extraAlphaMult
      ship.extraAlphaMult2 = parent.extraAlphaMult2
      ship.isPhased = parent.isPhased
      //母舰相位时，模块禁止自动开火，禁止手动开火
      if(ship.isPhased){
        ship.isHoldFireOneFrame = true
        ship.blockCommandForOneFrame(ShipCommand.FIRE)
      }

      //advance各种计时器和tag效果
      shieldCheckTimer.advance(amount)
      val tags = ship.hullSpec.tags
      for(tag in tags){
        if(tag.startsWith("aEP_ShieldAlwaysOn")){
          val param = tag.split("_")
          val overloadTime = param[2].toFloat()

          ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK)
          ship.blockCommandForOneFrame(ShipCommand.VENT_FLUX)
          val id = "shield_always_on"

          if (ship.fluxTracker.isOverloadedOrVenting) {
            ship.mutableStats.fluxDissipation.modifyMult(id, 10f)
            if(aEP_Combat.getTargetCurrentAimed(id, ship) < 1f){
              aEP_CombatEffectPlugin.addEffect(aEP_Combat.MarkTarget(overloadTime, id, overloadTime+0.1f, ship))
              ship.fluxTracker.stopOverload()
              ship.fluxTracker.beginOverloadWithTotalBaseDuration(overloadTime)
            }

          } else {
            ship.mutableStats.fluxDissipation.modifyMult(id, 1f)
            if (!ship.shield.isOn) {
              ship.shield.toggleOn()
            }
          }

        }

      }

      //如果玩家正在操控parent或者玩家鼠标指向了模块的碰撞圈附近，且模块撑起了护盾，在护盾的尖端显示模块当前容量（防止多个模块和本体重叠）
      var shouldShowStatus = false
      if(Global.getCombatEngine().playerShip == parent) shouldShowStatus = true
      val dist = MathUtils.getDistance(Global.getCombatEngine().playerShip.mouseTarget?: aEP_ID.VECTOR2F_ZERO, ship.location)
      if(dist < ship.collisionRadius + 10f) shouldShowStatus = true
      if(shouldShowStatus){
        var color = Misc.getPositiveHighlightColor()
        if(ship.owner == 1) color = Misc.getNegativeHighlightColor()
        if(ship.owner == 100) color = Misc.getHighlightColor()
        var string = ship.hullSpec.hullName
        for(tag in ship.hullSpec.tags){
          if(tag.startsWith("aEP_Module_display_")) string = tag.replace("aEP_Module_display_","")

        }
        //只有玩家舰船 == ship时才会显示，number是条末端的数字
        MagicUI.drawInterfaceStatusBar(
          parent, ship.id, ship.fluxLevel,
          color, color, ship.hardFluxLevel,
          string, ship.hitpoints.toInt())

        //模块过载时，UI提示多久重新上限
        if(ship.fluxTracker.overloadTimeRemaining > 0f){
          if(shieldCheckTimer.intervalElapsed()){
            val str = String.format("%.0f",ship.fluxTracker.overloadTimeRemaining+0.1f)
            val loc = getExtendedLocationFromPoint(ship.location,ship.facing, ship.shieldRadiusEvenIfNoShield)
            Global.getCombatEngine().addFloatingText(loc, str, 40f, Misc.getNegativeHighlightColor(), ship,1f,0.25f)
          }
        }
      }
    }
  }
}

//飞行坦克系统
class aEP_FlyingTank : aEP_BaseHullMod(){
  companion object{
    const val REPAIR_AMOUNT_PER_SECOND = 20f
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship?:return
    if(!ship.hasListenerOfClass(RangeListener::class.java)){
      ship.addListener(RangeListener(ship, ship.armorGrid.armorRating* REPAIR_AMOUNT_PER_SECOND/100f))
    }
  }

  class RangeListener(val ship: ShipAPI, val repairPerSec:Float) : AdvanceableListener{
    val armorTracker = IntervalUtil(0.5f,1.5f)

    override fun advance(amount: Float) {

      armorTracker.advance(amount)
      if(armorTracker.intervalElapsed()){
       aEP_Tool.findToRepair(
         ship,
         repairPerSec* armorTracker.elapsed,
         1f,0f,10f,0f, false)
      }

    }
  }
}


