package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import combat.util.aEP_Tool;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class aEP_SimpleMissileShipAI implements ShipAIPlugin
{
  ShipAPI ship;
  ShipAPI target;
  Vector2f toTargetPo;
  float holdFireTime = 0f;
  ShipwideAIFlags flags = new ShipwideAIFlags();
  ShipAIConfig config = new ShipAIConfig();
  float textTimer;


  public aEP_SimpleMissileShipAI(ShipAPI ship) {
    this.ship = ship;
  }

  @Override
  public void setDoNotFireDelay(float amount) {
    holdFireTime = amount;
  }

  @Override
  public void forceCircumstanceEvaluation() {
    target = null;
  }


  @Override
  public void advance(float amount) {
    if (target == null || !Global.getCombatEngine().isInPlay(target) || !target.isAlive()) {
      toTargetPo = aEP_Tool.getExtendedLocationFromPoint(ship.getLocation(), ship.getFacing(), 100f);
      for(ShipAPI s : Global.getCombatEngine().getShips()){
        if(ship.getOwner() != 1) continue;
        if(target == null) target = s;
        if(s.getHullSize().ordinal() > target.getHullSize().ordinal()){
          target = s;
        }
      }
      textTimer = textTimer + amount;
      if (textTimer > 4f) {
        textTimer = 0;
        Global.getCombatEngine().addFloatingText(ship.getLocation(), "Target Searching", 10f, Color.white, ship, 1, 0.4f);
      }

    }
    else {
      toTargetPo = target.getLocation();
      textTimer = textTimer + amount;
      if (textTimer > 4f) {
        textTimer = 0;
        Global.getCombatEngine().addFloatingText(ship.getLocation(), "Target Locked", 10f, Color.red, ship, 1, 0.4f);
        Global.getCombatEngine().addFloatingText(target.getLocation(), "Target Locked", 10f, Color.red, target, 1, 0.4f);
      }
    }
    aEP_Tool.moveToPosition(ship, toTargetPo);
  }


  @Override
  public boolean needsRefit() {
    return false;
  }

  @Override
  public ShipwideAIFlags getAIFlags() {
    return flags;
  }

  @Override
  public void cancelCurrentManeuver() {

  }

  @Override
  public ShipAIConfig getConfig() {
    return config;
  }


  public void setTarget(ShipAPI newTarget) {
    this.target = newTarget;
  }
}
