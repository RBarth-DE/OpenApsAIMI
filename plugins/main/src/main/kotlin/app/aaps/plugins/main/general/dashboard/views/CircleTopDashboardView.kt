package app.aaps.plugins.main.general.dashboard.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import app.aaps.plugins.main.databinding.ComponentCircleTopStatusHybridBinding

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
            getProp<Int>("glucoseMgdl")?.let { bgMgdl ->
                binding.glucoseRing.update(
                    bgMgdl = bgMgdl,
                    mainText = getProp<String>("glucoseText") ?: "--",
                    subLeftText = getProp<String>("timeAgo") ?: "",
                    subRightText = getProp<String>("deltaText") ?: "",
                    noseAngleDeg = getProp<Float>("noseAngleDeg"),
                )
            }

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
