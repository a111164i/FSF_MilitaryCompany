package data.scripts

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import data.scripts.utils.aEP_BaseCombatEffect
import data.scripts.utils.aEP_Render
import data.scripts.utils.aEP_Tool
import data.scripts.weapons.Glow
import java.util.*
import kotlin.collections.LinkedHashMap

/**
 * 自由特效管理器，统一处理自定义特效的更新、渲染、生命周期管理
 * 核心职责：
 * 1. 注册到战斗引擎，接管所有aEP_BaseCombatEffect类型特效的生命周期
 * 2. 分离特效的逻辑更新（advance）和渲染（render）流程
 * 3. 保证特效添加/移除的线程安全（单帧内）
 */
class aEP_CombatEffectPlugin : BaseEveryFrameCombatPlugin(), CombatLayeredRenderingPlugin {

  // 常量定义：替换魔法值，提升可读性
  companion object Mod {
    private const val LOG_TAG = "aEP_CombatEffectPlugin"
    private const val CUSTOM_DATA_KEY = "aEP_CombatRenderPlugin"
    // 调试日志开关：发布时设为false
    var DEBUG_MODE = false

    /**
     * 向管理器添加新特效（线程安全，单帧内延迟添加）
     * @param effect 待添加的自定义特效
     */
    fun addEffect(effect: aEP_BaseCombatEffect) {
      val combatEngine = Global.getCombatEngine()
      val plugin = combatEngine?.customData?.get(CUSTOM_DATA_KEY) as? aEP_CombatEffectPlugin

      if (plugin != null) {
        plugin.pendingAddEffects.add(effect)
        // 调试日志：仅DEBUG模式输出
        if (DEBUG_MODE) {
          Global.getLogger(javaClass).info("$LOG_TAG ${effect.javaClass.simpleName}: Added")
        }
      } else {
        // 日志级别改为WARN，突出异常
        Global.getLogger(javaClass).warn("$LOG_TAG ${effect.javaClass.simpleName}: Plugin not found in Custom Data")
      }
    }

    /**
     * 获取当前激活的特效列表（只读快照）
     */
    fun getActiveEffects(): List<aEP_BaseCombatEffect> {
      val combatEngine = Global.getCombatEngine()
      val plugin = combatEngine?.customData?.get(CUSTOM_DATA_KEY) as? aEP_CombatEffectPlugin
      return plugin?.activeEffects?.toList() ?: emptyList()
    }

    fun getRenderUtils(name: String): aEP_Render.RenderUtils? {
      val combatEngine = Global.getCombatEngine()
      val plugin = combatEngine?.customData?.get(CUSTOM_DATA_KEY) as? aEP_CombatEffectPlugin
      return plugin?.renderUtils?.get(name)
    }

    /**
     * @param name 用class的全称，必须要包含父类的包名
     */
    fun addRenderUtils(name: String, renderUtils: aEP_Render.RenderUtils) {
      val combatEngine = Global.getCombatEngine()
      val plugin = combatEngine?.customData?.get(CUSTOM_DATA_KEY) as? aEP_CombatEffectPlugin
      plugin?.renderUtils?.put(name, renderUtils)
    }
  }

  // 空安全优化：移除lateinit，改用可空类型+默认值
  private var engine: CombatEngineAPI? = null
  private val layers: EnumSet<CombatEngineLayers> = EnumSet.allOf(CombatEngineLayers::class.java)
  private var shouldEnd = false
  private var frameAmount = 0f // 单帧时间增量，替代原类级别的amount

  // 集合优化：使用LinkedList的特性，提升遍历/移除效率
  private val activeEffects = LinkedList<aEP_BaseCombatEffect>()
  private val pendingAddEffects = LinkedList<aEP_BaseCombatEffect>()
  private val renderUtils = LinkedHashMap<String,aEP_Render.RenderUtils>()

