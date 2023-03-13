package combat.util;

import org.lazywizard.lazylib.MathUtils;

import java.awt.*;

public class aEP_ColorTracker
{
  float toR;
  float toG;
  float toB;
  float toA;
  float time = 1;
  float nowR;
  float nowG;
  float nowB;
  float nowA;

  public aEP_ColorTracker(float nowR, float nowG, float nowB, float nowA) {
    this.nowR = nowR;
    this.nowG = nowG;
    this.nowB = nowB;
    this.nowA = nowA;
  }

  public aEP_ColorTracker(Color input) {
    this.nowR = input.getRed();
    this.nowG = input.getGreen();
    this.nowB = input.getBlue();
    this.nowA = input.getAlpha();
  }

  public aEP_ColorTracker(float toR, float toG, float toB, float toA, float time, Color input) {
    this.time = time;
    this.nowR = input.getRed();
    this.nowG = input.getGreen();
    this.nowB = input.getBlue();
    this.nowA = input.getAlpha();
    this.toR = (toR - nowR) / time;
    this.toG = (toG - nowG) / time;
    this.toB = (toB - nowB) / time;
    this.toA = (toA - nowA) / time;
  }

  public Color advance(float amount) {
    nowR += toR * amount;
    nowG += toG * amount;
    nowB += toB * amount;
    nowA += toA * amount;
    nowR = MathUtils.clamp(nowR, 0, 255);
    nowG = MathUtils.clamp(nowG, 0, 255);
    nowB = MathUtils.clamp(nowB, 0, 255);
    nowA = MathUtils.clamp(nowA, 0, 255);
    time = MathUtils.clamp(time - amount, 0.001f, 99f);
    return getColorNow();
  }

  public Color getColorNow() {
    return new Color((int) nowR, (int) nowG, (int) nowB, (int) nowA);
  }

  public void setToColor(float r, float g, float b, float a, float time) {
    this.toR = (r - nowR) / time;
    this.toG = (g - nowG) / time;
    this.toB = (b - nowB) / time;
    this.toA = (a - nowA) / time;
  }

  public void setColor(float r, float g, float b, float a) {
    this.nowR = r;
    this.nowG = g;
    this.nowB = b;
    this.nowA = a;
  }

  public float getRed() {
    return nowR;
  }

  public float getGreen() {
    return nowG;
  }

  public float getBlue() {
    return nowB;
  }

  public float getAlpha() {
    return nowA;
  }
}
