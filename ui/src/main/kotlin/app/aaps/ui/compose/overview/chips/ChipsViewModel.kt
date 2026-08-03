package app.aaps.ui.compose.overview.chips

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.displayText
import app.aaps.core.objects.extensions.round
import app.aaps.core.ui.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

@Immutable
data class IobUiState(
    val text: String = "",
    val iobTotal: Double = 0.0
)

@Immutable
data class CobUiState(
    val text: String = "",
    val carbsReq: Int = 0,
    val cobValue: Double = 0.0
)

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

@Stable
data class BoostChipState(
    val state: String = "",       // IDLE/OBSERVING/CONFIRMED/COMMITTED/RECOVERING
    val color: Long = 0xFF78909C, // blue-grey default
    val detail: String = "",      // e.g. "×1.20"
    val tier: String = "",        // V1 tier fallback
    val isBoost: Boolean = false, // true when BOOST is active APS
    // Widget-style data
    val dynIsf: String = "",      // e.g. "32.1"
    val tdd: String = "",         // e.g. "38.4U"
    val profilePct: String = "",  // e.g. "130%"
    val activity: String = "",    // e.g. "INACTIVE" or V5 score
    val iob: String = "",         // e.g. "4.6U"
    val boostTier: String = "",   // e.g. "UAM_BOOST"
    val mlRisk: String = ""       // e.g. "0.12"
)

