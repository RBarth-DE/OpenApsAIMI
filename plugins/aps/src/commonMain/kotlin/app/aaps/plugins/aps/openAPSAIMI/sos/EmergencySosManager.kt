package app.aaps.plugins.aps.openAPSAIMI.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.aaps.plugins.aps.R
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import androidx.core.content.edit

/**
 * 🚨 AIMI Emergency SOS Manager (SMS Only - Advanced Pro Version)
 *
 * - SMS sent after 30 minutes of BG < threshold.
 * - IMMEDIATE trigger if BG < critical threshold OR Delta <= -10 mg/dL.
 * - Follow-up every 15 minutes if BG does not rise above Threshold + 10.
 * - Dynamic text for Recovery Phase (positive Delta).
 *
 * @author MTR & Lyra AI
 */
object EmergencySosManager {

    private const val TAG = "AIMI_SOS_Manager"
    private const val SOS_PREFS = "aimi_sos_advanced_prefs"

    private const val KEY_FIRST_BELOW_THRESHOLD_TIME = "first_below_threshold_time"
    private const val KEY_LAST_ACTION_TIME = "last_action_time"
    private const val KEY_LAST_VALID_BG_TIME = "last_valid_bg_time"
    private const val KEY_STALE_ALERT_TRIGGERED = "stale_alert_triggered"

    private const val OBSERVATION_WINDOW_MS = 30 * 60 * 1000L  // 30 minutes for first SOS
    private const val FOLLOWUP_INTERVAL_MS  = 15 * 60 * 1000L  // 15 minutes between follow-up SMS

