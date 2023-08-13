package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType
import com.fs.starfarer.api.impl.combat.MineStrikeStats
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_Tool
import data.scripts.ai.shipsystemai.aEP_FlareStrikeAI
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class aEP_FlareStrikeSS: MineStrikeStats() {
  companion object{
    const val ID = "aEP_FlareStrikeSS"
    const val MINE_WEAPON_ID = "aEP_des_shuishi_flare"
    val MINE_JITTER_COLOR = Color(255, 95, 45, 155)
    val RANGE = Global.getSettings().getWeaponSpec(MINE_WEAPON_ID).maxRange
  }

  override fun spawnMine(source: ShipAPI?, mineLoc: Vector2f?) {
    val engine = Global.getCombatEngine()

    if(source != null){
      if(source.customData[aEP_FlareStrikeAI.ID] != null){
        mineLoc?.set(source.customData[aEP_FlareStrikeAI.ID] as Vector2f)
        source.customData.remove(aEP_FlareStrikeAI.ID)
      }
    }
    var currLoc = Misc.getPointAtRadius(mineLoc, 30f + Math.random().toFloat() * 30f)
    val start = Math.random().toFloat() * 360f
    var angle = start
    while (angle < start + 390) {
      if (angle != start) {
        val loc = Misc.getUnitVectorAtDegreeAngle(angle)
        loc.scale(50f + Math.random().toFloat() * 30f)
        currLoc = Vector2f.add(mineLoc, loc, Vector2f())
      }
      for (other in Global.getCombatEngine().missiles) {
        if (!other.isMine) continue
        val dist = Misc.getDistance(currLoc, other.location)
        if (dist < other.collisionRadius + 40f) {
          currLoc = null
          break
        }
      }
      if (currLoc != null) {
        break
      }
      angle += 30f
    }
    if (currLoc == null) {
      currLoc = Misc.getPointAtRadius(mineLoc, 30f + Math.random().toFloat() * 30f)
    }


    val mine = engine.spawnProjectile(
      source, null,
      MINE_WEAPON_ID,
      currLoc,
      Math.random().toFloat() * 360f, null) as MissileAPI

    if (source != null) {

      Global.getCombatEngine().applyDamageModifiersToSpawnedProjectileWithNullWeapon(
        source, WeaponType.MISSILE, false, mine.damage
      )
    }


    val fadeInTime = 0.5f
    mine.velocity.scale(0f)
    mine.fadeOutThenIn(fadeInTime)

    Global.getCombatEngine().addPlugin(createMissileJitterPlugin(mine, fadeInTime))



    Global.getSoundPlayer().playSound("mine_teleport", 1f, 1f, mine.location, mine.velocity)
  }

  override fun createMissileJitterPlugin(mine: MissileAPI?, fadeInTime: Float): EveryFrameCombatPlugin {
    return object : BaseEveryFrameCombatPlugin() {
      var elapsed = 0f
      override fun advance(amount: Float, events: List<InputEventAPI>) {
        if (Global.getCombatEngine().isPaused) return
        elapsed += amount
        var jitterLevel = mine!!.currentBaseAlpha
        if (jitterLevel < 0.5f) {
          jitterLevel *= 2f
        } else {
          jitterLevel = (1f - jitterLevel) * 2f
        }
        val jitterRange = 1f - mine!!.currentBaseAlpha
        //jitterRange = (float) Math.sqrt(jitterRange);
        val maxRangeBonus = 50f
        val jitterRangeBonus = jitterRange * maxRangeBonus
        var c = MINE_JITTER_COLOR
        c = Misc.setAlpha(c, 70)
        //mine.setJitter(this, c, jitterLevel, 15, jitterRangeBonus * 0.1f, jitterRangeBonus);
        mine.setJitter(this, c, jitterLevel, 15, jitterRangeBonus * 0, jitterRangeBonus)
        if (jitterLevel >= 1 || elapsed > fadeInTime) {
          Global.getCombatEngine().removePlugin(this)
        }
      }
    }
  }

  override fun getMaxRange(ship: ShipAPI?): Float {
    return aEP_Tool.getSystemRange(ship,RANGE)
  }

  override fun getMineRange(ship: ShipAPI?): Float {
    return aEP_Tool.getSystemRange(ship,RANGE)
  }

  override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI?): Boolean {
    return super.isUsable(system, ship)
  }

  override fun getFuseTime(): Float {
    return 1.2f
  }
}