package data.scripts.utils

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.util.IntervalUtil
import org.lazywizard.lazylib.FastTrig
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.lang.ref.WeakReference
import kotlin.math.abs

open class aEP_MovingSprite : aEP_BaseCombatEffect{

  // 全局共享现代渲染工具（避免重复创建VAO/VBO）
  companion object {
    private val renderUtils = aEP_Render.RenderUtils()
    private val activeRenders = mutableListOf<WeakReference<aEP_MovingSprite>>()
    private var lastEngine: com.fs.starfarer.api.combat.CombatEngineAPI? = null

    private fun syncEngine() {
      val engine = Global.getCombatEngine()
      if (engine != lastEngine) {
        activeRenders.clear()
        lastEngine = engine
      }
    }

    private fun renderBatch(viewport: ViewportAPI) {
      if (activeRenders.isEmpty()) return

      val vertexList = mutableListOf<Float>()
      for (spriteRef in activeRenders) {
        val sprite = spriteRef.get() ?: continue
        if (viewport.isNearViewport(sprite.loc, sprite.radius + 600f)) {
          vertexList.addAll(sprite.generateVertices())
        }
      }

      if (vertexList.isEmpty()) return

      aEP_Render.openGL11CombatLayerRendering()
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
      val textureId = (activeRenders.firstOrNull()?.get()?.spriteTexId) ?: 0
      renderUtils.bindTexture(textureId)
      renderUtils.submitVertices(vertexList.toFloatArray(), viewport)
      renderUtils.drawArrays(vertexList.size / 8, renderMode = GL11.GL_QUADS)
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
  *  百分比
  * */
  var fadeIn = 0f
  var fadeOut = 0f

  var size: Vector2f = Vector2f(20f,20f)
  var sizeChangeSpeed: Vector2f = Vector2f(0f,0f)

  var color = Color(255,255,255,255)
  var changingColor = Color(0,0,0,0)

  private var spriteTexId : Int
  private var point1Left = Vector2f(0f,0f)
  private var point1Right = Vector2f(0f,0f)
  private var point2Left = Vector2f(0f,0f)
  private var point2Right = Vector2f(0f,0f)

  constructor(position: Vector2f, size: Vector2f, angle: Float, spriteId: String){
    val id: Array<String> = spriteId.split("\\.".toRegex()).toTypedArray()
    if(spriteId.contains('/')){
      spriteTexId = Global.getSettings().getSprite(spriteId).textureId
    }else{
      spriteTexId = Global.getSettings().getSprite(id[0], id[1]).textureId
    }
    this.loc = position
    this.angle = angle
    this.size = size
  }

  constructor(sprite: SpriteAPI, position: Vector2f?){
    spriteTexId = sprite.textureId
    if(position != null)this.loc = position
    this.angle = sprite.angle
    this.size = Vector2f(sprite.width,sprite.height)
    this.color = sprite.color
  }

  constructor(position: Vector2f, velocity: Vector2f, angleSpeed: Float,angle: Float, fadeIn:Float, full:Float, fadeOut: Float,sizeChange:Vector2f, size:Vector2f, spriteId:String, color:Color){
    val id: Array<String> = spriteId.split("\\.".toRegex()).toTypedArray()
    if(spriteId.contains('/')){
      spriteTexId = Global.getSettings().getSprite(spriteId).textureId
    }else{
      spriteTexId = Global.getSettings().getSprite(id[0], id[1]).textureId
    }
    this.loc = position
    this.velocity = velocity
    this.angle = angle
    this.angleSpeed = angleSpeed
    this.size = size
    this.sizeChangeSpeed = sizeChange
    this.lifeTime = fadeIn+full+fadeOut
    this.fadeIn = fadeIn/lifeTime
    this.fadeOut = fadeOut/lifeTime
    this.color = color
  }

  init {
    // 将当前实例的弱引用添加到活跃渲染列表，如果是本场战斗第一次添加则同步引擎实例，移除上一层战斗遗留的粒子
    syncEngine()
    activeRenders.add(WeakReference(this))
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
    velocity = Vector2f(velocity.x + acceleration.x * amount,velocity.y + acceleration.y)
    loc = Vector2f(loc.x + velocity.x * amount, loc.y + velocity.y * amount)
    updatePositon(loc, angle)
    //每隔0.1秒进行减速
    stopForceTimer.advance(amount)
    if (stopForceTimer.intervalElapsed()) {
      velocity.scale(stopSpeed)
      angleSpeed *= stopSpeed
    }

    angle = aEP_Tool.angleAdd(angle, angleSpeed * amount)
    val sizeX = abs(size.getX() + sizeChangeSpeed.getX() * amount)
    val sizeY = abs(size.getY() + sizeChangeSpeed.getY() * amount)
    size = Vector2f(sizeX,sizeY)

    //更新渲染范围
    radius = Math.max(size.x,size.y )
  }

  override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
    if (Global.getCombatEngine().combatUI == null) {
      activeRenders.clear()
      return
    }

    val firstSprite = activeRenders.firstOrNull()?.get() ?: return
    if (firstSprite == this) {
      Companion.renderBatch(viewport)
    }
  }

  override fun readyToEnd() {
    activeRenders.removeAll { ref -> ref.get() == this }
  }

  // 生成顶点数据（位置x,y + 颜色r,g,b,a + 纹理坐标u,v）
  private fun generateVertices(): List<Float> {
    val vertices = mutableListOf<Float>()
    val red = changingColor.red / 255f
    val green = changingColor.green / 255f
    val blue = changingColor.blue / 255f
    val alpha = changingColor.alpha / 255f

    vertices.add(point1Left.x)
    vertices.add(point1Left.y)
    vertices.add(red)
    vertices.add(green)
    vertices.add(blue)
    vertices.add(alpha)
    vertices.add(0f)
    vertices.add(0f)

    vertices.add(point1Right.x)
    vertices.add(point1Right.y)
    vertices.add(red)
    vertices.add(green)
    vertices.add(blue)
    vertices.add(alpha)
    vertices.add(1f)
    vertices.add(0f)

    vertices.add(point2Right.x)
    vertices.add(point2Right.y)
    vertices.add(red)
    vertices.add(green)
    vertices.add(blue)
    vertices.add(alpha)
    vertices.add(1f)
    vertices.add(1f)

    vertices.add(point2Left.x)
    vertices.add(point2Left.y)
    vertices.add(red)
    vertices.add(green)
    vertices.add(blue)
    vertices.add(alpha)
    vertices.add(0f)
    vertices.add(1f)

    return vertices
  }

  open fun setInitVel(vel: Vector2f) {
    velocity = Vector2f(velocity.x + vel.x, velocity.y + vel.y)
  }

  open fun addAcc(toAddAcceleration: Vector2f) {
    acceleration = Vector2f(acceleration.x + toAddAcceleration.x, acceleration.y + toAddAcceleration.y)
  }

  private fun updatePositon (position: Vector2f,angle: Float){
    val rad = Math.toRadians(angle.toDouble())
    val x0 = FastTrig.cos(rad).toFloat()
    val y0 = FastTrig.sin(rad).toFloat()
    //计算旋转的是半长，不是全长
    val length = size.x/2f
    val height = size.y/2f
    point1Left = Vector2f(position.x - length*x0 + height*y0,position.y - length*y0 - height*x0)
    point1Right = Vector2f(position.x + length*x0 + height*y0,position.y + length*y0 - height*x0)
    point2Left = Vector2f(position.x - length*x0 - height*y0,position.y - length*y0 + height*x0)
    point2Right = Vector2f(position.x+ length*x0 - height*y0,position.y + length*y0 + height*x0)
  }

}
