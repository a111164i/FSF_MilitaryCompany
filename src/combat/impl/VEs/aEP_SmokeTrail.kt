package combat.impl.VEs

import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.util.IntervalTracker
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_AngleTracker
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

open class aEP_SmokeTrail : aEP_BaseCombatEffect {
  var lastFrameLoc = Vector2f(0f,0f)
  var maxDistWithoutSmoke = 20f
  var spawnSmokeSize = 25f
  var endSmokeSize = 80f

  var initSpeed = 150f
  var stopSpeed = 0.9f

  var fadeIn = 0.1f
  var fadOut = 0.9f
  var smokeLifeTime = 3f
  var color = Color(120,120,120,120)

  val smokeSpreadAngleTracker = aEP_AngleTracker(0f, 0f, 2f, 20f, -20f)
  val spawnSmokeTracker = IntervalUtil(0.05f,0.05f)

  constructor(m: DamagingProjectileAPI, maxDistWithoutSmoke: Float, lifeTime: Float, smokeSize: Float, maxSmokeSize: Float, color:Color) {
    init(m)
    lastFrameLoc = Vector2f(m.location.x,m.location.y)
    this.maxDistWithoutSmoke = maxDistWithoutSmoke
    this.smokeLifeTime = lifeTime
    this.spawnSmokeSize = smokeSize
    this.endSmokeSize = maxSmokeSize
    this.color = color
  }

  override fun advanceImpl(amount: Float) {
    super.advanceImpl(amount)
    entity?: return
    val entity = entity as CombatEntityAPI

    spawnSmokeTracker.advance(amount)
    if(!spawnSmokeTracker.intervalElapsed()) return

    var movedDistLastFrame = MathUtils.getDistance(lastFrameLoc, entity.location)
    val smokeSize = spawnSmokeSize
    val sizeChange = (endSmokeSize - smokeSize) / smokeLifeTime
    val facingFromLastFrameLoc = VectorUtils.getAngle(lastFrameLoc,entity.location)
    var shouldAddOrignalPosition = false

    if (movedDistLastFrame > maxDistWithoutSmoke) {
      //当一帧移动超过8倍最小距离，视为瞬移，简化计算，直接在原地生成烟雾
      if(movedDistLastFrame > maxDistWithoutSmoke * 8){
        shouldAddOrignalPosition = true
      }else{
        //处于之间时，生成一整条连续的烟雾线
        var num = 0
        val max = (movedDistLastFrame/maxDistWithoutSmoke).toInt() + 1
        val movePerSmoke = movedDistLastFrame/max
        while (num < max) {
          val smokeLoc = aEP_Tool.getExtendedLocationFromPoint(lastFrameLoc,facingFromLastFrameLoc,num*movePerSmoke)
          val smoke = aEP_MovingSmoke(smokeLoc)
          smoke.lifeTime = smokeLifeTime
          smoke.fadeIn = fadeIn
          smoke.fadeOut = fadOut
          smoke.sizeChangeSpeed = sizeChange
          smoke.size = smokeSize
          smoke.stopSpeed = stopSpeed
          smoke.color = color
          smoke.setInitVel(aEP_Tool.speed2Velocity(aEP_Tool.angleAdd(facingFromLastFrameLoc, smokeSpreadAngleTracker.curr), -initSpeed))
          smokeSpreadAngleTracker.advance(1f)
          aEP_CombatEffectPlugin.addEffect(smoke)
          num += 1
        }
        lastFrameLoc.set(entity.location.x, entity.location.y)
      }
    }else{
      shouldAddOrignalPosition = true
    }

    if(shouldAddOrignalPosition){
      val smoke = aEP_MovingSmoke(entity.location)
      smoke.lifeTime = smokeLifeTime
      smoke.fadeIn = fadeIn
      smoke.fadeOut = fadOut
      smoke.sizeChangeSpeed = sizeChange
      smoke.size = smokeSize
      smoke.stopSpeed = stopSpeed
      smoke.color = color
      smoke.setInitVel(aEP_Tool.speed2Velocity(aEP_Tool.angleAdd(facingFromLastFrameLoc, smokeSpreadAngleTracker.curr), -initSpeed))
      smokeSpreadAngleTracker.advance(1f)
      aEP_CombatEffectPlugin.addEffect(smoke)
      lastFrameLoc.set(entity.location.x, entity.location.y)
    }

    //摆动角度处理
    if(smokeSpreadAngleTracker.isInPosition){
      smokeSpreadAngleTracker.randomizeTo()
    }
  }
}