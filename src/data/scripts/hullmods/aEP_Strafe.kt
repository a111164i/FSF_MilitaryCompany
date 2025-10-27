package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.combat.LidarArrayStats
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicRender
import org.magiclib.util.MagicUI
import java.awt.Color
import java.util.*

class aEP_Strafe(): aEP_BaseHullMod(), AdvanceableListener {
  companion object{
    const val ID = "aEP_Strafe"
    // 几秒充能100%
    const val TIME_TO_REACH_MAX = 14f
    // 100%充能能转化为几秒冷却
    const val TIME_TO_REACH_ZERO = 6f

    // 最大充能百分之几百
    const val MAX_CHARGE = 1f

    const val MIN_FIRE_INTERVAL = 0.08333f

    // 消耗百分之多少恢复一次系统充能
    const val CHARGE_CONVERT_TO_SYSTEM_CHARGE = 0.9f

    val FM_RED_EMP_FRINGE = Color(255, 127, 80, 205)
  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
  }


  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize, stats: MutableShipStatsAPI, id: String) {


  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if(!ship.hasListenerOfClass(this::class.java)){
      val listener = aEP_Strafe()
      listener.ship = ship
      ship.addListener(listener)
    }
  }

  override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize, ship: ShipAPI?): String? {
    ship ?: return ""

    if (index == 0) return String.format("%.0f", TIME_TO_REACH_MAX)
    if (index == 1) return String.format("%.0f", TIME_TO_REACH_ZERO)
    if (index == 2) return String.format("%.2f", MIN_FIRE_INTERVAL)
    if (index == 3) return String.format("%.0f", CHARGE_CONVERT_TO_SYSTEM_CHARGE/ MAX_CHARGE *100f )+"%"

    else return ""
  }

  //--------------------------------------//
  //以下是listener的变量
  var maxEnergy = MAX_CHARGE
  var energy = 0f

  val glowSprite = Global.getSettings().getSprite("aEP_FX","des_taodong_glow")
  var glowTimer = 0f

  var shouldFire = false

  lateinit var ship: ShipAPI

  override fun advance(amount: Float) {

    if(ship.isHulk || !ship.isAlive) return
    val glowLevel = energy / maxEnergy

    //抵消大槽武器冷却
    for(w in ship.allWeapons){
      if(w.slot.slotSize == WeaponAPI.WeaponSize.LARGE && !w.slot.isDecorative){
        if(w.cooldownRemaining > MIN_FIRE_INTERVAL && energy > 0f){
          val toUse = ((w.cooldownRemaining - MIN_FIRE_INTERVAL) / TIME_TO_REACH_ZERO).coerceAtMost(energy)
          energy -= toUse
          w.setRemainingCooldownTo(w.cooldownRemaining - toUse * TIME_TO_REACH_ZERO)
        }
        w.setGlowAmount(glowLevel,LidarArrayStats.WEAPON_GLOW)
      }
    }

    //闪烁完毕以后发射导弹
    if(ship.system != null){
      if(ship.system.isActive){
        shouldFire = true
      }else{
        if(shouldFire){
          shouldFire = false
          for(w in ship.allWeapons){
            if(w.slot.id.startsWith("M_")){
              w.setForceFireOneFrame(true)
            }
          }
        }
      }
    }



    //每秒回复充能
    energy += amount / TIME_TO_REACH_MAX
    energy = energy.coerceAtLeast(0f).coerceAtMost(maxEnergy)


    //是否需要转给系统充能
    if(ship.system.maxAmmo > 0 && ship.system.ammo == 0){
      if(energy > CHARGE_CONVERT_TO_SYSTEM_CHARGE){
        energy -= CHARGE_CONVERT_TO_SYSTEM_CHARGE
        ship.system.ammo += 1
      }
    }


    //维持舰体闪光贴图
    glowTimer = (glowTimer + amount).coerceAtMost(1f)
    if (glowTimer >= 1f) {
      glowTimer -= 1f
      var i = 0
      while (i < 4) {
        MagicRender.objectspace(
          glowSprite, ship, Misc.ZERO, Misc.ZERO, Vector2f(glowSprite.width, glowSprite.height),
          ship.renderOffset, -180f, 0f,
          true, Misc.scaleAlpha(FM_RED_EMP_FRINGE, Math.min(glowLevel, 1f)),
          glowLevel * 3.5f,
          0f, 1f, 1f, 0f,
          0.3f, 0.3f, 0.4f,
          true,
          CombatEngineLayers.BELOW_SHIPS_LAYER,
          GL11.GL_SRC_ALPHA, GL11.GL_ONE
        )
        i += 1
      }
    }


    //维持玩家左下角的提示
    if (Global.getCombatEngine().playerShip == ship) {
      val hullmodName = Global.getSettings().getHullModSpec(ID).displayName
      Global.getCombatEngine().maintainStatusForPlayerShip(
        this.javaClass.simpleName+"1",  //key
        Global.getSettings().getHullModSpec(aEP_ReactiveArmor.ID).spriteName,  //sprite name,full, must be registed in setting first
        hullmodName,  //title
        "Energy: "  + (glowLevel * 100f ).toInt() + "%",  //data
        false)

      //维持扫射magicUI
      MagicUI.drawHUDStatusBar(ship,
        glowLevel, null,null, 0f ,hullmodName, String.format("%.1f",glowLevel*100f)+"%",true )

    }
  }
}