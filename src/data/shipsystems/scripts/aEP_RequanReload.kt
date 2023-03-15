package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import combat.plugin.aEP_CombatEffectPlugin;
import combat.util.aEP_Tool;

public class aEP_RequanReload extends BaseShipSystemScript
{
  boolean didVisual = false;
  private static final float COOLDOWN_REDUCE = 5f;

  @Override
  public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    ShipAPI ship = (ShipAPI) stats.getEntity();
    if (ship == null) return;

    //使用的一瞬间启用特效
    if(!didVisual){
      aEP_CombatEffectPlugin.Mod.addEffect(new aEP_NCReloadScript.RefresherOrb(ship));
      didVisual = true;
    }

    //effectLevel == 1f 时运行一次
    if (effectLevel < 1f) return;
    for (WeaponAPI w : ship.getAllWeapons()) {
      //少量减少内置导弹cd
      if (w.getSlot().getWeaponType() != WeaponAPI.WeaponType.BUILT_IN) {
        if(aEP_Tool.Util.isNormalWeaponType(w,true) && w.getSpec().getType() == WeaponAPI.WeaponType.MISSILE){
          w.setRemainingCooldownTo(w.getCooldownRemaining()-Math.min(COOLDOWN_REDUCE, w.getCooldownRemaining()));
        }
      }else { //回复系统导弹
        w.beginSelectionFlash();
        w.getAmmoTracker().setAmmo(Math.min(w.getAmmo() + w.getSpec().getBurstSize(), w.getMaxAmmo()));
      }
    }

  }

  @Override
  public void unapply(MutableShipStatsAPI stats, String id) {
    didVisual = false;
  }
}
