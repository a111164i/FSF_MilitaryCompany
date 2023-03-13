package data.scripts.campaign.entity;


import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import combat.util.aEP_Tool;
import data.hullmods.aEP_CruiseMissileCarrier;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static combat.util.aEP_DataTool.txt;

public class aEP_CruiseMissileEntityPlugin implements CustomCampaignEntityPlugin
{
  private static final float CR_REDUCE_PERCENT  = 0.25f;

  String missileVariantId;
  SectorEntityToken token;
  CampaignFleetAPI targetFleet;
  Vector2f velocity = new Vector2f(0f, 0f);
  float maxSpeed = 300f;
  float speed = 300f;
  float maxAngleSpeed = 60f;
  float angleSpeed = 0f;
  float searchRange = 500f;
  float searchAngle = 60f;

  float textTimer = 999f;

  float time;
  float lifeTime = 8;


  public void setVariantId(String missileVariantId) {
    this.missileVariantId = missileVariantId;
  }

  public void setTargetFleet(CampaignFleetAPI targetFleet) {
    this.targetFleet = targetFleet;
  }

  @Override
  public void init(SectorEntityToken entity, Object params) {
    this.token = entity;
  }

  /**
   * @param amount in seconds. Use SectorAPI.getClock() to figure out how many campaign days that is.
   */
  @Override
  public void advance(float amount) {
    time = time + amount;
    if (time > lifeTime) {
      token.setExpired(true);
    }

    Vector2f toLocation = token.getLocation();

    //change velocity's facing
    float newAngle = token.getFacing() + angleSpeed * amount;
    if (newAngle > 360) {
      newAngle = newAngle - 360;
    }
    if (newAngle < 0) {
      newAngle = newAngle + 360;
    }
    token.setFacing(newAngle);
    speed = aEP_Tool.limitToTop(speed, maxSpeed, 0);
    velocity = aEP_Tool.Util.speed2Velocity(token.getFacing(), speed);

    //set a new location due to velocity
    toLocation.setX(toLocation.x + velocity.x * amount);
    toLocation.setY(toLocation.y + velocity.y * amount);
    token.setLocation(toLocation.x, toLocation.y);

    if(targetFleet == null) targetFleet = findNearestHostileFleet(token, searchRange, searchAngle);
    if (targetFleet != null) {
      angleSpeed = flyToLoc(token.getLocation(), targetFleet.getLocation());
      if (MathUtils.getDistance(token.getLocation(), targetFleet.getLocation()) < targetFleet.getRadius()) {
        if(targetFleet != Global.getSector().getPlayerFleet()){
          spawnBattle(missileVariantId);
        }else {
          spawnBattleToPlayer(missileVariantId);
        }

        token.setExpired(true);
      }
    }
    else {
      angleSpeed = 0;
    }

    //create engine glow
    Vector2f engineLoc = aEP_Tool.getExtendedLocationFromPoint(token.getLocation(), token.getFacing(), -token.getRadius());
    token.getContainingLocation().addParticle(engineLoc,
      new Vector2f(0f, 0f),
      10f,
      1,
      1f,
      amount * 5f,
      new Color(240, 240, 60, 60));


    textTimer = textTimer + amount;
    if (textTimer > 0.5f) {
      textTimer = 0f;
      if (targetFleet != null) {
        token.addFloatingText("Target Locked", Color.red, 0.4f);
        targetFleet.addFloatingText("Target Locked", Color.red, 0.4f);
      }
      else {
        token.addFloatingText("Target Searching", Color.white, 0.4f);
      }
    }

  }

  /**
   * Should only render for specified layer. Will be called once per each layer, per frame.
   * Needs to respect viewport.getAlphaMult() - i.e. use that alpha value for rendering.
   * <p>
   * Needs to render at the entity's location - there's no translation before this method call.
   * <p>
   * If a sprite is specified, it will be rendered in the bottommost layer of the layers this entity renders
   * for. This method will be called after the sprite has rendered.
   *
   * @param layer
   * @param viewport
   */
  @Override
  public void render(CampaignEngineLayers layer, ViewportAPI viewport) {

  }

  /**
   * How far away from the viewport the center of this entity can be before it stops being rendered.
   * Should at least be the radius of the entity; sometimes more may be necessary depending on the
   * visual effect desired.
   *
   * @return
   */
  @Override
  public float getRenderRange() {
    return 100000;
  }

  @Override
  public boolean hasCustomMapTooltip() {
    return false;
  }

  @Override
  public float getMapTooltipWidth() {
    return 0;
  }

  @Override
  public boolean isMapTooltipExpandable() {
    return false;
  }

  @Override
  public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {

  }

  @Override
  public void appendToCampaignTooltip(TooltipMakerAPI tooltip, SectorEntityToken.VisibilityLevel level) {

  }

