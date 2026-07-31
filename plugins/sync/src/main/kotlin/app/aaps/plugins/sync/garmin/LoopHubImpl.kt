package app.aaps.plugins.sync.garmin

import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.HR
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.convertedToPercent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface to the functionality of the looping algorithm and storage systems.
 */
@Singleton
class LoopHubImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val persistenceLayer: PersistenceLayer,
    private val userEntryLogger: UserEntryLogger,
    private val preferences: Preferences,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val dateUtil: DateUtil,
    private val wizardBolusExecutor: WizardBolusExecutor,
    private val activePlugin: ActivePlugin,
    @ApplicationScope private val appScope: CoroutineScope
) : LoopHub {

    @VisibleForTesting
    var clock: Clock = Clock.systemUTC()

    /** Returns the active insulin profile. */
    override val currentProfile: Profile? get() = runBlocking { profileFunction.getProfile() }

    /** Returns the name of the active insulin profile. */
    override val currentProfileName: String
        get() = runBlocking { profileFunction.getProfileName() }

    /** Returns the glucose unit (mg/dl or mmol/l) as selected by the user. */
    override val glucoseUnit: GlucoseUnit
        get() = GlucoseUnit.fromText(preferences.get(StringKey.GeneralUnits))

    /** Returns the remaining bolus insulin on board. */
    override val insulinOnboard: Double
        get() = runBlocking { iobCobCalculator.calculateIobFromBolus() }.iob

    /** Returns the remaining bolus and basal insulin on board. */
    override val insulinBasalOnboard: Double
        get() = runBlocking { iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended() }.basaliob

    /** Returns the remaining carbs on board. */
    override val carbsOnboard: Double?
        get() = runBlocking { iobCobCalculator.getCobInfo("LoopHubImpl") }.displayCob

    /** Returns true if the pump is connected. */
    override val isConnected: Boolean get() = runBlocking { loop.runningMode() } != RM.Mode.DISCONNECTED_PUMP

    /** Short loop status for the watch face. */
    override val loopStatus: String
        get() = when (runBlocking { loop.runningMode() }) {
            RM.Mode.CLOSED_LOOP -> "CLOSED"
            RM.Mode.CLOSED_LOOP_LGS -> "LGS"
            RM.Mode.OPEN_LOOP -> "OPEN"
            RM.Mode.DISABLED_LOOP -> "DISABLED"
            RM.Mode.SUPER_BOLUS -> "SUPERBOLUS"
            RM.Mode.DISCONNECTED_PUMP -> "DISCONN"
            RM.Mode.SUSPENDED_BY_PUMP,
            RM.Mode.SUSPENDED_BY_USER,
            RM.Mode.SUSPENDED_BY_DST -> "SUSPEND"
            else -> "LOOP"
        }

    /** Epoch-ms of the last APS run (loop freshness), or null if it has never run. */
    override val lastLoopEpochMs: Long?
        get() = loop.lastRun?.lastAPSRun

    /** Current DynISF / variable sensitivity in the user's glucose units. Prefer the live APS
     *  variable_sens; fall back to the profile ISF; null if neither is available. */
    override val variableSensInUnits: Double?
        get() {
            val mgdl = loop.lastRun?.constraintsProcessed?.variableSens
                ?: runBlocking { currentProfile?.getIsfMgdl("GarminLoopHub") }
                ?: return null
            return profileUtil.fromMgdlToUnits(mgdl, glucoseUnit)
        }

    /** Returns true if the current profile is set of a limited amount of time. */
    override val isTemporaryProfile: Boolean
        get() {
            val ps = runBlocking { persistenceLayer.getEffectiveProfileSwitchActiveAt(clock.millis()) }
            return ps != null && ps.originalDuration > 0
        }

    /** Returns the factor by which the basal rate is currently raised (> 1) or lowered (< 1). */
    override val temporaryBasal: Double
        get() {
            return currentProfile?.let {
                val tb = runBlocking { processedTbrEbData.getTempBasalIncludingConvertedExtended(clock.millis()) }
                tb?.convertedToPercent(clock.millis(), it)?.div(100.0)
            } ?: Double.NaN
        }

    override val lowGlucoseMark
        get() = profileUtil.convertToMgdl(
            preferences.get(UnitDoubleKey.OverviewLowMark), glucoseUnit
        )

    override val highGlucoseMark
        get() = profileUtil.convertToMgdl(
            preferences.get(UnitDoubleKey.OverviewHighMark), glucoseUnit
        )

    // ── BOOST fields for Garmin watchface ──
    private val boostResult: APSResult?
        get() = activePlugin.activeAPS?.takeIf { it.algorithm == APSResult.Algorithm.BOOST }?.lastAPSResult

    override val isBoostActive: Boolean
        get() = activePlugin.activeAPS?.algorithm == APSResult.Algorithm.BOOST

    override val boostV5State: String?
        get() = (boostResult?.rawData() as? RT)?.boostV5_state

    override val boostTier: String?
        get() = (boostResult?.rawData() as? RT)?.boostTier

    override val boostDynIsf: String?
        get() = boostResult?.variableSens?.let { String.format(Locale.US, "%.0f", it) }

    override val boostTdd: String?
        get() = (boostResult?.rawData() as? RT)?.tdd?.let { String.format(Locale.US, "%.1f", it) }

    /** Tells the loop algorithm that the pump is physically connected. */
    override fun connectPump() {
        appScope.launch {
            persistenceLayer.cancelCurrentRunningMode(clock.millis(), Action.RECONNECT, Sources.Garmin)
            commandQueue.cancelTempBasal(enforceNew = true)
        }
    }

    /** Tells the loop algorithm that the pump will be physically disconnected
     *  for the given number of minutes. */
    override fun disconnectPump(minutes: Int) {
        currentProfile?.let { p ->
            appScope.launch {
                loop.handleRunningModeChange(
                    durationInMinutes = minutes,
                    profile = p,
                    newRM = RM.Mode.DISCONNECTED_PUMP,
                    action = Action.DISCONNECT,
                    source = Sources.Garmin,
                    listValues = listOf(ValueWithUnit.Minute(minutes))
                )
            }
        }
    }

    /** Retrieves the glucose values starting at from. */
    override fun getGlucoseValues(from: Instant, ascending: Boolean): List<GV> = runBlocking {
        persistenceLayer.getBgReadingsDataFromTime(from.toEpochMilli(), ascending)
    }

    /** Notifies the system that carbs were eaten and stores the value. */
    override fun postCarbs(carbohydrates: Int) {
        aapsLogger.info(LTag.GARMIN, "post $carbohydrates g carbohydrates")
        val carbsAfterConstraints =
            carbohydrates.coerceAtMost(constraintChecker.getMaxCarbsAllowed().value())
        // Instant carbs now ride the shared executor (one audited path); it logs the user entry + delivers.
        appScope.launch {
            wizardBolusExecutor.deliverCarbs(
                carbs = carbsAfterConstraints,
                note = null,
                source = Sources.Garmin,
                onError = { aapsLogger.error(LTag.GARMIN, "carbs delivery failed: $it") }
            )
        }
    }

    // mod Bolus and temp target
    /** Triggers a bolus. */
    override fun postBolus(bolus: Double) {
        aapsLogger.info(LTag.GARMIN, "trigger a bolus of $bolus U")
        userEntryLogger.log(
            action = Action.BOLUS,
            source = Sources.Garmin,
            note = null,
            ValueWithUnit.Insulin(bolus)
        )
        val detailedBolusInfo = DetailedBolusInfo().apply {
            eventType = TE.Type.SNACK_BOLUS
            insulin = bolus
        }
        appScope.launch {
            commandQueue.bolus(detailedBolusInfo)
        }
    }

    override fun postTempTarget(target: Double, duration: Int) {
        if (target == 0.0 || duration == 0) {
            appScope.launch {
                persistenceLayer.cancelCurrentTemporaryTargetIfAny(
                    timestamp = dateUtil.now(),
                    action = Action.TT,
                    source = Sources.TTDialog,
                    note = null,
                    listValues = listOf()
                )
            }
        } else {
            appScope.launch {
                persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                    temporaryTarget = TT(
                        timestamp = dateUtil.now(),
                        duration = TimeUnit.MINUTES.toMillis(duration.toLong()),
                        reason = TT.Reason.WEAR,
                        lowTarget = profileUtil.convertToMgdl(target, profileUtil.units),
                        highTarget = profileUtil.convertToMgdl(target, profileUtil.units)
                    ),
                    action = Action.TT,
                    source = Sources.Garmin,
                    note = null,
                    listValues = listOf(
                        ValueWithUnit.TETTReason(TT.Reason.AUTOMATION),
                        ValueWithUnit.Mgdl(target),
                        ValueWithUnit.Minute(duration)
                    ).filterNotNull()
                )
            }
        }
    }
    // end mod

    /** Stores hear rate readings that a taken and averaged of the given interval. */
    override fun storeHeartRate(
        samplingStart: Instant, samplingEnd: Instant,
        avgHeartRate: Int,
        device: String?
    ) {
        val hr = HR(
            timestamp = samplingStart.toEpochMilli(),
            duration = samplingEnd.toEpochMilli() - samplingStart.toEpochMilli(),
            dateCreated = clock.millis(),
            beatsPerMinute = avgHeartRate.toDouble(),
            device = device ?: "Garmin",
        )
        runBlocking {
            persistenceLayer.insertOrUpdateHeartRates(listOf(hr))
        }
    }

    override fun storeStepsCount(
        samplingStart: Instant,
        samplingEnd: Instant,
        steps5min: Int,
        steps10min: Int,
        steps15min: Int,
        steps30min: Int,
        steps60min: Int,
        steps180min: Int,
        device: String?,
    ) {
        val sc = SC(
            duration = samplingEnd.toEpochMilli() - samplingStart.toEpochMilli(),
            timestamp = samplingEnd.toEpochMilli(),
            steps5min = steps5min,
            steps10min = steps10min,
            steps15min = steps15min,
            steps30min = steps30min,
            steps60min = steps60min,
            steps180min = steps180min,
            device = device ?: "Garmin",
            dateCreated = clock.millis(),
        )
        runBlocking {
            try {
                val result: PersistenceLayer.TransactionResult<SC> = persistenceLayer.insertOrUpdateStepsCount(sc)
                val id = result.inserted.firstOrNull()?.id ?: result.updated.firstOrNull()?.id
                aapsLogger.info(
                    LTag.GARMIN,
                    "✅ Steps stored in DB: ID=$id, 5min=$steps5min, timestamp=${java.util.Date(samplingEnd.toEpochMilli())}"
                )
            } catch (error: Exception) {
                aapsLogger.error(
                    LTag.GARMIN,
                    "❌ Failed to store steps: ${error.message}"
                )
            }
        }
    }

    /** Stores a batch of fine-grained (≈1-min) HR samples from CIQ background service. */
    override fun storeHeartRates(samples: List<Pair<Long, Int>>, device: String?) {
        val dev = device ?: "Garmin"
        val hrList = samples.mapNotNull { (endMs, bpm) ->
            if (bpm <= 10 || endMs <= 0L) return@mapNotNull null
            HR(
                timestamp = endMs,
                duration = 60_000L,
                dateCreated = clock.millis(),
                beatsPerMinute = bpm.toDouble(),
                device = dev,
            )
        }
        if (hrList.isNotEmpty()) {
            runBlocking {
                persistenceLayer.insertOrUpdateHeartRates(hrList)
            }
        }
    }

    /** Stores a Garmin step-count snapshot with single end timestamp (venu3 format). */
    override fun storeSteps(
        timestampMs: Long,
        steps5min: Int, steps10min: Int, steps15min: Int,
        steps30min: Int, steps60min: Int, steps180min: Int,
        device: String?
    ) {
        if (timestampMs <= 0L) return
        val sc = SC(
            duration = 300_000L,
            timestamp = timestampMs,
            steps5min = steps5min, steps10min = steps10min, steps15min = steps15min,
            steps30min = steps30min, steps60min = steps60min, steps180min = steps180min,
            device = device ?: "Garmin",
            dateCreated = clock.millis(),
        )
        runBlocking {
            try {
                val result: PersistenceLayer.TransactionResult<SC> = persistenceLayer.insertOrUpdateStepsCount(sc)
                val id = result.inserted.firstOrNull()?.id ?: result.updated.firstOrNull()?.id
                aapsLogger.info(
                    LTag.GARMIN,
                    "✅ Steps stored in DB: ID=$id, 5min=$steps5min, timestamp=${java.util.Date(timestampMs)}"
                )
            } catch (error: Exception) {
                aapsLogger.error(
                    LTag.GARMIN,
                    "❌ Failed to store steps: ${error.message}"
                )
            }
        }
    }
}
