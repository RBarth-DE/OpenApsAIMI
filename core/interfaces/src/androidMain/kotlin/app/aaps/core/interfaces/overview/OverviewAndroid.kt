package app.aaps.core.interfaces.overview

import android.content.Context
import android.widget.TextView

/**
 * The Android-only half of [Overview].
 *
 * Upstream deleted these members with the Compose migration. The fork's old overview screen still
 * uses them, so they live here instead of in commonMain. Callers cast the injected [Overview] to
 * this interface.
 */
interface OverviewAndroid : Overview {

    /**
     * Set textView content with version and warning color.
     */
    fun setVersionView(view: TextView)

    /**
     * Apply status lights settings from NS.
     */
    fun applyStatusLightsFromNs(context: Context?)
}
