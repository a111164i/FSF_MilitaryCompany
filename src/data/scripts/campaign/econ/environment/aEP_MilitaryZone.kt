package data.scripts.campaign.econ.environment

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.util.IntervalUtil
import combat.util.aEP_ID

class aEP_MilitaryZone : BaseMarketConditionPlugin() {

  companion object {
    private const val LUXURY_REDUCE = 8
    private const val DRUG_REDUCE = 8
    private const val ORGAN_REDUCE = 4

    private const val ORE_SUPPLY = 2
    private const val RARE_ORE_SUPPLY = 2
    private const val VOLATILE_SUPPLY = 2
    private const val ORGANIC_SUPPLY = 2

    private const val MAX_IND_BONUS = 2f

    public const val ID = "aEP_MilitaryZone"
  }
  val modifiedCom = ArrayList<String>()
  val reapplyTracker = IntervalUtil(0.5f,0.5f)

  override fun advance(amount: Float) {
    super.advance(amount)
    reapplyTracker.advance(Global.getSector().clock.convertToDays(amount))
    if(reapplyTracker.intervalElapsed()) apply(modId)
  }

  override fun apply(id: String) {
    super.apply(id)
    market?:return
    //可能为null，如果没有人占领这个市场
    val faction: FactionAPI? = market.faction

    if(faction?.id != aEP_ID.FACTION_ID_FSF){
      unapply(id)
      return
    }

    val size = market.size
    val reduceLux = LUXURY_REDUCE
    val reduceDrug = DRUG_REDUCE
    val reduceOrgan = ORGAN_REDUCE
    //先清空之前修改的值
    unapply(id)
    modifiedCom.clear()

    for(ind in market.industries){
      //消除人口的奢侈品，毒品，器官需求
      if(ind.id == Industries.POPULATION) {
        val ind  = ind as BaseIndustry
        for (dem in ind.allDemand) {
          if (dem.commodityId == Commodities.LUXURY_GOODS) {
            ind.demand(modId,Commodities.LUXURY_GOODS,-reduceLux, name)
            if(!modifiedCom.contains(dem.commodityId) ) modifiedCom.add(dem.commodityId)
          }
          if (dem.commodityId == Commodities.DRUGS) {
            ind.demand(modId,Commodities.DRUGS,-reduceDrug, name)
            if(!modifiedCom.contains(dem.commodityId) ) modifiedCom.add(dem.commodityId)
          }
          if (dem.commodityId == Commodities.ORGANS) {
            ind.demand(modId,Commodities.ORGANS,-reduceOrgan, name)
            if(!modifiedCom.contains(dem.commodityId) ) modifiedCom.add(dem.commodityId)
          }
        }
      }

      //消除采矿的 毒品 需求，增加产量1
      if(ind.id == Industries.MINING) {
        val ind  = ind as BaseIndustry
        for (dem in ind.allDemand) {
          if (dem.commodityId == Commodities.DRUGS) {
            ind.demand(modId,Commodities.DRUGS,-reduceDrug, name)
            if(!modifiedCom.contains(dem.commodityId) ) modifiedCom.add(dem.commodityId)
          }
        }

        //增加产量
        for (sup in ind.allSupply){
          if (sup.commodityId == Commodities.ORE) {
            ind.supply(modId,Commodities.ORE, ORE_SUPPLY, name)
            if(!modifiedCom.contains(sup.commodityId) ) modifiedCom.add(sup.commodityId)
          }
          if (sup.commodityId == Commodities.RARE_ORE) {
            ind.supply(modId,Commodities.RARE_ORE, RARE_ORE_SUPPLY, name)
            if(!modifiedCom.contains(sup.commodityId) ) modifiedCom.add(sup.commodityId)
          }
          if (sup.commodityId == Commodities.VOLATILES) {
            ind.supply(modId,Commodities.VOLATILES, VOLATILE_SUPPLY, name)
            if(!modifiedCom.contains(sup.commodityId) ) modifiedCom.add(sup.commodityId)
          }
          if (sup.commodityId == Commodities.ORGANICS) {
            ind.supply(modId,Commodities.ORGANICS, ORGANIC_SUPPLY, name)
            if(!modifiedCom.contains(sup.commodityId) ) modifiedCom.add(sup.commodityId)
          }
        }
      }



    }

    //增加最大工业设施数量
    market.stats.dynamic.getMod(Stats.MAX_INDUSTRIES).modifyFlat(id, MAX_IND_BONUS)
  }

  override fun unapply(id: String) {
    for(ind in market.industries){
      for(dem in ind.allDemand){
        dem.quantity.unmodify(modId)
      }
      for(sup in ind.allSupply){
        sup.quantity.unmodify(modId)
      }
    }
  }

  override fun getRelatedCommodities(): MutableList<String> {
    return modifiedCom
  }

  override fun isTransient(): Boolean {
    return false
  }


}