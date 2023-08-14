package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.lwjgl.util.vector.Vector2f
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.util.ColorShifter
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_DataTool
import combat.util.aEP_ID
import combat.util.aEP_Render
import combat.util.aEP_Tool
import org.lazywizard.lazylib.FastTrig
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.opengl.GL11
import shaders.aEP_BloomMask
import java.awt.Color
import java.util.*
import kotlin.math.absoluteValue

class aEP_ControledShield internal constructor() : aEP_BaseHullMod() {
  companion object {
    private val mag: MutableMap<HullSize, Float> = HashMap()
    //REDUCE_MULT 是 1 - x
    private const val REDUCE_MULT = 0.75f
    private const val UPKEEP_PUNISH = 2f
    private const val MAX_WEAPON_RANGE_CAP = 900f
    val SHIFT_COLOR = Color(105,155,255,195)
    private const val COLOR_RECOVER_INTERVAL = 0.025f //by seconds
    const val ID = "aEP_ControledShield"

    init {
      mag[HullSize.FIGHTER] = 800f
      mag[HullSize.FRIGATE] = 900f
      mag[HullSize.DESTROYER] = 1000f
      mag[HullSize.CRUISER] = 1100f
      mag[HullSize.CAPITAL_SHIP] = 1100f
    }
  }

  init {
    haveToBeWithMod.add("aEP_MarkerDissipation")
    notCompatibleList.add("hardenedshieldemitter")
    notCompatibleList.add(aEP_ResistShield.ID)
  }

  /**
   * 使用这个
   *
   * @param ship
   * @param id
   */
  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
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
      val dist = MathUtils.getDistance(ship.location, proj.location) - ship.shield.radius
      if(dist < (mag[ship.hullSize] ?: 1000f)){
        //发射时处于范围外
        val spawnDist = MathUtils.getDistance(ship.location, proj.spawnLocation) - ship.shield.radius
        if(spawnDist > (mag[ship.hullSize] ?: 1000f)){
          //跳过已经被上过标记的
          if(proj.customData.containsKey(ID)) continue
          proj.setCustomData(ID,1f)
          aEP_CombatEffectPlugin.addEffect(ShowLockCircle(proj, ship))
        }
      }
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
    if (index == 0) return "" + mag[HullSize.FRIGATE]!!.toInt()
    if (index == 1) return "" + mag[HullSize.DESTROYER]!!.toInt()
    if (index == 2) return "" + mag[HullSize.CRUISER]!!.toInt()
    if (index == 3) return "" + mag[HullSize.CAPITAL_SHIP]!!.toInt()
    if (index == 4) return "" + (100f - REDUCE_MULT * 100f).toInt() + "%"
    if (index == 5) return "" + (MAX_WEAPON_RANGE_CAP).toInt()
    return ""
  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return  true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {

    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_ControledShield01"), 5f, arrayOf(Color.green, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", mag[hullSize]?:1000f),
      String.format("%.0f", REDUCE_MULT*100f) +"%")
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_ControledShield02"), 5f, arrayOf(Color.red, highLight),
      aEP_ID.HULLMOD_POINT,
      aEP_DataTool.txt("base"),
      String.format("%.0f", MAX_WEAPON_RANGE_CAP))
    //显示不兼容插件
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("not_compatible") +"{%s}", 5f, arrayOf(Color.red, highLight),
      aEP_ID.HULLMOD_POINT,
      showModName(notCompatibleList))


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
    init {
      radius = rad + ship.collisionRadius
      layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
    }


    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      //谨防有人中途取消护盾
      ship.shield?:return null

      param?: return null
      if (param is DamagingProjectileAPI && shieldHit) {
        val from = param.spawnLocation?:param.location
        if (ship.isAlive) {
          val dist = Misc.getDistance(from, point)
          if (dist >= rad) {
            ship.mutableStats.shieldDamageTakenMult.modifyMult(ID,1f - REDUCE_MULT)
            ship.mutableStats.shieldUpkeepMult.modifyFlat(ID, UPKEEP_PUNISH)
            shifter.shift( ID, SHIFT_COLOR,0.001f,0.1f,0.6f)
            ringShifter.shift(ID,SHIFT_COLOR,0.001f,0.1f,0.6f)
            timer = COLOR_RECOVER_INTERVAL
            didChange = true
            return null
          }
        }
      }
      if (param is BeamAPI && shieldHit) {
        val from = param.from
        if (ship.isAlive) {
          val dist = Misc.getDistance(from, point)
          if (dist >= rad) {
            ship.mutableStats.shieldDamageTakenMult.modifyMult(ID,1f - REDUCE_MULT)
            ship.mutableStats.shieldUpkeepMult.modifyFlat(ID, UPKEEP_PUNISH)
            shifter.shift( ID, SHIFT_COLOR,0.001f,0.05f,0.4f)
            ringShifter.shift(ID,SHIFT_COLOR,0.001f,0.05f,0.4f)
            timer = COLOR_RECOVER_INTERVAL
            didChange = true
            return null
          }
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
      if(weapon.slot.isSystemSlot) return 0f
      if(weapon.slot.isBuiltIn) return 0f
      if(weapon.type == WeaponAPI.WeaponType.MISSILE) return 0f

      val baseRange = weapon.spec?.maxRange ?: MAX_WEAPON_RANGE_CAP
      if(baseRange > MAX_WEAPON_RANGE_CAP){
        return MAX_WEAPON_RANGE_CAP-baseRange
      }
      return 0f
    }

    override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
      if(Global.getCombatEngine().playerShip == ship
        && ship.shield?.isOn == true){

        //begin
        aEP_Render.openGL11CombatLayerRendering()

        val center = Vector2f(ship.location)

        val width = 20f + ship.collisionRadius * 0.1f
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
          GL11.glColor4f(0.41f,0.3f,1f, 0.25f)
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

        //画完全的圆
        val edge = 6
        val angleStep = 360f/edge
        var startingAngle = time * 180f

        GL11.glBegin(GL11.GL_QUAD_STRIP)
        for (i in 0..edge) {
          val a = i * angleStep + startingAngle
          val pointFar = aEP_Tool.getExtendedLocationFromPoint(entity!!.location, a, largeRad)
          GL11.glColor4f(0.41f,0.3f,1f, 0.75f)
          GL11.glVertex2f(pointFar.x, pointFar.y)

          val pointNear = aEP_Tool.getExtendedLocationFromPoint(entity!!.location, a, smallRad)
          GL11.glColor4f(0.41f,0.3f,1f, 0.5f)
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