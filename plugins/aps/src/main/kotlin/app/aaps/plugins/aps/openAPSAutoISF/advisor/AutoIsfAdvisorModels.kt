package app.aaps.plugins.aps.openAPSAutoISF.advisor

import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey

data class AutoIsfMetrics(
    val periodLabel: String,
    val tir70_180: Double,
    val tir70_140: Double,
    val timeBelow70: Double,
    val timeBelow54: Double,
    val timeAbove180: Double,
    val timeAbove250: Double,
    val meanBg: Double,
    val gmi: Double,
    val tdd: Double,
    val basalPercent: Double,
    val todayTir: Double?,
    val todayTdd: Double?
)

sealed class AutoIsfSeverity {
    object Good : AutoIsfSeverity()
    object Warning : AutoIsfSeverity()
    object Critical : AutoIsfSeverity()
}

enum class AutoIsfPriority { Critical, High, Medium, Low }

sealed class AutoIsfAction {
    data class PreferenceUpdate(
        val key: PreferenceKey,
        val newValue: Any,
        val currentValue: Any,
        val reason: String
    ) : AutoIsfAction()
}

data class AutoIsfRecommendation(
    val title: String,
    val description: String,
    val priority: AutoIsfPriority,
    val action: AutoIsfAction? = null
)

data class AutoIsfPrefsSnapshot(
    val useAutoIsfWeights: Boolean,
    val autoIsfMin: Double,
    val autoIsfMax: Double,
    val bgAccelWeight: Double,
    val bgBrakeWeight: Double,
    val lowBgWeight: Double,
    val highBgWeight: Double,
    val ppWeight: Double,
    val duraWeight: Double,
    val iobThPercent: Int,
    val smbDeliveryRatio: Double,
    val smbDeliveryRatioMin: Double,
    val smbDeliveryRatioMax: Double,
    val smbDeliveryRatioBgRange: Double,
    val smbMaxRangeExtension: Double,
    val autosensMax: Double,
    val profileISF: Double
)

data class AutoIsfReport(
    val generatedAt: Long,
    val metrics: AutoIsfMetrics,
    val overallScore: Double,
    val overallSeverity: AutoIsfSeverity,
    val recommendations: List<AutoIsfRecommendation>
)
