package app.aaps.ui.compose.overview.graphs

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.overview.AuditorStateProvider
import app.aaps.core.interfaces.overview.AuditorDisplayState
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.objects.extensions.convertedToAbsolute
import java.time.LocalDate
import java.time.ZoneId
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.displayText
import app.aaps.core.objects.extensions.round
import app.aaps.core.ui.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import app.aaps.core.utils.MidnightUtils
import kotlinx.coroutines.flow.map
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.data.model.SC
import app.aaps.core.interfaces.pump.PumpRate
import javax.inject.Inject

/**
 * ViewModel for Overview graphs (Compose/Vico version).
 *
 * Architecture: Independent Series Updates
 * - Each series (BG readings, bucketed, IOB, COB, etc.) has its own StateFlow
 * - UI collects each flow separately
 * - Only the changed series triggers recomposition
 * - Time range is derived from all series (recalculates as data arrives)
 *
 * Workers emit to cache flows → ViewModel exposes flows → UI collects independently
 */

/**
 * Static chart configuration (doesn't change during graph lifetime)
 */
data class ChartConfig(
    val highMark: Double,
    val lowMark: Double
)

/**
 * UI state for BG info section display
 */
@Immutable
data class BgInfoUiState(
    val bgInfo: BgInfoData?,
    val timeAgoText: String
)

/**
 * UI state for IOB display
 */
@Immutable
data class IobUiState(
    val text: String = "",
    val iobTotal: Double = 0.0
)

/**
 * UI state for COB display
 */
@Immutable
data class CobUiState(
    val text: String = "",
    val carbsReq: Int = 0,
    val cobValue: Double = 0.0
)

/**
 * UI state for sensitivity / autosens display
 */
@Immutable
data class SensitivityUiState(
    val asText: String = "",
    val isfFrom: String = "",
    val isfTo: String = "",
    val dialogText: String = "",
    val ratio: Double = 1.0,
    val isEnabled: Boolean = true,
    val hasData: Boolean = false
)

@Immutable
data class AutomationEventData(val id: String, val title: String)

data class ModesUiState(
    val events: List<AutomationEventData> = emptyList()
)

/**
 * UI state for APS Pulse / rate panel
 */
@Immutable
data class PulseUiState(
    val titleText: String = "",
    val summaryText: String = "",
    val metaText: String = "",
    val hintText: String = "",
    val isHypoRisk: Boolean = false
)

/**
 * UI state for Time In Range panel
 */
@Immutable
data class TirUiState(
    val veryLow: Float = 0f,
    val low: Float = 0f,
    val inRange: Float = 0f,
    val high: Float = 0f,
    val veryHigh: Float = 0f,
    val readingCount: Int = 0,
    val avgMgDl: Float = 0f,
    val a1c: Float = 0f
)

/**
 * UI state for the compact status panel (top-right overlay).
 */
@Immutable
data class StatusPanelUiState(
    val stepsText: String = "--",
    val hrText: String = "--",
    val lastSmbTime: String = "--:--",
    val lastSmbAmount: String = "--",
    val basalPctText: String = "--",
    val basalRateText: String = "--",
    val iobText: String = "--"
)

data class VicoChartLook(
    val bgReadingTintKey: String,
    val chartBackdropKey: String,
)

