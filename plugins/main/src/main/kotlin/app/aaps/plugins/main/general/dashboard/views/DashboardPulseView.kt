package app.aaps.plugins.main.general.dashboard.views
import kotlinx.coroutines.runBlocking

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isGone
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.ComponentDashboardPulseBinding
import app.aaps.plugins.main.general.dashboard.viewmodel.StatusCardState

class DashboardPulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding =
        ComponentDashboardPulseBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        orientation = VERTICAL
        binding.aimiPulseContainer.setOnClickListener {
            try {
                val intent = Intent().setClassName(context, "app.aaps.plugins.aps.openAPSAIMI.advisor.pulse.AimiPulseDetailActivity")
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("DashboardPulseView", "Failed to launch AimiPulseDetailActivity: ${e.message}")
            }
        }
    }

    fun update(state: StatusCardState) {
        binding.aimiPulseTitle.text = state.aimiPulseTitle
        binding.aimiPulseSummary.text = state.aimiPulseSummary
        binding.aimiPulseMeta.text = state.aimiPulseMeta
        binding.aimiPulseMeta.isGone = state.aimiPulseMeta.isBlank()
        binding.aimiPulseContainer.setBackgroundResource(
            if (state.aimiPulseHypoRisk) R.drawable.dashboard_chip_background_warning
            else R.drawable.dashboard_chip_background
        )
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
        binding.aimiPulseContainer.contentDescription = cd
    }
}