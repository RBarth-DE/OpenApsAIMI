package app.aaps.plugins.aps.openAPSAIMI.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSAIMI.autodrive.learning.OnlineLearner
import app.aaps.plugins.aps.openAPSAIMI.learning.BasalLearner
import app.aaps.plugins.aps.openAPSAIMI.learning.BasalNeuralLearner
import app.aaps.plugins.aps.openAPSAIMI.learning.UnifiedReactivityLearner

/**
 * Overview page showing all AIMI learner algorithms with their warm-up progress,
 * current runtime, time to 100% effectiveness, and live status data.
 *
 * Cards gated behind a feature toggle are dimmed when the feature is off so
 * they don't confuse users who never enabled the corresponding subsystem.
 */
@Composable
fun AimiLearnerOverviewScreen(
    basalNeuralLearner: BasalNeuralLearner?,
    unifiedReactivityLearner: UnifiedReactivityLearner?,
    basalLearner: BasalLearner?,
    onlineLearner: OnlineLearner? = null,
    pkpdElapsedHours: Float? = null,
    preferences: Preferences? = null,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(R.string.aimi_learner_overview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
        ) {
            // ── 1. BasalNeuralLearner (Governance) ──
            LearnerCard(
                title = stringResource(R.string.aimi_learner_governance_name),
                description = stringResource(R.string.aimi_learner_governance_desc),
                warmupHours = 3.0,
                fullHours = 24.0,
                elapsedHours = basalNeuralLearner?.elapsedLearningHours,
                // Governance is always active when T3c Brittle or T3c Adaptive Basal is on
                enabled = preferences?.get(BooleanKey.OApsAIMIT3cBrittleMode) == true
                    || preferences?.get(BooleanKey.OApsAIMIT3cAdaptiveBasalEnabled) == true,
                liveStatus = buildString {
                    val gov = basalNeuralLearner?.getGovernanceSnapshot()
                    if (gov != null) {
                        append("Status: ${gov.action.name} | ")
                        append("Samples: ${gov.sampleCount}/288 | ")
                        append("Confidence: ${"%.0f".format(gov.confidence * 100)}%")
                        if (gov.hypoRate > 0) append(" | HypoRate: ${"%.1f".format(gov.hypoRate * 100)}%")
                    } else {
                        append("Not yet initialized — waiting for first tick")
                    }
                },
            )

            Spacer(modifier = Modifier.height(AapsSpacing.medium))

            // ── 2. UnifiedReactivityLearner ──
            LearnerCard(
                title = stringResource(R.string.aimi_learner_reactivity_name),
                description = stringResource(R.string.aimi_learner_reactivity_desc),
                warmupHours = 2.0,
                fullHours = 24.0,
                // Reactivity always runs (core of AIMI)
                enabled = true,
                elapsedHours = unifiedReactivityLearner?.elapsedLearningHours,
                liveStatus = buildString {
                    val analysis = unifiedReactivityLearner?.lastAnalysis
                    if (analysis != null) {
                        append("Global: ${"%.2f".format(analysis.globalFactor)}× | ")
                        append("ShortTerm: ${"%.2f".format(analysis.shortTermFactor)}× | ")
                        append("TIR70-180: ${"%.0f".format(analysis.tir70_180)}% | ")
                        append("CV: ${"%.0f".format(analysis.cv_percent)}%")
                    } else {
                        append("Collecting data — first analysis at ~30 min")
                    }
                },
            )

            Spacer(modifier = Modifier.height(AapsSpacing.medium))

            // ── 3. BasalLearner (3-Scale) ──
            LearnerCard(
                title = stringResource(R.string.aimi_learner_basal3scale_name),
                description = stringResource(R.string.aimi_learner_basal3scale_desc),
                warmupHours = 2.0,
                fullHours = 72.0,
                // BasalLearner always runs when AIMI is the active algorithm
                enabled = true,
                elapsedHours = basalLearner?.elapsedLearningHours,
                liveStatus = buildString {
                    if (basalLearner != null) {
                        append("Short: ${"%.2f".format(basalLearner.shortTermMultiplier)}× | ")
                        append("Medium: ${"%.2f".format(basalLearner.mediumTermMultiplier)}× | ")
                        append("Long: ${"%.2f".format(basalLearner.longTermMultiplier)}×")
                    } else {
                        append("Not available")
                    }
                },
            )

            Spacer(modifier = Modifier.height(AapsSpacing.medium))

            // ── 4. AdaptivePkPdEstimator ──
            LearnerCard(
                title = stringResource(R.string.aimi_learner_pkpd_name),
                description = stringResource(R.string.aimi_learner_pkpd_desc),
                warmupHours = 0.3,
                fullHours = 72.0,
                enabled = preferences?.get(BooleanKey.OApsAIMIPkpdEnabled) == true,
                elapsedHours = pkpdElapsedHours,
                liveStatus = stringResource(R.string.aimi_learner_pkpd_status),
            )

            Spacer(modifier = Modifier.height(AapsSpacing.medium))

            // ── 5. OnlineLearner (Autodrive Gradient Descent) ──
            LearnerCard(
                title = stringResource(R.string.aimi_learner_online_name),
                description = stringResource(R.string.aimi_learner_online_desc),
                warmupHours = 0.5,
                fullHours = 48.0,
                enabled = preferences?.get(BooleanKey.OApsAIMIautoDriveActive) == true,
                elapsedHours = onlineLearner?.elapsedLearningHours,
                liveStatus = stringResource(R.string.aimi_learner_online_status),
            )

            Spacer(modifier = Modifier.height(AapsSpacing.medium))

            // ── 6. NightGrowthResistanceLearner ──
            LearnerCard(
                title = stringResource(R.string.aimi_learner_ngr_name),
                description = stringResource(R.string.aimi_learner_ngr_desc),
                warmupHours = 0.0,
                fullHours = 12.0,
                enabled = preferences?.get(BooleanKey.OApsAIMINightGrowthEnabled) == true,
                liveStatus = stringResource(R.string.aimi_learner_ngr_status),
            )

            Spacer(modifier = Modifier.height(AapsSpacing.medium))

            // ── 7. WCycleLearner ──
            LearnerCard(
                title = stringResource(R.string.aimi_learner_wcycle_name),
                description = stringResource(R.string.aimi_learner_wcycle_desc),
                warmupHours = 720.0,
                fullHours = 2160.0,
                enabled = preferences?.get(BooleanKey.OApsAIMIwcycle) == true,
                liveStatus = stringResource(R.string.aimi_learner_wcycle_status),
            )

            Spacer(modifier = Modifier.height(AapsSpacing.medium))

            // ── 8. Peak & DIA Governors ──
            LearnerCard(
                title = stringResource(R.string.aimi_learner_governors_name),
                description = stringResource(R.string.aimi_learner_governors_desc),
                warmupHours = 6.0,
                fullHours = 72.0,
                enabled = preferences?.get(BooleanKey.OApsAIMIPeakGovernorEnabled) == true
                    || preferences?.get(BooleanKey.OApsAIMIDiaGovernorEnabled) == true,
                elapsedHours = pkpdElapsedHours,
                liveStatus = stringResource(R.string.aimi_learner_governors_status),
            )

            Spacer(modifier = Modifier.height(AapsSpacing.extraLarge))
        }
    }
}

