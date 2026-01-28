package data.scripts.utils

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.ViewportAPI
import org.dark.shaders.util.ShaderLib
import org.lwjgl.opengl.GL20
import java.nio.ByteBuffer

/**
 * 基于aEP_BaseCombatEffect的着色器渲染基础类
 * 
 * 核心职责：
 * 1. 继承aEP_BaseCombatEffect的生命周期管理
 * 2. 封装着色器初始化、渲染和清理逻辑
 * 3. 通过静态变量共享shader程序，减少重复初始化开销
 * 
 * 使用方式：
 * 1. 继承此类
 * 2. 提供shader文件路径（通过getShaderPaths方法）
 * 3. 实现renderShader方法，设置uniform参数并渲染
 * 4. 可选：重写其他方法自定义行为
 */
abstract class aEP_BaseCombatEffectWithShader : aEP_BaseCombatEffect() {

    companion object {
        // 静态变量：存储已加载的shader程序
        // Key: shaderId, Value: program
        private val shaderPrograms = mutableMapOf<String, Int>()

        /**
         * 获取或创建shader程序
         * @param shaderId shader的唯一标识
         * @param vertShaderPath 顶点着色器文件路径
         * @param fragShaderPath 片段着色器文件路径
         * @return shader程序ID，如果加载失败返回0
         */
        fun getOrCreateProgram(shaderId: String, vertShaderPath: String, fragShaderPath: String): Int {
            // 如果已经加载过，直接返回
            if (shaderPrograms.containsKey(shaderId)) {
                return shaderPrograms[shaderId]!!
            }

            // 加载新的shader程序
            return try {
                val vertShader = Global.getSettings().loadText(vertShaderPath)
                val fragShader = Global.getSettings().loadText(fragShaderPath)
                val program = ShaderLib.loadShader(vertShader, fragShader)

                if (program > 0) {
                    shaderPrograms[shaderId] = program
                    Global.getLogger(aEP_BaseCombatEffectWithShader::class.java)
                        .info("Shader loaded: $shaderId")
                } else {
                    Global.getLogger(aEP_BaseCombatEffectWithShader::class.java)
                        .warn("Failed to load shader: $shaderId")
                }

                program
            } catch (e: Exception) {
                Global.getLogger(aEP_BaseCombatEffectWithShader::class.java)
                    .error("Error loading shader: $shaderId", e)
                0
            }
        }

        private fun deleteProgramWithShaders(program: Int) {
            if (program <= 0) return
            val countbb = ByteBuffer.allocateDirect(4)
            val shadersbb = ByteBuffer.allocateDirect(8)
            val count = countbb.asIntBuffer()
            val shaders = shadersbb.asIntBuffer()
            GL20.glGetAttachedShaders(program, count, shaders)
            val attached = count.get(0).coerceAtMost(shaders.capacity())
            for (i in 0 until attached) {
                val shader = shaders.get(i)
                if (shader > 0) {
                    GL20.glDeleteShader(shader)
                }
            }
            GL20.glDeleteProgram(program)
        }

        /**
         * 清理所有shader程序
         * 通常在游戏退出或mod卸载时调用
         */
        fun cleanupAllShaders() {
            shaderPrograms.forEach { (_, program) ->
                deleteProgramWithShaders(program)
            }
            shaderPrograms.clear()
            Global.getLogger(aEP_BaseCombatEffectWithShader::class.java)
                .info("All shader programs cleaned up")
        }

        /**
         * 清理指定shader程序
         * 通常在你明确不再需要该shader时调用
         */
        fun cleanupShader(shaderId: String) {
            val program = shaderPrograms.remove(shaderId) ?: return
            deleteProgramWithShaders(program)
            Global.getLogger(aEP_BaseCombatEffectWithShader::class.java)
                .info("Shader program cleaned up: $shaderId")
        }
    }

    // 实例变量
    protected var shaderId: String = ""
    protected var program: Int = 0
    protected var isShaderEnabled: Boolean = true
    protected var vertShaderPath: String = ""
    protected var fragShaderPath: String = ""

    /**
     * 绑定shader到当前实例，并使用默认路径规则
     * 调用时机：子类初始化完成后
     */
    fun setShader(id: String) {
        shaderId = id
        vertShaderPath = "data/shaders/${id}Vert.vert"
        fragShaderPath = "data/shaders/${id}Frag.frag"
    }

