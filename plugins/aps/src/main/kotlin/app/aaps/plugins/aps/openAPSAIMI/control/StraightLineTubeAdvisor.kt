package app.aaps.plugins.aps.openAPSAIMI.control

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import javax.inject.Inject
import javax.inject.Singleton
/**
 * **Straight-line tube advisor (MPC-lite)** — short-horizon, discrete-candidate control.
 *
 * Tunables: [DoubleKey.AimiTubeHypoFloorMgdl], [DoubleKey.AimiTubeHyperBandMgdl],
 * [DoubleKey.AimiTubeAggressiveness], [DoubleKey.AimiTubeBasalTrimMax], [DoubleKey.AimiTubeKappaSafetyMargin].
 *
 * **Basal coupling**: when SMB cap scale `s` is below 1, profile basal may be scaled by
 * `1 − trimMax·(1−s)` (floor 0.88). On hypo veto (`!feasible`), uses full `trimMax` against basal.
 */
@Singleton
class StraightLineTubeAdvisor @Inject constructor(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) {

    private var lastScale: Double = 1.0

    data class Input(
        val bgMgdl: Double,
        val deltaMgdlPer5m: Double,
        val iobU: Double,
        val cobG: Double,
        val isfMgdlPerU: Double,
        val diaHours: Double,
        val targetMgdl: Double,
        val maxSmbU: Double,
        val minPredictedBg: Double?,
        val eventualBgMgdl: Double,
    )

    data class Outcome(
        val smbCapScale: Double,
        /** Multiply `profile.current_basal` and `profile.max_daily_basal` (typically ≤ 1). */
        val basalCapScale: Double,
        val feasible: Boolean,
        val chosenCost: Double,
        val reason: String,
    )

    companion object {
        private const val TAG = "StraightLineTube"
        private const val BASAL_SCALE_FLOOR = 0.88

        /** Marginal drop in *min predicted BG* per 1 U SMB (mg/dL per U), ISF-scaled, clamped. */
        fun kappaMinPredDropPerUnit(isfMgdlPerU: Double): Double {
            val isf = isfMgdlPerU.coerceIn(25.0, 400.0)
            return (8.0 + 22.0 * (50.0 / isf)).coerceIn(8.0, 45.0)
        }

        private val CANDIDATES = doubleArrayOf(1.0, 0.85, 0.7, 0.55, 0.4, 0.25, 0.1, 0.0)

        private const val W_BG = 0.35
        private const val W_EV = 0.55
        private const val W_S_BASE = 6.0
        private const val W_DS_BASE = 4.0
        private const val W_HYPER_BASE = 0.85
        private const val W_IOB_STACK = 0.45
    }

    private fun basalCapScale(smbScale: Double, feasible: Boolean, trimMax: Double): Double {
        if (trimMax <= 1e-9) return 1.0
        val deficit = if (feasible) (1.0 - smbScale).coerceIn(0.0, 1.0) else 1.0
        return (1.0 - trimMax * deficit).coerceIn(BASAL_SCALE_FLOOR, 1.0)
    }

    fun advise(input: Input): Outcome {
        val hypoFloor = preferences.get(DoubleKey.AimiTubeHypoFloorMgdl)
        val hyperBand = preferences.get(DoubleKey.AimiTubeHyperBandMgdl)
        val agg = preferences.get(DoubleKey.AimiTubeAggressiveness).coerceIn(0.5, 2.0)
        val basalTrimMax = preferences.get(DoubleKey.AimiTubeBasalTrimMax).coerceIn(0.0, 0.25)
        val kappaMargin = preferences.get(DoubleKey.AimiTubeKappaSafetyMargin).coerceIn(0.0, 0.35)

        val wS = W_S_BASE / agg
        val wDs = W_DS_BASE / agg
        val wHyper = W_HYPER_BASE * agg

        val minPred = input.minPredictedBg
        if (minPred == null || !minPred.isFinite()) {
            return Outcome(1.0, 1.0, true, 0.0, "SKIP(no_minPred)")
        }
        val dia = input.diaHours.coerceIn(3.0, 9.0)
        val kappa = kappaMinPredDropPerUnit(input.isfMgdlPerU) *
            (6.0 / dia).coerceIn(0.82, 1.18) *
            (1.0 + kappaMargin)
        val smbMax = input.maxSmbU.coerceAtLeast(0.0)
        if (smbMax <= 1e-6) {
            return Outcome(1.0, 1.0, true, 0.0, "SKIP(no_maxSmb)")
        }

        val T = input.targetMgdl
        val bgErr = input.bgMgdl - T
        val evErr = input.eventualBgMgdl - T
        val hyperExcess = (input.eventualBgMgdl - (T + hyperBand)).coerceAtLeast(0.0)

        var bestS = 0.0
        var bestCost = Double.POSITIVE_INFINITY
        var anyFeasible = false

        for (s in CANDIDATES) {
            val minAfter = minPred - s * smbMax * kappa
            if (minAfter < hypoFloor) continue
            anyFeasible = true

            val iobStack = (input.iobU - 1.5).coerceAtLeast(0.0)
            val j = W_BG * bgErr * bgErr +
                W_EV * evErr * evErr +
                wS * s * s +
                wDs * (s - lastScale) * (s - lastScale) +
                wHyper * hyperExcess * hyperExcess * (1.0 - s) +
                W_IOB_STACK * iobStack * s

            if (j < bestCost) {
                bestCost = j
                bestS = s
            }
        }

        if (!anyFeasible) {
            lastScale = 0.0
            val bScale = basalCapScale(0.0, feasible = false, trimMax = basalTrimMax)
            val msg =
                "VETO hypoTube minPred=${"%.0f".format(minPred)} floor=${"%.0f".format(hypoFloor)} κ=${"%.1f".format(kappa)} maxSmb=${"%.2f".format(smbMax)} basal×${"%.2f".format(bScale)}"
            aapsLogger.warn(LTag.APS, "[$TAG] $msg")
            return Outcome(
                smbCapScale = 0.0,
                basalCapScale = bScale,
                feasible = false,
                chosenCost = Double.POSITIVE_INFINITY,
                reason = msg
            )
        }

        lastScale = bestS
        val minAfterBest = minPred - bestS * smbMax * kappa
        val bScale = basalCapScale(bestS, feasible = true, trimMax = basalTrimMax)
        val reason =
            "s=${"%.2f".format(bestS)} basal×${"%.2f".format(bScale)} J=${"%.1f".format(bestCost)} " +
                "minPred=${"%.0f".format(minPred)}→${"%.0f".format(minAfterBest)} ev=${"%.0f".format(input.eventualBgMgdl)} " +
                "κ=${"%.1f".format(kappa)} COB=${"%.1f".format(input.cobG)} IOB=${"%.2f".format(input.iobU)}"
        aapsLogger.info(LTag.APS, "[$TAG] $reason")
        return Outcome(
            smbCapScale = bestS,
            basalCapScale = bScale,
            feasible = true,
            chosenCost = bestCost,
            reason = reason
        )
    }
}
