package data.scripts.ai.shipsystemai

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import data.scripts.utils.aEP_Tool
import data.scripts.utils.aEP_Tool.Util.getExtendedLocationFromPoint
import data.scripts.hullmods.aEP_TwinFighter
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.absoluteValue

class aEP_CoordinatedCombatAI: aEP_BaseSystemAI() {

  companion object{
    const val ID = "aEP_CoordinatedCombatAI"

  }

  var mode = 0
  var forcePullBackTime = 0f
  var noSwapTime = 4f

  override fun initImpl() {
    thinkTracker.setInterval(0.01f,0.2f)
  }

  override fun advance(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    if(forcePullBackTime > 0f){
      ship.blockCommandForOneFrame(ShipCommand.PULL_BACK_FIGHTERS)
      ship.isPullBackFighters = true
    }
    super.advance(amount, missileDangerDir, collisionDangerDir, target)
  }

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    shouldActive= false

    val leftCharge = (system?.ammo)?:1
    val maxAmmo = (system?.maxAmmo)?:1
    val leftPercent = (leftCharge/maxAmmo.toFloat())
    var willingMode0 = 0f
    var willingMode1 = 0f
    var willingMode2 = 0f
    var willingMode3 = 0f
    //要是战机被摧毁，根本没法用系统
    val m = aEP_TwinFighter.getM(ship)
    if(m == null || aEP_Tool.isDead(m)) return
    val distShipFtr = MathUtils.getDistance(m,ship)

    val damAboutShip = findNearbyProjectile(ship,2f)
    val damAboutModule = findNearbyProjectile(m,2f)
    var distToTarget = Float.MAX_VALUE
    if(ship.shipTarget != null) distToTarget = MathUtils.getDistance(ship,ship.shipTarget)
    val longestRange = aEP_Tool.getLongestNormalWeapon(ship)?.range?:1f

    //检测顺序，靠后的覆盖靠前的
    //1技能，本体加火力
    if(longestRange - distToTarget > 0f) {
      if(leftCharge > 5){
        willingMode1 += 100f
      }else if (leftCharge > 4){
        willingMode1 += 65f
      }else if (leftCharge > 3){
        willingMode1 += 51f
      }
    }

    //2技能，无人机堡垒
    if(ship.shipTarget != null){
      if(ship.shipTarget.shipTarget == m){
        if(damAboutModule > 500f){
          if(leftCharge > 4){
            willingMode2 += 100f
          }else if (leftCharge > 3){
            willingMode2 += 65f
          }else if (leftCharge > 2){
            willingMode2 += 51f
          }
        }
      }
    }
    if(damAboutModule > 500f){
      willingMode2 += 60f
    }
    if(distShipFtr < 100f) willingMode2 = 0f

    //3技能，交换位置
    noSwapTime = MathUtils.clamp(noSwapTime-amount,0f,100f)
    if(ship.shipTarget != null && noSwapTime <= 0f){
      //如果母舰拥有目标，而战机最近的一个敌舰也是目标
      val fighterNearest = aEP_Tool.getNearestEnemyCombatShip(m)
      if(fighterNearest == ship.shipTarget){
        val t = ship.shipTarget
        val angleTarget2M = VectorUtils.getAngle(t.location, m.location)
        val angleTarget2Ship = VectorUtils.getAngle(t.location, ship.location)
        //模块和本体的同一目标，且角度相差120度以上
        if(MathUtils.getShortestRotation(angleTarget2M,angleTarget2Ship).absoluteValue > 90f) {
          //模块当前周围没有其他敌人，并且模块已经绕到了敌人的护盾后面，如果没有盾，就正后面
          var backAngle = t.facing - 180f
          if(t.shield != null && t.shield.isOn) backAngle = t.shield.facing - 180f
          //模块当前距离敌人的后背小于30度
          if(MathUtils.getShortestRotation(angleTarget2M,backAngle).absoluteValue < 60f){
            if (leftCharge > 2){
              willingMode3 += 100f
              noSwapTime = 4f
            }
          }
        }
      }

    }

