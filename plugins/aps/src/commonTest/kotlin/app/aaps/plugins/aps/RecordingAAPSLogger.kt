package app.aaps.plugins.aps

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

/**
 * A logger for `commonTest`.
 *
 * Mockito is JVM only, so a test that runs on iOS cannot mock [AAPSLogger] - it needs a hand written
 * one. Writing it once here rather than inside each test keeps the nineteen members in one place.
 *
 * It records, rather than only swallowing: most of what these tests check is what the class said
 * about a decision, and a recording fake can show that without any mocking at all.
 */
class RecordingAAPSLogger : AAPSLogger {

    /** Everything logged through the plain `message` overloads, in the order it was logged. */
    val messages = mutableListOf<String>()

    /** Everything logged at error level, whatever overload was used. */
    val errors = mutableListOf<String>()

    private fun record(message: String): String {
        messages.add(message)
        return message
    }

    override fun debug(message: String) {
        record(message)
    }

    override fun debug(enable: Boolean, tag: LTag, message: String) {
        if (enable) record(message)
    }

    override fun debug(tag: LTag, message: String) {
        record(message)
    }

    /** The accessor is not called: it exists so a disabled log never builds the string. */
    override fun debug(tag: LTag, accessor: () -> String) {
    }

    override fun debug(tag: LTag, format: String, vararg arguments: Any?) {
        record(format)
    }

    override fun warn(tag: LTag, message: String) {
        record(message)
    }

    override fun warn(tag: LTag, format: String, vararg arguments: Any?) {
        record(format)
    }

    override fun info(tag: LTag, message: String) {
        record(message)
    }

    override fun info(tag: LTag, format: String, vararg arguments: Any?) {
        record(format)
    }

    override fun error(tag: LTag, message: String) {
        errors.add(record(message))
    }

    override fun error(tag: LTag, message: String, throwable: Throwable) {
        errors.add(record(message))
    }

    override fun error(tag: LTag, format: String, vararg arguments: Any?) {
        errors.add(record(format))
    }

    override fun error(message: String) {
        errors.add(record(message))
    }

    override fun error(message: String, throwable: Throwable) {
        errors.add(record(message))
    }

    override fun error(format: String, vararg arguments: Any?) {
        errors.add(record(format))
    }

    override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {
        record(message)
    }

    override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {
        record(message)
    }

    override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {
        record(message)
    }

    override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {
        errors.add(record(message))
    }
}
