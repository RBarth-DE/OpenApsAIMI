package app.aaps.core.ui.compose

import androidx.compose.runtime.compositionLocalOf

/**
 * Opens another screen of this app by its class name.
 *
 * A shared screen sometimes has to open a screen that only one platform has, for example an Android
 * `Activity`. Only that platform can do it, so the host provides an implementation through
 * [LocalScreenOpener]. Where nobody provides one the default is [ScreenOpener.Unavailable]:
 * [isAvailable] stays false and the call site hides the control, so a shared screen never shows a
 * button that does nothing.
 *
 * [LocalAppIcon] is provided in the same way.
 */
interface ScreenOpener {

    /** False when this target cannot open screens by name. The call site should then hide the control. */
    val isAvailable: Boolean

    /**
     * Opens the screen with this fully qualified class name. Does nothing when that screen is not
     * part of this build or cannot be opened.
     */
    fun open(className: String)

    /** The default on every target that has no real implementation. */
    object Unavailable : ScreenOpener {
        override val isAvailable = false
        override fun open(className: String) = Unit
    }
}

/**
 * The [ScreenOpener] of this composition, [ScreenOpener.Unavailable] by default. An Android host
 * provides `AndroidScreenOpener` here.
 */
val LocalScreenOpener = compositionLocalOf<ScreenOpener> { ScreenOpener.Unavailable }
