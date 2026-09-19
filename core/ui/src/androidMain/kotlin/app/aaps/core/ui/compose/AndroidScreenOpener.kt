package app.aaps.core.ui.compose

import android.content.Context
import android.content.Intent

/**
 * Android [ScreenOpener]. Pass the `Activity` that shows the screen: starting an activity from an
 * application context would need `FLAG_ACTIVITY_NEW_TASK` and would put it in another task.
 */
class AndroidScreenOpener(private val context: Context) : ScreenOpener {

    override val isAvailable = true

    override fun open(className: String) {
        try {
            context.startActivity(Intent().setClassName(context, className))
        } catch (_: Exception) {
            // The screen may be missing from this flavour or refuse to start. The shared screen
            // must not crash because of that, so keep the caller's old behaviour: do nothing.
        }
    }
}
