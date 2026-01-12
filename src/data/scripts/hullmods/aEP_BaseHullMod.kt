package data.scripts.hullmods

import com.fs.starfarer.api.Global
import data.scripts.utils.aEP_Tool.Util.addDebugLog
import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import java.util.HashSet
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_DataTool
import data.scripts.utils.aEP_ID
import org.lwjgl.input.Keyboard
import org.magiclib.kotlin.computeNumFighterBays
import org.magiclib.util.MagicIncompatibleHullmods.removeHullmodWithWarning
import java.awt.Color
import java.lang.Exception
import java.lang.StringBuffer

open class aEP_BaseHullMod : BaseHullMod() {

  companion object{
    const val PARAGRAPH_PADDING_SMALL = 5f
    const val PARAGRAPH_PADDING_BIG = 10f
    const val TEXT_HEIGHT_SMALL = 20f

    var shouldShowF1Content = false
  }

  val notCompatibleList = HashSet<String>()
  val haveToBeWithMod = HashSet<String>()
  val allowOnHullsize = HashMap<ShipAPI.HullSize, Boolean>()
  val banShipList = HashSet<String>()
  var requireShield = false
  var canInstallOnCarrier = true

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
    var conflictId: String = id
    while (iterator.hasNext()) {
      val it = iterator.next() as String
      if (notCompatibleList.contains(it)) {
        shouldRemove = true
        conflictId = it
      }
    }

    //遍历必须安装的船插表，若任何一个未安装，返回false，移除自己
    //如果不满足其他安装条件，也移除自己
    shouldRemove = false
    iterator = haveToBeWithMod.iterator()
    while (iterator.hasNext()) {
      //如果自己是内置的，就不需要检查，上升流改内置了爆反装甲但是没有fsf插件
      if (!ship.variant.hasHullMod(iterator.next()) && !(ship.variant.nonBuiltInHullmods.contains(id))) {
        shouldRemove = true
      }
    }

    //自定义的安装条件
    if (!isApplicableToShip(ship)) {
      shouldRemove = true
    }

    //如果检测到需要remove，这里开始执行
    if (shouldRemove) {
      if (!ship.variant.nonBuiltInHullmods.contains(id)) {
        //如果本 mod 是 built-in且自己和自己冲突（意味着移除原因是缺少前置mod，比如上升流改作为非fsf舰船，内置了爆反），什么都不做
        if(conflictId == id) return
        //如果本 mod 是 built-in而冲突的不是，移除发生冲突的那个
        removeHullmodWithWarning(ship.variant, conflictId, id)
        return
      } else {
        //如果本 mod 不是是 built-in的话，移除本mod
        removeHullmodWithWarning(ship.variant, id, conflictId)
      }
      return
    }

