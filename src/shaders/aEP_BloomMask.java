package shaders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import combat.impl.aEP_BaseCombatEffect;
import org.dark.shaders.util.ShaderLib;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import static org.lwjgl.opengl.GL11.*;

/**
 * 加入shaderPlugin的同时，也要加入 combatEffectPlugin，这样才会调用 advance方法
 * 加入shaderPlugin前，应该打开 renderInShader的开关，不再在 combatEffectPlugin中渲染
 */
public class aEP_BloomMask extends aEP_BaseCombatEffect
{

  public aEP_BloomMask() {
    setLifeTime(3f);
  }


  /**
   * 把大地图绝对坐标转换为窗口相对坐标
   * */
  public static Vector2f convertPositionToScreen(Vector2f absolutePosition) {
    ViewportAPI v = Global.getCombatEngine().getViewport();
    float vMult = v.getViewMult();
    Vector2f C = v.getCenter();
    float H = v.getVisibleHeight();
    float W = v.getVisibleWidth();
    float spriteX = ShaderLib.getInternalWidth() * vMult;
    float spriteY = ShaderLib.getInternalHeight() * vMult;
    Vector2f LL = new Vector2f(C.x - W / 2, C.y - H / 2);
    float x = (absolutePosition.x - LL.x) / spriteX;
    float y = (absolutePosition.y - LL.y) / spriteY;
    return new Vector2f(x, y);
  }

  /**
   * 使用draw，不要用这个
   * */
  @Override
  public void render(@Nullable CombatEngineLayers layer, @Nullable ViewportAPI viewport) {
    int screenTex = ShaderLib.getForegroundTexture(Global.getCombatEngine().getViewport());
    GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTex);
    aEP_BloomShader.setLevelX(0);
    aEP_BloomShader.setLevelY(0);
    aEP_BloomShader.setLevelAlpha(1f);
    draw();
    GL11.glPopAttrib();
  }

  /**
   * begin开头end结尾，只负责画区域，其他已经设定好了
   */
  public void draw() {
    /*
    glBegin(GL_QUADS);
    ShipAPI ship = Global.getCombatEngine().getPlayerShip();
    ViewportAPI v = Global.getCombatEngine().getViewport();
    float vMult = v.getViewMult();
    Vector2f C = v.getCenter();
    float H = v.getVisibleHeight();
    float W = v.getVisibleWidth();
    float spriteX = ShaderLib.getInternalWidth() * vMult;
    float spriteY = ShaderLib.getInternalHeight() * vMult;
    Vector2f LL = new Vector2f(C.x-W/2f,C.y-H/2f);
    Vector2f LR = new Vector2f(C.x+W/2f,C.y-H/2f);
    Vector2f UL = new Vector2f(C.x-W/2f,C.y+H/2f);
    Vector2f UR = new Vector2f(C.x+W/2f,C.y+H/2f);


    Vector2f texP1 = convertPositionToScreen(LL);
    Vector2f texP2 = convertPositionToScreen(LR);
    Vector2f texP3 = convertPositionToScreen(UL);
    Vector2f texP4 = convertPositionToScreen(UR);



    GL11.glTexCoord2f(0, 0);
    GL11.glVertex2f(LL.getX(), LL.getY());
    GL11.glTexCoord2f(texP2.x,0);
    GL11.glVertex2f(LR.getX(), LR.getY());
    GL11.glTexCoord2f(texP4.x,texP4.y );
    GL11.glVertex2f(UR.getX(), UR.getY());
    GL11.glTexCoord2f(0,texP3.y );
    GL11.glVertex2f(UL.getX(),UL.getY());
    glEnd();
     */
  }

  public static class aEP_BloomMaskSF extends aEP_BaseCombatEffect {
    public aEP_BloomMaskSF() {
      setLifeTime(10f);
    }

    @Override
    public void advance(float amount) {
      super.advance(amount);
      ShaderLib.getForegroundTexture(Global.getCombatEngine().getViewport());
      int screenTex = ShaderLib.getForegroundTexture(Global.getCombatEngine().getViewport());
      GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTex);
      aEP_BloomShader.setLevelX(30 * (1 - getTime() / getLifeTime()));
      aEP_BloomShader.setLevelY(2 * (1 - getTime() / getLifeTime()));
      draw();
      GL11.glPopAttrib();
      super.advanceImpl(amount);
      setTime(11f);
    }



    /**
     * begin开头end结尾，只负责画区域，其他已经设定好了
     */
    public void draw() {

    }

  }
}

