package app.aaps.implementation.alerts.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class LocalAlertLongKey(
    override val key: String,
    override val defaultValue: Long,
) : LongNonPreferenceKey {

    NextPumpDisconnectedAlarm("nextPumpDisconnectedAlarm", 0L),
    NextMissedReadingsAlarm("nextMissedReadingsAlarm", 0L),
    NextHypoAlarm("nextHypoAlarm", 0L),
    NextHyperAlarm("nextHyperAlarm", 0L),
    NextRapidFallAlarm("nextRapidFallAlarm", 0L)
}