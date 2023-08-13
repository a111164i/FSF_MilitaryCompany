package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.*
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.ids.Stats.EXPLOSION_DAMAGE_MULT
import com.fs.starfarer.api.impl.campaign.ids.Stats.EXPLOSION_RADIUS_MULT
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.combat.DamperFieldOmegaStats
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.combat.entities.DamagingExplosion
import combat.impl.VEs.aEP_MovingSmoke
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.*
import combat.util.aEP_DataTool.txt
import combat.util.aEP_Tool.Util.REPAIR_COLOR
import combat.util.aEP_Tool.Util.REPAIR_COLOR2
import combat.util.aEP_Tool.Util.addDebugLog
import combat.util.aEP_Tool.Util.addDebugPoint
import combat.util.aEP_Tool.Util.angleAdd
import combat.util.aEP_Tool.Util.findToRepair
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.getRelativeLocationData
import data.scripts.weapons.aEP_DecoAnimation
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicAnim
import org.magiclib.util.MagicLensFlare
import org.magiclib.util.MagicRender
import org.magiclib.util.MagicUI
import java.awt.Color
import kotlin.math.abs

/**
* 不知道为什么游戏中不显示基础描述，这里只放一些玩家看不到的插件
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
    val MAX_MOVE_TIME = 12f
    val FLUX_INCREASE = 100f
    val RADAR_SPEED = -90f
    val EXPLODSION_DAMAGE_MULT = 0.005f
    val EXPLODSION_RANGE_MULT = 0.5f
  }


  /**
   * 使用这个
   **/
  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship.mutableStats.empDamageTakenMult.modifyMult(ID,0f)
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
        val color = Color(
          0.35f + 0.65f*fluxLevel,
          0.55f,
          0.35f + 0.65f*(1f-fluxLevel),
          (0.2f * shieldLevel * shieldLevel) + MagicAnim.smooth((0.35f*(1f-fluxLevel))))
        ship.shield.innerColor = color
        ship.shield.ringColor = color
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

//吞弹护盾
class aEP_ProjectileDenialShield : aEP_BaseHullMod(){
  companion object{
    const val ID = "aEP_ProjectileDenialShield"
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    if (ship.fluxTracker.isOverloaded && ship.fluxTracker.overloadTimeRemaining > 6f) {
      ship.fluxTracker.setOverloadDuration(6f)
    }

  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
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
      //如果proj可以穿透战机护盾，直接吞掉同时战机自爆
      if(param is DamagingProjectileAPI){
        if(param.projectileSpec?.isPassThroughFightersOnlyWhenDestroyed == false
          && param.projectileSpec?.isPassThroughFighters == true){

          val damage = param.damage.damage
          if(ship.maxFlux - ship.currFlux < damage){
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
          }else{
            ship.fluxTracker.increaseFlux(damage, true)
          }
          Global.getCombatEngine().removeEntity(param)
        }

        if(param is MissileAPI){
          param.spec?.explosionSpec?.minDamage?.div(10f)
          param.spec?.explosionSpec?.maxDamage?.div(10f)
        }

          //能抓到近炸，但是不能修改explosionSpec所以没有意义
        if(param is DamagingExplosion){
          param.damageAmount = param.damageAmount/10f
        }
      }
      if(param is BeamAPI){
        //如果光束可以穿透，最终目标不是自己但是对自己造成了伤害
        //当触发时，降低光束本帧的伤害，同时给自己涨幅能
        if(param.damageTarget is ShipAPI && param.damageTarget != target){
          //注意光束的damage是只有在造成伤害的那一帧才会赋予一个新的，其他时间为0，数值等同于面板
          //每当战机承受了伤害，同时beam的最终落点不是战机（代表可以穿透战机）时，给beam的最终落点的舰船一个持续0.33秒的减伤，但是战机没有减伤
          val beamTarget = param.damageTarget as ShipAPI
          //一切动作，比如把listener加入beamTarget都在BeamDamageReduce类的init里面，这里只管new
          BeamDamageReduce(param, beamTarget, 0.33f)
          return null
        }
      }
      return null
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
      damage.modifier.modifyMult(aEP_ProjectileDenialShield.ID, 0.1f)
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

  }

