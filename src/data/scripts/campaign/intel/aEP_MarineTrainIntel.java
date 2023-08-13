package data.scripts.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import combat.util.aEP_ID;
import org.lazywizard.lazylib.campaign.CampaignUtils;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

import static combat.util.aEP_DataTool.txt;

public class aEP_MarineTrainIntel extends BaseIntelPlugin {

  public static String TERMINATE_BUTTON_ID = "aEP_MarineTrainIntel_terminate";
  public static float REFUND_PRICE = 45f;

  public boolean shouldEnd = false;
  public boolean isEnded = false;
  public float trainNumber;
  SectorAPI sector;
  FactionAPI faction;
  float passedTimeByDays = 0f;
  float relationRequire = 0f;
  //training time and amount
  float trainTime;


  public aEP_MarineTrainIntel(float trainTime, float trainNumber) {
    sector = Global.getSector();
    this.trainTime = trainTime;
    this.trainNumber = trainNumber;
    this.faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF);
    Global.getLogger(this.getClass()).info(this.getClass().getName() +" initialized");

    setImportant(true);
    Global.getSector().getIntelManager().addIntel(this);
  }

  @Override
  public void advance(float amount) {
    //只有关系为正才会推动进度
    if(faction.getRelToPlayer().getRel() >= relationRequire){
      passedTimeByDays = passedTimeByDays + sector.getClock().convertToDays(amount);
    }

    if (passedTimeByDays >= trainTime ) {
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


  //控制右侧细长条信息栏
  @Override
  public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
    FactionAPI faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF);
    Color highLight = Misc.getHighlightColor();
    Color grayColor = Misc.getGrayColor();
    Color txtColor = Misc.getTextColor();
    Color barBgColor = faction.getDarkUIColor();
    Color factionColor = faction.getBaseUIColor();
    Color titleTextColor = faction.getColor();

    //船插描述里为5f，intel的小段为10f大段为30f
    info.addSectionHeading(txt("mission_info"), titleTextColor, barBgColor, Alignment.MID, 30f);
    info.addImages(250f, 90f, 10f, 10f,
            Global.getSettings().getSpriteName("aEP_illustrations","marine01"),
            faction.getFactionSpec().getLogo());


    //已经完成直接从这出去，不显示后续内容
    if (shouldEnd) {
      info.addPara(txt("MarineTrain03"), 10f, highLight, (int) (trainNumber) + "");
      return;
    }

    //显示剩余时间
    info.addPara(txt("MarineTrain04"), 10f, highLight, (int) (trainNumber) + "", (int) (trainTime - passedTimeByDays) + "");

    //关系不够，显示训练暂停，人员被扣押
    if(faction.getRelToPlayer().getRel() < relationRequire){
      info.addPara(txt("MarineTrain05"), 10f, highLight, (int)(relationRequire*100f)+"");
    }

    //单面终止合同的按钮
    info.addButton(
            txt("mission_terminate"), TERMINATE_BUTTON_ID,
            txtColor,barBgColor,Alignment.MID, CutStyle.ALL,
            120, 20, 30f);
  }

  @Override
  public boolean doesButtonHaveConfirmDialog(Object buttonId) {
    if(buttonId.equals(TERMINATE_BUTTON_ID)) return true;
    return false;
  }

  @Override
  public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
    if(buttonId.equals(TERMINATE_BUTTON_ID)) {
      prompt.addPara(String.format(txt("MarineTrain06"),(int)(trainNumber * REFUND_PRICE)),5f);
    }
  }


  @Override
  public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
    if(buttonId.equals(TERMINATE_BUTTON_ID)){
      Global.getSector().getPlayerFleet().getCargo().getCredits().add(trainNumber * REFUND_PRICE);

      Global.getSector().getIntelManager().removeIntel(this);
      Global.getSector().removeScript(this);

      ui.recreateIntelUI();
    }
  }

  //control when to end
  @Override
  public boolean shouldRemoveIntel() {
    return isEnded;
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



  @Override
  public String getIcon() {
    return Global.getSettings().getSpriteName("aEP_icons", "train_marine");
  }

  //control tags
  @Override
  public Set<String> getIntelTags(SectorMapAPI map) {
    Set<String> tags = new LinkedHashSet();
    tags.add("aEP_FSF");
    tags.add("Missions");
    return tags;
  }


  @Override
  public String getSortString() {
    return "FSF";
  }

}
