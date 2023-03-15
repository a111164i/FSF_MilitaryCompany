package data.shipsystems.scripts

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.util.IntervalTracker
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_ID
import combat.util.aEP_Tool
import data.scripts.ai.aEP_BaseShipAI
import data.scripts.weapons.aEP_DecoAnimation
import data.shipsystems.scripts.ai.aEP_DroneTimeAlterAI
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.sql.RowIdLifetime

class aEP_AntiPhaseDrone : BaseShipSystemScript() {

  companion object{
    const val WING_SPEC_ID = "aEP_ftr_ut_shendu_wing"

    const val FIGHTER_LIFETIME = 24f
    const val MAX_DISTANCE = 1600f
  }

  lateinit var hold1:WeaponAPI
  lateinit var hold2:WeaponAPI
  lateinit var armor:WeaponAPI
  lateinit var fighter:WeaponAPI
  lateinit var ship: ShipAPI

  var state: ShipSystemStatsScript.State = ShipSystemStatsScript.State.IN
  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    ship = (stats?.entity?: return)as ShipAPI
    this.state = (state?: ShipSystemAPI.SystemState.IDLE) as ShipSystemStatsScript.State
    val ship = (ship?:return)
    searchWeapon(ship)

    controlHold(effectLevel)
    controlArmor(effectLevel)
    controlFighter(effectLevel)

