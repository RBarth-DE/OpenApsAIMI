package app.aaps.plugins.main.general.dashboard.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import app.aaps.core.ui.R as CoreUiR
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.viewmodel.AdjustmentCardState
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * The dashboard "adjustment" card: the AIMI decision lines, the pump badges and the run-loop button.
 *
 * The views are found by hand: a Kotlin Multiplatform module never gets generated view binding
 * classes, so there is no `ComponentAdjustmentStatusBinding` to use.
 */
class AdjustmentStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val glycemiaLine: TextView
    private val predictionLine: TextView
    private val iobActivityLine: TextView
    private val decisionLine: TextView
    private val modeLine: TextView
    private val pumpBadgeReservoir: TextView
    private val pumpBadgeSite: TextView
    private val pumpBadgeSensor: TextView
    private val safetyLine: TextView
    private val emptyMessage: TextView
    private val adjustmentContainer: LinearLayout
    private val runLoopButton: MaterialButton

    init {
        LayoutInflater.from(context).inflate(R.layout.component_adjustment_status, this, true)
        glycemiaLine = findViewById(R.id.glycemia_line)
        predictionLine = findViewById(R.id.prediction_line)
        iobActivityLine = findViewById(R.id.iob_activity_line)
        decisionLine = findViewById(R.id.decision_line)
        modeLine = findViewById(R.id.mode_line)
        pumpBadgeReservoir = findViewById(R.id.pump_badge_reservoir)
        pumpBadgeSite = findViewById(R.id.pump_badge_site)
        pumpBadgeSensor = findViewById(R.id.pump_badge_sensor)
        safetyLine = findViewById(R.id.safety_line)
        emptyMessage = findViewById(R.id.empty_message)
        adjustmentContainer = findViewById(R.id.adjustment_container)
        runLoopButton = findViewById(R.id.run_loop_button)
    }

    fun setOnRunLoopClickListener(listener: OnClickListener) {
        runLoopButton.setOnClickListener(listener)
    }

    fun update(state: AdjustmentCardState) {
        glycemiaLine.text = state.glycemiaLine
        predictionLine.text = state.predictionLine
        iobActivityLine.text = state.iobActivityLine
        decisionLine.text = state.decisionLine
        val dash = context.getString(CoreUiR.string.value_unavailable_short)
        val resV = state.pumpReservoirPlain.ifBlank { dash }
        val siteV = state.pumpSitePlain.ifBlank { dash }
        val sensV = state.pumpSensorPlain.ifBlank { dash }
        pumpBadgeReservoir.text =
            context.getString(R.string.dashboard_pump_pill_reservoir, resV)
        pumpBadgeReservoir.contentDescription =
            context.getString(R.string.dashboard_pump_pill_reservoir_a11y, resV)
        pumpBadgeSite.text =
            context.getString(R.string.dashboard_pump_pill_site, siteV)
        pumpBadgeSite.contentDescription =
            context.getString(R.string.dashboard_pump_pill_site_a11y, siteV)
        pumpBadgeSensor.text =
            context.getString(R.string.dashboard_pump_pill_sensor, sensV)
        pumpBadgeSensor.contentDescription =
            context.getString(R.string.dashboard_pump_pill_sensor_a11y, sensV)
        safetyLine.text = state.safetyLine
        if (state.modeLine.isNullOrBlank()) {
            modeLine.visibility = View.GONE
        } else {
            modeLine.text = state.modeLine
            modeLine.visibility = View.VISIBLE
        }
        val adjustments = state.adjustments
        adjustmentContainer.removeAllViews()
        emptyMessage.visibility = if (adjustments.isEmpty()) View.VISIBLE else View.GONE
        val chipVerticalPadding = resources.getDimensionPixelSize(R.dimen.dashboard_chip_padding_vertical)
        val chipHorizontalPadding = resources.getDimensionPixelSize(R.dimen.dashboard_chip_padding_horizontal)
        val chipSpacing = resources.getDimensionPixelSize(R.dimen.dashboard_chip_spacing)
        adjustments.forEach { text ->
            val textView = TextView(context).apply {
                this.text = text
                setTextColor(ContextCompat.getColor(context, R.color.dashboard_on_surface))
                background = AppCompatResources.getDrawable(context, R.drawable.dashboard_chip_background)
                setPadding(chipHorizontalPadding, chipVerticalPadding, chipHorizontalPadding, chipVerticalPadding)
                TextViewCompat.setTextAppearance(this, MaterialR.style.TextAppearance_MaterialComponents_Body2)
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = chipSpacing
            }
            adjustmentContainer.addView(textView, params)
        }
    }
}
