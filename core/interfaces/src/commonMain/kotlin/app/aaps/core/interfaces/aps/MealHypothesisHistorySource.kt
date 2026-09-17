package app.aaps.core.interfaces.aps

import kotlinx.coroutines.flow.StateFlow

/**
 * Core view of the OpenAPSBoostV5 meal hypothesis state. Declaration order matches the
 * plugin enum, so [MealHypothesisCoreState.ordinal] can be used as the y value of a graph.
 */
enum class MealHypothesisCoreState {
    IDLE,
    OBSERVING,
    CONFIRMED,
    COMMITTED,
    RECOVERING,
}

/** One recorded meal hypothesis state change: the state active from [timestamp] on. */
data class MealHypothesisStateEntry(
    val timestamp: Long,
    val state: MealHypothesisCoreState,
)

/**
 * Source for the recorded meal hypothesis state history of the BOOST algorithm.
 * Implemented by the OpenAPSBoostV5 plugin; the UI reads it without depending on the plugin.
 */
interface MealHypothesisHistorySource {

    /** Time-ordered list of state changes, oldest first. Empty while BOOST was never active. */
    val historyFlow: StateFlow<List<MealHypothesisStateEntry>>
}
