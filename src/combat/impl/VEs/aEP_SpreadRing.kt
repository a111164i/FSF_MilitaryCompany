package combat.impl.VEs

import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.util.Noise
import combat.impl.aEP_BaseCombatEffect
import combat.util.aEP_ColorTracker
import combat.util.aEP_Tool
import org.lazywizard.lazylib.FastTrig
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

open class aEP_SpreadRing : aEP_BaseCombatEffect {
  var spreadSpeed = 0f
  var width = 0f
  var ringRadius = 0f
  var startRadius = 0f
  var endRadius = 0f
  var minRadius = 0f
  var initColor = aEP_ColorTracker(0f, 0f, 0f, 0f)
  var endColor = aEP_ColorTracker(0f, 0f, 0f, 0f)
  var precision = 80
  var center = Vector2f(0f, 0f)
  var noise: Noise? = null
  var fadeAfter = 0f
  var fadeTime = 0.5f

  constructor(
    spreadSpeed: Float,
    width: Float,
    initColor: Color,
    startRadius: Float,
    endRadius: Float,
    center: Vector2f
  ) {
    this.spreadSpeed = spreadSpeed
    this.width = width
    this.initColor = aEP_ColorTracker(initColor)
    this.ringRadius = startRadius
    this.center = center
    this.startRadius = startRadius
    this.endRadius = endRadius
    minRadius = startRadius
    noise = Noise()
    radius = endRadius
  }


  override fun advance(amount: Float) {
    super.advance(amount)
    ringRadius = MathUtils.clamp(ringRadius + spreadSpeed * amount, 0f, 99999f)
    val toRenderRadius = MathUtils.clamp(ringRadius, 10f, 99999f)
    if (toRenderRadius - width > endRadius || toRenderRadius <= 10f && spreadSpeed <= 0) cleanup()
    if (endColor.alpha < 1f && initColor.alpha < 1f) cleanup()


    //change center if anchor != null
    if (entity != null) center = entity!!.location
    if (time > fadeAfter && fadeAfter > 0) {
      initColor.setToColor(initColor.red, initColor.green, initColor.blue, 0f, fadeTime)
      endColor.setToColor(endColor.red, endColor.green, endColor.blue, 0f, fadeTime)
      fadeAfter = 0f
    }


    //改变颜色
    initColor!!.advance(amount)
    endColor.advance(amount)

    advanceImpl(amount)
  }

  override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
    //calculate num of vertex
    val toRenderRadius = MathUtils.clamp(ringRadius, 10f, 99999f)
    val numOfVertex = MathUtils.clamp(precision + ((toRenderRadius + width) / 5f).toInt(), precision, 560)

    //使用模板缓存的步骤一般如下：
    //开启模板测试
    //绘制模板，写入模板缓冲(不写入color buffer和depth buffer)
    //关闭模板缓冲写入
    //接着我们利用模板缓冲中的值决定是丢弃还是保留后续绘图中的片元。

    //当启动 模板测试时，
    //通过模板测试的片段像素点会被替换到颜色缓冲区中，从而显示出来，
    //未通过的则不会保存到颜色缓冲区中，从而达到了过滤的功能

    //glPushAttrib()把绘制前的状态，推入栈保存起来，接下来要动参数了
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
    //开启模板处理
    GL11.glEnable(GL11.GL_STENCIL_TEST)
    //画模板测试
    drawStencilCircle(center, numOfVertex.toFloat(), endRadius)

    //第二轮设置完毕后，开始画实际渲染的圆环
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    GL11.glDisable(GL11.GL_TEXTURE_2D)
    GL11.glBegin(GL11.GL_QUAD_STRIP)
    for (i in 0..numOfVertex) {
      val innerR = Math.max(minRadius, toRenderRadius - width)
      val outerR = Math.min(toRenderRadius, endRadius)
      val pointNear = Vector2f(center.x + innerR * FastTrig.cos(2f * Math.PI * i / numOfVertex).toFloat(), center.y + innerR * FastTrig.sin(2f * Math.PI * i / numOfVertex).toFloat())
      val pointFar = Vector2f(center.x + outerR * FastTrig.cos(2 * Math.PI * i / numOfVertex).toFloat(), center.y + outerR * FastTrig.sin(2 * Math.PI * i / numOfVertex).toFloat())
      GL11.glColor4ub(endColor.red.toInt().toByte(), endColor.green.toInt().toByte(), endColor.blue.toInt().toByte(), endColor.alpha.toInt().toByte())

      GL11.glVertex2f(pointNear.x, pointNear.y)
      GL11.glColor4ub(initColor.red.toInt().toByte(), initColor.green.toInt().toByte(), initColor.blue.toInt().toByte(), initColor.alpha.toInt().toByte())
      GL11.glVertex2f(pointFar.x, pointFar.y)
      //aEP_Tool.addDebugText("1",point);
    }
    GL11.glEnd()

