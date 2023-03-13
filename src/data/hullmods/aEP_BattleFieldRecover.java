package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class aEP_BattleFieldRecover extends BaseHullMod
{
  @Override
  public void advanceInCombat(ShipAPI ship, float amount) {

  }

  @Override
  public boolean shouldAddDescriptionToTooltip(ShipAPI.HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
    return true;
  }

  @Override
  public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
    tooltip.addButton("New", "aEP_BFR_build", 120, 20, 20f);

  }


}