@HiltViewModel(assistedFactory = GraphViewModel.Factory::class)
@Stable
class GraphViewModel @AssistedInject constructor(
    @Assisted cache: OverviewDataCache,
    private val graphConfigRepository: GraphConfigRepository,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val iobCobCalculator: IobCobCalculator,
    private val decimalFormatter: DecimalFormatter,
    private val loop: Loop,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val profileUtil: ProfileUtil,
    private val activePlugin: ActivePlugin,
    private val automation: Automation,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val auditorStateProvider: AuditorStateProvider
) : ViewModel() {

    @Inject lateinit var ch: ConcentrationHelper

    @AssistedFactory
    interface Factory {

        fun create(cache: OverviewDataCache): GraphViewModel
    }

    // Chart config - updates when high/low mark preferences change
    private val _chartConfigFlow = MutableStateFlow(
        ChartConfig(
            highMark = preferences.get(UnitDoubleKey.OverviewHighMark),
            lowMark = preferences.get(UnitDoubleKey.OverviewLowMark)
        )
    )
    val chartConfigFlow: StateFlow<ChartConfig> = _chartConfigFlow.asStateFlow()

    /** Drives BG Y-axis label recomposition when the user switches mg/dl ↔ mmol in General. */
    val generalUnits: StateFlow<String> = preferences.observe(StringKey.GeneralUnits)

    private val _vicoChartLook = MutableStateFlow(
        VicoChartLook(
            bgReadingTintKey = preferences.get(StringKey.OverviewVicoBgReadingTint),
            chartBackdropKey = preferences.get(StringKey.OverviewVicoChartBackdrop),
        )
    )
    val vicoChartLookFlow: StateFlow<VicoChartLook> = _vicoChartLook.asStateFlow()

    init {
        // Update chart config when high/low mark preferences change
        // drop(1) skips the initial emission (already set in field initializer)
        preferences.observe(UnitDoubleKey.OverviewHighMark)
            .drop(1)
            .onEach { highMark -> _chartConfigFlow.update { it.copy(highMark = highMark) } }
            .launchIn(viewModelScope)
        preferences.observe(UnitDoubleKey.OverviewLowMark)
            .drop(1)
            .onEach { lowMark -> _chartConfigFlow.update { it.copy(lowMark = lowMark) } }
            .launchIn(viewModelScope)
        preferences.observe(StringKey.OverviewVicoBgReadingTint)
            .drop(1)
            .onEach { v -> _vicoChartLook.update { it.copy(bgReadingTintKey = v) } }
            .launchIn(viewModelScope)
        preferences.observe(StringKey.OverviewVicoChartBackdrop)
            .drop(1)
            .onEach { v -> _vicoChartLook.update { it.copy(chartBackdropKey = v) } }
            .launchIn(viewModelScope)
    }

    // Graph configuration (which series on which graph)
    val graphConfigFlow: StateFlow<GraphConfig> = graphConfigRepository.graphConfigFlow

    fun updateGraphConfig(config: GraphConfig) = graphConfigRepository.update(config)

    /**
     * Converts a BG value from mg/dL (DB / [BgDataPoint.value]) to the Y coordinate used on the chart.
     * Uses [ProfileUtil.units] (same source as [app.aaps.core.graph.data.GlucoseValueDataPoint.getY]) so the
     * graph tracks General → Units even if a pref snapshot in Compose lags by a frame.
     */
    fun glucoseMgdlToChartY(mgdl: Double): Double =
        profileUtil.fromMgdlToUnits(mgdl, profileUtil.units)

    /** Inverse of [glucoseMgdlToChartY] (e.g. SMB fallback interpolation among mg/dL points). */
    fun glucoseDisplayYToMgdl(displayY: Double): Double =
        profileUtil.convertToMgdl(displayY, profileUtil.units)

    /**
     * Formats a Y tick on the BG chart when Y is already in **display units** (same space as legacy GraphView).
     * Reads [ProfileUtil.units] on each call so axis labels match the setting immediately.
     */
    fun formatBgChartAxisTick(chartYInDisplayUnits: Double): String =
        when (profileUtil.units) {
            GlucoseUnit.MMOL -> decimalFormatter.to1Decimal(chartYInDisplayUnits)
            GlucoseUnit.MGDL -> decimalFormatter.to0Decimal(chartYInDisplayUnits)
        }

    /** ~48 mg/dL grid spacing on the dashboard BG axis, in current display units. */
    fun chartBgSoftAxisStep(): Double =
        if (profileUtil.units == GlucoseUnit.MGDL) 48.0 else 48.0 * Constants.MGDL_TO_MMOLL

    /**
     * Formats a vertical label when the value is still in **mg/dL** (e.g. Canvas dashboard stub axis from raw points).
     */
    fun formatBgAxisLabelFromMgdl(mgdlY: Double): String =
        formatBgChartAxisTick(glucoseMgdlToChartY(mgdlY))

    // Individual series flows - each can trigger independent recomposition
    val bgReadingsFlow: StateFlow<List<BgDataPoint>> = cache.bgReadingsFlow
    val bucketedDataFlow: StateFlow<List<BgDataPoint>> = cache.bucketedDataFlow
    val predictionsFlow: StateFlow<List<BgDataPoint>> = cache.predictionsFlow

    // Secondary graph flows
    val iobGraphFlow = cache.iobGraphFlow
    val absIobGraphFlow = cache.absIobGraphFlow
    val cobGraphFlow = cache.cobGraphFlow
    val activityGraphFlow = cache.activityGraphFlow
    val bgiGraphFlow = cache.bgiGraphFlow
    val deviationsGraphFlow = cache.deviationsGraphFlow
    val ratioGraphFlow = cache.ratioGraphFlow
    val devSlopeGraphFlow = cache.devSlopeGraphFlow
    val varSensGraphFlow = cache.varSensGraphFlow
    val heartRateGraphFlow = cache.heartRateGraphFlow
    val stepsGraphFlow = cache.stepsGraphFlow
    val iobThGraphFlow = cache.iobThGraphFlow
    val finalIsfGraphFlow = cache.finalIsfGraphFlow
    val acceIsfGraphFlow = cache.acceIsfGraphFlow
    val bgIsfGraphFlow = cache.bgIsfGraphFlow
    val ppIsfGraphFlow = cache.ppIsfGraphFlow
    val duraIsfGraphFlow = cache.duraIsfGraphFlow
    val treatmentGraphFlow = cache.treatmentGraphFlow
    val epsGraphFlow = cache.epsGraphFlow
    val basalGraphFlow = cache.basalGraphFlow
    val targetLineFlow = cache.targetLineFlow
    val runningModeGraphFlow = cache.runningModeGraphFlow

    // NSClient status (pump/openAPS/uploader from Nightscout)
    val nsClientStatusFlow = cache.nsClientStatusFlow

    // =========================================================================
    // BG Info Section (Overview info display)
    // =========================================================================

    // Ticker flow for periodic updates (every 30 seconds) — used for timeAgo text and now line
    private val ticker30s = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    /** Current time updated every 30s — use as key for now line position */
    val nowTimestamp: StateFlow<Long> = ticker30s.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = System.currentTimeMillis()
    )

    // BG info UI state - combines bgInfo with periodic timeAgo updates
    val bgInfoState: StateFlow<BgInfoUiState> = combine(
        cache.bgInfoFlow,
        ticker30s
    ) { bgInfo, _ ->
        BgInfoUiState(
            bgInfo = bgInfo,
            timeAgoText = dateUtil.minAgo(rh, bgInfo?.timestamp)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BgInfoUiState(bgInfo = null, timeAgoText = "")
    )

    // =========================================================================
    // IOB / COB current values (updated every 2.5 minutes)
    // =========================================================================

    private val iobCobTicker = flow {
        while (true) {
            emit(Unit)
            delay(150_000L) // 2.5 minutes
        }
    }

    val iobUiState: StateFlow<IobUiState> = iobCobTicker.combine(cache.iobGraphFlow) { _, _ ->
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val total = bolusIob.iob + basalIob.basaliob
        IobUiState(
            text = rh.gs(R.string.format_insulin_units, total),
            iobTotal = total
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IobUiState()
    )

    val cobUiState: StateFlow<CobUiState> = iobCobTicker.combine(cache.cobGraphFlow) { _, _ ->
        val cobInfo = iobCobCalculator.getCobInfo("GraphViewModel COB")
        var cobText = cobInfo.displayText(rh, decimalFormatter)
            ?: rh.gs(R.string.value_unavailable_short)
        var carbsReq = 0

        val constraintsProcessed = loop.lastRun?.constraintsProcessed
        val lastRun = loop.lastRun
        if (config.APS && constraintsProcessed != null && lastRun != null) {
            if (constraintsProcessed.carbsReq > 0) {
                val lastCarbsTime = persistenceLayer.getNewestCarbs()?.timestamp ?: 0L
                if (lastCarbsTime < lastRun.lastAPSRun) {
                    cobText += " ${constraintsProcessed.carbsReq}${rh.gs(R.string.required)}"
                }
                carbsReq = constraintsProcessed.carbsReq
            }
        }

        CobUiState(text = cobText, carbsReq = carbsReq, cobValue = cobInfo.displayCob ?: 0.0)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CobUiState()
    )

    // =========================================================================
    // Sensitivity / Autosens (updated every 2.5 minutes with IOB/COB)
    // =========================================================================

    val sensitivityUiState: StateFlow<SensitivityUiState> = iobCobTicker.combine(cache.iobGraphFlow) { _, _ ->
        buildSensitivityUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SensitivityUiState()
    )

    private suspend fun buildSensitivityUiState(): SensitivityUiState {
        val lastAutosensData = iobCobCalculator.ads.getLastAutosensData("Overview", aapsLogger, dateUtil)
        val lastAutosensRatio = lastAutosensData?.autosensResult?.ratio
        val lastAutosensPercent = lastAutosensRatio?.let { it * 100 }

        val isEnabled = if (config.AAPSCLIENT) preferences.get(BooleanNonKey.AutosensUsedOnMainPhone)
        else constraintChecker.isAutosensModeEnabled().value()

        val profile = profileFunction.getProfile()
        val request = loop.lastRun?.request
        val isfMgdl = profile?.getProfileIsfMgdl()
        val variableSens =
            if (config.APS) request?.variableSens ?: 0.0
            else if (config.AAPSCLIENT) processedDeviceStatusData.getAPSResult()?.variableSens ?: 0.0
            else 0.0
        val ratioUsed = request?.autosensResult?.ratio ?: 1.0
        val units = profileFunction.getUnits()

        var asText = ""
        var isfFrom = ""
        var isfTo = ""
        val dialogText = ArrayList<String>()

        if (variableSens != isfMgdl && variableSens != 0.0 && isfMgdl != null) {
            // Variable ISF branch — hide "AS: 100%" from overview when ratio is exactly 100%
            lastAutosensPercent?.let {
                if (it != 100.0)
                    asText = rh.gs(R.string.autosens_short, it)
                dialogText.add(rh.gs(R.string.autosens_long, it))
            }
            isfFrom = String.format(Locale.getDefault(), "%1$.1f", profileUtil.fromMgdlToUnits(isfMgdl, units))
            isfTo = String.format(Locale.getDefault(), "%1$.1f", profileUtil.fromMgdlToUnits(variableSens, units))
            if (ratioUsed != 1.0 && ratioUsed != lastAutosensRatio)
                dialogText.add(rh.gs(R.string.algorithm_long, ratioUsed * 100))
            val isfForCarbs = profile.getIsfMgdlForCarbs(dateUtil.now(), "Overview", config, processedDeviceStatusData)
            dialogText.add(rh.gs(R.string.isf_for_carbs, profileUtil.fromMgdlToUnits(isfForCarbs, units)))
            if (config.APS) {
                activePlugin.activeAPS?.getSensitivityOverviewString()?.let { dialogText.add(it) }
            }
        } else {
            // Standard autosens-only branch — skip when ratio is exactly 100%
            lastAutosensData?.let {
                val pct = it.autosensResult.ratio * 100
                if (pct != 100.0)
                    asText = rh.gs(R.string.autosens_short, pct)
            }
        }

        return SensitivityUiState(
            asText = asText,
            isfFrom = isfFrom,
            isfTo = isfTo,
            dialogText = dialogText.joinToString("\n"),
            ratio = lastAutosensRatio ?: 1.0,
            isEnabled = isEnabled,
            hasData = lastAutosensData != null
        )
    }

    // Derived time range from actual data (recalculates as series arrive)
    // When PREDICTIONS overlay is enabled, extends into the future to fit prediction points;
    // otherwise clamps to toTime so the x-axis doesn't reserve empty future space.
    val derivedTimeRange: StateFlow<Pair<Long, Long>?> = combine(
        cache.bgReadingsFlow,
        cache.bucketedDataFlow,
        cache.predictionsFlow,
        cache.timeRangeFlow,
        graphConfigFlow
    ) { bgReadings, bucketedData, predictions, cacheTimeRange, graphConfig ->
        val showPredictions = SeriesType.PREDICTIONS in graphConfig.bgOverlays
        val effectivePredictions = if (showPredictions) predictions else emptyList()
        val allTimestamps = (bgReadings + bucketedData + effectivePredictions).map { it.timestamp }

        if (allTimestamps.isEmpty()) {
            cacheTimeRange?.let {
                val upper = if (showPredictions) {
                    val minFutureEnd = System.currentTimeMillis() +
                        T.hours(Constants.PREDICTION_GRAPH_MIN_HOURS.toLong()).msecs()
                    maxOf(it.endTime, minFutureEnd)
                } else {
                    it.toTime
                }
                Pair(it.fromTime, upper)
            } ?: run {
                // Clean DB: no data and no cached range (worker never ran) — fall back to the
                // default window so the axis frame still renders instead of staying blank.
                val now = dateUtil.now()
                Pair(now - Constants.GRAPH_TIME_RANGE_HOURS * 3600_000L, now)
            }
        } else {
            val minTime = allTimestamps.minOrNull() ?: return@combine null
            val maxTime = allTimestamps.maxOrNull() ?: return@combine null
            val cacheUpper = cacheTimeRange?.let { if (showPredictions) it.endTime else it.toTime }
            // // Also consider endTime from cache (may extend beyond prediction points)
            // val effectiveMax = if (cacheTimeRange != null) maxOf(maxTime, cacheTimeRange.endTime) else maxTime
            // Pair(minTime, effectiveMax)
            // Force the graph to end no later than 1 hours from now
            val oneHourFromNow = System.currentTimeMillis() + 60 * 60 * 1000L * 1
            var effectiveMax = if (cacheUpper != null) maxOf(maxTime, cacheUpper) else maxTime
            if (showPredictions && effectivePredictions.isNotEmpty()) {
                val minFutureEnd = System.currentTimeMillis() +
                    T.hours(Constants.PREDICTION_GRAPH_MIN_HOURS.toLong()).msecs()
                effectiveMax = maxOf(effectiveMax, minFutureEnd).coerceAtMost(oneHourFromNow) // This is the line that caps the future view
            }
            Pair(minTime, effectiveMax)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
        aapsLogger.debug(LTag.UI, "GraphViewModel initialized - exposing independent series flows")
    }


    val modesFlow: StateFlow<ModesUiState> = ticker30s.map {
        ModesUiState(
            events = automation.events.value
                .filter { it.isEnabled }
                .take(10)
                .map { AutomationEventData(id = it.id, title = it.title) }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ModesUiState()
    )

    val pulseFlow: StateFlow<PulseUiState> = ticker30s.map {
        buildPulseUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PulseUiState()
    )

    fun runAutomationEvent(eventId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val event = automation.events.value.firstOrNull { it.id == eventId } ?: return@launch
            automation.processEvent(event)
        }
    }

    val tirFlow: StateFlow<TirUiState> = ticker30s.map {
        buildTirUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TirUiState()
    )

    val statusPanelFlow: StateFlow<StatusPanelUiState> = ticker30s.map {
        buildStatusPanelUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatusPanelUiState()
    )

    val auditorStateFlow: StateFlow<AuditorDisplayState> = auditorStateProvider.displayStateFlow

    /** Reactive: true when the active APS plugin is AutoISF. Re-checked on every 30 s tick. */
    val isAutoIsfActiveFlow: StateFlow<Boolean> = ticker30s.map {
        activePlugin.activeAPS?.algorithm == APSResult.Algorithm.AUTO_ISF
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), activePlugin.activeAPS?.algorithm == APSResult.Algorithm.AUTO_ISF)

    /** Reactive: true when the active APS plugin is AIMI. Re-checked on every 30 s tick. */
    val isAIMIActiveFlow: StateFlow<Boolean> = ticker30s.map {
        activePlugin.activeAPS?.algorithm == APSResult.Algorithm.AIMI
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), activePlugin.activeAPS?.algorithm == APSResult.Algorithm.AIMI)

    @Volatile var lastInteractionMs: Long = 0L
        private set

    fun onGraphInteraction() {
        preferences.put(BooleanNonKey.ObjectivesScaleUsed, true)
        lastInteractionMs = System.currentTimeMillis()
    }

    override fun onCleared() {
        super.onCleared()
        aapsLogger.debug(LTag.UI, "GraphViewModel cleared")
    }

    private suspend fun buildTirUiState(): TirUiState {
        val end = System.currentTimeMillis()
        val start = end - 24 * 60 * 60 * 1000L
        val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, ascending = true)
        if (readings.isEmpty()) return TirUiState()
        var veryLow = 0; var low = 0; var inRange = 0; var high = 0; var veryHigh = 0
        for (gv in readings) {
            when {
                gv.value < 54.0  -> veryLow++
                gv.value < 70.0  -> low++
                gv.value <= 180.0 -> inRange++
                gv.value <= 250.0 -> high++
                else             -> veryHigh++
            }
        }
        val total = readings.size.toFloat()
        val avgMgDl = readings.map { it.value }.average().toFloat()
        val a1c = ((avgMgDl + 46.7) / 28.7).toFloat()
        return TirUiState(
            veryLow  = veryLow  / total * 100f,
            low      = low      / total * 100f,
            inRange  = inRange  / total * 100f,
            high     = high     / total * 100f,
            veryHigh = veryHigh / total * 100f,
            readingCount = readings.size,
            avgMgDl  = avgMgDl,
            a1c      = a1c
        )
    }


    private fun buildPulseUiState(): PulseUiState {
        val lastRun = loop.lastRun
        val ts = lastRun?.lastAPSRun
        val titleText = if (ts != null && ts > 0L) {
            val elapsed = (dateUtil.now() - ts).coerceAtLeast(0L)
            rh.gs(R.string.pulse_panel_title_with_age, dateUtil.age(elapsed, true, rh).trim())
        } else {
            rh.gs(R.string.pulse_panel_title)
        }

        val request = lastRun?.request
            ?: return PulseUiState(titleText = titleText, summaryText = rh.gs(R.string.pulse_panel_no_data))

        val isHypoRisk = (request.rawData() as? RT)?.isHypoRisk == true

        val plain = request.reason
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        val summaryCore = if (plain.isNotBlank()) {
            val single = plain.replace('\n', ' ').replace(Regex("\\s{2,}"), " ").trim()
            if (single.length <= 220) single else single.take(219).trimEnd() + "…"
        } else {
            rh.gs(
                R.string.pulse_panel_fallback,
                decimalFormatter.to2Decimal(request.smb),
                if (request.rate == -1.0) "—" else decimalFormatter.to2Decimal(request.rate)
            )
        }
        val summaryText = if (isHypoRisk) rh.gs(R.string.pulse_panel_hypo_prefix) + " " + summaryCore
        else summaryCore

        val metaText = rh.gs(
            R.string.pulse_panel_meta,
            decimalFormatter.to2Decimal(request.smb),
            if (request.rate == -1.0) "—" else decimalFormatter.to2Decimal(request.rate) + " U/h",
            decimalFormatter.to0Decimal((request.autosensResult?.ratio ?: 1.0) * 100.0)
        )

        return PulseUiState(
            titleText = titleText,
            summaryText = summaryText,
            metaText = metaText,
            hintText = "", //rh.gs(R.string.pulse_panel_hint),  //remove for now. not usefully
            isHypoRisk = isHypoRisk
        )
    }

    private suspend fun buildStatusPanelUiState(): StatusPanelUiState {
        val now = System.currentTimeMillis()
        val midnight = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Tagesschritte direkt aus DB — gleicher Weg wie UnifiedActivityProvider
        val todayRecords = persistenceLayer.getStepsCountFromTimeToTime(midnight, System.currentTimeMillis())

        // Source-Priorität: Garmin > Wear > HC > Phone
        val bestSource = todayRecords.map { it.device }.firstOrNull { it == "Garmin-Watchface" }
            ?: todayRecords.map { it.device }.firstOrNull { it.startsWith("Wear") }
            ?: todayRecords.map { it.device }.firstOrNull { it == "HealthConnect" }
            ?: todayRecords.firstOrNull()?.device

        // Bucket into 5-min slices and take max per bucket so frequent HC writes don't overcount.
        // Same dedup strategy as OpenAPSAutoISFPlugin.activityMonitor().
        fun maxPer5MinBucket(records: List<SC>): Int = records
            .groupBy { it.timestamp / (5 * 60_000L) }
            .values
            .sumOf { bucket -> bucket.maxOfOrNull { sc -> sc.steps5min.coerceAtLeast(0) } ?: 0 }

        val sourceRecords = todayRecords.filter { it.device == bestSource }
        val stepsToday = maxPer5MinBucket(sourceRecords)

        val recentRecords = sourceRecords.filter { it.timestamp >= System.currentTimeMillis() - 15 * 60 * 1000L }
        val fiveMinAgo = System.currentTimeMillis() - 5 * 60 * 1000L
        val stepsDelta = maxPer5MinBucket(recentRecords.filter { it.timestamp >= fiveMinAgo })

        val stepsText = if (stepsToday > 0) "$stepsToday / +$stepsDelta" else "--"

        // HR — latest non-zero BPM reading
        val hrText = heartRateGraphFlow.value.heartRates
            .filter { it.value > 0 }.lastOrNull()?.value?.toInt()?.toString() ?: "--"

        // Last SMB (bolus)
        val lastBolusMs = activePlugin.activePump.lastBolusTime.value ?: 0L
        val smbSeconds = MidnightUtils.secondsFromMidnight(lastBolusMs)
        val lastSmbTime = if (smbSeconds > 0) dateUtil.formatHHMM(smbSeconds) else "--:--"
        val bolusCU = activePlugin.activePump.lastBolusAmount.value?.cU ?: 0.0
        val lastSmbAmount = if (bolusCU > 0.0) {
            ch.insulinAmountString(PumpInsulin(bolusCU))
            //remove the U200 part.
            .substringBefore("(").trim()
        }
        else "--"

        // Current basal (TBR or loop fallback)
        val unavail = rh.gs(R.string.value_unavailable_short)
        val tbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val (basalPctText, basalRateText) =
            if (tbr?.isValid == true) {
                val profile = profileFunction.getProfile()
                val rate = profile?.let {
                    rh.gs(
                        R.string.format_insulin_units,
                        tbr.convertedToAbsolute(now, it)
                    )
                } ?: unavail
                val pct = profile?.let {
                    val profileBasal = it.getBasal(now)
                    val absoluteRate = tbr.convertedToAbsolute(now, it)
                    rh.gs(R.string.formatPercent, (absoluteRate / profileBasal * 100))
                } ?: unavail
                pct to rate
            } else {
                // TBR not active → Display profile basal at 100%
                val profile = profileFunction.getProfile()
                val rate = profile?.let {
                    rh.gs(R.string.format_insulin_units, it.getBasal(now))
                } ?: unavail
                rh.gs(R.string.formatPercent, 100.0) to rate
            }

        // IOB — total bolus + basal IOB
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val iobText = rh.gs(R.string.format_insulin_units, bolusIob.iob + basalIob.basaliob)

        return StatusPanelUiState(
            stepsText = stepsText,
            hrText = hrText,
            lastSmbTime = lastSmbTime,
            lastSmbAmount = lastSmbAmount,
            basalPctText = basalPctText,
            basalRateText = basalRateText,
            iobText = iobText
        )
    }
}
