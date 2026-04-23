package app.aaps.plugins.aps.openAPSAIMI.physio

import android.content.Context
import android.os.Environment
import android.provider.Settings
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

data class HormonitorDecisionEventMTR(
    val eventId: String,
    val eventTimestamp: Long,
    val trigger: String,
    val profileIsfMgdl: Double,
    val profileBasalUph: Double,
    val currentBgMgdl: Double,
    val cobG: Double,
    val iobU: Double,
    val cyclePhase: String? = null,
    val cycleDay: Int? = null,
    val cycleTrackingMode: String? = null,
    val contraceptiveType: String? = null,
    val wcycleBasalMult: Double? = null,
    val wcycleSmbMult: Double? = null,
    val wcycleIsfMult: Double? = null,
    val thyroidStatus: String? = null,
    val inflammationStatus: String? = null,
    val hrNowBpm: Int? = null,
    val hrAvg15mBpm: Int? = null,
    val rhrRestingBpm: Int? = null,
    val hrvRmssdMs: Double? = null,
    val steps5m: Int? = null,
    val steps15m: Int? = null,
    val steps60m: Int? = null,
    val activityState: String? = null,
    val sleepDebtMinutes: Int? = null,
    val sleepEfficiency: Double? = null,
    val physioSnapshotTimestamp: Long? = null,
    val physioSnapshotValidFlag: Boolean? = null,
    val physioTrace: PhysioDecisionTraceMTR
) {
    fun toJSON(datasetId: String, generatedAtIsoUtc: String, appVersion: String, schemaVersion: String): JSONObject =
        JSONObject().apply {
            put("dataset_id", datasetId)
            put("generated_at", generatedAtIsoUtc)
            put("app_version", appVersion)
            put("schema_version", schemaVersion)
            put("event_id", eventId)
            put("timestamp", eventTimestamp)
            put("trigger", trigger)
            put("profile_isf_mgdl", profileIsfMgdl)
            put("profile_basal_uph", profileBasalUph)
            put("current_bg_mgdl", currentBgMgdl)
            put("cob_g", cobG)
            put("iob_u", iobU)
            put("cycle_phase", cyclePhase ?: JSONObject.NULL)
            put("cycle_day", cycleDay ?: JSONObject.NULL)
            put("cycle_tracking_mode", cycleTrackingMode ?: JSONObject.NULL)
            put("contraceptive_type", contraceptiveType ?: JSONObject.NULL)
            put("wcycle_basal_mult", wcycleBasalMult ?: JSONObject.NULL)
            put("wcycle_smb_mult", wcycleSmbMult ?: JSONObject.NULL)
            put("wcycle_isf_mult", wcycleIsfMult ?: JSONObject.NULL)
            put("thyroid_status", thyroidStatus ?: JSONObject.NULL)
            put("inflammation_status", inflammationStatus ?: JSONObject.NULL)
            put("hr_now_bpm", hrNowBpm ?: JSONObject.NULL)
            put("hr_avg_15m_bpm", hrAvg15mBpm ?: JSONObject.NULL)
            put("rhr_resting_bpm", rhrRestingBpm ?: JSONObject.NULL)
            put("hrv_rmssd_ms", hrvRmssdMs ?: JSONObject.NULL)
            put("steps_5m", steps5m ?: JSONObject.NULL)
            put("steps_15m", steps15m ?: JSONObject.NULL)
            put("steps_60m", steps60m ?: JSONObject.NULL)
            put("activity_state", activityState ?: JSONObject.NULL)
            put("sleep_debt_minutes", sleepDebtMinutes ?: JSONObject.NULL)
            put("sleep_efficiency", sleepEfficiency ?: JSONObject.NULL)
            put("physio_snapshot_timestamp", physioSnapshotTimestamp ?: JSONObject.NULL)
            put("physio_snapshot_valid_flag", physioSnapshotValidFlag ?: JSONObject.NULL)
            put("physio_state", physioTrace.physioState)
            put("physio_confidence", physioTrace.physioConfidence)
            put("physio_data_quality", physioTrace.physioDataQuality)
            put("sleep_quality_score", physioTrace.sleepQualityScore ?: JSONObject.NULL)
            put("isf_factor", physioTrace.isfFactor)
            put("basal_factor", physioTrace.basalFactor)
            put("smb_factor", physioTrace.smbFactor)
            put("reactivity_factor", physioTrace.reactivityFactor)
            put("physio_veto_reason", physioTrace.vetoReason ?: JSONObject.NULL)
            put("final_loop_decision_type", physioTrace.finalLoopDecisionType ?: JSONObject.NULL)
            put("source", physioTrace.source)
        }
}

