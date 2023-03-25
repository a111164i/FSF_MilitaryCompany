package combat.impl.VEs

import com.fs.starfarer.api.Global
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
  val maxTimeWithoutSmoke = IntervalUtil(0.1f,0.1f)
  var maxDistWithoutSmoke = 20f
  var spawnSmokeSize = 25f
  var endSmokeSize = 80f

  var initSpeed = 150f
  var stopSpeed = 0.9f

  var fadeIn = 0.2f
  var fadOut = 0.8f
  var smokeLifeTime = 3f
  var color = Color(120,120,120,120)
  var flareColor:Color? = null
  var flareColor2:Color? = null


  val smokeSpreadAngleTracker = aEP_AngleTracker(0f, 0f, 2f, 20f, -20f)

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

    var movedDistLastFrame = MathUtils.getDistance(lastFrameLoc, entity.location)
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
        val facingFromLastFrameLoc = VectorUtils.getAngle(lastFrameLoc,entity.location)
        //随机一部分lifeTime，造成末端不均匀消散
        while (num < max) {
          spawnSmoke(aEP_Tool.getExtendedLocationFromPoint(lastFrameLoc,facingFromLastFrameLoc,num*movePerSmoke))
          num += 1
        }
        lastFrameLoc.set(entity.location.x, entity.location.y)
      }
      maxTimeWithoutSmoke.elapsed = 0f
    }else{
      //如果一帧内没有移动超过maxDist，即停留在原地时，开始计时，停留超过一段时间也会smoke
      maxTimeWithoutSmoke.advance(amount)
      if(maxTimeWithoutSmoke.intervalElapsed()) shouldAddOrignalPosition = true
    }

    if(shouldAddOrignalPosition){
      spawnSmoke(entity.location)
      lastFrameLoc.set(entity.location.x, entity.location.y)
    }

    //摆动角度处理
    if(smokeSpreadAngleTracker.isInPosition){
      smokeSpreadAngleTracker.randomizeTo()
    }
  }

  fun spawnSmoke(smokeLoc : Vector2f){
    val smokeSize = spawnSmokeSize
    val sizeChange = (endSmokeSize - smokeSize) / smokeLifeTime
    val smoke = aEP_MovingSmoke(smokeLoc)
    val initVel = aEP_Tool.speed2Velocity(aEP_Tool.angleAdd(entity!!.facing, smokeSpreadAngleTracker.curr), -initSpeed)
    val random= MathUtils.getRandomNumberInRange(0.5f,1f)
    smoke.lifeTime = smokeLifeTime *random
    smoke.fadeIn = fadeIn * random
    smoke.fadeOut = fadOut * random
    smoke.sizeChangeSpeed = sizeChange
    smoke.size = smokeSize
    smoke.stopSpeed = stopSpeed
    smoke.color = color
    smoke.setInitVel(initVel)
    smokeSpreadAngleTracker.advance(1f)
    aEP_CombatEffectPlugin.addEffect(smoke)

    if(flareColor != null || flareColor2 != null){
      Global.getCombatEngine().addNebulaSmokeParticle(smokeLoc,initVel,smokeSize,1f,0.2f,0.3f,0.5f,flareColor)
      Global.getCombatEngine().addNebulaSmokeParticle(smokeLoc,initVel,smokeSize,1f,0.5f,0.4f,0.5f,flareColor2)
    }
  }
}