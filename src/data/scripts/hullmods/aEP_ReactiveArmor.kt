package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_DataTool
import data.scripts.weapons.aEP_m_s_era3
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_ReactiveArmor(): aEP_BaseHullMod(), DamageTakenModifier, AdvanceableListener {

  companion object{
    const val ID = "aEP_ReactiveArmor"
    const val DAMAGE_THRESHOLD = 600f
    const val ARMOR_THRESHOLD = 1f
    const val REDUCE_MULT = 0.9f
    const val SMOD_MANEUVER_PUNISH = 0f

    private val MAX_TRIGGER: MutableMap<ShipAPI.HullSize, Float> = HashMap<HullSize, Float>().withDefault { 8f }
    init {
      MAX_TRIGGER[ShipAPI.HullSize.FIGHTER] = 2f
      MAX_TRIGGER[ShipAPI.HullSize.FRIGATE] = 4f
      MAX_TRIGGER[ShipAPI.HullSize.DESTROYER] = 6f
      MAX_TRIGGER[ShipAPI.HullSize.CRUISER] = 8f
      MAX_TRIGGER[ShipAPI.HullSize.CAPITAL_SHIP] = 9f
    }
    private val SMOD_PUNISH: MutableMap<ShipAPI.HullSize, Float> = HashMap<HullSize, Float>().withDefault { 2f }
    init {
      SMOD_PUNISH[ShipAPI.HullSize.FIGHTER] = 1f
      SMOD_PUNISH[ShipAPI.HullSize.FRIGATE] = 1f
      SMOD_PUNISH[ShipAPI.HullSize.DESTROYER] = 1f
      SMOD_PUNISH[ShipAPI.HullSize.CRUISER] = 2f
      SMOD_PUNISH[ShipAPI.HullSize.CAPITAL_SHIP] = 2f
    }

    /**
    * @return 0f代表不需要触发，1f在这里面已经修改过了伤害，外面需要返回ID
    * */
    fun damageModify(damage: DamageAPI, ship: ShipAPI, param: Any?, thres:Float, armor_level_thres:Float):Float{
      var realDamage = damage.damage * damage.modifier.modifiedValue
      when(damage.type){
        DamageType.KINETIC -> realDamage *= 0.5f
        DamageType.ENERGY -> realDamage *= 1f
        DamageType.FRAGMENTATION -> realDamage *= 0.35f
        else -> {}
      }

      val damageThreshold = computeThreshold(thres, ship, armor_level_thres)

      if(realDamage  < damageThreshold)return 0f

      //如果光束打到了自己身上
      if(param is BeamAPI){
        //如果本船对这根光束已经上了对策减伤buff，不再触发爆反
        if(ship.customData?.containsKey(param.toString()) == true){
          return 0f
        }
        //如果需要触发，加入减伤buff，并把param.toString()加入本船的customData
        BeamDamageReduce(param, ship, 2f)
      }

      //以上的检测都通过了，说明需要修改伤害
      //damage.damage = damage.baseDamage * 0.1f
      damage.modifier.modifyMult(ID,1f - REDUCE_MULT)
      return 1f
    }

    fun computeThreshold(thres:Float, ship: ShipAPI?, armor_level_thres:Float): Float{
      val damageThreshold = Math.min(thres, (ship?.armorGrid?.armorRating?:1000f) * armor_level_thres)
      return damageThreshold
    }
  }

  init {
    notCompatibleList.add(HullMods.HEAVYARMOR)
    haveToBeWithMod.add(aEP_SpecialHull.ID)
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if(!ship.hasListenerOfClass(aEP_ReactiveArmor::class.java)){
      val listener = aEP_ReactiveArmor()
      listener.ship = ship
      var number = MAX_TRIGGER[ship.hullSpec.hullSize]?:8f
      if(isSMod(ship)) {
        number -= SMOD_PUNISH[ship.hullSpec.hullSize]?:1f
      }
      listener.maxTrigger = number.toInt()
      ship.addListener(listener)
    }
  }

  override fun applyEffectsAfterShipAddedToCombatEngine(ship: ShipAPI, id: String) {
    if(!ship.hasListenerOfClass(aEP_ReactiveArmor::class.java)){
      val listener = aEP_ReactiveArmor()
      listener.ship = ship
      var number = MAX_TRIGGER[ship.hullSpec.hullSize]?:8f
      if(isSMod(ship)) {
        number -= SMOD_PUNISH[ship.hullSpec.hullSize]?:1f
      }
      listener.maxTrigger = number.toInt()
      ship.addListener(listener)
    }
  }

  override fun applySmodEffectsAfterShipCreationImpl(ship: ShipAPI, stats: MutableShipStatsAPI, id: String) {
    stats.getAcceleration().modifyMult(id, 1f - SMOD_MANEUVER_PUNISH * 0.01f)
    stats.getDeceleration().modifyMult(id, 1f - SMOD_MANEUVER_PUNISH * 0.01f)
    stats.getTurnAcceleration().modifyMult(id, 1f - SMOD_MANEUVER_PUNISH * 0.01f)
    stats.getMaxTurnRate().modifyMult(id, 1f - SMOD_MANEUVER_PUNISH * 0.01f)
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize, ship: ShipAPI?): String? {
    val damageThres = computeThreshold(DAMAGE_THRESHOLD, ship, ARMOR_THRESHOLD)

    if (index == 0) return String.format("%.0f",damageThres)
    if (index == 1) return String.format("-%.0f", 90f) +"%"
    if (index == 2) return String.format("%.0f", MAX_TRIGGER[hullSize]?:10f)
    return null
  }

  override fun getSModDescriptionParam(index: Int, hullSize: HullSize?): String? {
    //if (index == 0) return String.format("%.0f", SMOD_MANEUVER_PUNISH)+"%"
    if (index == 0) return String.format("%.0f", SMOD_PUNISH[hullSize])
    return null
  }

  override fun isSModEffectAPenalty(): Boolean {
    return true
  }

  //以下都是listener的部分
  //--------------------------------------------//
  //以下变量都是listener使用的，hullmod中不使用
  lateinit var ship:ShipAPI
  var maxTrigger = 8
  var triggered = 0

  var timeLastTriggered = 0f

  override fun advance(amount: Float) {
    timeLastTriggered += amount
    if(triggered < maxTrigger){
      //维持玩家左下角的提示
      if (Global.getCombatEngine().playerShip == ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName+"1",  //key
          Global.getSettings().getHullModSpec(ID).spriteName,  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("charge")+": "  + (maxTrigger-triggered),  //data
          false)
      }
    }else{
      ship.removeListener(this)
    }
  }

  override fun modifyDamageTaken(param: Any?, target: CombatEntityAPI?, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
    if(triggered > maxTrigger) return null
    if(shieldHit) return null
    val engine = Global.getCombatEngine()

    val didMod = damageModify(damage, ship, param, DAMAGE_THRESHOLD, ARMOR_THRESHOLD)
    if(didMod > 0f){
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

      //文字提示
      Global.getCombatEngine().addFloatingText(
        point, Global.getSettings().getHullModSpec(ID).displayName+" !", 10f,
        Color.magenta, ship, 1f, 1f)

      //增加触发次数
      triggered += 1
      timeLastTriggered = 0f

      return ID
    }

    return null
  }


}