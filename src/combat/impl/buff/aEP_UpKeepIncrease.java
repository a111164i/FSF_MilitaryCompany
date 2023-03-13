package combat.impl.buff;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import combat.impl.aEP_Buff;
import combat.util.aEP_Blinker;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;

public class aEP_UpKeepIncrease extends aEP_Buff
{
  static final float MAX_PROBABILITY = 100f;
  static final float MIN_ARC_INTERNAL = 0.1f;
  static final String USE_TEXTURE = "aEP_FX.noise";


  SpriteAPI tex;
  float upKeepIncreasePerStack;
  float createArcTimer = 10f;
  aEP_Blinker B;


  public aEP_UpKeepIncrease(float lifeTime, ShipAPI ship, boolean isRenew, float maxStack, float upKeepIncreasePerStack, String id) {
    setLifeTime(lifeTime);
    setStackNum(1f);
    setEntity(ship);
    setRenew(isRenew);
    setMaxStack(maxStack);
    setBuffType(id);
    this.upKeepIncreasePerStack = upKeepIncreasePerStack;
    this.B = new aEP_Blinker(1f, lifeTime);
    String[] split = USE_TEXTURE.split("\\.");
    tex = Global.getSettings().getSprite(split[0], split[1]);
  }

  @Override
  public void play() {
    ShipAPI ship = (ShipAPI) getEntity();
    if (ship.getShield() == null || ship.getShield().getType() == ShieldAPI.ShieldType.NONE) {
      setShouldEnd(true);
      return;
    }
    //不用原版机制了
    //ship.getMutableStats().getShieldUpkeepMult().modifyFlat(buffType, upKeepIncreasePerStack * stackNum / baseUpKeep);
    ship.getFluxTracker().increaseFlux(upKeepIncreasePerStack * getStackNum() * 0.1f, false);
    createArcTimer = createArcTimer + 0.1f;
    if (createArcTimer > MIN_ARC_INTERNAL) {
      Color shieldColor = ship.getShield().getInnerColor();
      createArcTimer = 0f;
      if (MathUtils.getRandomNumberInRange(0, 100) < MAX_PROBABILITY * (getStackNum() / getMaxStack())) {
        Global.getCombatEngine().spawnEmpArcVisual(aEP_Tool.getExtendedLocationFromPoint(ship.getLocation(), MathUtils.getRandomNumberInRange(0, 360), ship.getShield().getRadius()),
          ship,
          MathUtils.getRandomPointInCircle(ship.getLocation(), ship.getShield().getRadius()),
          ship,
          MathUtils.getRandomNumberInRange(5, 20),
          new Color(50, 50, 200, 240),
          new Color(150, 150, 200, 200));
      }
    }
  }

  @Override
  public void readyToEnd() {
    ((ShipAPI) getEntity()).getMutableStats().getShieldUpkeepMult().unmodify(getBuffType());
  }

}
