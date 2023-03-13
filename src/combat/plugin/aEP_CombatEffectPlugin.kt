package combat.plugin

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.input.InputEventAPI
import combat.impl.aEP_BaseCombatEffect
import combat.util.aEP_Tool
import java.util.*
/**
 * 自由特效类，实现自定义特效渲染
 * */
class aEP_CombatEffectPlugin :BaseEveryFrameCombatPlugin, CombatLayeredRenderingPlugin{

  var amount = 0f
  var layers: EnumSet<CombatEngineLayers> = EnumSet.allOf(CombatEngineLayers::class.java)
  var shouldEnd = false
  lateinit var engine: CombatEngineAPI
  private val effects: LinkedList<aEP_BaseCombatEffect> = LinkedList()
  private val newEffects: LinkedList<aEP_BaseCombatEffect> = LinkedList()

  constructor(){

  }

  /**
   * EveryFrameCombatPlugin的方法
   * 本类在setting里面注册过，而且继承了EveryFrameCombatPlugin
   * 每次在combat开始时，游戏本体自动调用本方法并加入engine运行的EveryFrameCombatPlugin们
   * 这也是实现EveryFrameCombatPlugin的唯一作用
   * */
  override fun init( engine:CombatEngineAPI) {
    this.engine = engine
    //把自己也放入engin的CombatLayeredRenderingPlugin之中
    engine.addLayeredRenderingPlugin(this)
    //将自己的引用放入 customData
    engine.customData["aEP_CombatRenderPlugin"] = this
    //清理列表，每次战斗结束挂起时总会剩很多没结束的effect
    effects.clear()
    Global.getLogger(this.javaClass).info("aEP_CombatEffectPlugin register in EveryFrameCombatPlugin")
  }

  /**
   * CombatLayeredRenderingPlugin的方法
   * 注意本类不会自动加入engine的CombatLayeredRenderingPlugin中
   * 在EveryFrameCombatPlugin的init中放进去，放进去的时候会被运行一次
   * */
  override fun init(entity: CombatEntityAPI?) {
    //以防万一
    (engine?: Global.getCombatEngine())
    Global.getLogger(this.javaClass).info("aEP_CombatEffectPlugin register in CombatLayeredRenderingPlugin")
  }

  /**
   * CombatLayeredRenderingPlugin的advance方法
   * 和render分开
   * 注意一下暂停时，advance并不调用，render会继续每帧调用
   * 请务必把逻辑和渲染分开写！！
   */
  override fun advance(amount: Float) {
    //以防万一
    (engine?: Global.getCombatEngine())?: return
    if(engine.isPaused) this.amount = 0f else this.amount = amount

    /*
     * newEffects用于储存本帧新生成的 effects
     * 避免effect生成新effect导致遍历过程中动态修改报错
     * 对应VE是不起效的，所以避免VE中生成新的 VE
     */
    //把上一帧生成的新effect加进本帧接下来要运行的effect列表
    for (e in newEffects) {
      effects.add(e)
    }
    newEffects.clear()


    val toRemove: MutableList<aEP_BaseCombatEffect> = ArrayList()
    for (e in effects) {
      //如果isExpire，本帧不再运行advance，直接送去移除
      if(e.isExpired){
        toRemove.add(e)
        continue
      }
      //这里运行被aEP_BaseCombatEffect重载的advance方法
      //如果运行后，或触发某个条件后会自动设置isExpire
      e.advance(amount)
    }
    effects.removeAll(toRemove)
  }


  /**
   * 设置这个为最大值，本类负责渲染整个战场全部的aEP自定义特效
   * 在特效自己aEP_BaseCombatEffect类的render方法中判定这个特效要不要渲染
   * */
  override fun getRenderRadius(): Float {
    return 99999999f
  }

  /**
   * 逐一渲染列表中的全部自定义特效
   */
  override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
    layer?:return
    viewport?:return
    for (e in effects) {
      if(!e.activeLayers.contains(layer)) continue
      if(e is aEP_BaseCombatEffect && e.renderInShader) continue
      e.render(layer, viewport)
    }
  }

  /**
   * 本方法指明每一层都要运行一次render方法
   * 要不要在该层渲染点东西在render里面做判定
   */
  override fun getActiveLayers(): EnumSet<CombatEngineLayers> {
    return layers
  }

  override fun cleanup() {
    aEP_Tool.addDebugLog("aEP_CombatEffectPlugin clear up")
    shouldEnd = true
  }

  override fun isExpired(): Boolean {
    if(shouldEnd) aEP_Tool.addDebugLog("aEP_CombatEffectPlugin expired")
    return shouldEnd
  }

  /**
   * 静态函数部分
   */
  companion object Mod{
    fun addEffect(e: aEP_BaseCombatEffect){
      val c = Global.getCombatEngine().customData["aEP_CombatRenderPlugin"] as aEP_CombatEffectPlugin?
      c?.newEffects?.add(e) ?:Global.getLogger(this.javaClass).info(this.javaClass.name+" failed adding to aEO_CombatEffect")
      //Global.getLogger(this.javaClass).info(this.javaClass.name+": added to effect list")
    }
  }

}
