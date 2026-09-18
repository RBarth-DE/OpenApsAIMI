package app.aaps.core.interfaces.pump

import android.widget.TextView
import app.aaps.core.data.model.TE

interface WarnColors {

    fun setColorInverse(view: TextView?, value: Double, warnLevel: Int, urgentLevel: Int)
    fun setColorByAge(view: TextView?, therapyEvent: TE, warnThreshold: Int, urgentThreshold: Int)
}
