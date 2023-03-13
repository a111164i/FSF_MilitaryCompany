package data.scripts.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import combat.util.aEP_DataTool;
import combat.util.aEP_Tool;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

public class aEP_BaseMission extends BaseIntelPlugin
{
  //shouldEnd大于0时，任务检测视已经完成全部流程但是还没交的状态
  //用于生涯npc检测该任务是否可交
  public int shouldEnd = 0;
  //把这个设置为true，任务真正结束，并开始调用一次结算函数
  public boolean readyToEnd = false;
  public float lifeTime = 0f;
  public float time = 0f;
  public SectorAPI sector;
  public FactionAPI faction;
  private String name;
  public SectorEntityToken mapLocation;
  Set<String> tags = new LinkedHashSet<String>();


  /**
   * 基础的Intel要被看到，至少要设置tag，否则不在Intel页面new以外的tag中显示
   */
  aEP_BaseMission(){
    endingTimeRemaining = 0f;
    ended = false;
    ending = false;

  }

  @Override
  public void advance(float amount) {
    float days = Global.getSector().getClock().convertToDays(amount);
    //已经结束的任务从这里出去
    if (isEnded()) return;
    //正在结束的任务从这里出去，百分之99的情况是立刻结束所以无视就行，限时任务直在子类里单独写，不用这个
    //单纯是父类里面要这个所以不得不加
    if (ending) {
      endingTimeRemaining = endingTimeRemaining - days;
      if (endingTimeRemaining <= 0) {
        ended = true;
        notifyEnded();
      }
      return;
    }

    //若lifeTime设置为负数或者0就则不启用
    //否则时间到点自动结束
    if (lifeTime > 0f) {
      time = time + days;
      if (time >= lifeTime) {
        readyToEnd = true;
      }
    }

    //把readyToEnd设置为true后会运行一次结算函数，然后变成正在结束的状态就从上面出去，不再进入这里
    //在多结局任务时，记得在结算函数里根据不同结局进行不同处理
    if (readyToEnd) {
      readyToEnd();
      ending = true;
      return;
    }
    advanceImpl(amount);
  }

  /**
   * 用这个
   * */
  @Override
  protected void advanceImpl(float amount) {
    super.advanceImpl(amount);
  }

  /**
   * 结算任务，只运行一次
   * */
  public void readyToEnd() {
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getSmallDescriptionTitle() {
    return "Mission Target";
  }

  /**
   * 控制左下角的简略报告
   * */
  @Override
  public void createIntelInfo(TooltipMakerAPI info,ListInfoMode mode) {
    float pad = 3f;
    float opad = 10f;
    Color h = Misc.getHighlightColor();
    Color g = Misc.getGrayColor();
    Color c = faction.getBaseUIColor();
    info.setParaFontDefault();
    info.addPara(aEP_DataTool.txt(""), c, pad);
    info.setBulletedListMode(BULLET);
    info.addPara(aEP_DataTool.txt(""), opad, g, h);
  }

  /**
   * intel页面右边的一竖条详细介绍，默认开启
   * */
  @Override
  public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
    super.createSmallDescription(info, width, height);
  }

  /**
   * 覆盖intel页面的星图，一大面的详细结束，配合hasLargeDescription()使用
   * */
  @Override
  public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
    super.createLargeDescription(panel, width, height);
  }


  @Override
  public Color getTitleColor(ListInfoMode mode) {
    return faction.getBaseUIColor();
  }

  @Override
  public FactionAPI getFactionForUIColors() {
    return faction;
  }

  @Override
  public SectorEntityToken getMapLocation(SectorMapAPI map) {
    return mapLocation;
  }

  /**
   * 和 postLocation一起用
   * */
  public void setMapLocation(SectorEntityToken mapLocation) {
    this.mapLocation = mapLocation;
  }

  @Override
  public Set<String> getIntelTags(@Nullable SectorMapAPI map) {
    if (isImportant()) {
      tags.add(Tags.INTEL_IMPORTANT);
    }
    if (isNew()) {
      tags.add(Tags.INTEL_NEW);
    }
    if (map != null) {
      SectorEntityToken loc = getMapLocation(map);
      if (loc != null) {
        float max = Global.getSettings().getFloat("maxRelayRangeInHyperspace");
        float dist = Misc.getDistanceLY(loc.getLocationInHyperspace(), Global.getSector().getPlayerFleet().getLocationInHyperspace());
        if (dist <= max) {
          tags.add(Tags.INTEL_LOCAL);
        }
      }
    }

//		FactionAPI faction = getFactionForUIColors();
//		if (faction != null && !faction.isPlayerFaction()) {
//			if (faction.isHostileTo(Factions.PLAYER)) {
//				tags.add(Tags.INTEL_HOSTILE);
//			} else {
//				tags.add(Tags.INTEL_NOT_HOSTILE);
//			}
//		}

    return tags;
  }

  @Override
  public String getIcon() {
    return  Global.getSettings().getSpriteName("aEP_icons", "AWM1");
  }

  @Override
  public boolean isDone() {
    return ended;
  }
}
