package combat.impl.VEs

import com.fs.starfarer.api.Global
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

val DEFAULT_YELLOW = Color(240,210,50,150)

fun spawnRepairingSpark(location: Vector2f, color:Color?){
  var usingColor = Color(240,210,10)
  if(color != null){
    usingColor = color
  }
  val engine = Global.getCombatEngine()
  val numOfSparks = MathUtils.getRandomNumberInRange(10,20)
  val size = arrayOf(5,8)
  var i = 0
  while (i < numOfSparks) {
    val speed = 20f + MathUtils.getRandomNumberInRange(0,60)
    val vel = aEP_Tool.speed2Velocity(MathUtils.getRandomNumberInRange(0f, 360f), speed)
    engine!!.addSmoothParticle(
      location,
      vel,  //velocity
      MathUtils.getRandomNumberInRange(size[0].toFloat(), size[1].toFloat()),
      1f,  // brightness
      0.75f,  //particle live time
      usingColor
    )
    i += 1
  }

  //create brighter spark center point
  engine.addSmoothParticle(
    location,
    Vector2f(0f, 0f),  //velocity
    10f,
    1f,  // brightness
    0.5f,  //particle live time
    Color(255,255,255)
  )

  //create brighter spark in center
  engine.addSmoothParticle(
    location,
    Vector2f(0f, 0f),  //velocity
    30f,
    1f,  // brightness
    0.5f,  //particle live time
    usingColor
  )

  //create spark around light
  engine.addHitParticle(
    location,
    Vector2f(0f, 0f),  //velocity
    100f,
    0.1f,  // brightness
    0.75f,  //particle live time
    Color(usingColor.red,usingColor.green,usingColor.blue,30)
  )

}