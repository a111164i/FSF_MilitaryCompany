package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.lwjgl.util.vector.Vector2f
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.util.ColorShifter
import data.scripts.utils.aEP_BaseCombatEffect
import data.scripts.aEP_CombatEffectPlugin
import data.scripts.utils.aEP_DataTool
import data.scripts.utils.aEP_ID
import data.scripts.utils.aEP_Render
import data.scripts.utils.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue
import kotlin.math.pow

class aEP_ShieldControlled internal constructor() : aEP_BaseHullMod() {
  companion object {
    private val mag: MutableMap<HullSize, Float> = HashMap()
    init {
      mag[HullSize.FIGHTER] = 800f
      mag[HullSize.FRIGATE] = 900f
      mag[HullSize.DESTROYER] = 950f
      mag[HullSize.CRUISER] = 1050f
      mag[HullSize.CAPITAL_SHIP] = 1150f
    }
    private const val REDUCE_MULT = 0.5f

    //如果距离小于这个数，也会提供减伤
    private const val MIN_DAMAGE_RANGE = 0f

    //外环，更远的弹丸提供更多减伤
    private const val OUTER_RANGE = 2400f
    private const val OUTER_DAMAGE_REDUCE_MULT = 0f

    //REDUCE_MULT 是 1 - x
    private const val UPKEEP_PUNISH = 0f
    private const val MAX_WEAPON_RANGE_CAP = 900f
    val SHIFT_COLOR = Color(105,155,255,205)
    private const val COLOR_RECOVER_INTERVAL = 0.025f //by seconds

    //基础降低来自导弹的伤害
    private const val BASE_DAMAGE_REDUCE_MULT = 0.20f //
    const val ID = "aEP_ShieldControlled"

    fun shouldModRange(w: WeaponAPI): Boolean{
      val spec = w.spec
      val slot = w.slot
      if(spec.maxRange <= MAX_WEAPON_RANGE_CAP) return false
      if(w.type != WeaponAPI.WeaponType.BALLISTIC && w.type != WeaponAPI.WeaponType.ENERGY ) return false
      return true
    }

  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.HARDENED_SHIELDS)
    notCompatibleList.add(aEP_ShieldFloating.ID)

    allowOnHullsize[HullSize.FRIGATE] = true
    allowOnHullsize[HullSize.DESTROYER] = true
    allowOnHullsize[HullSize.CRUISER] = true
    allowOnHullsize[HullSize.CAPITAL_SHIP] = true

