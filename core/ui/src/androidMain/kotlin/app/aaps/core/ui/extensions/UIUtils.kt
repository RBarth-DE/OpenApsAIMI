package app.aaps.core.ui.extensions

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun Boolean.toVisibility() = if (this) View.VISIBLE else View.GONE
fun Boolean.toVisibilityKeepSpace() = if (this) View.VISIBLE else View.INVISIBLE

fun runOnUiThread(runnable: Runnable?) = runnable?.let {
    Handler(Looper.getMainLooper()).post(it)
}

/**
 * Calls [block] with the size of the system bars and of the camera cutout, now and whenever they
 * change. Use it when a screen has to place one of its views rather than pad the whole content.
 *
 * A view only receives the part of the insets that its parents did not use already. A screen that
 * has an action bar handling the status bar therefore gets a top value of 0 here, and no double gap.
 */
fun View.onSystemBarInsets(block: (Insets) -> Unit) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        block(insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()))
        insets
    }
}

/**
 * Keeps the content of this view clear of the status bar, the navigation bar and the camera cutout.
 *
 * Call it on the root view of a screen. The padding is added inside that view, so a background
 * colour set on it still covers the whole screen and only the child views move inwards. Any padding
 * the view already has is kept and the insets are added on top.
 *
 * From Android 15 every window is drawn behind the system bars. A screen that does not ask for
 * these insets shows its first rows under the camera and its last rows under the navigation bar.
 */
fun View.applySystemBarPadding() {
    val startPadding = paddingLeft
    val topPadding = paddingTop
    val endPadding = paddingRight
    val bottomPadding = paddingBottom
    onSystemBarInsets { bars ->
        setPadding(
            startPadding + bars.left,
            topPadding + bars.top,
            endPadding + bars.right,
            bottomPadding + bars.bottom
        )
    }
}
