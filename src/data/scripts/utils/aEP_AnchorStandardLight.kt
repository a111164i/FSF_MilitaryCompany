package data.scripts.utils

import org.dark.shaders.light.StandardLight
import com.fs.starfarer.api.combat.CombatEntityAPI
import org.lwjgl.util.vector.Vector2f

class aEP_AnchorStandardLight(var light: StandardLight, var anchor: CombatEntityAPI, lifeTime: Float) : aEP_BaseCombatEffect() {
  var relativeInfo: Vector2f
  override fun advance(amount: Float) {
    super.advance(amount)
    light.location = aEP_Tool.getAbsoluteLocation(relativeInfo, anchor, false)
    advanceImpl(amount)
  }

  init {
    val lightPos = light.location
    relativeInfo = aEP_Tool.getRelativeLocationData(lightPos, anchor, false)
    this.lifeTime = lifeTime
  }
}