package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.util.ColorShifter
import combat.impl.aEP_BaseCombatEffect
import combat.impl.aEP_BaseCombatEffectWithKey
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Combat
import combat.util.aEP_DataTool.txt
import combat.util.aEP_Render
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import data.scripts.hullmods.aEP_ShieldControlled
import data.scripts.hullmods.aEP_TwinFighter
import data.scripts.hullmods.aEP_TwinFighter.Companion.RECALL_SPEED_BONUS_ID
import data.scripts.shipsystems.aEP_system.FortressShieldStats
import data.scripts.weapons.PredictionStripe
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicAnim
import java.awt.Color
import java.util.*

class aEP_CoordinatedCombat : BaseShipSystemScript(), EveryFrameCombatPlugin {

  companion object{
    const val ID = "aEP_CoordinatedCombat"
    const val SHIELD_BOOST_CLASS_KEY = ID+"_ShieldBoost"
    const val WEAPON_BOOST_CLASS_KEY = ID+"_WeaponBoost"
    const val DRONE_DEFENSE_CLASS_KEY = ID+"_DroneBoost"

    const val SYSTEM_BLOCKING_KEY = ID+"_OtherInUse"

    const val DEFENSE_DECOY_MIN_DIST = 25f
  }

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    val amount = aEP_Tool.getAmount(ship)

    //如果没有双生战机就直接不起效
    if(!ship.hasListenerOfClass(aEP_TwinFighter::class.java)) return
    //拿到监听器，从里面得到ftr
    val ftr = ship.listenerManager.getListeners(aEP_TwinFighter::class.java)[0].ftr
    ftr ?: return
    if(aEP_Tool.isDead(ftr)) return

    var cd = 2f
    if(effectLevel == 1f){
      while (true){
        //每次释放系统，会设置一个key，
        //在某个系统buff过期时会消除掉这个key，用于防止多个系统被同时激活
        ship.setCustomData(SYSTEM_BLOCKING_KEY,1f)
        if(Keyboard.isKeyDown(Keyboard.KEY_1)) {
          var ammoFeedTime = 6f
          aEP_BaseCombatEffect.addOrRefreshEffect(ftr, WEAPON_BOOST_CLASS_KEY,
            {c -> c.time = 0.5f},
            {
              val c = WeaponBoost(ship)
              c.setKeyAndPutInData(WEAPON_BOOST_CLASS_KEY)
              c.lifeTime = ammoFeedTime
              aEP_CombatEffectPlugin.addEffect(c)
            })

          cd = 6f
          break
        }
        if(Keyboard.isKeyDown(Keyboard.KEY_2)){
          var droneDefenseTime = 6f
          aEP_BaseCombatEffect.addOrRefreshEffect(ftr, DRONE_DEFENSE_CLASS_KEY,
            {c -> c.time = 0.5f},
            {
              if(ftr.shield != null){
                //如果成功释放，添加需要的key
                ship.setCustomData(aEP_TwinFighter.FORCE_SPLIT_KEY,1f)
                val c = DroneDefense(ship, ftr)
                c.setKeyAndPutInData(DRONE_DEFENSE_CLASS_KEY)
                c.lifeTime = droneDefenseTime
                aEP_CombatEffectPlugin.addEffect(c)

                //加一个抖动，表示激活系统
                val jitter = aEP_Combat.AddJitterBlink(0.2f,droneDefenseTime-2f, 2f,ftr)
                jitter.color = Color(100,75,255,50)
                jitter.maxRange = 2f
                jitter.maxRangePercent = 0f
                jitter.jitterShield = true
              }
            })

          cd = 6f
          break
        }
        if(Keyboard.isKeyDown(Keyboard.KEY_3)){
          //瞬间交换不和其他任何系统冲突，立刻移除之前设置的key
          ship.customData.remove(SYSTEM_BLOCKING_KEY)
          val shipNewLoc = aEP_Tool.findEmptyLocationAroundPoint(ftr.location, 25f, 300f)
          ftr.location.set(ship.location)
          ship.location.set(shipNewLoc)
          //如果母舰已经锁定了敌人，调整指向
          if(ship.shipTarget != null){
            ship.facing = VectorUtils.getAngle(ship.location, ship.shipTarget.location)
          }else{
            ship.facing = VectorUtils.getAngle(ship.location, ship.mouseTarget)
          }
          aEP_CombatEffectPlugin.addEffect(SwapPhantom(ship,ftr))
          aEP_CombatEffectPlugin.addEffect(SwapPhantom(ftr,ship))
          cd = 1f
          break
        }

        //不加其他条件的默认系统
        var shieldTime = 5f
        aEP_BaseCombatEffect.addOrRefreshEffect(ftr, SHIELD_BOOST_CLASS_KEY,
          {c -> c.time = 0.5f},
          {
            //如果成功释放，添加需要的key
            ship.setCustomData(aEP_TwinFighter.FORCE_PULL_BACK_KEY,1f)
            val c = ShieldBoost(ship, ftr)
            c.setKeyAndPutInData(SHIELD_BOOST_CLASS_KEY)
            c.lifeTime = shieldTime
            aEP_CombatEffectPlugin.addEffect(c)
          })
        //加一个抖动，表示激活系统
        val jitter = aEP_Combat.AddJitterBlink(0.2f,0f,shieldTime,ftr)
        jitter.color = ftr.shield?.innerColor?: jitter.color
        jitter.maxRange = 5f
        jitter.jitterShield = false
        cd = 5f
        break
      }
    }