  CampaignFleetAPI findNearestHostileFleet(SectorEntityToken usingFleet, float radius, float angle) {
    float nearestDist = radius;
    CampaignFleetAPI targetFleet = null;
    for (CampaignFleetAPI aroundFleet : usingFleet.getContainingLocation().getFleets()) {
      if (Math.abs(MathUtils.getShortestRotation(usingFleet.getFacing(), VectorUtils.getAngle(usingFleet.getLocation(), aroundFleet.getLocation()))) < angle / 2f) {
        if (aroundFleet.isHostileTo(usingFleet) && aroundFleet != usingFleet) {
          float dist = MathUtils.getDistance(usingFleet.getLocation(), aroundFleet.getLocation());
          if (dist <= nearestDist) {
            nearestDist = dist;
            targetFleet = aroundFleet;
          }
        }
      }
    }

    return targetFleet;
  }

  float flyToLoc(Vector2f from, Vector2f to) {
    float angleDist = MathUtils.getShortestRotation(token.getFacing(), VectorUtils.getAngle(from, to));
    if (angleDist > 2) {
      return maxAngleSpeed;
    }
    else if (angleDist < -2) {
      return -maxAngleSpeed;
    }
    else {
      return 0;
    }
  }

  void spawnBattle(String missileVariantId) {
    CampaignFleetAPI missileFleet = FleetFactoryV3.createEmptyFleet(token.getFaction().getId(), Global.getSettings().getVariant(missileVariantId).getDisplayName(), null);
    missileFleet.getFleetData().addFleetMember(missileVariantId);
    missileFleet.getMembersWithFightersCopy().get(0).setFlagship(true);

    token.getContainingLocation().spawnFleet(token, 0, 0, missileFleet);

    CampaignFleetAPI originalFleet = Global.getSector().getPlayerFleet();
    Global.getSector().setPlayerFleet(missileFleet);

    //启动战斗
    BattleCreationContext context = new BattleCreationContext(Global.getSector().getPlayerFleet(), FleetGoal.ATTACK, targetFleet, FleetGoal.ESCAPE);
    context.objectivesAllowed = false;
    context.enemyDeployAll = true;
    context.fightToTheLast = true;
    Global.getSector().getCampaignUI().startBattle(context);

    //向战斗引擎中加入导弹制导和强制部署的 combatEveryFrame
    Global.getCombatEngine().addPlugin(new MissileCombatPlugin(false));

    //进入战斗后强制暂停生涯
    Global.getSector().setPaused(false);
    //Global.getLogger(this.getClass()).info("p1"+Global.getSector().isPaused());
    //加入监听器，战后换回舰船
    Global.getSector().addScript(new ExchangePlayerFleet(originalFleet));
    //Global.getLogger(this.getClass()).info("p2"+Global.getSector().isPaused());
    token.getContainingLocation().removeEntity(token);
  }

  //玩家是被射的一边
  void spawnBattleToPlayer(String missileVariantId) {
    CampaignFleetAPI missileFleet = FleetFactoryV3.createEmptyFleet(token.getFaction().getId(), Global.getSettings().getVariant(missileVariantId).getDisplayName(), null);
    missileFleet.getFleetData().addFleetMember(missileVariantId);
    missileFleet.getMembersWithFightersCopy().get(0).setFlagship(true);

    token.getContainingLocation().spawnFleet(token, 0, 0, missileFleet);
    //启动战斗
    BattleCreationContext context = new BattleCreationContext(Global.getSector().getPlayerFleet(), FleetGoal.ESCAPE, missileFleet, FleetGoal.ATTACK);
    context.objectivesAllowed = false;
    context.enemyDeployAll = true;
    context.fightToTheLast = true;
    Global.getSector().getCampaignUI().startBattle(context);

    //加入监听器，战后消除导弹舰队
    Global.getSector().addScript(new DespawnMissileFleet(missileFleet));

    token.getContainingLocation().removeEntity(token);
  }


  class ExchangePlayerFleet implements EveryFrameScript {

    CampaignFleetAPI toExchange;
    float supply;
    int frame= 0;
    boolean shouldEnd = false;

    ExchangePlayerFleet(CampaignFleetAPI toExchange) {
      this.toExchange = toExchange;
      this.supply = toExchange.getCargo().getSupplies();
    }


    @Override
    public boolean isDone() {
      return shouldEnd;
    }

    @Override
    public boolean runWhilePaused() {
      return true;
    }

