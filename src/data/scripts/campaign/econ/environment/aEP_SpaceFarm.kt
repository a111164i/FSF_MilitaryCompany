package data.scripts.campaign.econ.environment

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.util.IntervalUtil
import org.lazywizard.lazylib.MathUtils

class aEP_SpaceFarm : BaseMarketConditionPlugin() {

  companion object {
    private const val TOTAL_UNIT_BASE = 3
    private const val MAX_SIZE = 3
    private const val PER_SIZE_BONUS = 1

    private const val TOTAL_ORGANIC_UNIT_BASE = 3
    private const val MAX_ORGANIC_SIZE = 3
    private const val PER_ORGANIC_SIZE_BONUS = 0.5

    public const val ID = "aEP_SpaceFarm"
  }

  val reapplyTracker = IntervalUtil(0.5f,0.5f)

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
    var unitSupply = TOTAL_UNIT_BASE + MathUtils.clamp(size-3,0,MAX_SIZE) * PER_SIZE_BONUS
    var OrganicSupply = TOTAL_ORGANIC_UNIT_BASE + (MathUtils.clamp(size-3,0,MAX_ORGANIC_SIZE) * PER_ORGANIC_SIZE_BONUS).toInt()

    //先清空之前修改的值
    unapply(id)

    for(ind in market.industries){
      if(ind.id == Industries.POPULATION){
        ind.supply(modId,Commodities.FOOD, unitSupply, name)
        ind.supply(modId,Commodities.ORGANICS, OrganicSupply, name)
      }
    }

  }

  override fun unapply(id: String) {
    for(ind in market.industries){
      if(ind.id == Industries.POPULATION){
        //设置为0就会自动取消加成
        ind.supply(modId,Commodities.FOOD,0,name)
        ind.supply(modId,Commodities.ORGANICS, 0, name)
      }
    }
  }

  override fun getRelatedCommodities(): MutableList<String> {
    return  return mutableListOf(Commodities.FOOD,Commodities.ORGANICS)
  }

  override fun isTransient(): Boolean {
    return false
  }
}