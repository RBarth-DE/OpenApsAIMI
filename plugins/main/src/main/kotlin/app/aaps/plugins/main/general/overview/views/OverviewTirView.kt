package app.aaps.plugins.main.general.overview.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.aaps.plugins.main.databinding.ComponentOverviewTirBinding

class OverviewTirView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding = ComponentOverviewTirBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        orientation = VERTICAL
    }

    fun update(veryLow: Double, low: Double, inRange: Double, high: Double, veryHigh: Double, avg: Double, a1c: Double) {
        updateBar(binding.tirVlBar, binding.tirVlLabel, veryLow)
        updateBar(binding.tirLBar, binding.tirLLabel, low)
        updateBar(binding.tirTrBar, binding.tirTrLabel, inRange)
        updateBar(binding.tirHBar, binding.tirHLabel, high)
        updateBar(binding.tirVhBar, binding.tirVhLabel, veryHigh)
        binding.tirStats.text = String.format("Avg %.0f mg/dL · A1C %.1f%%", avg, a1c)
    }

    private fun updateBar(bar: View, label: TextView, value: Double) {
        val params = bar.layoutParams as LayoutParams
        params.weight = maxOf(0.00001f, (value / 100.0).toFloat())
        bar.layoutParams = params
        label.text = if (value >= 5.0) String.format("%.0f%%", value) else ""
    }
}