class AimiHormonitorStudyExporterMTR(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) {
    companion object {
        private const val SCHEMA_VERSION = "1.0.0"
        private const val FILE_NAME = "AIMI_HORMONITOR_event_stream_v1.jsonl"
        private const val DAILY_FILE_NAME = "AIMI_HORMONITOR_daily_outcomes_v1.jsonl"
        private const val QA_FILE_NAME = "AIMI_HORMONITOR_dataset_qa_v1.jsonl"
        private const val STATE_FILE_NAME = "AIMI_HORMONITOR_daily_state_v1.json"
        private const val TAG = "AimiHormonitorStudyExporterMTR"
        private const val DAILY_EMIT_INTERVAL_MS = 30L * 60L * 1000L
        private const val SNAPSHOT_STALE_THRESHOLD_SECONDS = 30L * 60L
        private const val QA_MIN_COMPLETENESS = 0.98
        private const val QA_MIN_TEMPORAL_COHERENCE = 0.99
        private const val QA_MAX_PENDING_DECISION_RATE = 0.01
        private const val QA_MAX_STALE_SNAPSHOT_RATE = 0.10
    }

    @Volatile
    private var sharedStorageDeniedLogged = false
    @Volatile
    private var lastDailyEmitMs: Long = 0L
    @Volatile
    private var lastQaEmitMs: Long = 0L

    private val sharedDir = File(Environment.getExternalStorageDirectory().absolutePath + "/Documents/AAPS")
    private val appScopedDir = File(context.getExternalFilesDir(null), "AAPS")
    private val dailyCounters = LinkedHashMap<String, DailyDecisionCounters>()
    private val qaCounters = LinkedHashMap<String, DailyQaCounters>()

    init {
        restoreDailyState()
    }

    fun export(event: HormonitorDecisionEventMTR) {
        val generatedAt = isoUtcNow()
        val payload = event
            .toJSON(
                datasetId = stableDatasetId(),
                generatedAtIsoUtc = generatedAt,
                appVersion = appVersion(),
                schemaVersion = SCHEMA_VERSION
            )
            .toString()

        val target = File(sharedDir, FILE_NAME)
        val fallback = File(appScopedDir, FILE_NAME)
        appendJsonlSafely(target, fallback, payload)
    }

