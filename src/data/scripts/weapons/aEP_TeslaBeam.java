package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import combat.impl.aEP_BaseCombatEffect;
import combat.plugin.aEP_CombatEffectPlugin;
import org.lazywizard.lazylib.MathUtils;

public class aEP_TeslaBeam implements BeamEffectPlugin {
    static final float DAMAGE_PER_TICK = 20f;
    static final float EMP_PER_TICK = 100f;

    static final float MANEUVER_MULT = 0.5f;
    static final float MAX_SPEED_MULT = 0.5f;

    static final String ID = "aEP_TeslaBeam";
    IntervalUtil arcTracker = new IntervalUtil(0.2f,0.2f);

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        arcTracker.advance(amount);
        if(arcTracker.intervalElapsed()){
            //穿盾电弧的source必须不为null，检测到击中目标为shipAPI才进入穿盾电弧环节，否则只产生视觉emp
            if(beam.getDamageTarget() != null && beam.getDamageTarget() instanceof ShipAPI){
                ShipAPI target = (ShipAPI) beam.getDamageTarget();
                if(beam.getWeapon() != null && beam.getWeapon().getShip() != null){
                    Global.getCombatEngine().spawnEmpArcPierceShields(
                            beam.getWeapon().getShip(),
                            beam.getFrom(),
                            beam.getWeapon().getShip(),
                            target,
                            DamageType.ENERGY,
                            DAMAGE_PER_TICK,
                            EMP_PER_TICK,
                            target.getCollisionRadius()*2f+beam.getLength(),
                            "tachyon_lance_emp_impact",
                            MathUtils.getRandomNumberInRange(0f,8f)+8f,
                            beam.getFringeColor(),
                            beam.getCoreColor());
                    if(!target.getCustomData().containsKey(ID)){
                        target.setCustomData(ID,1f);
                        aEP_CombatEffectPlugin.Mod.addEffect(new SlowDown(target));
                    }
                }
            }else {
                if(beam.getWeapon() != null && beam.getWeapon().getShip() != null) {
                    Global.getCombatEngine().spawnEmpArcVisual(
                            beam.getFrom(),
                            beam.getWeapon().getShip(),
                            beam.getTo(),
                            beam.getWeapon().getShip(),
                            MathUtils.getRandomNumberInRange(0f,8f)+8f,
                            beam.getFringeColor(),
                            beam.getCoreColor());

                }
            }
        }
    }

    class SlowDown extends aEP_BaseCombatEffect{
        ShipAPI target;

        SlowDown(ShipAPI target){
            super(0.5f, target);
            this.target = target;

            target.getMutableStats().getAcceleration().modifyMult(ID,MANEUVER_MULT);
            target.getMutableStats().getDeceleration().modifyMult(ID,MANEUVER_MULT);
            target.getMutableStats().getMaxSpeed().modifyMult(ID,MAX_SPEED_MULT);

            target.getMutableStats().getTurnAcceleration().modifyMult(ID,MANEUVER_MULT);
            target.getMutableStats().getMaxTurnRate().modifyMult(ID,MAX_SPEED_MULT);

        }

        @Override
        public void readyToEnd() {
            target.getMutableStats().getAcceleration().unmodify(ID);
            target.getMutableStats().getDeceleration().unmodify(ID);
            target.getMutableStats().getMaxSpeed().unmodify(ID);

            target.getMutableStats().getTurnAcceleration().unmodify(ID);
            target.getMutableStats().getMaxTurnRate().unmodify(ID);

            target.removeCustomData(ID);
        }
    }
}
