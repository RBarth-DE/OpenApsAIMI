package app.aaps.plugins.aps.openAPSAIMI.safety
import kotlinx.coroutines.runBlocking

data class SafetyDecision(
    val stopBasal: Boolean,
    val bolusFactor: Double,
    val reason: String,
    val basalLS: Boolean,
    val isHypoRisk: Boolean = false
)
