package app.aaps.database

import kotlin.time.Duration.Companion.days

/**
 * Bounds for [app.aaps.database.daos.TemporaryBasalDao.getTemporaryBasalActiveAt] so SQLite
 * does not scan/sort the full history when resolving the active temp basal.
 */
object TemporaryBasalQueryBounds {

    /** How far back to search for an active TBR at [timestamp] (ms). */
    val MAX_ACTIVE_LOOKBACK_MS: Long = 7.days.inWholeMilliseconds

    /** Ignore rows with corrupt/overlong duration (ms). */
    val MAX_REASONABLE_DURATION_MS: Long = 7.days.inWholeMilliseconds

    fun earliestTimestampForActiveAt(timestamp: Long): Long =
        (timestamp - MAX_ACTIVE_LOOKBACK_MS).coerceAtLeast(0L)
}