@Composable
private fun LearnerCard(
    title: String,
    description: String,
    warmupHours: Double,
    fullHours: Double,
    liveStatus: String,
    elapsedHours: Float? = null,
    enabled: Boolean = true,
) {
    val effectiveElapsed = elapsedHours?.toDouble()?.coerceAtLeast(warmupHours) ?: warmupHours

    val progressLabel = when {
        effectiveElapsed < 1.0 -> "${"%.0f".format(effectiveElapsed * 60)} min"
        effectiveElapsed < 24.0 -> "${"%.1f".format(effectiveElapsed)}h"
        effectiveElapsed < 720.0 -> "${"%.0f".format(effectiveElapsed / 24)} days"
        else -> "${"%.0f".format(effectiveElapsed / 720)} months"
    }

    val progress: Float = if (fullHours > 0) {
        (effectiveElapsed / fullHours).toFloat().coerceIn(0.05f, 1.0f)
    } else {
        0.5f
    }

    // Dim disabled cards to 40% opacity so they recede visually
    val cardAlpha = if (enabled) 1f else 0.4f
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        elevation = CardDefaults.cardElevation(),
    ) {
        Column(
            modifier = Modifier.padding(AapsSpacing.extraLarge),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                )
                if (!enabled) {
                    Spacer(modifier = Modifier.width(AapsSpacing.small))
                    Text(
                        text = stringResource(R.string.aimi_learner_disabled_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.width(AapsSpacing.medium))
                Text(
                    text = "learning $progressLabel / ${"%.0f".format(fullHours)}h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(AapsSpacing.small))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(AapsSpacing.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.aimi_learner_time_to_full_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(AapsSpacing.small))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                )
            }
            Spacer(modifier = Modifier.height(AapsSpacing.small))
            Text(
                text = liveStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
