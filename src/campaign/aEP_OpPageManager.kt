package campaign

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import combat.util.aEP_Tool.Util.limitToTop
import java.util.ArrayList

class aEP_OpPageManager(allOpts: List<String>, num: Int, returnOpt: String) {

  /**
   * @param allOpts List<Array of [name, id, tooltip]>, by sequence
   * @param num option number per page
   * @param returnOpt return to where if choose nothing
   */
  private val allOpts: List<Array<String?>>
  private val returnOpt: String
  private val maxOptsPerPage: Int
  private var currPage: Int
  private val maxPage: Int
  fun show(dialog: InteractionDialogAPI) {
    dialog.optionPanel.clearOptions()
    var num = currPage * maxOptsPerPage
    var toNum = currPage * maxOptsPerPage + maxOptsPerPage
    if (allOpts.size - num < maxOptsPerPage) {
      toNum = allOpts.size
    }
    while (num < toNum) {
      //dialog.getTextPanel().addPara(num+"");
      dialog.optionPanel.addOption(allOpts[num][0], allOpts[num][1], allOpts[num][2])
      num ++
    }
    if (currPage > 0) {
      dialog.optionPanel.addOption("prev", "aEP_PageManager_previous")
    }
    if (currPage < maxPage - 1) {
      dialog.optionPanel.addOption("next", "aEP_PageManager_next")
    }

    //add return opt
    dialog.optionPanel.addOption("back", returnOpt)
  }

  fun next() {
    currPage = limitToTop((currPage + 1).toFloat(), maxPage.toFloat(), 0f).toInt()
  }

  fun previous() {
    currPage = limitToTop((currPage - 1).toFloat(), maxPage.toFloat(), 0f).toInt()
  }

  init {
    val opts: MutableList<Array<String?>> = ArrayList()
    var i = 0
    while (i < allOpts.size) {
      val option = arrayOfNulls<String>(3)
      var x = 0
      while (x < option.size) {
        option[x] = allOpts[i + x] as String
        x += 1
      }
      opts.add(option)
      i += option.size
    }
    this.allOpts = opts
    maxOptsPerPage = num
    currPage = 0
    maxPage = this.allOpts.size / maxOptsPerPage + 1
    this.returnOpt = returnOpt
    Global.getSector().memoryWithoutUpdate["\$aEP_OpPageManager"] = this
  }
}

fun getManager():  aEP_OpPageManager?{
  val toReturn = Global.getSector().memoryWithoutUpdate["\$aEP_OpPageManager"]
  return toReturn as aEP_OpPageManager?
}