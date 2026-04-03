package app.aaps.plugins.main.general.dashboard.viewmodel

import java.io.Serializable

data class AdjustmentCardState(
    val glycemiaLine: String,
    val predictionLine: String,
    val iobActivityLine: String,
    val decisionLine: String,
    val pumpLine: String,
    val safetyLine: String,
    val modeLine: String?,
    val adjustments: List<String>,
    val reason: String?,
    val peakTime: Double? = null,
    val dia: Double? = null,
    val targetBg: Double? = null,
    val smb: Double? = null,
    val basal: Double? = null,
    val detailedReason: String? = null,
    val isHypoRisk: Boolean = false,
    val trajectoryTitle: String? = null,
    val trajectoryAscii: String? = null,
    val trajectoryMetrics: String? = null,
    val trajectoryRelevance: Double? = null
) : Serializable
