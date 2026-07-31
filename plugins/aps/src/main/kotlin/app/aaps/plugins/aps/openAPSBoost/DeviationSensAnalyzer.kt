package app.aaps.plugins.aps.openAPSBoost

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.stats.TddCalculator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deviation-based sensitivity adjustment for the Boost APS engine.
 *
 * Compares observed BG readings against expected values over an 8-hour sliding window
 * to detect systematic insulin-sensitivity drift. When the user's BG consistently
 * runs above or below target in non-meal periods, the algorithm adjusts ISF to compensate.
 *
 * Based on the original tim2000s Boost implementation.
 *
 * @see RT.deviationSensRatio
 * @see RT.deviationSensSource
 */
@Singleton
class DeviationSensAnalyzer @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val tddCalculator: TddCalculator,
    private val aapsLogger: AAPSLogger
) {

    /**
     * Result of the deviation-based sensitivity analysis.
     *
     * @property ratio Applied ratio (> 1 = more resistant, < 1 = more sensitive)
     * @property source "deviation" or "tdd_fallback" or "none"
     * @property cleanCount Number of clean (non-meal-affected) entries in the 8H window
     * @property totalCount Total entries in the 8H window
     */
    data class DeviationSensResult(
        val ratio: Double,
        val source: String,
        val cleanCount: Int,
        val totalCount: Int
    )

    companion object {
        /** Analysis window in milliseconds (8 hours). */
        private const val WINDOW_MS = 8L * 3600 * 1000L

        /** Meal look-back: a reading is "meal-affected" if it falls within 3H of carb entry. */
        private const val MEAL_LOOKBACK_MS = 3L * 3600 * 1000L

        /** Minimum clean entries required; below this we fall back to TDD ratio. */
        private const val MIN_CLEAN_COUNT = 10

        /**
         * Scaling factor for converting mean BG deviation (mg/dL) to a sensitivity ratio.
         *
         * A consistent +10 mg/dL deviation produces ~1.05 ratio (5 % more resistant);
         * a consistent -10 mg/dL produces ~0.95 ratio (5 % more sensitive).
         */
        private const val DEV_SCALING_FACTOR = 200.0

        /** Minimum and maximum allowed ratio — safety bounds. */
        private const val MIN_RATIO = 0.7
        private const val MAX_RATIO = 1.3

        /** BG rising faster than this (mg/dL per minute) is flagged as meal-affected. */
        private const val RISING_THRESHOLD_MGDL_PER_MIN = 2.0
    }

    /**
     * Run the deviation-based sensitivity analysis for the window ending at [now].
     *
     * Returns a [DeviationSensResult] with the sensitivity ratio and metadata.
     * Falls back to a TDD-based ratio when there aren't enough clean BG entries.
     */
    suspend fun analyze(now: Long): DeviationSensResult {
        val windowStart = now - WINDOW_MS
        val mealLookbackStart = now - WINDOW_MS - MEAL_LOOKBACK_MS

        // ── 1. Fetch BG readings ──
        val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(windowStart, now, ascending = true)
        if (readings.size < 2) {
            aapsLogger.debug(LTag.APS, "DeviationSens: insufficient BG readings (${readings.size}) — using TDD fallback")
            return tddFallback(now)
        }

        // ── 2. Fetch carb entries to identify meal windows ──
        val carbEntries = persistenceLayer.getCarbsFromTimeToTimeExpanded(
            mealLookbackStart, now, ascending = true
        )

        // ── 3. Get profile target ──
        val profile = profileFunction.getProfile()
        val targetBg = profile?.getTargetMgdl()
        if (targetBg == null) {
            aapsLogger.debug(LTag.APS, "DeviationSens: no profile target available — using TDD fallback")
            return tddFallback(now)
        }

        // ── 4. Classify each reading as clean or meal-affected ──
        val deviations = mutableListOf<Double>()
        var totalCount = 0
        var cleanCount = 0

        for (i in 1 until readings.size) {
            totalCount++
            val reading = readings[i]
            val prevReading = readings[i - 1]
            val bg = reading.value
            val prevBg = prevReading.value
            val delta = bg - prevBg
            val timeDeltaMs = reading.timestamp - prevReading.timestamp
            // Guard against zero or negative intervals (unlikely, but defensive)
            val safeTimeDeltaMs = if (timeDeltaMs > 0) timeDeltaMs else 60_000L
            val deltaPerMin = delta / (safeTimeDeltaMs / 60_000.0)

            // Meal-affected: reading falls within 3H of a carb entry
            val inMealWindow = carbEntries.any { carb ->
                reading.timestamp in carb.timestamp..(carb.timestamp + MEAL_LOOKBACK_MS)
            }

            // Also flag if BG is rising faster than the threshold (suggests active carb absorption)
            val rapidRise = deltaPerMin > RISING_THRESHOLD_MGDL_PER_MIN

            val isMealAffected = inMealWindow || rapidRise

            if (!isMealAffected) {
                cleanCount++
                // Positive deviation = BG above target = more resistant
                deviations.add(bg - targetBg)
            }
        }

        if (readings.size - 1 != totalCount) {
            aapsLogger.warn(
                LTag.APS,
                "DeviationSens: count mismatch — readings.size=${readings.size}, totalCount=$totalCount"
            )
        }

        // ── 5. Check minimum clean count ──
        if (cleanCount < MIN_CLEAN_COUNT) {
            aapsLogger.debug(LTag.APS, "DeviationSens: only $cleanCount clean entries (< $MIN_CLEAN_COUNT) — using TDD fallback")
            return tddFallback(now)
        }

        // ── 6. Compute ratio ──
        val meanDeviation = deviations.sum() / cleanCount
        val ratio = (1.0 + meanDeviation / DEV_SCALING_FACTOR).coerceIn(MIN_RATIO, MAX_RATIO)

        aapsLogger.debug(
            LTag.APS,
            "DeviationSens: ratio=%.3f clean=$cleanCount/$totalCount meanDev=%.1f mg/dL".format(
                ratio, meanDeviation
            )
        )

        return DeviationSensResult(
            ratio = ratio,
            source = "deviation",
            cleanCount = cleanCount,
            totalCount = totalCount
        )
    }

    /**
     * Fallback: use TDD (total daily dose) ratio when deviation analysis is not possible.
     *
     * `ratio = TDD_last_24h / TDD_7d`, clamped to [MIN_RATIO, MAX_RATIO].
     * Returns `source = "none"` with ratio 1.0 when TDD data is unavailable.
     */
    private suspend fun tddFallback(now: Long): DeviationSensResult {
        val tdd7D = tddCalculator.averageTDD(
            tddCalculator.calculate(7, allowMissingDays = true)
        )?.data?.totalAmount

        val tddLast24H = tddCalculator.calculateDaily(-24, 0)?.totalAmount

        if (tdd7D != null && tdd7D > 0 && tddLast24H != null && tddLast24H > 0) {
            val ratio = (tddLast24H / tdd7D).coerceIn(MIN_RATIO, MAX_RATIO)
            aapsLogger.debug(
                LTag.APS,
                "DeviationSens: TDD fallback ratio=%.3f (24H=%.1f / 7D=%.1f)".format(ratio, tddLast24H, tdd7D)
            )
            return DeviationSensResult(ratio = ratio, source = "tdd_fallback", cleanCount = 0, totalCount = 0)
        }

        return DeviationSensResult(ratio = 1.0, source = "none", cleanCount = 0, totalCount = 0)
    }
}