    //生成飞机
    if(effectLevel == 1f){
      val engine = Global.getCombatEngine()

      //屏蔽左上角入场提升
      val suppressBefore = engine?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages
      engine?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages = true
      //生成无人机
      val drone = engine?.getFleetManager(ship.owner)?.spawnShipOrWing(
        WING_SPEC_ID,
        aEP_Tool.getExtendedLocationFromPoint(fighter.location,fighter.currAngle,-34f)?: aEP_ID.VECTOR2F_ZERO,
        fighter.currAngle)
      //恢复左上角入场提示
      engine?.getFleetManager(ship.owner)?.isSuppressDeploymentMessages = suppressBefore?: false
      aEP_CombatEffectPlugin.addEffect(SelfDestruct(drone as ShipAPI,ship,FIGHTER_LIFETIME))
    }

  }


  //这个最先调用，所以在这里初始化这几个装饰武器
  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    ship = (stats?.entity?: return)as ShipAPI
    val ship = (ship?:return)
    state = ShipSystemStatsScript.State.COOLDOWN
    searchWeapon(ship)
    controlHold(0f)
    controlArmor(0f)
    controlFighter(0f)

  }

  fun searchWeapon(ship: ShipAPI){
    for(w in ship.allWeapons){
      if(!w.slot.isDecorative) continue
      if(w.slot.id.equals("ARMOR")) {armor = w; continue;}
      if(w.slot.id.equals("HOLD_1")) {hold1 = w; continue;}
      if(w.slot.id.equals("HOLD_2")) {hold2 = w; continue;}
      if(w.slot.id.equals("FIGHTER")) {fighter = w; continue;}
    }
  }

  fun controlArmor(level:Float){
    val plugin = armor.effectPlugin as aEP_DecoAnimation
    var toUseLevel = 0f
    //0.2f时横向展开
    if(level < 0.2f){
      toUseLevel = level/0.2f
      plugin.setMoveToSideLevel(toUseLevel)
    //0.2f-0.6f中的0.4f内向下滑动
    }else{
      toUseLevel = (level-0.2f)/0.4f
      plugin.setMoveToLevel(toUseLevel)
    }
  }

  fun controlHold(level:Float){
    val plugin1 = hold1.effectPlugin as aEP_DecoAnimation
    val plugin2 = hold2.effectPlugin as aEP_DecoAnimation
    var toUseLevel = 0f
    if(level < 0.6f){
      plugin1.decoMoveController.effectiveLevel = toUseLevel
      plugin2.decoMoveController.effectiveLevel = toUseLevel
    //0.6f侧板完全打开后，开始展开
    }else{
      toUseLevel = (level-0.6f)/0.3f
      plugin1.setMoveToLevel(toUseLevel)
      plugin2.setMoveToLevel(toUseLevel)
    }

  }

  fun controlFighter(level:Float){
    val plugin = fighter.effectPlugin as aEP_DecoAnimation

    //调整帧数的本帧会导致本帧数贴图位置出问题
    //就算直接设置effectiveLevel重置贴图位置 也是武器的下一帧才会被advance
    //所以设置帧数以后，手工把贴图归正
    if(state == ShipSystemStatsScript.State.IN && fighter.animation.frame == 0){
      fighter.animation.frame = 1
      fighter.sprite.setCenter(plugin.decoMoveController.originalX,plugin.decoMoveController.originalY)
    }else if (state == ShipSystemStatsScript.State.COOLDOWN && fighter.animation.frame == 1){
      fighter.animation.frame = 0
      fighter.sprite.setCenter(plugin.decoMoveController.originalX,plugin.decoMoveController.originalY)
    }

    var toUseLevel = 0f
    if(level < 0.6f){
      plugin.decoMoveController.effectiveLevel = 0f
    //和hold同步，只不过加一个控制帧数的
    }else{
      toUseLevel = (level-0.6f)/0.3f
      plugin.setMoveToLevel(toUseLevel)
    }


  }

  inner class SelfDestruct : aEP_BaseCombatEffect{
    lateinit var f:ShipAPI
    lateinit var parent:ShipAPI
    lateinit var moveAi: aEP_BaseShipAI
    var target:ShipAPI? = null
    val id = "aEP_SelfDestruct"
    val jitterColor = Color(255,50,50)

    constructor(f: ShipAPI, parent: ShipAPI, lifeTime: Float):super(lifeTime,f) {
      this.f = f
      this.parent = parent
      if(parent.shipTarget != null && MathUtils.getDistance(parent.shipTarget.location,f.location) < MAX_DISTANCE) {
        target = parent.shipTarget
        //如果发射的时候锁定了目标，使用自定义ai
        moveAi = object : aEP_BaseShipAI(f) {
          val newAttackLocTracker = IntervalTracker(0.5f,1f)
          var attackLoc = target?.location

          override fun advanceImpl(amount: Float) {
            if(target == null || target !is ShipAPI){
              f.resetDefaultAI()
              return
            }
            val target = target as ShipAPI
            if(!target.isAlive || target.isHulk){
              f.resetDefaultAI()
              return
            }

            val dist = MathUtils.getDistance(f,target)
            val attackDist = target.collisionRadius + 50f
            if(dist > attackDist){
              aEP_Tool.flyThroughPosition(f,target.location)
            }else{
              newAttackLocTracker.advance(amount)
              if(newAttackLocTracker.intervalElapsed()){
                val angle = MathUtils.getRandomNumberInRange(0f,360f)
                val d = MathUtils.getRandomNumberInRange(target.collisionRadius/2f,attackDist)
                attackLoc = aEP_Tool.speed2Velocity(angle,d)
              }
              aEP_Tool.moveToAngle(f,VectorUtils.getAngle(f.location,target.location))
              aEP_Tool.setToPosition(f,attackLoc)
            }

            if(f.system.state == ShipSystemAPI.SystemState.IDLE && f.fullTimeDeployed>3f) f.useSystem()
          }

          override fun forceCircumstanceEvaluation() {
            val newTarget = aEP_Tool.getNearestFriendCombatShip(f)
            if(newTarget != null) target = newTarget
          }
        }
        f.shipAI = moveAi
      }
    }

    override fun advanceImpl(amount: Float) {
      //如果母舰被毁，立刻自爆
      if(!parent.isAlive || parent.isHulk) { shouldEnd = true; readyToEnd(); }

      //如果距离母舰过远，机动性下降
      if(MathUtils.getDistance(f,parent) > aEP_Tool.getSystemRange(parent, MAX_DISTANCE)){
        f.mutableStats.maxSpeed.modifyMult(id,0.5f)
        f.mutableStats.acceleration.modifyMult(id,0.5f)
      }else{
        f.mutableStats.maxSpeed.unmodify(id)
        f.mutableStats.acceleration.unmodify(id)
      }

      //快炸了的时候给提示
      val jitterThreshold = 4f
      if(lifeTime - time < jitterThreshold){
        val level = 1f - (lifeTime - time)/jitterThreshold
        f.setJitter(id,aEP_Tool.getColorWithAlpha(jitterColor,level),level,2,1f*level)
      }
    }

    //自爆
    override fun readyToEnd() {
      f.mutableStats.armorDamageTakenMult.unmodify()
      f.mutableStats.hullDamageTakenMult.unmodify()
      f.mutableStats.highExplosiveDamageTakenMult.unmodify()
      Global.getCombatEngine().applyDamage(
        f,f.location,
        999999999f,DamageType.HIGH_EXPLOSIVE,0f,
        true,false,f,false)
    }
  }
}
