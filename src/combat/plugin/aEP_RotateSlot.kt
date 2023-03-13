package combat.plugin

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.WeaponAPI
import java.util.LinkedHashMap
import com.fs.starfarer.api.combat.CombatEngineAPI
import combat.plugin.aEP_RotateSlot
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.input.InputEventAPI
import org.lwjgl.util.vector.Vector2f
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.FastTrig
import java.util.ArrayList

class aEP_RotateSlot : BaseEveryFrameCombatPlugin() {
  var toMove: MutableMap<Int, WeaponAPI> = LinkedHashMap()
  override fun init(engine: CombatEngineAPI) {
    //Global.getLogger(this.javaClass).info("NeiBoRotate_init" + ships.size)
    for (ship in ships) {
      for (slot in ship.hullSpec.allWeaponSlotsCopy) {
        if (slot.id.contains("COVER")) {
          val originSlot = Global.getSettings().getHullSpec(ship.hullSpec.hullId).getWeaponSlotAPI(slot.id)
          slot.location.set(originSlot.location)
          slot.angle = 0f
          //Global.getLogger(this.getClass()).info("NeiBoRotate_restore" + "+" + originSlot.getId());
        }
      }
      //进入战场的 variant并不是装配页面的 variant
      //进入战场的 variant可以任意设置 spec而不互相影响
      //结束战斗时会把战斗的variant改在装配的variant上面
      //战斗外（如 hullmod内）改变spec会改变全局
      //通过移除武器会导致进战场缺武器，返回装配页面不缺
    }
    ships.clear()
    toMove.clear()
  }

  override fun advance(amount: Float, events: List<InputEventAPI>) {
    if (Global.getCombatEngine().isPaused) return
    //Global.getLogger(this.getClass()).info("NeiBoRotate_size"+ships.size());
    for (ship in ships) {
      var weapon: WeaponAPI? = null
      var main: WeaponAPI? = null
      toMove.clear()
      for (w in ship.allWeapons) {
        val slotId = w.slot.id
        if (!slotId.contains("COVER")) {
          continue
        }
        if (slotId.contains("MAIN")) {
          main = w
          continue
        }
        if (slotId == "COVER") {
          weapon = w
          continue
        }
        val num = slotId.replace("COVER", "").toInt()
        toMove[num - 1] = w
      }
      if (main == null || weapon == null || Global.getCombatEngine().isPaused) return
      weapon.currAngle = main.currAngle
      for (i in 0..9) if (toMove.containsKey(i)) {
        val w = toMove[i]
        val slot = w!!.slot
        val center = weapon.location
        val point = weapon.getFirePoint(i)
        //ship.getFacing()是以东为0度，北为90度，无负值
        //VectorUtils.getAngle()得到的角度与战场坐标一致
        val x = VectorUtils.getAngle(weapon.location, weapon.getFirePoint(i)) - ship.facing
        val dist = Vector2f(point.x - center.x, point.y - center.y)
        val y = Math.sqrt((dist.getX() * dist.getX() + dist.getY() * dist.getY()).toDouble()).toFloat()
        val relDistX = y * FastTrig.cos(Math.toRadians(x.toDouble())).toFloat()
        val relDistY = y * FastTrig.sin(Math.toRadians(x.toDouble())).toFloat()
        val weaponSlotRelPos = weapon.slot.location
        slot.location[weaponSlotRelPos.x + relDistX] = weaponSlotRelPos.y + relDistY //FirePoint是绝对位置
        slot.angle = main.currAngle - ship.facing
        //w.getLocation().set(ship.getMouseTarget());
        w.currAngle = main.currAngle
      }
    }
  }

  companion object {
    var ships: MutableList<ShipAPI> = ArrayList()
  }
}