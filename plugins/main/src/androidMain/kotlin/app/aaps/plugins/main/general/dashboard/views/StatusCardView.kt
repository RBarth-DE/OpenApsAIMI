package app.aaps.plugins.main.general.dashboard.views

import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import app.aaps.core.ui.elements.GlucoseCircleView
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.viewmodel.StatusCardState
import com.google.android.material.card.MaterialCardView

/**
 * The dashboard status card: glucose, trend arrow, loop status and the AIMI context indicator.
 *
 * The views are found by hand: a Kotlin Multiplatform module never gets generated view binding
 * classes, so there is no `ComponentStatusCardBinding` to use.
 */
class StatusCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val glucoseValue: TextView
    private val trendArrow: ImageView
    private val loopStatus: TextView
    private val timeAgo: TextView
    private val deltaValue: TextView
    private val loopIndicator: View
    private val iobText: TextView
    private val pumpStatusText: TextView
    private val predictionText: TextView
    private val unicornIcon: ImageView
    private val glucoseCircle: GlucoseCircleView
    private val aimiContextIndicator: View?

    init {
        LayoutInflater.from(context).inflate(R.layout.component_status_card, this, true)
        glucoseValue = findViewById(R.id.glucose_value)
        trendArrow = findViewById(R.id.trend_arrow)
        loopStatus = findViewById(R.id.loop_status)
        timeAgo = findViewById(R.id.time_ago)
        deltaValue = findViewById(R.id.delta_value)
        loopIndicator = findViewById(R.id.loop_indicator)
        iobText = findViewById(R.id.iob_text)
        pumpStatusText = findViewById(R.id.pump_status_text)
        predictionText = findViewById(R.id.prediction_text)
        unicornIcon = findViewById(R.id.unicorn_icon)
        glucoseCircle = findViewById(R.id.glucose_circle)
        aimiContextIndicator = findViewById(R.id.aimi_context_indicator)
        isClickable = true
        isFocusable = true
    }

    fun setOnAimiIconClickListener(listener: OnClickListener) {
        aimiContextIndicator?.setOnClickListener(listener)
    }

    fun update(state: StatusCardState) {
        glucoseValue.text = state.glucoseText
        glucoseValue.setTextColor(state.glucoseColor)
        trendArrow.visibility = if (state.trendArrowRes == null) View.GONE else View.VISIBLE
        state.trendArrowRes?.let { trendArrow.setImageResource(it) }
        trendArrow.contentDescription = state.trendDescription
        loopStatus.text = state.loopStatusText
        timeAgo.text = state.timeAgo
        deltaValue.text = state.deltaText
        timeAgo.contentDescription = state.timeAgoDescription
        glucoseValue.paintFlags =
            if (state.isGlucoseActual) glucoseValue.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            else glucoseValue.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        loopIndicator.alpha = if (state.loopIsRunning) 1f else 0.4f
        iobText.text = state.iobText
        pumpStatusText.text = HtmlCompat.fromHtml(state.pumpStatusText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        predictionText.text = state.predictionText
        unicornIcon.setImageResource(state.unicornImageRes)  // 🦄 Update dynamic unicorn image

        aimiContextIndicator?.visibility =
            if (state.isAimiContextActive) View.VISIBLE else View.GONE

        // 🎨 Update GlucoseCircleView colors based on BG value
        if (state.glucoseValue != null && state.targetLow != null && state.targetHigh != null) {
            glucoseCircle.setGlucose(
                state.glucoseValue,
                state.targetLow,
                state.targetHigh
            )
        }

        contentDescription = state.contentDescription
    }
}
