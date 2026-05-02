package app.aaps.plugins.aps.openAPSAutoISF.advisor

import app.aaps.core.keys.DoubleKey
import java.util.Locale

/**
 * Heuristic rules for AutoISF ISF weight parameters.
 * Reads glycemic metrics and proposes targeted DoubleKey changes.
 * No AIMI dependency.
 */
class AutoIsfWeightsAdvisor {

    fun analyze(metrics: AutoIsfMetrics, prefs: AutoIsfPrefsSnapshot): List<AutoIsfRecommendation> {
        val recs = mutableListOf<AutoIsfRecommendation>()
        val hypo = metrics.timeBelow70
        val hyper = metrics.timeAbove180
        val tir = metrics.tir70_180

        // Weights not active — suggest enabling them first
        if (!prefs.useAutoIsfWeights && tir < 0.70) {
            recs.add(
                AutoIsfRecommendation(
                    title = "Enable AutoISF weights",
                    description = "AutoISF weights are disabled. Enabling them allows dynamic ISF adjustment " +
                        "based on glucose acceleration, post-prandial patterns, and duration — which may improve " +
                        "your TIR (currently ${pct(tir)}%).",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = app.aaps.core.keys.BooleanKey.ApsUseAutoIsfWeights,
                        newValue = true,
                        currentValue = false,
                        reason = "Low TIR suggests potential benefit from dynamic ISF weighting."
                    )
                )
            )
            return recs  // Other weight tuning is moot if weights are off
        }

        // HighBG weight — drives sensitivity increase at high glucose
        if (hyper >= 0.20 && hypo < 0.05) {
            val proposed = (prefs.highBgWeight + 0.10).coerceAtMost(3.0)
            if (proposed > prefs.highBgWeight + 0.04) {
                recs.add(
                    AutoIsfRecommendation(
                        title = "Increase HighBG weight",
                        description = "Time above 180 is ${pct(hyper)}% with low hypo risk (${pct(hypo)}%). " +
                            "Raising HighBG weight from ${fmt(prefs.highBgWeight)} to ${fmt(proposed)} " +
                            "increases ISF sensitivity when BG is elevated.",
                        priority = AutoIsfPriority.Medium,
                        action = AutoIsfAction.PreferenceUpdate(
                            key = DoubleKey.ApsAutoIsfHighBgWeight,
                            newValue = proposed,
                            currentValue = prefs.highBgWeight,
                            reason = "Persistent hyperglycemia with low hypo risk."
                        )
                    )
                )
            }
        }

        // HighBG weight — reduce when causing lows
        if (hypo >= 0.05 && prefs.highBgWeight > 1.0) {
            val proposed = (prefs.highBgWeight - 0.15).coerceAtLeast(0.0)
            if (proposed < prefs.highBgWeight - 0.05) {
                recs.add(
                    AutoIsfRecommendation(
                        title = "Reduce HighBG weight",
                        description = "Frequent lows (${pct(hypo)}%). HighBG weight of ${fmt(prefs.highBgWeight)} " +
                            "may be contributing via over-aggressive sensitivity increase. Consider ${fmt(proposed)}.",
                        priority = AutoIsfPriority.High,
                        action = AutoIsfAction.PreferenceUpdate(
                            key = DoubleKey.ApsAutoIsfHighBgWeight,
                            newValue = proposed,
                            currentValue = prefs.highBgWeight,
                            reason = "Reduce sensitivity at high BG to limit post-correction lows."
                        )
                    )
                )
            }
        }

        // LowBG weight — reduce when causing lows (it normally lowers ISF when BG is low, i.e. more insulin)
        if (hypo >= 0.05 && prefs.lowBgWeight > 0.3) {
            val proposed = (prefs.lowBgWeight - 0.10).coerceAtLeast(0.0)
            if (proposed < prefs.lowBgWeight - 0.04) {
                recs.add(
                    AutoIsfRecommendation(
                        title = "Reduce LowBG weight",
                        description = "Hypo rate is ${pct(hypo)}%. LowBG weight of ${fmt(prefs.lowBgWeight)} " +
                            "makes AutoISF more aggressive when BG is already falling. Reducing to ${fmt(proposed)} " +
                            "lowers that aggression.",
                        priority = AutoIsfPriority.High,
                        action = AutoIsfAction.PreferenceUpdate(
                            key = DoubleKey.ApsAutoIsfLowBgWeight,
                            newValue = proposed,
                            currentValue = prefs.lowBgWeight,
                            reason = "Reduce insulin delivery contribution when BG is falling."
                        )
                    )
                )
            }
        }

