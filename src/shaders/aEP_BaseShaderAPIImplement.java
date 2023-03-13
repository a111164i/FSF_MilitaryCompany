package shaders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import combat.impl.aEP_BaseCombatEffect;
import org.dark.shaders.util.ShaderAPI;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.List;

public class aEP_BaseShaderAPIImplement implements ShaderAPI
{
  public static int program = 0;
  public String shaderId = "aEP_BaseShaderAPIImplement";
  boolean isEnabled = true;
  boolean isCombat = true;
  CombatEngineLayers layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
  RenderOrder order = RenderOrder.OBJECT_SPACE;

  public aEP_BaseShaderAPIImplement(String id){
    this.shaderId = id;
  }

  @Override
  public void renderInWorldCoords(ViewportAPI viewportAPI) {

  }

  @Override
  public void renderInScreenCoords(ViewportAPI viewportAPI) {

  }


  @Override
  public void advance(float amount, List<InputEventAPI> list) {

  }

  @Override
  public void initCombat() {

  }

  @Override
  public void destroy() {
    ByteBuffer countbb;
    ByteBuffer shadersbb;
    IntBuffer count;
    IntBuffer shaders;
    int i;
    if (program != 0) {
      countbb = ByteBuffer.allocateDirect(4);
      shadersbb = ByteBuffer.allocateDirect(8);
      count = countbb.asIntBuffer();
      shaders = shadersbb.asIntBuffer();
      GL20.glGetAttachedShaders(program, count, shaders);
      for (i = 0; i < 2; ++i) {
        GL20.glDeleteShader(shaders.get());
      }

      GL20.glDeleteProgram(program);
    }
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  public void setEnabled(boolean enabled) {
    isEnabled = enabled;
  }

  @Override
  public CombatEngineLayers getCombatLayer() {
    return layer;
  }

  @Override
  public boolean isCombat() {
    return isCombat;
  }

  public void setCombat(boolean combat) {
    isCombat = combat;
  }

  @Override
  public RenderOrder getRenderOrder() {
    return order;
  }

  public void setOrder(RenderOrder order) {
    this.order = order;
  }

  public void setLayer(CombatEngineLayers layer) {
    this.layer = layer;
  }

  public void setProgram(int program) {
    aEP_BaseShaderAPIImplement.program = program;
  }

  public List<aEP_BaseCombatEffect> getRenderList(){
    if(!Global.getCombatEngine().getCustomData().containsKey(shaderId)) {
      List<aEP_BaseCombatEffect> renderList = new LinkedList<aEP_BaseCombatEffect>();
      Global.getCombatEngine().getCustomData().put(shaderId,renderList);
      return renderList;
    }
    return (List<aEP_BaseCombatEffect>)Global.getCombatEngine().getCustomData().get(shaderId);
  }
}
