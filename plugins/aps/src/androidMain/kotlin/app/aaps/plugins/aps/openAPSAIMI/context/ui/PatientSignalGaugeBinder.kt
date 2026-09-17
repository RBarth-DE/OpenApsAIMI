package app.aaps.plugins.aps.openAPSAIMI.context.ui

import android.view.View
import android.widget.TextView
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSAIMI.patient.PatientSignalGauge
import com.google.android.material.progressindicator.LinearProgressIndicator

internal object PatientSignalGaugeBinder {

    fun bind(container: View, gauge: PatientSignalGauge) {
        val views = PatientSignalGaugeViews(container)
        views.textGaugeLabel.text = "${gauge.label} ${gauge.percent}%"
        views.progressGauge.setProgressCompat(gauge.percent, true)
    }

    fun bindAll(
        mealContainer: View,
        endogenousContainer: View,
        resistanceContainer: View,
        thermalContainer: View,
        sensorContainer: View,
        gauges: List<PatientSignalGauge>,
    ) {
        if (gauges.size < 5) {
            return
        }
        bind(mealContainer, gauges[0])
        bind(endogenousContainer, gauges[1])
        bind(resistanceContainer, gauges[2])
        bind(thermalContainer, gauges[3])
        bind(sensorContainer, gauges[4])
    }
}

/**
 * View ports of `item_patient_signal_gauge.xml`.
 *
 * Written by hand: a Kotlin Multiplatform module never gets generated view binding classes, so
 * there is no `ItemPatientSignalGaugeBinding` to use. See `ActivityContextViews` for the same
 * reason.
 */
private class PatientSignalGaugeViews(root: View) {
    val textGaugeLabel: TextView = root.findViewById(R.id.textGaugeLabel)
    val progressGauge: LinearProgressIndicator = root.findViewById(R.id.progressGauge)
}
