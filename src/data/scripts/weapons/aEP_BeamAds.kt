package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import java.awt.Color

class aEP_BeamAds : BeamEffectPlugin {
  companion object {

    val REPAIR_COLOR = Color(250, 250, 178, 240)
    val REPAIR_COLOR2 = Color(250, 220, 70, 250)

  }

  var repairAmount = 6f
  var repairPercent = 0.005f
  private var didRepair = false

  init {
    val hlString = Global.getSettings().getWeaponSpec("aEP_ftr_ut_repair_beam").customPrimaryHL
    var i = 0
    for(num in hlString.split("|")){
      if(i == 0) repairAmount = num.toFloat()
      if(i == 1) repairPercent = num.replace("%","").toFloat()/100f
      i += 1
    }
  }


  override fun advance(amount: Float, engine: CombatEngineAPI, beam: BeamAPI) {

  }

}


