package data.hullmods;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.WeaponOPCostModifier;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.ids.Stats.*;


public class aEP_MultiTurret extends BaseHullMod
{

  static final Map<WeaponAPI.WeaponSize, Integer> mag = new HashMap<>();
  static final Map<WeaponAPI.WeaponSize, Integer> mag2 = new HashMap<>();
  static final int SMALL_WEAPON_MOD = 1;
  static final int MEDIUM_WEAPON_MOD = 3;
  static final int LARGE_WEAPON_MOD = 8;

  static {
    mag.put(WeaponAPI.WeaponSize.SMALL, 1);
    mag.put(WeaponAPI.WeaponSize.MEDIUM, 2);
    mag.put(WeaponAPI.WeaponSize.LARGE, 3);
  }

  static {
    mag2.put(WeaponAPI.WeaponSize.SMALL, 1);
    mag2.put(WeaponAPI.WeaponSize.MEDIUM, 2);
    mag2.put(WeaponAPI.WeaponSize.LARGE, 3);
  }

  List<String> needBalanceWeapon = new ArrayList<>();

  @Override
  public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
    //stats.addListener(new opMod());
    stats.getDynamic().getMod(SMALL_BALLISTIC_MOD).modifyFlat(id, -SMALL_WEAPON_MOD);
    stats.getDynamic().getMod(MEDIUM_BALLISTIC_MOD).modifyFlat(id, -MEDIUM_WEAPON_MOD);
    stats.getDynamic().getMod(LARGE_BALLISTIC_MOD).modifyFlat(id, -LARGE_WEAPON_MOD);
    stats.getDynamic().getMod(SMALL_ENERGY_MOD).modifyFlat(id, -SMALL_WEAPON_MOD);
    stats.getDynamic().getMod(MEDIUM_ENERGY_MOD).modifyFlat(id, -MEDIUM_WEAPON_MOD);
    stats.getDynamic().getMod(LARGE_ENERGY_MOD).modifyFlat(id, -LARGE_WEAPON_MOD);
  }

  @Override
  public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
    /*
    if(!ship.getMutableStats().getListenerManager().hasListener(opMod.class)) ship.getMutableStats().addListener(new opMod());
    MutableCharacterStatsAPI personStats = null;
    if(ship.getCaptain() != null) personStats = ship.getCaptain().getStats();
    if (ship.getVariant().getUnusedOP(personStats) < 0)
    {
      while (ship.getVariant().getNumFluxCapacitors()>0 && ship.getVariant().getUnusedOP(personStats)<0) ship.getVariant().setNumFluxCapacitors(ship.getVariant().getNumFluxCapacitors()-1);
      while (ship.getVariant().getNumFluxVents()>0 && ship.getVariant().getUnusedOP(personStats)<0) ship.getVariant().setNumFluxVents(ship.getVariant().getNumFluxVents()-1);
      while (ship.getVariant().getUnusedOP(personStats)<0 && ship.getVariant().getFittedWeaponSlots().size()>0) ship.getVariant().clearSlot((String)ship.getVariant().getFittedWeaponSlots().toArray()[0]);
      while (ship.getVariant().getUnusedOP(personStats)<0 && ship.getVariant().getNonBuiltInHullmods().size()>0) ship.getVariant().removeMod((String) ship.getVariant().getNonBuiltInHullmods().toArray()[0]);
      if(ship.getVariant().getUnusedOP(personStats)<0) ship.getVariant().clear();
    }

     */
  }

  @Override
  public String getDescriptionParam(int index, HullSize hullSize) {
    if (index == 0) return "" + SMALL_WEAPON_MOD;
    if (index == 1) return "" + MEDIUM_WEAPON_MOD;
    if (index == 2) return "" + LARGE_WEAPON_MOD;
    return null;
  }

  /*
  @Override
  public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {return (needBalanceWeapon.size()>0? true :false);}
  @Override
  public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec)
  {
    tooltip.addSectionHeading(txt("compatible"), Alignment.MID, 10f);
    if(needBalanceWeapon.size()<=0) tooltip.addPara("- {%s}", 5f, Misc.getTextColor(), Misc.getHighlightColor(), txt("no"));
    Iterator iterator = needBalanceWeapon.iterator();
    while (iterator.hasNext()) tooltip.addPara("- "+txt("NeedToBalance")+"{%s}", 5f, Misc.getTextColor(), Misc.getHighlightColor(), Global.getSettings().getWeaponSpec((String) iterator.next()).getWeaponName());
  }

   */

  @Override
  public boolean affectsOPCosts() {
    return true;
  }


  class opMod implements WeaponOPCostModifier
  {
    int toReduce = 0;

    @Override
    public int getWeaponOPCost(MutableShipStatsAPI stats, WeaponSpecAPI weapon, int currCost) {
      needBalanceWeapon.clear();
      boolean hasDiffer = false;
      ShipVariantAPI variant = stats.getVariant();
      for (String slotId : stats.getVariant().getFittedWeaponSlots()) {
        if (slotId.startsWith("R0")) {
          if (!variant.getWeaponId(slotId).equals(variant.getWeaponId(slotId.replace("R0", "L0")))) {
            hasDiffer = true;
            needBalanceWeapon.add(variant.getWeaponId(slotId));
          }
        }
        if (slotId.startsWith("L0")) {
          if (!variant.getWeaponId(slotId).equals(variant.getWeaponId(slotId.replace("L0", "R0")))) {
            hasDiffer = true;
            needBalanceWeapon.add(variant.getWeaponId(slotId));
          }
        }
      }
      if (hasDiffer) return currCost + 1;
      return currCost;
    }
  }
}