    @Synchronized
    fun exportDailyOutcomes(
        event: HormonitorDecisionEventMTR,
        tirLowPct: Double?,
        tirInRangePct: Double?,
        tirAbovePct: Double?,
        tdd24hTotalU: Double?,
        snapshotSource: String?,
        snapshotAgeSeconds: Long?,
        snapshotConfidence: Double?
    ) {
        val dayKey = localDayKey(event.eventTimestamp)
        val counters = dailyCounters.getOrPut(dayKey) { DailyDecisionCounters() }
        val qa = qaCounters.getOrPut(dayKey) { DailyQaCounters() }
        counters.totalLoops += 1
        qa.totalEvents += 1
        updateQaCounters(
            qa = qa,
            event = event,
            tirLowPct = tirLowPct,
            tirInRangePct = tirInRangePct,
            tirAbovePct = tirAbovePct,
            tdd24hTotalU = tdd24hTotalU
        )
        when (event.physioTrace.finalLoopDecisionType) {
            "smb" -> counters.smbCount += 1
            "suspend" -> counters.suspendCount += 1
            "tbr_up" -> counters.tbrUpCount += 1
            "tbr_down" -> counters.tbrDownCount += 1
            else -> counters.noneCount += 1
        }
        if (!event.physioTrace.vetoReason.isNullOrBlank()) {
            counters.vetoCount += 1
        }
        persistDailyState()

        val now = System.currentTimeMillis()
        if (now - lastDailyEmitMs < DAILY_EMIT_INTERVAL_MS) return
        lastDailyEmitMs = now

        val generatedAt = isoUtcNow()
        val payload = JSONObject().apply {
            put("dataset_id", stableDatasetId())
            put("generated_at", generatedAt)
            put("app_version", appVersion())
            put("schema_version", SCHEMA_VERSION)
            put("day_local", dayKey)
            put("tdd_24h_total_u", tdd24hTotalU ?: JSONObject.NULL)
            put("tir_low_pct", tirLowPct ?: JSONObject.NULL)
            put("tir_in_range_pct", tirInRangePct ?: JSONObject.NULL)
            put("tir_above_pct", tirAbovePct ?: JSONObject.NULL)
            put("hypo_proxy_pct", tirLowPct ?: JSONObject.NULL)
            put("source_reliability_score", computeSourceReliabilityScore(snapshotAgeSeconds, snapshotConfidence))
            put("source_sync_age_seconds", snapshotAgeSeconds ?: JSONObject.NULL)
            put("source_snapshot_confidence", snapshotConfidence ?: JSONObject.NULL)
            put("source_snapshot_origin", snapshotSource ?: JSONObject.NULL)
            put(
                "source_stale_flag",
                snapshotAgeSeconds?.let { it > SNAPSHOT_STALE_THRESHOLD_SECONDS } ?: JSONObject.NULL
            )
            put("decision_count_total", counters.totalLoops)
            put("decision_count_smb", counters.smbCount)
            put("decision_count_suspend", counters.suspendCount)
            put("decision_count_tbr_up", counters.tbrUpCount)
            put("decision_count_tbr_down", counters.tbrDownCount)
            put("decision_count_none", counters.noneCount)
            put("decision_count_physio_veto", counters.vetoCount)
        }.toString()

        val target = File(sharedDir, DAILY_FILE_NAME)
        val fallback = File(appScopedDir, DAILY_FILE_NAME)
        appendJsonlSafely(target, fallback, payload)
        maybeEmitQaReport(dayKey, generatedAt, qa, snapshotAgeSeconds)
    }

    private fun appendJsonlSafely(primary: File, fallback: File, line: String) {
        try {
            appendLine(primary, line)
        } catch (primaryError: Exception) {
            if (!sharedStorageDeniedLogged) {
                sharedStorageDeniedLogged = true
                aapsLogger.warn(
                    LTag.APS,
                    "[$TAG] Study export denied on shared storage (${primary.absolutePath}). " +
                        "Switching to app-scoped fallback (${fallback.absolutePath}). reason=${primaryError.message}"
                )
            }
            try {
                appendLine(fallback, line)
            } catch (fallbackError: Exception) {
                aapsLogger.error(
                    LTag.APS,
                    "[$TAG] Study export failed on both primary and fallback paths.",
                    fallbackError
                )
            }
        }
    }

