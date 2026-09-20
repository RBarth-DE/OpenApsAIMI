package app.aaps.core.interfaces.logging

/**
 * A hand written [AAPSLogger] that keeps what was logged.
 *
 * Mockito is JVM only, so a test that used `mock<AAPSLogger>()` cannot move to `commonTest` with the
 * class it covers. This replaces it. Nothing here calls the lazy overload's accessor: a fake that
 * evaluated it would run code the test did not ask for, and the point of the overload is that the
 * message is only built when the log level allows it.
 *
 * [messages] holds everything at info level or above, in order, as `TAG message`. [errors] holds the
 * error messages on their own, which is what most assertions want.
 *
 * A `format` overload records the format string without its arguments - there is no shared
 * `String.format` to fill them in with. An assertion that needs the filled-in text has to build that
 * text itself.
 */
class RecordingAAPSLogger : AAPSLogger {

    val messages = mutableListOf<String>()
    val errors = mutableListOf<String>()

    override fun debug(message: String) = record(LTag.CORE, message)

    override fun debug(enable: Boolean, tag: LTag, message: String) {
        // The flag is the caller's decision about whether this message is worth logging at all.
        if (enable) record(tag, message)
    }

    override fun debug(tag: LTag, message: String) = record(tag, message)

    override fun debug(tag: LTag, accessor: () -> String) = Unit

    override fun debug(tag: LTag, format: String, vararg arguments: Any?) = record(tag, format)

    override fun warn(tag: LTag, message: String) = record(tag, message)

    override fun warn(tag: LTag, format: String, vararg arguments: Any?) = record(tag, format)

    override fun info(tag: LTag, message: String) = record(tag, message)

    override fun info(tag: LTag, format: String, vararg arguments: Any?) = record(tag, format)

    override fun error(tag: LTag, message: String) {
        record(tag, message)
        errors.add(message)
    }

    override fun error(tag: LTag, message: String, throwable: Throwable) {
        record(tag, message)
        errors.add(message)
    }

    override fun error(tag: LTag, format: String, vararg arguments: Any?) {
        record(tag, format)
        errors.add(format)
    }

    override fun error(message: String) {
        record(LTag.CORE, message)
        errors.add(message)
    }

    override fun error(message: String, throwable: Throwable) {
        record(LTag.CORE, message)
        errors.add(message)
    }

    override fun error(format: String, vararg arguments: Any?) {
        record(LTag.CORE, format)
        errors.add(format)
    }

    override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = record(tag, message)

    override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = record(tag, message)

    override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = record(tag, message)

    override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {
        record(tag, message)
        errors.add(message)
    }

    private fun record(tag: LTag, message: String) {
        messages.add("$tag $message")
    }
}
