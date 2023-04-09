package data.missions

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.util.ListMap
import combat.util.aEP_Tool.Util.isNormalWeaponSlotType
import combat.util.aEP_Tool.Util.isNormalWeaponType
import java.awt.Color

class aEP_MissionUtils{

  companion object {
    var DEFAULT_WEAPON_DATA = ListMap<String>()
    var DEFAULT_WING_DATA = ListMap<String>()
    var DEFAULT_HULLMOD_DATA = ListMap<String>()
    var didChange = false
    var shouldChange = false

    @JvmStatic
    fun loadDefaultWeapon(list: List<WeaponSpecAPI>){
      for(spec in list){
        if(spec.hasTag(Tags.RESTRICTED)){
          DEFAULT_WEAPON_DATA.getList(spec.weaponId).add(Tags.RESTRICTED)
          continue
        }
      }
    }

    @JvmStatic
    fun loadDefaultWing(list: List<FighterWingSpecAPI>){
      for(spec in list){
        val list = DEFAULT_WING_DATA.getList(spec.id)
        var tag = Tags.RESTRICTED
        if( spec.hasTag(tag)){
          list.add(tag)
          continue
        }
      }
    }

    @JvmStatic
    fun loadDefaultHullMod(list: List<HullModSpecAPI>){
      for(spec in list){
        //本体没有hidden的变量，找个凑数的，作用只是占位，实际使用set/isHidden
        if(spec.isHidden){
          val list = DEFAULT_HULLMOD_DATA.getList(spec.id)
          list.add(Tags.THEME_HIDDEN)
          continue
        }
      }
    }

    @JvmStatic
    fun filterAllNonFactionalWeapons(factionAPI: FactionAPI){
      val setting = Global.getSettings()
      for ( hullModSpec in setting.allHullModSpecs) {
        if(factionAPI.knownHullMods.contains(hullModSpec.id)) continue
        hullModSpec.isHidden = true
      }
      for( weaponSpec in setting.allWeaponSpecs){
        if(factionAPI.knownWeapons.contains(weaponSpec.weaponId)) continue
        weaponSpec.tags.add(Tags.RESTRICTED)
      }
      for( wingSpec in setting.allFighterWingSpecs){
        if(factionAPI.knownFighters.contains(wingSpec.id)) continue
        wingSpec.tags.add(Tags.RESTRICTED)
      }


      didChange = true
    }

    @JvmStatic
    fun restore(){
      if(!didChange) return

      for(spec in Global.getSettings().allWeaponSpecs){
        //先检测，如果没有就不需要getList的了
        var tag = Tags.RESTRICTED
        if(spec.hasTag(tag)){
          val list = DEFAULT_WEAPON_DATA.getList(spec.weaponId)
          if(!list.contains(tag)){
            spec.tags.remove(tag)
          }
        }
      }
      for(spec in Global.getSettings().allFighterWingSpecs){
        var tag = Tags.RESTRICTED
        if(spec.hasTag(tag)) {
          val list = DEFAULT_WING_DATA.getList(spec.id)
          if (!list.contains(tag)) {
            spec.tags.remove(tag)
          }
        }
      }
      for(spec in Global.getSettings().allHullModSpecs){
        var tag = Tags.THEME_HIDDEN
        if(spec.hasTag(Tags.RESTRICTED)) {
          val list = DEFAULT_HULLMOD_DATA.getList(spec.id)
          if (spec.isHidden && !list.contains(tag)) {
            spec.isHidden = false
          }
        }
      }

      didChange = false
    }

    @JvmStatic
    fun disableUnknownWeapon(f: FactionAPI, ship: ShipAPI){
      //找到fsf不会的武器
      val shouldRemove: ArrayList<WeaponAPI?> = ArrayList()
      for (w in ship.getAllWeapons()) {
        if (!isNormalWeaponSlotType(w.slot, true)) continue
        if (!isNormalWeaponType(w, true)) continue
        if (f.knownWeapons.contains(w.id)) continue
        shouldRemove.add(w)
      }
      //禁用找到的武器
      for (weapon in shouldRemove) {
        weapon!!.disable(true)
      }

      //找到fsf不会的武器联队
      val shouldRemoveWing: ArrayList<FighterWingAPI?> = ArrayList()
      var i = 0
      for (wing in ship.getAllWings()) {
        i += 1
        if (i <= ship.getHullSpec().getBuiltInWings().size) continue
        if (f.knownFighters.contains(wing.wingId)) continue
        shouldRemoveWing.add(wing)
      }
      //扣掉联队的所有战机
      for (wing in shouldRemoveWing) {
        for (fighter in wing!!.wingMembers) {
          if (fighter.isAlive) {
            Global.getCombatEngine().removeEntity(fighter)
          }
        }
        wing.source.currRate = 0f
      }

      //找到fsf不会的舰船插件
      for (hullmodId in ship.getVariant().getNonBuiltInHullmods()) {
        if (f.knownHullMods.contains(hullmodId)) continue
        Global.getCombatEngine().applyDamage(
          ship,
          ship.getLocation(),
          ship.maxHitpoints / 20f,
          DamageType.ENERGY,
          0f, true, false, ship
        )
        Global.getCombatEngine().addFloatingText(
          ship.getLocation(),
          "No " + Global.getSettings().getHullModSpec(hullmodId).displayName,
          35f,
          Color.red, ship, 0f, 1f
        )
      }
    }


  }


}

