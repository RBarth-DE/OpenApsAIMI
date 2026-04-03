package app.aaps.core.ui.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class GlucoseRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    fun update(bgMgdl: Int, mainText: String, subLeftText: String, subRightText: String, noseAngleDeg: Float? = null) {}
}
