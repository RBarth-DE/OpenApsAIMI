package app.aaps.plugins.aps.openAPSAIMI.steps
import kotlinx.coroutines.runBlocking

import app.aaps.core.data.model.SC
import app.aaps.core.data.model.HR
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🎛️ Unified Activity Provider - MTR Implementation
 * 
 * Orchestrates data retrieval from multiple sources (Wear OS, Health Connect, Phone)
 * based on user preferences and data freshness validation.
 * 
 * Logic:
 * - Queries PersistenceLayer for data within specified window (freshness check)
 * - Filters by source based on preferred Mode (Wear, Auto, HC Only)
 * - Returns the most recent valid data point
 * 
 * @author MTR & Lyra AI - AIMI Activity Orchestrator
 */
@Singleton
class UnifiedActivityProviderMTR @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val sp: SP,
    private val aapsLogger: AAPSLogger
) : ActivityVitalsProvider {

    companion object {
        private const val TAG = "ActivityProvider"
        
        // Preference Key
        const val PREF_KEY_SOURCE_MODE = "aimi_activity_source_mode"
        
        // Mode Values
        const val MODE_PREFER_WEAR = "prefer_wear"
        const val MODE_AUTO_FALLBACK = "auto"
        const val MODE_HEALTH_CONNECT_ONLY = "hc_only"
        const val MODE_DISABLED = "disabled"
        
        // Default Mode
        const val DEFAULT_MODE = MODE_AUTO_FALLBACK
        
        // Known Source Identifiers
        private const val SOURCE_HC = "HealthConnect"
        private const val SOURCE_PHONE = "PhoneSensor"
        private const val SOURCE_GARMIN = "Garmin-Watchface"


        /**
         * Static helper to get mode from any component context
         */
        fun getMode(context: android.content.Context): String {
            val prefs = context.getSharedPreferences("${context.packageName}_preferences", android.content.Context.MODE_PRIVATE)
            return prefs.getString(PREF_KEY_SOURCE_MODE, DEFAULT_MODE) ?: DEFAULT_MODE
        }
    }

    override fun getLatestSteps(windowMs: Long): StepsResult? {
        val mode = getMode()
        if (mode == MODE_DISABLED) return null

        val now = System.currentTimeMillis()
        val start = now - windowMs

        return try {
            val records = runBlocking { persistenceLayer.getStepsCountFromTimeToTime(start, now) }
                .sortedByDescending { it.timestamp }

            // DEBUG
            //aapsLogger.error(LTag.GARMIN, "getLatestSteps: windowMs=$windowMs found=${records.size} records")
            // records.forEach { r ->
            //     aapsLogger.error(LTag.GARMIN, "  record: device=${r.device} steps5=${r.steps5min} steps15=${r.steps15min} ts=${r.timestamp}")
            // }

            val garminRecord = records.firstOrNull { it.device == SOURCE_GARMIN }
            val wearRecord   = records.firstOrNull { isWearDevice(it.device) }
            val hcRecord     = records.firstOrNull { it.device == SOURCE_HC }
            val phoneRecord  = records.firstOrNull { it.device == SOURCE_PHONE }

            //aapsLogger.error(LTag.GARMIN, "mode=$mode garmin=${garminRecord?.steps5min} wear=${wearRecord?.steps5min} hc=${hcRecord?.steps5min} phone=${phoneRecord?.steps5min}")

            when (mode) {
                MODE_PREFER_WEAR   -> garminRecord?.let { toStepsResult(it) }
                    ?: wearRecord?.let { toStepsResult(it) }
                MODE_HEALTH_CONNECT_ONLY -> hcRecord?.let { toStepsResult(it) }
                MODE_AUTO_FALLBACK -> {
                    val result = garminRecord?.let { toStepsResult(it) }
                        ?: wearRecord?.let { toStepsResult(it) }
                        ?: hcRecord?.let { toStepsResult(it) }
                        ?: if (garminRecord == null && wearRecord == null) {
                            phoneRecord?.let { toStepsResult(it) }
                        } else null
                    aapsLogger.debug(LTag.GARMIN, "AUTO_FALLBACK result: steps=${result?.steps} source=${result?.source}")
                    result
                }
                else -> null
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.GARMIN, "getLatestSteps error", e)
            null
        }
    }

    fun getStepsTotalSince(startMs: Long): StepsResult? {
        val mode = getMode()
        if (mode == MODE_DISABLED) return null

        val now = System.currentTimeMillis()

        return try {
            val records = runBlocking { persistenceLayer
                .getStepsCountFromTimeToTime(startMs, now) }
                .sortedBy { it.timestamp } // zeitlich vorwärts

            if (records.isEmpty()) return null

            // Quelle nach Modus auswählen
            val filtered = when (mode) {
                MODE_PREFER_WEAR ->
                    records.filter { isWearDevice(it.device) }

                MODE_HEALTH_CONNECT_ONLY ->
                    records.filter { it.device == SOURCE_HC }

                MODE_AUTO_FALLBACK -> {
                    val wear = records.filter { isWearDevice(it.device) }
                    wear.ifEmpty { records.filter { it.device == SOURCE_HC || it.device == SOURCE_PHONE } }
                }
                else -> emptyList()
            }

            if (filtered.isEmpty()) return null

            // WICHTIG: nur Delta-Felder summieren
            val totalSteps = filtered.sumOf { it.steps5min }

            StepsResult(
                steps = totalSteps,
                timestamp = now,
                source = filtered.first().device,
                duration = now - startMs
            )
        } catch (e: Exception) {
            aapsLogger.error(LTag.GARMIN, "[$TAG] Error fetching total steps", e)
            null
        }
    }

    override fun getLatestHeartRate(windowMs: Long): HrResult? {
        val mode = getMode()
        if (mode == MODE_DISABLED) return null
        
        val now = System.currentTimeMillis()
        val start = now - windowMs
        
        try {
            val records = runBlocking { persistenceLayer.getHeartRatesFromTimeToTime(start, now) }
                .sortedByDescending { it.timestamp }
                
            if (records.isEmpty()) return null
            
            val wearRecord = records.firstOrNull { isWearDevice(it.device) }
            val hcRecord = records.firstOrNull { it.device == SOURCE_HC }
            
            return when (mode) {
                MODE_PREFER_WEAR -> wearRecord?.let { toHrResult(it) }
                MODE_HEALTH_CONNECT_ONLY -> hcRecord?.let { toHrResult(it) }
                MODE_AUTO_FALLBACK -> {
                    // Priority: Wear > HC
                    wearRecord?.let { toHrResult(it) } 
                        ?: hcRecord?.let { toHrResult(it) }
                }
                else -> null
            }
            
        } catch (e: Exception) {
            aapsLogger.error(LTag.GARMIN, "[$TAG] Error fetching HR", e)
            return null
        }
    }
    
    // Helpers
    
    private fun getMode(): String {
        return sp.getString(PREF_KEY_SOURCE_MODE, DEFAULT_MODE)
    }
    
    private fun isWearDevice(device: String?): Boolean {
        if (device == null) return false
        return device != SOURCE_HC && device != SOURCE_PHONE
    }
    
    private fun toStepsResult(sc: SC): StepsResult {
        // Steps5min is usually the "recent rate". 
        // Or should we return sum of window? The method is getLatestSteps.
        // Returning the latest record's steps5min gives "current activity level".
        return StepsResult(
            steps = sc.steps5min, // Using 5min as standard accumulator
            timestamp = sc.timestamp,
            source = sc.device,
            duration = sc.duration
        )
    }
    
    private fun toHrResult(hr: HR): HrResult {
        return HrResult(
            bpm = hr.beatsPerMinute,
            timestamp = hr.timestamp,
            source = hr.device
        )
    }
}
