package data.scripts.campaign.intel

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.listeners.ColonyCrisesSetupListener
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.intel.events.*
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.EventStageData
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.HAERandomEventData
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.Stage
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import data.scripts.utils.aEP_DataTool.txt
import data.scripts.utils.aEP_ID.Companion.FACTION_ID_FSF_ADV
import java.awt.Color


/**
 * 在modPlugin里面的onGameLoad方法里调用 ensureRegistered()
 * 玩家建立殖民地到了该触发危机的时候，会自动被游戏调用 finishedAddingCrisisFactors，将cause和factor添加到HostileActivityEventIntel
 */
class FSFHostileActivityCrisisSetup : ColonyCrisesSetupListener {

    // HostileActivityEventIntel 是单例，在这个tab中维护所有的Crisis的cause/factor，每次触发危机event时，只会触发一个event
    override fun finishedAddingCrisisFactors(intel: HostileActivityEventIntel) {
        val factor = FSFHostileActivityFactor(intel)
        val cause = FSFHostileActivityCause(intel)
        intel.addActivity(factor, cause)
    }

    companion object {
        @JvmStatic
        fun ensureRegistered() {
            val manager = Global.getSector().listenerManager
            if (!manager.hasListenerOfClass(FSFHostileActivityCrisisSetup::class.java)) {
                manager.addListener(FSFHostileActivityCrisisSetup())
            }
        }
    }
}


/**
 *  Factor负责在提示栏显示 FSF Crisis 的描述、计算每月进度贡献、以及决定是否能触发随机危机（rollEvent/fireEvent）。
 *  在 HostileActivityEvent 每次更新进度、进行威胁评估或滚动事件时，都会查找所有注册的 Factor。
 *  因此要保证它只依赖 intel 和自身因子列表的状态，避免每帧重建。
 */
private class FSFHostileActivityFactor(intel: HostileActivityEventIntel) : BaseHostileActivityFactor(intel) {

    private val factionColor: Color
        get() = Global.getSector().getFaction(FACTION_ID_FSF_ADV)?.baseUIColor ?: Misc.getHighlightColor()


    override fun getDesc(intel: BaseEventIntel) = txt("aEP_HAE_FSF_Event")

    override fun getDescColor(intel: BaseEventIntel): Color {
        return if (getProgress(intel) > 0) factionColor else Misc.getGrayColor()
    }

    override fun getProgressStr(intel: BaseEventIntel?): String {
        val progression = getProgress(intel)
        if(progression <= 0) return ""
        else if(progression <= 35) return ""
        else if(progression <= 75) return ""
        return ""
    }


    override fun getEventFrequency(intel: HostileActivityEventIntel, stage: EventStageData): Float {
        return if (stage.id === Stage.HA_EVENT) 10f else 0f
    }

    /**
     * HostileActivityEvent 在进入 HA_EVENT 阶段且满足触发条件时
     * 会依次调用各 Factor 的 rollEvent 方法，上传需要被roll的事件
     * 在data.custom里塞入必要数据，供后续传入 fireEvent 使用。
     * 不一定被roll中，如果没roll中就不会调用 fireEvent
     */
    override fun rollEvent(intel: HostileActivityEventIntel, stage: EventStageData) {
        val data = HAERandomEventData(this, stage)
        stage.rollData = data
        // player sees a “potential crisis” intel update. No world effects occur here.
        intel.sendUpdateIfPlayerHasIntel(data, false)
    }

    /**
     * 在rollEvent中上传自己event的信息
     * 如果没有被roll中，就不会被调用fireEvent
     * 如果roll中了，就被调用fireEvent
     * 并且stage.data中存着之前塞进HAERandomEventData.Custom的必要数据
     * 如果成功触发事件返回true
     */
    override fun fireEvent(intel: HostileActivityEventIntel, stage: EventStageData): Boolean {
        return false
    }

    override fun addBulletPointForEvent(
        intel: HostileActivityEventIntel,
        stage: EventStageData,
        info: TooltipMakerAPI,
        mode: com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode,
        isUpdate: Boolean,
        tc: Color,
        initPad: Float
    ) {
        info.addPara("FSF-affiliated sympathizers are preparing a propaganda push against your colonies.", initPad, tc, Misc.getHighlightColor())
    }

    override fun addBulletPointForEventReset(
        intel: HostileActivityEventIntel,
        stage: EventStageData,
        info: TooltipMakerAPI,
        mode: com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode,
        isUpdate: Boolean,
        tc: Color,
        initPad: Float
    ) {
        info.addPara("FSF infiltration is temporarily contained.", initPad, tc)
    }

    override fun addStageDescriptionForEvent(intel: HostileActivityEventIntel, stage: EventStageData, info: TooltipMakerAPI) {
        val small = 8f
        info.addPara("The FSF is quietly agitating for influence on your colonies and may try to sway key local leaders.", small, factionColor, "FSF")
        stage.beginResetReqList(info, true, "crisis", 10f)
        info.addPara("Reduce hostile activity progress to avoid unrest and exposure.", 0f)
        stage.endResetReqList(info, false, "crisis", -1, -1)
        addBorder(info, factionColor)
    }

    override fun getEventStageIcon(intel: HostileActivityEventIntel, stage: EventStageData): String? {
        return Global.getSector().getFaction(FACTION_ID_FSF_ADV)?.crest
    }

    override fun getStageTooltipImpl(intel: HostileActivityEventIntel, stage: EventStageData): TooltipCreator? {
       if(stage.id === Stage.HA_EVENT){
           return getDefaultEventTooltip(
               "FSF influence campaign", intel, stage)
       }
        return getDefaultEventTooltip("FSF influence campaign", intel, stage)
    }

    override fun getNameForThreatList(first: Boolean) = "FSF influence"

    override fun getNameColorForThreatList() = factionColor

    override fun getEventStageSound(data: HAERandomEventData) = "colony_threat"
}

private class FSFHostileActivityCause(intel: HostileActivityEventIntel) : BaseHostileActivityCause2(intel) {

    // Cause 是 Factor 内部的粒度单位，HostileActivityEventIntel 会在 getMonthlyProgress() 时遍历所有 Cause，
    // 收集它们的 getProgress() 结果加总进度条。它同时提供系统级别的 getMagnitudeContribution 用于地图上显示的威胁强度。
    // 每当移除 Factor 时，所有 Cause 都会随之被移除；若希望动态开启/关闭某个子逻辑，可修改 Cause 的 shouldShow/getProgress。

    // 当 HostileActivity 每个月计算进度时会调用这个方法，它累加玩家所有殖民地的规模，用于进度条增长。
    override fun getProgress(): Int {
        return Misc.getPlayerMarkets(false).fold(0) { acc, market -> acc + market.size }
    }

    override fun getDesc() = "Player colonies under FSF outreach"

    override fun getTooltip(): TooltipCreator {
        return object : BaseFactorTooltip() {
            override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any?) {
                tooltip.addPara("FSF sentiment in your colonies grows with each population increase and open contact.", 0f)
            }
        }
    }

    // 系统危险度说明：每次 HostileActivity 评估各系统威胁时调用，用该系统内玩家殖民地规模计算 FSF 的基础危险值。
    override fun getMagnitudeContribution(system: StarSystemAPI): Float {
        val total = Misc.getMarketsInLocation(system, Factions.PLAYER).fold(0f) { acc, market -> acc + market.size }
        return (total / 18f).coerceAtMost(0.3f)
    }
}
