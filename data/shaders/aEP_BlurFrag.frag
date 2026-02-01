// 声明GLSL版本为140（桌面版规范，对应OpenGL 3.1，替代原130版本）
#version 140

// ========== 全局uniform变量（CPU端传入，控制模糊/衰减参数） ==========
uniform vec2 screen;          // 屏幕/纹理尺寸（宽、高）
uniform float scale;          // 缩放系数（用于归一化模糊偏移量）
uniform float levelX;         // 水平模糊基础强度
uniform float levelY;         // 垂直模糊基础强度
uniform float levelAlpha;     // 最终透明度系数
uniform sampler2D tex;        // 输入纹理采样器（待模糊的纹理）

// 屏幕边缘柔和参数：控制屏幕四周边界的模糊衰减，任一≤0则关闭该功能
uniform float edgeFalloffRange = 0; // 屏幕边缘衰减范围（>0有效，≤0关闭）
uniform float edgeSmoothness = 0.1;   // 屏幕边缘过渡平滑度（>0有效，≤0关闭，0~0.2推荐）

// 圆形模糊+独立边缘柔和参数（圆心可在纹理空间外，GLSL端做防护）
uniform vec2 circleCenter;    // 圆心坐标（纹理空间，0-1，可超出）
uniform float circleRadius;   // 圆半径（纹理空间，0-1）
uniform float circleEdgeSoftness = 0.02; // 圆形边缘专属柔和参数（纹理空间0~0.1）

// ========== 片段着色器输入（替代原varying/gl_TexCoord，140语法） ==========
in vec2 vTexCoord;            // 插值后纹理坐标（纹理空间0-1）

// ========== 片段着色器输出（140推荐显式声明，替代gl_FragColor） ==========
out vec4 fragColor;

void main()
{
    vec2 coord = vTexCoord;   // 纹理空间坐标（0-1），所有距离计算的基准
    vec4 finalColor = vec4(0.0);
    float edgeAttenuation = 1.0; // 初始化屏幕衰减系数为1.0（关闭时的默认值）

    // ========== 步骤1：屏幕边缘柔和衰减系数 ==========
    // 新增：参数有效性检测，两个参数同时>0才启用屏幕边缘柔和，任一≤0则跳过
    if (edgeFalloffRange > 0.0 && edgeSmoothness > 0.0)
    {
        float distToLeft = coord.x;
        float distToRight = 1.0 - coord.x;
        float distToTop = coord.y;
        float distToBottom = 1.0 - coord.y;
        float minEdgeDist = min(min(distToLeft, distToRight), min(distToTop, distToBottom));

        edgeAttenuation = smoothstep(
        edgeFalloffRange - edgeSmoothness,
        edgeFalloffRange + edgeSmoothness,
        minEdgeDist
        );
        edgeAttenuation = clamp(edgeAttenuation, 0.0, 1.0);
    }
    // 未进入if则edgeAttenuation保持1.0，屏幕边缘柔和功能完全失效

    // ========== 步骤2：圆形边缘柔和衰减系数 ==========
    // 使用平方距离避免开方（更快），仍保持与真实距离相同的单调性
    vec2 toCenter = coord - circleCenter; // 允许圆心超出[0,1]
    float distSqToCircleCenter = dot(toCenter, toCenter); // (dx^2 + dy^2)

    // 计算圆心到边缘的两个关键半径：内侧半径与外侧“柔和过渡”终点
    float circleCoreEnd = circleRadius; // 内侧半径，<=该值时衰减趋近1
    float circleEdgeEnd = circleRadius + circleEdgeSoftness; // 外侧过渡终点
    circleEdgeEnd = min(circleEdgeEnd, 1.0); // 防止超过纹理空间范围

    // 用平方半径参与smoothstep，避免开方但效果一致
    float circleCoreEndSq = circleCoreEnd * circleCoreEnd;
    float circleEdgeEndSq = circleEdgeEnd * circleEdgeEnd;

    // smoothstep: 当距离在[edgeEnd, coreEnd]之间时，从0平滑过渡到1
    // 近圆心(<=coreEnd)得到1，远离圆心(>=edgeEnd)得到0
    float circleAttenuation = smoothstep(
        circleEdgeEndSq,   // 衰减结束（外侧）
        circleCoreEndSq,   // 衰减起始（内侧）
        distSqToCircleCenter
    );
    circleAttenuation = clamp(circleAttenuation, 0.0, 1.0);

    // ========== 步骤3：合并双层衰减系数，缩放模糊偏移量 ==========
    float combinedAttenuation = max(edgeAttenuation, circleAttenuation);
    float pixelDistanceX = levelX / (scale * screen.x) * combinedAttenuation;
    float pixelDistanceY = levelY / (scale * screen.y) * combinedAttenuation;

    // ========== 步骤4：5-tap高斯模糊逻辑 ==========
    const float kernel[5] = {0.1260, 0.2334, 0.2812, 0.2334, 0.1260};
    const float kernelSum = 1.0;
    const int halfSize = 2;

    vec4 horizontalColor = vec4(0.0);
    for(int x = -halfSize; x <= halfSize; x++)
    {
        float offsetX = float(x) * pixelDistanceX;
        horizontalColor += texture(tex, coord + vec2(offsetX, 0.0)) * kernel[x + halfSize];
    }

    vec4 verticalColor = vec4(0.0);
    for(int y = -halfSize; y <= halfSize; y++)
    {
        float offsetY = float(y) * pixelDistanceY;
        verticalColor += texture(tex, coord + vec2(0.0, offsetY)) * kernel[y + halfSize];
    }

    // ========== 步骤5：合并模糊结果，保证透明） ==========
    finalColor = (horizontalColor + verticalColor) / 2.0;
    finalColor.a *= levelAlpha * circleAttenuation; // 圆外/纹理外完全透明

    // 输出最终颜色：屏幕柔和可关闭，圆形模糊/边缘柔和不受影响
    fragColor = finalColor;
}