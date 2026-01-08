package app.aaps.core.ui.views

import android.graphics.Color
import kotlin.math.roundToInt

object GlucoseColorMapper {

    // Default palette (can be overridden via GlucoseRingView styleable attrs)
    val DEFAULT_LOW_COLOR: Int = Color.parseColor("#2ECC71") // green
    val DEFAULT_HIGH_COLOR: Int = Color.parseColor("#E74C3C") // red

    const val DEFAULT_LOW_THRESHOLD_MGDL: Int = 100
    const val DEFAULT_HIGH_THRESHOLD_MGDL: Int = 220

    /**
     * Interpolated color from [lowColor] (<= [lowThresholdMgdl]) to [highColor] (>= [highThresholdMgdl]).
     */
    fun colorFor(
        bgMgdl: Int,
        lowColor: Int = DEFAULT_LOW_COLOR,
        highColor: Int = DEFAULT_HIGH_COLOR,
        lowThresholdMgdl: Int = DEFAULT_LOW_THRESHOLD_MGDL,
        highThresholdMgdl: Int = DEFAULT_HIGH_THRESHOLD_MGDL
    ): Int {
        val t = ((bgMgdl - lowThresholdMgdl).toFloat() / (highThresholdMgdl - lowThresholdMgdl).toFloat())
            .coerceIn(0f, 1f)
        return lerpColor(lowColor, highColor, t)
    }

    /**
     * Legacy helper: maps BG to a full 0..360 sweep.
     * (Kept for backward compatibility if other callers still use it.)
     */
    fun sweepFor(bgMgdl: Int): Float {
        return ((bgMgdl.coerceIn(40, 250) - 40) / 210f) * 360f
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val ar = Color.red(a)
        val ag = Color.green(a)
        val ab = Color.blue(a)
        val aa = Color.alpha(a)

        val br = Color.red(b)
        val bg = Color.green(b)
        val bb = Color.blue(b)
        val ba = Color.alpha(b)

        val r = (ar + (br - ar) * t).roundToInt()
        val g = (ag + (bg - ag) * t).roundToInt()
        val bl = (ab + (bb - ab) * t).roundToInt()
        val al = (aa + (ba - aa) * t).roundToInt()

        return Color.argb(al, r, g, bl)
    }
}
