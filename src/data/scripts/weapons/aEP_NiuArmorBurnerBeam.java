package data.scripts.weapons;

import com.fs.starfarer.api.FactoryAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.*;
import combat.util.aEP_Tool;
import org.lwjgl.util.vector.Vector2f;

public class aEP_NiuArmorBurnerBeam implements BeamEffectPlugin
{
  static final float PURE_DAMAGE_PERCENT = 0.5f;


  @Override
  public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
    //每0.1s触发一次didDamageThisFrame()
    if (beam.getSource() == null || !beam.didDamageThisFrame() || beam.getDamageTarget() == null
    || !(beam.getDamageTarget() instanceof ShipAPI)) return;
    ShipAPI ship = beam.getSource();
    ShipAPI target = (ShipAPI) beam.getDamageTarget();
    float pureDamageToArmor = PURE_DAMAGE_PERCENT;

  }

  //每当一道BeamAPI击中敌人，给敌人施加一个伤害监听器
  //光束0.1s结算一次，所有每次增加监听器0.1s的生命，BeamAPI不再持续伤害敌人时，监听器自动结束
  //只要来自本道光束的伤害在上一帧产生了结构伤害
  //敌人减免来自该BeamAPI的伤害
  class aEP_ArmorBurnerBeamDamageListener implements DamageListener, AdvanceableListener, DamageTakenModifier, DamageDealtModifier
  {
    ShipAPI ship;
    BeamAPI source;
    float time = 1f;

    public aEP_ArmorBurnerBeamDamageListener(BeamAPI source,ShipAPI ship) {
      this.source = source;
      this.ship = ship;
      //不能这样，原因不明，会无法ship.removeListener()
      //this.ship = source.getSource();
    }

    @Override
    public void advance(float amount) {
      time -= amount;
      if (time < 0) ship.removeListener(this);
    }

    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {

      return null;
    }

    //先于实际施加伤害，对于同一帧的伤害，先全部过一遍这个修改函数，再逐一施加于船体上
    //param是造成伤害的发射物，projectileAPI，missileAPI，beamAPI等
    //进行装甲格移除也会进监听器
    //return修改项的id
    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
      if(param == null) return null;
      if(target instanceof ShipAPI && param == source){
        //getBaseDamage()是每秒伤害，不是每dot伤害
        //aEP_Tool.Util.addDebugLog("did");
        damage.getModifier().modifyMult("aEP_ArmorBurnerBeamDamageListener",1f-PURE_DAMAGE_PERCENT);
      }
      return "aEP_ArmorBurnerBeamDamageListener";
    }


    //source 是伤害源头，ShipAPI, projectileAPI，missileAPI，beamAPI等
    //每遭受一次伤害，先 modifyDamageTaken才会 report
    //发生在伤害已经施加在船体之后
    @Override
    public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {

    }



  }
}

