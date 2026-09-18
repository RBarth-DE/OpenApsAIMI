package app.aaps.plugins.main.general.overview

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.graph.data.BarGraphSeries
import app.aaps.core.graph.data.DataPointWithLabelInterface
import app.aaps.core.graph.data.DeviationDataPointLegacy
import app.aaps.core.graph.data.FixedLineGraphSeries
import app.aaps.core.graph.data.GlucoseValueDataPoint
import app.aaps.core.graph.data.LineGraphSeries
import app.aaps.core.graph.data.PointsWithLabelGraphSeries
import app.aaps.core.graph.data.RunningModeDataPoint
import app.aaps.core.graph.data.ScaledDataPoint
import app.aaps.core.graph.data.StepsDataPoint
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.graph.Scale
import app.aaps.core.interfaces.graph.SeriesData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.convertedToPercent
import app.aaps.core.objects.extensions.isInProgress
import app.aaps.core.ui.extensions.toStringFull
import app.aaps.core.ui.extensions.toStringShort
import app.aaps.plugins.main.R
import com.jjoe64.graphview.series.DataPoint
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.AppScope
import kotlin.time.Instant

@SingleIn(AppScope::class)
class OverviewDataImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val preferences: Preferences,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val persistenceLayer: PersistenceLayer,
    private val processedTbrEbData: ProcessedTbrEbData
) : OverviewData {

    var rangeToDisplay = 6 // for graph
    override var toTime: Long = 0
    override var fromTime: Long = 0
    override var endTime: Long = 0

    fun reset() {
        pumpStatus = ""
        calcProgressPct = 100
        bgReadingsArray = ArrayList()
        maxBgValue = Double.MIN_VALUE
        bucketedGraphSeries = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
        bgReadingGraphSeries = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
        predictionsGraphSeries = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
        baseBasalGraphSeries = LineGraphSeries<ScaledDataPoint>()
        tempBasalGraphSeries = LineGraphSeries<ScaledDataPoint>()
        basalLineGraphSeries = LineGraphSeries<ScaledDataPoint>()
        absoluteBasalGraphSeries = LineGraphSeries<ScaledDataPoint>()
        temporaryTargetSeries = LineGraphSeries<DataPoint>()
        runningModesSeries = PointsWithLabelGraphSeries<RunningModeDataPoint>()
        maxIAValue = 0.0
        activitySeries = FixedLineGraphSeries<ScaledDataPoint>()
        activityPredictionSeries = FixedLineGraphSeries<ScaledDataPoint>()
        maxIobValueFound = Double.MIN_VALUE
        iobSeries = FixedLineGraphSeries<ScaledDataPoint>()
        absIobSeries = FixedLineGraphSeries<ScaledDataPoint>()
        iobPredictions1Series = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
        maxBGIValue = Double.MIN_VALUE
        minusBgiSeries = FixedLineGraphSeries<ScaledDataPoint>()
        minusBgiHistSeries = FixedLineGraphSeries<ScaledDataPoint>()
        maxCobValueFound = Double.MIN_VALUE
        cobSeries = FixedLineGraphSeries<ScaledDataPoint>()
        cobMinFailOverSeries = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
        maxDevValueFound = Double.MIN_VALUE
        deviationsSeries = BarGraphSeries<DeviationDataPointLegacy>()
        maxRatioValueFound = 5.0
        minRatioValueFound = -maxRatioValueFound
        ratioSeries = LineGraphSeries<ScaledDataPoint>()
        maxFromMaxValueFound = Double.MIN_VALUE
        maxFromMinValueFound = Double.MIN_VALUE
        dsMaxSeries = LineGraphSeries<ScaledDataPoint>()
        dsMinSeries = LineGraphSeries<ScaledDataPoint>()
        maxTreatmentsValue = 0.0
        treatmentsSeries = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
        maxEpsValue = 0.0
        epsSeries = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
        maxTherapyEventValue = 0.0
        therapyEventSeries = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
        heartRateGraphSeries = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
        stepsCountGraphSeries = PointsWithLabelGraphSeries<StepsDataPoint>()
        maxVarSensValueFound = 200.0
        minVarSensValueFound = 50.0
        varSensSeries = LineGraphSeries<ScaledDataPoint>()
        // AutoISF
        maxIobThValueFound = 0.0
        minIobThValueFound = 0.0
        iobThSeries = LineGraphSeries<ScaledDataPoint>()
        maxAcceIsfValueFound = 1.5
        minAcceIsfValueFound = 0.5
        acceIsfSeries = LineGraphSeries<ScaledDataPoint>()
        maxBgIsfValueFound = 1.5
        minBgIsfValueFound = 0.5
        bgIsfSeries = LineGraphSeries<ScaledDataPoint>()
        maxPpIsfValueFound = 1.5
        minPpIsfValueFound = 0.5
        ppIsfSeries = LineGraphSeries<ScaledDataPoint>()
        maxDuraIsfValueFound = 1.5
        minDuraIsfValueFound = 0.5
        duraIsfSeries = LineGraphSeries<ScaledDataPoint>()
        maxFinalIsfValueFound = 1.5
        minFinalIsfValueFound = 0.5
        finalIsfSeries = LineGraphSeries<ScaledDataPoint>()
    }

    fun initRange() {
        rangeToDisplay = preferences.get(IntNonKey.RangeToDisplay)

        val tz = TimeZone.currentSystemDefault()
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val local = now.toLocalDateTime(tz)
        val truncatedHour = LocalDateTime(local.year, local.month, local.day, local.hour, 0)
        val nextFullHour = truncatedHour.toInstant(tz).plus(1, DateTimeUnit.HOUR, tz)

        toTime = nextFullHour.toEpochMilliseconds() + 100000 // a little bit more to avoid wrong rounding - GraphView specific
        fromTime = toTime - T.hours(rangeToDisplay.toLong()).msecs()
        endTime = toTime
    }

    /*
     * PUMP STATUS
     */

    var pumpStatus: String = ""

    /*
     * CALC PROGRESS
     */

    var calcProgressPct: Int = 100

    /*
    * TEMPORARY BASAL
    */

    fun temporaryBasalText(): String =
        runBlocking {
            profileFunction.getProfile()?.let { profile ->
                var temporaryBasal = processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now())
                if (temporaryBasal?.isInProgress(dateUtil) == false) temporaryBasal = null
                temporaryBasal?.let { rh.gs(app.aaps.core.ui.R.string.temp_basal_overview_short_name) + " " + it.toStringShort(rh) }
                    ?: rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, profile.getBasal())
            } ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
        }

    fun temporaryBasalDialogText(): String =
        runBlocking {
            profileFunction.getProfile()?.let { profile ->
                processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let { temporaryBasal ->
                    "${rh.gs(app.aaps.core.ui.R.string.base_basal_rate_label)}: ${rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, profile.getBasal())}" +
                        "\n" + rh.gs(app.aaps.core.ui.R.string.tempbasal_label) + ": " + temporaryBasal.toStringFull(profile, dateUtil, rh)
                }
                    ?: "${rh.gs(app.aaps.core.ui.R.string.base_basal_rate_label)}: ${rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, profile.getBasal())}"
            } ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
        }

    @DrawableRes fun temporaryBasalIcon(): Int =
        runBlocking {
            profileFunction.getProfile()?.let { profile ->
                processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let { temporaryBasal ->
                    val percentRate = temporaryBasal.convertedToPercent(dateUtil.now(), profile)
                    when {
                        percentRate > 100 -> R.drawable.ic_cp_basal_tbr_high
                        percentRate < 100 -> R.drawable.ic_cp_basal_tbr_low
                        else              -> R.drawable.ic_cp_basal_no_tbr
                    }
                }
            } ?: R.drawable.ic_cp_basal_no_tbr
        }

    @AttrRes fun temporaryBasalColor(context: Context?): Int =
        runBlocking {
            processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now())?.let {
                rh.gac(context, app.aaps.core.ui.R.attr.basal)
            } ?: rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)
        }

    /*
     * EXTENDED BOLUS
    */

    fun extendedBolusText(): String =
        runBlocking { persistenceLayer.getExtendedBolusActiveAt(dateUtil.now()) }?.let { extendedBolus ->
            if (!extendedBolus.isInProgress(dateUtil)) ""
            else if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, extendedBolus.rate)
            else ""
        } ?: ""

    fun extendedBolusDialogText(): String =
        runBlocking { persistenceLayer.getExtendedBolusActiveAt(dateUtil.now()) }?.toStringFull(dateUtil, rh) ?: ""

    /*
     * Graphs
     */

    var bgReadingsArray: List<GV> = ArrayList()
    var maxBgValue = Double.MIN_VALUE
    var bucketedGraphSeries: SeriesData = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
    var bgReadingGraphSeries: SeriesData = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
    var predictionsGraphSeries: SeriesData = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()

    val basalScale = Scale()
    var baseBasalGraphSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()
    var tempBasalGraphSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()
    var basalLineGraphSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()
    var absoluteBasalGraphSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()

    var temporaryTargetSeries: SeriesData = LineGraphSeries<DataPoint>()
    var runningModesSeries: SeriesData = PointsWithLabelGraphSeries<RunningModeDataPoint>()
    var maxIAValue = 0.0
    val actScale = Scale()
    var activitySeries: SeriesData = FixedLineGraphSeries<ScaledDataPoint>()
    var activityPredictionSeries: SeriesData = FixedLineGraphSeries<ScaledDataPoint>()

    var maxEpsValue = 0.0
    val epsScale = Scale()
    var epsSeries: SeriesData = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
    var maxTreatmentsValue = 0.0
    var treatmentsSeries: SeriesData = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
    var maxTherapyEventValue = 0.0
    var therapyEventSeries: SeriesData = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()

    var maxIobValueFound = Double.MIN_VALUE
    val iobScale = Scale()
    var iobSeries: SeriesData = FixedLineGraphSeries<ScaledDataPoint>()
    var absIobSeries: SeriesData = FixedLineGraphSeries<ScaledDataPoint>()
    var iobPredictions1Series: SeriesData = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()

    var maxBGIValue = Double.MIN_VALUE
    val bgiScale = Scale()
    var minusBgiSeries: SeriesData = FixedLineGraphSeries<ScaledDataPoint>()
    var minusBgiHistSeries: SeriesData = FixedLineGraphSeries<ScaledDataPoint>()

    var maxCobValueFound = Double.MIN_VALUE
    val cobScale = Scale()
    var cobSeries: SeriesData = FixedLineGraphSeries<ScaledDataPoint>()
    var cobMinFailOverSeries: SeriesData = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()

    var maxDevValueFound = Double.MIN_VALUE
    val devScale = Scale()
    var deviationsSeries: SeriesData = BarGraphSeries<DeviationDataPointLegacy>()

    var maxRatioValueFound = 5.0
    var minRatioValueFound = -maxRatioValueFound
    val ratioScale = Scale()
    var ratioSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()

    var maxFromMaxValueFound = Double.MIN_VALUE
    var maxFromMinValueFound = Double.MIN_VALUE
    val dsMaxScale = Scale()
    val dsMinScale = Scale()
    var dsMaxSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()
    var dsMinSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()
    var heartRateScale = Scale()
    var heartRateGraphSeries: SeriesData = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
    var stepsForScale = Scale()
    var stepsCountGraphSeries: SeriesData = PointsWithLabelGraphSeries<StepsDataPoint>()

    var maxVarSensValueFound = 200.0
    var minVarSensValueFound = 50.0
    val varSensScale = Scale()
    var varSensSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()

    // AutoISF interim results
    var maxIobThValueFound = Double.MIN_VALUE
    var minIobThValueFound = 0.0
    var iobThSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()

    var maxAcceIsfValueFound = 1.5
    var minAcceIsfValueFound = 0.5
    val acceIsfScale = Scale()
    var acceIsfSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()

    var maxBgIsfValueFound = 1.5
    var minBgIsfValueFound = 0.5
    val bgIsfScale = Scale()
    var bgIsfSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()

    var maxPpIsfValueFound = 1.5
    var minPpIsfValueFound = 0.5
    val ppIsfScale = Scale()
    var ppIsfSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()

    var maxDuraIsfValueFound = 1.5
    var minDuraIsfValueFound = 0.5
    val duraIsfScale = Scale()
    var duraIsfSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()

    var maxFinalIsfValueFound = 1.5
    var minFinalIsfValueFound = 0.5
    val finalIsfScale = Scale()
    var finalIsfSeries: SeriesData = LineGraphSeries<ScaledDataPoint>()

    fun replacePredictionGraphSeriesFromWorker(points: List<BgDataPoint>) {
        if (points.isEmpty()) {
            predictionsGraphSeries = PointsWithLabelGraphSeries<DataPointWithLabelInterface>()
            return
        }
        val arr = points.map { p ->
            val mgdl = p.value
            val gv = GV(
                timestamp = p.timestamp,
                raw = mgdl,
                value = mgdl,
                trendArrow = TrendArrow.NONE,
                noise = 0.0,
                sourceSensor = sourceSensorForBgType(p.type),
            )
            GlucoseValueDataPoint(gv, profileUtil, rh, dateUtil)
        }.toTypedArray()
        predictionsGraphSeries = PointsWithLabelGraphSeries(arr)
    }

    private fun sourceSensorForBgType(type: BgType): SourceSensor = when (type) {
        BgType.IOB_PREDICTION   -> SourceSensor.IOB_PREDICTION
        BgType.COB_PREDICTION   -> SourceSensor.COB_PREDICTION
        BgType.A_COB_PREDICTION -> SourceSensor.A_COB_PREDICTION
        BgType.UAM_PREDICTION   -> SourceSensor.UAM_PREDICTION
        BgType.ZT_PREDICTION    -> SourceSensor.ZT_PREDICTION
        BgType.REGULAR,
        BgType.BUCKETED         -> SourceSensor.IOB_PREDICTION
    }
}
