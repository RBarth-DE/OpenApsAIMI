package app.aaps.plugins.aps.advisor

import android.content.SharedPreferences
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.aps.R

/**
 * Shared 48 h "Ask AI" cooldown helpers used by both the AutoISF and AIMI advisors.
 * Each caller passes its own SharedPreferences instance so cooldowns are isolated
 * between advisors.
 */
object AdvisorCooldown {

    private const val KEY_LAST = "__ai_call_last"
    const val COOLDOWN_MS = 48L * 3600L * 1000L

    fun remainingMs(prefs: SharedPreferences): Long {
        val last = prefs.getLong(KEY_LAST, 0L)
        if (last <= 0L) return 0L
        val elapsed = System.currentTimeMillis() - last
        return (COOLDOWN_MS - elapsed).coerceAtLeast(0L)
    }

    fun markNow(prefs: SharedPreferences) {
        prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    fun format(rh: ResourceHelper, ms: Long): String {
        val totalMinutes = ((ms + 59_000L) / 60_000L).coerceAtLeast(1L)
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0  -> rh.gs(R.string.advisor_cooldown_dh, days.toInt(), hours.toInt())
            hours > 0 -> rh.gs(R.string.advisor_cooldown_hm, hours.toInt(), minutes.toInt())
            else      -> rh.gs(R.string.advisor_cooldown_m, minutes.toInt())
        }
    }

    fun isErrorResult(s: String): Boolean {
        val t = s.trimStart()
        return t.startsWith("API key missing", true) ||
            t.startsWith("Connection error", true) ||
            t.startsWith("Claude error", true) ||
            t.startsWith("Gemini error", true) ||
            t.startsWith("Error (", true)
    }
}
