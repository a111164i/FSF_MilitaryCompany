package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * 和原版的区别在于第一个参数决定了要不要清除当前老的选项
 */
public class aEP_AddOption extends BaseCommandPlugin {

  private static int shouldClear = 0;

  //AddSelector <order> <result variable> <text> <color> <min> <max>
  public boolean execute(String ruleId, final InteractionDialogAPI dialog, List<Misc.Token> params, final Map<String, MemoryAPI> memoryMap) {
    if (dialog == null) return false;

    shouldClear = params.get(0).getInt(memoryMap);
    String id = params.get(1).getString(memoryMap);
    String text = params.get(2).getString(memoryMap);

    OptionPanelAPI options = dialog.getOptionPanel();
    options.addOption(text, id);
    return true;
  }

  @Override
  public boolean doesCommandAddOptions() {
    return shouldClear > 0;
  }

  @Override
  public int getOptionOrder(List<Misc.Token> params, final Map<String, MemoryAPI> memoryMap) {
    int order = (int) params.get(0).getFloat(memoryMap);
    return order;
  }

}
