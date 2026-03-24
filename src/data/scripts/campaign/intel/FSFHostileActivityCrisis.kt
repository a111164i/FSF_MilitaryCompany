package data.scripts.campaign.intel

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.listeners.ColonyCrisesSetupListener
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.EventStageData
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.HAERandomEventData
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.Stage
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import java.awt.Color

private const val FACTION_ID = "aEP_FSF"

class FSFHostileActivityCrisisSetup : ColonyCrisesSetupListener {

    // 这个回调会在所有默认的“殖民危机”因素都添加完后由 HostileActivityEventIntel 调用，
    // 参数 intel 是正在构建的 HostileActivityEventIntel 实例，我们在这里注册 FSF 的因子和原因
    override fun finishedAddingCrisisFactors(intel: HostileActivityEventIntel) {
        val factor = FSFHostileActivityFactor(intel)
        val cause = FSFHostileActivityCause(intel)
        intel.addActivity(factor, cause)
    }

    companion object {
        // 这个方法由 FSFModPlugin 在 存档读取/开新档时时调用，确保监听器在listenerManager存在一个实例
        // 保证在 HostileActivityEvent 更新进度条前捕捉到 finishedAddingCrisisFactors 回调
        @JvmStatic
        fun ensureRegistered() {
            val manager = Global.getSector().listenerManager
            if (!manager.hasListenerOfClass(FSFHostileActivityCrisisSetup::class.java)) {
                manager.addListener(FSFHostileActivityCrisisSetup())
            }
        }
    }
}

private class FSFHostileActivityFactor(intel: HostileActivityEventIntel) : BaseHostileActivityFactor(intel) {

    // 这个 Factor 会在 HostileActivityEventIntel 构建过程中、由 finishedAddingCrisisFactors 注入。
    // 它负责在提示栏显示 FSF 的描述、计算每月进度贡献、以及决定是否能触发随机危机（rollEvent/fireEvent）。
    // 在 HostileActivityEvent 每次更新进度、进行威胁评估或滚动事件时，都会查找所有注册的 Factor。
    // 因此要保证它只依赖 intel 和自身因子列表的状态，避免每帧重建。

    private val factionColor: Color
        get() = Global.getSector().getFaction(FACTION_ID)?.baseUIColor ?: Misc.getHighlightColor()

    override fun getDesc(intel: BaseEventIntel) = "FSF outreach"

    override fun getDescColor(intel: BaseEventIntel): Color {
        return if (getProgress(intel) > 0) factionColor else Misc.getGrayColor()
    }

    override fun getEventFrequency(intel: HostileActivityEventIntel, stage: EventStageData): Float {
        return if (stage.id === Stage.HA_EVENT) 15f else 0f
    }

    override fun rollEvent(intel: HostileActivityEventIntel, stage: EventStageData) {
        val data = HAERandomEventData(this, stage)
        stage.rollData = data
        intel.sendUpdateIfPlayerHasIntel(data, false)
    }

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
        return Global.getSector().getFaction(FACTION_ID)?.crest
    }

    override fun getStageTooltipImpl(intel: HostileActivityEventIntel, stage: EventStageData): TooltipCreator? {
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
