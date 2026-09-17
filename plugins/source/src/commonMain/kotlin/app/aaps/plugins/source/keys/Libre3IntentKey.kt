package app.aaps.plugins.source.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.source.SourceStrings

enum class Libre3IntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.ACTIVITY,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {
    Status(
        key = "libre3_status",
        title = SourceStrings.libre3_status_title,
        summary = SourceStrings.libre3_status_summary
    ),
    Start(
        key = "libre3_start",
        title = SourceStrings.libre3_start_title,
        summary = SourceStrings.libre3_start_summary
    ),
    Warmup(
        key = "libre3_warmup",
        title = SourceStrings.libre3_warmup_title,
        summary = SourceStrings.libre3_warmup_summary
    ),
}