    //0技能，开盾
    if(m.maxFlux - m.currFlux > 500f){
      if(leftCharge > 3 && damAboutShip > 200f){
        willingMode0 += 90f
      }else if (leftCharge > 2 && damAboutShip > 200f){
        willingMode0 += 80f
      }else if (leftCharge > 1 && damAboutShip > 100f){
        willingMode0 += 70f
      }else if (leftCharge > 0 && damAboutShip > 100f){
        willingMode0 += 60f
      }
    }

    willingMode0 += MathUtils.getRandomNumberInRange(0f,50f)
    willingMode1 += MathUtils.getRandomNumberInRange(0f,50f)
    willingMode2 += MathUtils.getRandomNumberInRange(0f,50f)
    willingMode3 += MathUtils.getRandomNumberInRange(0f,50f)

    if(willingMode3 > 100){
      mode = 3
      shouldActive = true
    }else if(willingMode0 > 100){
      mode = 0
      shouldActive = true
    }else if(willingMode2 > 100){
      mode = 2
      shouldActive = true
    }else if(willingMode1 > 100){
      mode = 1
      shouldActive = true
    }

    //进行无人机z的控制
    if(m.hullLevel <= 0.5f){
      if(forcePullBackTime == 0f) forcePullBackTime = (m.maxHitpoints+m.armorGrid.armorRating)/aEP_TwinFighter.REPAIR_SPEED
    }

    //设置好要用哪个技能，systemScript里面会读取
    ship.setCustomData(ID, mode)
  }

  //找到附近可能击中自己
  fun findNearbyProjectile(entity: CombatEntityAPI, time2Hit: Float) : Float{

    var aboutToHit = 0f
    //如果自己无碰撞，不可能有东西击中自己
    if(entity.collisionClass == CollisionClass.NONE) return aboutToHit

    for (tmp in Global.getCombatEngine().projectiles) {
      //对于友方弹丸
      if(tmp.owner == entity.owner )
        //可穿透友军的，无视
        if(tmp.collisionClass == CollisionClass.PROJECTILE_NO_FF
          || tmp.collisionClass == CollisionClass.PROJECTILE_FIGHTER
          || tmp.collisionClass == CollisionClass.MISSILE_NO_FF
          || tmp.collisionClass == CollisionClass.RAY_FIGHTER ) continue

      //对于无碰撞体积的弹丸，无视
      if(tmp.collisionClass == CollisionClass.NONE) continue
      //对于对自己拥有碰撞的弹丸，计算速度的朝向和速度大小平方
      val angAndS = aEP_Tool.velocity2Speed(tmp.velocity)
      val distSq = MathUtils.getDistanceSquared(entity, tmp)

      //计算飞行2秒后的位置，是否可能划过自己的碰撞圈
      var collide = CollisionUtils.getCollides(
        tmp.location,
        getExtendedLocationFromPoint(tmp.location,angAndS.x,angAndS.y * time2Hit),
        entity.location, entity.collisionRadius)

      //对于停留在自己周围100内的，直接算碰撞（空雷）
      if(distSq <= 100f) collide = true

      if(collide){
        val dam = aEP_Tool.computeEffectiveDamage(tmp.damage, 0.75f,1f,0.75f, 0.25f)
        aboutToHit += dam
      }
    }

    //计算完弹丸，来算光束
    for (b in Global.getCombatEngine().beams){
      if((b as CombatEntityAPI).collisionClass == CollisionClass.NONE) continue
      if(b.damage.damage <= 10f) continue
      val distSq = MathUtils.getDistance(entity, b.to)
      if(distSq <= 10f){
        aboutToHit += b.damage.damage/1.33f
      }
    }

    return aboutToHit
  }


  class ForcePullBackFor()
}