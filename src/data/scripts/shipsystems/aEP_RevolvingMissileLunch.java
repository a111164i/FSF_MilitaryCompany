package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import combat.impl.aEP_BaseCombatEffect;
import combat.plugin.aEP_CombatEffectPlugin;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class aEP_RevolvingMissileLunch extends BaseShipSystemScript
{
  static final float FIRE_CONVERT_HARD_FLUX = 250f;

  static final float ARC_LIFE_TIME = 1.1f;
  static final float ARC_INTERVAL = 0.5f;

  boolean forceUse = false;
  boolean disablePermanent = false;

  IntervalUtil fireTimer = new IntervalUtil(0.2f, 0.2f);
  IntervalUtil particleTimer = new IntervalUtil(0.05f, 0.15f);

  @Override
  public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    //复制粘贴
    if(stats == null || stats.getEntity() == null || !(stats.getEntity() instanceof ShipAPI)) return;
    ShipAPI ship = (ShipAPI) stats.getEntity();

    //死了强行使用一次
    if (!ship.isAlive() && !disablePermanent) {
      disablePermanent = true;
      forceUse = true;
      ship.getSystem().setAmmo(1);
      ship.getSystem().setCooldownRemaining(0);
      ship.useSystem();
    }

    //播放动画
    for (WeaponAPI w : ship.getAllWeapons()) {
      if (w.getSpec().getWeaponId().equals("aEP_revo_missile")) {
        if (effectLevel > 0) w.getAnimation().play();
        else w.getAnimation().pause();
      }
    }

    //系统没激活从这出去
    if (effectLevel < 0.9f && !forceUse) return;

    //set jitter
    ship.setJitter(id, new Color(240, 100, 200, 10), effectLevel, 16, 5);
    ship.setJitterShields(false);


    float amount = aEP_Tool.getAmount(ship);
    fireTimer.advance(amount);
    particleTimer.advance(amount);

    //每隔fireTimer往下走
    if (!fireTimer.intervalElapsed()) return;
    for (WeaponAPI w : ship.getAllWeapons()) {
      if (w.getSpec().getWeaponId().equals("aEP_revo_missile")) {
        if (ship.getFluxTracker().getCurrFlux() < FIRE_CONVERT_HARD_FLUX) {
          ship.getSystem().deactivate();
          forceUse = false;
          return;
        }
        float angle = w.getCurrAngle() + MathUtils.getRandomNumberInRange(w.getCurrSpread() / 2f, -w.getCurrSpread() / 2f);
        Vector2f point = aEP_Tool.Util.getWeaponOffsetInAbsoluteCoo(w).get(0);
        Global.getCombatEngine().spawnMuzzleFlashOrSmoke(ship, w.getSlot(), w.getSpec(), 0, angle);
        MissileAPI missile = (MissileAPI) Global.getCombatEngine().spawnProjectile(
          ship,
          w,
          w.getId(),
          point,
          angle,
          ship.getVelocity());
        ship.getFluxTracker().increaseFlux(-FIRE_CONVERT_HARD_FLUX,true);
        aEP_CombatEffectPlugin.Mod.addEffect(new aEP_ARC_SPAWN(ship, missile));


        //spawn smooth particle if glow interval elapsed
        if (particleTimer.intervalElapsed()) {
          //add yellow around glow
          float range = MathUtils.getRandomNumberInRange(20f, 50f);
          Vector2f randPoint = MathUtils.getRandomPointOnCircumference(w.getLocation(), range);
          Global.getCombatEngine().addSmoothParticle(randPoint,
            aEP_Tool.Util.speed2Velocity(MathUtils.getRandomNumberInRange(10, range), VectorUtils.getAngle(randPoint, w.getLocation())),
            MathUtils.getRandomNumberInRange(2f, 5f),
            1f,
            1f,
            Color.blue);
        }
      }
    }
  }

  static class aEP_ARC_SPAWN extends aEP_BaseCombatEffect
  {
    float arcTimer;
    CombatEntityAPI ship;
    CombatEntityAPI missile;

    aEP_ARC_SPAWN(CombatEntityAPI ship, CombatEntityAPI missile) {
      this.arcTimer = ARC_INTERVAL + ARC_LIFE_TIME;
      this.ship = ship;
      this.missile = missile;
    }

    @Override
    public void advanceImpl(float amount) {
      arcTimer += amount;
      if (arcTimer < ARC_INTERVAL) return;
      arcTimer -= ARC_INTERVAL;
      Global.getCombatEngine().spawnEmpArcVisual(MathUtils.getRandomPointInCircle(ship.getLocation(), ship.getCollisionRadius()), ship, missile.getLocation(), missile, 6f, Color.magenta, Color.white);

      if(arcTimer > ARC_LIFE_TIME) cleanup();
    }

  }


}
