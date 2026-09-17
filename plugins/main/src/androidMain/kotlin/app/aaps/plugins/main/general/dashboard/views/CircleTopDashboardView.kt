package app.aaps.plugins.main.general.dashboard.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import app.aaps.core.keys.BooleanKey
import app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualityTier
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.dashboard.GlucoseHeroRing
import app.aaps.core.ui.compose.dashboard.GlucoseHeroUiState
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcPluginObjectives
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.views.GlucoseRingColorComputer
import app.aaps.plugins.main.general.dashboard.compose.DashboardMetricIcon
import app.aaps.plugins.main.general.dashboard.compose.DashboardQuickActionsBar
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.viewmodel.StatusCardState
import java.util.Locale
import java.util.TimeZone
/**
 * CircleTopDashboardView - Modern Circle-Top Hybrid Dashboard
 * 
 * ✨ Features:
 * - Compose glucose hero ([GlucoseHeroRing]) under [AapsTheme] (ring, nose, telemetry arc, typography)
 * - Context & Auditor badges (repositioned top-left/right)
 * - 2 columns of detailed metrics (8 infos)
 * - 4 quick actions (Advisor, Adjust, Meal, Context) en Compose
 * - Trend arrow + delta display
 * - Loop status indicator
 * 
 * 🎯 Design: Hybrid of feature/circle-top + existing AIMI badges
 * 
 * 🔧 Technical: Uses reflection to bypass Kotlin cache issues
 */
class CircleTopDashboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private var circleTopActionListener: CircleTopActionListener? = null

    private val accessibilityManager: AccessibilityManager? by lazy {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }

    // The views are found by hand: a Kotlin Multiplatform module never gets generated view
    // binding classes, so there is no `ComponentCircleTopStatusHybridBinding` to use.
    private val glucoseHeroCompose: ComposeView
    private val dashboardQuickActionsCompose: ComposeView
    private val aimiContextIndicator: ComposeView
    private val aimiAuditorIndicatorContainer: FrameLayout
    private val loopStatusChip: MaterialButton
    private val dashboardPumpChipReservoir: TextView
    private val dashboardPumpChipBattery: TextView
    private val dashboardPumpChipSite: TextView
    private val dashboardHeroStatusIob: TextView
    private val dashboardHeroStatusMeta: TextView
    private val btnDashboardMetricsSimple: MaterialButton
    private val btnDashboardMetricsExtended: MaterialButton
    private val dashboardMetricsCompactScroll: HorizontalScrollView
    private val dashboardCompactSteps: TextView
    private val dashboardCompactIob: TextView
    private val dashboardCompactHr: TextView
    private val dashboardCompactBasal: TextView
    private val dashboardMetricsExtendedContainer: LinearLayout
    private val metricIconSteps: ComposeView
    private val metricIconCob: ComposeView
    private val metricIconCv: ComposeView
    private val metricIconHr: ComposeView
    private val metricIconLastReading: ComposeView
    private val metricIconTbr: ComposeView
    private val metricIconBasal: ComposeView
    private val metricIconActivity: ComposeView
    private val metricIconAdaptiveSmoothing: ComposeView
    private val stepsText: TextView
    private val cobText: TextView
    private val cvText: TextView
    private val hrText: TextView
    private val lastSensorValueText: TextView
    private val tbrRateText: TextView
    private val basalText: TextView
    private val activityText: TextView
    private val adaptiveSmoothingQualityBadge: FrameLayout
    private val aimiPulseContainer: LinearLayout
    private val aimiPulseTitle: TextView
    private val aimiPulseSummary: TextView
    private val aimiPulseMeta: TextView
    private val aimiTelemetrySectionLabel: TextView
    private val aimiInsightsContainer: LinearLayout
    private val insightT3c: TextView
    private val insightManoeuvre: TextView
    private val insightFactor: TextView
    private val aimiMlConfidenceStrip: LinearLayout
    private val aimiMlConfidenceDetail: TextView
    private val tirStatsText: TextView
    private val tirVeryLowBar: View
    private val tirLowBar: View
    private val tirInRangeBar: View
    private val tirHighBar: View
    private val tirVeryHighBar: View
    private val tirVeryLowLabel: TextView
    private val tirLowLabel: TextView
    private val tirInRangeLabel: TextView
    private val tirHighLabel: TextView
    private val tirVeryHighLabel: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.component_circle_top_status_hybrid, this, true)
        glucoseHeroCompose = findViewById(R.id.glucose_hero_compose)
        dashboardQuickActionsCompose = findViewById(R.id.dashboard_quick_actions_compose)
        aimiContextIndicator = findViewById(R.id.aimi_context_indicator)
        aimiAuditorIndicatorContainer = findViewById(R.id.aimi_auditor_indicator_container)
        loopStatusChip = findViewById(R.id.loop_status_chip)
        dashboardPumpChipReservoir = findViewById(R.id.dashboard_pump_chip_reservoir)
        dashboardPumpChipBattery = findViewById(R.id.dashboard_pump_chip_battery)
        dashboardPumpChipSite = findViewById(R.id.dashboard_pump_chip_site)
        dashboardHeroStatusIob = findViewById(R.id.dashboard_hero_status_iob)
        dashboardHeroStatusMeta = findViewById(R.id.dashboard_hero_status_meta)
        btnDashboardMetricsSimple = findViewById(R.id.btn_dashboard_metrics_simple)
        btnDashboardMetricsExtended = findViewById(R.id.btn_dashboard_metrics_extended)
        dashboardMetricsCompactScroll = findViewById(R.id.dashboard_metrics_compact_scroll)
        dashboardCompactSteps = findViewById(R.id.dashboard_compact_steps)
        dashboardCompactIob = findViewById(R.id.dashboard_compact_iob)
        dashboardCompactHr = findViewById(R.id.dashboard_compact_hr)
        dashboardCompactBasal = findViewById(R.id.dashboard_compact_basal)
        dashboardMetricsExtendedContainer = findViewById(R.id.dashboard_metrics_extended_container)
        metricIconSteps = findViewById(R.id.metric_icon_steps)
        metricIconCob = findViewById(R.id.metric_icon_cob)
        metricIconCv = findViewById(R.id.metric_icon_cv)
        metricIconHr = findViewById(R.id.metric_icon_hr)
        metricIconLastReading = findViewById(R.id.metric_icon_last_reading)
        metricIconTbr = findViewById(R.id.metric_icon_tbr)
        metricIconBasal = findViewById(R.id.metric_icon_basal)
        metricIconActivity = findViewById(R.id.metric_icon_activity)
        metricIconAdaptiveSmoothing = findViewById(R.id.metric_icon_adaptive_smoothing)
        stepsText = findViewById(R.id.steps_text)
        cobText = findViewById(R.id.cob_text)
        cvText = findViewById(R.id.cv_text)
        hrText = findViewById(R.id.hr_text)
        lastSensorValueText = findViewById(R.id.last_sensor_value_text)
        tbrRateText = findViewById(R.id.tbr_rate_text)
        basalText = findViewById(R.id.basal_text)
        activityText = findViewById(R.id.activity_text)
        adaptiveSmoothingQualityBadge = findViewById(R.id.adaptive_smoothing_quality_badge)
        aimiPulseContainer = findViewById(R.id.aimi_pulse_container)
        aimiPulseTitle = findViewById(R.id.aimi_pulse_title)
        aimiPulseSummary = findViewById(R.id.aimi_pulse_summary)
        aimiPulseMeta = findViewById(R.id.aimi_pulse_meta)
        aimiTelemetrySectionLabel = findViewById(R.id.aimi_telemetry_section_label)
        aimiInsightsContainer = findViewById(R.id.aimi_insights_container)
        insightT3c = findViewById(R.id.insight_t3c)
        insightManoeuvre = findViewById(R.id.insight_manoeuvre)
        insightFactor = findViewById(R.id.insight_factor)
        aimiMlConfidenceStrip = findViewById(R.id.aimi_ml_confidence_strip)
        aimiMlConfidenceDetail = findViewById(R.id.aimi_ml_confidence_detail)
        tirStatsText = findViewById(R.id.tir_stats_text)
        tirVeryLowBar = findViewById(R.id.tir_very_low_bar)
        tirLowBar = findViewById(R.id.tir_low_bar)
        tirInRangeBar = findViewById(R.id.tir_in_range_bar)
        tirHighBar = findViewById(R.id.tir_high_bar)
        tirVeryHighBar = findViewById(R.id.tir_very_high_bar)
        tirVeryLowLabel = findViewById(R.id.tir_very_low_label)
        tirLowLabel = findViewById(R.id.tir_low_label)
        tirInRangeLabel = findViewById(R.id.tir_in_range_label)
        tirHighLabel = findViewById(R.id.tir_high_label)
        tirVeryHighLabel = findViewById(R.id.tir_very_high_label)
    }

    private val heroState = mutableStateOf(
        GlucoseHeroUiState(
            ringColorArgb = android.graphics.Color.GRAY,
            centerTextColorArgb = android.graphics.Color.WHITE,
            subTextColorArgb = android.graphics.Color.LTGRAY,
            surfaceColorArgb = android.graphics.Color.TRANSPARENT,
        )
    )

    private var composeHeroAttached: Boolean = false

    private val actionListenerState = mutableStateOf<CircleTopActionListener?>(null)

    /** Tint for adaptive smoothing badge wave icon (updated from [updateWithState]). */
    private val adaptiveSmoothingIconTintState = mutableStateOf(Color.Gray)

    private var dashboardPreferences: Preferences? = null

    private var suppressDashboardMetricsModeCallback: Boolean = false
    private var metricsModeToggleListenerInstalled: Boolean = false

    /**
     * Wire les [ComposeView] du hero glucose et de la barre d’actions + [AapsTheme] ([LocalPreferences]).
     * À appeler une fois depuis [androidx.fragment.app.Fragment.onViewCreated], avant [setActionListener].
     */
    fun attachComposeHeroDependencies(preferences: Preferences) {
        dashboardPreferences = preferences
        if (composeHeroAttached) return
        composeHeroAttached = true
        val heroCompose: ComposeView = glucoseHeroCompose
        // Detach-driven disposal: correct when this view is inside Compose AndroidView (activity
        // lifecycle stays alive while the view tree is torn down and rebuilt).
        heroCompose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        heroCompose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    val hero by heroState
                    GlucoseHeroRing(state = hero, modifier = Modifier.fillMaxSize())
                }
            }
        }

        val quickActionsCompose: ComposeView = dashboardQuickActionsCompose
        quickActionsCompose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        quickActionsCompose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    val listener by actionListenerState
                    DashboardQuickActionsBar(
                        onAdvisor = { listener?.onAimiAdvisorClicked() },
                        onAdjust = { listener?.onAdjustClicked() },
                        onMeal = { listener?.onAimiPreferencesClicked() },
                        onContext = { listener?.onStatsClicked() },
                    )
                }
            }
        }
        attachDashboardMetricComposeIcons(preferences)
        installDashboardMetricsModeToggle()
    }

    private fun attachDashboardMetricComposeIcons(preferences: Preferences) {
        fun dashboardColor(resId: Int): Color = Color(ContextCompat.getColor(context, resId))

        fun bindMetricIcon(
            view: ComposeView,
            tint: Color,
            sizeDp: Dp = 18.dp,
            icon: ImageVector,
        ) {
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            view.setContent {
                CompositionLocalProvider(LocalPreferences provides preferences) {
                    AapsTheme {
                        DashboardMetricIcon(imageVector = icon, tint = tint, size = sizeDp)
                    }
                }
            }
        }

        val muted = dashboardColor(R.color.dashboard_on_surface_muted)
        val onSurface = dashboardColor(R.color.dashboard_on_surface)
        val attention = dashboardColor(R.color.dashboard_metric_attention)
        val info = dashboardColor(R.color.dashboard_metric_info)
        val warning = dashboardColor(R.color.dashboard_metric_warning)

        aimiContextIndicator.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        aimiContextIndicator.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    Icon(
                        imageVector = IcPluginObjectives,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        bindMetricIcon(metricIconSteps, muted, 18.dp, Icons.Outlined.DirectionsWalk)
        bindMetricIcon(metricIconCob, attention, 18.dp, IcCarbs)
        bindMetricIcon(metricIconCv, onSurface, 18.dp, Icons.Outlined.Waves)
        bindMetricIcon(metricIconHr, muted, 18.dp, Icons.Outlined.Favorite)
        bindMetricIcon(metricIconLastReading, info, 18.dp, Icons.Outlined.WaterDrop)
        bindMetricIcon(metricIconTbr, warning, 18.dp, Icons.Outlined.ErrorOutline)
        bindMetricIcon(metricIconBasal, onSurface, 18.dp, Icons.Outlined.Tune)
        bindMetricIcon(metricIconActivity, onSurface, 18.dp, Icons.Outlined.Tune)

        adaptiveSmoothingIconTintState.value = muted
        metricIconAdaptiveSmoothing.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        metricIconAdaptiveSmoothing.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    val tint by adaptiveSmoothingIconTintState
                    Icon(
                        imageVector = Icons.Outlined.Waves,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = tint,
                    )
                }
            }
        }
    }

    /**
     * Reconcile chip + visibilité avec les préférences (ex. changement depuis l’écran Paramètres).
     */
    fun syncDashboardMetricsModeFromPreferences() {
        val prefs = dashboardPreferences ?: return
        val extended = prefs.get(BooleanKey.OverviewDashboardExtendedMetrics)
        suppressDashboardMetricsModeCallback = true
        btnDashboardMetricsSimple.isChecked = !extended
        btnDashboardMetricsExtended.isChecked = extended
        suppressDashboardMetricsModeCallback = false
        applyDashboardMetricsMode(extended)
    }

    private fun installDashboardMetricsModeToggle() {
        val prefs = dashboardPreferences ?: return
        if (!metricsModeToggleListenerInstalled) {
            metricsModeToggleListenerInstalled = true
            btnDashboardMetricsSimple.setOnClickListener {
                if (suppressDashboardMetricsModeCallback) return@setOnClickListener
                if (!prefs.get(BooleanKey.OverviewDashboardExtendedMetrics)) {
                    suppressDashboardMetricsModeCallback = true
                    btnDashboardMetricsSimple.isChecked = true
                    btnDashboardMetricsExtended.isChecked = false
                    suppressDashboardMetricsModeCallback = false
                    return@setOnClickListener
                }
                applyDashboardMetricsModeFromUserSelection(prefs, extended = false)
            }
            btnDashboardMetricsExtended.setOnClickListener {
                if (suppressDashboardMetricsModeCallback) return@setOnClickListener
                if (prefs.get(BooleanKey.OverviewDashboardExtendedMetrics)) {
                    suppressDashboardMetricsModeCallback = true
                    btnDashboardMetricsSimple.isChecked = false
                    btnDashboardMetricsExtended.isChecked = true
                    suppressDashboardMetricsModeCallback = false
                    return@setOnClickListener
                }
                applyDashboardMetricsModeFromUserSelection(prefs, extended = true)
            }
        }
        syncDashboardMetricsModeFromPreferences()
    }

    private fun applyDashboardMetricsModeFromUserSelection(
        prefs: Preferences,
        extended: Boolean,
    ) {
        suppressDashboardMetricsModeCallback = true
        btnDashboardMetricsSimple.isChecked = !extended
        btnDashboardMetricsExtended.isChecked = extended
        suppressDashboardMetricsModeCallback = false
        prefs.put(BooleanKey.OverviewDashboardExtendedMetrics, extended)
        applyDashboardMetricsMode(extended)
    }

    private fun applyDashboardMetricsMode(extended: Boolean) {
        dashboardMetricsExtendedContainer.isVisible = extended
        dashboardMetricsCompactScroll.isVisible = !extended
        aimiInsightsContainer.isVisible = extended
        aimiTelemetrySectionLabel.isVisible = extended
        dashboardHeroStatusIob.isVisible = extended
        if (!extended) {
            aimiMlConfidenceStrip.isGone = true
        }
    }

    private fun applyLoopStatusChip(loopIsRunning: Boolean) {
        val btn = loopStatusChip
        val bg = ContextCompat.getColor(context, R.color.dashboard_chip_background)
        val strokeOk = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_step1)
        val strokeWarn = ContextCompat.getColor(context, R.color.dashboard_metric_attention)
        val iconOk = ContextCompat.getColor(context, R.color.dashboard_metric_info)
        val iconWarn = ContextCompat.getColor(context, R.color.dashboard_metric_attention)
        btn.backgroundTintList = ColorStateList.valueOf(bg)
        btn.strokeColor = ColorStateList.valueOf(if (loopIsRunning) strokeOk else strokeWarn)
        btn.iconTint = ColorStateList.valueOf(if (loopIsRunning) iconOk else iconWarn)
        btn.alpha = if (loopIsRunning) 1f else 0.92f
    }

    /**
     * Text strip under trajectory insights: sensor tier (if any) + relevance + health. Shown only in extended metrics mode.
     */
    private fun updateAimiMlConfidenceStrip(state: StatusCardState) {
        val extended = dashboardPreferences?.get(BooleanKey.OverviewDashboardExtendedMetrics) == true
        if (!extended) {
            aimiMlConfidenceStrip.isGone = true
            return
        }
        val sep = context.getString(R.string.dashboard_aimi_ml_strip_separator)
        val parts = mutableListOf<String>()
        state.adaptiveSmoothingQualityTier?.let { tier ->
            val wordRes = when (tier) {
                AdaptiveSmoothingQualityTier.OK -> R.string.dashboard_aimi_ml_sensor_ok
                AdaptiveSmoothingQualityTier.UNCERTAIN -> R.string.dashboard_aimi_ml_sensor_uncertain
                AdaptiveSmoothingQualityTier.BAD -> R.string.dashboard_aimi_ml_sensor_low
            }
            parts.add(
                context.getString(
                    R.string.dashboard_aimi_ml_strip_part_sensor,
                    context.getString(wordRes),
                ),
            )
        }
        val rel = state.trajectoryRelevanceScore ?: 0.0
        val relPct = when {
            rel in 0.0..1.0 -> (rel * 100.0).toInt().coerceIn(0, 100)
            rel > 1.0 -> rel.toInt().coerceIn(0, 100)
            else -> 0
        }
        parts.add(
            context.getString(R.string.dashboard_aimi_ml_strip_relevance, relPct),
        )
        val health = (state.aimiHealthScore ?: 0.0).coerceIn(0.0, 1.0)
        val healthPct = (health * 100.0).toInt().coerceIn(0, 100)
        parts.add(
            context.getString(R.string.dashboard_aimi_ml_strip_health, healthPct),
        )
        val detail = parts.joinToString(separator = sep)
        aimiMlConfidenceDetail.text = detail
        aimiMlConfidenceStrip.isGone = detail.isBlank()
    }

    private fun updateCompactMetricChips(state: StatusCardState) {
        dashboardCompactSteps.text = state.stepsText ?: "--"
        dashboardCompactIob.text =
            state.iobText.trim().takeUnless { it.isEmpty() } ?: "--"
        dashboardCompactHr.text = state.hrText ?: "--"
        dashboardCompactBasal.text = state.tbrRateCompactText ?: state.tbrRateText ?: "--"
    }

    /**
     * Update all dashboard components with fresh state data
     * Uses reflection to access properties (bypasses Kotlin cache)
     */
    fun updateWithState(state: Any) {
        try {
            val stateClass = state::class.java
            
            // Helper function to safely get property value
            fun <T> getProp(name: String): T? {
                return try {
                    val accessor = "get" + name.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                    }
                    val getter = stateClass.getMethod(accessor)
                    @Suppress("UNCHECKED_CAST")
                    getter.invoke(state) as? T
                } catch (e: Exception) {
                    null
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // 1. Glucose hero (Compose / AapsTheme)
            // ═══════════════════════════════════════════════════════════════
            // Use [StatusCardState] fields directly so delta/time/angles are never dropped by reflection.
            if (state is StatusCardState) {
                updateCompactMetricChips(state)
                state.glucoseMgdl?.let { bgMgdl ->
                    val arcP = telemetryArcProgress(state)
                    val arcC = arcP?.let { telemetryArcColor(it) }
                    heroState.value = buildGlucoseHeroUiState(
                        bgMgdl = bgMgdl,
                        cardState = state,
                        glucoseText = state.glucoseText,
                        timeAgo = state.timeAgo,
                        deltaText = state.deltaText,
                        noseAngle = state.noseAngleDeg,
                        glucoseColor = state.glucoseColor,
                        arcProgress = arcP,
                        arcColorArgb = arcC,
                    )
                    glucoseHeroCompose.contentDescription = context.getString(
                        R.string.dashboard_glucose_ring_content_description,
                        state.glucoseText,
                        state.deltaText,
                    )
                }
            }

            if (state is StatusCardState) {
                updateAimiMlConfidenceStrip(state)
            } else {
                aimiMlConfidenceStrip.isGone = true
            }

            // ═══════════════════════════════════════════════════════════════
            // 2. Left Column Metrics
            // ═══════════════════════════════════════════════════════════════
            cobText.text = getProp<String>("cobText") ?: "0g"
            cvText.text = getProp<String>("cvText") ?: "CV --%"
            activityText.text = getProp<String>("activityPctText") ?: "0%"

            // ═══════════════════════════════════════════════════════════════
            // 3. Right Column Metrics
            // ═══════════════════════════════════════════════════════════════
            lastSensorValueText.text = getProp<String>("lastSensorValueText") ?: "--"

            // Adaptive Smoothing Quality badge (informational, phase 1)
            if (state is StatusCardState) {
                val tier = state.adaptiveSmoothingQualityTier
                adaptiveSmoothingQualityBadge.isGone = tier == null
                if (tier != null) {
                    val bgRes = when (tier) {
                        app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualityTier.OK ->
                            R.drawable.dashboard_chip_background_quality_ok
                        app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualityTier.UNCERTAIN ->
                            R.drawable.dashboard_chip_background_quality_uncertain
                        app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualityTier.BAD ->
                            R.drawable.dashboard_chip_background_quality_bad
                    }
                    adaptiveSmoothingQualityBadge.setBackgroundResource(bgRes)

                    val tintRes = when (tier) {
                        app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualityTier.OK ->
                            R.color.dashboard_on_surface_muted
                        app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualityTier.UNCERTAIN ->
                            R.color.dashboard_metric_attention
                        app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualityTier.BAD ->
                            R.color.dashboard_chip_border_warning
                    }
                    adaptiveSmoothingIconTintState.value = Color(context.getColor(tintRes))

                    adaptiveSmoothingQualityBadge.contentDescription = state.adaptiveSmoothingQualityBadgeText
                    adaptiveSmoothingQualityBadge.setOnClickListener {
                        it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        if (state.adaptiveSmoothingQualityDialogMessage.isNotBlank()) {
                            OKDialog.show(
                                context,
                                context.getString(R.string.adaptive_smoothing_quality_dialog_title),
                                state.adaptiveSmoothingQualityDialogMessage
                            )
                        }

                        val manager = accessibilityManager
                        if (manager != null && manager.isEnabled && manager.isTouchExplorationEnabled) {
                            announceForAccessibility(state.adaptiveSmoothingQualityBadgeText)
                        }
                    }
                }
            }

            tbrRateText.text = getProp<String>("tbrRateText") ?: "0.00 U/h"
            basalText.text = getProp<String>("basalText") ?: "--"

            // ═══════════════════════════════════════════════════════════════
            // 4. TIR Bar (24H)
            // ═══════════════════════════════════════════════════════════════
            val currentTime = System.currentTimeMillis()
            val startOfDay = currentTime / (1000 * 3600 * 24) * (1000 * 3600 * 24) - TimeZone.getDefault().getOffset(currentTime)
            val endOfDay = startOfDay + (1000 * 3600 * 24)

            tirStatsText.text = getProp<String>("tirStatsLine")?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.dashboard_tir_stats_placeholder)

            val vl = getProp<Double>("tirVeryLow") ?: 0.0
            val l = getProp<Double>("tirLow") ?: 0.0
            val tr = getProp<Double>("tirTarget") ?: 0.0
            val h = getProp<Double>("tirHigh") ?: 0.0
            val vh = getProp<Double>("tirVeryHigh") ?: 0.0

            fun updateBar(view: View, label: TextView, value: Double) {
                val params = view.layoutParams as android.widget.LinearLayout.LayoutParams
                // ensure at least a small sliver is shown so view doesn't collapse if value=0, 
                // but if we want to hide 0, we can use weight 0
                params.weight = Math.max(0.00001f, (value / 100.0).toFloat())
                view.layoutParams = params
                
                // only show text label if segment is large enough to display it
                if (value >= 5.0) {
                    label.text = String.format(Locale.getDefault(), "%.0f%%", value)
                } else {
                    label.text = ""
                }
            }

            updateBar(tirVeryLowBar, tirVeryLowLabel, vl)
            updateBar(tirLowBar, tirLowLabel, l)
            updateBar(tirInRangeBar, tirInRangeLabel, tr)
            updateBar(tirHighBar, tirHighLabel, h)
            updateBar(tirVeryHighBar, tirVeryHighLabel, vh)

            // ═══════════════════════════════════════════════════════════════
            // 5. Loop chip + hero column (IOB si étendu, lecture / capteur)
            // ═══════════════════════════════════════════════════════════════
            val loopLabel = getProp<String>("loopStatusText")
                ?: context.getString(R.string.closed_loop)
            loopStatusChip.text = loopLabel
            loopStatusChip.contentDescription = loopLabel
            val loopRunning =
                if (state is StatusCardState) state.loopIsRunning
                else getProp<Boolean>("loopIsRunning") ?: true
            applyLoopStatusChip(loopRunning)

            // Pompe / réservoir / site — pastilles lisibles (même esprit que la puce boucle / CGM).
            if (state is StatusCardState) {
                val dash = context.getString(app.aaps.core.ui.R.string.value_unavailable_short)
                val resV = state.reservoirText?.trim().orEmpty().ifBlank { dash }
                val battV = state.pumpBatteryText?.trim().orEmpty().ifBlank { dash }
                val siteV = state.infusionAgeText?.trim().orEmpty().ifBlank { dash }
                dashboardPumpChipReservoir.text =
                    context.getString(R.string.dashboard_pump_pill_reservoir, resV)
                dashboardPumpChipReservoir.contentDescription =
                    context.getString(R.string.dashboard_pump_pill_reservoir_a11y, resV)
                dashboardPumpChipBattery.text =
                    context.getString(R.string.dashboard_pump_pill_battery, battV)
                dashboardPumpChipBattery.contentDescription =
                    context.getString(R.string.dashboard_pump_pill_battery_a11y, battV)
                dashboardPumpChipSite.text =
                    context.getString(R.string.dashboard_pump_pill_site, siteV)
                dashboardPumpChipSite.contentDescription =
                    context.getString(R.string.dashboard_pump_pill_site_a11y, siteV)
                dashboardPumpChipReservoir.isClickable = false
                dashboardPumpChipReservoir.isFocusable = false
                dashboardPumpChipBattery.isClickable = false
                dashboardPumpChipBattery.isFocusable = false
                dashboardPumpChipSite.isClickable = false
                dashboardPumpChipSite.isFocusable = false
            }

            val extendedMetrics =
                dashboardPreferences?.get(BooleanKey.OverviewDashboardExtendedMetrics) == true
            dashboardHeroStatusIob.isVisible = extendedMetrics
            if (extendedMetrics) {
                val iobLine =
                    if (state is StatusCardState) state.iobText else getProp<String>("iobText")
                dashboardHeroStatusIob.text =
                    iobLine?.trim().takeUnless { it.isNullOrEmpty() } ?: "—"
            }

            // Prefer human-readable age (minAgoLong), not minAgoShort "(-2)" delta convention.
            val readingAgeHuman =
                if (state is StatusCardState) state.timeAgoDescription
                else getProp<String>("timeAgoDescription")
            val sensorAge =
                if (state is StatusCardState) state.sensorAgeText else getProp<String>("sensorAgeText")
            val readingPart = readingAgeHuman?.trim().orEmpty().ifBlank { "—" }
            dashboardHeroStatusMeta.text = buildString {
                append(
                    context.getString(
                        R.string.dashboard_hero_status_reading_line,
                        readingPart,
                    ),
                )
                val s = sensorAge?.trim().orEmpty()
                if (s.isNotEmpty()) {
                    append('\n')
                    append(
                        context.getString(
                            R.string.dashboard_hero_status_sensor_line,
                            s,
                        ),
                    )
                }
            }
            
            // Steps & HR
            stepsText.text = getProp<String>("stepsText") ?: "--"
            hrText.text = getProp<String>("hrText") ?: "--"
            
            // ═══════════════════════════════════════════════════════════════
            // 5b. AIMI Pulse (real APS reason + facts)
            // ═══════════════════════════════════════════════════════════════
            val showAimiPulse = dashboardPreferences?.get(BooleanKey.OverviewShowHybridDashboardAimiPulse) == true
            aimiPulseContainer.isGone = !showAimiPulse
            if (state is StatusCardState && showAimiPulse) {
                aimiPulseTitle.text = state.aimiPulseTitle
                aimiPulseSummary.text = state.aimiPulseSummary
                val meta = state.aimiPulseMeta
                aimiPulseMeta.text = meta
                aimiPulseMeta.isGone = meta.isBlank()
                val cd = buildString {
                    append(state.aimiPulseTitle)
                    append(". ")
                    append(state.aimiPulseSummary)
                    if (state.aimiPulseMeta.isNotBlank()) {
                        append(". ")
                        append(state.aimiPulseMeta)
                    }
                    append(". ")
                    append(context.getString(R.string.dashboard_cd_aimi_pulse))
                }
                aimiPulseContainer.contentDescription = cd
                if (state.aimiPulseHypoRisk) {
                    aimiPulseContainer.setBackgroundResource(R.drawable.dashboard_chip_background_warning)
                } else {
                    aimiPulseContainer.setBackgroundResource(R.drawable.dashboard_chip_background)
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // 6. AIMI Insights
            // ═══════════════════════════════════════════════════════════════
            insightT3c.text = getProp<String>("insightT3c") ?: "🎯 --"
            insightManoeuvre.text = getProp<String>("insightManoeuvre") ?: "🌀 --"
            insightFactor.text = getProp<String>("insightFactor") ?: "⚡ x1.0"
            
            // Insights container: trajectory health drives emphasis (aligned with telemetry arc thresholds)
            val health = getProp<Double>("aimiHealthScore") ?: 1.0
            aimiInsightsContainer.setBackgroundResource(
                when {
                    health < 0.45 -> R.drawable.dashboard_chip_background_warning
                    health < 0.72 -> R.drawable.dashboard_chip_background_quality_uncertain
                    else -> R.drawable.dashboard_chip_background
                }
            )
            

            
        } catch (e: Exception) {
            // Fallback: Log error but don't crash
            e.printStackTrace()
        }
    }

    /**
     * Set action listeners for quick actions (Compose) and the AIMI pulse card.
     */
    fun setActionListener(listener: CircleTopActionListener) {
        circleTopActionListener = listener
        actionListenerState.value = listener

        fun announceIfAccessibilityEnabled(messageRes: Int) {
            val manager = accessibilityManager
            if (manager != null && manager.isEnabled && manager.isTouchExplorationEnabled) {
                announceForAccessibility(context.getString(messageRes))
            }
        }

        fun withHaptic(action: () -> Unit): View.OnClickListener = View.OnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            action()
        }

        aimiPulseContainer.setOnClickListener(withHaptic {
            circleTopActionListener?.onAimiPulseClicked()
            announceIfAccessibilityEnabled(R.string.dashboard_chip_announced_aimi_pulse_details)
        })
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Accessors for DashboardFragment integration
    // ═══════════════════════════════════════════════════════════════
    
    /** Get container for Auditor badge (will be populated by DashboardFragment) */
    fun getAuditorContainer(): FrameLayout = aimiAuditorIndicatorContainer
    
    /** Get AIMI Context indicator (visibility controlled by DashboardFragment) */
    fun getContextIndicator(): View = aimiContextIndicator
    
    /** Cible tactile pour ouvrir le dialog boucle ([DashboardFragment]). */
    fun getLoopIndicator(): View = loopStatusChip

    /**
     * Blended telemetry arc progress (0..1): relevance, health, or sensor-quality proxy when APS data sparse.
     */
    private fun telemetryArcProgress(state: StatusCardState): Float? {
        val rel = state.trajectoryRelevanceScore
        val health = state.aimiHealthScore
        val tierProxy = state.adaptiveSmoothingQualityTier?.let { tier ->
            when (tier) {
                AdaptiveSmoothingQualityTier.OK -> 0.88
                AdaptiveSmoothingQualityTier.UNCERTAIN -> 0.58
                AdaptiveSmoothingQualityTier.BAD -> 0.35
            }
        }
        val combined: Double? = when {
            rel != null && health != null -> 0.5 * (rel + health)
            rel != null -> rel
            health != null -> health
            tierProxy != null -> tierProxy
            else -> null
        }
        return combined?.toFloat()?.coerceIn(0f, 1f)
    }

    private fun telemetryArcColor(progress: Float): Int {
        val resId = when {
            progress >= 0.72f -> app.aaps.core.ui.R.color.glucose_ring_step1
            progress >= 0.45f -> app.aaps.core.ui.R.color.glucose_ring_step2
            else -> app.aaps.core.ui.R.color.glucose_ring_step3
        }
        return ContextCompat.getColor(context, resId)
    }

    private fun resolveThemeColor(attr: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) tv.data else 0xFF888888.toInt()
    }

    private fun buildGlucoseHeroUiState(
        bgMgdl: Int,
        cardState: StatusCardState?,
        glucoseText: String,
        timeAgo: String,
        deltaText: String,
        noseAngle: Float?,
        glucoseColor: Int?,
        arcProgress: Float?,
        arcColorArgb: Int?,
    ): GlucoseHeroUiState {
        val step1 = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_step1)
        val step2 = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_step2)
        val step3 = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_step3)
        val step4 = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_step4)
        // Ring palette: stepped TIR-style bands (green→120, etc.). Hypo band uses default clinical floor (70)
        // from [hypoMaxMgdlAttr], not profile target low — otherwise BG just under a high target-low (e.g. 100 vs 105)
        // stays orange and ignores step1MaxMgdl. Compose [GlucoseHeroRing] uses this [ringArgb] only.
        val ringArgb = GlucoseRingColorComputer.compute(
            bgMgdl = bgMgdl,
            hypoMaxFromProfile = null,
            severeHypoMaxMgdl = 54f,
            hypoMaxMgdlAttr = 70f,
            useSteppedColors = true,
            step1MaxMgdl = 120f,
            step2MaxMgdl = 160f,
            step3MaxMgdl = 220f,
            stepColor1 = step1,
            stepColor2 = step2,
            stepColor3 = step3,
            stepColor4 = step4,
        )
        // Delta + time: [GlucoseHeroRing] uses the same order as Overview [BgInfoSection] (delta on top).
        return GlucoseHeroUiState(
            mainText = glucoseText,
            subLeftText = deltaText,
            subRightText = timeAgo,
            noseAngleDeg = noseAngle,
            ringColorArgb = ringArgb,
            centerTextColorArgb = glucoseColor
                ?: ContextCompat.getColor(context, app.aaps.core.ui.R.color.white),
            subTextColorArgb = resolveThemeColor(android.R.attr.textColorSecondary),
            surfaceColorArgb = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_surface),
            telemetryProgress = arcProgress,
            telemetryColorArgb = arcColorArgb,
            strokeWidthDp = 4f,
        )
    }
}

/**
 * Listener for dashboard quick actions and the AIMI pulse card.
 */
interface CircleTopActionListener {
    fun onAimiAdvisorClicked()
    fun onAdjustClicked()
    fun onAimiPreferencesClicked()
    fun onStatsClicked()
    fun onAimiPulseClicked()
}
