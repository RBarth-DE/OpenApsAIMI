package app.aaps.plugins.aps.openAPSAIMI.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.interfaces.aps.AimiAdaptationEvidenceType
import app.aaps.core.interfaces.aps.AimiAdaptationMetric
import app.aaps.core.interfaces.aps.AimiAdaptationMetricId
import app.aaps.core.interfaces.aps.AimiAdaptationModuleId
import app.aaps.core.interfaces.aps.AimiAdaptationModuleStatus
import app.aaps.core.interfaces.aps.AimiAdaptationPhase
import app.aaps.core.interfaces.aps.AimiAdaptationReasonCode
import app.aaps.core.interfaces.aps.AimiAdaptationStatus
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.plugins.aps.ApsStrings
import java.text.DateFormat
import java.util.Date

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS

@Composable
fun AimiAdaptationStatusLabScreen(
    status: AimiAdaptationStatus?,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(ApsStrings.aimi_adaptation_status_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiStrings.back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        if (status == null || status.modules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(AapsSpacing.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(ApsStrings.aimi_adaptation_status_no_data),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            val now = System.currentTimeMillis()
            val activeCount = status.modules.count { it.phase == AimiAdaptationPhase.ACTIVE }
            val readyCount = status.modules.count { it.phase == AimiAdaptationPhase.READY }
            val waitingOrLearningCount = status.modules.count {
                it.phase == AimiAdaptationPhase.WAITING || it.phase == AimiAdaptationPhase.LEARNING
            }
            val attentionCount = status.modules.count {
                it.phase == AimiAdaptationPhase.BLOCKED || it.phase == AimiAdaptationPhase.STALE
            }
            val latestUpdatedAt = status.modules.mapNotNull { it.updatedAt }.maxOrNull()
            val snapshotAgeMillis = latestUpdatedAt?.let { (now - it).coerceAtLeast(0L) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(AapsSpacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                        Text(
                            text = stringResource(
                                ApsStrings.aimi_adaptation_status_counts,
                                activeCount,
                                readyCount,
                                waitingOrLearningCount,
                                attentionCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (latestUpdatedAt != null && snapshotAgeMillis != null) {
                            Text(
                                text = lastUpdateText(latestUpdatedAt, snapshotAgeMillis),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                items(
                    items = status.modules,
                    key = { it.moduleId },
                ) { moduleStatus ->
                    AdaptationModuleCard(moduleStatus, now)
                }
            }
        }
    }
}

@Composable
private fun AdaptationModuleCard(
    status: AimiAdaptationModuleStatus,
    now: Long,
) {
    val containerColor = when (status.phase) {
        AimiAdaptationPhase.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
        AimiAdaptationPhase.READY -> MaterialTheme.colorScheme.secondaryContainer
        AimiAdaptationPhase.BLOCKED -> MaterialTheme.colorScheme.errorContainer
        AimiAdaptationPhase.STALE -> MaterialTheme.colorScheme.tertiaryContainer
        AimiAdaptationPhase.DISABLED,
        AimiAdaptationPhase.WAITING,
        -> MaterialTheme.colorScheme.surfaceVariant
        AimiAdaptationPhase.LEARNING -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        ) {
            Text(
                text = moduleName(status.moduleId),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            ) {
                Text(
                    text = phaseName(status.phase),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = reasonName(status.reason),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            val effectiveUpdatedAt = status.updatedAt
            val ageMillis = effectiveUpdatedAt?.let { (now - it).coerceAtLeast(0L) }
            if (effectiveUpdatedAt != null && ageMillis != null) {
                Text(
                    text = lastUpdateText(effectiveUpdatedAt, ageMillis),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (status.phase != AimiAdaptationPhase.DISABLED) {
                Text(
                    text = stringResource(ApsStrings.aimi_adaptation_no_module_update),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            status.progress?.let { progress ->
                Text(
                    text = stringResource(
                        ApsStrings.aimi_adaptation_progress_format,
                        evidenceName(progress.type),
                        progress.completed,
                        progress.required,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (status.metrics.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(ApsStrings.aimi_adaptation_metrics_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                status.metrics.forEach { metric ->
                    MetricRow(status.moduleId, metric)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    moduleId: AimiAdaptationModuleId,
    metric: AimiAdaptationMetric,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
    ) {
        Text(
            text = metricName(metric.id),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = metricValue(moduleId, metric),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun lastUpdateText(updatedAt: Long, ageMillis: Long): String {
    val timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(updatedAt))
    val age = when {
        ageMillis < MINUTE_MS -> stringResource(ApsStrings.aimi_adaptation_freshness_now)
        ageMillis < HOUR_MS -> stringResource(
            ApsStrings.aimi_adaptation_freshness_minutes,
            ageMillis / MINUTE_MS,
        )
        else -> stringResource(
            ApsStrings.aimi_adaptation_freshness_hours,
            ageMillis / HOUR_MS,
        )
    }
    return stringResource(ApsStrings.aimi_adaptation_last_update_format, timestamp, age)
}

@Composable
private fun moduleName(id: AimiAdaptationModuleId): String = stringResource(
    when (id) {
        AimiAdaptationModuleId.BASAL_NEURAL_GOVERNANCE -> ApsStrings.aimi_adaptation_module_basal_neural_governance
        AimiAdaptationModuleId.UNIFIED_REACTIVITY -> ApsStrings.aimi_adaptation_module_unified_reactivity
        AimiAdaptationModuleId.BASAL_MULTI_SCALE -> ApsStrings.aimi_adaptation_module_basal_multi_scale
        AimiAdaptationModuleId.PKPD -> ApsStrings.aimi_adaptation_module_pkpd
        AimiAdaptationModuleId.ONLINE_LEARNER -> ApsStrings.aimi_adaptation_module_online_learner
        AimiAdaptationModuleId.NIGHT_GROWTH_RESISTANCE -> ApsStrings.aimi_adaptation_module_night_growth_resistance
        AimiAdaptationModuleId.WCYCLE -> ApsStrings.aimi_adaptation_module_wcycle
        AimiAdaptationModuleId.PEAK_GOVERNOR -> ApsStrings.aimi_adaptation_module_peak_governor
        AimiAdaptationModuleId.DIA_GOVERNOR -> ApsStrings.aimi_adaptation_module_dia_governor
    },
)

@Composable
private fun phaseName(phase: AimiAdaptationPhase): String = stringResource(
    when (phase) {
        AimiAdaptationPhase.DISABLED -> ApsStrings.aimi_adaptation_phase_disabled
        AimiAdaptationPhase.WAITING -> ApsStrings.aimi_adaptation_phase_waiting
        AimiAdaptationPhase.LEARNING -> ApsStrings.aimi_adaptation_phase_learning
        AimiAdaptationPhase.READY -> ApsStrings.aimi_adaptation_phase_ready
        AimiAdaptationPhase.ACTIVE -> ApsStrings.aimi_adaptation_phase_active
        AimiAdaptationPhase.STALE -> ApsStrings.aimi_adaptation_phase_stale
        AimiAdaptationPhase.BLOCKED -> ApsStrings.aimi_adaptation_phase_blocked
    },
)

@Composable
private fun reasonName(reason: AimiAdaptationReasonCode): String = stringResource(
    when (reason) {
        AimiAdaptationReasonCode.FEATURE_DISABLED -> ApsStrings.aimi_adaptation_reason_feature_disabled
        AimiAdaptationReasonCode.WAITING_FOR_SAMPLES -> ApsStrings.aimi_adaptation_reason_waiting_for_samples
        AimiAdaptationReasonCode.WAITING_FOR_EVENTS -> ApsStrings.aimi_adaptation_reason_waiting_for_events
        AimiAdaptationReasonCode.WAITING_FOR_FEEDBACKS -> ApsStrings.aimi_adaptation_reason_waiting_for_feedbacks
        AimiAdaptationReasonCode.WAITING_FOR_OBSERVATIONS -> ApsStrings.aimi_adaptation_reason_waiting_for_observations
        AimiAdaptationReasonCode.WARMUP -> ApsStrings.aimi_adaptation_reason_warmup
        AimiAdaptationReasonCode.LEARNING_IN_PROGRESS -> ApsStrings.aimi_adaptation_reason_learning_in_progress
        AimiAdaptationReasonCode.READY -> ApsStrings.aimi_adaptation_reason_ready
        AimiAdaptationReasonCode.ACTIVE -> ApsStrings.aimi_adaptation_reason_active
        AimiAdaptationReasonCode.DATA_STALE -> ApsStrings.aimi_adaptation_reason_data_stale
        AimiAdaptationReasonCode.SAFETY_HOLD -> ApsStrings.aimi_adaptation_reason_safety_hold
        AimiAdaptationReasonCode.CONTEXT_BLOCKED -> ApsStrings.aimi_adaptation_reason_context_blocked
        AimiAdaptationReasonCode.DEPENDENCY_DISABLED -> ApsStrings.aimi_adaptation_reason_dependency_disabled
        AimiAdaptationReasonCode.NO_RESULT -> ApsStrings.aimi_adaptation_reason_no_result
    },
)

@Composable
private fun evidenceName(type: AimiAdaptationEvidenceType): String = stringResource(
    when (type) {
        AimiAdaptationEvidenceType.SAMPLES -> ApsStrings.aimi_adaptation_evidence_samples
        AimiAdaptationEvidenceType.EVENTS -> ApsStrings.aimi_adaptation_evidence_events
        AimiAdaptationEvidenceType.FEEDBACKS -> ApsStrings.aimi_adaptation_evidence_feedbacks
        AimiAdaptationEvidenceType.OBSERVATIONS -> ApsStrings.aimi_adaptation_evidence_observations
    },
)

@Composable
private fun metricName(id: AimiAdaptationMetricId): String = stringResource(
    when (id) {
        AimiAdaptationMetricId.SAMPLE_COUNT -> ApsStrings.aimi_adaptation_metric_sample_count
        AimiAdaptationMetricId.SHORT_BUFFER_COUNT -> ApsStrings.aimi_adaptation_metric_short_buffer_count
        AimiAdaptationMetricId.MEDIUM_BUFFER_COUNT -> ApsStrings.aimi_adaptation_metric_medium_buffer_count
        AimiAdaptationMetricId.FASTING_SAMPLE_COUNT -> ApsStrings.aimi_adaptation_metric_fasting_sample_count
        AimiAdaptationMetricId.SHORT_UPDATE_COUNT -> ApsStrings.aimi_adaptation_metric_short_update_count
        AimiAdaptationMetricId.MEDIUM_UPDATE_COUNT -> ApsStrings.aimi_adaptation_metric_medium_update_count
        AimiAdaptationMetricId.LONG_UPDATE_COUNT -> ApsStrings.aimi_adaptation_metric_long_update_count
        AimiAdaptationMetricId.SHORT_ANALYSIS_COUNT -> ApsStrings.aimi_adaptation_metric_short_analysis_count
        AimiAdaptationMetricId.LONG_ANALYSIS_COUNT -> ApsStrings.aimi_adaptation_metric_long_analysis_count
        AimiAdaptationMetricId.PENDING_PREDICTION_COUNT -> ApsStrings.aimi_adaptation_metric_pending_prediction_count
        AimiAdaptationMetricId.EVALUATED_FEEDBACK_COUNT -> ApsStrings.aimi_adaptation_metric_evaluated_feedback_count
        AimiAdaptationMetricId.RELEASE_COUNT -> ApsStrings.aimi_adaptation_metric_release_count
        AimiAdaptationMetricId.ACCEPTED_UPDATE_COUNT -> ApsStrings.aimi_adaptation_metric_accepted_update_count
        AimiAdaptationMetricId.SHORT_MULTIPLIER -> ApsStrings.aimi_adaptation_metric_short_multiplier
        AimiAdaptationMetricId.MEDIUM_MULTIPLIER -> ApsStrings.aimi_adaptation_metric_medium_multiplier
        AimiAdaptationMetricId.LONG_MULTIPLIER -> ApsStrings.aimi_adaptation_metric_long_multiplier
        AimiAdaptationMetricId.COMBINED_MULTIPLIER -> ApsStrings.aimi_adaptation_metric_combined_multiplier
        AimiAdaptationMetricId.GLOBAL_FACTOR -> ApsStrings.aimi_adaptation_metric_global_factor
        AimiAdaptationMetricId.SHORT_FACTOR -> ApsStrings.aimi_adaptation_metric_short_factor
        AimiAdaptationMetricId.SEGMENT_FACTOR -> ApsStrings.aimi_adaptation_metric_segment_factor
        AimiAdaptationMetricId.SENSITIVITY_FACTOR -> ApsStrings.aimi_adaptation_metric_sensitivity_factor
        AimiAdaptationMetricId.LAST_ERROR -> ApsStrings.aimi_adaptation_metric_last_error
        AimiAdaptationMetricId.DIA_HOURS -> ApsStrings.aimi_adaptation_metric_dia
        AimiAdaptationMetricId.PEAK_MINUTES -> ApsStrings.aimi_adaptation_metric_peak
        AimiAdaptationMetricId.TIR_PERCENT -> ApsStrings.aimi_adaptation_metric_tir
        AimiAdaptationMetricId.CV_PERCENT -> ApsStrings.aimi_adaptation_metric_cv
        AimiAdaptationMetricId.CONFIDENCE -> ApsStrings.aimi_adaptation_metric_confidence
        AimiAdaptationMetricId.SMB_MULTIPLIER -> ApsStrings.aimi_adaptation_metric_smb_multiplier
        AimiAdaptationMetricId.BASAL_MULTIPLIER -> ApsStrings.aimi_adaptation_metric_basal_multiplier
        AimiAdaptationMetricId.EXTRA_IOB_HEADROOM_UNITS -> ApsStrings.aimi_adaptation_metric_extra_iob_headroom
        AimiAdaptationMetricId.DAY_IN_CYCLE -> ApsStrings.aimi_adaptation_metric_day_in_cycle
        AimiAdaptationMetricId.IC_MULTIPLIER -> ApsStrings.aimi_adaptation_metric_ic_multiplier
        AimiAdaptationMetricId.EFFECTIVE_VALUE -> ApsStrings.aimi_adaptation_metric_effective_value
        AimiAdaptationMetricId.PRIOR_VALUE -> ApsStrings.aimi_adaptation_metric_prior_value
        AimiAdaptationMetricId.LEARNED_VALUE -> ApsStrings.aimi_adaptation_metric_learned_value
    },
)

@Composable
private fun metricValue(
    moduleId: AimiAdaptationModuleId,
    metric: AimiAdaptationMetric,
): String = when (metric.id) {
    AimiAdaptationMetricId.SAMPLE_COUNT,
    AimiAdaptationMetricId.SHORT_BUFFER_COUNT,
    AimiAdaptationMetricId.MEDIUM_BUFFER_COUNT,
    AimiAdaptationMetricId.FASTING_SAMPLE_COUNT,
    AimiAdaptationMetricId.SHORT_UPDATE_COUNT,
    AimiAdaptationMetricId.MEDIUM_UPDATE_COUNT,
    AimiAdaptationMetricId.LONG_UPDATE_COUNT,
    AimiAdaptationMetricId.SHORT_ANALYSIS_COUNT,
    AimiAdaptationMetricId.LONG_ANALYSIS_COUNT,
    AimiAdaptationMetricId.PENDING_PREDICTION_COUNT,
    AimiAdaptationMetricId.EVALUATED_FEEDBACK_COUNT,
    AimiAdaptationMetricId.RELEASE_COUNT,
    AimiAdaptationMetricId.ACCEPTED_UPDATE_COUNT,
    AimiAdaptationMetricId.DAY_IN_CYCLE,
    -> stringResource(ApsStrings.aimi_adaptation_metric_value_count, metric.value)

    AimiAdaptationMetricId.DIA_HOURS ->
        stringResource(ApsStrings.aimi_adaptation_metric_value_hours, metric.value)

    AimiAdaptationMetricId.PEAK_MINUTES ->
        stringResource(ApsStrings.aimi_adaptation_metric_value_minutes, metric.value)

    AimiAdaptationMetricId.TIR_PERCENT,
    AimiAdaptationMetricId.CV_PERCENT,
    -> stringResource(ApsStrings.aimi_adaptation_metric_value_percent, metric.value)

    AimiAdaptationMetricId.EXTRA_IOB_HEADROOM_UNITS ->
        stringResource(ApsStrings.aimi_adaptation_metric_value_units, metric.value)

    AimiAdaptationMetricId.LAST_ERROR ->
        stringResource(ApsStrings.aimi_adaptation_metric_value_mgdl, metric.value)

    AimiAdaptationMetricId.EFFECTIVE_VALUE,
    AimiAdaptationMetricId.PRIOR_VALUE,
    AimiAdaptationMetricId.LEARNED_VALUE,
    -> when (moduleId) {
        AimiAdaptationModuleId.PEAK_GOVERNOR ->
            stringResource(ApsStrings.aimi_adaptation_metric_value_minutes, metric.value)
        AimiAdaptationModuleId.DIA_GOVERNOR ->
            stringResource(ApsStrings.aimi_adaptation_metric_value_hours, metric.value)
        else -> stringResource(ApsStrings.aimi_adaptation_metric_value_number, metric.value)
    }

    else -> stringResource(ApsStrings.aimi_adaptation_metric_value_number, metric.value)
}