    // FIX 5: Persistent scope with SupervisorJob — survives individual coroutine failures
    private val sosScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @JvmStatic
    fun evaluateSosCondition(
        aapsLogger: AAPSLogger,
        bg: Double,
        delta: Double,
        iob: Double,
        context: Context,
        preferences: Preferences,
        nowMs: Long
    ) {

        val appContext = context.applicationContext

        val isSosEnabled        = preferences.get(BooleanKey.AimiEmergencySosEnable)
        val threshold           = preferences.get(IntKey.AimiEmergencySosThreshold).toDouble()
        val immediateThreshold  = preferences.get(IntKey.AimiEmergencySosImmediateThreshold).toDouble()
        val staleThresholdMs    = preferences.get(IntKey.AimiEmergencySosStaleThreshold).toLong() * 60 * 1000L

        val phone1 = preferences.get(StringKey.AimiEmergencySosPhone).trim()
        val phone2 = preferences.get(StringKey.AimiEmergencySosPhone2).trim()
        val prefs  = appContext.getSharedPreferences(SOS_PREFS, Context.MODE_PRIVATE)
        aapsLogger.debug(LTag.APS, "SOS evaluateSosCondition BG=$bg , SOS=$isSosEnabled , Critical=$threshold , Immediate=$immediateThreshold , Sensor=$staleThresholdMs , #1=$phone1  , #2=$phone1")

        val canSms = ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

        if (!isSosEnabled || (phone1.isEmpty() && phone2.isEmpty()) || !canSms) {
            resetSosState(aapsLogger, prefs)
            return
        }

        // FIX 1: Recovery check uses threshold + 10, not hardcoded 10.0
        // FIX 4: Recovery evaluated FIRST — before follow-up and hypo logic
        val isBgRecovered = bg >= (threshold + 10.0)
        val isSensorError  = bg <= 10.0

        if (isBgRecovered) {
            prefs.edit { putLong(KEY_LAST_VALID_BG_TIME, nowMs) }
            if (prefs.getLong(KEY_FIRST_BELOW_THRESHOLD_TIME, 0L) != 0L ||
                prefs.getBoolean(KEY_STALE_ALERT_TRIGGERED, false)) {
                aapsLogger.debug(LTag.APS, "🟢 BG ($bg) recovered above threshold+10 (${threshold + 10.0}). Resetting SOS.")
                resetSosState(aapsLogger, prefs)
            }
            return  // BG normal — nothing to do
        }

        if (!isSensorError) {
            // Valid BG reading below threshold — track last valid time
            prefs.edit { putLong(KEY_LAST_VALID_BG_TIME, nowMs) }
        }

        val lastValidBgTime = prefs.getLong(KEY_LAST_VALID_BG_TIME, 0L)
        val lastActionTime  = prefs.getLong(KEY_LAST_ACTION_TIME, 0L)
        var shouldTriggerNow = false
        var isStaleScenario  = false

        // FIX 4: Follow-up check only if BG is still low (recovery already returned above)
        if (lastActionTime != 0L && nowMs - lastActionTime >= FOLLOWUP_INTERVAL_MS) {
            aapsLogger.debug(LTag.APS,"SOS  Follow-up lastActionTime=$lastActionTime -> shouldTriggerNow")
            shouldTriggerNow = true
        }

        // Stale sensor management
        if (!shouldTriggerNow && lastValidBgTime != 0L && (nowMs - lastValidBgTime >= staleThresholdMs)) {
            isStaleScenario = true
            if (lastActionTime == 0L) {
                shouldTriggerNow = true
                aapsLogger.debug(LTag.APS,"SOS Stale sensor lastActionTime=$lastActionTime -> shouldTriggerNow")
                prefs.edit { putBoolean(KEY_STALE_ALERT_TRIGGERED, true) }
            }
        }

        // Hypoglycemia management (first SMS)
        if (!shouldTriggerNow && !isStaleScenario && !isSensorError) {
            val firstBelowTimeStored = prefs.getLong(KEY_FIRST_BELOW_THRESHOLD_TIME, 0L)

            var firstBelowTime = firstBelowTimeStored
            if (firstBelowTime == 0L) {
                aapsLogger.debug(LTag.APS, appContext.getString(R.string.sos_log_monitoring_start, bg, threshold))
                prefs.edit { putLong(KEY_FIRST_BELOW_THRESHOLD_TIME, nowMs) }
                firstBelowTime = nowMs
            }

            if (lastActionTime == 0L) {
                when {
                    bg < immediateThreshold                        -> shouldTriggerNow = true
                    delta <= -10.0                                 -> shouldTriggerNow = true
                    nowMs - firstBelowTime >= OBSERVATION_WINDOW_MS -> shouldTriggerNow = true
                }
            }
            aapsLogger.debug(LTag.APS,"SOS Hypoglycemia management lastActionTime=$lastActionTime -> shouldTriggerNow=$shouldTriggerNow")
        }

        // Send SMS
        if (shouldTriggerNow) {
            val timeLabel   = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(nowMs))
            val deltaString = if (delta >= 0) "+${String.format("%.1f", delta)}" else String.format("%.1f", delta)

            aapsLogger.debug(LTag.APS, appContext.getString(R.string.sos_log_sending, bg, deltaString))

            // FIX 5: Use persistent sosScope instead of fire-and-forget CoroutineScope
            sosScope.launch {
                try {
                    val location = fetchLocation(aapsLogger, appContext)

                    val isCritical   = bg < immediateThreshold
                    val isRecovering = delta > 0.0

                    val title: String
                    val footer: String

                    when {
                        isStaleScenario -> {
                            title  = appContext.getString(R.string.sos_sms_title_stale)
                            footer = appContext.getString(R.string.sos_sms_footer_stale)
                        }
                        isCritical -> {
                            title  = appContext.getString(R.string.sos_sms_title_critical)
                            footer = appContext.getString(R.string.sos_sms_footer_critical)
                        }
                        isRecovering -> {
                            title  = appContext.getString(R.string.sos_sms_title_recovery)
                            footer = appContext.getString(R.string.sos_sms_footer_recovery)
                        }
                        else -> {
                            title  = appContext.getString(R.string.sos_sms_title_low)
                            footer = appContext.getString(R.string.sos_sms_footer_low)
                        }
                    }

                    val message = if (isStaleScenario) {
                        "$title\n${appContext.getString(R.string.sos_sms_label_last_bg)}: ${bg.toInt()}\n${appContext.getString(R.string.sos_sms_label_trend)}: $deltaString\n${appContext.getString(R.string.sos_sms_label_time)}: $timeLabel$footer"
                    } else {
                        "$title\n${appContext.getString(R.string.sos_sms_label_bg)}: ${bg.toInt()}\n${appContext.getString(R.string.sos_sms_label_trend)}: $deltaString\n${appContext.getString(R.string.sos_sms_label_iob)}: ${String.format("%.2f", iob)}U\n${appContext.getString(R.string.sos_sms_label_time)}: $timeLabel$footer"
                    }

                    if (phone1.isNotEmpty()) sendRawSms( aapsLogger, appContext, phone1, message, location)
                    if (phone2.isNotEmpty()) sendRawSms( aapsLogger, appContext, phone2, message, location)

                } catch (e: Exception) {
                    aapsLogger.error(LTag.APS, appContext.getString(R.string.sos_log_error_send), e)
                }
            }
            prefs.edit { putLong(KEY_LAST_ACTION_TIME, nowMs) }
        }
    }

    // FIX 2: resetSosState now also clears KEY_LAST_VALID_BG_TIME
    private fun resetSosState(aapsLogger: AAPSLogger, prefs: android.content.SharedPreferences) {
        aapsLogger.debug(LTag.APS, "SOS resetSosState")
        prefs.edit {
            putLong(KEY_FIRST_BELOW_THRESHOLD_TIME, 0L)
                .putLong(KEY_LAST_ACTION_TIME, 0L)
                .putLong(KEY_LAST_VALID_BG_TIME, 0L)
                .putBoolean(KEY_STALE_ALERT_TRIGGERED, false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation(aapsLogger: AAPSLogger, context: Context): Location? {
        aapsLogger.debug(LTag.APS, "SOS fetchLocation")
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) { null }
    }

    private fun sendRawSms(aapsLogger: AAPSLogger, context: Context, phone: String, message: String, location: Location?) {
        aapsLogger.debug(LTag.APS, "SOS sendRawSms")
        val posLabel = context.getString(R.string.sos_sms_label_pos)
        val loc = location?.let { "\n$posLabel: https://www.google.com/maps?q=${it.latitude},${it.longitude}" }
            ?: "\n$posLabel: ${context.getString(R.string.sos_sms_pos_not_available)}"
        
        val fullMsg = message + loc
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getSystemService(SmsManager::class.java)
            else
                @Suppress("DEPRECATION") SmsManager.getDefault()

            val parts = smsManager?.divideMessage(fullMsg)
            if (parts != null && parts.size > 1)
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            else
                smsManager?.sendTextMessage(phone, null, fullMsg, null, null)
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "SOS SMS Error for $phone", e)
        }
    }
}
