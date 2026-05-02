package app.aaps.plugins.aps.openAPSAutoISF.advisor

import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import java.util.Locale

/**
 * Heuristic rules for AutoISF SMB delivery parameters.
 * No AIMI dependency.
 */
class AutoIsfSmbAdvisor {

    fun analyze(metrics: AutoIsfMetrics, prefs: AutoIsfPrefsSnapshot): List<AutoIsfRecommendation> {
        val recs = mutableListOf<AutoIsfRecommendation>()
        val hypo = metrics.timeBelow70
        val hyper = metrics.timeAbove180

        // SMB delivery ratio — reduce on high hypo rate
        if (hypo >= 0.06 && prefs.smbDeliveryRatio > 0.40) {
            val proposed = (prefs.smbDeliveryRatio - 0.10).coerceAtLeast(0.1)
            if (proposed < prefs.smbDeliveryRatio - 0.04) {
                recs.add(
                    AutoIsfRecommendation(
                        title = "Reduce SMB delivery ratio",
                        description = "Hypo rate is ${pct(hypo)}%. Current SMB delivery ratio is ${fmt(prefs.smbDeliveryRatio)}. " +
                            "Reducing to ${fmt(proposed)} limits the SMB dose fraction to lower hypo risk.",
                        priority = AutoIsfPriority.High,
                        action = AutoIsfAction.PreferenceUpdate(
                            key = DoubleKey.ApsAutoIsfSmbDeliveryRatio,
                            newValue = proposed,
                            currentValue = prefs.smbDeliveryRatio,
                            reason = "Reduce SMB size while hypo pressure is elevated."
                        )
                    )
                )
            }
        }

        // SMB delivery ratio min — reduce if lows are frequent
        if (hypo >= 0.06 && prefs.smbDeliveryRatioMin > 0.30) {
            val proposed = (prefs.smbDeliveryRatioMin - 0.10).coerceAtLeast(0.0)
            if (proposed < prefs.smbDeliveryRatioMin - 0.04) {
                recs.add(
                    AutoIsfRecommendation(
                        title = "Reduce SMB delivery ratio minimum",
                        description = "Hypo rate is ${pct(hypo)}%. SMB delivery ratio min of ${fmt(prefs.smbDeliveryRatioMin)} " +
                            "sets a floor that keeps SMBs large even when BG is low. Consider ${fmt(proposed)}.",
                        priority = AutoIsfPriority.Medium,
                        action = AutoIsfAction.PreferenceUpdate(
                            key = DoubleKey.ApsAutoIsfSmbDeliveryRatioMin,
                            newValue = proposed,
                            currentValue = prefs.smbDeliveryRatioMin,
                            reason = "Lower floor allows AutoISF to reduce SMBs more aggressively when BG is falling."
                        )
                    )
                )
            }
        }

        // SMB delivery ratio — increase when hyper dominates and lows are minimal
        if (hyper >= 0.25 && hypo < 0.04 && prefs.smbDeliveryRatio < 0.60) {
            val proposed = (prefs.smbDeliveryRatio + 0.10).coerceAtMost(1.0)
            recs.add(
                AutoIsfRecommendation(
                    title = "Increase SMB delivery ratio",
                    description = "Time above 180 is ${pct(hyper)}% with low hypo risk (${pct(hypo)}%). " +
                        "Raising SMB delivery ratio from ${fmt(prefs.smbDeliveryRatio)} to ${fmt(proposed)} " +
                        "delivers a larger fraction of the calculated SMB.",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfSmbDeliveryRatio,
                        newValue = proposed,
                        currentValue = prefs.smbDeliveryRatio,
                        reason = "Persistent high BG with low hypo risk — allow larger SMB delivery."
                    )
                )
            )
        }

        // IOB threshold — lower when lows are frequent (limits further insulin when IOB is building)
        if (hypo >= 0.05 && prefs.iobThPercent > 80) {
            val proposed = (prefs.iobThPercent - 15).coerceAtLeast(30)
            if (proposed < prefs.iobThPercent - 5) {
                recs.add(
                    AutoIsfRecommendation(
                        title = "Lower IOB threshold",
                        description = "Frequent lows (${pct(hypo)}%). IOB threshold of ${prefs.iobThPercent}% " +
                            "allows delivery even with significant IOB on board. Reducing to ${proposed}% " +
                            "stops additional insulin sooner.",
                        priority = AutoIsfPriority.High,
                        action = AutoIsfAction.PreferenceUpdate(
                            key = IntKey.ApsAutoIsfIobThPercent,
                            newValue = proposed,
                            currentValue = prefs.iobThPercent,
                            reason = "Reduce IOB threshold to cut off SMBs earlier when hypo risk is elevated."
                        )
                    )
                )
            }
        }

        // IOB threshold — raise when hyper is dominant (allow more delivery with IOB on board)
        if (hyper >= 0.25 && hypo < 0.04 && prefs.iobThPercent < 100) {
            val proposed = (prefs.iobThPercent + 15).coerceAtMost(150)
            recs.add(
                AutoIsfRecommendation(
                    title = "Raise IOB threshold",
                    description = "Persistent hyperglycemia (${pct(hyper)}%) with low hypo risk. IOB threshold " +
                        "of ${prefs.iobThPercent}% may be cutting off SMBs too early. Raising to ${proposed}% " +
                        "allows more insulin when BG is elevated.",
                    priority = AutoIsfPriority.Low,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = IntKey.ApsAutoIsfIobThPercent,
                        newValue = proposed,
                        currentValue = prefs.iobThPercent,
                        reason = "Raise IOB threshold to allow SMBs in high BG situations."
                    )
                )
            )
        }

        return recs
    }

    private fun pct(v: Double) = (v * 100).toInt()
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
}
