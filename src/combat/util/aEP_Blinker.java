package combat.util;

import combat.impl.aEP_BaseCombatEffect;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicAnim;

public class aEP_Blinker extends aEP_BaseCombatEffect
{
  float blinkLevel = 1f;
  float blinkSpeed = 1f;
  boolean blinkUp = false;

  public aEP_Blinker(float blinkSpeed, float lifeTime) {
    this.blinkSpeed = blinkSpeed;
    setLifeTime(lifeTime);
  }

  @Override
  public void advance(float amount) {
    super.advance(amount);
    if (blinkUp && blinkLevel >= 1f) blinkUp = false;
    else if (!blinkUp && blinkLevel <= 0f) blinkUp = true;

    if (blinkUp) blinkLevel = blinkLevel + blinkSpeed * amount;
    else blinkLevel = blinkLevel - blinkSpeed * amount;

    blinkLevel = MathUtils.clamp(blinkLevel, 0, 1);
    super.advanceImpl(amount);
  }


  public float getBlinkLevel() {
    return MagicAnim.smooth(blinkLevel);
  }

  public void setBlinkSpeed(float blinkSpeed) {
    this.blinkSpeed = blinkSpeed;
  }
}