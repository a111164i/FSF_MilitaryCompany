//by a111164
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;


public class aEP_DefDroneAI extends aEP_BaseShipAI {

  private ShipAPI d;

  public aEP_DefDroneAI(FleetMemberAPI member, ShipAPI ship) {
    super(ship);
  }

  @Override
  public void advanceImpl(float amount) {

  }
}
  
   
      
   