@Stable
class ChipsViewModel @AssistedInject constructor(
    @Assisted cache: OverviewDataCache,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val profileUtil: ProfileUtil,
    private val activePlugin: ActivePlugin,
    private val rh: ResourceHelper,
    private val decimalFormatter: DecimalFormatter,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rxBus: RxBus
) : ViewModel() {

    init {
        android.util.Log.e("BOOST_DASH", "ChipsViewModel created, activeAPS=${activePlugin.activeAPS?.javaClass?.simpleName} algo=${activePlugin.activeAPS?.algorithm}")
        val initial = buildBoostChipState()
        android.util.Log.e("BOOST_DASH", "Initial boost chip: isBoost=${initial.isBoost} state=${initial.state}")
    }

    @AssistedFactory
    interface Factory {

        fun create(cache: OverviewDataCache): ChipsViewModel
    }

    private val iobCobTicker = flow {
        while (true) {
            emit(Unit)
            delay(150_000L) // 2.5 minutes
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

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
        val cobInfo = iobCobCalculator.getCobInfo("ChipsViewModel COB")
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

    val sensitivityUiState: StateFlow<SensitivityUiState> = iobCobTicker.combine(cache.iobGraphFlow) { _, _ ->
        buildSensitivityUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SensitivityUiState()
    )

    val boostChipState: StateFlow<BoostChipState> = flow {
        while (true) { emit(buildBoostChipState()); delay(30_000L) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = buildBoostChipState()
    )

    private fun buildBoostChipState(): BoostChipState {
        val aps = activePlugin.activeAPS
        aapsLogger.debug(LTag.UI, "buildBoostChipState: activeAPS=${aps?.javaClass?.simpleName} algo=${aps?.algorithm}")
        if (aps == null) return BoostChipState()
        if (aps.algorithm != APSResult.Algorithm.BOOST) {
            aapsLogger.debug(LTag.UI, "buildBoostChipState: not BOOST, algo=${aps.algorithm}")
            return BoostChipState()
        }
        val result = aps.lastAPSResult
        aapsLogger.debug(LTag.UI, "buildBoostChipState: lastAPSResult=$result boostV5_state=${(result?.rawData() as? RT)?.boostV5_state}")
        if (result == null) return BoostChipState(isBoost = true, state = "BOOST", color = 0xFF4CAF50L)
        val raw = result.rawData()
        if (raw !is RT) return BoostChipState(isBoost = true, state = "BOOST", color = 0xFF4CAF50L)
        val v5State = raw.boostV5_state
        val dynIsf = result.variableSens?.let { "%.0f".format(it) } ?: "--"
        val tdd = raw.tdd?.let { "%.1fU".format(it) } ?: "--"
        val profilePct = raw.boostProfileSwitch?.let { "${it}%" } ?: "--"
        val activity = v5State?.let { raw.boostV5_score?.let { "%.2f".format(it) } ?: "--" }
            ?: raw.boostActive?.let { if (it) "ACTIVE" else "INACTIVE" } ?: "--"
        val iob = raw.IOB?.let { "%.1fU".format(it) } ?: "--"
        val boostTier = raw.boostTier ?: "--"
        val mlRisk = raw.mlHypoRisk?.let { "%.2f".format(it) } ?: "--"

        return if (v5State != null) {
            BoostChipState(
                state = v5State,
                color = when (v5State.uppercase()) {
                    "OBSERVING" -> 0xFFFFC107L
                    "CONFIRMED" -> 0xFFFF6E40L
                    "COMMITTED" -> 0xFFFF9800L
                    "RECOVERING" -> 0xFF26C6DAL
                    else -> 0xFF78909CL
                },
                detail = raw.boostV5_actionMult?.let { "×%.2f".format(it) } ?: "",
                isBoost = true,
                dynIsf = dynIsf, tdd = tdd, profilePct = profilePct, activity = activity, iob = iob,
                boostTier = boostTier, mlRisk = mlRisk
            )
        } else {
            BoostChipState(
                state = raw.boostTier ?: "BOOST",
                color = 0xFF4CAF50L,
                tier = raw.boostTier ?: "",
                isBoost = true,
                dynIsf = dynIsf, tdd = tdd, profilePct = profilePct, activity = activity, iob = iob,
                boostTier = boostTier, mlRisk = mlRisk
            )
        }
    }

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
        val ratioUsed =
            if (config.APS) request?.autosensResult?.ratio ?: 1.0
            else if (config.AAPSCLIENT) processedDeviceStatusData.openAPSData.suggested?.sensitivityRatio ?: 1.0
            else 1.0
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
            val profileIsfDisplayed = profileUtil.fromMgdlToUnits(isfMgdl, units)
            val variableIsfDisplayed = profileUtil.fromMgdlToUnits(variableSens, units)
            isfFrom = String.format(Locale.getDefault(), "%1$.1f", profileIsfDisplayed)
            isfTo = String.format(Locale.getDefault(), "%1$.1f", variableIsfDisplayed)
            dialogText.add(rh.gs(R.string.isf_profile, profileIsfDisplayed))
            dialogText.add(rh.gs(R.string.isf_variable, variableIsfDisplayed))
            if (ratioUsed != 1.0 && ratioUsed != lastAutosensRatio)
                dialogText.add(rh.gs(R.string.algorithm_long, ratioUsed * 100))
            val isfForCarbs = profile.getIsfMgdlForCarbs(dateUtil.now(), "Overview", config, processedDeviceStatusData)
            dialogText.add(rh.gs(R.string.isf_for_carbs, profileUtil.fromMgdlToUnits(isfForCarbs, units)))
            if (config.APS) {
                activePlugin.activeAPS?.getSensitivityOverviewString()?.let { dialogText.add(it) }
            }
        } else {
            // Standard autosens-only branch — hide "AS: 100%" from chip but always show in dialog
            lastAutosensData?.let {
                val pct = it.autosensResult.ratio * 100
                if (pct != 100.0)
                    asText = rh.gs(R.string.autosens_short, pct)
                dialogText.add(rh.gs(R.string.autosens_long, pct))
            }
            if (isfMgdl != null) {
                val profileIsfDisplayed = profileUtil.fromMgdlToUnits(isfMgdl, units)
                dialogText.add(rh.gs(R.string.isf_profile, profileIsfDisplayed))
                lastAutosensRatio?.let { ratio ->
                    dialogText.add(rh.gs(R.string.isf_effective, profileUtil.fromMgdlToUnits(isfMgdl * ratio, units)))
                }
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

    fun showIobInfo() {
        viewModelScope.launch {
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            val total = bolusIob.iob + basalIob.basaliob
            val message =
                rh.gs(R.string.bolus_iob_label) + ": " + rh.gs(R.string.format_insulin_units, bolusIob.iob) + "\n" +
                    rh.gs(R.string.treatments_wizard_basaliob_label) + ": " + rh.gs(R.string.format_insulin_units, basalIob.basaliob) + "\n" +
                    rh.gs(R.string.iob) + ": " + rh.gs(R.string.format_insulin_units, total)
            rxBus.send(
                EventShowDialog.Ok(
                    title = rh.gs(R.string.iob),
                    message = message
                )
            )
        }
    }
}