    requireShield = true
  }

  /**
   * 使用这个
   *
   * @param ship
   * @param id
   */
  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    ship.mutableStats.missileShieldDamageTakenMult.modifyMult(ID, 1f - BASE_DAMAGE_REDUCE_MULT)
  }

  /**
   * 加入listener使用这个
   */
  override fun applyEffectsAfterShipAddedToCombatEngine(ship: ShipAPI, id: String) {
    if (ship.shield == null || ship.shield.type == ShieldAPI.ShieldType.NONE) {
      return
    }

    if(!ship.customData.containsKey(ID)){
      val c = DamageTakenMult(ship, mag[ship.hullSize]?:1000f)
      ship.addListener(c)
      aEP_CombatEffectPlugin.addEffect(c)
      ship.setCustomData(ID,1f)
    }
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    //advance部分只负责打框的视觉效果，故不是玩家开船就不需要执行
    if(Global.getCombatEngine().playerShip != ship) return

    if(ship.shield != null && ship.shield.isOn)
    for(proj in Global.getCombatEngine().projectiles){
      //目前处于范围内
      val distSq = MathUtils.getDistanceSquared(ship.location, proj.location) - ship.shield.radius
      if(distSq < (mag[ship.hullSize] ?: 1000f).pow(2)){
        //无视导弹，和来源于导弹的弹丸
        if(proj is MissileAPI || proj.isFromMissile) continue

        //发射时处于范围外
        val spawnDistSq = MathUtils.getDistanceSquared(ship.location, proj.spawnLocation) - ship.shield.radius
        if(spawnDistSq > (mag[ship.hullSize] ?: 1000f).pow(2)){
          //跳过已经被上过标记的
          if(proj.customData.containsKey(ID)) continue
          proj.setCustomData(ID,1f)
          aEP_CombatEffectPlugin.addEffect(ShowLockCircle(proj, ship))
        }
      }
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
    if (index == 0) return String.format("%.0f", mag[hullSize]?:1000f)
    if (index == 1) return String.format("-%.0f", REDUCE_MULT*100f)+"%"
    if (index == 2) return String.format("-%.0f", BASE_DAMAGE_REDUCE_MULT*100f)+"%"
    if (index == 3) return String.format("%.0f", MAX_WEAPON_RANGE_CAP)

    return ""
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {

    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highlight = Misc.getHighlightColor()
    val negativeHighlight = Misc.getNegativeHighlightColor()

    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()

    val titleTextColor: Color = faction.color
    val factionColor: Color = faction.baseUIColor
    val factionDarkColor = faction.darkUIColor
    val factionBrightColor = faction.brightUIColor

    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, PARAGRAPH_PADDING_SMALL)

    //表格显示所有受到射程惩罚的武器
    //用表格显示总装填率的最大值，回复速度，最大消耗速度
    val affectedWeaponList = ArrayList<WeaponSpecAPI>()
    ship?.run {
      for(w in ship.allWeapons){
        if(!shouldModRange(w)) continue
        if(!affectedWeaponList.contains(w.spec)) affectedWeaponList.add(w.spec)
      }
    }
    if(!affectedWeaponList.isEmpty()){
      val col2W0 = 120f
      //第一列显示的名称，尽可能可能的长
      val col1W0 = (width - col2W0 - PARAGRAPH_PADDING_BIG)
      tooltip.beginTable(
        factionColor, factionDarkColor, factionBrightColor,
        TEXT_HEIGHT_SMALL, true, true,
        *arrayOf<Any>(
          "Weapon Spec", col1W0,
          "Punish", col2W0)
      )
      for(spec in affectedWeaponList){
        val punish = spec.maxRange - MAX_WEAPON_RANGE_CAP
        tooltip.addRow(
          Alignment.MID, highlight, spec.weaponName,
          Alignment.MID, negativeHighlight, String.format("-%.0f", punish),
        )
      }
      tooltip.addTable("", 0, PARAGRAPH_PADDING_SMALL)
    }

    //tooltip.beginTable(factionColor, )
    //显示不兼容插件
    showIncompatible(tooltip)


    //tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor, Alignment.MID, 5f)
    //val image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec(ID).spriteName, 48f)

    //tooltip.addImageWithText(5f)

    //额外灰色说明
    //tooltip.addPara(aEP_DataTool.txt("aEP_RapidDissipate02"), Color.gray, 5f)

  }

  internal class DamageTakenMult(private val ship: ShipAPI, val rad: Float) : DamageTakenModifier, AdvanceableListener, WeaponBaseRangeModifier, aEP_BaseCombatEffect(0f, ship) {
    private var timer = 0f
    private var didChange = false
    private val shifter = ColorShifter(ship.shield.innerColor)
    private val ringShifter = ColorShifter(ship.shield.ringColor)

    val reduceRangeSq = rad * rad
    val outerRangeSq = OUTER_RANGE * OUTER_RANGE
    val minRangeSq = MIN_DAMAGE_RANGE * MIN_DAMAGE_RANGE

    init {
      radius = rad + ship.collisionRadius
      layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
    }

    fun changeShield(distSq:Float, damage: DamageAPI, isBeam:Boolean) : Boolean{
      var alpha = 0.6f
      if(isBeam) alpha = 0.4f

      if(distSq >= outerRangeSq){
        damage.modifier.modifyMult(ID, 1f - OUTER_DAMAGE_REDUCE_MULT)
        shifter.shift( ID, SHIFT_COLOR,0.001f,0.1f,alpha)
        ringShifter.shift(ID,SHIFT_COLOR,0.001f,0.1f,alpha)
        timer = COLOR_RECOVER_INTERVAL
        didChange = true
        return true
      }

      if (distSq >= reduceRangeSq || distSq < minRangeSq) {
        damage.modifier.modifyMult(ID, 1f - REDUCE_MULT)
        shifter.shift( ID, SHIFT_COLOR,0.001f,0.1f,alpha)
        ringShifter.shift(ID,SHIFT_COLOR,0.001f,0.1f,alpha)
        timer = COLOR_RECOVER_INTERVAL
        didChange = true
        return true
      }
      return false
    }

    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      //谨防有人中途取消护盾
      ship.shield?:return null

      param?: return null
      //不对导弹减伤，不对来源于导弹弹丸减伤
      if (param is DamagingProjectileAPI && param !is MissileAPI && !param.isFromMissile && shieldHit) {
        val from = param.spawnLocation?:param.location
        if (ship.isAlive) {
          val distSq = Misc.getDistanceSq(from, point)
          if(changeShield(distSq,damage, false)) return ID
        }
      }
      if (param is BeamAPI && shieldHit ) {

        //如果光束来源于dem，固定减伤
        if(param.source?.hasTag(Tags.VARIANT_FX_DRONE) == true){
          damage.modifier.modifyMult(ID, 1f - BASE_DAMAGE_REDUCE_MULT)
          return ID
        }

        val from = param.from
        if (ship.isAlive) {
          val distSq = Misc.getDistanceSq(from, point)
          if(changeShield(distSq, damage,true)) return ID
        }
      }
      return null
    }

    override fun advance(amount: Float) {
      //谨防有人中途取消护盾
      ship.shield?:return

      shifter.advance(amount)
      ringShifter.advance(amount)
      ship.shield.innerColor = shifter.curr
      ship.shield.ringColor = ringShifter.curr

      timer -= amount
      timer = MathUtils.clamp(timer,0f, COLOR_RECOVER_INTERVAL)
      if (timer <= 0f && didChange) {
        ship.mutableStats.shieldDamageTakenMult.unmodify(ID)
        ship.mutableStats.shieldUpkeepMult.unmodify(ID)
        didChange = false
      }

    }

    override fun getWeaponBaseRangePercentMod(ship: ShipAPI?, weapon: WeaponAPI?): Float {
      return 0f
    }

    override fun getWeaponBaseRangeMultMod(ship: ShipAPI?, weapon: WeaponAPI?): Float {
      return 1f
    }

    override fun getWeaponBaseRangeFlatMod(ship: ShipAPI?, weapon: WeaponAPI?): Float {
      weapon?:return 0f
      if(!shouldModRange(weapon)) return 0f

      val baseRange = weapon.spec?.maxRange ?: MAX_WEAPON_RANGE_CAP
      if(baseRange > MAX_WEAPON_RANGE_CAP){
        return MAX_WEAPON_RANGE_CAP-baseRange
      }
      return 0f
    }

    override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {

      if(Global.getCombatEngine().playerShip == ship
        && ship.shield?.isOn == true &&  ship.ai == null){

        //begin
        aEP_Render.openGL11CombatLayerRendering()

        val center = Vector2f(ship.location)

        val width = 25f
        val largeRad = (rad + ship.shield.radius)
        val smallRad = largeRad - width


        //画完全的圆
        val numOfVertex = (rad/25f).toInt()
        val angleStep = 3f
        var angle = ship.shield.facing - ship.shield.activeArc/2f
        var drawnAngle = 0f

        GL11.glBegin(GL11.GL_QUAD_STRIP)
        while (drawnAngle < ship.shield.activeArc + angleStep) {
          val a = angle + drawnAngle
          drawnAngle += angleStep
          val pointFar = aEP_Tool.getExtendedLocationFromPoint(center, a, largeRad)
          GL11.glColor4f(0.41f,0.3f,1f, 0.15f)
          GL11.glVertex2f(pointFar.x, pointFar.y)

          val pointNear = aEP_Tool.getExtendedLocationFromPoint(center, a, smallRad)
          GL11.glColor4f(0.41f,0.3f,1f, 0f)
          GL11.glVertex2f(pointNear.x, pointNear.y)
        }
        GL11.glEnd()

        aEP_Render.closeGL11()
      }
    }
  }

  class ShowLockCircle(entity: CombatEntityAPI,val toShip: ShipAPI) : aEP_BaseCombatEffect(0f, entity){

    init {
      layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
    }

    override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {

      //如果玩家切到别的船，原先的警示效果立刻作废
      if (Global.getCombatEngine().playerShip != toShip){
        shouldEnd = true
        return
      }

      //只有在护盾开启时渲染
      if(toShip.shield?.isOn == true){
        //如果弹丸不在盾角内，也不渲染
        val toProjAngle = VectorUtils.getAngle(toShip.location, entity!!.location)
        val angleDist = MathUtils.getShortestRotation(toShip.shield.facing, toProjAngle).absoluteValue
        if(angleDist > toShip.shield.activeArc/2f) return

        //开始画图
        aEP_Render.openGL11CombatLayerRendering()

        var rad = (entity!!.collisionRadius).coerceAtLeast(8f).coerceAtMost(44f) + 4f
        val animationTime = 0.33f
        if(time < animationTime){
          rad += 36f * (animationTime - time)/animationTime
        }

        val width = 5f
        val largeRad = (width + rad)
        val smallRad = largeRad - width

        var alphaMult = 1f
        if(entity is DamagingProjectileAPI){
          val proj = entity as DamagingProjectileAPI
          var d = proj.damage.baseDamage
          if(proj.damage.type == DamageType.FRAGMENTATION) d *= 0.25f
          if(proj.damage.type == DamageType.ENERGY) d *= 0.75f
          alphaMult = 0.25f
          if(d > 500f) alphaMult = 1f
          else if(d > 100f){
            alphaMult = ((d - 100f)/400f) * 0.75f + 0.25f
           }else{
          }

        }

        //画完全的圆
        val edge = 6
        val angleStep = 360f/edge
        var startingAngle = time * 180f

        GL11.glBegin(GL11.GL_QUAD_STRIP)
        for (i in 0..edge) {
          val a = i * angleStep + startingAngle
          val pointFar = aEP_Tool.getExtendedLocationFromPoint(entity!!.location, a, largeRad)
          GL11.glColor4f(0.41f,0.3f,1f, 0.75f * alphaMult)
          GL11.glVertex2f(pointFar.x, pointFar.y)

          val pointNear = aEP_Tool.getExtendedLocationFromPoint(entity!!.location, a, smallRad)
          GL11.glColor4f(0.41f,0.3f,1f, 0.5f * alphaMult)
          GL11.glVertex2f(pointNear.x, pointNear.y)
        }
        GL11.glEnd()

        aEP_Render.closeGL11()
      }
    }

    override fun readyToEnd() {
      entity!!.removeCustomData(ID)
    }
  }


}