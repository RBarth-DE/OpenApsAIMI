package app.aaps.plugins.main.general.dashboard.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.card.MaterialCardView
import app.aaps.plugins.main.databinding.ViewGlucoseGraphPlaceholderBinding

class GlucoseGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
    private val binding = ViewGlucoseGraphPlaceholderBinding.inflate(LayoutInflater.from(context), this, true)

    val graph: FrameLayout get() = binding.graphView
    val rangeButton: android.widget.Button get() = binding.graphRangeButton
    val chartMenuButton: android.widget.ImageButton get() = binding.chartMenuButton

    fun setUpdateMessage(message: String) {
        binding.graphUpdatedAt.text = message
    }

    fun showPlaceholder(show: Boolean) {
        binding.graphPlaceholder.visibility = if (show) View.VISIBLE else View.GONE
        binding.graphView.visibility = if (show) View.GONE else View.VISIBLE
    }
}
