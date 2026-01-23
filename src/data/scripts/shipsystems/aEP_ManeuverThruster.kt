package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import data.scripts.aEP_CombatEffectPlugin.Mod.addEffect
import data.scripts.utils.aEP_AngleTracker
import data.scripts.utils.aEP_BaseCombatEffect
import data.scripts.utils.aEP_DataTool
import data.scripts.utils.aEP_ID.Companion.FX_THRUSTER_PATH
import data.scripts.utils.aEP_Tool
import org.lazywizard.lazylib.FastTrig
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import sound.F
import java.awt.Color

class aEP_ManeuverThruster:  BaseShipSystemScript() {

  companion object{
    const val MAX_SPEED_FLAT_BONUS = 250f
    const val MAX_TURN_FLAT_BONUS = 30f
    private const val EFFECT_KEY = "aEP_ManeuverThrusterEffect"
  }

  var didRollBack = false
  override fun apply(stats: MutableShipStatsAPI?, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    val ship = (stats?.entity?: return)as ShipAPI

    val controller = ship.customData[EFFECT_KEY] as? ManeuverThrusterEffect
      ?: ManeuverThrusterEffect(ship).also {
        ship.setCustomData(EFFECT_KEY, it)
        addEffect(it)
      }

    controller.systemActive = effectLevel > 0f

    if (state == ShipSystemStatsScript.State.OUT) {
      stats.maxSpeed.unmodify(id)
      stats.maxTurnRate.unmodify(id)
    }
    else {
      didRollBack = false
      stats.maxSpeed.modifyFlat(id, MAX_SPEED_FLAT_BONUS)
      stats.acceleration.modifyFlat(id, MAX_SPEED_FLAT_BONUS * 2f * effectLevel)
      stats.deceleration.modifyFlat(id, MAX_SPEED_FLAT_BONUS * 2f * effectLevel)

      stats.maxTurnRate.modifyFlat(id, MAX_TURN_FLAT_BONUS)
      stats.maxTurnRate.modifyPercent(id, 100f)
      stats.turnAcceleration.modifyFlat(id, MAX_TURN_FLAT_BONUS * 2f * effectLevel)
      stats.turnAcceleration.modifyPercent(id, 200f * effectLevel)
    }
    if(effectLevel <= 0f && !didRollBack){
      didRollBack = true
      stats.maxSpeed.unmodify(id)
      stats.maxTurnRate.unmodify(id)
      stats.turnAcceleration.unmodify(id)
      stats.acceleration.unmodify(id)
      stats.deceleration.unmodify(id)
    }
  }

  private class ManeuverThrusterEffect(val ship: ShipAPI) : aEP_BaseCombatEffect(0f, ship){
    private val engines = ArrayList<EngineVisual>()
    val arcOverrides = HashMap<String, Float>()
    var systemActive = false

    init {
      buildEngineList()
    }

    fun getEngines(): List<EngineVisual> = engines

    fun setArcOverride(slotId: String, arc: Float){
      arcOverrides[slotId] = arc
      engines.firstOrNull { it.slotId == slotId }?.updateArc(arc)
    }

    private fun buildEngineList(){
      if(engines.isNotEmpty()) return
      for(slot in ship.hullSpec.allWeaponSlotsCopy){
        if(!slot.isDecorative) continue
        val loc = slot.computePosition(ship)
        var closest: ShipEngineAPI? = null
        var closestDistSq = Float.MAX_VALUE
        for(e in ship.engineController.shipEngines){
          val distSq = MathUtils.getDistanceSquared(loc,e.location)
          if(distSq < closestDistSq){
            closestDistSq = distSq
            closest = e
          }
        }
        val engine = closest ?: continue
        val visual = EngineVisual(
          slot,
          ship,
          engine,
          slot.angle,
          180f, slot.arc)
        engines.add(visual)
      }
    }

    override fun advanceImpl(amount: Float) {
      if(aEP_Tool.isDead(ship)){
        shouldEnd = true
        return
      }

      val (targetAngle, hasInput) = computeDesiredAngle()
      val toAngle = if(hasInput) (targetAngle + 180f + 360f) % 360f else ship.facing
      if(hasInput) ship.engineController.forceShowAccelerating()

      engines.forEach { it.advance(amount, toAngle, hasInput) }
    }

    private fun computeDesiredAngle(): Pair<Float, Boolean> {
      val ctrl = ship.engineController ?: return ship.facing to false
      val facing = ship.facing

      // combine strafe + accel/back
      var x = 0f; var y = 0f
      fun addDir(a: Float) {
        x += FastTrig.cos(Math.toRadians(a.toDouble())).toFloat()
        y += FastTrig.sin(Math.toRadians(a.toDouble())).toFloat()
      }
      if (ctrl.isStrafingLeft) addDir(facing + 90f)
      if (ctrl.isStrafingRight) addDir(facing - 90f)
      if (ctrl.isAccelerating) addDir(facing)
      if (ctrl.isAcceleratingBackwards) addDir(facing + 180f)
      if (ctrl.isTurningLeft) addDir(facing + 45f)
      if (ctrl.isTurningRight) addDir(facing - 45f)

      if (x == 0f && y == 0f) return ship.facing to false
      return VectorUtils.getFacing(Vector2f(x, y)) to true
    }
    override fun readyToEnd() {
      ship.customData.remove(EFFECT_KEY)
    }
  }

  private class EngineVisual(

    private val slot: WeaponSlotAPI,
    private val ship: ShipAPI,
    private val engine: ShipEngineAPI,
    private val slotFacing: Float,
    private val rotateSpeed : Float,
    private val arc : Float
  ){

    private val sprite = Global.getSettings().getSprite(FX_THRUSTER_PATH)
    val slotId: String = slot.id
    private val angleData = aEP_AngleTracker(0f, 0f, rotateSpeed, arc/2, -arc/2)

    fun updateArc(arc: Float){
      angleData.max = arc/2f
      angleData.min = -arc/2f
    }

    fun convertTargetAngleToSlotSpace(targetAngle: Float): Float{
      // Use slot base angle (slotFacing + ship.facing) to pick the nearest direction within arc
      val baseAbsAngle = (slotFacing + ship.facing) % 360f
      var relativeAngle = MathUtils.getShortestRotation(baseAbsAngle, targetAngle)
      relativeAngle = MathUtils.clamp(relativeAngle, -arc/2f, arc/2f)
      return relativeAngle
    }

    fun getCurrentAbsoluteAngle(): Float{
      return (angleData.curr + slotFacing + ship.facing)%360f
    }

    fun advance(amount: Float, targetAngle: Float, hasInput: Boolean){
      angleData.to = convertTargetAngleToSlotSpace(targetAngle)
      if(!hasInput) angleData.to = 0f
      angleData.advance(amount)
      val thrusterAbsAngle = getCurrentAbsoluteAngle()
      // engineSlot.angle是相对角度
      engine.engineSlot.angle = slotFacing + angleData.curr
      //aEP_Tool.addDebugText(thrusterAbsAngle.toString(), engine.engineSlot.computePosition(ship.location, ship.facing))
      val loc = slot.computePosition(ship)

      MagicRender.singleframe(sprite,loc,
        Vector2f(sprite.width,sprite.height),
        (thrusterAbsAngle + 180f +90f)%360f,
        Color.white,
        false,CombatEngineLayers.BELOW_SHIPS_LAYER)
    }
  }
}