  /**
   * 战斗引擎初始化时触发，完成插件注册和数据初始化
   * 不一定先比advance执行，具体看alex注释
   * @param engine 战斗引擎实例
   */
  override fun init(engine: CombatEngineAPI) {
    this.engine = engine
    // 注册分层渲染插件
    engine.addLayeredRenderingPlugin(this)
    // 存入自定义数据，供外部调用
    engine.customData[CUSTOM_DATA_KEY] = this
    // 清空残留特效
    activeEffects.clear()
    pendingAddEffects.clear()
    FSFModPlugin.loadLightData()

    Global.getLogger(javaClass).info("$LOG_TAG Registered in EveryFrameCombatPlugin")
  }

  /**
   * 分层渲染插件初始化（仅做安全校验）
   * @param entity 关联实体（本插件无关联实体）
   */
  override fun init(entity: CombatEntityAPI?) {
    engine ?: Global.getCombatEngine()
    Global.getLogger(javaClass).info("$LOG_TAG Registered in CombatLayeredRenderingPlugin")
  }

  /**
   * 每帧更新特效逻辑（与渲染分离，暂停时不执行）
   * @param amount 帧时间增量（秒）
   */
  override fun advance(amount: Float) {
    val currentEngine = engine ?: Global.getCombatEngine() ?: return
    // 暂停时重置时间增量，避免特效异常
    frameAmount = if (currentEngine.isPaused) 0f else amount

    // 批量添加待加入的新特效（替代循环add，提升效率）
    if (pendingAddEffects.isNotEmpty()) {
      activeEffects.addAll(pendingAddEffects)
      pendingAddEffects.clear()
    }

    // 迭代器遍历：边遍历边移除，避免创建临时列表
    val iterator = activeEffects.iterator()
    while (iterator.hasNext()) {
      val effect = iterator.next()
      if (effect.isExpired) {
        iterator.remove()
        continue
      }
      // 执行每个视效的advance逻辑
      effect.advance(frameAmount)
    }

    // 调试日志：通过开关控制，避免发布时输出
    if (DEBUG_MODE) {
      Global.getLogger(javaClass).info("$LOG_TAG Effects count: ${activeEffects.size}")
    }
  }

  /**
   * 获取渲染半径（覆盖整个战场）
   * @return 渲染半径最大值
   */
  override fun getRenderRadius(): Float = Float.MAX_VALUE

  /**
   * 按层渲染所有激活的特效
   * @param layer 当前渲染层
   * @param viewport 视口实例
   */
  override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
    // 空安全简化：使用作用域函数，减少冗余判断
    layer ?: return
    viewport ?: return

    activeEffects.forEach { effect ->
      // 合并判断条件，减少分支嵌套
      if (effect.activeLayers.contains(layer) && effect is aEP_BaseCombatEffect
        && !effect.renderInShader) {

        effect.render(layer, viewport)

      }
    }
  }

  /**
   * 获取所有激活的渲染层（本插件接管所有层）
   * @return 所有战斗引擎渲染层
   */
  override fun getActiveLayers(): EnumSet<CombatEngineLayers> = layers

  /**
   * 插件清理：释放资源，避免内存泄漏
   */
  override fun cleanup() {
    Global.getLogger(javaClass).info("$LOG_TAG Clean up...")
    shouldEnd = true
    // 主动移除引擎中的插件引用，避免内存泄漏
    engine?.run {
      removePlugin(this@aEP_CombatEffectPlugin)
      customData.remove(CUSTOM_DATA_KEY)
    }
    // 清空所有特效
    for( activeEffect in activeEffects){
      activeEffect.cleanup()
    }
    for( pendingAddEffect in activeEffects){
      pendingAddEffect.cleanup()
    }
    activeEffects.clear()
    pendingAddEffects.clear()
    // reset全部的renderUtils
    for ( r in renderUtils.values) {
      r.reset()
    }
    Global.getLogger(javaClass).info("$LOG_TAG Clean up...Done")
  }

  /**
   * 检查插件是否过期
   * @return true=过期，false=仍有效
   */
  override fun isExpired(): Boolean {
    if (shouldEnd) {
      Global.getLogger(javaClass).info("$LOG_TAG is now Expired")
    }
    return shouldEnd
  }


}