package data.scripts.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

import static combat.util.aEP_DataTool.txt;
import static data.scripts.world.aEP_systems.aEP_FSF_DWR43.FACTORY_STATION_MARKET_ID;

public class aEP_AWM4Intel extends aEP_BaseMission
{
  SectorEntityToken token; //实际隐藏跳跃点附近随机生成的位置，有一定距离
  MarketAPI market;

  public aEP_AWM4Intel() {
    super();

    market = Global.getSector().getEconomy().getMarket(FACTORY_STATION_MARKET_ID);
    if(market == null) readyToEnd = true;
    StarSystemAPI system = (StarSystemAPI)market.getContainingLocation() ;
    token = system.getHyperspaceAnchor();
    Vector2f aroundLoc = MathUtils.getRandomPointInCircle(token.getLocation(),1000f);
    token = Global.getSector().getHyperspace().createToken(aroundLoc);
    setMapLocation(token);

    Global.getSector().getIntelManager().addIntel(this);
  }

  @Override
  public void advanceImpl(float amount) {
    if(market == null) {
      readyToEnd = true;
      return;
    }
    if(Global.getSector().getPlayerFleet().getContainingLocation() == market.getContainingLocation()){
      readyToEnd = true;
    }

  }

  @Override
  public void readyToEnd() {

  }

  @Override
  public String getIcon() {
    return Global.getSettings().getSpriteName("aEP_icons", "AWM1");
  }

  //控制左下角的简略报告
  @Override
  public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
    Color highLight = Misc.getHighlightColor();
    Color grayColor = Misc.getGrayColor();
    Color whiteColor = Misc.getTextColor();
    Color barColor = faction.getDarkUIColor();
    Color titleTextColor = faction.getColor();

    info.setParaFontDefault();
    info.addPara(txt("AWM04_title"), titleTextColor, 3f);
    info.setBulletedListMode(BULLET);
    info.addPara(txt("AWM04_mission01"), 10f, grayColor);

  }

  //intel页面右边的一竖条详细介绍，默认开启
  @Override
  public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
    Color highLight = Misc.getHighlightColor();
    Color grayColor = Misc.getGrayColor();
    Color whiteColor = Misc.getTextColor();
    Color barColor = faction.getDarkUIColor();
    Color titleTextColor = faction.getColor();

    info.addPara(txt("AWM04_mission02"), 10f, whiteColor);


    if (Global.getSettings().isDevMode()) {
      info.addPara("devMode force finish", Color.yellow, 10f);
      info.addButton("Finish Mission", "Finish Mission", 120, 20, 20f);
    }

  }

  @Override
  public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
    if (buttonId.equals("Finish Mission")) {
      shouldEnd = 1;
    }
  }

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
