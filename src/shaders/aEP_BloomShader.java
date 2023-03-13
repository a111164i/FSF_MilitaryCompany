package shaders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import combat.impl.aEP_BaseCombatEffect;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class aEP_BloomShader extends aEP_BaseShaderAPIImplement
{
  boolean active = true;
  //并不会每次战斗前都 init
  public aEP_BloomShader() {
    super("aEP_BloomShader");
    initCombat();
    Global.getLogger(this.getClass()).info( "aEP_BloomShader_init");
  }

  public static void add(aEP_BaseCombatEffect CVEWS) {
    aEP_BloomShader bloomShader = (aEP_BloomShader) ShaderLib.getShaderAPI(aEP_BloomShader.class);
    if (bloomShader == null) {
      bloomShader = new aEP_BloomShader();
      ShaderLib.addShaderAPI(bloomShader);
    }
    bloomShader.getRenderList().add(CVEWS);
  }

  public static void setLevelX(float levelX) {
    GL20.glUniform1f(GL20.glGetUniformLocation(program, "levelX"), levelX);
  }

  public static void setLevelY(float levelY) {
    GL20.glUniform1f(GL20.glGetUniformLocation(program, "levelY"), levelY);
  }

  public static void setLevelAlpha(float alpha) {
    GL20.glUniform1f(GL20.glGetUniformLocation(program, "levelAlpha"), alpha);
  }

  @Override
  public void initCombat() {
    //为渲染应用一个高斯模糊shader
    //默认卷积核为5x5
    //实现柔和边缘的效果
    String vertShader;
    String fragShader;
    try {
      vertShader = Global.getSettings().loadText("data/shaders/aEP_BlurVert.vert");
      fragShader = Global.getSettings().loadText("data/shaders/aEP_BlurFrag.frag");
      program = ShaderLib.loadShader(vertShader, fragShader);
      Global.getLogger(this.getClass()).info("Shader loaded");
    } catch (IOException ex) {
      Global.getLogger(this.getClass()).info("Shader not found");
    }
  }

  @Override
  public void renderInWorldCoords(ViewportAPI viewportAPI) {
    /*
    initCombat();
    if(program == 0)
    {
      aEP_Tool.addDebugText("fail");
      return;
    }
     */
    List<aEP_BaseCombatEffect> allRenders = getRenderList();
    if (!active && program == 0 && allRenders.size() > 0) {
      initCombat();
      active = true;
    }
    // aEP_Tool.addDebugText(Global.getCombatEngine().getViewport().getVisibleWidth()+"_"+Global.getCombatEngine().getViewport().getVisibleHeight());
    List toRemove = new ArrayList();
    //ShaderLib.beginDraw(program);
    //GL20.glLinkProgram(program);
    GL20.glUseProgram(program);
    //参数传入要在 useProgram之后
    ViewportAPI view = Global.getCombatEngine().getViewport();
    GL20.glUniform2f(GL20.glGetUniformLocation(program, "screen"), view.getVisibleWidth() / view.getViewMult(), view.getVisibleHeight() / view.getViewMult());
    GL20.glUniform1f(GL20.glGetUniformLocation(program, "levelX"), 1f);
    GL20.glUniform1f(GL20.glGetUniformLocation(program, "levelY"), 1f);
    GL20.glUniform1f(GL20.glGetUniformLocation(program, "scale"), view.getViewMult());
    //GL20.glUniform1i(GL20.glGetUniformLocation(program,"tex"),ShaderLib.getScreenTexture());
    //GL11.glEnable(GL_TEXTURE_2D);
    //GL11.glBindTexture(GL_TEXTURE_2D,ShaderLib.getScreenTexture());
    for (int i = 0; i < allRenders.size(); i++) {
      aEP_BaseCombatEffect cvf = allRenders.get(i);
      //因为这是个纯渲染器，不包含advance的部分，所以直接检测shouldEnd
      if (cvf.getShouldEnd()) {
        toRemove.add(allRenders.get(i));
        continue;
      }
      setLevelX(1f);
      setLevelY(1f);
      setLevelAlpha(1f);
      cvf.render(layer,view);
    }
    GL20.glUseProgram(0);
    //ShaderLib.exitDraw();
    allRenders.removeAll(toRemove);
    if (allRenders.size() <= 0) {
      destroy();
      program = 0;
      active = false;
    }
  }

  @Override
  public CombatEngineLayers getCombatLayer() {
    return CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
  }


}
