package app.aaps.ui.compose.overview

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.overview.AuditorDisplayState
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.aapsClient.AapsClientStatusCard
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.graphs.StatusPanelUiState
import app.aaps.ui.compose.overview.statusLights.StatusViewModel

@Composable
fun OverviewScreenStacked(
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeProgress: Float,
    isSimpleMode: Boolean,
    calcProgress: Int,
    graphViewModel: GraphViewModel,
    manageViewModel: ManageViewModel,
    statusViewModel: StatusViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    onNavigate: (NavigationRequest) -> Unit,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier
) {
    val config = LocalConfig.current
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val sensitivityUiState by graphViewModel.sensitivityUiState.collectAsStateWithLifecycle()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
    val statusPanelState by graphViewModel.statusPanelFlow.collectAsStateWithLifecycle()
    val auditorState by graphViewModel.auditorStateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var statusExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
    ) {
        if (calcProgress < 100) {
            LinearProgressIndicator(
                progress = { calcProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left: BG Info + sensitivity chip
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BgInfoSection(
                    bgInfo = bgInfoState.bgInfo,
                    timeAgoText = bgInfoState.timeAgoText
                )
                SensitivityChipBlock(state = sensitivityUiState)
            }

            // Middle: chips + status panel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                OverviewChipsColumn(
                    runningMode = runningMode,
                    runningModeText = runningModeText,
                    runningModeProgress = runningModeProgress,
                    isSimpleMode = isSimpleMode,
                    tempTargetText = tempTargetText,
                    tempTargetState = tempTargetState,
                    tempTargetProgress = tempTargetProgress,
                    tempTargetReason = tempTargetReason,
                    onNavigate = onNavigate
                )
                OverviewStatusPanel(
                    state = statusPanelState,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Right: Auditor indicator + AIMI action buttons
            Column(
                modifier = Modifier.padding(start = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AuditorIconButton(state = auditorState) {
                    try {
                        context.startActivity(
                            Intent().setClassName(
                                context,
                                "app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui.AuditorVerdictActivity"
                            )
                        )
                    } catch (_: Exception) {}
                }
                AimiActionButton(label = stringResource(app.aaps.core.ui.R.string.aimi_btn_advisor)) {
                    try {
                        context.startActivity(
                            Intent().setClassName(
                                context,
                                "app.aaps.plugins.aps.openAPSAIMI.advisor.AimiProfileAdvisorActivity"
                            )
                        )
                    } catch (_: Exception) {}
                }
                AimiActionButton(label = stringResource(app.aaps.core.ui.R.string.aimi_btn_meal)) {
                    try {
                        context.startActivity(
                            Intent().setClassName(
                                context,
                                "app.aaps.plugins.aps.openAPSAIMI.advisor.meal.MealAdvisorActivity"
                            )
                        )
                    } catch (_: Exception) {}
                }
                AimiActionButton(label = stringResource(app.aaps.core.ui.R.string.aimi_btn_context)) {
                    try {
                        context.startActivity(
                            Intent().setClassName(
                                context,
                                "app.aaps.plugins.aps.openAPSAIMI.context.ui.ContextActivity"
                            )
                        )
                    } catch (_: Exception) {}
                }
                AimiActionButton(label = stringResource(app.aaps.core.ui.R.string.aimi_btn_stats)) {
                    onNavigate(NavigationRequest.Element(ElementType.STATISTICS))
                }
            }
        }

        OverviewStatusSection(
            sensorStatus = statusState.sensorStatus,
            insulinStatus = statusState.insulinStatus,
            cannulaStatus = statusState.cannulaStatus,
            batteryStatus = statusState.batteryStatus,
            showFill = statusState.showFill,
            showPumpBatteryChange = statusState.showPumpBatteryChange,
            onNavigate = onNavigate,
            statusLightsDef = statusLightsDef,
            onCopyFromNightscout = { manageViewModel.copyStatusLightsFromNightscout() },
            expanded = statusExpanded,
            onExpandedChange = { statusExpanded = it }
        )

        if (config.AAPSCLIENT) {
            val nsClientStatus by graphViewModel.nsClientStatusFlow.collectAsStateWithLifecycle()
            val flavorTint = when {
                config.AAPSCLIENT3 -> AapsTheme.generalColors.flavorClient3Tint
                config.AAPSCLIENT2 -> AapsTheme.generalColors.flavorClient2Tint
                else               -> AapsTheme.generalColors.flavorClient1Tint
            }
            AapsClientStatusCard(
                statusData = nsClientStatus,
                flavorTint = flavorTint
            )
        }

        GraphsSection(graphViewModel = graphViewModel, isSimpleMode = isSimpleMode)
    }
}

// =========================================================================
// AIMI panel composables — internal so OverviewScreenSplit can use them too
// =========================================================================

private val StatusChipStepsColor = Color(0xFF26A69A)  // teal
private val StatusChipHrColor    = Color(0xFFEF5350)  // red
private val StatusChipSmbColor   = Color(0xFFFF7043)  // orange
private val StatusChipBasalColor = Color(0xFF42A5F5)  // light blue
private val StatusChipIobColor   = Color(0xFF7E57C2)  // purple

@Composable
internal fun OverviewStatusPanel(
    state: StatusPanelUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        StatusChip(
            iconRes = app.aaps.core.ui.R.drawable.ic_dashboard_shoe,
            text = state.stepsText,
            chipColor = StatusChipStepsColor
        )
        StatusChip(
            iconRes = app.aaps.core.objects.R.drawable.ic_cp_heart_rate,
            text = state.hrText,
            chipColor = StatusChipHrColor
        )
        StatusChip(
            iconRes = app.aaps.core.ui.R.drawable.ic_dashboard_droplet,
            text = "${state.lastSmbTime} · ${state.lastSmbAmount}",
            chipColor = StatusChipSmbColor
        )
        StatusChip(
            iconRes = app.aaps.core.ui.R.drawable.ic_dashboard_wave,
            text = "${state.basalPctText} · ${state.basalRateText}",
            chipColor = StatusChipBasalColor
        )
        StatusChip(
            iconRes = app.aaps.core.ui.R.drawable.ic_dashboard_iob,
            text = state.iobText,
            chipColor = StatusChipIobColor
        )
    }
}

@Composable
private fun StatusChip(
    @DrawableRes iconRes: Int,
    text: String,
    chipColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        modifier = modifier
            .fillMaxWidth()
            .height(AapsSpacing.chipHeight)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = chipColor,
                modifier = Modifier.size(AapsSpacing.chipIconSize)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = chipColor.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = AapsSpacing.small)
            )
        }
    }
}

@Composable
internal fun AuditorIconButton(
    state: AuditorDisplayState,
    onClick: () -> Unit
) {
    val tint = when (state) {
        AuditorDisplayState.IDLE       -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        AuditorDisplayState.PROCESSING -> AapsTheme.generalColors.ttActivity
        AuditorDisplayState.READY      -> AapsTheme.generalColors.statusNormal
        AuditorDisplayState.WARNING    -> AapsTheme.generalColors.statusWarning
        AuditorDisplayState.ERROR      -> AapsTheme.generalColors.statusCritical
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            painter = painterResource(app.aaps.core.ui.R.drawable.ic_audit_monitor),
            contentDescription = "Auditor",
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
internal fun AimiActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        modifier = Modifier
            .height(26.dp)
            .widthIn(min = 56.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
