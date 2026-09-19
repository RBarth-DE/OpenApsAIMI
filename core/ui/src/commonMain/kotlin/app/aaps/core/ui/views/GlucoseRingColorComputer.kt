package app.aaps.core.ui.views

/**
 * Pure palette logic for glucose ring UIs: the Android view `GlucoseRingView`, and
 * [app.aaps.core.ui.compose.dashboard.GlucoseHeroRing] (unit-tested, no Android View).
 *
 * Lives in commonMain so the Compose ring can use it on every target. Colours are plain ARGB
 * ints, which both callers already work in, so nothing here needs a platform type.
 */
object GlucoseRingColorComputer {

    /** Fallback for a missing BG reading. Same value as `android.graphics.Color.GRAY`. */
    private const val GRAY = 0xFF888888.toInt()

    fun compute(
        bgMgdl: Int?,
        hypoMaxFromProfile: Float?,
        severeHypoMaxMgdl: Float,
        hypoMaxMgdlAttr: Float,
        useSteppedColors: Boolean,
        step1MaxMgdl: Float,
        step2MaxMgdl: Float,
        step3MaxMgdl: Float,
        stepColor1: Int,
        stepColor2: Int,
        stepColor3: Int,
        stepColor4: Int,
    ): Int {
        val v = bgMgdl ?: return GRAY
        val hypoCap = (hypoMaxFromProfile ?: hypoMaxMgdlAttr).coerceAtLeast(severeHypoMaxMgdl + 1f)
        val vf = v.toFloat()
        if (!useSteppedColors) {
            return when {
                vf < severeHypoMaxMgdl -> stepColor4
                vf < hypoCap -> stepColor3
                vf <= 180f -> stepColor1
                else -> stepColor3
            }
        }
        return when {
            vf < severeHypoMaxMgdl -> stepColor4
            vf < hypoCap -> stepColor3
            vf <= step1MaxMgdl -> stepColor1
            vf <= step2MaxMgdl -> stepColor2
            vf <= step3MaxMgdl -> stepColor3
            else -> stepColor4
        }
    }
}
