package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.BaseCampaignObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.econ.CommRelayCondition;
import com.fs.starfarer.api.impl.campaign.intel.misc.CommSnifferIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class aEP_FSFRelayPlugin extends BaseCampaignObjectivePlugin
{
  @Override
  public void init(SectorEntityToken entity, Object pluginParams) {
    super.init(entity, pluginParams);
    readResolve();
  }

  Object readResolve() {
    return this;
  }

  @Override
  public void advance(float amount) {
    if ((this.entity.getContainingLocation() == null) || (this.entity.isInHyperspace())) {
      return;
    }
    if (this.entity.getMemoryWithoutUpdate().getBoolean("$objectiveNonFunctional")) {
      return;
    }
    for (MarketAPI market : Misc.getMarketsInLocation(this.entity.getContainingLocation())) {
      CommRelayCondition mc = CommRelayCondition.get(market);
      if (mc == null) {
        market.addCondition("comm_relay");
        mc = CommRelayCondition.get(market);
      }
      if (mc != null) {
        mc.getRelays().add(this.entity);
      }
    }
    checkIntelFromCommSniffer();
  }

  protected boolean isMakeshift() {
    return this.entity.hasTag("makeshift");
  }

  @Override
  public void printNonFunctionalAndHackDescription(TextPanelAPI text) {
    if (this.entity.getMemoryWithoutUpdate().getBoolean("$objectiveNonFunctional")) {
      text.addPara("各种信号显示这个中继器并没有在工作，考虑到中继器和FSF公司的距离，是大胆狂徒？还是一场全面攻势？");
    }
    if (isHacked().booleanValue()) {
      text.addPara("已经有一个通讯嗅探器在中继器上");
    }
  }

  @Override
  public void printEffect(TooltipMakerAPI text, float pad) {
    int bonus = Math.abs(Math.round(
      CommRelayCondition.COMM_RELAY_BONUS));
    if (isMakeshift()) {
      bonus = Math.abs(Math.round(
        CommRelayCondition.MAKESHIFT_COMM_RELAY_BONUS));
    }
    text.addPara("      %s该势力星系内殖民地的稳定性",
      pad, Misc.getHighlightColor(), "+" + bonus);
  }

  @Override
  public void addHackStatusToTooltip(TooltipMakerAPI text, float pad) {
    int bonus = Math.abs(Math.round(
      CommRelayCondition.COMM_RELAY_BONUS));
    if (isMakeshift()) {
      bonus = Math.abs(Math.round(
        CommRelayCondition.MAKESHIFT_COMM_RELAY_BONUS));
    }
    if (isHacked().booleanValue()) {
      text.addPara("%s星系内殖民地的稳定性",
        pad, Misc.getHighlightColor(), "+" + bonus);
      text.addPara("你已经安装了一个通讯嗅探器", Misc.getTextColor(), pad);
    }
    else {
      text.addPara("%s该势力星系内殖民地的稳定性",
        pad, Misc.getHighlightColor(), "+" + bonus);
    }
  }

  @Override
  public void setHacked(boolean hacked) {
    if (hacked) {
      setHacked(hacked, -1.0F);
      boolean found = CommSnifferIntel.getExistingSnifferIntelForRelay(this.entity) != null;
      if (!found) {
        CommSnifferIntel intel = new CommSnifferIntel(this.entity);
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (dialog != null) {
          Global.getSector().getIntelManager().addIntelToTextPanel(intel, dialog.getTextPanel());
        }
      }
    }
    else {
      setHacked(hacked, -1.0F);
    }
  }

  private void checkIntelFromCommSniffer() {
    if (!isHacked().booleanValue()) {
      return;
    }
    boolean playerInRelayRange = Global.getSector().getIntelManager().isPlayerInRangeOfCommRelay();
    for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getCommQueue()) {
      if ((intel instanceof CommSnifferReadableIntel)) {
        CommSnifferReadableIntel csi = (CommSnifferReadableIntel) intel;
        if (csi.canMakeVisibleToCommSniffer(playerInRelayRange, this.entity)) {
          intel.setForceAddNextFrame(true);
        }
      }
    }
  }

  public interface CommSnifferReadableIntel
  {
    boolean canMakeVisibleToCommSniffer(boolean paramBoolean, SectorEntityToken paramSectorEntityToken);
  }
}
