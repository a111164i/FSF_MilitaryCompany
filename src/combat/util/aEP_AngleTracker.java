package combat.util;

import org.lazywizard.lazylib.MathUtils;

import static java.lang.Math.abs;

public class aEP_AngleTracker {
  float curr;
  float to;
  float speed;
  float max;
  float min;


  public aEP_AngleTracker(float curr, float to, float speed, float max, float min) {
    this.curr = curr;
    this.to = to;
    this.speed = speed;
    this.max = max;
    this.min = min;
  }

  public void advance(float amount) {
    float toMove;
    if (curr > to) toMove = -Math.min(curr - to, speed * amount);
    else toMove = Math.min(to - curr, speed * amount);
    curr += toMove * (0.5f * (1f - abs(curr)/(max+1f)) + 0.5f);
    curr = MathUtils.clamp(curr, min, max);

  }

  public float getCurr() {
    return curr;
  }

  public void setCurr(float curr) {
    this.curr = curr;
  }

  public float getTo() {
    return to;
  }

  public void setTo(float to) {
    this.to = to;
  }

  public float getSpeed() {
    return speed;
  }

  public void setSpeed(float speed) {
    this.speed = speed;
  }

  public float getMax() {
    return max;
  }

  public void setMax(float max){ this.max = max;}

  public void setMin(float min) {this.min = min;}

  public float getMin() {
    return min;
  }

  public boolean isInPosition() {
    boolean inPosition = abs(curr - to) < 0.01f;
    return inPosition;
  }

  public void randomizeTo() {
    to = MathUtils.getRandom().nextFloat() * (max - min) + min;
  }
}