  override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {
    var modifier = 1f
    when(ship.hullSpec.baseHullId){
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

//模块插件，包括减伤爆炸伤害,禁止v排，同步相位状态，当玩家操控模块的母舰，或者鼠标指向时，显示模块的容量状态
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

    //如果本模块不拥有parent，重置一次状态，并从这里出去
    if(ship.parentStation == null || aEP_Tool.isDead(ship) ) {
      if( !ship.customData.containsKey(ID)){
        ship.setCustomData(ID,1f)
        ship.extraAlphaMult = 1f
        ship.extraAlphaMult2 = 1f
        ship.isPhased = false
      }
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


    if(ship.isPhased){
      //禁止自动开火，禁止手动开火
      ship.isHoldFireOneFrame = true
      ship.blockCommandForOneFrame(ShipCommand.FIRE)
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
      val currFlux = ship.currFlux
      val maxFlux = ship.maxFlux
      val string = ship.hullSpec.hullName
      //只有玩家舰船 == ship时才会显示，number是条末端的数字
      MagicUI.drawInterfaceStatusBar(
        parent,
        ship.id,
        ship.fluxLevel,
        color,
        color,
        ship.hardFluxLevel,
        string,
        ship.hitpoints.toInt())
      //这个方法无法叠加多个
//      MagicUI.drawHUDStatusBar(
//        parent,
//        ship.fluxLevel,
//        color,
//        color,
//        ship.hardFluxLevel,
//        "1",
//        "2",
//        true
//      )
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

//战地重建-AI版
class aEP_EmergencyReconstructAi() : aEP_EmergencyReconstruct(){

  companion object{
    const val ID = "aEP_EmergencyReconstructAi"

    const val REPAIR_TIME_BASE = 10f
    const val REPAIR_TIME_PER_DP = 0.5f

    val CHANGE_DROP_HULLSIZE = HashMap<ShipAPI.HullSize, Float>()

    init {
      CHANGE_DROP_HULLSIZE.put(ShipAPI.HullSize.FIGHTER, 0.33f)
      CHANGE_DROP_HULLSIZE.put(ShipAPI.HullSize.FRIGATE, 0.15f)
      CHANGE_DROP_HULLSIZE.put(ShipAPI.HullSize.DESTROYER, 0.20f)
      CHANGE_DROP_HULLSIZE.put(ShipAPI.HullSize.CRUISER, 0.25f)
      CHANGE_DROP_HULLSIZE.put(ShipAPI.HullSize.CAPITAL_SHIP, 0.33f)
      CHANGE_DROP_HULLSIZE.withDefault { 0.5f }
    }

  }
  constructor(ship: ShipAPI):this(){
    this.ship = ship
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

    if(!ship.hasListenerOfClass(this.javaClass)){
      val listenerClass = aEP_EmergencyReconstructAi(ship)
      val dp =  ship.mutableStats.dynamic.getMod(Stats.DEPLOYMENT_POINTS_MOD).computeEffective(ship.hullSpec.suppliesToRecover)
      listenerClass.repairTimeTotal = REPAIR_TIME_BASE + REPAIR_TIME_PER_DP * dp
      listenerClass.activeChanceDropPerUse = CHANGE_DROP_HULLSIZE[ship.hullSpec.hullSize]?:0.5f
      ship.addListener(listenerClass)
    }

  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

  }

  override fun advanceInCampaign(member: FleetMemberAPI, amount: Float) {
    if(!member.variant.hasTag(Tags.VARIANT_UNBOARDABLE)){
      member.variant.addTag(Tags.VARIANT_UNBOARDABLE)
    }
  }

  /**
   * 这里开始是listener的部分，各个船独立，改变量都要在listener里面动，hullmod的advance是全局的
   * */
  override fun advance(amount: Float) {
    reconstructTimer -= amount
    reconstructTimer.coerceAtLeast(0f).coerceAtMost(99f)
    //维修模式时
    if(reconstructTimer > 0f){
      duringRepairEffect(amount)

    }else if(didRepair ){//退出维修模式时运行一次
      onceWhenOutRepair()

    }

    if(aEP_Tool.isDead(ship)){
      ship.removeCustomData(ACTIVE_KEY)
      ship.removeListener(this)
    }

    //addDebugLog(reconstructTime.toString())
  }

  //if true is returned, the hull damage to be taken is negated.
  override fun notifyAboutToTakeHullDamage(param: Any?, ship: ShipAPI, point: Vector2f, damageAmount: Float): Boolean {
    //进入维修模式的那一刻运行一次
    if(damageAmount >= ship.hitpoints && reconstructTimer <= 0f){

//      aEP_CombatEffectPlugin.addEffect(aEP_Combat.StandardTeleport(1f,ship,
//        Vector2f(Global.getCombatEngine().playerShip.mouseTarget),ship.facing))
      val test = MathUtils.getRandomNumberInRange(0f,100f)
      val threshold = (activeChance * 100f)
      if(test <= threshold){
        onceWhenInRepair()

        var txt = String.format("Reconstruct Test: %.0f",test)
        if(threshold < 100) txt += String.format(" + %.0f",100-threshold)
        Global.getCombatEngine().addFloatingText(ship.location, txt, 30f, Color.green, ship, 1f,5f)

      }else{

        onceWhenFailTest()

        var txt = String.format("Reconstruct Test: %.0f",test)
        if(threshold < 100) txt += String.format(" + %.0f",100-threshold)
        Global.getCombatEngine().addFloatingText(ship.location, txt, 30f, Color.red, ship, 1f,5f)

      }
    }

    if(reconstructTimer > 0f) return true
    return false
  }

  override fun duringRepairEffect(amount: Float){
    didRepair = true
    ship.mutableStats.hullDamageTakenMult.modifyMult(aEP_EmergencyReconstruct.ID, 0f)
    ship.mutableStats.armorDamageTakenMult.modifyMult(aEP_EmergencyReconstruct.ID, 0f)
    ship.mutableStats.combatWeaponRepairTimeMult.modifyFlat(aEP_EmergencyReconstruct.ID, Float.MAX_VALUE)
    ship.mutableStats.combatEngineRepairTimeMult.modifyFlat(aEP_EmergencyReconstruct.ID, Float.MAX_VALUE)
    ship.mutableStats.engineDamageTakenMult.modifyMult(aEP_EmergencyReconstruct.ID, 0f)

    for(e in ship.engineController.shipEngines){
      if(!e.isDisabled){
        e.applyDamage(999f, ship)
        e.disable()
      }
    }
    for(w in ship.allWeapons){
      if(!w.isDisabled) {
        w.disable()
      }
    }

    //禁止一切操作
    ship.blockCommandForOneFrame(ShipCommand.FIRE)
    ship.blockCommandForOneFrame(ShipCommand.VENT_FLUX)
    ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM)
    ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK)
    ship.blockCommandForOneFrame(ShipCommand.ACCELERATE)
    ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS)
    ship.blockCommandForOneFrame(ShipCommand.PULL_BACK_FIGHTERS)

    var level = 1f
    val fadeTime = 4f
    if(reconstructTimer > repairTimeTotal-fadeTime) level =  (repairTimeTotal - reconstructTimer)/ fadeTime
    ship.fadeToColor(aEP_EmergencyReconstruct.ID, Color(75, 75, 75, 255), 1f, 1f, level)
    ship.isJitterShields = false
    ship.setCircularJitter(true)
    ship.setJitterUnder(aEP_EmergencyReconstruct.ID, REPAIR_COLOR2, level, 36, 8f+ship.collisionRadius*0.1f)

    //相位效果
    ship.extraAlphaMult = level * 0.5f + 0.5f
    ship.extraAlphaMult2 = level * 0.5f + 0.5f
    ship.setApplyExtraAlphaToEngines(true)
    if(level >= 1f){
      if(ship.phaseCloak != null && ship.phaseCloak.isActive) ship.phaseCloak.deactivate()
      if(ship.shield != null && ship.shield.isOn) ship.shield.toggleOff()
      ship.isPhased = true
      ship.collisionClass = CollisionClass.NONE
    }

    repairTimer.advance(amount)
    if(repairTimer.intervalElapsed()){
      ship.velocity.scale(0.75f)
      ship.angularVelocity *= 0.75f
      val repaired = findToRepair(ship,
        ((ship.armorGrid.maxArmorInCell*(ship.armorGrid.grid.size * ship.armorGrid.grid[0].size)+ship.maxHitpoints) * 0.5f)/repairTimeTotal,
        1f,1f,100f,1f)
      if(repaired >= 1f){
        //reconstructTime = 0.1f
      }
    }
  }

  override fun onceWhenOutRepair(){

    ship.mutableStats.hullDamageTakenMult.unmodify(aEP_EmergencyReconstruct.ID)
    ship.mutableStats.armorDamageTakenMult.unmodify(aEP_EmergencyReconstruct.ID)
    ship.mutableStats.combatWeaponRepairTimeMult.unmodify(aEP_EmergencyReconstruct.ID)
    ship.mutableStats.combatEngineRepairTimeMult.unmodify(aEP_EmergencyReconstruct.ID)
    ship.mutableStats.engineDamageTakenMult.unmodify(aEP_EmergencyReconstruct.ID)

    ship.removeCustomData(ACTIVE_KEY)

    for(e in ship.engineController.shipEngines){
      if(e.isDisabled) e.repair()
    }
    for(w in ship.allWeapons){
      if(w.isDisabled) w.repair()
    }

    //取消相位效果
    ship.isPhased = false
    ship.collisionClass = CollisionClass.SHIP
    ship.extraAlphaMult = 1f
    ship.extraAlphaMult2 = 1f

    //如果是因为舰船被摧毁导致的运行，不需要清除损伤贴图
    if(didRepair ){
      didRepair = false
      ship.syncWeaponDecalsWithArmorDamage()
      ship.clearDamageDecals()
    }
  }

  override fun onceWhenInRepair(){

    ship.setCustomData(ACTIVE_KEY,true)

    activeChance -= activeChanceDropPerUse
    activeChance.coerceAtLeast(0f).coerceAtMost(1f)

    ship.hitpoints = 1f
    reconstructTimer = repairTimeTotal
    ship.engineController.forceFlameout(true)
    for(s in Global.getCombatEngine().ships){
      if(s.shipTarget == ship){
        s.shipTarget = null
      }
    }


    val scaffoldSize = 100f
    val cellSize = ship.armorGrid.cellSize
    val x = ship.armorGrid.leftOf * cellSize
    val y = ship.armorGrid.above * cellSize
    val leftTopAbs = Vector2f.add(VectorUtils.rotate(Vector2f(-x,y),ship.facing-90f), ship.location,null)

    val leftTopRel = getRelativeLocationData(leftTopAbs,ship,false)
    val centerRel = getRelativeLocationData(ship.location, ship,false)

    val xLength = (ship.armorGrid.leftOf + ship.armorGrid.rightOf)*cellSize
    val xNum =   (xLength/scaffoldSize).toInt() + 1
    val xStep = xLength/xNum

    val yLength = (ship.armorGrid.above + ship.armorGrid.below)*cellSize
    val yNum =   (yLength/scaffoldSize).toInt() + 1
    val yStep = yLength/yNum

    for(i in 0..yNum){
      val yMoveOffset = (yNum/2f - i) * yStep
      val yTime = abs(yMoveOffset/yStep)
      for(j in 0..xNum){
        val xOffset = (xNum/2f - j) * xStep
        val xTime = abs(xOffset/xStep) + yTime
        //渲染x支架
        aEP_CombatEffectPlugin.addEffect(RepairScaffoldUp(
          xTime,1f, reconstructTimer - i * 0.5f,
          ship, xOffset, yMoveOffset, centerRel))
      }
      //渲染水平支架，后于x支架，要盖在上面
      aEP_CombatEffectPlugin.addEffect(RepairSideFrameUp(
        yTime,  xNum/2f, reconstructTimer - i * 0.5f,ship,
        0f,yMoveOffset,(xLength+scaffoldSize)/2f,centerRel))
    }

    var i = xLength * yLength
    while (i > 0){
      i -= scaffoldSize * scaffoldSize * 10
      aEP_CombatEffectPlugin.addEffect(RepairSideMoving(1f,reconstructTimer - yNum * 0.5f,ship,centerRel))
    }
  }

  override fun onceWhenFailTest(){
    ship.removeListener(this)
  }

  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return false
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
  }
}
