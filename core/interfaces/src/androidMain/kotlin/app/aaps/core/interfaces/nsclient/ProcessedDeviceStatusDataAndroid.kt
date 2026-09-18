package app.aaps.core.interfaces.nsclient

import android.text.Spanned

/**
 * The Android-only half of [ProcessedDeviceStatusData].
 *
 * The status text is built as HTML and shown in the old overview screen, which is Android only.
 * Upstream moved this text assembly to common code with plain Strings. The Android implementation
 * is bound to both interfaces with a repeated `@ContributesBinding`, so a caller that needs the
 * HTML members injects this type directly while common code keeps injecting
 * [ProcessedDeviceStatusData].
 */
interface ProcessedDeviceStatusDataAndroid : ProcessedDeviceStatusData {

    fun pumpStatus(nsSettingsStatus: NSSettingsStatus): Spanned

    fun pumpStatusHtml(nsSettingsStatus: NSSettingsStatus): String

    val extendedPumpStatusHtml: String

    val extendedOpenApsStatusHtml: String

    val openApsStatus: Spanned

    val openApsStatusHtml: String

    val uploaderStatusSpanned: Spanned

    val uploaderStatusHtml: String

    val extendedUploaderStatusHtml: String
}
