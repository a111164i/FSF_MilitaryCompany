package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_ID
import data.scripts.weapons.aEP_m_s_era
import data.scripts.weapons.aEP_m_s_era3
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f

class aEP_ReactiveArmor: aEP_BaseHullMod(), DamageTakenModifier {

  var ship: ShipAPI? = null
  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if(!ship.hasListenerOfClass(this.javaClass)){
      this.ship = ship
      ship.addListener(this)
    }
  }

  override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
    if(ship == null) return null
    if(shieldHit) return null

    val ship = ship as ShipAPI
    val engine = Global.getCombatEngine()
    val realDamage = damage.damage * damage.modifier.modifiedValue

    if(realDamage  < aEP_m_s_era.DAMAGE_THRESHOLD
      && realDamage < (ship.armorGrid?.armorRating?:1000f) * aEP_m_s_era.ARMOR_THRESHOLD) return null

    if(param is BeamAPI && param.damageTarget is ShipAPI){
      //如果这个光束已经被上了减伤buff，不再触发爆反
      if(param.damageTarget.customData.containsKey(param.toString())) return null
      BeamDamageReduce(param, param.damageTarget as ShipAPI, 1.1f)
    }

    //产生炮口烟，刷出弹丸，立刻引爆
    val angle = VectorUtils.getAngle(ship.location, point)
    val proj = engine.spawnProjectile(
      ship,
      engine.createFakeWeapon(ship, aEP_m_s_era3::class.simpleName),
      aEP_m_s_era3::class.simpleName,
      point,  //FirePoint得到的是绝对位置
      angle,
      ship.velocity?: Misc.ZERO) as MissileAPI
    proj.explode()

    //降低90%的伤害
    damage.damage = damage.baseDamage * 0.1f

    return null
  }


}