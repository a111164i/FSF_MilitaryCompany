package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize

class aEP_PingdingMainSwapHidden: aEP_BaseHullMod(){

  companion object{
    const val ID = "aEP_PingdingMainSwapHidden"
    const val ID_PLACE_HODLER1 = "aEP_PingdingMainSwap1"
    const val MAIN_SLOT = "MAIN"
    const val WEAPON_PREFIX = "aEP_cru_pingding_main"

    val SWAP_LIST = ArrayList<String>()
    init {
      SWAP_LIST.add("aEP_cru_pingding_main")
      SWAP_LIST.add("aEP_cru_pingding_main2")
      SWAP_LIST.add("aEP_cru_pingding_main3")
    }

  }

  override fun applyEffectsBeforeShipCreation(hullSize: HullSize?, stats: MutableShipStatsAPI, id: String?) {
    if (stats.entity == null) return

    for(i in 0 until SWAP_LIST.size){
      if(SWAP_LIST[i] == stats.variant.getWeaponId(MAIN_SLOT)){
        val n = i + 1
        this.spec.spriteName = "graphics/aEP_hullmods/aEP_pingding_main0$n.png"

      }
    }


    //占位插件不执行任何代码
    if (!id.equals(ID)) return

    //如果主武器位置上面没有武器，就加第一个
    if(stats.variant.getWeaponId(MAIN_SLOT) == null){
      stats.variant.addWeapon(MAIN_SLOT, SWAP_LIST[0])
    }

    //检测自己有没有占位插件插件，如果没有说明之前移除了，该替换武器了
    if(!stats.variant.hasHullMod(ID_PLACE_HODLER1)){
      //占位插件加回来
      stats.variant.addMod(ID_PLACE_HODLER1)
      //记录老武器，然后清理老武器，然后根据老武器list循环到下一个武器加上
      val curr = stats.variant.getWeaponId(MAIN_SLOT)
      curr?:run {
        stats.variant.addWeapon(MAIN_SLOT, SWAP_LIST[0])
        return
      }
      stats.variant.clearSlot(MAIN_SLOT)
      val new =  getNextWeaponId(curr)
      stats.variant.addWeapon(MAIN_SLOT, new)
    }

  }

  override fun getDisplayCategoryIndex(): Int {
    return 2
  }

  override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String) {
    ship?:return

    if (ship.originalOwner < 0) {
      //undo fix for weapons put in cargo
      if (Global.getSector() != null && Global.getSector().playerFleet != null && Global.getSector().playerFleet.cargo != null && Global.getSector().playerFleet.cargo.stacksCopy != null &&
        !Global.getSector().playerFleet.cargo.stacksCopy.isEmpty()
      ) {
        for (s in Global.getSector().playerFleet.cargo.stacksCopy) {
          if (s.isWeaponStack
            && s.weaponSpecIfWeapon.weaponId.startsWith(WEAPON_PREFIX)
          ) {
            Global.getSector().playerFleet.cargo.removeStack(s)
          }
        }
      }
    }
  }

  fun getNextWeaponId(curr:String) : String{
    if(curr != SWAP_LIST[SWAP_LIST.size-1]){
      for(i in 0 until SWAP_LIST.size){
        if(curr == SWAP_LIST[i]) return SWAP_LIST[i+1]
      }
    }else{
      return SWAP_LIST[0]
    }
    return  SWAP_LIST[0]
  }


}