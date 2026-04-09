package app.aaps.core.interfaces.overview.graph

import kotlinx.coroutines.flow.StateFlow

/**
 * Series types available for all graphs (BG primary and secondary).
 * Each type maps to a data flow in OverviewDataCache.
 * IOB implicitly includes bolus markers, COB implicitly includes carbs markers.
 *
 * Not all types are allowed on all graphs — the UI enforces allowed sets per graph type.
 * BG graph: BOLUS, ACTIVITY.
 * Secondary graphs: all types.
 */
enum class SeriesType {

    IOB,
    ABS_IOB,
    COB,
    BGI,
    DEVIATIONS,
    SENSITIVITY,
    VAR_SENSITIVITY,
    DEV_SLOPE,
    HEART_RATE,
    STEPS,
    ACTIVITY,
    MODES,
    PULSE,
    TIR,
    BOLUS   // SMB triangle markers on BG graph (fixed at bottom)
}

/**
 * Configuration for the overview graphs.
 *
 * @param bgOverlays       Overlay toggles for the BG graph (currently only [SeriesType.ACTIVITY]).
 * @param showIobGraph     Whether the fixed IOB/BAS graph is visible (default true).
 * @param iobOverlays      Overlay toggles for the fixed IOB graph (currently only [SeriesType.ACTIVITY]).
 * @param secondaryGraphs  Ordered list of user-configurable secondary graph configurations.
 *   IOB cannot appear here (it has a dedicated fixed slot). Each graph is a List (not Set) to
 *   preserve selection order:
 *   - list[0] = left axis (start), list[1] = right axis (end).
 *   Max 2 series per graph. FIFO: adding a 3rd deselects the oldest.
 */
data class GraphConfig(
    val bgOverlays: List<SeriesType> = listOf(SeriesType.BOLUS, SeriesType.ACTIVITY),
    val showIobGraph: Boolean = true,
    val iobOverlays: List<SeriesType> = listOf(SeriesType.ACTIVITY),
    val secondaryGraphs: List<List<SeriesType>> = listOf(
        listOf(SeriesType.COB)
    )
) {

    companion object {

        /** Maximum number of secondary graphs allowed */
        const val MAX_SECONDARY_GRAPHS = 5
    }
}

/**
 * Repository for graph configuration persistence.
 * Loads/saves GraphConfig and exposes it as a reactive StateFlow.
 */
interface GraphConfigRepository {

    /** Current graph configuration, updated reactively on changes */
    val graphConfigFlow: StateFlow<GraphConfig>

    /** Update and persist graph configuration */
    fun update(config: GraphConfig)
}