    private fun appendLine(file: File, line: String) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        file.appendText("$line\n")
    }

    private fun appVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun stableDatasetId(): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        } catch (_: Exception) {
            ""
        }
        val raw = "${context.packageName}|$androidId|hormonitor_v1"
        return sha256Hex(raw).take(24)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
    }

    private fun isoUtcNow(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }

    private fun localDayKey(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date(timestamp))
    }

    private fun computeSourceReliabilityScore(snapshotAgeSeconds: Long?, snapshotConfidence: Double?): Double {
        val confidence = (snapshotConfidence ?: 0.0).coerceIn(0.0, 1.0)
        val agePenalty = when {
            snapshotAgeSeconds == null -> 1.0
            snapshotAgeSeconds <= 600L -> 0.0
            snapshotAgeSeconds >= SNAPSHOT_STALE_THRESHOLD_SECONDS -> 0.5
            else -> ((snapshotAgeSeconds - 600L).toDouble() / (SNAPSHOT_STALE_THRESHOLD_SECONDS - 600L)) * 0.5
        }
        return (confidence - agePenalty).coerceIn(0.0, 1.0)
    }

    private fun persistDailyState() {
        val stateFile = File(appScopedDir, STATE_FILE_NAME)
        try {
            if (!stateFile.exists()) {
                stateFile.parentFile?.mkdirs()
                stateFile.createNewFile()
            }
            val countersJson = JSONObject()
            dailyCounters.forEach { (day, counters) ->
                countersJson.put(day, JSONObject().apply {
                    put("totalLoops", counters.totalLoops)
                    put("smbCount", counters.smbCount)
                    put("suspendCount", counters.suspendCount)
                    put("tbrUpCount", counters.tbrUpCount)
                    put("tbrDownCount", counters.tbrDownCount)
                    put("noneCount", counters.noneCount)
                    put("vetoCount", counters.vetoCount)
                })
            }
            val root = JSONObject().apply {
                put("schema_version", SCHEMA_VERSION)
                put("last_daily_emit_ms", lastDailyEmitMs)
                put("last_qa_emit_ms", lastQaEmitMs)
                put("daily_counters", countersJson)
                put("daily_qa_counters", JSONObject().apply {
                    qaCounters.forEach { (day, counters) ->
                        put(day, JSONObject().apply {
                            put("totalEvents", counters.totalEvents)
                            put("criticalFieldMissingCount", counters.criticalFieldMissingCount)
                            put("invalidTimestampCount", counters.invalidTimestampCount)
                            put("decisionPendingCount", counters.decisionPendingCount)
                            put("staleSnapshotCount", counters.staleSnapshotCount)
                        })
                    }
                })
            }
            stateFile.writeText(root.toString())
        } catch (_: Exception) {
            // Never break loop/export path on state persistence failures.
        }
    }

    private fun restoreDailyState() {
        val stateFile = File(appScopedDir, STATE_FILE_NAME)
        if (!stateFile.exists()) return
        try {
            val root = JSONObject(stateFile.readText())
            lastDailyEmitMs = root.optLong("last_daily_emit_ms", 0L)
            lastQaEmitMs = root.optLong("last_qa_emit_ms", 0L)
            val counters = root.optJSONObject("daily_counters") ?: return
            counters.keys().forEach { day ->
                val item = counters.optJSONObject(day) ?: return@forEach
                dailyCounters[day] = DailyDecisionCounters(
                    totalLoops = item.optInt("totalLoops", 0),
                    smbCount = item.optInt("smbCount", 0),
                    suspendCount = item.optInt("suspendCount", 0),
                    tbrUpCount = item.optInt("tbrUpCount", 0),
                    tbrDownCount = item.optInt("tbrDownCount", 0),
                    noneCount = item.optInt("noneCount", 0),
                    vetoCount = item.optInt("vetoCount", 0)
                )
            }
            val qa = root.optJSONObject("daily_qa_counters")
            qa?.keys()?.forEach { day ->
                val item = qa.optJSONObject(day) ?: return@forEach
                qaCounters[day] = DailyQaCounters(
                    totalEvents = item.optInt("totalEvents", 0),
                    criticalFieldMissingCount = item.optInt("criticalFieldMissingCount", 0),
                    invalidTimestampCount = item.optInt("invalidTimestampCount", 0),
                    decisionPendingCount = item.optInt("decisionPendingCount", 0),
                    staleSnapshotCount = item.optInt("staleSnapshotCount", 0)
                )
            }
        } catch (_: Exception) {
            // If state is corrupted, keep runtime defaults.
        }
    }

    private fun updateQaCounters(
        qa: DailyQaCounters,
        event: HormonitorDecisionEventMTR,
        tirLowPct: Double?,
        tirInRangePct: Double?,
        tirAbovePct: Double?,
        tdd24hTotalU: Double?
    ) {
        if (
            event.eventId.isBlank() ||
            event.trigger.isBlank() ||
            event.eventTimestamp <= 0L ||
            event.physioTrace.finalLoopDecisionType.isNullOrBlank()
        ) {
            qa.criticalFieldMissingCount += 1
        }
        if (event.eventTimestamp <= 0L || event.eventTimestamp > System.currentTimeMillis() + 5 * 60_000L) {
            qa.invalidTimestampCount += 1
        }
        if (event.physioTrace.finalLoopDecisionType == "pending") {
            qa.decisionPendingCount += 1
        }
        val metricsMissing = listOf(tirLowPct, tirInRangePct, tirAbovePct, tdd24hTotalU).count { it == null }
        if (metricsMissing >= 2) {
            qa.criticalFieldMissingCount += 1
        }
    }

    private fun maybeEmitQaReport(dayKey: String, generatedAt: String, qa: DailyQaCounters, snapshotAgeSeconds: Long?) {
        if (snapshotAgeSeconds != null && snapshotAgeSeconds > SNAPSHOT_STALE_THRESHOLD_SECONDS) {
            qa.staleSnapshotCount += 1
        }
        val now = System.currentTimeMillis()
        if (now - lastQaEmitMs < DAILY_EMIT_INTERVAL_MS) return
        lastQaEmitMs = now

        val completeness = if (qa.totalEvents == 0) 1.0
        else ((qa.totalEvents - qa.criticalFieldMissingCount).toDouble() / qa.totalEvents.toDouble()).coerceIn(0.0, 1.0)
        val temporalCoherence = if (qa.totalEvents == 0) 1.0
        else ((qa.totalEvents - qa.invalidTimestampCount).toDouble() / qa.totalEvents.toDouble()).coerceIn(0.0, 1.0)
        val pendingDecisionRate = if (qa.totalEvents == 0) 0.0 else qa.decisionPendingCount.toDouble() / qa.totalEvents.toDouble()

        val payload = JSONObject().apply {
            put("dataset_id", stableDatasetId())
            put("generated_at", generatedAt)
            put("app_version", appVersion())
            put("schema_version", SCHEMA_VERSION)
            put("day_local", dayKey)
            put("qa_total_events", qa.totalEvents)
            put("qa_completeness_score", completeness)
            put("qa_temporal_coherence_score", temporalCoherence)
            put("qa_pending_decision_rate", pendingDecisionRate)
            put("qa_stale_snapshot_rate", if (qa.totalEvents == 0) 0.0 else qa.staleSnapshotCount.toDouble() / qa.totalEvents.toDouble())
            put("qa_critical_field_missing_count", qa.criticalFieldMissingCount)
            put("qa_invalid_timestamp_count", qa.invalidTimestampCount)
        }.toString()

        val target = File(sharedDir, QA_FILE_NAME)
        val fallback = File(appScopedDir, QA_FILE_NAME)
        appendJsonlSafely(target, fallback, payload)
        logQaStatusLine(
            dayKey = dayKey,
            completeness = completeness,
            temporalCoherence = temporalCoherence,
            pendingDecisionRate = pendingDecisionRate,
            staleSnapshotRate = if (qa.totalEvents == 0) 0.0 else qa.staleSnapshotCount.toDouble() / qa.totalEvents.toDouble()
        )
        persistDailyState()
    }

    private fun logQaStatusLine(
        dayKey: String,
        completeness: Double,
        temporalCoherence: Double,
        pendingDecisionRate: Double,
        staleSnapshotRate: Double
    ) {
        val pass = completeness >= QA_MIN_COMPLETENESS &&
            temporalCoherence >= QA_MIN_TEMPORAL_COHERENCE &&
            pendingDecisionRate <= QA_MAX_PENDING_DECISION_RATE &&
            staleSnapshotRate <= QA_MAX_STALE_SNAPSHOT_RATE
        val levelTag = if (pass) "PASS" else "WARN"
        val message =
            "HORMONITOR_QA_STATUS[$levelTag] day=$dayKey " +
                "completeness=${formatPct(completeness)} " +
                "temporal=${formatPct(temporalCoherence)} " +
                "pending=${formatPct(pendingDecisionRate)} " +
                "stale=${formatPct(staleSnapshotRate)}"
        if (pass) {
            aapsLogger.info(LTag.APS, message)
        } else {
            aapsLogger.warn(LTag.APS, message)
        }
    }

    private fun formatPct(value: Double): String = "%.2f%%".format(value * 100.0)

    private data class DailyDecisionCounters(
        var totalLoops: Int = 0,
        var smbCount: Int = 0,
        var suspendCount: Int = 0,
        var tbrUpCount: Int = 0,
        var tbrDownCount: Int = 0,
        var noneCount: Int = 0,
        var vetoCount: Int = 0
    )

    private data class DailyQaCounters(
        var totalEvents: Int = 0,
        var criticalFieldMissingCount: Int = 0,
        var invalidTimestampCount: Int = 0,
        var decisionPendingCount: Int = 0,
        var staleSnapshotCount: Int = 0
    )
}
