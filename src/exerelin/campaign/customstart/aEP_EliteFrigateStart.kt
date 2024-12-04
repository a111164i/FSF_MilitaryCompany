package exerelin.campaign.customstart

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.rules.MemKeys
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.CharacterCreationData
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity
import com.fs.starfarer.api.impl.campaign.rulecmd.NGCAddStandardStartingScript
import exerelin.campaign.ExerelinSetupData
import exerelin.campaign.PlayerFactionStore
import exerelin.utilities.StringHelper

class aEP_EliteFrigateStart : CustomStart() {
  override fun execute(dialog: InteractionDialogAPI, memoryMap: Map<String, MemoryAPI>) {
    val data = memoryMap[MemKeys.LOCAL]!!["\$characterData"] as CharacterCreationData
    data.addScriptBeforeTimePass {
      Global.getSector().memoryWithoutUpdate["\$spacerStart"] = true
      //Global.getSector().getMemoryWithoutUpdate().set("$nex_startLocation", "nomios");
    }
    val vid = "kite_original_Stock"
    data.addStartingFleetMember(vid, FleetMemberType.SHIP)
    val temp = Global.getFactory().createFleetMember(FleetMemberType.SHIP, vid)
    val fuel = temp.fuelCapacity.toInt()
    data.startingCargo.addItems(CargoAPI.CargoItemType.RESOURCES, Commodities.CREW, 2f)
    data.startingCargo.addItems(CargoAPI.CargoItemType.RESOURCES, Commodities.SUPPLIES, 15f)
    data.startingCargo.addItems(CargoAPI.CargoItemType.RESOURCES, Commodities.FUEL, fuel.toFloat())
    AddRemoveCommodity.addFleetMemberGainText(temp.variant, dialog.textPanel)
    AddRemoveCommodity.addCommodityGainText(Commodities.CREW, 2, dialog.textPanel)
    AddRemoveCommodity.addCommodityGainText(Commodities.SUPPLIES, 15, dialog.textPanel)
    AddRemoveCommodity.addCommodityGainText(Commodities.FUEL, fuel, dialog.textPanel)
    data.startingCargo.credits.add(2000f)
    AddRemoveCommodity.addCreditsGainText(2000, dialog.textPanel)
    val stats = data.person.stats
    stats.addPoints(1)
    val tempFleet = FleetFactoryV3.createEmptyFleet(
      PlayerFactionStore.getPlayerFactionIdNGC(), FleetTypes.PATROL_SMALL, null
    )
    tempFleet.fleetData.addFleetMember(temp)
    tempFleet.fleetData.setFlagship(temp)
    temp.captain = data.person
    temp.repairTracker.cr = 0.7f
    tempFleet.fleetData.setSyncNeeded()
    tempFleet.fleetData.syncIfNeeded()
    tempFleet.forceSync()

    // enforce normal difficulty
    data.difficulty = "normal"
    ExerelinSetupData.getInstance().easyMode = false
    PlayerFactionStore.setPlayerFactionIdNGC(Factions.PLAYER)
    ExerelinSetupData.getInstance().freeStart = true
    data.addScript {
      val fleet = Global.getSector().playerFleet
      NGCAddStandardStartingScript.adjustStartingHulls(fleet)
      fleet.fleetData.ensureHasFlagship()
      for (member in fleet.fleetData.membersListCopy) {
        val max = member.repairTracker.maxCR
        member.repairTracker.cr = max
      }
      fleet.fleetData.setSyncNeeded()

      // add spacer obligation if not already set in
      if (!ExerelinSetupData.getInstance().spacerObligation) {
        Nex_SpacerObligation()
      }
    }
    dialog.visualPanel.showFleetInfo(
      StringHelper.getString("exerelin_ngc", "playerFleet", true), tempFleet, null, null
    )
    dialog.optionPanel.addOption(StringHelper.getString("done", true), "nex_NGCDone")
    dialog.optionPanel.addOption(StringHelper.getString("back", true), "nex_NGCStartBack")
  }
}