        // Post-prandial weight — increase when meals cause sustained highs but overall hypo is low
        if (hyper >= 0.20 && hypo < 0.04 && tir < 0.70 && prefs.ppWeight < 1.5) {
            val proposed = (prefs.ppWeight + 0.15).coerceAtMost(3.0)
            recs.add(
                AutoIsfRecommendation(
                    title = "Increase post-prandial weight",
                    description = "TIR is ${pct(tir)}% with ${pct(hyper)}% above 180. Increasing PpWeight from " +
                        "${fmt(prefs.ppWeight)} to ${fmt(proposed)} strengthens the post-meal ISF correction.",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfPpWeight,
                        newValue = proposed,
                        currentValue = prefs.ppWeight,
                        reason = "Post-prandial correction may be insufficient."
                    )
                )
            )
        }

        // Duration weight — increase for prolonged highs
        if (hyper >= 0.25 && hypo < 0.04 && prefs.duraWeight < 1.5) {
            val proposed = (prefs.duraWeight + 0.15).coerceAtMost(3.0)
            recs.add(
                AutoIsfRecommendation(
                    title = "Increase duration weight",
                    description = "Time above 180 is ${pct(hyper)}% — prolonged high glucose pattern. Raising " +
                        "DuraWeight from ${fmt(prefs.duraWeight)} to ${fmt(proposed)} increases ISF the longer BG stays elevated.",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfDuraWeight,
                        newValue = proposed,
                        currentValue = prefs.duraWeight,
                        reason = "Prolonged hyperglycemia suggests insufficient duration-based correction."
                    )
                )
            )
        }

        // BG acceleration weight — tune down if oscillating (high variability)
        val variability = hypo + hyper  // Simple proxy for glycemic variability
        if (variability > 0.40 && prefs.bgAccelWeight > 0.5) {
            val proposed = (prefs.bgAccelWeight - 0.10).coerceAtLeast(0.0)
            if (proposed < prefs.bgAccelWeight - 0.04) {
                recs.add(
                    AutoIsfRecommendation(
                        title = "Tune acceleration weight",
                        description = "High glycemic variability (hypo ${pct(hypo)}% + hyper ${pct(hyper)}%). " +
                            "Reducing BgAccelWeight from ${fmt(prefs.bgAccelWeight)} to ${fmt(proposed)} may smooth " +
                            "the ISF response to rapid BG changes.",
                        priority = AutoIsfPriority.Low,
                        action = AutoIsfAction.PreferenceUpdate(
                            key = DoubleKey.ApsAutoIsfBgAccelWeight,
                            newValue = proposed,
                            currentValue = prefs.bgAccelWeight,
                            reason = "High variability suggests over-reaction to acceleration."
                        )
                    )
                )
            }
        }

        // AutoISF max bound — raise if hyper is persistent and max is at default/low
        if (hyper >= 0.25 && hypo < 0.04 && prefs.autoIsfMax <= 1.2) {
            val proposed = (prefs.autoIsfMax + 0.10).coerceAtMost(2.0)
            recs.add(
                AutoIsfRecommendation(
                    title = "Raise AutoISF upper bound",
                    description = "Persistent hyperglycemia (${pct(hyper)}%) with ISF max at ${fmt(prefs.autoIsfMax)}. " +
                        "Raising the cap to ${fmt(proposed)} gives AutoISF more headroom to correct highs.",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfMax,
                        newValue = proposed,
                        currentValue = prefs.autoIsfMax,
                        reason = "ISF upper bound may be capping the correction in persistent high BG."
                    )
                )
            )
        }

        // AutoISF min bound — lower if lows are frequent
        if (hypo >= 0.05 && prefs.autoIsfMin >= 0.7) {
            val proposed = (prefs.autoIsfMin - 0.10).coerceAtLeast(0.1)
            if (proposed < prefs.autoIsfMin - 0.04) {
                recs.add(
                    AutoIsfRecommendation(
                        title = "Lower AutoISF lower bound",
                        description = "Frequent lows (${pct(hypo)}%). The ISF floor of ${fmt(prefs.autoIsfMin)} " +
                            "prevents AutoISF from reducing insulin adequately. Lowering to ${fmt(proposed)} " +
                            "allows more sensitivity reduction when BG is falling.",
                        priority = AutoIsfPriority.High,
                        action = AutoIsfAction.PreferenceUpdate(
                            key = DoubleKey.ApsAutoIsfMin,
                            newValue = proposed,
                            currentValue = prefs.autoIsfMin,
                            reason = "ISF lower bound may be preventing adequate insulin reduction."
                        )
                    )
                )
            }
        }

        return recs
    }

    private fun pct(v: Double) = (v * 100).toInt()
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
}
