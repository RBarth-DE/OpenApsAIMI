package app.aaps.core.ui.elements

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class GlucoseCircleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var glucoseValue: Double? = null
    private var targetLow: Double? = null
    private var targetHigh: Double? = null

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    fun setGlucose(value: Double?, targetLow: Double?, targetHigh: Double?) {
        this.glucoseValue = value
        this.targetLow = targetLow
        this.targetHigh = targetHigh
        invalidate()
    }

    private fun glucoseColor(): Int {
        val v = glucoseValue ?: return Color.GRAY
        val low = targetLow ?: 70.0
        val high = targetHigh ?: 180.0
        return when {
            v < low -> Color.parseColor("#FF6D00")
            v > high -> Color.parseColor("#FFD600")
            else -> Color.parseColor("#00C853")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 10f
        circlePaint.color = Color.argb(40, 128, 128, 128)
        canvas.drawCircle(cx, cy, r, circlePaint)
        ringPaint.color = glucoseColor()
        canvas.drawCircle(cx, cy, r, ringPaint)
    }
}
