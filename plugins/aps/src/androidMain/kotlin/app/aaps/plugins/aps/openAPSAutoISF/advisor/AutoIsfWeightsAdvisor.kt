package app.aaps.plugins.aps.openAPSAutoISF.advisor

import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.snapToStep
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
            return recs
        }

        // ── HighBG weight ──────────────────────────────────────────────────────────
        if (hyper >= 0.20 && hypo < 0.05) {
            val proposed = DoubleKey.ApsAutoIsfHighBgWeight.snapToStep(prefs.highBgWeight + 0.10)
            if (proposed > prefs.highBgWeight + 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Increase HighBG weight",
                    description = "Time above 180 is ${pct(hyper)}% with low hypo risk (${pct(hypo)}%). " +
                        "Raising HighBG weight from ${fmt(prefs.highBgWeight)} to ${fmt(proposed)} " +
                        "increases ISF sensitivity when BG is elevated.",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfHighBgWeight, newValue = proposed,
                        currentValue = prefs.highBgWeight,
                        reason = "Persistent hyperglycemia with low hypo risk."
                    )
                ))
            }
        }

        if (hypo >= 0.05 && prefs.highBgWeight > DoubleKey.ApsAutoIsfHighBgWeight.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfHighBgWeight.snapToStep(prefs.highBgWeight - 0.10)
            if (proposed < prefs.highBgWeight - 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Reduce HighBG weight",
                    description = "Frequent lows (${pct(hypo)}%). HighBG weight of ${fmt(prefs.highBgWeight)} " +
                        "may be contributing via over-aggressive sensitivity increase. Consider ${fmt(proposed)}.",
                    priority = AutoIsfPriority.High,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfHighBgWeight, newValue = proposed,
                        currentValue = prefs.highBgWeight,
                        reason = "Reduce sensitivity at high BG to limit post-correction lows."
                    )
                ))
            }
        }

        // ── LowBG weight ───────────────────────────────────────────────────────────
        if (hyper >= 0.20 && hypo < 0.04 && prefs.lowBgWeight < DoubleKey.ApsAutoIsfLowBgWeight.max) {
            val proposed = DoubleKey.ApsAutoIsfLowBgWeight.snapToStep(prefs.lowBgWeight + 0.10)
            if (proposed > prefs.lowBgWeight + 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Increase LowBG weight",
                    description = "Persistent hyper (${pct(hyper)}%) with low hypo risk (${pct(hypo)}%). " +
                        "Raising LowBG weight from ${fmt(prefs.lowBgWeight)} to ${fmt(proposed)} " +
                        "increases insulin delivery when BG is in the lower range.",
                    priority = AutoIsfPriority.Low,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfLowBgWeight, newValue = proposed,
                        currentValue = prefs.lowBgWeight,
                        reason = "Increase aggressiveness in lower BG range to help pull down persistent highs."
                    )
                ))
            }
        }

        if (hypo >= 0.05 && prefs.lowBgWeight > DoubleKey.ApsAutoIsfLowBgWeight.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfLowBgWeight.snapToStep(prefs.lowBgWeight - 0.10)
            if (proposed < prefs.lowBgWeight - 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Reduce LowBG weight",
                    description = "Hypo rate is ${pct(hypo)}%. LowBG weight of ${fmt(prefs.lowBgWeight)} " +
                        "makes AutoISF more aggressive when BG is already falling. Reducing to ${fmt(proposed)} " +
                        "lowers that aggression.",
                    priority = AutoIsfPriority.High,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfLowBgWeight, newValue = proposed,
                        currentValue = prefs.lowBgWeight,
                        reason = "Reduce insulin delivery contribution when BG is falling."
                    )
                ))
            }
        }

        // ── BgAccel weight ─────────────────────────────────────────────────────────
        if (hyper >= 0.20 && hypo < 0.04 && prefs.bgAccelWeight < DoubleKey.ApsAutoIsfBgAccelWeight.max) {
            val proposed = DoubleKey.ApsAutoIsfBgAccelWeight.snapToStep(prefs.bgAccelWeight + 0.05)
            if (proposed > prefs.bgAccelWeight + 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Increase BG acceleration weight",
                    description = "Persistent hyper (${pct(hyper)}%) with low hypo risk. " +
                        "Raising BgAccelWeight from ${fmt(prefs.bgAccelWeight)} to ${fmt(proposed)} " +
                        "makes AutoISF respond faster to rising BG.",
                    priority = AutoIsfPriority.Low,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfBgAccelWeight, newValue = proposed,
                        currentValue = prefs.bgAccelWeight,
                        reason = "Faster ISF response to BG rises may help limit post-meal peaks."
                    )
                ))
            }
        }

        val variability = hypo + hyper
        if (variability > 0.40 && prefs.bgAccelWeight > DoubleKey.ApsAutoIsfBgAccelWeight.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfBgAccelWeight.snapToStep(prefs.bgAccelWeight - 0.10)
            if (proposed < prefs.bgAccelWeight - 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Reduce BG acceleration weight",
                    description = "High glycemic variability (hypo ${pct(hypo)}% + hyper ${pct(hyper)}%). " +
                        "Reducing BgAccelWeight from ${fmt(prefs.bgAccelWeight)} to ${fmt(proposed)} may smooth " +
                        "the ISF response to rapid BG changes.",
                    priority = AutoIsfPriority.Low,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfBgAccelWeight, newValue = proposed,
                        currentValue = prefs.bgAccelWeight,
                        reason = "High variability suggests over-reaction to acceleration."
                    )
                ))
            }
        }

        // ── BgBrake weight ─────────────────────────────────────────────────────────
        if (hypo >= 0.05 && prefs.bgBrakeWeight < DoubleKey.ApsAutoIsfBgBrakeWeight.max) {
            val proposed = DoubleKey.ApsAutoIsfBgBrakeWeight.snapToStep(prefs.bgBrakeWeight + 0.05)
            if (proposed > prefs.bgBrakeWeight + 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Increase BG brake weight",
                    description = "Frequent lows (${pct(hypo)}%). Raising BgBrakeWeight from " +
                        "${fmt(prefs.bgBrakeWeight)} to ${fmt(proposed)} reduces ISF when BG is falling fast, " +
                        "limiting insulin delivery before a hypo.",
                    priority = AutoIsfPriority.High,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfBgBrakeWeight, newValue = proposed,
                        currentValue = prefs.bgBrakeWeight,
                        reason = "Stronger braking when BG is falling may reduce hypo frequency."
                    )
                ))
            }
        }

        if (hyper >= 0.20 && hypo < 0.04 && prefs.bgBrakeWeight > DoubleKey.ApsAutoIsfBgBrakeWeight.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfBgBrakeWeight.snapToStep(prefs.bgBrakeWeight - 0.05)
            if (proposed < prefs.bgBrakeWeight - 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Reduce BG brake weight",
                    description = "Persistent hyper (${pct(hyper)}%) with low hypo risk. " +
                        "BgBrakeWeight of ${fmt(prefs.bgBrakeWeight)} may be suppressing ISF corrections too early. " +
                        "Reducing to ${fmt(proposed)} allows more aggressiveness.",
                    priority = AutoIsfPriority.Low,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfBgBrakeWeight, newValue = proposed,
                        currentValue = prefs.bgBrakeWeight,
                        reason = "Reduce braking to allow stronger ISF correction at high BG."
                    )
                ))
            }
        }

        // ── Post-prandial weight ───────────────────────────────────────────────────
        if (hyper >= 0.20 && hypo < 0.04 && tir < 0.70 && prefs.ppWeight < DoubleKey.ApsAutoIsfPpWeight.max) {
            val proposed = DoubleKey.ApsAutoIsfPpWeight.snapToStep(prefs.ppWeight + 0.015)
            if (proposed > prefs.ppWeight + 0.004) {
                recs.add(AutoIsfRecommendation(
                    title = "Increase post-prandial weight",
                    description = "TIR is ${pct(tir)}% with ${pct(hyper)}% above 180. Increasing PpWeight from " +
                        "${fmt(prefs.ppWeight)} to ${fmt(proposed)} strengthens the post-meal ISF correction.",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfPpWeight, newValue = proposed,
                        currentValue = prefs.ppWeight,
                        reason = "Post-prandial correction may be insufficient."
                    )
                ))
            }
        }

        if (hypo >= 0.05 && prefs.ppWeight > DoubleKey.ApsAutoIsfPpWeight.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfPpWeight.snapToStep(prefs.ppWeight - 0.015)
            if (proposed < prefs.ppWeight - 0.004) {
                recs.add(AutoIsfRecommendation(
                    title = "Reduce post-prandial weight",
                    description = "Frequent lows (${pct(hypo)}%). PpWeight of ${fmt(prefs.ppWeight)} " +
                        "may be driving over-correction after meals. Reducing to ${fmt(proposed)}.",
                    priority = AutoIsfPriority.High,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfPpWeight, newValue = proposed,
                        currentValue = prefs.ppWeight,
                        reason = "Post-prandial ISF boost may be causing post-meal lows."
                    )
                ))
            }
        }

        // ── Duration weight ────────────────────────────────────────────────────────
        if (hyper >= 0.25 && hypo < 0.04 && prefs.duraWeight < DoubleKey.ApsAutoIsfDuraWeight.max) {
            val proposed = DoubleKey.ApsAutoIsfDuraWeight.snapToStep(prefs.duraWeight + 0.15)
            if (proposed > prefs.duraWeight + 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Increase duration weight",
                    description = "Time above 180 is ${pct(hyper)}% — prolonged high glucose pattern. Raising " +
                        "DuraWeight from ${fmt(prefs.duraWeight)} to ${fmt(proposed)} increases ISF the longer BG stays elevated.",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfDuraWeight, newValue = proposed,
                        currentValue = prefs.duraWeight,
                        reason = "Prolonged hyperglycemia suggests insufficient duration-based correction."
                    )
                ))
            }
        }

        if (hypo >= 0.05 && prefs.duraWeight > DoubleKey.ApsAutoIsfDuraWeight.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfDuraWeight.snapToStep(prefs.duraWeight - 0.15)
            if (proposed < prefs.duraWeight - 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Reduce duration weight",
                    description = "Frequent lows (${pct(hypo)}%). DuraWeight of ${fmt(prefs.duraWeight)} " +
                        "may be sustaining aggressive ISF too long after highs. Reducing to ${fmt(proposed)}.",
                    priority = AutoIsfPriority.High,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfDuraWeight, newValue = proposed,
                        currentValue = prefs.duraWeight,
                        reason = "Duration-based ISF boost may be causing delayed post-correction lows."
                    )
                ))
            }
        }

        // ── AutoISF max bound ──────────────────────────────────────────────────────
        if (hyper >= 0.25 && hypo < 0.04 && prefs.autoIsfMax < DoubleKey.ApsAutoIsfMax.max) {
            val proposed = DoubleKey.ApsAutoIsfMax.snapToStep(prefs.autoIsfMax + 0.10)
            if (proposed > prefs.autoIsfMax) {
                recs.add(AutoIsfRecommendation(
                    title = "Raise AutoISF upper bound",
                    description = "Persistent hyperglycemia (${pct(hyper)}%) with ISF max at ${fmt(prefs.autoIsfMax)}. " +
                        "Raising the cap to ${fmt(proposed)} gives AutoISF more headroom to correct highs.",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfMax, newValue = proposed,
                        currentValue = prefs.autoIsfMax,
                        reason = "ISF upper bound may be capping the correction in persistent high BG."
                    )
                ))
            }
        }

        if (hypo >= 0.05 && prefs.autoIsfMax > DoubleKey.ApsAutoIsfMax.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfMax.snapToStep(prefs.autoIsfMax - 0.10)
            if (proposed < prefs.autoIsfMax) {
                recs.add(AutoIsfRecommendation(
                    title = "Lower AutoISF upper bound",
                    description = "Frequent lows (${pct(hypo)}%) with ISF max at ${fmt(prefs.autoIsfMax)}. " +
                        "A high ceiling allows AutoISF to become very aggressive. " +
                        "Reducing to ${fmt(proposed)} limits over-correction.",
                    priority = AutoIsfPriority.High,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfMax, newValue = proposed,
                        currentValue = prefs.autoIsfMax,
                        reason = "Elevated ISF ceiling may contribute to post-correction lows."
                    )
                ))
            }
        }

        // ── AutoISF min bound ──────────────────────────────────────────────────────
        // Raise: only meaningful if previously lowered below default
        if (hyper >= 0.20 && hypo < 0.04 && prefs.autoIsfMin < DoubleKey.ApsAutoIsfMin.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfMin.snapToStep(prefs.autoIsfMin + 0.10)
            if (proposed > prefs.autoIsfMin + 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Raise AutoISF lower bound",
                    description = "Hyper (${pct(hyper)}%) with ISF min at ${fmt(prefs.autoIsfMin)} — below default. " +
                        "Raising to ${fmt(proposed)} prevents AutoISF from reducing insulin too aggressively.",
                    priority = AutoIsfPriority.Low,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfMin, newValue = proposed,
                        currentValue = prefs.autoIsfMin,
                        reason = "ISF floor may be set too low, preventing adequate insulin delivery."
                    )
                ))
            }
        }

        if (hypo >= 0.05 && prefs.autoIsfMin > DoubleKey.ApsAutoIsfMin.min) {
            val proposed = DoubleKey.ApsAutoIsfMin.snapToStep(prefs.autoIsfMin - 0.10)
            if (proposed < prefs.autoIsfMin - 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Lower AutoISF lower bound",
                    description = "Frequent lows (${pct(hypo)}%). The ISF floor of ${fmt(prefs.autoIsfMin)} " +
                        "prevents AutoISF from reducing insulin adequately. Lowering to ${fmt(proposed)} " +
                        "allows more sensitivity reduction when BG is falling.",
                    priority = AutoIsfPriority.High,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfMin, newValue = proposed,
                        currentValue = prefs.autoIsfMin,
                        reason = "ISF lower bound may be preventing adequate insulin reduction."
                    )
                ))
            }
        }

        // ── SMB delivery ratio ─────────────────────────────────────────────────────
        if (hyper >= 0.20 && hypo < 0.04 && prefs.smbDeliveryRatio < DoubleKey.ApsAutoIsfSmbDeliveryRatio.max) {
            val proposed = DoubleKey.ApsAutoIsfSmbDeliveryRatio.snapToStep(prefs.smbDeliveryRatio + 0.05)
            if (proposed > prefs.smbDeliveryRatio + 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Increase SMB delivery ratio",
                    description = "Persistent hyper (${pct(hyper)}%) with low hypo risk. " +
                        "Raising SMB ratio from ${fmt(prefs.smbDeliveryRatio)} to ${fmt(proposed)} " +
                        "delivers more insulin per SMB cycle.",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfSmbDeliveryRatio, newValue = proposed,
                        currentValue = prefs.smbDeliveryRatio,
                        reason = "Higher SMB ratio may improve correction of persistent highs."
                    )
                ))
            }
        }

        if (hypo >= 0.05 && prefs.smbDeliveryRatio > DoubleKey.ApsAutoIsfSmbDeliveryRatio.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfSmbDeliveryRatio.snapToStep(prefs.smbDeliveryRatio - 0.05)
            if (proposed < prefs.smbDeliveryRatio - 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Reduce SMB delivery ratio",
                    description = "Frequent lows (${pct(hypo)}%). SMB ratio of ${fmt(prefs.smbDeliveryRatio)} " +
                        "may be delivering too much insulin per cycle. Reducing to ${fmt(proposed)}.",
                    priority = AutoIsfPriority.High,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfSmbDeliveryRatio, newValue = proposed,
                        currentValue = prefs.smbDeliveryRatio,
                        reason = "Lower SMB ratio reduces per-cycle insulin delivery to limit lows."
                    )
                ))
            }
        }

        // ── SMB delivery ratio min ─────────────────────────────────────────────────
        if (hyper >= 0.20 && hypo < 0.04 && prefs.smbDeliveryRatioMin < DoubleKey.ApsAutoIsfSmbDeliveryRatioMin.max) {
            val proposed = DoubleKey.ApsAutoIsfSmbDeliveryRatioMin.snapToStep(prefs.smbDeliveryRatioMin + 0.05)
            if (proposed > prefs.smbDeliveryRatioMin + 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Increase SMB delivery ratio minimum",
                    description = "Persistent hyper (${pct(hyper)}%). Raising the SMB ratio floor from " +
                        "${fmt(prefs.smbDeliveryRatioMin)} to ${fmt(proposed)} ensures a stronger baseline delivery.",
                    priority = AutoIsfPriority.Low,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfSmbDeliveryRatioMin, newValue = proposed,
                        currentValue = prefs.smbDeliveryRatioMin,
                        reason = "Higher SMB ratio floor improves correction at lower BG levels."
                    )
                ))
            }
        }

        if (hypo >= 0.05 && prefs.smbDeliveryRatioMin > DoubleKey.ApsAutoIsfSmbDeliveryRatioMin.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfSmbDeliveryRatioMin.snapToStep(prefs.smbDeliveryRatioMin - 0.05)
            if (proposed < prefs.smbDeliveryRatioMin - 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Reduce SMB delivery ratio minimum",
                    description = "Frequent lows (${pct(hypo)}%). Lowering the SMB ratio floor from " +
                        "${fmt(prefs.smbDeliveryRatioMin)} to ${fmt(proposed)} reduces baseline delivery aggressiveness.",
                    priority = AutoIsfPriority.High,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfSmbDeliveryRatioMin, newValue = proposed,
                        currentValue = prefs.smbDeliveryRatioMin,
                        reason = "Lower SMB ratio floor reduces insulin at the low end of the BG range."
                    )
                ))
            }
        }

        // ── SMB delivery ratio max ─────────────────────────────────────────────────
        if (hyper >= 0.25 && hypo < 0.04 && prefs.smbDeliveryRatioMax < DoubleKey.ApsAutoIsfSmbDeliveryRatioMax.max) {
            val proposed = DoubleKey.ApsAutoIsfSmbDeliveryRatioMax.snapToStep(prefs.smbDeliveryRatioMax + 0.05)
            if (proposed > prefs.smbDeliveryRatioMax + 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Increase SMB delivery ratio maximum",
                    description = "Persistent hyper (${pct(hyper)}%). Raising the SMB ratio ceiling from " +
                        "${fmt(prefs.smbDeliveryRatioMax)} to ${fmt(proposed)} allows stronger delivery at high BG.",
                    priority = AutoIsfPriority.Medium,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfSmbDeliveryRatioMax, newValue = proposed,
                        currentValue = prefs.smbDeliveryRatioMax,
                        reason = "Higher SMB ratio ceiling allows more aggressive correction at high BG."
                    )
                ))
            }
        }

        if (hypo >= 0.05 && prefs.smbDeliveryRatioMax > DoubleKey.ApsAutoIsfSmbDeliveryRatioMax.defaultValue) {
            val proposed = DoubleKey.ApsAutoIsfSmbDeliveryRatioMax.snapToStep(prefs.smbDeliveryRatioMax - 0.05)
            if (proposed < prefs.smbDeliveryRatioMax - 0.04) {
                recs.add(AutoIsfRecommendation(
                    title = "Reduce SMB delivery ratio maximum",
                    description = "Frequent lows (${pct(hypo)}%). SMB ratio ceiling of ${fmt(prefs.smbDeliveryRatioMax)} " +
                        "may allow too much insulin at high BG leading to overcorrection. Reducing to ${fmt(proposed)}.",
                    priority = AutoIsfPriority.High,
                    action = AutoIsfAction.PreferenceUpdate(
                        key = DoubleKey.ApsAutoIsfSmbDeliveryRatioMax, newValue = proposed,
                        currentValue = prefs.smbDeliveryRatioMax,
                        reason = "Lower SMB ratio ceiling limits over-aggressive correction at high BG."
                    )
                ))
            }
        }

        return recs
    }

    private fun pct(v: Double) = (v * 100).toInt()
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
}