    //在对话的预览界面，会出现战斗未开始，但是已经生成船的情况
    try {
      applyEffectsAfterShipCreationImpl(ship, id)
      if(isSMod(ship)){
        applySmodEffectsAfterShipCreationImpl(ship, ship.mutableStats, id)
      }

    } catch (e1: Exception) {
      if(ship == null){
        addDebugLog("Error in hullmod: $id at applyEffectsAfterShipCreationImpl: param @ship is null")
        return
      }
      addDebugLog("Error in hullmod: $id at applyEffectsAfterShipCreationImpl")
    }
  }

  /**
   * 对于使用listener的船插，在这里写加入listener的部分，如果船插被内置，有可能战斗开始时listener未被加入
   */
  override fun applyEffectsAfterShipAddedToCombatEngine(ship: ShipAPI, id: String) {
    super.applyEffectsAfterShipAddedToCombatEngine(ship, id)
  }

  override fun isApplicableToShip(ship: ShipAPI): Boolean {

    //检查是否能装无盾船上面
    if(requireShield && ship.shield == null){
      return false
    }

    //检测是否航母（拥有自由甲板）
    if((!canInstallOnCarrier && ship.hullSpec.builtInWings.size < ship.variant.computeNumFighterBays())){
      return false
    }

    //检测舰体尺寸
    if(allowOnHullsize[ship.hullSize] != true){
      return false
    }

    //遍历必须安装的船插表，若任何一个未安装（如果自己是内置的，就不需要检查，上升流改内置了爆反装甲但是没有fsf插件），返回false
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

    //检查是否能装无盾船上面
    if(requireShield && ship.shield == null){
      return aEP_DataTool.txt("must_have_shield")
    }

    //检测是否航母（拥有自由甲板）
    if(!canInstallOnCarrier && ship.hullSpec.builtInWings.size < ship.variant.computeNumFighterBays()){
      return aEP_DataTool.txt("must_have_no_free_deck")
    }

    //检测舰体尺寸
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
   * 包含一部分检测F1额外信息的代码，不要覆盖
   * 在船插还是物品的的时候，ship的参数可能为null
   * 默认为true，不然不显示描述
   **/
  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    if(Keyboard.isKeyDown(Keyboard.KEY_F1)){
      shouldShowF1Content = !shouldShowF1Content
    }
    return shouldAddDescriptionToTooltipImpl(hullSize, ship, isForModSpec)
  }


  /**
   * 使用这个
   **/
  open fun shouldAddDescriptionToTooltipImpl(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return  true
  }


  /**
   * 在船插还是物品的的时候，ship的参数可能为null
   **/
  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highlight = Misc.getHighlightColor()
    val negativeHighlight = Misc.getNegativeHighlightColor()

    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()

    val titleTextColor: Color = faction.color
    val factionColor: Color = faction.baseUIColor
    val factionDarkColor = faction.darkUIColor
    val factionBrightColor = faction.brightUIColor

    super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec)
    showIncompatible(tooltip)
  }


  /**
   * 使用这个
   */
  open fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {

  }

  open fun showIncompatible(tooltip: TooltipMakerAPI){
    val highLight = Misc.getNegativeHighlightColor()
    //显示不兼容插件
    tooltip.addPara(" %s "+ aEP_DataTool.txt("not_compatible") +" %s ", 8f, arrayOf(Color.red, highLight),
      aEP_ID.HULLMOD_POINT,
      showModName(notCompatibleList))
  }

  open fun showModName(list: Set<String>): String {
    val toReturn = StringBuffer()
    for (id in list.toTypedArray()) {
      if(Misc.getMod(id) !=null){
        toReturn.append('<')
        toReturn.append(Global.getSettings().getHullModSpec(id).displayName)
        toReturn.append('>')
        toReturn.append(' ')
      }
    }
    if(toReturn.isEmpty()) {
      toReturn.append("None")
    }
    return toReturn.toString()
  }

  open fun addPositivePara(tooltip: TooltipMakerAPI, mainTxtId: String ,data: Array<String>){
    val highLight = Misc.getHighlightColor()
    tooltip.addPara(" %s "+ aEP_DataTool.txt(mainTxtId), PARAGRAPH_PADDING_SMALL,
      arrayOf(Misc.getPositiveHighlightColor(), highLight),
      aEP_ID.HULLMOD_POINT,
      *data)
  }

  open fun addDoubleEdgePara(tooltip: TooltipMakerAPI, mainTxtId: String ,data: Array<String>){
    val highLight = Misc.getHighlightColor()
    tooltip.addPara(" %s "+ aEP_DataTool.txt(mainTxtId), PARAGRAPH_PADDING_SMALL, arrayOf(Misc.getHighlightColor(), highLight),
      aEP_ID.HULLMOD_POINT,
      *data)
  }

  open fun addNegativePara(tooltip: TooltipMakerAPI, mainTxtId: String ,data: Array<String>){
    val highLight = Misc.getHighlightColor()
    tooltip.addPara(" %s "+ aEP_DataTool.txt(mainTxtId), PARAGRAPH_PADDING_SMALL, arrayOf(Misc.getNegativeHighlightColor(), highLight),
      aEP_ID.HULLMOD_POINT,
      *data)
  }

  open fun addGrayPara(tooltip: TooltipMakerAPI, mainTxtId: String ,data: Array<String>){
    val gray = Misc.getGrayColor()
    tooltip.addPara(" %s "+ aEP_DataTool.txt(mainTxtId), PARAGRAPH_PADDING_SMALL, gray , Color.LIGHT_GRAY,
      aEP_ID.HULLMOD_POINT,
      *data)
  }


  open fun addSubBulletPara(tooltip: TooltipMakerAPI, mainTxtId: String ,data: Array<String>){
    val highLight = Misc.getHighlightColor()
    tooltip.addPara(aEP_ID.HULLMOD_BULLET+ aEP_DataTool.txt(mainTxtId), PARAGRAPH_PADDING_SMALL, arrayOf(highLight),
      *data)
  }

  /**
   * 使用这个。原版的Base类里面没有分离，s-mod的加成直接写在普通的applyEffect里面，这样很不好
   */
  open fun applySmodEffectsAfterShipCreationImpl(ship: ShipAPI, stats: MutableShipStatsAPI, id: String) {

  }

  override fun showInRefitScreenModPickerFor(ship: ShipAPI): Boolean {
    if(haveToBeWithMod.contains(aEP_SpecialHull.ID) && !ship.variant.hasHullMod(aEP_SpecialHull.ID)) return false
    return super.showInRefitScreenModPickerFor(ship)
  }


}