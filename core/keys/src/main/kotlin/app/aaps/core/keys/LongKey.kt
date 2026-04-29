package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey

enum class LongKey(
    override val key: String,
    override val defaultValue: Long,
    override val min: Long = Long.MIN_VALUE,
    override val max: Long = Long.MAX_VALUE,
    override val titleResId: Int = 0,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : LongPreferenceKey {

    FslSmoothLastTimeRaw(key = "fsl_last_time_raw", defaultValue = -1, min = -1, defaultedBySM = true),
    FslCalibrationStart(key = "fsl_cal_start_time", defaultValue =-1, min = -1, defaultedBySM = true),
    AppStart(key = "app_start_time",defaultValue = 0, defaultedBySM = true),

}
