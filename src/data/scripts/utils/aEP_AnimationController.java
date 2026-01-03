package data.scripts.utils;


import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

import java.util.HashMap;
import java.util.Map;

public class aEP_AnimationController
{
  //range,speed(by percent)
  static final Map<String, Float[]> mag = new HashMap<>();

  static {
    //变化速度，一秒钟能播放几遍
    mag.put("aEP_des_yangji_flak", new Float[]{1f});
  }

  public float effectiveLevel = 0;
  float toLevel = 0;
  //播放速度，一秒能循环几遍
  float speed = 1;
  AnimationAPI animation;
  WeaponAPI weapon;

  public aEP_AnimationController(WeaponAPI weapon, float frameRate) {
    this.weapon = weapon;
    this.animation = weapon.getAnimation();
    if (mag.equals(weapon.getSpec().getWeaponId())) this.speed = frameRate / animation.getNumFrames();
  }

  public void advance(float amount) {
    //glow贴图也存在2帧，为了防止和glow冲突，animation必须在mag中声明才能使用
    if(!mag.containsKey(weapon.getSpec().getWeaponId())) return;
    if (speed <= 0) return;
    //aEP_Tool.addDebugText("1");
    float toMove;
    if (effectiveLevel > toLevel) {
      toMove = -Math.min(effectiveLevel - toLevel, speed * amount);
    }
    else {
      toMove = Math.min(toLevel - effectiveLevel, speed * amount);
    }
    effectiveLevel = effectiveLevel + toMove;
    animation.setFrame((int) (animation.getNumFrames() * effectiveLevel - 0.001f));
  }

  public void setToLevel(float toLevel) {
    this.toLevel = toLevel;
  }

  public void setSpeed(float frameRate) {
    this.speed = frameRate / animation.getNumFrames();
  }
}
