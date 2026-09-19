package app.aaps.plugins.sync.nsclientV3.data

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Holds the device status the Nightscout client receives.
 *
 * This is the shared half, and it is upstream's file: a plain data holder with no Android types in
 * it, so it builds on every target. The fork's old overview screen shows those values as HTML, and
 * that half is Android only - it needs `ResourceHelper` and `HtmlHelper`, so it sits in
 * `ProcessedDeviceStatusDataAndroidImpl` in this module's androidMain and reads through this one.
 * One object holds the state; the Android class only formats it.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ProcessedDeviceStatusDataImpl(
    private val apsResultProvider: () -> APSResult
) : ProcessedDeviceStatusData {

    override var pumpData: ProcessedDeviceStatusData.PumpData? = null

    override var device: ProcessedDeviceStatusData.Device? = null

    override val uploaderMap = HashMap<String, ProcessedDeviceStatusData.Uploader>()

    override var openAPSData = ProcessedDeviceStatusData.OpenAPSData()

    override val openApsTimestamp: Long
        get() = if (openAPSData.clockSuggested != 0L) openAPSData.clockSuggested else -1

    override fun getAPSResult(): APSResult? =
        openAPSData.suggested?.let { apsResultProvider().with(it) }

    override val uploaderStatus: String
        get() {
            var minBattery = 100
            for ((_, uploader) in uploaderMap) {
                if (minBattery > uploader.battery) minBattery = uploader.battery
            }
            return "$minBattery%"
        }
}
