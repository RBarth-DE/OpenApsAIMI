package app.aaps.core.interfaces.aps

import android.annotation.SuppressLint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RT(
    var algorithm: APSResult.Algorithm = APSResult.Algorithm.UNKNOWN,
    var runningDynamicIsf: Boolean? = false,
    var runningAutoIsf: Boolean? = false,
    @Serializable(with = TimestampToIsoSerializer::class)
    var timestamp: Long? = null,
    val temp: String = "absolute",
    var bg: Double? = null,
    var tick: String? = null,
    var eventualBG: Double? = null,
    var targetBG: Double? = null,
    var snoozeBG: Double? = null, // AMA only
    var insulinReq: Double? = null,
    var carbsReq: Int? = null,
    var carbsReqWithin: Int? = null,
    var units: Double? = null, // micro bolus
    @Serializable(with = TimestampToIsoSerializer::class)
    var deliverAt: Long? = null, // The time at which the micro bolus should be delivered
    var sensitivityRatio: Double? = null, // autosens ratio (fraction of normal basal)
    @Serializable(with = StringBuilderSerializer::class)
    var reason: StringBuilder = StringBuilder(),
    var duration: Int? = null,
    var rate: Double? = null,
    var predBGs: Predictions? = null,
    var COB: Double? = null,
    var IOB: Double? = null,
    var variable_sens: Double? = null,
    var isfMgdlForCarbs: Double? = null, // used to pass to AAPS client
    @Serializable(with = ConsoleLogSerializer::class)
    var consoleLog: MutableList<String>? = null,
    var consoleError: MutableList<String>? = null
) {
    // ── Fields below moved out of primary constructor to avoid >100-param call ──

    // ── BOOST algorithm fields ──
    var boostTier: String? = null               // Which tier was triggered (e.g. "UAM_BOOST", "PERCENT_SCALE", etc.)
    var boostActive: Boolean? = null             // Whether Boost was in its active time window
    var fastCarbProtection: Boolean? = null      // Whether fast-carb rebound protection suppressed UAM/Accel tiers this cycle
    var dynamicISF: Double? = null               // Dosing ISF (future_sens) used for insulin requirement
    var predictionISF: Double? = null            // Prediction ISF (variable_sens) used for BG predictions
    var sensNormalTarget: Double? = null         // ISF at normal target BG level
    var tdd: Double? = null                      // Blended TDD value used in ISF calculation
    var tddRatio: Double? = null                 // Sensitivity ratio derived from TDD (8h weighted / 7D)
    var insulinReqPctEffective: Double? = null   // Effective insulin required % used for dosing
    var deltaAcceleration: Double? = null        // Delta acceleration percentage
    var boostProfileSwitch: Int? = null          // Effective profile % (activity-adjusted)
    var deviationSensRatio: Double? = null       // The applied sensitivity ratio (> 1 = more resistant)
    var deviationSensSource: String? = null      // "deviation" or "tdd_fallback" or "none"
    var deviationSensClean: Int? = null          // Number of clean (non-meal) entries in the 8H window
    var deviationSensTotal: Int? = null          // Total entries in the 8H window
    var mlHypoRisk: Double? = null              // P(hypo event in next 4h), 0.0-1.0
    var mlRiskScale: Double? = null             // SMB scaling factor applied (1.0 = no reduction)
    var mlPostSmbRisk: Double? = null           // P(hypo in next 4h) at projected post-SMB IOB
    var mlPostSmbScale: Double? = null          // additional damping applied (1.0 = no reduction)
    var mlPostSmbMicroBolusBefore: Double? = null   // microBolus before post-SMB damping (diagnostics)
    var mlMealLikely: Double? = null            // P(BG peak >= current+50 in next 90 min), 0.0-1.0
    var mlMealG3Released: Boolean? = null       // true if any v4.4+ release condition lifted the G3 hold this cycle (V3MLG3 only)
    var mlG3ReleaseSource: String? = null       // v4.4.1: which release condition fired ("delta_accl" | "bg_threshold" | "meal_model")
    // BOOST V5/V6 Observe-Confirm-Commit pipeline fields
    var boostV5_score: Double? = null            // meal_signal_score 0.0-1.0
    var boostV5_state: String? = null            // IDLE | OBSERVING | CONFIRMED | COMMITTED | RECOVERING
    var boostV5_age: Int? = null                 // cycles in current state
    var boostV5_budget: Double? = null           // aggression_budget U
    var boostV5_actionMult: Double? = null       // action multiplier for the current state
    var boostV5_finalDose: Double? = null        // V5's would-have-delivered SMB (U) — direct comparator to rT.units
    var boostV5_velocityFactor: Double? = null   // climb-velocity dose scale applied to the raw shot
    var boostV5_doseAfterCaps: Double? = null    // U — after velocity + state dose-cap, before Phase-3 brakes
    var boostV5_doseAfterBrakes: Double? = null  // U — after the Phase-3 composed brake stack, before the floor
    var boostV5_gateReduction: String? = null    // compact summary of which Phase 3 gates fired
    var boostV5_active: Boolean? = null          // true when V5 was the ACTIVE doser this cycle (not shadow) — drives the V5 overview/widget
    var boostV5_committedCap: Double? = null     // per-user COMMITTED per-cycle hold cap (U) — for dose-gate backtests (2026-07-02)
    var minGuardBG: Double? = null               // smart-selected predicted-low (COB/UAM/IOB blend) — read by V5 shadow
    var isfShadow_ratioRaw: Double? = null       // Boost ISF shadow raw TDD ratio
    var isfShadow_ratioEma: Double? = null       // Boost ISF shadow EMA-smoothed ratio
    var isfShadow_warmup: Double? = null         // Boost ISF shadow warmup fraction
    var isfShadow_variableSens: Double? = null   // Boost ISF shadow variable sensitivity
    var isfShadow_deltaPct: Double? = null       // Boost ISF shadow delta percentage
    var isfShadow_insulinReq: Double? = null     // Boost ISF shadow insulin requirement
    var isfShadow_microBolus: Double? = null     // Boost ISF shadow micro-bolus
    var boostV5_postRescueWindow: Boolean? = null   // V5 post-rescue window active
    var boostV5_cumulativeCapU: Double? = null   // V5 cumulative cap in units
    var boostV5_smbVol60Min: Double? = null      // V5 60-min SMB volume
    var boostSteps_feed: String? = null           // Step source detail
    var sleepState: String? = null                // Sleep detector state
    var sleepStateEnteredAtMs: Long? = null       // Sleep state entry timestamp
    var sleepEntryReason: String? = null          // Sleep entry reason
    var sleepLearnedStartMin: Int? = null         // Learned sleep start (min of day)
    var sleepLearnedWakeMin: Int? = null          // Learned wake time (min of day)
    var sleepLearnedDurationMin: Int? = null      // Learned sleep duration
    var sleepLearnedSessionCount: Int? = null     // Learned session count
    var hrLearnedRestingBpm: Int? = null           // Learned resting HR
    var hrLearnedDaytimeBpm: Int? = null           // Learned daytime HR
    var hrBpmLatest: Double? = null              // Latest HR reading
    var hrBpmAvg5m: Double? = null               // 5-min avg HR
    var hrBpmAvg15m: Double? = null              // 15-min avg HR
    var hrBpmMax5m: Double? = null               // 5-min max HR
    var hrBpmMin5m: Double? = null               // 5-min min HR
    var hrReadingsCount15m: Int? = null          // HR readings in 15-min window
    var hrSource_resolved: String? = null        // Resolved HR source
    var hrSource_states: String? = null          // HR source states
    var boostActivityLoad_baselineSteps: Int? = null    // Activity load baseline
    var boostActivityLoad_lastDaySteps: Int? = null     // Activity load last day
    var boostActivityLoad_ratio: Double? = null         // Activity load ratio
    var boostActivityLoad_wouldDeltaIsfPct: Double? = null  // Activity load ISF delta
    var boostActivityLoad_source: String? = null        // Activity load source
    var boostActivitySource_resolved: String? = null    // Resolved activity source
    var boostActivitySource_states: String? = null      // Activity source states
    var boostActivitySource_bridge: String? = null      // Activity source bridge
    var boostActivityLoad_stepsToday: Int? = null        // Steps today
    var boostActivityLoad_stepsSource: String? = null    // Steps source
    var boostActivityLoad_intradayRatio: Double? = null   // Intraday ratio
    var boostActivityLoad_intradayDeltaIsfPct: Double? = null  // Intraday ISF delta
    var boostAutosens_mode: String? = null               // Autosens mode
    var boostAutosens_orefRatio: Double? = null          // Oref ratio
    var boostAutosens_curveRatio: Double? = null         // Curve ratio
    var boostAutosens_appliedRatio: Double? = null       // Applied ratio
    var boostV5_aggressionKnob: Double? = null           // V5 aggression knob value
    var boostV5_confirmedCap: Double? = null             // V5 confirmed cap
    var boostV5_confirmGate: String? = null              // V5 confirm gate state
    var boostV5_floorWouldAdd: Double? = null            // V5 floor uplift
    var boostV5_prospectiveShot: Double? = null          // V5 pre-brake dose
    var BoostV5AutoConfigSchemaVersion: Int? = null      // V5 auto-config schema version
    var boostV5_velocityBudgetWouldAdd: Double? = null    // V5 velocity budget uplift

    // ── v4-specific fields (after BOOST for v3 compat) ──
    @Serializable(with = StringBuilderSerializer::class)
    var aimilog: StringBuilder = StringBuilder() 
    var isHypoRisk: Boolean = false 
    var aiAuditorEnabled: Boolean = false 
    var aiAuditorVerdict: String? = null 
    var aiAuditorConfidence: Double? = null 
    var aiAuditorModulation: String? = null 
    var aiAuditorRiskFlags: String? = null 
    var learnersInfo: String? = null 
    var trajectoryEnabled: Boolean = false 
    var trajectoryType: String? = null 
    var trajectoryCurvature: Double? = null 
    var trajectoryConvergence: Double? = null 
    var trajectoryCoherence: Double? = null 
    var trajectoryEnergy: Double? = null 
    var trajectoryOpenness: Double? = null 
    var trajectoryHealth: Int? = null 
    var trajectoryModulationActive: Boolean = false 
    var trajectoryWarningsCount: Int? = null 
    var trajectoryConvergenceETA: Int? = null 
    var trajectoryRelevanceScore: Double? = null 
    var contextEnabled: Boolean = false 
    var contextIntentCount: Int = 0 
    var contextModulation: Double = 1.0 
    @Transient
    var aimiAdaptationStatus: AimiAdaptationStatus? = null

    fun serialize() = Json.encodeToString(serializer(), this)

    object StringBuilderSerializer : KSerializer<StringBuilder> {

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringBuilder", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: StringBuilder) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): StringBuilder {
            return StringBuilder().append(decoder.decodeString())
        }
    }

    /**
     * 🛡️ Custom serializer for consoleLog that sanitizes decorative characters
     * 
     * Purpose: Keep visual logs with emojis for display, but serialize clean ASCII-only JSON
     * 
     * Removes:
     * - Emojis (📊 🍱 ⚠️ etc.)
     * - Box drawing characters (│ └ etc.)  
     * - Unicode arrows (→ × etc.)
     * - Control characters (\0 \n \t etc.)
     * 
     * Preserves:
     * - ASCII printable characters (0x20-0x7E)
     * - Essential content (numbers, letters, punctuation)
     */
    object ConsoleLogSerializer : KSerializer<MutableList<String>?> {
        
        override val descriptor: SerialDescriptor = 
            kotlinx.serialization.descriptors.listSerialDescriptor<String>()
        
        override fun serialize(encoder: Encoder, value: MutableList<String>?) {
            if (value == null) {
                encoder.encodeNull()
                return
            }
            
            // Sanitize each log entry before serialization
            val sanitized = value.map { entry ->
                entry
                    // Remove all non-ASCII characters (emojis, unicode, etc.)
                    .replace(Regex("[^\\x20-\\x7E]"), "")
                    // Collapse multiple spaces into one
                    .replace(Regex("\\s+"), " ")
                    // Trim leading/trailing spaces
                    .trim()
            }.filter { it.isNotEmpty() }  // Remove empty entries
            
            // Encode as list
            val compositeEncoder = encoder.beginCollection(descriptor, sanitized.size)
            sanitized.forEachIndexed { index, item ->
                compositeEncoder.encodeStringElement(descriptor, index, item)
            }
            compositeEncoder.endStructure(descriptor)
        }
        
        override fun deserialize(decoder: Decoder): MutableList<String>? {
            // Simple deserialization: decode as list normally
            val compositeDecoder = decoder.beginStructure(descriptor)
            val list = mutableListOf<String>()
            
            while (true) {
                val index = compositeDecoder.decodeElementIndex(descriptor)
                if (index == kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE) break
                list.add(compositeDecoder.decodeStringElement(descriptor, index))
            }
            compositeDecoder.endStructure(descriptor)
            return list
        }
    }

    object TimestampToIsoSerializer : KSerializer<Long> {

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LongToIso", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Long) {
            encoder.encodeString(toISOString(value))
        }

        override fun deserialize(decoder: Decoder): Long {
            return fromISODateString(decoder.decodeString())
        }

        fun fromISODateString(isoDateString: String): Long {
            val parser = ISODateTimeFormat.dateTimeParser()
            val dateTime = DateTime.parse(isoDateString, parser)
            return dateTime.toDate().time
        }

        fun toISOString(date: Long): String {
            @Suppress("SpellCheckingInspection", "LocalVariableName")
            val FORMAT_DATE_ISO_OUT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            val f: DateFormat = SimpleDateFormat(FORMAT_DATE_ISO_OUT, Locale.getDefault())
            f.timeZone = TimeZone.getTimeZone("UTC")
            return f.format(date)
        }
    }

    companion object {

        private val serializer = Json { ignoreUnknownKeys = true }
        fun deserialize(jsonString: String) = serializer.decodeFromString(serializer(), jsonString)
    }
}
