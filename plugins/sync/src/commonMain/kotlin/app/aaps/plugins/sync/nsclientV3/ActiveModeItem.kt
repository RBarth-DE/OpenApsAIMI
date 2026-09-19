package app.aaps.plugins.sync.nsclientV3

import app.aaps.plugins.sync.nsShared.ModePreset
import kotlin.time.Clock
/**
 * Represents an active mode with remaining time.
 * Used for displaying "What's running now" to parents.
 */
data class ActiveModeItem(
    val mode: ModePreset,
    val startedAt: Long,
    val durationMs: Long
) {
    val remainingMs: Long
        get() = (startedAt + durationMs) - Clock.System.now().toEpochMilliseconds()
    
    val remainingMinutes: Int
        get() = (remainingMs / 60000).toInt().coerceAtLeast(0)
    
    val isExpired: Boolean
        get() = remainingMs <= 0
    
    val progressPercent: Int
        get() {
            if (durationMs == 0L) return 100
            val elapsed = Clock.System.now().toEpochMilliseconds() - startedAt
            return ((elapsed.toFloat() / durationMs) * 100).toInt().coerceIn(0, 100)
        }
}
