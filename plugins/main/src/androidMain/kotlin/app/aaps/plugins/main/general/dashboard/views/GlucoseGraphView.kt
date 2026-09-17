package app.aaps.plugins.main.general.dashboard.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import app.aaps.plugins.main.R
import com.google.android.material.card.MaterialCardView
import com.jjoe64.graphview.GraphView

/**
 * Card that holds the dashboard glucose graph.
 *
 * The views are found by hand: a Kotlin Multiplatform module never gets generated view binding
 * classes, so there is no `ViewGlucoseGraphPlaceholderBinding` to use.
 */
class GlucoseGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val graphView: GraphView
    private val graphRangeButton: Button
    private val graphUpdatedAt: TextView
    private val graphPlaceholder: View

    init {
        LayoutInflater.from(context).inflate(R.layout.view_glucose_graph_placeholder, this, true)
        graphView = findViewById(R.id.graph_view)
        graphRangeButton = findViewById(R.id.graph_range_button)
        graphUpdatedAt = findViewById(R.id.graph_updated_at)
        graphPlaceholder = findViewById(R.id.graph_placeholder)
        isClickable = false
        isFocusable = false
    }

    val graph: GraphView get() = graphView
    val rangeButton: Button get() = graphRangeButton

    fun setUpdateMessage(message: String) {
        graphUpdatedAt.text = message
    }

    fun showPlaceholder(show: Boolean) {
        graphPlaceholder.visibility = if (show) View.VISIBLE else View.GONE
        graphView.visibility = if (show) View.GONE else View.VISIBLE
    }
}
