package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.loading.MissileSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_ID.Companion.HULLMOD_POINT
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import java.awt.Color
import kotlin.math.roundToInt

class aEP_MissilePlatform : aEP_BaseHullMod() {

  companion object {
    private const val MISSILE_HITPOINT_BUFF = 0f //by percent
    //武器的最大备弹量卡在2轮或者20%备弹
    private const val MISSILE_MAX_MULT = 2
    private const val MISSILE_MAX_PERCENT = 20

    private const val MIN_RATE = 0.35f //by percent
    private const val MAX_RATE_MULT = 15f //池子大小等于所有武器装配点/100的总和的多少倍（多少秒消耗干净）
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
          sprite, absLoc, ringSize,
          startingAngle, UI_COLOR1, true,
          CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)

        level -= 0.125f
        startingAngle += angleAdd
      }

      //aEP_Render.closeGL11()
    }

    fun isMissileWeapon(w:WeaponAPI):Boolean{
      //槽位既不是导弹槽也不是复合槽的，不行
      if (w.slot.weaponType != WeaponAPI.WeaponType.MISSILE
        && w.slot.weaponType != WeaponAPI.WeaponType.COMPOSITE) return false
      //武器不是导弹武器，不行
      if (w.type != WeaponAPI.WeaponType.MISSILE) return false
      //武器没有弹药，不行
      if (w.ammoTracker == null) return false
      //武器没有弹药，不行，原版里面不适用弹药的武器，这个数为maxValue
      if (w.ammo == Int.MAX_VALUE) return false
      //武器拥有基础的自装填速度的，不行
      if (w.spec.ammoPerSecond > 0f) return false
      return true
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

  //变宽！！！
  //原版测距仪是412，这里也用412
  override fun getTooltipWidth(): Float {
    return 412f
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


    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f)
    // 正面

    //统计全船的导弹数据，用于后面的表格
    var totalOp = 0f
    var totalConsumption = 0f
    val allLargeSpec = ArrayList<WeaponSpecAPI>()
    val allMediumSpec = ArrayList<WeaponSpecAPI>()
    val allSmallSpec = ArrayList<WeaponSpecAPI>()
    ship?.run {
      for(w in ship.allWeapons) {
        if(!isMissileWeapon(w)) continue
        val spec = w.spec
        totalOp += spec.getOrdnancePointCost(null)
        totalConsumption += MAX_RELOAD_SPEED[spec.size]?:0f
        when (spec.size){
          WeaponAPI.WeaponSize.LARGE -> {
            if (!allLargeSpec.contains(spec)) allLargeSpec.add(spec)
          }
          WeaponAPI.WeaponSize.MEDIUM ->{
            if (!allMediumSpec.contains(spec)) allMediumSpec.add(spec)
          }
          WeaponAPI.WeaponSize.SMALL -> {
            if (!allSmallSpec.contains(spec)) allSmallSpec.add(spec)
          }
        }
      }

    }


//    //用表格显示总装填率的最大值，回复速度，最大消耗速度
//    val col2W0 = width * 0.5f
//    //第一列显示的名称，尽可能可能的长
//    val col1W0 = (width - col2W0 - PARAGRAPH_PADDING_BIG)
//    tooltip.beginTable(
//      factionColor, factionDarkColor, factionBrightColor,
//      TEXT_HEIGHT_SMALL, true, true,
//      *arrayOf<Any>( txt("aEP_MissilePlatform01"), col1W0,
//        "Statistics", col2W0)
//    )
//    tooltip.addRow(
//      Alignment.MID, highlight, txt("total"),
//      Alignment.MID, highlight, txt("equalsTo") + String.format(" %.1f OP",totalOp * MAX_RATE_MULT),
//    )
//    tooltip.addRow(
//      Alignment.MID, highlight, txt("recover")+txt("speed"),
//      Alignment.MID, highlight, txt("equalsTo") + String.format(" %.1f OP/s",totalOp * RATE_INCREASE_SPEED_MULT),
//    )
//    tooltip.addRow(
//      Alignment.MID, highlight, txt("max")+txt("consumption")+txt("speed") ,
//      Alignment.MID, highlight, txt("equalsTo") + String.format(" %.1f OP/s",totalConsumption * 100f),
//    )
//    val minLevel = (totalOp * RATE_INCREASE_SPEED_MULT/(totalConsumption * 100f + 0.01f)).coerceAtLeast(0f).coerceAtMost(1f)
//      tooltip.addRow(
//      Alignment.MID, highlight, txt("min")+txt("level") ,
//      Alignment.MID, highlight, String.format("%.0f",minLevel * 100f)+"%",
//    )
//    tooltip.addTable("", 0, PARAGRAPH_PADDING_SMALL)


    addPositivePara(tooltip, "aEP_MissilePlatform07", arrayOf())
    //表格显示最大导弹回复速度
    //只显示可预期长度的数字的列，写一个固定的列宽度，
    val col2W = width * 0.70f
    //第一列显示的名称，尽可能可能的长
    val col1W = (width - col2W - PARAGRAPH_PADDING_BIG)
    tooltip.beginTable(
      factionColor, factionDarkColor, factionBrightColor,
      TEXT_HEIGHT_SMALL, true, true,
      *arrayOf<Any>("Missile Size", col1W,
        "Ammo Production per 100s", col2W))
    tooltip.addRow(
      Alignment.MID, highlight, "Large",
      Alignment.MID, highlight, txt("equalsTo") + String.format(" %.1f OP", (MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.LARGE]?:0.2f) * 100f))
    tooltip.addRow(
      Alignment.MID, highlight, "Medium",
      Alignment.MID, highlight, txt("equalsTo") + String.format(" %.1f OP", (MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.MEDIUM]?:0.2f) * 100f))
    tooltip.addRow(
      Alignment.MID, highlight, "Small",
      Alignment.MID, highlight, txt("equalsTo") + String.format(" %.1f OP", (MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.SMALL]?:0.2f) * 100f))
    tooltip.addTable("", 0, PARAGRAPH_PADDING_SMALL)

    // 说明总装填率的储备值和回复值
    addPositivePara(tooltip, "aEP_MissilePlatform02", arrayOf(
      String.format("%.1f", RATE_INCREASE_SPEED_MULT/MAX_RATE_MULT * 100f)+" %/s"
    ))


    // 解释锻炉功率水平的下降，如何达到最低平衡水平
    addDoubleEdgePara(tooltip, "aEP_MissilePlatform03", arrayOf()) // 说明总装填率的储备值和回复值


    if(shouldShowF1Content){
      addDoubleEdgePara(tooltip, "aEP_MissilePlatform06", arrayOf()) // 说明总装填率的储备值和回复值
      //表格显示每个武器的 弹药/OP比
      val col2W2 = 120f
      val col3W2 = 60f
      val col1W2 = (width - col2W2 - col3W2 - PARAGRAPH_PADDING_BIG)
      tooltip.beginTable(
        factionColor, factionDarkColor, factionBrightColor,
        TEXT_HEIGHT_SMALL, true, true,
        *arrayOf<Any>("Missile Spec", col1W2,
          "OP", col3W2,
          txt("aEP_MissilePlatform04"), col2W2,
          ))

      //val label = tooltip.createLabel( "Large", highlight)
      //label.autoSizeToWidth(10f)
      if(!allLargeSpec.isEmpty()){
        //tooltip.addRow(Alignment.LMID, highlight, "Large")
        for(spec in allLargeSpec){
          val op =  spec.getOrdnancePointCost(null)
          val ammo =spec.maxAmmo.toFloat()
          val consumptionSpeed = op/ammo / (MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.LARGE]!! )
          tooltip.addRow(
            Alignment.LMID, txtColor, spec.weaponName,
            Alignment.MID, txtColor, String.format("%.0f", op),
            Alignment.MID, txtColor, String.format("%2.2f Sec.", consumptionSpeed),
          )
        }
      }
      if(!allMediumSpec.isEmpty()) {
        //tooltip.addRow(Alignment.LMID, highlight, "Medium")
        for (spec in allMediumSpec) {
          val op = spec.getOrdnancePointCost(null)
          val ammo = spec.maxAmmo.toFloat()
          val consumptionSpeed =  op/ammo / (MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.MEDIUM]!! )
          tooltip.addRow(
            Alignment.LMID, txtColor, spec.weaponName,
            Alignment.MID, txtColor, String.format("%.0f", op),
            Alignment.MID, txtColor, String.format("%2.2f Sec.", consumptionSpeed),
          )
        }
      }
      if(!allSmallSpec.isEmpty()) {
        //tooltip.addRow(Alignment.LMID, highlight, "Small")
        for (spec in allSmallSpec) {
          val op = spec.getOrdnancePointCost(null)
          val ammo = spec.maxAmmo.toFloat()
          val consumptionSpeed = op/ammo / (MAX_RELOAD_SPEED[WeaponAPI.WeaponSize.SMALL]!!)
          tooltip.addRow(
            Alignment.LMID, txtColor, spec.weaponName,
            Alignment.MID, txtColor, String.format("%.0f", op),
            Alignment.MID, txtColor, String.format("%2.2f Sec.", consumptionSpeed),
          )
        }
      }
      tooltip.addRow(
        Alignment.MID, highlight, "Total",
        Alignment.MID, highlight, String.format("%.0f",totalOp),
      )
      tooltip.addTable("", 0, PARAGRAPH_PADDING_SMALL)
    }




    //实际上就是把原本扩展架的量慢慢给玩家
    // 负面
    tooltip.addPara(" %s " + txt("aEP_MissilePlatform05"), 5f,arrayOf(Color.red), HULLMOD_POINT, MISSILE_MAX_MULT.toString(), "$MISSILE_MAX_PERCENT%")
    showIncompatible(tooltip)

    //灰色额外说明
    addGrayPara(tooltip, "aEP_MissilePlatform09", arrayOf())
    addGrayPara(tooltip, "aEP_MissilePlatform11", arrayOf())
    addGrayPara(tooltip, "aEP_MissilePlatform12", arrayOf("F1"))
  }

  public inner class LoadingMap constructor(var ship: ShipAPI, val maxRate:Float) : AdvanceableListener {
    val ammoLoaderTracker = IntervalUtil(0.25f,0.25f)
    //[ loadingProgress, ammoPerFire, ammoPerRegen, ammoPerRegenConvertedToOp ]
    var MPTimerMap: MutableMap<WeaponAPI, kotlin.Array<Float>> = HashMap()
    var currRate = maxRate

    override fun advance(amount: Float) {
      if (ship.currentCR < 0.4f || aEP_Tool.isDead(ship)) {
        return
      }

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
          spec.displayName,  //title
          aEP_DataTool.txt("aEP_MissilePlatform01") +": "  + (level * 100f).toInt() + "%",  //data
          false
        )
      }

      //画出装填率指示器
      val decoSlot = ship.hullSpec.getWeaponSlot("MP_ID")?:return
      val loc = decoSlot.computePosition(ship)
      createIndicator(loc, decoSlot.computeMidArcAngle(ship), level)

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

        val level = (currRate/maxRate).coerceAtLeast(MIN_RATE)
        val data = MPTimerMap[w] as Array<Float>

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
        currRate = MathUtils.clamp(currRate,0f, maxRate)
      }
    }

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

  private fun createIndicator(loc:Vector2f, facing:Float, rateLevel: Float){
    val useLevel = (rateLevel- MIN_RATE) * (1f/ (1f-MIN_RATE))
    val step = 0.15f
    val maxStep = 6
    for( i in 1..maxStep){
      var level = 1f
      if(useLevel < 1f - step * (i-1)){
        if(useLevel > 1f - step * i){
          level = (useLevel - (1f - step * i))/step
        }else{
          level = 0f
        }
      }

      val c = Color( 1f-level, level,0f)
      val sprite = Global.getSettings().getSprite("aEP_FX","missile_platform_indicator")
      val renderLoc = aEP_Tool.getExtendedLocationFromPoint(loc,facing, 10.5f - 3f * i)
      renderLoc.set(MathUtils.getRandomPointInCircle(renderLoc,0.36f))
      MagicRender.singleframe(
        sprite, renderLoc, Vector2f(sprite.width,sprite.height),facing + 90f,
        c,true)

    }
  }
}

