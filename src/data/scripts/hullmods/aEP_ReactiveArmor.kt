package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_ID
import data.scripts.weapons.aEP_m_s_era3
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_ReactiveArmor(): aEP_BaseHullMod(), DamageTakenModifier, AdvanceableListener {
  companion object{
    const val ID = "aEP_ReactiveArmor"
    const val DAMAGE_THRESHOLD = 600f
    const val ARMOR_THRESHOLD = 0.8f
    const val REDUCE_MULT = 0.9f
    private val MAX_TRIGGER: MutableMap<ShipAPI.HullSize, Int> = HashMap()
    init {
      MAX_TRIGGER[ShipAPI.HullSize.FIGHTER] = 2
      MAX_TRIGGER[ShipAPI.HullSize.FRIGATE] = 6
      MAX_TRIGGER[ShipAPI.HullSize.DESTROYER] = 8
      MAX_TRIGGER[ShipAPI.HullSize.CRUISER] = 10
      MAX_TRIGGER[ShipAPI.HullSize.CAPITAL_SHIP] = 12
    }

    /**
    * @return 0f代表不需要触发，1f在这里面已经修改过了伤害，外面需要返回ID
    * */
    fun damageModify(damage: DamageAPI, ship: ShipAPI, param: Any?, thres:Float, armor_level_thres:Float):Float{
      var realDamage = damage.damage * damage.modifier.modified
      when(damage.type){
        DamageType.KINETIC -> realDamage *= 0.5f
        DamageType.ENERGY -> realDamage *= 0.75f
        DamageType.FRAGMENTATION -> realDamage *= 0.35f
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
        BeamDamageReduce(param, ship, 1.5f)
      }

      //以上的检测都通过了，说明需要修改伤害
      damage.damage = damage.baseDamage * 0.1f
      return 1f
    }

    fun computeThreshold(thres:Float, ship: ShipAPI?, armor_level_thres:Float): Float{
      val damageThreshold = Math.max(thres, (ship?.armorGrid?.armorRating?:1000f) * armor_level_thres)
      return damageThreshold
    }
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if(!ship.hasListenerOfClass(aEP_ReactiveArmor::class.java)){
      val listener = aEP_ReactiveArmor()
      listener.ship = ship
      listener.maxTrigger = MAX_TRIGGER[ship.hullSpec.hullSize]?:10
      ship.addListener(listener)
    }
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {

    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)

    val damageThreshold = computeThreshold(DAMAGE_THRESHOLD, ship, ARMOR_THRESHOLD)
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_ReactiveArmor01"), 5f, arrayOf(Color.green, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.1f", damageThreshold),
      String.format("%.0f", REDUCE_MULT *100f) +"%")
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_ReactiveArmor02"), 5f, arrayOf(Color.red, highLight),
      aEP_ID.HULLMOD_POINT,
      MAX_TRIGGER[hullSize].toString())

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
          aEP_DataTool.txt("aEP_ReactiveArmor03")  + (maxTrigger-triggered),  //data
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
        point,
        "Reactive Armor !", 10f,
        Color.magenta, ship, 1f, 1f)

      //增加触发次数
      triggered += 1
      timeLastTriggered = 0f

      return ID
    }

    return null
  }


}