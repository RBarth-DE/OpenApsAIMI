package app.aaps.plugins.aps.openAPSAIMI.steps

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🎛️ AIMI Steps Manager - MTR Central Controller
 * 
 * Manages all steps data sources (Garmin, Wear OS, Health Connect, Phone Sensor).
 * Starts/stops sync services and provides unified status.
 * 
 * Sources Priority (all write to DB):
 * 1. Garmin (if available) - Highest priority
 * 2. Wear OS (if available)
 * 3. Phone Sensor (always available) - Persistent backup
 * 4. Health Connect (Android 14+) - Fallback if others missing
 * 
 * All consumers (DetermineBasalAIMI2, Dashboard) read from DB.
 * 
 * @author MTR & Lyra AI - AIMI Steps Integration
 */
@Singleton
class AIMIStepsManagerMTR @Inject constructor(
    private val healthConnectSync: AIMIHealthConnectSyncServiceMTR,
    private val phoneStepsSync: AIMIPhoneStepsSyncServiceMTR,
    private val aapsLogger: AAPSLogger
) {
    
    companion object {
        private const val TAG = "StepsManager"
    }

    private val refCount = AtomicInteger(0)

    /**
     * Starts all steps sync services (legacy, single-plugin callers).
     */
    fun start() {
        ensureStarted()
    }

    /**
     * Stops all steps sync services (legacy, single-plugin callers).
     */
    fun stop() {
        ensureStopped()
    }

    /**
     * Increments the reference count and starts sync services on the first reference.
     * Call this from any plugin that needs step/activity data.
     */
    fun ensureStarted() {
        val count = refCount.incrementAndGet()
        if (count > 1) {
            aapsLogger.debug(LTag.APS, "[$TAG] Ref count now $count (already started)")
            return
        }

        aapsLogger.info(LTag.APS, "[$TAG] 🚀 Starting AIMI Steps Manager (ref=1)")

        try {
            // Start phone sensor sync (always available)
            phoneStepsSync.start()
            aapsLogger.info(LTag.APS, "[$TAG] ✅ Phone sensor sync started")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "[$TAG] ❌ Phone sensor sync failed to start", e)
        }

        try {
            // Start Health Connect sync (Android 14+ only)
            healthConnectSync.start()
            aapsLogger.info(LTag.APS, "[$TAG] ✅ Health Connect sync started")
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "[$TAG] ❌ Health Connect sync failed to start", e)
        }

        aapsLogger.info(LTag.APS, "[$TAG] 📊 Steps Manager Status:")
        aapsLogger.info(LTag.APS, "[$TAG]   - Garmin: Via GarminPlugin (HTTP/CIQ)")
        aapsLogger.info(LTag.APS, "[$TAG]   - Wear OS: Via DataHandlerMobile")
        aapsLogger.info(LTag.APS, "[$TAG]   - Phone: Active (syncing every 5 min)")
        aapsLogger.info(LTag.APS, "[$TAG]   - Health Connect: ${healthConnectSync.getSyncStatus()}")
    }

    /**
     * Decrements the reference count and stops sync services when count reaches zero.
     * Call this from any plugin that no longer needs step/activity data.
     */
    fun ensureStopped() {
        val count = refCount.decrementAndGet()
        if (count > 0) {
            aapsLogger.debug(LTag.APS, "[$TAG] Ref count now $count (still active)")
            return
        }

        aapsLogger.info(LTag.APS, "[$TAG] 🛑 Stopping AIMI Steps Manager (ref=0)")

        phoneStepsSync.stop()
        healthConnectSync.stop()

        // Reset ref count to 0 in case of overshoot
        refCount.set(0)
    }
    
    /**
     * Gets comprehensive status of all sources
     */
    fun getSourcesStatus(): Map<String, String> {
        val active = refCount.get() > 0
        return mapOf(
            "Garmin" to "Via GarminPlugin (passive)",
            "WearOS" to "Via DataHandlerMobile (passive)",
            "PhoneSensor" to if (active) "Active (syncing)" else "Stopped",
            "HealthConnect" to healthConnectSync.getSyncStatus()
        )
    }
    
    /**
     * Triggers manual sync for all active sources
     */
    fun triggerManualSync() {
        aapsLogger.info(LTag.APS, "[$TAG] 🔄 Manual sync triggered for all sources")
        healthConnectSync.triggerManualSync()
        // Phone sensor syncs automatically via timer
    }
    
    /**
     * Logs status for debugging
     */
    fun logStatus() {
        aapsLogger.info(LTag.APS, "[$TAG] ═══════════════════════════════════════")
        aapsLogger.info(LTag.APS, "[$TAG] AIMI Steps Sources Status:")
        getSourcesStatus().forEach { (source, status) ->
            aapsLogger.info(LTag.APS, "[$TAG]   $source: $status")
        }
        aapsLogger.info(LTag.APS, "[$TAG] ═══════════════════════════════════════")
    }
}