    //当进入战斗时，生涯的EveryFrame会被挂起，不是暂停而是直接不运行
    //在刚刚进入战斗时，生涯依然会持续运行一段小时间，直到战斗完全初始化完毕，此时会持续把 sector暂停
    @Override
    public void advance(float amount) {
      //持续尝试生成dialog,当对话框成功弹出，则交换回舰队
      if(Global.getSector().getCampaignUI().showInteractionDialog(new ShowLoadedDialog(targetFleet), targetFleet) == true){
        if(Global.getSector().getPlayerFleet() != toExchange) {

          for (FleetMemberAPI member : targetFleet.getMembersWithFightersCopy()) {
            member.getRepairTracker().setCR(Math.min(member.getRepairTracker().getCR(), 1 - member.getRepairTracker().getRepairRatePerDay() * member.getRepairTracker().getRemainingRepairTime()));
            if (member.getRepairTracker().isCrashMothballed()) {
              targetFleet.removeFleetMemberWithDestructionFlash(member);
            }
          }

          CampaignFleetAPI original = Global.getSector().getPlayerFleet();
          Global.getSector().setPlayerFleet(toExchange);
          //据说会出现切换舰队把补给吞了的问题，做个保险
          toExchange.getCargo().addSupplies(supply - toExchange.getCargo().getSupplies());
          Global.getSector().getPlayerFleet().getFleetData().setSyncNeeded();
          original.removeFleetMemberWithDestructionFlash(original.getFlagship());
          original.despawn();
        }
        shouldEnd = true;
      }

    }
  }

  class DespawnMissileFleet implements EveryFrameScript
  {
    CampaignFleetAPI missileFleet;
    boolean shouldEnd = false;

    DespawnMissileFleet(CampaignFleetAPI missileFleet) {
      this.missileFleet = missileFleet;
    }

    @Override
    public boolean isDone() {
      return shouldEnd;
    }

    @Override
    public boolean runWhilePaused() {
      return true;
    }

    //当进入战斗时，生涯的EveryFrame会被挂起，不是暂停而是直接不运行
    //在刚刚进入战斗时，生涯依然会持续运行一段小时间，直到战斗完全初始化完毕，此时会持续把 sector暂停
    @Override
    public void advance(float amount) {
      //当对话框成功弹出，则交换回舰队
      if(Global.getSector().getCampaignUI().showInteractionDialog(new ShowLoadedDialog(Global.getSector().getPlayerFleet()), Global.getSector().getPlayerFleet()) == true){
        missileFleet.despawn();
        shouldEnd = true;
      }
    }

  }

  class MissileCombatPlugin extends BaseEveryFrameCombatPlugin
  {
    CombatEngineAPI engine;
    boolean isEnemy = false;

    MissileCombatPlugin(boolean isEnemy){
      this.isEnemy = isEnemy;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
      ShipAPI missile = null;
      FleetSide which = FleetSide.PLAYER;
      if(isEnemy == true){
        which = FleetSide.ENEMY;
      }
      //第一次退出选船画面
      if (!engine.getCombatUI().isShowingDeploymentDialog() ) {
        //若没有部署，强制部署
        if(engine.getFleetManager(which).getReservesCopy().size() > 0){
          for (FleetMemberAPI member : engine.getFleetManager(which).getReservesCopy()) {
            if (member.getHullSpec().getHullId().contains(aEP_CruiseMissileCarrier.SHIP_ID)) {
              missile = engine.getFleetManager(which).spawnFleetMember(member, new Vector2f(0f, -engine.getMapHeight() / 2f + 5f), 90f, 0f);
              engine.getFleetManager(which).removeFromReserves(member);
            }
          }
        //若已经部署，战场上唯一一艘就是missile
        }else{
          missile = Global.getCombatEngine().getFleetManager(which).getAllEverDeployedCopy().get(0).getShip();
        }
      }
      if(missile == null) return;
      missile.setInvalidTransferCommandTarget(true);
      missile.resetDefaultAI();
      //做好任务以后可以结束了
      engine.removePlugin(this);
    }


    @Override
    public void init(CombatEngineAPI engine) {
      this.engine = engine;
    }

  }

  class ShowLoadedDialog implements InteractionDialogPlugin
  {
    InteractionDialogAPI dialog;
    CampaignFleetAPI toShow;


    ShowLoadedDialog(CampaignFleetAPI toShow){

      this.toShow = toShow;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
      this.dialog = dialog;
      //把被打击的舰队的cr降低，降低量为cr与repair值的差值的一定百分比
      for(FleetMemberAPI m : toShow.getMembersWithFightersCopy()){
        if(m.isCapital() || m.isCruiser() || m.isDestroyer() || m.isFrigate()){
          float cr = m.getRepairTracker().getCR();
          float repair = m.getRepairTracker().computeRepairednessFraction();
          if(cr > repair){
            m.getRepairTracker().setCR(cr - (cr-repair)*CR_REDUCE_PERCENT);
          }
        }
      }


      dialog.getTextPanel().addPara(txt("MissileEntity_after"));
      dialog.getVisualPanel().showFleetInfo(txt("target_fleet"), toShow, null, null);
      dialog.getOptionPanel().addOption("confirm", null);

    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
      switch (optionText) {
        case "confirm":
          this.dialog.dismiss();
      }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {

    }

    @Override
    public void advance(float amount) {

    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {

    }

    @Override
    public Object getContext() {
      return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
      return null;
    }

  }


}
    