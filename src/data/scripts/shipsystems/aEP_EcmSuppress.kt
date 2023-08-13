package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.skills.ElectronicWarfare
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import combat.impl.VEs.aEP_SpreadRing
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Tool
import data.scripts.weapons.Glow
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.AIUtils
import java.awt.Color
import java.util.*

class aEP_EcmSuppress: BaseShipSystemScript() {
  companion object{
    const val ID = "aEP_EcmSuppress"
    const val SYSTEM_RANGE = 2000f
    const val SYSTEM_TIME = 10f
    val JITTER_COLOR = Color(71,90,175,40)
    val RING_COLOR = Color(71,90,175,125)
    val RING_COLOR2 = Color(71,190,175,125)


    val PUNISH_HULLSIZE = HashMap<ShipAPI.HullSize, Float>()
    const val SELF_BONUS = 1f
    init {
      PUNISH_HULLSIZE[ShipAPI.HullSize.DEFAULT] = -2f
      PUNISH_HULLSIZE[ShipAPI.HullSize.FIGHTER] = -0f

      PUNISH_HULLSIZE[ShipAPI.HullSize.FRIGATE] = -1f
      PUNISH_HULLSIZE[ShipAPI.HullSize.DESTROYER] = -2f
      PUNISH_HULLSIZE[ShipAPI.HullSize.CRUISER] = -3f
      PUNISH_HULLSIZE[ShipAPI.HullSize.CAPITAL_SHIP] = -4f
    }

  }

  var didOnce = false

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //复制粘贴
    if (stats == null || stats.entity == null || stats.entity !is ShipAPI) return
    val ship = stats.entity as ShipAPI
    val amount = aEP_Tool.getAmount(ship)

    ship.setJitter(ID, JITTER_COLOR, effectLevel, 16, (10f+ship.collisionRadius*0.1f) * effectLevel)

    if(!didOnce && effectLevel >= 1f){
      didOnce = true
      val searchRange = aEP_Tool.getSystemRange(ship, SYSTEM_RANGE)
      for(e in AIUtils.getNearbyEnemies(ship, searchRange - ship.collisionRadius)){
        if(e.isDrone) continue
        if(e.isFighter) continue
        if(e.isStationModule) continue

        //终结目前生效的ecmChange，放一个新的上去（刷新buff的时间）
        val dist = MathUtils.getDistance(e, ship.location)

        if(e.customData.containsKey(ID)){
          (e.customData[ID] as EcmChange).shouldEnd = true
        }
        val effect = EcmChange(e, PUNISH_HULLSIZE[e.hullSize]?:-2f, SYSTEM_TIME, dist/searchRange)
        e.setCustomData(ID, effect)
        aEP_CombatEffectPlugin.addEffect(effect)
      }

      var self = ship
      if(ship.isStationModule && ship.parentStation != null) {
        self = ship.parentStation
      }

      //终结目前生效的ecmChange，放一个新的上去（刷新buff的时间）
      if(self.customData.containsKey(ID)){
        (self.customData[ID] as EcmChange).shouldEnd = true
      }
      val effect = EcmChange(self, SELF_BONUS, SYSTEM_TIME, 0f)
      self.setCustomData(ID, effect)
      aEP_CombatEffectPlugin.addEffect(effect)


      val ring = aEP_SpreadRing(
        searchRange,
        50f,
        RING_COLOR,
        ship.collisionRadius, searchRange + ship.collisionRadius, ship.location)
      ring.layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER)
      ring.initColor.setToColor(RING_COLOR.red.toFloat(),RING_COLOR.green.toFloat(),RING_COLOR.blue.toFloat(),0f,1f)
      ring.endColor.setColor(0f,0f,0f,0f)
      aEP_CombatEffectPlugin.addEffect(ring)

    }

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    //stats.dynamic.getMod(Stats.ELECTRONIC_WARFARE_FLAT).unmodify(id)
    //stats.dynamic.getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyFlat(id, 10f)
    didOnce = false
  }

  class EcmChange(val ship: ShipAPI, val ecmPoint: Float, lifetime:Float, var arcDelay : Float): aEP_BaseCombatEffect(lifetime, ship){
    init {
      ship.mutableStats.dynamic.getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyFlat(ID, ecmPoint)

    }

    override fun advanceImpl(amount: Float) {
      val timeLeft = lifeTime - time

      if(time > arcDelay){
        for (i in 0 until 11){
          val from = MathUtils.getRandomPointInCircle(ship.location, ship.collisionRadius)
          val to = MathUtils.getRandomPointInCircle(from, 50f)
          val thickness = MathUtils.getRandomNumberInRange(8f,24f)
          Global.getCombatEngine().spawnEmpArcVisual(
            from, ship,
            to, ship,
            thickness,
            RING_COLOR, Color.white)
          arcDelay = Float.MAX_VALUE
        }
      }

      val fadingThreshold = 4f
      if(timeLeft < fadingThreshold){
        val level = timeLeft/fadingThreshold
        ship.mutableStats.dynamic.getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyFlat(ID, ecmPoint * level)
      }
    }

    override fun readyToEnd() {
      ship.mutableStats.dynamic.getMod(Stats.ELECTRONIC_WARFARE_FLAT).unmodify(ID)
    }
  }
}