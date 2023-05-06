package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.absoluteValue

class aEP_DroneBurstAI: aEP_BaseSystemAI() {


  override fun initImpl() {
    thinkTracker.setInterval(0.25f, 0.25f)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {

    target?: kotlin.run {
      shouldActive = false
      return
    }

    val dist = MathUtils.getDistance(ship,target)
    val toTargetFacing = VectorUtils.getAngle(ship.location, target.location)
    val angleDistAbs = MathUtils.getShortestRotation(ship.facing, toTargetFacing).absoluteValue

    //战术系统ai检测
    if (dist <= 500f || angleDistAbs > 45f) {
      shouldActive = false
    }else{
      shouldActive = true
    }

  }
}