package data.scripts.campaign.items;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.hullmods.aEP_CruiseMissileCarrier;
import data.scripts.campaign.entity.aEP_CruiseMissileEntityPlugin;
import data.scripts.campaign.intel.aEP_CruiseMissileLoadIntel;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static combat.util.aEP_DataTool.txt;


public class aEP_CruiseMissile extends BaseSpecialItemPlugin
{
  protected List<String> tags = new ArrayList<String>();
  CargoAPI cargo;

  public static CustomCampaignEntityAPI createMissile(SectorEntityToken token, float angle) {
    CustomCampaignEntityAPI missile = token.getContainingLocation().addCustomEntity(null,
      null,
      aEP_CruiseMissileCarrier.CAMPAIGN_ENTITY_ID,
      token.getFaction().getId());
    missile.setFacing(angle);
    missile.setContainingLocation(token.getContainingLocation());
    missile.setLocation(token.getLocation().x, token.getLocation().y);
    aEP_CruiseMissileEntityPlugin plugin = (aEP_CruiseMissileEntityPlugin) missile.getCustomPlugin();
    plugin.setVariantId(aEP_CruiseMissileCarrier.SHIP_VARIANT_ID);
    return missile;
  }

  @Override
  public void init(CargoStackAPI stack) {
    super.init(stack);
    cargo = stack.getCargo();
    String param = spec.getParams();
    if (!param.isEmpty()) {
      for (String tag : param.split(",")) {
        tag = tag.trim();
        if (tag.isEmpty()) continue;
        tags.add(tag);
      }
    }

  }

  /**
   * 右键后自动消耗一个，如果使用失败记得手动返还一个
   * */
  @Override
  public void performRightClickAction() {
    boolean useOne = false;
    if (cargo != null && cargo.getFleetData() != null && cargo.getFleetData().getFleet() != null) {
      for(FleetMemberAPI m : Global.getSector().getPlayerFleet().getMembersWithFightersCopy()){
        if(aEP_CruiseMissileLoadIntel.Companion.getLoadedAmount(m.getId()) >= 1f && aEP_CruiseMissileLoadIntel.Companion.getLoadedItemId(m.getId()).equals(this.itemId)){
          aEP_CruiseMissileLoadIntel.Companion.getLOADING_MAP().keySet().remove(m.getId());
          useOne = true;
          break;
        }
      }
    }

    //改过了，现在不需要消耗从仓库里的，在装填时已经扣了一个
    //任何情况都补一个回来
    Global.getSector().getPlayerFleet().getCargo().addSpecial(new SpecialItemData(this.itemId, null), 1);

    if (!useOne) {
      //Global.getSector().getPlayerFleet().getCargo().addSpecial(new SpecialItemData(aEP_CruiseMissileCarrier.SPECIAL_ITEM_ID, null), 1);
      return;
    }

    aEP_CruiseMissileLoadIntel.Companion.createMissileEntityFromPlayer(itemId);
  }

  @Override
  public boolean hasRightClickAction() {
    return Global.getSector().getCampaignUI().getCurrentCoreTab() == CoreUITabId.CARGO;
  }

  @Override
  public boolean shouldRemoveOnRightClickAction() {
    return true;
  }

  @Override
  public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) {
    float opad = 10f;

    tooltip.addTitle(getName());

    String design = getDesignType();
    Misc.addDesignTypePara(tooltip, design, opad);

    if (!spec.getDesc().isEmpty()) {
      Color c = Misc.getTextColor();
      tooltip.addPara(spec.getDesc(), c, opad);
    }
    Color c = Misc.getTextColor();
    if (useGray) c = Misc.getGrayColor();
    tooltip.addPara(txt("CruiseMissileItem01"), c, opad);

    int loadedNum = 0;
    if (cargo != null && cargo.getFleetData() != null && cargo.getFleetData().getFleet() != null) {
      for(FleetMemberAPI m : Global.getSector().getPlayerFleet().getMembersWithFightersCopy()){
        if(aEP_CruiseMissileLoadIntel.Companion.getLoadedAmount(m.getId()) >= 1f && aEP_CruiseMissileLoadIntel.Companion.getLoadedItemId(m.getId()).equals(this.itemId)){
          loadedNum += 1;
        }
      }
    }

    if (loadedNum < 1)
      tooltip.addPara(txt("CruiseMissileItem02"), Color.red, opad);
    else
      tooltip.addPara(txt("CruiseMissileItem03"), opad, Color.white, Color.yellow, loadedNum + "");


    if (Global.getSector().getCampaignUI().getCurrentCoreTab() != CoreUITabId.CARGO)
      tooltip.addPara(txt("CruiseMissileItem04"), Color.red, opad);

  }


}

