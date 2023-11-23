package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.skills.SupportDoctrine
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import java.awt.Color
import kotlin.math.roundToInt

class aEP_FirstDeck:aEP_BaseHullMod() {

  companion object{
    const val ID = "aEP_FirstDeck"
    //非轰炸机机加成
    const val AMMO_BONUS_PERCENT = 100f
    const val CAP_BONUS_PERCENT = 100f
    const val VENT_BONUS_PERCENT = 25f

    //轰炸机加成
    const val SPEED_BONUS_PERCENT = 50f

    //整备率转移
    const val RR_DRAIN_THRESHOLD = 0.70f
    const val RR_DRAIN_PEC_SECOND = 5f

    //非f甲板的伤害惩罚
    const val NON_F_DECK_DAMAGE_REDUCE_MULT = 0.5f

  }

  init {
  }


  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

    var i = 0
    while(i < ship.launchBaysCopy.size-1){
      val bay = ship.launchBaysCopy[i]
      val nextBay = ship.launchBaysCopy[i+1]
      if(nextBay.currRate < RR_DRAIN_THRESHOLD){
        val maxDrain = RR_DRAIN_PEC_SECOND * amount
        val nextEmpty = 1f - nextBay.currRate
        //最低整备值是0.3
        val bayLeft = bay.currRate - 0.3f
        val toDrain = maxDrain.coerceAtMost(nextEmpty).coerceAtMost(bayLeft)
        nextBay.currRate += toDrain
        bay.currRate -= toDrain
      }

       i += 1
    }

    var k = 0
    for(bay in ship.launchBaysCopy){
      k += 1
      //aEP_Tool.addDebugLog(k.toString() +":   "+bay.currRate.toString())
    }

  }

  override fun applyEffectsToFighterSpawnedByShip(fighter: ShipAPI?, ship: ShipAPI?, id: String?) {
    fighter?:return
    ship?:return
    val wing = fighter.wing?:return
    val bays = ship.launchBaysCopy
    //检查是否是最后一个甲板
    if(wing.source?.weaponSlot?.id?.contains("FF")?:return){
      if(wing.spec.isBomber){
        fighter.mutableStats.maxSpeed.modifyPercent(ID, SPEED_BONUS_PERCENT)
        fighter.mutableStats.acceleration.modifyPercent(ID, SPEED_BONUS_PERCENT/2f)
        fighter.mutableStats.deceleration.modifyPercent(ID, SPEED_BONUS_PERCENT/2f)
      }
      if(!wing.spec.isBomber){
        //因为是飞机已经生成后才触发，需要手动修改最大备弹并把当前备弹加到和最大一样
        fighter.mutableStats.ballisticAmmoBonus.modifyPercent(ID, AMMO_BONUS_PERCENT)
        fighter.mutableStats.energyAmmoBonus.modifyPercent(ID, AMMO_BONUS_PERCENT)
        fighter.mutableStats.missileAmmoBonus.modifyPercent(ID, AMMO_BONUS_PERCENT)
        for(w in fighter.allWeapons){
          if(w.type == WeaponAPI.WeaponType.BALLISTIC && w.usesAmmo()){
            w.maxAmmo = fighter.mutableStats.ballisticAmmoBonus.computeEffective(w.maxAmmo.toFloat()).toInt()
            w.ammo = w.maxAmmo
          }
          if(w.type == WeaponAPI.WeaponType.ENERGY && w.usesAmmo()){
            w.maxAmmo = fighter.mutableStats.ballisticAmmoBonus.computeEffective(w.maxAmmo.toFloat()).toInt()
            w.ammo = w.maxAmmo
          }
          if(w.type == WeaponAPI.WeaponType.MISSILE && w.usesAmmo()) {
            w.maxAmmo = fighter.mutableStats.ballisticAmmoBonus.computeEffective(w.maxAmmo.toFloat()).toInt()
            w.ammo = w.maxAmmo
          }
        }


        fighter.mutableStats.fluxCapacity.modifyPercent(ID, CAP_BONUS_PERCENT)
        fighter.mutableStats.fluxDissipation.modifyPercent(ID, VENT_BONUS_PERCENT)
      }

    }else{
      //不是最后一个甲板，受到惩罚
      fighter.mutableStats.damageToFighters.modifyMult(ID, 1f - NON_F_DECK_DAMAGE_REDUCE_MULT)
      fighter.mutableStats.damageToFrigates.modifyMult(ID, 1f - NON_F_DECK_DAMAGE_REDUCE_MULT)
      fighter.mutableStats.damageToDestroyers.modifyMult(ID, 1f - NON_F_DECK_DAMAGE_REDUCE_MULT)
      fighter.mutableStats.damageToCruisers.modifyMult(ID, 1f - NON_F_DECK_DAMAGE_REDUCE_MULT)
      fighter.mutableStats.damageToCapital.modifyMult(ID, 1f - NON_F_DECK_DAMAGE_REDUCE_MULT)
    }
  }

  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    //主效果
    tooltip.addSectionHeading(txt("effect"), Alignment.MID, 5f)
    //轰炸机加成
    tooltip.addPara("{%s}"+ txt("aEP_FirstDeck01"), 5f, arrayOf(Color.green,highLight),
      aEP_ID.HULLMOD_POINT,
      txt("bomber_wing"))
    tooltip.addPara("{%s}"+ txt("aEP_FirstDeck02"), 5f, arrayOf(txtColor,highLight),
      aEP_ID.HULLMOD_BULLET,
      String.format("%.0f", SPEED_BONUS_PERCENT) +"%")
    //非轰炸机加成
    tooltip.addPara("{%s}"+ txt("aEP_FirstDeck01"), 5f, arrayOf(Color.green,highLight),
      aEP_ID.HULLMOD_POINT,
      txt("non_bomber_wing"))
    tooltip.addPara("{%s}"+ txt("aEP_FirstDeck03"), 5f, arrayOf(txtColor,highLight),
      aEP_ID.HULLMOD_BULLET,
      String.format("%.0f", CAP_BONUS_PERCENT) +"%")
    tooltip.addPara("{%s}"+ txt("aEP_FirstDeck04"), 5f, arrayOf(txtColor,highLight),
      aEP_ID.HULLMOD_BULLET,
      String.format("%.0f", VENT_BONUS_PERCENT) +"%")
    tooltip.addPara("{%s}"+ txt("aEP_FirstDeck05"), 5f, arrayOf(txtColor,highLight),
      aEP_ID.HULLMOD_BULLET,
      String.format("%.0f", AMMO_BONUS_PERCENT) +"%")
    //双刃剑
    tooltip.addPara("{%s}"+ txt("aEP_FirstDeck06"), 5f, arrayOf(Color.yellow,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", RR_DRAIN_THRESHOLD * 100f) +"%",
      String.format("%.0f", RR_DRAIN_PEC_SECOND) +"%")

    //负面
    tooltip.addPara("{%s}"+ txt("aEP_FirstDeck07"), 5f, arrayOf(Color.red,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f", NON_F_DECK_DAMAGE_REDUCE_MULT * 100f) +"%")

    //不兼容
    //tooltip.addPara("{%s}"+ aEP_DataTool.txt("not_compatible") +"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))

    //预热完全后额外效果
    //tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor, Alignment.MID, 5f)

    //灰字额外说明
    //tooltip.addPara(aEP_DataTool.txt("MD_des04"), grayColor, 5f)

  }


}