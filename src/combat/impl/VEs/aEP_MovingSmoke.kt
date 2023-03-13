package combat.impl.VEs

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.util.IntervalUtil
import combat.impl.aEP_BaseCombatEffect
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*

class aEP_MovingSmoke : aEP_BaseCombatEffect{
  var velocity = Vector2f(0f, 0f)
  var angle = 0f
  var acceleration = Vector2f(0f, 0f)
  var angleSpeed= 0f


  val stopForceTimer = IntervalUtil(0.1f,0.1f)
  var stopSpeed = 1f

  /**
   * @param fadeIn by percent to the total lifetime
   * @param fadeOut by percent to the total lifetime
   */
  var fadeIn = 0f
  var fadeOut = 0f

  var size = 20f
  var sizeChangeSpeed = 0f

  var color = Color(240,240,240,240)
  var changingColor = Color(0,0,0,0)


  var numByLine = 4
  var usingX = MathUtils.getRandomNumberInRange(0, numByLine - 1)
  var usingY = MathUtils.getRandomNumberInRange(0, numByLine - 1)

  private var point1Left = Vector2f(0f,0f)
  private var point1Right = Vector2f(0f,0f)
  private var point2Left = Vector2f(0f,0f)
  private var point2Right = Vector2f(0f,0f)


  constructor(loc:Vector2f) {
    aEP_BaseCombatEffect()
    angle = MathUtils.getRandomNumberInRange(0f,360f)
    angleSpeed = MathUtils.getRandomNumberInRange(-30f,30f)
    this.loc = Vector2f(loc.x,loc.y)
    layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
  }

  override fun advanceImpl(amount: Float) {
    //默认颜色是处于 full状态
    changingColor = color
    val fadeInTime = fadeIn*lifeTime
    if (time < fadeInTime) {
      val R = color.red
      val G = color.green
      val B = color.blue
      val transparency = MathUtils.clamp(color.alpha * time / fadeInTime, 0f, 250f).toInt()
      changingColor = Color(R, G, B, transparency)
    }

    //fade out
    val fadeOutTime = fadeOut*lifeTime
    if (lifeTime - time < fadeOutTime) {
      val R = color.red
      val G = color.green
      val B = color.blue
      val transparency = MathUtils.clamp(color.alpha * (lifeTime - time) / fadeOutTime, 0f, 250f).toInt()
      changingColor = Color(R, G, B, transparency)
    }
    velocity = Vector2f(velocity.x + acceleration.x*amount,velocity.y + acceleration.y)
    loc = Vector2f(loc.x + velocity.x * amount, loc.y + velocity.y * amount)
    updatePositon(loc)

    stopForceTimer.advance(amount)
    if (stopForceTimer.intervalElapsed()) {
      velocity.scale(stopSpeed)
    }

    angle = aEP_Tool.angleAdd(angle, angleSpeed * amount)
    size = Math.abs(size + sizeChangeSpeed * amount)

    //更新渲染范围
    radius = size
  }

  override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
    //render会在 advance之前被调用
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glEnable(GL11.GL_TEXTURE_2D)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, Global.getSettings().getSprite("aEP_FX", "thick_smoke_all2").textureId)

    //begin
    GL11.glBegin(GL11.GL_QUADS)
    val red = changingColor.red
    val green = changingColor.green
    val blue = changingColor.blue
    val alpha = changingColor.alpha

    GL11.glColor4ub(red.toByte(), green.toByte(), blue.toByte(), alpha.toByte())
    val X = usingX
    val Y = usingY
    val percent = 1f / numByLine
    GL11.glTexCoord2f(X * percent, Y * percent)
    GL11.glVertex2f(point1Left.getX(), point1Left.getY())
    GL11.glTexCoord2f((X + 1) * percent, Y * percent)
    GL11.glVertex2f(point1Right.getX(), point1Right.getY())
    GL11.glTexCoord2f((X + 1) * percent, (Y + 1) * percent)
    GL11.glVertex2f(point2Right.getX(), point2Right.getY())
    GL11.glTexCoord2f(X * percent, (Y + 1) * percent)
    GL11.glVertex2f(point2Left.getX(), point2Left.getY())

    //end
    GL11.glEnd()
  }

  fun setInitVel(vel: Vector2f) {
    velocity = Vector2f(velocity.x + vel.x, velocity.y + vel.y)
  }

  fun addAcc(toAddAcceleration: Vector2f) {
    acceleration = Vector2f(acceleration.x + toAddAcceleration.x, acceleration.y + toAddAcceleration.y)
  }

  fun setAcc(newVel: Vector2f) {
    acceleration = Vector2f(newVel.x, newVel.y)
  }

  private fun updatePositon (position: Vector2f){
    point1Left = aEP_Tool.getExtendedLocationFromPoint(position, angle + 45f, size)
    point1Right = aEP_Tool.getExtendedLocationFromPoint(position, angle - 45f, size)
    point2Left = aEP_Tool.getExtendedLocationFromPoint(position, angle + 135f, size)
    point2Right = aEP_Tool.getExtendedLocationFromPoint(position, angle - 135f, size)
  }

}