package data.scripts.hullmods

import com.fs.starfarer.api.Global
import combat.util.aEP_Tool.Util.addDebugLog
import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import java.util.HashSet
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import org.magiclib.util.MagicIncompatibleHullmods.removeHullmodWithWarning
import java.lang.Exception
import java.lang.StringBuffer

open class aEP_BaseHullMod : BaseHullMod() {

  public val notCompatibleList = HashSet<String>()
  public val haveToBeWithMod = HashSet<String>()
  public val allowOnHullsize = HashMap<ShipAPI.HullSize, Boolean>()
  public val banShipList = HashSet<String>()

  init {
    allowOnHullsize[ShipAPI.HullSize.DEFAULT] = false
    allowOnHullsize[ShipAPI.HullSize.FIGHTER] = true

    allowOnHullsize[ShipAPI.HullSize.FRIGATE] = true
    allowOnHullsize[ShipAPI.HullSize.DESTROYER] = true
    allowOnHullsize[ShipAPI.HullSize.CRUISER] = true
    allowOnHullsize[ShipAPI.HullSize.CAPITAL_SHIP] = true
  }

  override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String) {
    ship?:return

    var shouldRemove = false
    //遍历所有已经安装船插，若存在任何一个排斥，返回false
    var iterator: Iterator<*> = ship.variant.hullMods.iterator()
    var conflictId: String? = ""
    while (iterator.hasNext()) {
      val it = iterator.next() as String
      if (notCompatibleList.contains(it)) {
        shouldRemove = true
        conflictId = it
      }
    }
    if (shouldRemove) {
      //如果本 mod 是 built-in的话，移除发生冲突的另一个，反之移除本 mod
      if (!ship.variant.nonBuiltInHullmods.contains(id)) {
        removeHullmodWithWarning(ship.variant, conflictId, id)
      } else {
        removeHullmodWithWarning(ship.variant, id, conflictId)
      }
    }

    //遍历必须安装的船插表，若任何一个未安装，返回false，移除自己
    //如果不满足其他安装条件，也移除自己
    shouldRemove = false
    iterator = haveToBeWithMod.iterator()
    while (iterator.hasNext()) {
      if (!ship.variant.hasHullMod(iterator.next())) {
        shouldRemove = true
      }
    }
    //自定义的安装条件
    if (!isApplicableToShip(ship)) {
      shouldRemove = true
    }
    if (shouldRemove) {
      ship.variant.removeMod(id)
    }

    //在对话的预览界面，会出现战斗未开始，但是已经生成船的情况
    try {
      applyEffectsAfterShipCreationImpl(ship, id)
      if(isSMod(ship)){
        applySmodEffectsAfterShipCreationImpl(ship, ship.mutableStats, id)
      }

    } catch (e1: Exception) {
      addDebugLog("Error in hullmod: $id at applyEffectsAfterShipCreationImpl")
    }
  }

  override fun isApplicableToShip(ship: ShipAPI): Boolean {

    //检查尺寸
    if(allowOnHullsize[ship.hullSize] != true){
      return false
    }

    //遍历必须安装的船插表，若任何一个未安装，返回false
    var iterator: Iterator<*> = haveToBeWithMod.iterator()
    while (iterator.hasNext()) {
      if (!ship.variant.hasHullMod(iterator.next() as String?)) return false

    }
    //遍历所有已经安装船插，若存在任何一个排斥，返回false
    iterator = ship.variant.hullMods.iterator()
    while (iterator.hasNext()) {
      if (notCompatibleList.contains(iterator.next())) {
        return false
      }
    }

    //遍历舰船黑名单，若存在任何一个排斥，返回false
    iterator = banShipList.iterator()
    while (iterator.hasNext()) {
      if (ship.hullSpec.baseHullId.equals(iterator.next()))  {
        return false
      }
    }
    return true
  }

  override fun getUnapplicableReason(ship: ShipAPI): String {

    if(allowOnHullsize[ship.hullSize] != true){
      return aEP_DataTool.txt("not_right_hullsize")
    }

    //缺失任何一个必须，返回false
    var iterator: Iterator<*> = haveToBeWithMod.iterator()
    while (iterator.hasNext()) {
      if (!ship.variant.hasHullMod(iterator.next() as String?)) {
        return aEP_DataTool.txt("HaveToBeWith") + ": " + showModName(haveToBeWithMod)
      }
    }
    //拥有任何一个排斥，返回false
    iterator = ship.variant.hullMods.iterator()
    while (iterator.hasNext()) {
      if (notCompatibleList.contains(iterator.next())) {
        return aEP_DataTool.txt("not_compatible") + ": " + showModName(notCompatibleList)
      }
    }
    //遍历舰船黑名单，若存在任何一个排斥，返回false
    iterator = banShipList.iterator()
    while (iterator.hasNext()) {
      if (ship.hullSpec.baseHullId.equals(iterator.next()))  {
        return aEP_DataTool.txt("not_compatible") + ": " + ship.hullSpec.hullName
      }
    }

    return aEP_DataTool.txt("not_compatible")
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    ship?: return
    if(ship.isHulk || !ship.isAlive) return
  }

  /**
   * 在船插还是物品的的时候，ship的参数可能为null
   * 默认为true，不然不显示描述
   **/
  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return  true
  }

  /**
   * 在船插还是物品的的时候，ship的参数可能为null
   **/
  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec)
  }

  /**
   * 使用这个
   */
  open fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

  }

  open fun showModName(list: Set<String>): String {
    val toReturn = StringBuffer()
    for (id in list.toTypedArray()) {
      if(Misc.getMod(id) !=null){
        toReturn.append(Global.getSettings().getHullModSpec(id).displayName + ", ")
      }
    }
    if(toReturn.length > 1) {
      toReturn.replace(toReturn.length-2,toReturn.length-1,"")
    }else{
      toReturn.append("None")
    }
    return toReturn.toString()
  }

  /**
   * 使用这个
   */
  open fun applySmodEffectsAfterShipCreationImpl(ship: ShipAPI, stats: MutableShipStatsAPI, id: String) {

  }

  override fun showInRefitScreenModPickerFor(ship: ShipAPI): Boolean {
    if(haveToBeWithMod.contains(aEP_MarkerDissipation.ID) && !ship.variant.hasHullMod(aEP_MarkerDissipation.ID)) return false
    return super.showInRefitScreenModPickerFor(ship)
  }
}