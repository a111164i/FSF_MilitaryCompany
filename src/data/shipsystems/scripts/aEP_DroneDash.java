package data.shipsystems.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import combat.plugin.aEP_BuffEffect;
import combat.impl.aEP_Buff;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class aEP_DroneDash extends BaseShipSystemScript
{
  static final float MAX_SPEED_BONUS = 200f;
  static final float TURN_RATE_BONUS = 250f;
  static final float ROTATE_SPEED = 20f;

  static final float END_BUFF_TIME = 1f;
  static final float END_TURN_RATE_BONUS = 300f;
  static final float END_TURN_ACC_BONUS = 50f;

  static final float DAMAGE_TAKEN = 0.25f;

  @Override
  public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    ShipAPI ship = (ShipAPI) stats.getEntity();
    if (ship == null) return;

    float amount = Global.getCombatEngine().getElapsedInLastFrame() * stats.getTimeMult().getModifiedValue();

    stats.getMaxSpeed().modifyFlat(id, Math.max(effectLevel, 0.5f) * MAX_SPEED_BONUS);
    stats.getAcceleration().modifyFlat(id, MAX_SPEED_BONUS * 2f);
    stats.getDeceleration().modifyMult(id, 0);

    stats.getEngineDamageTakenMult().modifyMult(id, DAMAGE_TAKEN);
    stats.getArmorDamageTakenMult().modifyMult(id,DAMAGE_TAKEN);
    stats.getHullDamageTakenMult().modifyMult(id,DAMAGE_TAKEN);

    ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
    ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
    ship.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT);
    ship.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT);
    ship.giveCommand(ShipCommand.ACCELERATE, null, 0);

    Vector2f angleAndSpeed = aEP_Tool.Util.velocity2Speed(ship.getVelocity());
    angleAndSpeed.y += ship.getAcceleration() * amount;
    ship.getVelocity().set(aEP_Tool.Util.speed2Velocity(angleAndSpeed.x, angleAndSpeed.y));


    //下面只在完全激活时跑一次
    if (effectLevel < 1) return;
    CombatEngineAPI engine = Global.getCombatEngine();
    //速度归0
    ship.getVelocity().set(new Vector2f(0, 0));
    //加烟
    int num = 16;
    for (int i = 0; i < num; i++) {
      engine.addNebulaSmokeParticle(MathUtils.getRandomPointInCircle(ship.getLocation(), ship.getCollisionRadius()),
        aEP_Tool.Util.speed2Velocity(ship.getFacing() - 180f, MathUtils.getRandomNumberInRange(50, 100)),

        MathUtils.getRandomNumberInRange(20, 60),
        2f,
        0.25f,
        0.5f,
        1,
        new Color(255, 250, 250, 125));
    }
    //加残影
    Vector2f vel = aEP_Tool.Util.speed2Velocity(ship.getFacing(), -100);
    ship.addAfterimage(new Color(255, 155, 155, 250),
      0, 0,
      vel.x, vel.y,
      5,
      0,
      0.5f,
      0.5f,
      true,
      false,
      true);
    vel = aEP_Tool.Util.speed2Velocity(ship.getFacing(), -200);
    ship.addAfterimage(new Color(255, 155, 155, 250),
      0, 0,
      vel.x, vel.y,
      5,
      0,
      0.5f,
      0.5f,
      true,
      false,
      true);
    vel = aEP_Tool.Util.speed2Velocity(ship.getFacing(), -300);
    ship.addAfterimage(new Color(255, 155, 155, 250),
      0, 0,
      vel.x, vel.y,
      5,
      0,
      0.5f,
      0.5f,
      true,
      false,
      true);
    MissileAPI m = null;

  }

  @Override
  public void unapply(MutableShipStatsAPI stats, String id) {
    stats.getMaxSpeed().unmodify(id);
    stats.getAcceleration().unmodify(id);
    stats.getDeceleration().unmodify(id);
    stats.getEngineDamageTakenMult().unmodify(id);
    stats.getArmorDamageTakenMult().unmodify(id);
    stats.getHullDamageTakenMult().unmodify(id);
    ShipAPI ship = (ShipAPI) stats.getEntity();
    if (ship != null)
      aEP_BuffEffect.addThisBuff(ship, new aEP_ExtraTurnRate(ship));
  }

  class aEP_ExtraTurnRate extends aEP_Buff
  {
    public aEP_ExtraTurnRate(ShipAPI ship) {
      setStackNum(1f);
      setMaxStack(1f);
      setEntity(ship);
      setRenew(true);
      setLifeTime(END_BUFF_TIME);
      setBuffType("aEP_ExtraTurnRate");
    }

    @Override
    public void play() {
      ShipAPI ship = (ShipAPI) getEntity();
      ship.getMutableStats().getMaxTurnRate().modifyPercent(getBuffType(), END_TURN_RATE_BONUS);
      ship.getMutableStats().getTurnAcceleration().modifyPercent(getBuffType(), END_TURN_RATE_BONUS * 2);
      ship.getMutableStats().getAcceleration().modifyPercent(getBuffType(), END_TURN_ACC_BONUS);
      ship.getMutableStats().getDeceleration().modifyPercent(getBuffType(), END_TURN_ACC_BONUS);
      ship.setJitterUnder(getBuffType(), new Color(255, 155, 155, 255), 1, 18, 1);

    }

    @Override
    public void readyToEnd() {
      ShipAPI ship = (ShipAPI) getEntity();
      ship.getMutableStats().getMaxTurnRate().unmodify(getBuffType());
      ship.getMutableStats().getTurnAcceleration().unmodify(getBuffType());
      ship.getMutableStats().getAcceleration().unmodify(getBuffType());
      ship.getMutableStats().getDeceleration().unmodify(getBuffType());
    }
  }
}
