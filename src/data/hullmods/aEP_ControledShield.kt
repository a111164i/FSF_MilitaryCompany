package data.hullmods

import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import java.util.HashMap
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import org.lwjgl.util.vector.Vector2f
import com.fs.starfarer.api.util.Misc
import java.awt.Color

class aEP_ControledShield internal constructor() : aEP_BaseHullMod() {
  companion object {
    private val mag: MutableMap<HullSize, Float> = HashMap()
    private const val REDUCE_MULT = 0.70f
    private const val UPKEEP_PUNISH = 2f
    private val SHIELD_COLOR = Color(240, 5, 240, 160)
    private const val COLOR_RECOVER_INTERVAL = 0.05f //by seconds
    private const val id = "aEP_ControledShield"

    init {
      mag[HullSize.FIGHTER] = 700f
      mag[HullSize.FRIGATE] = 750f
      mag[HullSize.DESTROYER] = 800f
      mag[HullSize.CRUISER] = 980f
      mag[HullSize.CAPITAL_SHIP] = 1130f
    }
  }

  init {
    haveToBeWithMod.add("aEP_MarkerDissipation")
    notCompatibleList.add("hardenedshieldemitter")
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
    if (!ship.hasListenerOfClass(DamageTakenMult::class.java)) {
      ship.addListener(DamageTakenMult(ship))
    }
  }


  override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
    if (index == 0) return "" + mag[HullSize.FRIGATE]!!.toInt()
    if (index == 1) return "" + mag[HullSize.DESTROYER]!!.toInt()
    if (index == 2) return "" + mag[HullSize.CRUISER]!!.toInt()
    if (index == 3) return "" + mag[HullSize.CAPITAL_SHIP]!!.toInt()
    if (index == 4) return "" + (REDUCE_MULT * 100f).toInt() + "%"
    return ""
  }

  internal class DamageTakenMult(private val ship: ShipAPI?) : DamageTakenModifier, AdvanceableListener {
    private var timer = 0f
    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      param?: return null
      if (param is DamagingProjectileAPI && shieldHit) {
        val from = param.spawnLocation
        if (ship != null && ship.isAlive) {
          val dist = Misc.getDistance(from, point)
          if (dist > mag[ship.hullSize]!!) {
            ship.mutableStats.shieldDamageTakenMult.modifyMult(id, REDUCE_MULT)
            ship.mutableStats.shieldUpkeepMult.modifyFlat(id, UPKEEP_PUNISH)
            ship.shield.innerColor = SHIELD_COLOR
            timer = 0f
            return id
          }
        }
      }
      return null

    }

    override fun advance(amount: Float) {
      //Global.getCombatEngine().addFloatingText(ship.getLocation(),  timer + "", 20f ,new Color (0, 100, 200, 240),ship, 0.25f, 120f);
      if (ship!!.shield.innerColor !== ship!!.hullSpec.shieldSpec.innerColor) {
        timer += amount
      }
      if (timer > COLOR_RECOVER_INTERVAL) {
        ship!!.shield.innerColor = ship.hullSpec.shieldSpec.innerColor
        ship.mutableStats.shieldDamageTakenMult.unmodify(id)
        ship.mutableStats.shieldUpkeepMult.unmodify(id)
        timer = 0f
      }
    }
  }
}