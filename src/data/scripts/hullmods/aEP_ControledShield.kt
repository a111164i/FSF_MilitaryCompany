package data.scripts.hullmods

import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import java.util.HashMap
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier
import org.lwjgl.util.vector.Vector2f
import com.fs.starfarer.api.util.Misc
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_ControledShield internal constructor() : aEP_BaseHullMod() {
  companion object {
    private val mag: MutableMap<HullSize, Float> = HashMap()
    private const val REDUCE_MULT = 0.75f
    private const val UPKEEP_PUNISH = 2f
    private const val MAX_WEAPON_RANGE_CAP = 900f
    private val SHIELD_COLOR = Color(240, 5, 240, 160)
    private const val COLOR_RECOVER_INTERVAL = 0.05f //by seconds
    private const val id = "aEP_ControledShield"

    init {
      mag[HullSize.FIGHTER] = 800f
      mag[HullSize.FRIGATE] = 850f
      mag[HullSize.DESTROYER] = 950f
      mag[HullSize.CRUISER] = 1025f
      mag[HullSize.CAPITAL_SHIP] = 1100f
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
    if (index == 5) return "" + (MAX_WEAPON_RANGE_CAP).toInt()
    return ""
  }

  internal class DamageTakenMult(private val ship: ShipAPI?) : DamageTakenModifier, AdvanceableListener, WeaponBaseRangeModifier {
    private var timer = 0f
    private var didChange = false
    override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      param?: return null
      if (param is DamagingProjectileAPI && shieldHit) {
        val from = param.spawnLocation
        if (ship != null && ship.isAlive) {
          val dist = Misc.getDistance(from, point)
          if (dist > (mag[ship.hullSize] ?: 1000f)) {
            ship.mutableStats.shieldDamageTakenMult.modifyMult(id, REDUCE_MULT)
            ship.mutableStats.shieldUpkeepMult.modifyFlat(id, UPKEEP_PUNISH)
            ship.shield.innerColor = SHIELD_COLOR
            timer = COLOR_RECOVER_INTERVAL
            didChange = true
            return id
          }
        }
      }
      return null
    }

    override fun advance(amount: Float) {
      //考虑禁盾的情况
      if(ship?.shield == null) return

      timer -= amount
      timer = MathUtils.clamp(timer,0f, COLOR_RECOVER_INTERVAL)
      if (timer <= 0f && didChange) {
        ship.shield.innerColor = ship.hullSpec.shieldSpec.innerColor
        ship.mutableStats.shieldDamageTakenMult.unmodify(id)
        ship.mutableStats.shieldUpkeepMult.unmodify(id)
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
  }
}