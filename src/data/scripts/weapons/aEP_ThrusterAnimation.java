package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class aEP_ThrusterAnimation extends MagicVectorThruster
{

  private static float maxFlameState = 30;// 0 == non flame, control flame length
  private ShipEngineControllerAPI.ShipEngineAPI e;
  private float amount = 0f;
  private float ORIGINAL_HEIGHT = 80f;
  private float ORIGINAL_WIDTH = 10f;

  public boolean enable = false;

  @Override
  public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
    if(enable){
      super.advance(amount,engine,weapon);
    }
  }

}

        	         	       	       	 