    //关闭模板测试
    GL11.glDisable(GL11.GL_STENCIL_TEST)
    //把glStencilFunc的设置还原到绘制之前
    GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 255)
    //还原参数到绘制之前的状态
    GL11.glPopAttrib()
  }

  @JvmName("setMinRadius1")
  fun setMinRadius(minRadius: Float) {
    this.minRadius = MathUtils.clamp(minRadius, 10f, 99999f)
  }

  @JvmName("getRadius1")
  fun getRadius(): Float {
    var level = MathUtils.clamp(time / lifeTime, 0f, 1f)
    level = aEP_Tool.exponentialDecreaseSmooth(level)
    val radiusChange = spreadSpeed * lifeTime * level
    return MathUtils.clamp(ringRadius + radiusChange, 10f, 99999f)
  }



  companion object {
    fun drawStencilCircle(center: Vector2f, numOfVertex: Float, endRadius: Float) {
      //禁止写入颜色和深度缓冲区，因为模板最终是不显示在屏幕上的
      //有些时候模板也需要显示，注意在合适的时候调整这部分代码。
      GL11.glDisable(GL11.GL_DEPTH_TEST)
      GL11.glColorMask(false, false, false, false)
      Color(0, 0, 255, 255)
      // glStencilFunc 用于指定模板测试的函数，这里指定是什么情况下通过模板测试。
      // 参数：
      // func 是比较方式，GL_ALWAYS表示总能通过
      // ref 是和当前模板缓冲中的值stencil进行比较的参考值，这个比较方式使用了第三个参数mask
      // mask 为蒙版: 通常用0x00表示全不过，和0xFF（即255）表示全过（二进制1表示过0表示不过）
      //例如 glStencilFunc(GL_EQUAL, 1, 255)
      //表示当前模板缓冲区中值为1的部分通过模板测试，这部分片元将被保留，其余地则被丢弃。
      GL11.glStencilFunc(GL11.GL_ALWAYS, GL11.GL_POLYGON_STIPPLE_BIT, 255)
      //  123     010     020
      //  456  &  101  =  406
      //  789     001     009
      //          mask
      // mask指向一个长度为128字节的空间，
      // 它表示了一个32*32的矩形应该如何镂空。
      // 其中：第一个字节表示了最左下方的从左到右（也可以是从右到左，这个可以修改）
      // 8个像素是否镂空（1表示不镂空，显示该像素；0表示镂空，显示其后面的颜色），
      // 最后一个字节表示了最右上方的8个像素是否镂空。
      GL11.glStencilMask(255)
      //glStencilOp 用于指定测试通过或者失败时执行的动作，例如保留缓冲区中值，或者使用ref值替代等操作。
      //现在表示通过测试的全部替换为ref的值（上一步已经设置ref为16）
      GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE)
      //glClearStencil指定glClear用于清除模板缓冲区重新写入的值
      GL11.glClearStencil(0)
      //模板缓冲和深度缓冲一样，需要清除，
      //默认清除时写入0，
      //上一步已经指定写入值为0
      //现在缓冲区全部被擦为0
      GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)


      // 设置完成后，画个不显示的圆作为模板缓冲区先
      // 被清空后的缓冲区值全部为0
      // glStencilFunc已经被设置为全部可通过，这个圆内的像素全部过了
      // glStencilOp已经被设置为通过的像素值由0替换为ref（即16）
      // 也就是外面都是0，圆内都是16
      GL11.glBegin(GL11.GL_POLYGON)
      var i = 0
      while (i <= numOfVertex) {
        val pointNear = Vector2f(center.x + endRadius * FastTrig.cos(2f * Math.PI * i / numOfVertex).toFloat(), center.y + endRadius * FastTrig.sin(2f * Math.PI * i / numOfVertex).toFloat())
        GL11.glVertex2f(pointNear.x, pointNear.y)
        i++
      }
      GL11.glEnd()
      //接下来的圆环是要实际画出来的，提前把颜色写入打开
      GL11.glColorMask(true, true, true, true)
      //接下来写入缓冲区的圆是要实际画出来的，把模板测试的参数改了
      //只有当前模板缓冲区中值为16的部分通过模板测试（即上一个画的圆的内部）
      GL11.glStencilFunc(GL11.GL_EQUAL, GL11.GL_POLYGON_STIPPLE_BIT, 255)
      //通过了模板测试的像素，不变动
      GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
      //这个环不写入缓冲区
      GL11.glStencilMask(0)
    }
  }
}
