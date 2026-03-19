package app.aaps.plugins.main.general.dashboard.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.viewbinding.ViewBinding
import app.aaps.plugins.main.databinding.ComponentCircleTopStatusHybridBinding
import java.util.Locale
import java.util.TimeZone


/**
 * CircleTopDashboardView - Modern Circle-Top Hybrid Dashboard
 * 
 * ✨ Features:
 * - GlucoseRingView with dynamic nose pointer
 * - Context & Auditor badges (repositioned top-left/right)
 * - 2 columns of detailed metrics (8 infos)
 * - 4 action chips (Advisor, Adjust, Prefs, Stats)
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

    private val binding = ComponentCircleTopStatusHybridBinding.inflate(
        LayoutInflater.from(context),
        this
    )

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
                    val getter = stateClass.getMethod("get${name.replaceFirstChar { it.uppercase(java.util.Locale.ROOT) }}")
                    @Suppress("UNCHECKED_CAST")
                    getter.invoke(state) as? T
                } catch (_: Exception) {
                    null
                }
            }
            // ═══════════════════════════════════════════════════════════════
            // 1. GlucoseRingView (Center Circle)
            // ═══════════════════════════════════════════════════════════════
            val bgMgdl = getProp<Int>("glucoseMgdl")
            binding.glucoseRing.update(
                bgMgdl = bgMgdl ?: 0,
                mainText = getProp<String>("glucoseText") ?: "--",
                subLeftText = getProp<String>("timeAgo") ?: "",
                subRightText = getProp<String>("deltaText") ?: "",
                noseAngleDeg = getProp<Float>("noseAngleDeg"),
            )


            // ═══════════════════════════════════════════════════════════════
            // 2. Left Column Metrics
            // ═══════════════════════════════════════════════════════════════
            binding.stepsText.text = getProp<String>("stepsText") ?: "--"
            binding.reservoirChip.text = getProp<String>("reservoirText") ?: "--"
            binding.reservoirChip.setTextColor( getProp<Int>("reservoirColor") ?: Color.WHITE )
            binding.infusionAgeText.text = getProp<String>("infusionAgeText") ?: "--"
            binding.infusionAgeText.setTextColor( getProp<Int>("infusionAgeColor") ?: Color.WHITE )
            binding.pumpBatteryText.text = getProp<String>("pumpBatteryText") ?: "--"
            binding.pumpBatteryText.setTextColor( getProp<Int>("pumpBatteryColor") ?: Color.WHITE )
            binding.sensorAgeText.text = getProp<String>("sensorAgeText") ?: "--"
            binding.sensorAgeText.setTextColor( getProp<Int>("sensorAgeColor") ?: Color.WHITE )

            // ═══════════════════════════════════════════════════════════════
            // 3. Right Column Metrics
            // ═══════════════════════════════════════════════════════════════
            binding.loopStatus.text = getProp<String>("loopStatusText") ?: "Closed Loop"
            binding.hrText.text = getProp<String>("hrText") ?: "--"
            //last SMB
            binding.lastUpdateText.text = getProp<String>("lastUpdateText") ?: "--"
            binding.lastSensorValueText.text = getProp<String>("lastSensorValueText") ?: "--"
            // TBR
            binding.basalPctText.text = getProp<String>("basalPctText") ?: "--"
            binding.basalText.text = getProp<String>("basalText") ?: "--"
            //IOB
            binding.iobText.text = getProp<String>("iobText") ?: "--"

            // ═══════════════════════════════════════════════════════════════
            // 4. TIR Bar (24H)
            // ═══════════════════════════════════════════════════════════════
            // val currentTime = System.currentTimeMillis()
            // val startOfDay = currentTime / (1000 * 3600 * 24) * (1000 * 3600 * 24) - TimeZone.getDefault().getOffset(currentTime)
            // val endOfDay = startOfDay + (1000 * 3600 * 24)

            val avg = getProp<Double>("avgBgMgdl") ?: Double.NaN
            val a1c = getProp<Double>("a1c") ?: Double.NaN
            if (!avg.isNaN() && !a1c.isNaN()) {
                binding.tirStatsText.text = String.format(Locale.US,"Avg %.0f • A1C %.1f%%", avg, a1c)
            } else {
                binding.tirStatsText.text = "Avg -- • A1C --"
            }

            val vl = getProp<Double>("tirVeryLow") ?: 0.0
            val l = getProp<Double>("tirLow") ?: 0.0
            val tr = getProp<Double>("tirTarget") ?: 0.0
            val h = getProp<Double>("tirHigh") ?: 0.0
            val vh = getProp<Double>("tirVeryHigh") ?: 0.0

            fun updateBar(view: View, label: android.widget.TextView, value: Double) {
                val params = view.layoutParams as android.widget.LinearLayout.LayoutParams
                // ensure at least a small sliver is shown so view doesn't collapse if value=0,
                // but if we want to hide 0, we can use weight 0
                params.weight = Math.max(0.00001f, (value / 100.0).toFloat())
                view.layoutParams = params

                // only show text label if segment is large enough to display it
                if (value >= 5.0) {
                    label.text = String.format(Locale.US, "%.0f%%", value)
                } else {
                    label.text = ""
                }
            }

            updateBar(binding.tirVeryLowBar, binding.tirVeryLowLabel, vl)
            updateBar(binding.tirLowBar, binding.tirLowLabel, l)
            updateBar(binding.tirInRangeBar, binding.tirInRangeLabel, tr)
            updateBar(binding.tirHighBar, binding.tirHighLabel, h)
            updateBar(binding.tirVeryHighBar, binding.tirVeryHighLabel, vh)

            // ═══════════════════════════════════════════════════════════════
            // 6. AIMI Insights
            // ═══════════════════════════════════════════════════════════════
            binding.insightT3c.text = getProp<String>("insightT3c") ?: "🎯 --"
            binding.insightManoeuvre.text = getProp<String>("insightManoeuvre") ?: "🌀 --"
            binding.insightFactor.text = getProp<String>("insightFactor") ?: "⚡ x1.0"
            val health = getProp<Double>("aimiHealthScore")
            binding.insightHealth.text = health?.let { "🩺 %.0f%%".format(it) } ?: "🩺 --"

            // Adjust container style based on health score (confidence)
            if (health != null && health < 80.0) {
                binding.aimiInsightsContainer.setBackgroundResource(app.aaps.plugins.main.R.drawable.dashboard_chip_background_warning)
            } else {
                binding.aimiInsightsContainer.background = null
            }   

        } catch (e: Exception) {
            // Fallback: Log error but don't crash
            e.printStackTrace()
        }
    }

    /**
     * Set action listeners for the 4 chips
     */
    fun setActionListener(listener: CircleTopActionListener) {
        binding.chipAimiAdvisor.setOnClickListener { listener.onAimiAdvisorClicked() }
        binding.chipAdjust.setOnClickListener { listener.onAdjustClicked() }
        binding.chipContext.setOnClickListener { listener.onAimiContextClicked() }
        binding.chipFood.setOnClickListener { listener.onAimiFoodClicked() }
        binding.chipAimiPref.setOnClickListener { listener.onAimiPreferencesClicked() }
        binding.chipStat.setOnClickListener { listener.onStatsClicked() }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Accessors for DashboardFragment integration
    // ═══════════════════════════════════════════════════════════════
    
    /** Get container for Auditor badge (will be populated by DashboardFragment) */
    fun getAuditorContainer(): FrameLayout = binding.aimiAuditorIndicatorContainer
    
    /** Get AIMI Context indicator (visibility controlled by DashboardFragment) */
    fun getContextIndicator(): View = binding.aimiContextIndicator
    
    /** Get Loop indicator (icon updated by DashboardFragment) */
    fun getLoopIndicator(): View = binding.loopIndicator

}

/**
 * Listener interface for the 4 action chips
 */
interface CircleTopActionListener {
    fun onAimiAdvisorClicked()
    fun onAdjustClicked()
    fun onAimiPreferencesClicked()
    fun onStatsClicked()
    fun onAimiContextClicked()
    fun onAimiFoodClicked()
}
