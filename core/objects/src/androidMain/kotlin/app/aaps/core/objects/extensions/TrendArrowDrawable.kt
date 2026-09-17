package app.aaps.core.objects.extensions

import androidx.annotation.DrawableRes
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.objects.R

/**
 * Trend arrow as a drawable resource. This is for the parts of the UI that still use an Android
 * `ImageView`. Compose code uses `directionToIcon` in :core:ui instead.
 *
 * Android only, because the drawables live in this module's androidMain resources.
 */
@DrawableRes
fun TrendArrow.directionToLegacyDrawable(): Int =
    when (this) {
        TrendArrow.TRIPLE_DOWN     -> R.drawable.ic_invalid
        TrendArrow.DOUBLE_DOWN     -> R.drawable.ic_doubledown
        TrendArrow.SINGLE_DOWN     -> R.drawable.ic_singledown
        TrendArrow.FORTY_FIVE_DOWN -> R.drawable.ic_fortyfivedown
        TrendArrow.FLAT            -> R.drawable.ic_flat
        TrendArrow.FORTY_FIVE_UP   -> R.drawable.ic_fortyfiveup
        TrendArrow.SINGLE_UP       -> R.drawable.ic_singleup
        TrendArrow.DOUBLE_UP       -> R.drawable.ic_doubleup
        TrendArrow.TRIPLE_UP       -> R.drawable.ic_invalid
        TrendArrow.NONE            -> R.drawable.ic_invalid
    }
