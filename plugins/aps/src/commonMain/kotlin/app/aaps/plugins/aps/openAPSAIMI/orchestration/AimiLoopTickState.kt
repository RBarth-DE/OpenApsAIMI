package app.aaps.plugins.aps.openAPSAIMI.orchestration

import kotlin.concurrent.Volatile

/**
 * The one piece of the AIMI loop's telemetry that shared code reads: whether a determine_basal tick
 * is running, and when it started.
 *
 * The telemetry, the tick wrapper and the study exporter behind them are Android only and live in
 * `androidMain`. [AimiLoopRuntimeGuard] is not: the app shell and the IOB/COB calculator on every
 * target ask whether the loop is mid-tick, so the guard has to stay in shared code. This object is the
 * small bridge between the two - two numbers, written by the Android side and read by everyone.
 *
 * Both fields are plain `@Volatile` writes, exactly as they were inside the telemetry. Only the tick
 * wrapper writes them, and at a tick boundary, so a reader can only ever see the running tick or the
 * one before it.
 */
object AimiLoopTickState {

    /** Id of the running tick, 0 when idle. Read by the loop for its study exporter. */
    @Volatile
    var activeTickId: Long = 0L
        private set

    @Volatile
    private var activeTickStartedWallMs: Long = 0L

    fun isTickInProgress(): Boolean = activeTickId > 0L

    /** Wall clock at the start of the active tick; 0 when no tick is running. */
    fun activeTickStartedWallMs(): Long = activeTickStartedWallMs

    /**
     * Marks a tick as running and returns the id it replaced, which the caller passes back to
     * [endTick] so a nested tick restores its parent instead of clearing it.
     */
    fun beginTick(tickId: Long, wallClockMs: Long): Long {
        val previous = activeTickId
        activeTickId = tickId
        activeTickStartedWallMs = wallClockMs
        return previous
    }

    /** Ends the tick that [beginTick] started, restoring the id it returned. */
    fun endTick(previousTickId: Long) {
        activeTickId = previousTickId
        activeTickStartedWallMs = 0L
    }
}
