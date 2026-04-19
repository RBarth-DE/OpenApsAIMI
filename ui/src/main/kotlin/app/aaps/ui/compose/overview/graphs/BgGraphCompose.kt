package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.graph.vico.Square
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.BasalGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.EpsGraphPoint
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.overview.graph.TargetLineData
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcProfile
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Series identifiers */
private const val SERIES_REGULAR = "regular"
private const val SERIES_BUCKETED = "bucketed"
private const val SERIES_PRED_IOB = "pred_iob"
private const val SERIES_PRED_COB = "pred_cob"
private const val SERIES_PRED_ACOB = "pred_acob"
private const val SERIES_PRED_UAM = "pred_uam"
private const val SERIES_PRED_ZT = "pred_zt"

/** All prediction series identifiers */
private val PREDICTION_SERIES = listOf(SERIES_PRED_IOB, SERIES_PRED_COB, SERIES_PRED_ACOB, SERIES_PRED_UAM, SERIES_PRED_ZT)

/**
 * BG Graph using Vico — dual-layer chart.
 *
 * Layer 0 (start axis): BG readings — regular (outlined circles) + bucketed (filled, range-colored)
 * Layer 1 (end axis, hidden): Basal — profile (dashed step) + actual delivered (solid step + area fill)
 *
 * Basal Y-axis is scaled so maxBasal = 25% of chart height (maxY = maxBasal * 4).
 *
 * Scroll/Zoom:
 * - Accepts external scroll/zoom states for synchronization with secondary graphs
 * - This is the primary interactive graph - user controls scroll/zoom here
 *
 * Tap on SMB triangle to show bolus details dialog.
 */
