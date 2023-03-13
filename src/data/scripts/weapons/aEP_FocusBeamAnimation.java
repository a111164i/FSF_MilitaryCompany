package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

public class aEP_FocusBeamAnimation implements EveryFrameWeaponEffectPlugin
{
  static final float ROTATE_SPEED = 5f;
  static final float SPREAD_ANGLE = 2.5f;
  static final float BARREL_DIST = 10f;
  float effectiveLevel = 0.5f;

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
    Vector2f barrelLoc1 = weapon.getFirePoint(1);
    float barrelFacing1 = VectorUtils.getAngle(barrelLoc1, aEP_Tool.getExtendedLocationFromPoint(weapon.getLocation(), weapon.getCurrAngle(), weapon.getRange()));
    float offset1 = MathUtils.getShortestRotation(weapon.getCurrAngle(), barrelFacing1);
    if (!weapon.isFiring()) {
      weapon.getSpec().getTurretAngleOffsets().set(0, -offset1);
      weapon.getSpec().getTurretAngleOffsets().set(1, offset1);
      weapon.getSpec().getHardpointAngleOffsets().set(0, -offset1);
      weapon.getSpec().getHardpointAngleOffsets().set(1, offset1);
      effectiveLevel = 0.5f;
      return;
    }
    effectiveLevel += ROTATE_SPEED * amount / SPREAD_ANGLE;
    while (effectiveLevel > 1f) effectiveLevel -= 1;
    float angleOffset = 0f;
    if (effectiveLevel < 0.5f) angleOffset = (-SPREAD_ANGLE + SPREAD_ANGLE * 2f * effectiveLevel * 2f);
    else angleOffset = (SPREAD_ANGLE - SPREAD_ANGLE * 2f * (effectiveLevel - 0.5f) * 2f);
    weapon.getSpec().getTurretAngleOffsets().set(0, angleOffset - offset1);
    weapon.getSpec().getTurretAngleOffsets().set(1, -angleOffset + offset1);
    weapon.getSpec().getHardpointAngleOffsets().set(0, angleOffset - offset1);
    weapon.getSpec().getHardpointAngleOffsets().set(1, -angleOffset + offset1);
  }
}
