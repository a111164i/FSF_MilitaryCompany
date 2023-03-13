package data.scripts.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

import static combat.util.aEP_DataTool.txt;

public class aEP_MarineTrainIntel extends BaseIntelPlugin
{

  public boolean shouldEnd = false;
  public boolean isEnded = false;
  public float trainNumber;
  SectorAPI sector;
  FactionAPI faction;
  float passedTimeByDays;
  //training time and amount
  float trainTime;


  public aEP_MarineTrainIntel(float trainTime, float trainNumber) {
    sector = Global.getSector();
    passedTimeByDays = 0f;
    this.trainTime = trainTime;
    this.trainNumber = trainNumber;
    this.faction = Global.getSector().getFaction("aEP_FSF");
    Global.getLogger(this.getClass()).info("你一些船员送去参加训练营了");
    Global.getSector().getIntelManager().queueIntel(this);
    shouldEnd = false;
  }

  @Override
  public void advance(float amount) {
    passedTimeByDays = passedTimeByDays + sector.getClock().convertToDays(amount);
    if (passedTimeByDays > trainTime) {
      shouldEnd = true;
      return;
    }

  }


  //this part control brief bar on left
  @Override
  public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
    Color h = Misc.getHighlightColor();
    Color g = Misc.getGrayColor();
    Color c = this.getTitleColor(mode);

    info.setParaFontDefault();
    info.addPara(txt("MarineTrain_title"), c, 3f);
    info.setBulletedListMode(BULLET);
    //this is title
    if (shouldEnd) {
      info.addPara(txt("MarineTrain01"), g, 10f);
    }
    else {
      info.addPara(txt("MarineTrain02"), g, 10f);
    }
  }


  //this control info part on right
  @Override
  public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
    Color h = Misc.getHighlightColor();
    Color g = Misc.getGrayColor();
    Color c = faction.getBaseUIColor();

    float opad = 10f;
    //已经完成直接从这出去
    if (shouldEnd) {
      info.addPara(txt("MarineTrain03"), opad, h, (int) (trainNumber) + "");
      return;
    }
    info.addPara(txt("MarineTrain04"), opad, h, (int) (trainNumber) + "", (int) (trainTime - passedTimeByDays) + "");

  }

  //control when to end
  @Override
  public boolean shouldRemoveIntel() {
    return isEnded;
  }

  //control tags
  @Override
  public Set<String> getIntelTags(SectorMapAPI map) {
    Set<String> tags = new LinkedHashSet();
    tags.add("Important");
    tags.add("aEP_FSF");
    return tags;
  }

  @Override
  public String getSortString() {
    return "FSF";
  }

  @Override
  public boolean runWhilePaused() {
    return false;
  }

  @Override
  public boolean isEnding() {
    return shouldEnd;
  }

  @Override
  public boolean isEnded() {
    return isEnded;
  }

  public void setEnded(boolean ended) {
    isEnded = ended;
  }

}
