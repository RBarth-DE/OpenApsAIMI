package app.aaps.plugins.aps.openAPSAutoISF.advisor

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Collects AutoISF glycemic metrics and delegates to sub-advisors for heuristic recommendations.
 * Completely independent of AIMI — only depends on stable core interfaces.
 */
class AutoIsfAdvisorService(
    private val profileFunction: ProfileFunction? = null,
    private val persistenceLayer: PersistenceLayer? = null,
    private val preferences: Preferences? = null,
    private val tddCalculator: TddCalculator? = null,
    private val tirCalculator: TirCalculator? = null
) {

    private val weightsAdvisor = AutoIsfWeightsAdvisor()
    private val smbAdvisor = AutoIsfSmbAdvisor()

    fun generateReport(periodDays: Int = 10): AutoIsfReport {
        val metrics = calculateMetrics(periodDays)
        val prefs = collectPrefs()
        val score = computeScore(metrics)
        val severity = classifySeverity(score)
        val recs = generateRecommendations(metrics, prefs).toMutableList()

        if (recs.isEmpty()) {
            recs.add(
                AutoIsfRecommendation(
                    title = "AutoISF is well tuned",
                    description = "No significant adjustments needed based on the last $periodDays days. " +
                        "TIR ${pct(metrics.tir70_180)}% · Hypos ${pct(metrics.timeBelow70)}% · Mean ${metrics.meanBg.toInt()} mg/dL.",
                    priority = AutoIsfPriority.Low
                )
            )
        }

        return AutoIsfReport(
            generatedAt = System.currentTimeMillis(),
            metrics = metrics,
            overallScore = score,
            overallSeverity = severity,
            recommendations = recs
        )
    }

    private fun generateRecommendations(
        metrics: AutoIsfMetrics,
        prefs: AutoIsfPrefsSnapshot
    ): List<AutoIsfRecommendation> {
        val all = mutableListOf<AutoIsfRecommendation>()
        all += weightsAdvisor.analyze(metrics, prefs)
        all += smbAdvisor.analyze(metrics, prefs)
        // Sort: Critical first, then High, Medium, Low
        return all.sortedBy { it.priority.ordinal }
    }

    fun calculateMetrics(days: Int): AutoIsfMetrics = runBlocking(Dispatchers.IO) {
        var tir70_180 = 0.65
        var tir70_140 = 0.40
        var timeBelow70 = 0.05
        var timeBelow54 = 0.01
        var timeAbove180 = 0.30
        var timeAbove250 = 0.05
        var meanBg = 160.0
        var tdd = 45.0
        var basalPercent = 0.50
        var todayTir: Double? = null
        var todayTdd: Double? = null

        tirCalculator?.runCatching {
            val tirs180 = calculate(days.toLong(), 70.0, 180.0)
            averageTIR(tirs180)?.let {
                tir70_180 = (it.inRangePct() ?: 0.0) / 100.0
                timeBelow70 = (it.belowPct() ?: 0.0) / 100.0
                timeAbove180 = (it.abovePct() ?: 0.0) / 100.0
            }

            val tirs54 = calculate(days.toLong(), 54.0, 180.0)
            averageTIR(tirs54)?.let { timeBelow54 = (it.belowPct() ?: 0.0) / 100.0 }

            val tirs250 = calculate(days.toLong(), 70.0, 250.0)
            averageTIR(tirs250)?.let { timeAbove250 = (it.abovePct() ?: 0.0) / 100.0 }

            val tirs140 = calculate(days.toLong(), 70.0, 140.0)
            averageTIR(tirs140)?.let { tir70_140 = (it.inRangePct() ?: 0.0) / 100.0 }

            val daily = calculateDaily(70.0, 180.0)
            if (daily != null && daily.size() > 0) {
                var maxKey = 0L
                var latest: app.aaps.core.interfaces.stats.TIR? = null
                for (i in 0 until daily.size()) {
                    val k = daily.keyAt(i)
                    if (k > maxKey) { maxKey = k; latest = daily.valueAt(i) }
                }
                latest?.let { todayTir = (it.inRangePct() ?: 0.0) / 100.0 }
            }
        }

        tddCalculator?.runCatching {
            val tdds = calculate(days.toLong(), true)
            averageTDD(tdds)?.let {
                tdd = it.data.totalAmount
                if (tdd > 0) basalPercent = it.data.basalAmount / tdd
            }
            calculateToday()?.let { todayTdd = it.totalAmount }
        }

        persistenceLayer?.runCatching {
            val now = System.currentTimeMillis()
            val from = now - days * 24 * 3600 * 1000L
            val readings = getBgReadingsDataFromTimeToTime(from, now, ascending = false)
                .map { it.value }.filter { it > 30.0 }
            if (readings.isNotEmpty()) meanBg = readings.average()
        }

        AutoIsfMetrics(
            periodLabel = "Last $days days",
            tir70_180 = tir70_180,
            tir70_140 = tir70_140,
            timeBelow70 = timeBelow70,
            timeBelow54 = timeBelow54,
            timeAbove180 = timeAbove180,
            timeAbove250 = timeAbove250,
            meanBg = meanBg,
            gmi = 3.31 + 0.02392 * meanBg,
            tdd = tdd,
            basalPercent = basalPercent,
            todayTir = todayTir,
            todayTdd = todayTdd
        )
    }

    fun collectPrefs(): AutoIsfPrefsSnapshot {
        val p = preferences ?: return defaultPrefs()
        return AutoIsfPrefsSnapshot(
            useAutoIsfWeights = p.get(BooleanKey.ApsUseAutoIsfWeights),
            autoIsfMin = p.get(DoubleKey.ApsAutoIsfMin),
            autoIsfMax = p.get(DoubleKey.ApsAutoIsfMax),
            bgAccelWeight = p.get(DoubleKey.ApsAutoIsfBgAccelWeight),
            bgBrakeWeight = p.get(DoubleKey.ApsAutoIsfBgBrakeWeight),
            lowBgWeight = p.get(DoubleKey.ApsAutoIsfLowBgWeight),
            highBgWeight = p.get(DoubleKey.ApsAutoIsfHighBgWeight),
            ppWeight = p.get(DoubleKey.ApsAutoIsfPpWeight),
            duraWeight = p.get(DoubleKey.ApsAutoIsfDuraWeight),
            iobThPercent = p.get(IntKey.ApsAutoIsfIobThPercent),
            smbDeliveryRatio = p.get(DoubleKey.ApsAutoIsfSmbDeliveryRatio),
            smbDeliveryRatioMin = p.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMin),
            smbDeliveryRatioMax = p.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMax),
            smbDeliveryRatioBgRange = p.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange),
            smbMaxRangeExtension = p.get(DoubleKey.ApsAutoIsfSmbMaxRangeExtension),
            autosensMax = p.get(DoubleKey.AutosensMax)
        )
    }

    private fun computeScore(m: AutoIsfMetrics): Double {
        val tirScore = m.tir70_180 * 10.0
        val hypoScore = (1.0 - (m.timeBelow70 * 5).coerceAtMost(1.0)) * 10.0
        val hyperScore = (1.0 - m.timeAbove180) * 10.0
        return (tirScore * 0.5) + (hypoScore * 0.3) + (hyperScore * 0.2)
    }

    private fun classifySeverity(score: Double): AutoIsfSeverity = when {
        score >= 7.0 -> AutoIsfSeverity.Good
        score >= 4.0 -> AutoIsfSeverity.Warning
        else -> AutoIsfSeverity.Critical
    }

    private fun defaultPrefs() = AutoIsfPrefsSnapshot(
        useAutoIsfWeights = false,
        autoIsfMin = 0.5, autoIsfMax = 1.2,
        bgAccelWeight = 0.5, bgBrakeWeight = 0.5,
        lowBgWeight = 0.5, highBgWeight = 0.5,
        ppWeight = 0.5, duraWeight = 0.5,
        iobThPercent = 100,
        smbDeliveryRatio = 0.5, smbDeliveryRatioMin = 0.3, smbDeliveryRatioMax = 0.7,
        smbDeliveryRatioBgRange = 40.0, smbMaxRangeExtension = 2.0,
        autosensMax = 1.2
    )

    private fun pct(v: Double) = (v * 100).toInt()
}
