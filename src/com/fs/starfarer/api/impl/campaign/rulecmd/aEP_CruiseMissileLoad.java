package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import combat.util.aEP_Tool;

import java.util.List;
import java.util.Map;

public class aEP_CruiseMissileLoad extends BaseCommandPlugin{

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        aEP_Tool.addDebugLog("dsd");
        dialog.showCargoPickerDialog("sd",
                "sd",
                "sds",
                true,
                200f,
                Global.getFactory().createCargo(false),
                null);
        return true;
    }
}
