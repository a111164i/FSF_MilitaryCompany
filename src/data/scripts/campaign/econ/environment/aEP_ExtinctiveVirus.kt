package data.scripts.campaign.econ.environment

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.util.IntervalUtil
import org.lazywizard.lazylib.MathUtils

class aEP_ExtinctiveVirus : BaseMarketConditionPlugin() {

  companion object {
    public const val PORDUCTION_MULT = 0.5f

    public const val ID = "aEP_ExtinctiveVirus"
  }

  val reapplyTracker = IntervalUtil(0.5f,0.5f)
  val modifiedCom = ArrayList<String>()

  override fun advance(amount: Float) {
    super.advance(amount)
    reapplyTracker.advance(Global.getSector().clock.convertToDays(amount))
    if(reapplyTracker.intervalElapsed()) apply(modId)
  }

  override fun apply(id: String) {
    super.apply(id)
    market?:return
    val faction: FactionAPI? = market.faction
    val size = market.size
    var mult = PORDUCTION_MULT

    //先清空之前修改的值
    unapply(id)
    modifiedCom.clear()
    for(ind in market.industries){
      for(sup in ind.allSupply){
        ind.supply(modId, sup.commodityId, -(sup.quantity.modified/2f).toInt(),name)
        if(!modifiedCom.contains(sup.commodityId) ) modifiedCom.add(sup.commodityId)
      }
    }

  }

  override fun unapply(id: String) {
    for(ind in market.industries){
      for(sup in ind.allSupply){
        ind.supply(modId, sup.commodityId, 0,name)
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