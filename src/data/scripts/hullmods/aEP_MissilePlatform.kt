package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import combat.util.aEP_DataTool
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.loading.MissileSpecAPI
import java.util.WeakHashMap
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_ID.Companion.HULLMOD_BULLET
import combat.util.aEP_ID.Companion.HULLMOD_POINT
import org.lazywizard.lazylib.MathUtils
import java.awt.Color
import java.lang.Exception
import kotlin.math.roundToInt

class aEP_MissilePlatform : aEP_BaseHullMod() {

  companion object {
    private const val MISSILE_HITPOINT_BUFF = 10f //by percent
    private const val MISSILE_MAX_MULT = 2 //by percent
    private const val MIN_RATE = 0.35f //by percent
    private const val MAX_RATE_MULT = 12f //
    private const val RATE_INCREASE_SPEED_MULT = 0.75f
    private const val RATE_DECREASE_SPEED_MULT = 1f
    const val ID = "aEP_MissilePlatform"
    private val MAX_RELOAD_SPEED = HashMap<WeaponAPI.WeaponSize,Float>()
    init {
      MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.LARGE] = 0.30f
      MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.MEDIUM] = 0.18f
      MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.SMALL] = 0.10f
    }
  }

  init {
    notCompatibleList.add("missleracks")
    notCompatibleList.add("missile_autoloader")
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
    if (index == 0) return txt("MP_des00")
    return ""
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if (ship.mutableStats == null) return

    val stats = ship.mutableStats
    stats.missileHealthBonus.modifyPercent(id, MISSILE_HITPOINT_BUFF)

    var maxRate = 0.01f
    for (w in ship.allWeapons) {
      if(!isMissileWeapon(w)) continue

      //记录所有武器的 op/100 得到总装率
      val opMissileWeapon = w.spec.getOrdnancePointCost(null)
      val reloadSpeed = opMissileWeapon/100f
      //计算所有导弹武器的装填速度，创建一个整备率池子
      maxRate += reloadSpeed

      //限制武器的最大弹药量
      val weaponMaxAmmoCap = (getAmmoPerFire(w) * MISSILE_MAX_MULT).coerceAtMost(w.spec.maxAmmo)
      w.maxAmmo = weaponMaxAmmoCap

    }
    if (!ship.customData.containsKey(ID)) {
      val loadingClass = LoadingMap(ship,maxRate * MAX_RATE_MULT)
      ship.customData.set(ID,loadingClass)
      ship.addListener(loadingClass)
    }
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


    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f)
    //tooltip.addGrid( 5 * 5f + 10f);
    //奖励项
    tooltip.addPara("{%s}"+txt("MP_des06"), 5f, arrayOf(Color.green), HULLMOD_POINT, String.format("%.0f", 100f)+"%")
    tooltip.addPara(HULLMOD_BULLET + txt("MP_des01"), 5f ,highLight, String.format("%.1f", MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.SMALL]?.times(100) ?: 0))
    tooltip.addPara(HULLMOD_BULLET + txt("MP_des02"), 5f, highLight, String.format("%.1f", MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.MEDIUM]?.times(100) ?: 0))
    tooltip.addPara(HULLMOD_BULLET + txt("MP_des03"), 5f, highLight, String.format("%.1f", MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.LARGE]?.times(100) ?: 0))
    var totalOp = 0f
    ship?.run {
      for (w in ship.allWeapons) {
        if (!isMissileWeapon(w)) continue
        val opMissileWeapon = w.spec.getOrdnancePointCost(null)
        totalOp += opMissileWeapon * RATE_INCREASE_SPEED_MULT
      }
    }
    tooltip.addPara("{%s}"+ txt("MP_des07"), 5f, arrayOf(Color.green, highLight), HULLMOD_POINT,String.format("%.1f", totalOp))


    tooltip.addPara("{%s}" + txt("missile_health_up") + "{%s}", 5f,arrayOf(Color.green), HULLMOD_POINT, MISSILE_HITPOINT_BUFF.toInt().toString() + "%")

    //实际上就是把原本扩展架的量慢慢给玩家
    //惩罚项
    tooltip.addPara("{%s}" + txt("MP_des04"), 5f,arrayOf(Color.red), HULLMOD_POINT, MISSILE_MAX_MULT.toString())
    tooltip.addPara("{%s}"+txt("not_compatible")+"{%s}", 5f, arrayOf(Color.red, highLight), HULLMOD_POINT,  showModName(notCompatibleList))
    //灰色额外说明
    tooltip.addPara(txt("MP_des08"), grayColor, 5f)
  }

  public inner class LoadingMap constructor(var ship: ShipAPI, val maxRate:Float) : AdvanceableListener {
    val ammoLoaderTracker = IntervalUtil(0.5f,0.5f)
    var MPTimerMap: MutableMap<WeaponAPI, Float> = WeakHashMap()
    var currRate = maxRate

    override fun advance(amount: Float) {
      if (ship.currentCR < 0.5f) {
        return
      }

      ammoLoaderTracker.advance(amount)
      if(ammoLoaderTracker.intervalElapsed()){
        for (w in ship.allWeapons) {
          if(!isMissileWeapon(w)) continue
          val reloadSpeed = (MAX_RELOAD_SPEED[w.size] ?: 0f)
          putInMap(w, reloadSpeed, ammoLoaderTracker.elapsed)

        }
      }

      //维持玩家左下角的提示
      if (Global.getCombatEngine().playerShip == ship) {
        val level = currRate/maxRate
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName+"1",  //key
          Global.getSettings().getSpriteName("aEP_hullsys",ID),  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("MP_des05")  + (level * 100f).toInt() + "%",  //data
          false
        )
      }


    }

    private fun putInMap(w: WeaponAPI, reloadSpeed: Float, amount: Float) {
      //Global.getCombatEngine().addFloatingText(w.getLocation(),reloadSpeed+"",20f,new Color(100,100,100,100),w.getShip(),1f,5f);
      if (MPTimerMap[w] == null) {
        MPTimerMap[w] = 0f
      } else {
        val level = currRate/maxRate

        //单轮装填弹数
        val ammoPerFire = getAmmoPerFire(w)
        val ammoPerRegen = (ammoPerFire).coerceAtMost(w.spec.maxAmmo)
        //限制导弹的备弹
        w.maxAmmo = (ammoPerFire * MISSILE_MAX_MULT ).coerceAtMost(w.spec.maxAmmo)

        //存入map的是op数量，而不是具体的弹数
        val opNow = MPTimerMap[w]?:0f
        val newOp = opNow + reloadSpeed * amount * level

        //把map里面的op点转换为导弹数
        val op = w.spec.getOrdnancePointCost(null)
        val opPerMissile = op/w.spec.maxAmmo.coerceAtLeast(1)
        val ammoPerRegenConvertToOp = ammoPerRegen * opPerMissile

        //需要装填，可以立刻回弹
        if(w.ammo <= (w.maxAmmo-ammoPerRegen) && newOp >= ammoPerRegenConvertToOp){
          //回复一颗
          MPTimerMap[w] = (newOp - ammoPerRegenConvertToOp)
          w.ammo += ammoPerRegen
          //降低整备率，该武器需要装填，装了多少就从池子里面取出多少整备率
          currRate -= reloadSpeed * amount * level * RATE_DECREASE_SPEED_MULT

        //需要装填，但是尚且不能回弹
        }else if(w.ammo <= (w.maxAmmo-ammoPerRegen) && newOp < ammoPerRegenConvertToOp){
          MPTimerMap[w] = newOp
          //降低整备率，该武器需要装填，装了多少就从池子里面取出多少整备率
          currRate -= reloadSpeed * amount * level * RATE_DECREASE_SPEED_MULT

        //武器已满 不需要装弹
        }else if(w.ammo >= w.maxAmmo) {

        }

        //无论如何，每门导弹都会提供整备率
        currRate += op / 100f * amount * RATE_INCREASE_SPEED_MULT

        //限最低整备率
        currRate = MathUtils.clamp(currRate,MIN_RATE* maxRate, maxRate)
      }
    }

  }

  private fun isMissileWeapon(w:WeaponAPI):Boolean{
    //槽位不是导弹槽或者复合槽，不行
    if (w.slot.weaponType != WeaponAPI.WeaponType.MISSILE && w.slot.weaponType != WeaponAPI.WeaponType.COMPOSITE) return false
    //武器不是导弹武器，不行
    if (w.type != WeaponAPI.WeaponType.MISSILE) return false
    //武器没有弹药，不行
    if (w.ammoTracker == null) return false
    //武器没有弹药，不行，原版里面不适用弹药的武器，这个数为maxValue
    if (w.ammo == Int.MAX_VALUE) return false
    return true
  }

  private fun getAmmoPerFire(w:WeaponAPI): Int{
    val spec = Global.getSettings().getWeaponSpec(w.id)
    var numOfBurst = spec.burstSize

    //计算linked和dual类型的单次发射量
    var numPerBurst = w.derivedStats.damagePerShot/w.damage.baseDamage

    //如果是mirv，计算每个子弹丸的伤害来统计一共几发
    if(spec.projectileSpec is MissileSpecAPI) {
      val missileSpec = spec.projectileSpec as MissileSpecAPI
      if(missileSpec.behaviorJSON != null){

        //尝试读取json里面子弹丸的数量和伤害来除
        try {
          if(missileSpec.behaviorJSON.getString("behavior").equals("MIRV")){
            val damagePerWarhead = missileSpec.behaviorJSON.getString("damage").toFloat().coerceAtLeast(Float.MIN_VALUE)
            val numOfWarhead = missileSpec.behaviorJSON.getString("numShots").toFloat().coerceAtLeast(Float.MIN_VALUE)

            numPerBurst = (w.derivedStats.damagePerShot / (numOfWarhead * damagePerWarhead) )
          }
        }catch (e1: Exception){
          numPerBurst = 1f
        }

      }
    }

    val totalNumPerBurst = numOfBurst * numPerBurst.roundToInt()

    return totalNumPerBurst
  }
}

