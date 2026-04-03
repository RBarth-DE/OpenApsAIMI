package app.aaps.core.ui.elements

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class GlucoseCircleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    fun setGlucose(value: Double?, targetLow: Double?, targetHigh: Double?) {}
}