    ship.system.deactivate()
    cd = ship.mutableStats.systemCooldownBonus.computeEffective(cd)
    ship.system.cooldown = cd
    ship.system.cooldownRemaining = cd
  }

  //这个最先调用
  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    val amount = aEP_Tool.getAmount(ship)

    //第一个运行的系统会增加一个全局EFS用于绘制ui
    if(!Global.getCombatEngine().customData.containsKey(ID)){
      val plg = aEP_CoordinatedCombat()
      Global.getCombatEngine().addPlugin(plg)
      Global.getCombatEngine().customData[ID] = plg
    }

  }

  override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {
    //如果没有双生战机就直接不起效
    if(!ship.hasListenerOfClass(aEP_TwinFighter::class.java)) return false
    //拿到监听器，从里面得到ftr
    val ftr = ship.listenerManager.getListeners(aEP_TwinFighter::class.java)[0].ftr
    ftr ?: return false
    if(aEP_Tool.isDead(ftr)) return false

    if(ship.customData.containsKey(SYSTEM_BLOCKING_KEY)) return false

    val dist = MathUtils.getDistance(ftr.location, ship.hullSpec.getWeaponSlot("FRONT").computePosition(ship))
    if(Keyboard.isKeyDown(Keyboard.KEY_2) && dist < DEFENSE_DECOY_MIN_DIST) return false
    if(Keyboard.isKeyDown(Keyboard.KEY_3) && dist < DEFENSE_DECOY_MIN_DIST) return false

    return true
  }

  override fun getInfoText(system: ShipSystemAPI, ship: ShipAPI): String {
    //如果没有双生战机就直接不起效
    if(!ship.hasListenerOfClass(aEP_TwinFighter::class.java)) return ""
    //拿到监听器，从里面得到ftr
    val ftr = ship.listenerManager.getListeners(aEP_TwinFighter::class.java)[0].ftr
    ftr ?: return ""
    if(aEP_Tool.isDead(ftr)) return txt("aEP_CoordinatedCombat01")

    //某个系统的buff尚未结束时，无法再次使用
    if(ship.customData.containsKey(SYSTEM_BLOCKING_KEY)) return txt("aEP_CoordinatedCombat02")

    val dist = MathUtils.getDistance(ftr.location, ship.hullSpec.getWeaponSlot("FRONT").computePosition(ship))
    if(Keyboard.isKeyDown(Keyboard.KEY_2) && dist < DEFENSE_DECOY_MIN_DIST) return txt("aEP_CoordinatedCombat03")
    if(Keyboard.isKeyDown(Keyboard.KEY_3) && dist < DEFENSE_DECOY_MIN_DIST) return txt("aEP_CoordinatedCombat03")

    //上面的错误提示优先级高于下面的其他启用提示
    if(Keyboard.isKeyDown(Keyboard.KEY_1)) return txt("aEP_CoordinatedCombat04")
    if(Keyboard.isKeyDown(Keyboard.KEY_2)) return txt("aEP_CoordinatedCombat05")
    if(Keyboard.isKeyDown(Keyboard.KEY_3)) return txt("aEP_CoordinatedCombat06")

    return txt("aEP_CoordinatedCombat07")
  }

  inner class SwapPhantom(val ship: ShipAPI, phantomType: ShipAPI):aEP_BaseCombatEffect(0.9f,ship){
    var shipSprite = ship.spriteAPI
    var phantomSprite = phantomType.spriteAPI

    var level = 1f
    init {
      layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
    }

    override fun advanceImpl(amount: Float) {
      level = 1f - (time/lifeTime)

      //把自己从透明逐渐显形
      ship.extraAlphaMult = 1f - level

      ship.collisionClass = CollisionClass.NONE
    }

    override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {

      val num = 4
      for ( i in 1..num){

        aEP_Render.openGL11CombatLayerRendering()

        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, phantomSprite.textureId)

        //半透明
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE)

        val renderCenter = MathUtils.getRandomPointInCircle(ship.location, 4f)

        val halfWidth = phantomSprite.width * 0.5f
        val halfHeight = phantomSprite.height * 0.5f

        // Move the coordinate system to the render center
        GL11.glTranslatef(renderCenter.x, renderCenter.y, 0f)

        // Rotate around the Z-axis by the ship's angle
        GL11.glRotatef(ship.facing - 90f, 0f, 0f, 1f)

        // Move the coordinate system back so the center of the sprite is at the origin
        GL11.glTranslatef(-halfWidth, -halfHeight, 0f)

        // Begin drawing a quad (4-sided polygon, in this case, a square)
        GL11.glBegin(GL11.GL_QUADS)

        // Specify vertices and texture coordinates for each corner
        val c = Misc.setAlpha(Color.white,(255*level).toInt())

        GL11.glColor4f(c.red/255f,c.green/255f,c.blue/255f,c.alpha/(255f*num))
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(0f, 0f)

        GL11.glColor4f(c.red/255f,c.green/255f,c.blue/255f,c.alpha/(255f*num))
        GL11.glTexCoord2f(1f * phantomSprite.textureWidth, 0f)
        GL11.glVertex2f(halfWidth * 2f, 0f)

        GL11.glColor4f(c.red/255f,c.green/255f,c.blue/255f,c.alpha/(255f*num))
        GL11.glTexCoord2f(1f * phantomSprite.textureWidth, 1f * phantomSprite.textureHeight)
        GL11.glVertex2f(halfWidth * 2f, halfHeight * 2f)

        GL11.glColor4f(c.red/255f,c.green/255f,c.blue/255f,c.alpha/(255f*num))
        GL11.glTexCoord2f(0f, 1f * phantomSprite.textureHeight)
        GL11.glVertex2f(0f, halfHeight * 2f)

        // End of quad drawing
        GL11.glEnd()

        aEP_Render.closeGL11()
      }
    }

    override fun readyToEnd() {
      if(ship.isFighter){
        ship.collisionClass = CollisionClass.FIGHTER
      }else{
        ship.collisionClass = CollisionClass.SHIP
      }

    }
  }

  inner class ShieldBoost(val ship:ShipAPI, val ftr:ShipAPI): aEP_BaseCombatEffectWithKey(ftr){

    override fun advanceImpl(amount: Float) {
      if(ftr.shield == null){
        shouldEnd = true
        return
      }

      var level = 1f
      if(time < 0.5f) level = (time)/0.5f
      if(time > lifeTime - 0.5f) level = (lifeTime - time)/0.5f
      level = MagicAnim.smooth(level)

      val bonusRad = 35f * level
      val baseRad = ftr.hullSpec.shieldSpec.radius
      ftr.shield.radius = baseRad + bonusRad

      val bonusMaxArc = 180f * level
      val baseMaxArc = ftr.hullSpec.shieldSpec.arc
      ftr.shield.arc = (baseMaxArc + bonusMaxArc).coerceAtMost(360f)

      val shiftXDist = -30f * level
      val baseCenterX = ftr.hullSpec.shieldSpec.centerX
      val baseCenterY = ftr.hullSpec.shieldSpec.centerY
      ftr.shield.setCenter(baseCenterX + shiftXDist, baseCenterY )

      ftr.mutableStats.shieldUnfoldRateMult.modifyMult(key, 2f)
      ftr.mutableStats.ballisticWeaponFluxCostMod.modifyFlat(aEP_TwinFighter.ID, Float.MAX_VALUE)
      ftr.mutableStats.energyWeaponFluxCostMod.modifyFlat(aEP_TwinFighter.ID, Float.MAX_VALUE)
      ftr.mutableStats.missileWeaponFluxCostMod.modifyFlat(aEP_TwinFighter.ID, Float.MAX_VALUE)
      ftr.mutableStats.dynamic.getStat(RECALL_SPEED_BONUS_ID).modifyMult(key,0.0001f)

      ftr.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK)
      ftr.shield.toggleOn()
    }

    override fun readyToEndImpl() {
      if(ftr.shield != null){
        val baseRad = ftr.hullSpec.shieldSpec.radius
        ftr.shield.radius = baseRad

        val baseMaxArc = ftr.hullSpec.shieldSpec.arc
        ftr.shield.arc = baseMaxArc

        val baseCenterX = ftr.hullSpec.shieldSpec.centerX
        val baseCenterY = ftr.hullSpec.shieldSpec.centerY
        ftr.shield.setCenter(baseCenterX, baseCenterY)

        ftr.mutableStats.shieldUnfoldRateMult.modifyMult(key, 1f)
        ftr.mutableStats.ballisticWeaponFluxCostMod.modifyFlat(aEP_TwinFighter.ID, 0f)
        ftr.mutableStats.energyWeaponFluxCostMod.modifyFlat(aEP_TwinFighter.ID, 0f)
        ftr.mutableStats.missileWeaponFluxCostMod.modifyFlat(aEP_TwinFighter.ID, 0f)
        ftr.mutableStats.dynamic.getStat(RECALL_SPEED_BONUS_ID).modifyMult(key,1f)

        ftr.shield.toggleOff()
      }
      ship.customData.remove(aEP_TwinFighter.FORCE_PULL_BACK_KEY)
      ship.customData.remove(SYSTEM_BLOCKING_KEY)
    }
  }

  inner class DroneDefense(val ship:ShipAPI, val ftr:ShipAPI): aEP_BaseCombatEffectWithKey(ftr){

    var damageTakenMult = 0.2f
    var maxSpeedMult = 0.2f
    var weaponRofMult = 0.2f
    override fun advanceImpl(amount: Float) {
      var level = 1f
      if(time < 0.5f) level = time/0.5f
      if(lifeTime - time < 0.5f) level = (lifeTime - level)/0.5f

      ftr.shield?:return

      //战斗中动态修改arc是不会生效的
      val baseRad: Float = ftr.mutableStats.shieldArcBonus.computeEffective(ftr.hullSpec.shieldSpec.arc)
      ftr.shield.arc = MathUtils.clamp(baseRad + 360f-baseRad * level, 0f, 360f)

      //修改数据
      ftr.mutableStats.shieldDamageTakenMult.modifyMult(ID, damageTakenMult)
      ftr.mutableStats.armorDamageTakenMult.modifyMult(ID, damageTakenMult)
      ftr.mutableStats.hullDamageTakenMult.modifyMult(ID, damageTakenMult)

      ftr.mutableStats.maxSpeed.modifyMult(ID, maxSpeedMult)
      //开火的实际上是模块m，在这里修改ftr的这个数据也能有用是因为在aEP_TwinFighter插件里面同步了ftr和m的这个数值
      ftr.mutableStats.ballisticRoFMult.modifyMult(ID, weaponRofMult)

      ftr.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK)
      ftr.shield.toggleOn()
    }

    override fun readyToEndImpl() {

      //修改数据
      ftr.mutableStats.shieldDamageTakenMult.modifyMult(ID, 1f)
      ftr.mutableStats.armorDamageTakenMult.modifyMult(ID, 1f)
      ftr.mutableStats.hullDamageTakenMult.modifyMult(ID, 1f)

      ftr.mutableStats.maxSpeed.modifyMult(ID, 1f)
      //开火的实际上是模块m，在这里修改ftr的这个数据也能有用是因为在aEP_TwinFighter插件里面同步了ftr和m的这个数值
      ftr.mutableStats.ballisticRoFMult.modifyMult(ID, 1f)

      val baseRad: Float = ftr.mutableStats.shieldArcBonus.computeEffective(ftr.hullSpec.shieldSpec.arc)
      ftr.shield.arc = MathUtils.clamp(baseRad, 0f, 360f)

      ftr.shield.toggleOff()

      ship.customData.remove(SYSTEM_BLOCKING_KEY)
      ship.customData.remove(aEP_TwinFighter.FORCE_SPLIT_KEY)
    }
  }

  inner class WeaponBoost(val ship: ShipAPI): aEP_BaseCombatEffectWithKey(ship){
    val color = Color(255,50,50,205)
    override fun advanceImpl(amount: Float) {
      ship.mutableStats.hitStrengthBonus.modifyFlat(key,50f)
      ship.setWeaponGlow(1f, color, EnumSet.of(WeaponType.BALLISTIC))
    }

    override fun readyToEndImpl()
    {
      ship.mutableStats.hitStrengthBonus.modifyFlat(key,50f)
      ship.setWeaponGlow(0f, color, EnumSet.of(WeaponType.BALLISTIC))

      ship.customData.remove(SYSTEM_BLOCKING_KEY)
    }
  }

  fun findTargetNearMouse(ship: ShipAPI): ShipAPI? {
    val engine = Global.getCombatEngine()
    val ships = engine.ships
    var minDist = Float.MAX_VALUE
    var closest: ShipAPI? = null
    for (other in ships) {
      if (other.isShuttlePod) continue
      if (other.isHulk) continue
      if (aEP_Tool.isShipTargetable(
          other, false,false,
          true,false,false)
        || other.hullSpec.baseHullId.equals("aEP_fga_shuangshen3")) continue


    }

    return closest
  }

  //-----------------------------------------//
  //EFS的部分
  //单纯用来显示ui
  var timeHolding1 = 0f
  var timeHolding2 = 0f
  var timeHolding3 = 0f

  override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
    //寻找当前玩家开的舰船是不是双生
    val ship:ShipAPI? = Global.getCombatEngine().playerShip
    if(ship?.hullSpec?.baseHullId?.equals("aEP_fga_shuangshen") == true && !aEP_Tool.isDead(ship)) {
      //如果是并且舰船存活

      //如果战术系统正在冷却或者战术系统当前不可使用
      if(ship.system.state != ShipSystemAPI.SystemState.IDLE || !isUsable(ship.system, ship)){
        timeHolding1 = 0f
        timeHolding2 = 0f
        timeHolding3 = 0f
      }

      //如果当前不存在链接，添加一个链接ui
      //拿到监听器，从里面得到ftr
      val ftr = ship.listenerManager.getListeners(aEP_TwinFighter::class.java)[0].ftr
      ftr?.run {
        if (aEP_Tool.isDead(ftr)) return
        val key = ID + "_chain"
        if (!ship.customData.contains(key)) {
          //在按下了3，并且系统未在冷却时，如果尚未有链接就创造一个
          if (timeHolding3 > 0f && ship.system.state == ShipSystemAPI.SystemState.IDLE) {
            val c = Chain(ship, ftr)
            ship.setCustomData(key, c)
            aEP_CombatEffectPlugin.addEffect(c)
          }
        } else {
          //链子的level跟随按下的时间走，如果按下的时间归零，链子也会立刻结束
          val c = ship.customData[key] as Chain
          c.extraColorAlphaMult = timeHolding3 / 1f
        }
      }
    }else{
      //如果找不到舰船，或者舰船被摧毁
      timeHolding1 = 0f
      timeHolding2 = 0f
      timeHolding3 = 0f
    }

    return
  }

  override fun processInputPreCoreControls(amount: Float, events: MutableList<InputEventAPI>) {
    val changeSpeed = 10f
    timeHolding1 -= amount * changeSpeed
    timeHolding1 = timeHolding1.coerceAtLeast(0f)
    timeHolding2 -= amount * changeSpeed
    timeHolding2 = timeHolding2.coerceAtLeast(0f)
    timeHolding3 -= amount * changeSpeed
    timeHolding3 = timeHolding3.coerceAtLeast(0f)

    //鉴于123一起按的时候系统启动的优先级1》2》3，如果同时按住，只显示优先级更高的那个ui
    if(Keyboard.isKeyDown(Keyboard.KEY_1)) {
      timeHolding1 += amount * changeSpeed * 2f
      timeHolding1 = timeHolding1.coerceAtMost(1f)
      return
    }
    if(Keyboard.isKeyDown(Keyboard.KEY_2)) {
      timeHolding2 += amount * changeSpeed * 2f
      timeHolding2 = timeHolding2.coerceAtMost(1f)
      return
    }
    if(Keyboard.isKeyDown(Keyboard.KEY_3)) {
      timeHolding3 += amount * changeSpeed * 2f
      timeHolding3 = timeHolding3.coerceAtMost(1f)
      return
    }
  }

  override fun renderInWorldCoords(viewport: ViewportAPI) {
    if(Global.getCombatEngine().playerShip?.hullSpec?.baseHullId?.equals("aEP_fga_shuangshen") != true) return
    val ship = Global.getCombatEngine().playerShip
    //拿到监听器，从里面得到ftr
    val ftr = ship.listenerManager.getListeners(aEP_TwinFighter::class.java)[0].ftr
    ftr ?: return
    if(aEP_Tool.isDead(ftr)) return

    if( (timeHolding1 == 0f && timeHolding2 == 0f && timeHolding3 == 0f)) return

    render(ship, timeHolding1,Misc.getPositiveHighlightColor())
    render(ftr, timeHolding2,Misc.getPositiveHighlightColor())
  }

  override fun renderInUICoords(viewport: ViewportAPI) {
  }

  /**
  * deprecated, 别用，具体看alex注释
  * */
  override fun init(engine: CombatEngineAPI?) {

  }

  fun drawFrame(largeRad: Float, smallRad: Float, r: Float, g: Float, b: Float, alpha: Float, nearAlpha: Float, target: ShipAPI) {
    //画4个角
    val angleStep = 90f
    val startingAngle = 45f
    val pointExtraMultiple = 1.21f
    var i = 0
    while (i <= 3) {
      val a = i * angleStep + startingAngle
      glBegin(GL_QUAD_STRIP)
      val pointFar: Vector2f = getExtendedLocationFromPoint(target.getLocation(), a - 15f, largeRad)
      glColor4f(r, g, b, alpha)
      glVertex2f(pointFar.x, pointFar.y)
      val pointNear: Vector2f = getExtendedLocationFromPoint(target.getLocation(), a - 15f, smallRad)
      glColor4f(r, g, b, nearAlpha)
      glVertex2f(pointNear.x, pointNear.y)
      val pointFar2: Vector2f = getExtendedLocationFromPoint(target.getLocation(), a, largeRad * pointExtraMultiple)
      glColor4f(r, g, b, alpha)
      glVertex2f(pointFar2.x, pointFar2.y)
      val pointNear2: Vector2f = getExtendedLocationFromPoint(target.getLocation(), a, smallRad * pointExtraMultiple)
      glColor4f(r, g, b, nearAlpha)
      glVertex2f(pointNear2.x, pointNear2.y)
      val pointFar3: Vector2f = getExtendedLocationFromPoint(target.getLocation(), a + 15f, largeRad)
      glColor4f(r, g, b, alpha)
      glVertex2f(pointFar3.x, pointFar3.y)
      val pointNear3: Vector2f = getExtendedLocationFromPoint(target.getLocation(), a + 15f, smallRad)
      glColor4f(r, g, b, nearAlpha)
      glVertex2f(pointNear3.x, pointNear3.y)
      glEnd()
      i += 1
    }
  }

  fun render(target: ShipAPI, elapsedTime:Float, frameColor: Color){

    val rad: Float = target.collisionRadius

    val width = 5f
    val largeRad = width + rad
    val smallRad = largeRad - width
    val extraRange = 50f
    //控制框的缩放
    var level = elapsedTime.coerceAtMost(1f)
    //控制框的颜色
    var colorLevel = 0f


    val r: Float = frameColor.red / 255f
    val g: Float = frameColor.green / 255f
    val b: Float = frameColor.blue / 255f
    val alpha = 0.75f
    val nearAlpha = alpha / 3f

    aEP_Render.openGL11CombatLayerRendering()
    drawFrame(
      largeRad + extraRange * (1f-level),
      smallRad + extraRange * (1f-level),
      r, g * (1f - colorLevel), b * (1f - colorLevel), alpha * level,
      nearAlpha * level,target)

    aEP_Render.closeGL11()
  }

  class Chain(val parent: ShipAPI,val ftr: ShipAPI): PredictionStripe(parent){

    init {
      scrollSpeed = -6f
      texLength = 20f
      halfWidth = 6f
      spriteTexId = Global.getSettings().getSprite("aEP_FX","forward").textureId
      layers.clear()
      layers.add(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
      noAutoTimeFade = true
      color = Misc.getPositiveHighlightColor()

      key = ID+"_chain"
    }
    val startPoint = Vector2f(parent.location)
    val endPoint = Vector2f(ftr.location)

    override fun advanceImpl(amount: Float) {
      startPoint.set(parent.location)
      endPoint.set(Vector2f(ftr.location))

      //如果玩家不再驾驶母舰了，或者战机不存在了，就移除链接
      if(Global.getCombatEngine().playerShip != parent || aEP_Tool.isDead(ftr)){
        shouldEnd = true
      }

      //如果透明度降低到0，也移除
      if(extraColorAlphaMult <= 0f){
        shouldEnd = true
      }

      super.advanceImpl(amount)
    }

    override fun createLineNodes() {
      //设置直线的头和尾
      fadePercent = 0.3f
      fadeEndSidePercent = 0.3f
      val xDiff = endPoint.x - startPoint.x
      val yDiff = endPoint.y - startPoint.y
      val step = 0.1f
      var m = 0f
      while( m <= 1f){
        val toAdd = Vector2f(xDiff, yDiff)
        toAdd.scale(m)
        linePoints.add(Vector2f.add(startPoint,toAdd,null))
        m += step
      }
    }
  }
}
