package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import campaign.aEP_OpPageManager;
import static campaign.AEP_OpPageManagerKt.getManager;

public class aEP_PageManager extends BaseCommandPlugin
{


  @Override
  public boolean execute(String ruleId, InteractionDialogAPI dialog, java.util.List<Misc.Token> params, java.util.Map<String, MemoryAPI> memoryMap) {

    switch (params.get(0).string) {
      case "check":
        return check(memoryMap);
      case "show":
        aEP_OpPageManager manager = getManager();
        manager.show(dialog);
        break;
      case "next":
        manager = getManager();
        manager.next();
        manager.show(dialog);
        break;
      case "previous":
        manager = getManager();
        manager.previous();
        manager.show(dialog);
        break;

    }


    return false;
  }

  private boolean check(java.util.Map<String, MemoryAPI> memoryMap) {
    if (getManager() == null) {
      return false;
    }

    String lastOptionId = memoryMap.get("local").getString("$option");
    return lastOptionId.startsWith("aEP_PageManager");
  }


}