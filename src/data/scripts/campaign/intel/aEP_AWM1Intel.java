package data.scripts.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import combat.util.aEP_ID;
import combat.util.aEP_Tool;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.impl.campaign.ids.Items.TAG_NO_DEALER;
import static combat.util.aEP_DataTool.txt;
import static combat.util.aEP_ID.FACTION_ID_FSF;

public class aEP_AWM1Intel extends aEP_BaseMission
{


  public static final Map<WeaponAPI.WeaponSize, Float> WEAPON_WEIGHT = new HashMap<WeaponAPI.WeaponSize, Float>();

  static {
    WEAPON_WEIGHT.put(WeaponAPI.WeaponSize.SMALL, 1f);
    WEAPON_WEIGHT.put(WeaponAPI.WeaponSize.MEDIUM, 2f);
    WEAPON_WEIGHT.put(WeaponAPI.WeaponSize.LARGE, 3f);
  }

  List<String> requestWeaponList = new ArrayList<>();
  List<String> haveWeaponList = new ArrayList<>();
  public static final String SPLITTER = "_/splitter/_";
  PersonAPI person;

  //第一次聊天直接接任务
  public aEP_AWM1Intel(List<String> requestWeaponList,PersonAPI person) {
    super(0f);
    this.person = person;
    this.requestWeaponList = requestWeaponList;
    Global.getSector().getIntelManager().addIntel(this);
  }

  public static List genWeaponList(){
    List requestWeaponList = new ArrayList();
    WeightedRandomPicker picker = new WeightedRandomPicker();
    List excludes = new ArrayList();
    excludes.add(Tags.NO_DROP);
    excludes.add(Tags.NO_BP_DROP);
    excludes.add(Tags.NO_SELL);
    excludes.add(TAG_NO_DEALER);
    excludes.add(Tags.RESTRICTED);
    excludes.add("FSF_bp");

    //限制那些武器可以进入抽取库
    //格式 "weaponId"+"SPLITTER"+"factionId"
    for (FactionAPI f : Global.getSector().getAllFactions()) {
      if(!f.isShowInIntelTab()) continue;
      if(f.getId().equals("aEP_FSF")) continue;
      for(String weaponId : f.getKnownWeapons()) {
        WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
        if (spec.getAIHints().contains(WeaponAPI.AIHints.SYSTEM)) continue;
        if (!spec.getTags().contains(excludes)) {
          boolean shouldAdd = false;
          if (spec.getTier() >= 3) shouldAdd = true;
          if (spec.getTags().contains("rare_bp")) shouldAdd = true;
          //如果这个武器第一次遇见，就塞进requestWeaponList，作为防重
          if(shouldAdd && !requestWeaponList.contains(weaponId)){
            requestWeaponList.add(weaponId);
            picker.add(spec.getWeaponId()+SPLITTER+f.getId(), 1f);
            continue;
          }
        }
      }
    }

    //清空之前用于防重的requestWeaponList，再取3个不一样的武器放进去
    requestWeaponList.clear();
    requestWeaponList.add(picker.pickAndRemove().toString());
    requestWeaponList.add(picker.pickAndRemove().toString());
    requestWeaponList.add(picker.pickAndRemove().toString());
    return requestWeaponList;
  }


  @Override
  public void advanceImpl(float amount) {
    haveWeaponList.clear();
    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
    for (String fullId : requestWeaponList) {
      String id = fullId.split(SPLITTER)[0];
      if (cargo.getNumWeapons(id) >= 1) {
        haveWeaponList.add(id);
      }
    }

    if (haveWeaponList.size() >= requestWeaponList.size() && shouldEnd == 0) {
      shouldEnd = 1;
    }

    //找齐了武器后，把人物标亮
    if(shouldEnd == 1){
      person.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT,true);
    }else {
      person.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT,false);
    }

  }

  @Override
  public void readyToEnd() {
    //结算时取消人的标亮
    person.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MISSION_IMPORTANT);
    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
    for (String weaponName : haveWeaponList) {
      cargo.removeItems(CargoAPI.CargoItemType.WEAPONS, weaponName, 1);
    }
  }

  @Override
  public String getIcon() {
    return Global.getSettings().getSpriteName("aEP_icons", "AWM1");
  }

  //this part controls brief bar on lower left
  @Override
  public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
    Color h = Misc.getHighlightColor();
    Color g = Misc.getGrayColor();
    Color c = faction.getBaseUIColor();

    info.setParaFontDefault();
    info.addPara(txt("AWM01_title"), c, 3f);
    info.setBulletedListMode(BULLET);
    //this is title
    for (String fullId : requestWeaponList) {
      String weaponId = fullId.split(SPLITTER)[0];
      String factionId = fullId.split(SPLITTER)[1];
      String weaponName = Global.getSettings().getWeaponSpec(weaponId).getWeaponName();
      info.addPara(txt("AWM01_mission05"), 3f, g, h, weaponName);
    }

  }


  //this controls info part on right
  @Override
  public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
    Color hightLight = Misc.getHighlightColor();
    Color grayColor = Misc.getGrayColor();
    Color whiteColor = Misc.getTextColor();
    Color barColor = faction.getDarkUIColor();
    Color titleTextColor = faction.getColor();
    Color c = faction.getBaseUIColor();


    info.setParaFontDefault();
    info.addPara(txt("AWM01_title"), c, 3f);
    for (String fullId : requestWeaponList) {
      String weaponId = fullId.split(SPLITTER)[0];
      String factionId = fullId.split(SPLITTER)[1];
      String weaponName = Global.getSettings().getWeaponSpec(weaponId).getWeaponName();
      String design = Global.getSettings().getWeaponSpec(weaponId).getManufacturer();
      String factionName = Global.getSector().getFaction(factionId).getDisplayName();
      Color designColor = Global.getSettings().getDesignTypeColor(design);
      Color factionColor = Global.getSector().getFaction(factionId).getColor();

      if (!haveWeaponList.contains(weaponId)) {
        info.addPara(txt("AWM01_mission04")+": ", 10f, hightLight,factionColor, factionName);
        info.setBulletedListMode(BULLET);
        Color[] hl = {whiteColor,designColor,Color.red};
        info.addPara(txt("AWM01_mission01"), 10f, hl, design,weaponName);
        info.setBulletedListMode("");
      }
      else {
        info.setBulletedListMode(BULLET);
        info.addPara(txt("AWM01_mission02"), 10f, whiteColor, Color.green, weaponName);
        info.setBulletedListMode("");
      }

    }

    if (shouldEnd == 1) {
      info.addPara(txt("AWM01_mission03"), 10f);
    }

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
