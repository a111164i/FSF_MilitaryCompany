package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.loading.MissileSpecAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_ID.Companion.HULLMOD_BULLET
import combat.util.aEP_ID.Companion.HULLMOD_POINT
import combat.util.aEP_Render
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import org.magiclib.util.MagicUI
import java.awt.Color
import kotlin.math.roundToInt

class aEP_MissilePlatform : aEP_BaseHullMod() {

  companion object {
    private const val MISSILE_HITPOINT_BUFF = 10f //by percent
    //武器的最大备弹量卡在2轮或者20%备弹
    private const val MISSILE_MAX_MULT = 2
    private const val MISSILE_MAX_PERCENT = 20

    private const val MIN_RATE = 0.35f //by percent
    private const val MAX_RATE_MULT = 14f //池子大小等于所有武器装配点/100的总和的多少倍（多少秒消耗干净）
    private const val RATE_INCREASE_SPEED_MULT = 0.6667f //每个武器提供的回复速度等于自身装配点的多少倍
    private const val RATE_DECREASE_SPEED_MULT = 1f //每个武器消耗总装率的速度是实际装填量的几倍
    const val ID = "aEP_MissilePlatform"
    private val MAX_RELOAD_SPEED = HashMap<WeaponAPI.WeaponSize,Float>()
    init {
      MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.LARGE] = 0.3333f
      MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.MEDIUM] = 0.20f
      MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.SMALL] = 0.12f
    }

    val UI_COLOR1 = Color(255,255,0,220)

    private val INDICATOR_SIZE = HashMap<WeaponAPI.WeaponSize,Vector2f>()
    init {
      INDICATOR_SIZE[WeaponAPI.WeaponSize.LARGE] = Vector2f(36f,36f)
      INDICATOR_SIZE[WeaponAPI.WeaponSize.MEDIUM] =Vector2f(28f,28f)
      INDICATOR_SIZE[WeaponAPI.WeaponSize.SMALL] = Vector2f(18f,18f)
    }



    fun drawChargeBar(absLoc: Vector2f, ship: ShipAPI?, size:WeaponAPI.WeaponSize?, percent:Float){
      if(ship != Global.getCombatEngine().playerShip) return

      //aEP_Render.openGL1()

      //val screenX = Global.getCombatEngine().viewport.convertWorldXtoScreenX(absLoc.x)
      //val screenY = Global.getCombatEngine().viewport.convertWorldYtoScreenY(absLoc.y)

      var level = percent
      val angleAdd = 45f
      var startingAngle = 0f
      while (level > 0.125f){
        val sprite = Global.getSettings().getSprite("aEP_FX","loading_ring")
        val ringSize = INDICATOR_SIZE[size]?: Vector2f(20f,20f)
        MagicRender.singleframe(
          sprite,
          absLoc,
          ringSize,
          startingAngle,
          UI_COLOR1,
          true,
          CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)

        level -= 0.125f
        startingAngle += angleAdd
      }

      //aEP_Render.closeGL11()
    }

  }

  init {
    notCompatibleList.add("missleracks")
    notCompatibleList.add("missile_autoloader")
  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
    if (index == 0) return txt("aEP_MissilePlatform01")
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
      val fireRoundLimit = getAmmoPerFire(w) * MISSILE_MAX_MULT
      val percentLimit = w.spec.maxAmmo * MISSILE_MAX_PERCENT/100
      val weaponMaxAmmoCap = (Math.max(fireRoundLimit, percentLimit)).coerceAtMost(w.spec.maxAmmo)
      w.maxAmmo = weaponMaxAmmoCap

    }
    if (!ship.customData.containsKey(ID)) {
      val loadingClass = LoadingMap(ship,maxRate * MAX_RATE_MULT)
      ship.setCustomData(ID,loadingClass)
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
    tooltip.addPara("{%s}"+txt("aEP_MissilePlatform07"), 5f, arrayOf(Color.green), HULLMOD_POINT, txt("aEP_MissilePlatform01"), String.format("%.0f", 100f)+"%")
    tooltip.addPara(HULLMOD_BULLET + txt("aEP_MissilePlatform02"), 5f ,highLight, String.format("%.1f", MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.SMALL]?.times(100) ?: 0))
    tooltip.addPara(HULLMOD_BULLET + txt("aEP_MissilePlatform03"), 5f, highLight, String.format("%.1f", MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.MEDIUM]?.times(100) ?: 0))
    tooltip.addPara(HULLMOD_BULLET + txt("aEP_MissilePlatform04"), 5f, highLight, String.format("%.1f", MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.LARGE]?.times(100) ?: 0))
    var totalOp = 0f
    ship?.run {
      for (w in ship.allWeapons) {
        if (!isMissileWeapon(w)) continue
        val opMissileWeapon = w.spec.getOrdnancePointCost(null)
        totalOp += opMissileWeapon * RATE_INCREASE_SPEED_MULT
      }
    }
    tooltip.addPara("{%s}"+ txt("aEP_MissilePlatform08"), 5f, arrayOf(Color.green, highLight), HULLMOD_POINT,String.format("%.1f", totalOp))

    tooltip.addPara("{%s}" + txt("aEP_MissilePlatform10"), 5f,arrayOf(Color.green), HULLMOD_POINT, MISSILE_HITPOINT_BUFF.toInt().toString() + "%")

    //实际上就是把原本扩展架的量慢慢给玩家
    //惩罚项
    tooltip.addPara("{%s}" + txt("aEP_MissilePlatform05"), 5f,arrayOf(Color.red), HULLMOD_POINT, MISSILE_MAX_MULT.toString(), "$MISSILE_MAX_PERCENT%")
    tooltip.addPara("{%s}"+txt("not_compatible")+"{%s}", 5f, arrayOf(Color.red, highLight), HULLMOD_POINT,  showModName(notCompatibleList))
    //灰色额外说明
    tooltip.addPara(txt("MP_des08"), grayColor, 5f)
  }

  public inner class LoadingMap constructor(var ship: ShipAPI, val maxRate:Float) : AdvanceableListener {
    val ammoLoaderTracker = IntervalUtil(0.25f,0.25f)
    //[ loadingProgress, ammoPerFire, ammoPerRegen, ammoPerRegenConvertedToOp ]
    var MPTimerMap: MutableMap<WeaponAPI, kotlin.Array<Float>> = HashMap()
    var currRate = maxRate

    override fun advance(amount: Float) {
      if (ship.currentCR < 0.5f) {
        return
      }


//      aEP_Render.openGL11ForText()
//      aEP_Render.FONT1.text = "sdsds32353462356b234sdfh4523d"
//      aEP_Render.FONT1.baseColor = Color.white
//      aEP_Render.FONT1.fontSize = 50f
//      aEP_Render.FONT1.maxHeight = 50f
//      aEP_Render.FONT1.maxWidth = 200f
//      //相对于屏幕的位置
//      aEP_Render.FONT1.draw(Vector2f(100f,100f))
//      aEP_Render.closeGL11ForText()


      ammoLoaderTracker.advance(amount)
      val level = currRate/maxRate
      for (w in ship.allWeapons) {
        if(!isMissileWeapon(w)) continue

        //计算弹量增加，如果map里面不存在就创建一个
        if(ammoLoaderTracker.intervalElapsed()){
          val reloadSpeed = (MAX_RELOAD_SPEED[w.size] ?: 0f)
          //在这个函数里面同时会卡死导弹的最大备弹
          putInMap(w, reloadSpeed, ammoLoaderTracker.elapsed)
        }

        //画进度条
        if(w.ammo >= w.maxAmmo) continue
        val data = MPTimerMap[w]?: continue

        val opNow = data[0]
        val ammoPerFire = data[1].toInt()
        val ammoPerRegen = data[2].toInt()
        val ammoPerRegenConvertToOp = data[3]
        val loadingProgress = opNow/ammoPerRegenConvertToOp
        drawChargeBar(w.location, ship, w.size,loadingProgress)


      }


      //维持玩家左下角的提示
      if (Global.getCombatEngine().playerShip == ship) {
        val level = currRate/maxRate
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName+"1",  //key
          Global.getSettings().getSpriteName("aEP_ui",ID),  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("aEP_MissilePlatform06")  + (level * 100f).toInt() + "%",  //data
          false
        )
      }


    }

    private fun putInMap(w: WeaponAPI, reloadSpeed: Float, amount: Float) {

      //第一次记录时，计算一次单次回复量等等数据，存入list
      if (MPTimerMap[w] == null) {

        //单轮装填弹数
        val ammoPerFire = getAmmoPerFire(w)
        val ammoPerRegen = (ammoPerFire).coerceAtMost(w.spec.maxAmmo)
        //把map里面的op点转换为导弹数
        val op = w.spec.getOrdnancePointCost(null)
        val opPerMissile = op/w.spec.maxAmmo.coerceAtLeast(1)
        val ammoPerRegenConvertToOp = ammoPerRegen * opPerMissile
        //记得记录数据的顺序
        MPTimerMap[w] = arrayOf(
          0f,
          ammoPerFire.toFloat(),
          ammoPerRegen.toFloat(),
          ammoPerRegenConvertToOp)

      } else {

        val level = currRate/maxRate
        val data = MPTimerMap[w] as kotlin.Array<Float>

        val opNow = data[0]
        val ammoPerFire = data[1].toInt()
        val ammoPerRegen = data[2].toInt()
        val ammoPerRegenConvertToOp = data[3]

        //限制导弹的备弹
        val fireRoundLimit = data[1].toInt()* MISSILE_MAX_MULT
        val percentLimit = w.spec.maxAmmo * MISSILE_MAX_PERCENT/100
        w.maxAmmo = (Math.max(fireRoundLimit,percentLimit)).coerceAtMost(w.spec.maxAmmo)

        //存入map的是op数量，而不是具体的弹数
        val newOp = opNow + reloadSpeed * amount * level

        //需要装填，可以立刻回弹
        if(w.ammo <= (w.maxAmmo-ammoPerRegen) && newOp >= ammoPerRegenConvertToOp){
          //回复一颗
          data[0] = (newOp - ammoPerRegenConvertToOp)
          w.ammo += ammoPerRegen
          //降低整备率，该武器需要装填，装了多少就从池子里面取出多少整备率
          currRate -= reloadSpeed * amount * level * RATE_DECREASE_SPEED_MULT

        //需要装填，但是尚且不能回弹
        }else if(w.ammo <= (w.maxAmmo-ammoPerRegen) && newOp < ammoPerRegenConvertToOp){
          data[0] = newOp
          //降低整备率，该武器需要装填，装了多少就从池子里面取出多少整备率
          currRate -= reloadSpeed * amount * level * RATE_DECREASE_SPEED_MULT

        //武器已满 不需要装弹
        }else if(w.ammo >= w.maxAmmo) {

        }

        //无论如何，每门导弹都会提供整备率
        currRate += w.spec.getOrdnancePointCost(null) / 100f * amount * RATE_INCREASE_SPEED_MULT

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
            //对于mirv，perShot读取的是一次发射的总和
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

