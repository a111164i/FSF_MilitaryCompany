//by a111164
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class aEP_FluxTubeAI implements AutofireAIPlugin
{

  private final ShipwideAIFlags flags = new ShipwideAIFlags();
  private final ShipAIConfig config = new ShipAIConfig();
  private final CombatEngineAPI engine;
  private final WeaponAPI weapon;
  private final ShipAPI ship;
  private CombatEntityAPI toTarget;
  private Vector2f toTargetPo;
  private String id;
  private SpriteAPI sprite;
  private float dist;
  private boolean shouldFire = false;
  private CombatEntityAPI target;

  public aEP_FluxTubeAI(WeaponAPI weapon) {
    this.ship = weapon.getShip();
    this.weapon = weapon;
    this.engine = Global.getCombatEngine();

  }

  public void forceOff() {
    return;
  }

  public Vector2f getTarget() {
    return toTargetPo;
  }

  public ShipAPI getTargetShip() {
    return null;
  }

  public MissileAPI getTargetMissile() {
    return null;
  }

  public WeaponAPI getWeapon() {
    return weapon;
  }

  public boolean shouldFire() {
    return shouldFire;
  }

  public void advance(float amount) {
    shouldFire = false;
    aEP_Tool.aimToAngle(weapon, weapon.getSlot().computeMidArcAngle(ship));
    if(ship.getFluxLevel() > 0.51f){
      shouldFire = true;
    }
  }

}  