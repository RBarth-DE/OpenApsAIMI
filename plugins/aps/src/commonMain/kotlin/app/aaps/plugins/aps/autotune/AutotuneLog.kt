package app.aaps.plugins.aps.autotune

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The text of the autotune session that is running, and the place its steps log to.
 *
 * One instance holds one session: the steps append their lines as they go and `AutotuneFS` writes the
 * whole text to `autotune.<date>.log` when the run ends. It is a class of its own, and not part of
 * `AutotuneFS`, because the steps that log are shared code while the file writing is Android only.
 */
@SingleIn(AppScope::class)
@Inject
class AutotuneLog(private val aapsLogger: AAPSLogger) {

    private val builder = StringBuilder()

    /** Everything logged since the last [clear]. */
    val text: String
        get() = builder.toString()

    fun atLog(message: String) {
        builder.append(message).append('\n')
        aapsLogger.debug(LTag.AUTOTUNE, message)
    }

    /** Empties the log, once its text has been written to a file. */
    fun clear() {
        builder.clear()
    }
}
