package app.aaps.plugins.main.general.dashboard

import android.os.Bundle
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.viewmodel.AdjustmentCardState
import app.aaps.plugins.main.general.dashboard.views.AdjustmentStatusView
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.toast.ToastUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import dev.zacsweers.metro.Inject

class AdjustmentDetailsActivity : TranslatedDaggerAppCompatActivity() {

    companion object {
        const val EXTRA_ADJUSTMENT_STATE = "extra_adjustment_state"
    }

    @Inject lateinit var loop: Loop
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper

    private lateinit var toolbar: MaterialToolbar
    private lateinit var adjustmentSummary: AdjustmentStatusView
    private lateinit var peakTimeValue: TextView
    private lateinit var diaValue: TextView
    private lateinit var targetBgValue: TextView
    private lateinit var smbValue: TextView
    private lateinit var basalValue: TextView
    private lateinit var trajectoryCard: MaterialCardView
    private lateinit var trajectoryTitle: TextView
    private lateinit var trajectoryAscii: TextView
    private lateinit var trajectoryMetrics: TextView
    private lateinit var reasonCard: MaterialCardView
    private lateinit var reasonText: TextView
    private lateinit var decisionsEmpty: TextView
    private lateinit var decisionsContainer: LinearLayout
    private lateinit var runLoopButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adjustment_details)
        // The views are found by hand: a Kotlin Multiplatform module never gets generated view
        // binding classes, so there is no `ActivityAdjustmentDetailsBinding` to use.
        toolbar = findViewById(R.id.toolbar)
        adjustmentSummary = findViewById(R.id.adjustment_summary)
        peakTimeValue = findViewById(R.id.peak_time_value)
        diaValue = findViewById(R.id.dia_value)
        targetBgValue = findViewById(R.id.target_bg_value)
        smbValue = findViewById(R.id.smb_value)
        basalValue = findViewById(R.id.basal_value)
        trajectoryCard = findViewById(R.id.trajectory_card)
        trajectoryTitle = findViewById(R.id.trajectory_title)
        trajectoryAscii = findViewById(R.id.trajectory_ascii)
        trajectoryMetrics = findViewById(R.id.trajectory_metrics)
        reasonCard = findViewById(R.id.reason_card)
        reasonText = findViewById(R.id.reason_text)
        decisionsEmpty = findViewById(R.id.decisions_empty)
        decisionsContainer = findViewById(R.id.decisions_container)
        runLoopButton = findViewById(R.id.run_loop_button)

        toolbar.title = getString(R.string.dashboard_adjustments_details_title)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val state = intent.getSerializableExtra(EXTRA_ADJUSTMENT_STATE) as? AdjustmentCardState
        if (state == null) {
            finish()
            return
        }
        adjustmentSummary.update(state)

        peakTimeValue.text = state.peakTime?.let { "%.0f min".format(it) } ?: "--"
        diaValue.text = state.dia?.let { "%.1f h".format(it) } ?: "--"
        targetBgValue.text = state.targetBg?.let { "%.0f".format(it) } ?: "--"
        smbValue.text = state.smb?.let { "%.2f U".format(it) } ?: "--"
        basalValue.text = state.basal?.let { "%.2f U/h".format(it) } ?: "--"

        // 🌀 Trajectory Visualization
        if (state.trajectoryTitle != null && state.trajectoryAscii != null) {
            trajectoryCard.isVisible = true
            trajectoryTitle.text = state.trajectoryTitle
            trajectoryAscii.text = state.trajectoryAscii
            trajectoryMetrics.text = state.trajectoryMetrics ?: ""
            trajectoryMetrics.isVisible = !state.trajectoryMetrics.isNullOrEmpty()
        } else {
            trajectoryCard.isVisible = false
        }

        val reasonToShow = state.detailedReason ?: state.reason
        if (!reasonToShow.isNullOrEmpty()) {
            reasonCard.isVisible = true
            reasonText.text = reasonToShow
        } else {
            reasonCard.isVisible = false
        }

        renderDecisions(state.adjustments)

        runLoopButton.setOnClickListener {
            ToastUtils.infoToast(this, resourceHelper.gs(R.string.dashboard_loop_run_requested))
            lifecycleScope.launch {
                try {
                    loop.invoke("AdjustmentDetails", true)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.APS, "Error invoking loop from details", e)
                }
            }
        }
    }

    private fun renderDecisions(decisions: List<String>) {
        decisionsEmpty.isVisible = decisions.isEmpty()
        decisionsContainer.isVisible = decisions.isNotEmpty()
        decisionsContainer.removeAllViews()
        if (decisions.isEmpty()) return
        val spacing = resources.getDimensionPixelSize(R.dimen.dashboard_chip_spacing)
        decisions.forEach { text ->
            val row = layoutInflater.inflate(
                R.layout.item_adjustment_detail,
                decisionsContainer,
                false
            ) as LinearLayout
            val content = row.findViewById<TextView>(R.id.detail_text)
            content.text = text
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = spacing }
            decisionsContainer.addView(row, params)
        }
    }
}
