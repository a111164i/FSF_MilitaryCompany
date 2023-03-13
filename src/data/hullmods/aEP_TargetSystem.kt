package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static combat.util.aEP_DataTool.txt;


public class aEP_TargetSystem extends BaseHullMod
{

  private static final Map<HullSize, Float> BONUS = new HashMap();
  private static final Map<HullSize, Float> PUNISH = new HashMap();

  static {
    BONUS.put(HullSize.FIGHTER, 0f);
    BONUS.put(HullSize.FRIGATE, 20f);
    BONUS.put(HullSize.DESTROYER, 15f);
    BONUS.put(HullSize.CRUISER, 10f);
    BONUS.put(HullSize.CAPITAL_SHIP, 5f);
  }

  static {
    PUNISH.put(HullSize.FIGHTER, 0f);
    PUNISH.put(HullSize.FRIGATE, 0.2f);
    PUNISH.put(HullSize.DESTROYER, 0.15f);
    PUNISH.put(HullSize.CRUISER, 0.10f);
    PUNISH.put(HullSize.CAPITAL_SHIP, 0.05f);
  }

  String id = "aEP_TargetSystem";

  @Override
  public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
    MutableShipStatsAPI stats = ship.getMutableStats();
    ShipAPI.HullSize hullSize = ship.getHullSize();

    stats.getBallisticWeaponRangeBonus().modifyPercent(id, BONUS.get(hullSize));
    stats.getEnergyWeaponRangeBonus().modifyPercent(id, BONUS.get(hullSize));

    if (ship.getShield() == null || ship.getShield().getType() == ShieldAPI.ShieldType.NONE) return;

    float flatMod = PUNISH.get(hullSize);
    flatMod = flatMod * 1 / MathUtils.clamp(ship.getShield().getFluxPerPointOfDamage(), 0.1f, 10f);
    stats.getShieldAbsorptionMult().modifyFlat(id, flatMod);
    this.id = id;
  }

  @Override
  public String getDescriptionParam(int index, HullSize hullSize, ShipAPI ship) {
    if (index == 0) return String.format("%.0f", BONUS.get(HullSize.FRIGATE));
    if (index == 1) return String.format("%.0f", BONUS.get(HullSize.DESTROYER));
    if (index == 2) return String.format("%.0f", BONUS.get(HullSize.CRUISER));
    if (index == 3) return String.format("%.0f", BONUS.get(HullSize.CAPITAL_SHIP));
    return "";
  }

  @Override
  public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
    return  true;
  }

  @Override
  public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f);
    //tooltip.addGrid( 5 * 5f + 10f);
    tooltip.addPara("- " + txt("weapon_range_up") + "{%s}", 5f, Color.white, Color.green, String.format("%.0f", BONUS.get(hullSize)) + "%");
    tooltip.addPara("- " + txt("shield_absorb_down") + "{%s}", 5f, Color.white, Color.red, String.format("%.2f", PUNISH.get(hullSize)));
  }

  @Override
  public boolean isApplicableToShip(ShipAPI ship) {
    return ship.getHullSpec().getManufacturer().equals(txt("manufacturer"));
  }

  @Override
  public String getUnapplicableReason(ShipAPI ship) {
    if (!ship.getHullSpec().getManufacturer().equals(txt("manufacturer"))) return txt("only_on_FSF");
    return "";
  }

}