    /**
     * 获取shader的唯一标识
     * 用于缓存和识别不同的shader程序
     */
    open fun getShaderId(): String = shaderId

    /**
     * 获取shader文件路径
     * @return Pair(顶点着色器路径, 片段着色器路径)
     */
    open fun getShaderPaths(): Pair<String, String> = Pair(vertShaderPath, fragShaderPath)

    /**
     * 初始化shader
     * 在构造函数或首次使用时调用
     */
    protected fun initShader() {
        if (shaderId.isBlank()) return // shader未绑定，不初始化
        if (program > 0) return // 已经初始化过

        shaderId = getShaderId()
        val (vertPath, fragPath) = getShaderPaths()
        program = getOrCreateProgram(shaderId, vertPath, fragPath)
    }

    /**
     * 检查shader是否已加载
     */
    protected fun isShaderLoaded(): Boolean {
        return program > 0
    }

    /**
     * 启用/禁用shader
     */
    fun setShaderEnabled(enabled: Boolean) {
        isShaderEnabled = enabled
    }

    /**
     * 获取shader程序ID
     */
    fun getProgram(): Int = program

    /**
     * 重写render方法，封装shader渲染流程
     */
    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        //里面有是否初次初始化的检测，每帧调用即可
        initShader()
        // 如果shader未启用或未加载，直接返回
        if (!isShaderEnabled || !isShaderLoaded()) {
            return
        }

        // 检查是否在当前层
        if (!layers.contains(layer)) return

        // 更新位置
        val center = loc
        if (entity != null) {
            center.set(entity!!.location)
        }

        // 检查是否在视口范围内
        val screenDist = radius * 1f
        if (!viewport.isNearViewport(center, screenDist)) {
            return
        }

        // 设置renderInShader标志，避免重复渲染
        renderInShader = true

        try {
            // 使用shader程序
            GL20.glUseProgram(program)

            // 调用renderImpl方法
            renderImpl(layer, viewport)

            // 恢复固定管线
            GL20.glUseProgram(0)
        } catch (e: Exception) {
            Global.getLogger(this::class.java)
                .error("Error rendering shader: $shaderId", e)
        } finally {
            // 确保恢复固定管线
            GL20.glUseProgram(0)
        }
    }

    /**
     * 子类应该重写renderImpl方法，子类在此方法中实现具体的shader渲染逻辑
     */
    override fun renderImpl(layer: CombatEngineLayers, viewport: ViewportAPI) {
        // 子类实现具体的shader渲染逻辑
    }

    /**
     * 清理资源
     * 注意：不删除shader程序，因为它是静态共享的
     */
    override fun readyToEnd() {
        // 子类可以重写此方法来清理特定资源
        // 注意：不要在这里删除shader程序，因为它是静态共享的
    }

    /**
     * 便捷方法：设置float类型的uniform变量
     */
    protected fun setUniformFloat(name: String, value: Float) {
        val location = GL20.glGetUniformLocation(program, name)
        if (location >= 0) {
            GL20.glUniform1f(location, value)
        }
    }

    /**
     * 便捷方法：设置vec2类型的uniform变量
     */
    protected fun setUniformVec2(name: String, x: Float, y: Float) {
        val location = GL20.glGetUniformLocation(program, name)
        if (location >= 0) {
            GL20.glUniform2f(location, x, y)
        }
    }

    /**
     * 便捷方法：设置vec3类型的uniform变量
     */
    protected fun setUniformVec3(name: String, x: Float, y: Float, z: Float) {
        val location = GL20.glGetUniformLocation(program, name)
        if (location >= 0) {
            GL20.glUniform3f(location, x, y, z)
        }
    }

    /**
     * 便捷方法：设置vec4类型的uniform变量
     */
    protected fun setUniformVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        val location = GL20.glGetUniformLocation(program, name)
        if (location >= 0) {
            GL20.glUniform4f(location, x, y, z, w)
        }
    }

    /**
     * 便捷方法：设置int类型的uniform变量
     */
    protected fun setUniformInt(name: String, value: Int) {
        val location = GL20.glGetUniformLocation(program, name)
        if (location >= 0) {
            GL20.glUniform1i(location, value)
        }
    }
}
