package app.aaps.implementation.pump

import android.widget.TextView
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.pump.WarnColors
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.R
import dev.zacsweers.metro.Inject

class WarnColorsImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil
) : WarnColors {

    override fun setColorInverse(view: TextView?, value: Double, warnLevel: Int, urgentLevel: Int) {
        view?.setTextColor(
            rh.gac(
                view.context,
                when {
                    value <= urgentLevel -> R.attr.urgentColor
                    value <= warnLevel   -> R.attr.warnColor
                    else                 -> R.attr.defaultTextColor
                }
            )
        )
    }

    override fun setColorByAge(view: TextView?, therapyEvent: TE, warnThreshold: Int, urgentThreshold: Int) {
        view?.setTextColor(
            rh.gac(
                view.context,
                when {
                    therapyEvent.isOlderThan(urgentThreshold, dateUtil) -> R.attr.lowColor
                    therapyEvent.isOlderThan(warnThreshold, dateUtil)   -> R.attr.highColor
                    else                                                -> R.attr.defaultTextColor
                }
            )
        )
    }
}
