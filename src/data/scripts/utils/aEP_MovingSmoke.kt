package data.scripts.utils

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.util.IntervalUtil
import data.scripts.aEP_CombatEffectPlugin
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_QUADS
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.lang.ref.WeakReference
import java.util.*

class aEP_MovingSmoke// 将当前实例添加到活跃渲染列表
  (loc: Vector2f) : aEP_BaseCombatEffect() {

  // 全局共享现代渲染工具（避免重复创建VAO/VBO）
  companion object {
    private val renderUtils = aEP_Render.RenderUtils()

    // 使用弱引用列表，避免内存泄漏
    private val activeRenders = mutableListOf<WeakReference<aEP_MovingSmoke>>()
    private var lastEngine: CombatEngineAPI? = null

    // 检测当前战斗引擎实例是否发生变化，如果变化就清理上一场战斗遗留的渲染对象（取巧了，但是有用）
    private fun syncEngine() {
      val engine = Global.getCombatEngine()
      if (engine != lastEngine) {
        activeRenders.clear()
        lastEngine = engine
      }
    }

    // 批量渲染所有活跃的aEP_MovingSmoke
    private fun renderBatch(viewport: ViewportAPI) {
      // 1, 收集所有顶点数据（位置+颜色+纹理坐标，float数组）
      val vertexList = mutableListOf<Float>()
      for (smokeRef in activeRenders) {
        // 获取弱引用中的实际对象
        val smoke = smokeRef.get() ?: continue

        // 仅处理存活且在视口内的实例
        if (viewport.isNearViewport(smoke.loc, smoke.radius + 600f)) {
          val smokeVertices = smoke.generateVertices()
          vertexList.addAll(smokeVertices)
        }
      }

      // 无有效顶点数据时直接返回
      if (vertexList.isEmpty()) return

      // 2, 提交顶点数据到GPU，批量绘制
      aEP_Render.openGL11CombatLayerRendering()
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
      renderUtils.bindTexture(Global.getSettings().getSprite("aEP_FX", "thick_smoke_all2").textureId)
      renderUtils.submitVertices(vertexList.toFloatArray(), viewport)
      renderUtils.drawArrays(vertexList.size / 8, renderMode = GL_QUADS) // 每顶点8个float (x,y,r,g,b,a,u,v)
      aEP_Render.closeGL11()
    }

  }

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

  // 材质为4x4个粒子形态在一张图上，随机挑一个
  var numByLine = 4
  var usingX = MathUtils.getRandomNumberInRange(0, numByLine - 1)
  var usingY = MathUtils.getRandomNumberInRange(0, numByLine - 1)

  private var point1Left = Vector2f(0f,0f)
  private var point1Right = Vector2f(0f,0f)
  private var point2Left = Vector2f(0f,0f)
  private var point2Right = Vector2f(0f,0f)

  init {
    angle = MathUtils.getRandomNumberInRange(0f,360f)
    angleSpeed = MathUtils.getRandomNumberInRange(-25f,25f)
    this.loc = Vector2f(loc.x,loc.y)
    layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)
    // 将当前实例的弱引用添加到活跃渲染列表，如果是本场战斗第一次添加则同步引擎实例，移除上一层战斗遗留的粒子
    syncEngine()
    activeRenders.add(WeakReference(this))
  }

  override fun advanceImpl(amount: Float) {
    //默认颜色是处于 full状态
    val R = color.red
    val G = color.green
    val B = color.blue
    changingColor = color

    //fade in
    val fadeInTime = fadeIn*lifeTime
    if (time < fadeInTime && fadeInTime > 0f) {
      val transparency = MathUtils.clamp(color.alpha * time / fadeInTime, 0f, 250f).toInt()
      changingColor = Color(R, G, B, transparency)
    }

    //fade out
    val fadeOutTime = fadeOut*lifeTime
    if (lifeTime - time < fadeOutTime &&  fadeOutTime > 0f) {
      val transparency = MathUtils.clamp(color.alpha * (lifeTime - time) / fadeOutTime, 0f, 250f).toInt()
      changingColor = Color(R, G, B, transparency)
    }
    velocity = Vector2f(velocity.x + acceleration.x*amount,velocity.y + acceleration.y*amount)
    loc = Vector2f(loc.x + velocity.x * amount, loc.y + velocity.y * amount)
    updatePositon(loc)

    stopForceTimer.advance(amount)
    if (stopForceTimer.intervalElapsed()) {
      velocity.scale(stopSpeed)
    }

    angle = aEP_Tool.angleAdd(angle, angleSpeed * amount)
    size = Math.abs(size + sizeChangeSpeed * amount)

    //更新渲染范围，方形边长转成斜角长
    radius = size * 1.414f
  }

  override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
    if(Global.getCombatEngine().combatUI == null) {
      activeRenders.clear()
      return
    }



    // 仅触发一次批量渲染
    if (activeRenders.isEmpty()) return
    val firstSmoke = activeRenders.firstOrNull()?.get() ?: return
    if (firstSmoke == this) {
      Companion.renderBatch(viewport)
    }
  }

  override fun readyToEnd() {
    // 将当前实例的弱引用从活跃渲染列表中移除
    activeRenders.removeAll { ref -> ref.get() == this }
  }

  // 生成顶点数据（位置x,y + 颜色r,g,b,a + 纹理坐标u,v）
  fun generateVertices(): List<Float> {
    val vertices = mutableListOf<Float>()
    val red = changingColor.red / 255f
    val green = changingColor.green / 255f
    val blue = changingColor.blue / 255f
    val alpha = changingColor.alpha / 255f

    //4x4的贴图中截取一块
    val X = usingX
    val Y = usingY
    val percent = 1f / numByLine

    // 顶点1: point1Left
    vertices.add(point1Left.x)
    vertices.add(point1Left.y)
    vertices.add(red)
    vertices.add(green)
    vertices.add(blue)
    vertices.add(alpha)
    vertices.add(X * percent)
    vertices.add(Y * percent)

    // 顶点2: point1Right
    vertices.add(point1Right.x)
    vertices.add(point1Right.y)
    vertices.add(red)
    vertices.add(green)
    vertices.add(blue)
    vertices.add(alpha)
    vertices.add((X + 1) * percent)
    vertices.add(Y * percent)

    // 顶点3: point2Right
    vertices.add(point2Right.x)
    vertices.add(point2Right.y)
    vertices.add(red)
    vertices.add(green)
    vertices.add(blue)
    vertices.add(alpha)
    vertices.add((X + 1) * percent)
    vertices.add((Y + 1) * percent)

    // 顶点4: point2Left
    vertices.add(point2Left.x)
    vertices.add(point2Left.y)
    vertices.add(red)
    vertices.add(green)
    vertices.add(blue)
    vertices.add(alpha)
    vertices.add(X * percent)
    vertices.add((Y + 1) * percent)

    return vertices
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