@Composable
fun BgGraphCompose(
    viewModel: GraphViewModel,
    bgOverlays: List<SeriesType>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    modifier: Modifier = Modifier
) {
    // Collect flows independently - each triggers recomposition only when it changes
    val bgReadings by viewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val bucketedData by viewModel.bucketedDataFlow.collectAsStateWithLifecycle()
    val showPredictions = SeriesType.PREDICTIONS in bgOverlays
    val rawPredictions by viewModel.predictionsFlow.collectAsStateWithLifecycle()
    val predictions = if (showPredictions) rawPredictions else emptyList()
    val rawBasalData by viewModel.basalGraphFlow.collectAsStateWithLifecycle()
    val targetData by viewModel.targetLineFlow.collectAsStateWithLifecycle()
    val showBasal = SeriesType.BASAL in bgOverlays
    val basalData = if (showBasal) rawBasalData else BasalGraphData(emptyList(), emptyList(), 0.0)
    val epsPoints by viewModel.epsGraphFlow.collectAsStateWithLifecycle()
    val showActivity = SeriesType.ACTIVITY in bgOverlays
    val activityData by viewModel.activityGraphFlow.collectAsStateWithLifecycle()
    val showBolus = SeriesType.BOLUS in bgOverlays
    val treatmentData by viewModel.treatmentGraphFlow.collectAsStateWithLifecycle()
    val smbColor = AapsTheme.elementColors.insulin
    val chartConfig by viewModel.chartConfigFlow.collectAsStateWithLifecycle()

    // Use derived time range or fall back to default (last GRAPH_TIME_RANGE_HOURS hours)
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = System.currentTimeMillis()
        val dayAgo = now - Constants.GRAPH_TIME_RANGE_HOURS * 60 * 60 * 1000L
        dayAgo to now
    }

    // Single model producer shared by all layers
    val modelProducer = remember { CartesianChartModelProducer() }

    // Series registry - tracks current data for each series
    val seriesRegistry = remember { mutableStateMapOf<String, List<BgDataPoint>>() }

    // Colors from theme (stable - won't change)
    val regularColor = AapsTheme.generalColors.originalBgValue
    val lowColor = AapsTheme.generalColors.bgLow
    val inRangeColor = AapsTheme.generalColors.bgInRange
    val highColor = AapsTheme.generalColors.bgHigh
    val basalColor = AapsTheme.elementColors.tempBasal
    val targetLineColor = AapsTheme.elementColors.tempTarget
    val activityColor = AapsTheme.elementColors.activity

    // Prediction colors
    val iobPredColor = AapsTheme.generalColors.iobPrediction
    val cobPredColor = AapsTheme.generalColors.cobPrediction
    val aCobPredColor = AapsTheme.generalColors.aCobPrediction
    val uamPredColor = AapsTheme.generalColors.uamPrediction
    val ztPredColor = AapsTheme.generalColors.ztPrediction

    val viewConfiguration = LocalViewConfiguration.current

    // Calculate x-axis range (must match COB graph for alignment)
    val maxX = remember(minTimestamp, maxTimestamp) {
        timestampToX(maxTimestamp, minTimestamp)
    }

    // Track which series are currently included (for matching LineProvider)
    val activeSeriesState = remember { mutableStateOf(listOf<String>()) }

    // Stable time range - only changes when timestamps change by more than 1 minute
    val stableTimeRange = remember(minTimestamp / 60000, maxTimestamp / 60000) {
        minTimestamp to maxTimestamp
    }

    // =========================================================================
    // SMB tap detection state
    // =========================================================================
    var selectedSmb by remember { mutableStateOf<BolusGraphPoint?>(null) }
    var chartWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    // Precompute visible SMB list for hit testing (same filter as rebuildChart)
    val visibleSmbs = remember(treatmentData, showBolus, minTimestamp) {
        if (!showBolus) emptyList()
        else treatmentData.boluses.filter { it.isValid }

    }

    suspend fun rebuildChart(
        currentBasalData: BasalGraphData,
        currentTargetData: TargetLineData,
        currentEpsPoints: List<EpsGraphPoint>,
        currentActivityData: ActivityGraphData,
        currentMaxBgY: Double,
        currentTreatmentData: TreatmentGraphData,
        showBolusMarkers: Boolean,
        currentLowMark: Double
    ) {
        val regularPoints = seriesRegistry[SERIES_REGULAR] ?: emptyList()
        val bucketedPoints = seriesRegistry[SERIES_BUCKETED] ?: emptyList()

        if (regularPoints.isEmpty() && bucketedPoints.isEmpty()) return

        modelProducer.runTransaction {
            // Block 1 → BG layer (layer 0, start axis)
            lineSeries {
                val activeSeries = mutableListOf<String>()

                if (regularPoints.isNotEmpty()) {
                    val dataPoints = regularPoints
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                    activeSeries.add(SERIES_REGULAR)
                }

                if (bucketedPoints.isNotEmpty()) {
                    val dataPoints = bucketedPoints
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                    activeSeries.add(SERIES_BUCKETED)
                }

                // Prediction series - each type as a separate line
                for (predSeries in PREDICTION_SERIES) {
                    val predPoints = seriesRegistry[predSeries]
                    if (predPoints != null && predPoints.isNotEmpty()) {
                        val dataPoints = predPoints
                            .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                            .sortedBy { it.first }
                        series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                        activeSeries.add(predSeries)
                    }
                }

                // Normalizer series
                series(x = normalizerX(maxX), y = NORMALIZER_Y)

                activeSeriesState.value = activeSeries.toList()
            }

            // Block 2 → Basal layer (layer 1, end axis)
            lineSeries {
                if (currentBasalData.profileBasal.size >= 2) {
                    val pts = currentBasalData.profileBasal
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }

                if (currentBasalData.actualBasal.size >= 2) {
                    val pts = currentBasalData.actualBasal
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 3 → Target line layer (layer 2, start axis)
            lineSeries {
                if (currentTargetData.targets.size >= 2) {
                    val pts = currentTargetData.targets
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 4 → EPS layer (layer 3, end axis — Y-values normalized to basal coordinate space)
            // EPS shares End axis with basal, so Y-values must fit within basalMaxY range.
            // Place icons at 75% of chart height for 100% profile, scaled proportionally.
            lineSeries {
                if (currentEpsPoints.isNotEmpty()) {
                    val epsBaseline = currentBasalData.maxBasal * 4.0 * 0.75 // 75% of basalMaxY
                    val pts = currentEpsPoints
                        .map { timestampToX(it.timestamp, minTimestamp) to (it.originalPercentage / 100.0 * epsBaseline) }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 5 → Activity layer (layer 4, start axis — Y-values normalized to BG coordinate space)
            // Scale so maxActivity maps to 80% of maxBgY (same as legacy: maxY * 0.8 / maxIAValue)
            lineSeries {
                val maxAct = currentActivityData.maxActivity
                if (!showActivity || maxAct <= 0.0 || currentActivityData.activity.size < 2) {
                    // Activity disabled or no data — emit dummy series (history + prediction)
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    return@lineSeries
                }
                val scaleFactor = currentMaxBgY * 0.8 / maxAct

                val pts = currentActivityData.activity
                    .map { timestampToX(it.timestamp, minTimestamp) to (it.value * scaleFactor) }
                    .sortedBy { it.first }
                series(x = pts.map { it.first }, y = pts.map { it.second })

                if (currentActivityData.activityPrediction.size >= 2) {
                    val predPts = currentActivityData.activityPrediction
                        .map { timestampToX(it.timestamp, minTimestamp) to (it.value * scaleFactor) }
                        .sortedBy { it.first }
                    series(x = predPts.map { it.first }, y = predPts.map { it.second })
                } else {
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 6 → SMB markers (layer 5, start axis — fixed at bottom of BG chart)
            lineSeries {
                val smbX = if (showBolusMarkers) {
                    currentTreatmentData.boluses
                        .filter { it.isValid }
                        .map { timestampToX(it.timestamp, minTimestamp) }
                        .filter { it in 0.0..maxX }
                } else emptyList()
                if (smbX.isNotEmpty()) {
                    series(x = smbX, y = List(smbX.size) { currentLowMark })
                } else {
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }
        }
    }

    // Split predictions by type into registry
    val predictionsByType = remember(predictions) {
        mapOf(
            SERIES_PRED_IOB to predictions.filter { it.type == BgType.IOB_PREDICTION },
            SERIES_PRED_COB to predictions.filter { it.type == BgType.COB_PREDICTION },
            SERIES_PRED_ACOB to predictions.filter { it.type == BgType.A_COB_PREDICTION },
            SERIES_PRED_UAM to predictions.filter { it.type == BgType.UAM_PREDICTION },
            SERIES_PRED_ZT to predictions.filter { it.type == BgType.ZT_PREDICTION }
        )
    }

    // Single LaunchedEffect for all data - ensures atomic updates
    LaunchedEffect(bgReadings, bucketedData, predictionsByType, rawBasalData, showBasal, targetData, epsPoints, activityData, showActivity, chartConfig, stableTimeRange, treatmentData, showBolus) {
        seriesRegistry[SERIES_REGULAR] = bgReadings
        seriesRegistry[SERIES_BUCKETED] = bucketedData
        for ((key, points) in predictionsByType) {
            seriesRegistry[key] = points
        }
        // maxBgY clamped against highMark (same as legacy GraphData.maxY logic)
        val allBgValues = (bgReadings + bucketedData).map { it.value }
        val maxBgY = if (allBgValues.isNotEmpty()) maxOf(allBgValues.max(), chartConfig.highMark) else chartConfig.highMark
        rebuildChart(basalData, targetData, epsPoints, activityData, maxBgY, treatmentData, showBolus, chartConfig.lowMark)
    }

    // Build lookup map for BUCKETED points: x-value -> BgDataPoint (for PointProvider)
    val bucketedLookup = remember(bucketedData, minTimestamp) {
        bucketedData.associateBy { timestampToX(it.timestamp, minTimestamp) }
    }

    val bucketedPointProvider = remember(bucketedLookup, lowColor, inRangeColor, highColor) {
        BucketedPointProvider(bucketedLookup, lowColor, inRangeColor, highColor)
    }

    // Time formatter and axis configuration
    val timeFormatter = rememberTimeFormatter(minTimestamp)
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)

    // =========================================================================
    // BG layer lines (layer 0)
    // =========================================================================

    val regularLine = remember(regularColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(
                        fill = Fill(Color.Transparent),
                        shape = CircleShape,
                        strokeFill = Fill(regularColor.copy(alpha = 0.3f)),
                        strokeThickness = 1.dp
                    ),
                    size = 6.dp
                )
            )
        )
    }

    val bucketedLine = remember(bucketedPointProvider) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = bucketedPointProvider
        )
    }

    val normalizerLine = remember { createNormalizerLine() }

    // Prediction lines - transparent connecting line with small filled circle points
    val iobPredLine = remember(iobPredColor) { createPredictionLine(iobPredColor) }
    val cobPredLine = remember(cobPredColor) { createPredictionLine(cobPredColor) }
    val aCobPredLine = remember(aCobPredColor) { createPredictionLine(aCobPredColor) }
    val uamPredLine = remember(uamPredColor) { createPredictionLine(uamPredColor) }
    val ztPredLine = remember(ztPredColor) { createPredictionLine(ztPredColor) }

    val activeSeries by activeSeriesState
    val bgLines = remember(activeSeries, regularLine, bucketedLine, iobPredLine, cobPredLine, aCobPredLine, uamPredLine, ztPredLine, normalizerLine) {
        buildList {
            if (SERIES_REGULAR in activeSeries) add(regularLine)
            if (SERIES_BUCKETED in activeSeries) add(bucketedLine)
            if (SERIES_PRED_IOB in activeSeries) add(iobPredLine)
            if (SERIES_PRED_COB in activeSeries) add(cobPredLine)
            if (SERIES_PRED_ACOB in activeSeries) add(aCobPredLine)
            if (SERIES_PRED_UAM in activeSeries) add(uamPredLine)
            if (SERIES_PRED_ZT in activeSeries) add(ztPredLine)
            add(normalizerLine)
        }
    }

    // =========================================================================
    // Basal layer lines (layer 1) — always 2 lines: [profileLine, actualLine]
    // =========================================================================

    // Profile basal: dashed line, no fill, step connector
    val profileBasalLine = remember(basalColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(basalColor)),
            stroke = LineCartesianLayer.LineStroke.Dashed(
                thickness = 1.dp,
                cap = StrokeCap.Round,
                dashLength = 1.dp,
                gapLength = 2.dp
            ),
            areaFill = null,
            pointConnector = Square
        )
    }

    // Actual delivered basal: solid line with semi-transparent area fill, step connector
    val actualBasalLine = remember(basalColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(basalColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.dp),
            areaFill = LineCartesianLayer.AreaFill.single(Fill(basalColor.copy(alpha = 0.3f))),
            pointConnector = Square
        )
    }

    val basalLines = remember(profileBasalLine, actualBasalLine) {
        listOf(profileBasalLine, actualBasalLine)
    }

    // =========================================================================
    // Target line (layer 2) — single line on start (BG) axis
    // =========================================================================

    val targetLine = remember(targetLineColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(targetLineColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.dp),
            areaFill = null,
            pointConnector = Square
        )
    }

    val targetLines = remember(targetLine) { listOf(targetLine) }

    // =========================================================================
    // EPS layer lines (layer 3) — profile icon points
    // =========================================================================

    val profileSwitchColor = AapsTheme.elementColors.profileSwitch
    val profilePainter = rememberVectorPainter(IcProfile)

    val epsLine = remember(profileSwitchColor, profilePainter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = PainterComponent(profilePainter, tint = profileSwitchColor),
                    size = 16.dp
                )
            )
        )
    }

    val epsLines = remember(epsLine) { listOf(epsLine) }

    // =========================================================================
    // Activity layer lines (layer 4) — solid historical + dashed prediction
    // =========================================================================

    val activityHistLine = remember(activityColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(activityColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp),
            areaFill = null
        )
    }

    val activityPredLine = remember(activityColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(activityColor)),
            stroke = LineCartesianLayer.LineStroke.Dashed(
                thickness = 1.5.dp,
                cap = StrokeCap.Round,
                dashLength = 4.dp,
                gapLength = 4.dp
            ),
            areaFill = null
        )
    }

    val activityLines = remember(activityHistLine, activityPredLine) {
        listOf(activityHistLine, activityPredLine)
    }

    val smbLine = remember(smbColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape),
                    size = 13.dp
                )
            )
        )
    }
    val smbLines = remember(smbLine) { listOf(smbLine) }

    // Basal Y-axis range: maxBasal * 4 so basal occupies ~25% of chart height
    // EPS layer shares End axis with basal, so both must use the same Y-range (basalMaxY)
    val basalMaxY = remember(basalData.maxBasal) {
        if (basalData.maxBasal > 0.0) basalData.maxBasal * 4.0 else 1.0
    }

    // =========================================================================
    // Decorations
    // =========================================================================

    val nowLineColor = MaterialTheme.colorScheme.onSurface
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor)
    val decorations = remember(nowLine) { listOf(nowLine) }

    // =========================================================================
    // Range providers — hoisted out of rememberCartesianChart so keys are re-evaluated on recomposition
    // =========================================================================

    val startAxisRangeProvider = remember(maxX) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX)
    }
    val endAxisRangeProvider = remember(maxX, basalMaxY) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = 0.0, maxY = basalMaxY)
    }

    // =========================================================================
    // SMB tap hit-test modifier
    // Left axis occupies ~36dp; remaining width is chart content.
    // chartX = (tapX - leftAxisPx + scrollPx) / (contentPx * zoom / maxX)
    // Tolerance: ±15dp in screen space → converted to chart units.
    // =========================================================================

    // Diese States werden im Lambda immer aktuell gelesen
    val latestZoom by rememberUpdatedState(zoomState.value)
    val latestScroll by rememberUpdatedState(scrollState.value)
    val latestChartWidth by rememberUpdatedState(chartWidthPx)

    // Initialzoom einmalig capturen (entspricht DEFAULT_GRAPH_ZOOM_MINUTES Viewport
    var initialZoom by remember { mutableFloatStateOf(-1f) }

    // Capture the initial zoom value via LaunchedEffect — must NOT be done during composition
    LaunchedEffect(zoomState.value) {
        if (initialZoom <= 0f && zoomState.value > 0f) {
            initialZoom = zoomState.value
        }
    }

    val smbTapModifier = if (showBolus && visibleSmbs.isNotEmpty()) {
        Modifier
            .pointerInput(visibleSmbs, minTimestamp, maxX) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    }
                    if (up != null) {
                        val leftAxisPx = with(density) { 36.dp.toPx() }
                        val contentPx = latestChartWidth - leftAxisPx  // ← latest
                        // Absicherung im pointerInput:
                        if (contentPx <= 0f) return@awaitEachGesture
                        if (initialZoom <= 0f) return@awaitEachGesture  // ← neu: warten bis initialisiert

                        val zoomFactor = latestZoom / initialZoom
                        // Sanity check — verhindert Division by zero oder extreme Werte
                        if (zoomFactor <= 0f || zoomFactor > 100f) return@awaitEachGesture

                        val pixelsPerUnit = (contentPx / DEFAULT_GRAPH_ZOOM_MINUTES) * zoomFactor
                        val tappedChartX = (down.position.x - leftAxisPx + latestScroll) / pixelsPerUnit
                        val toleranceUnits = with(density) { 30.dp.toPx() } / pixelsPerUnit

                        val hit = visibleSmbs.minByOrNull { smb ->
                            kotlin.math.abs(timestampToX(smb.timestamp, minTimestamp) - tappedChartX)
                        }?.takeIf { smb ->
                            kotlin.math.abs(timestampToX(smb.timestamp, minTimestamp) - tappedChartX) <= toleranceUnits
                        }

                        if (hit != null) {
                            //haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedSmb = hit
                        }
                    }
                }
            }
    } else Modifier

    // =========================================================================
    // SMB detail dialog
    // =========================================================================

    selectedSmb?.let { smb ->
        val timeStr = remember(smb.timestamp) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(smb.timestamp))
        }
        AlertDialog(
            onDismissRequest = { selectedSmb = null },
            title = { Text("SMB") },
            text = {
                Text(
                    // Adjust field name if TreatmentPoint uses a different property (e.g. units, value)
                    "Zeit: $timeStr\nMenge: ${"%.2f".format(smb.amount)} U"
                )
            },
            confirmButton = {
                Button(onClick = { selectedSmb = null }) {
                    Text("OK")
                }
            }
        )
    }

    // =========================================================================
    // Chart
    // =========================================================================

    // Box-Wrapper statt direkt auf CartesianChartHost
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .onGloballyPositioned { chartWidthPx = it.size.width.toFloat() }
            .then(smbTapModifier)
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                // Layer 0: BG (start axis, visible)
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(bgLines),
                    rangeProvider = startAxisRangeProvider,
                    verticalAxisPosition = Axis.Position.Vertical.Start
                ),
                // Layer 1: Basal (end axis, hidden — no endAxis parameter)
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(basalLines),
                    rangeProvider = endAxisRangeProvider,
                    verticalAxisPosition = Axis.Position.Vertical.End
                ),
                // Layer 2: Target line (start axis — shares BG Y-axis range)
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(targetLines),
                    rangeProvider = startAxisRangeProvider,
                    verticalAxisPosition = Axis.Position.Vertical.Start
                ),
                // Layer 3: EPS (end axis — shares basalMaxY range, EPS Y-values normalized in rebuildChart)
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(epsLines),
                    rangeProvider = endAxisRangeProvider,
                    verticalAxisPosition = Axis.Position.Vertical.End
                ),
                // Layer 4: Activity (start axis — shares BG Y-axis range, values normalized in rebuildChart)
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(activityLines),
                    rangeProvider = startAxisRangeProvider,
                    verticalAxisPosition = Axis.Position.Vertical.Start
                ),
                // Layer 5: SMB markers (start axis — triangle points fixed at lowMark, no line)
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(smbLines),
                    rangeProvider = startAxisRangeProvider,
                    verticalAxisPosition = Axis.Position.Vertical.Start
                ),
                startAxis = VerticalAxis.rememberStart(
                    itemPlacer = VerticalAxis.ItemPlacer.count(),
                    valueFormatter = remember { CartesianValueFormatter.decimal(0) },
                    label = rememberTextComponent(
                        style = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                        minWidth = TextComponent.MinWidth.fixed(30.dp)
                    ),
                    guideline = LineComponent(fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = timeFormatter,
                    itemPlacer = bottomAxisItemPlacer,
                    label = rememberTextComponent(
                        style = TextStyle(color = MaterialTheme.colorScheme.onSurface)
                    ),
                    guideline = LineComponent(fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                ),
                decorations = decorations,
                getXStep = { 1.0 }
            ),
            modelProducer = modelProducer,
            modifier = modifier
                .fillMaxWidth()
                .height(130.dp),
            scrollState = scrollState,
            zoomState = zoomState
        )
    }
}
