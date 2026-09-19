package app.aaps.plugins.aps.openAPSAIMI.orchestration

import kotlin.time.Clock

/**
 * Public surface for UI / app shell to avoid colliding with an in-flight AIMI loop tick.
 *
 * This object is shared code on purpose: the app shell and the IOB/COB calculator read it on every
 * target. It reads [AimiLoopTickState], which the Android-only `AimiLoopTelemetry` writes.
 */
object AimiLoopRuntimeGuard {

    /** True while `AimiLoopTelemetry.traceDetermineBasalTick` holds an active tick id. */
    fun isDetermineBasalTickInProgress(): Boolean = AimiLoopTickState.isTickInProgress()

    /** Milliseconds since the active tick started; 0 when idle. */
    fun activeTickAgeMs(): Long {
        val started = AimiLoopTickState.activeTickStartedWallMs()
        return if (started > 0L) (Clock.System.now().toEpochMilliseconds() - started).coerceAtLeast(0L) else 0L
    }

    /** Defer heavy overview/dashboard refresh while a determine_basal tick holds the loop lock. */
    fun overviewRefreshDeferMs(): Long = if (isDetermineBasalTickInProgress()) 2_500L else 0L
}
