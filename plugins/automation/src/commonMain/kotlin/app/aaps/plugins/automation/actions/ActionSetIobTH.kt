package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Percent
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionSetIobTH(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val uel: UserEntryLogger,
    private val preferences: Preferences
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var iobTHPercent: Int = 100

    override fun friendlyName(): TextRef = AutomationStrings.autoisf_iobTH_percent
    override fun shortDescription(): String = rh.gs(AutomationStrings.automate_set_iobTH_percent, iobTHPercent)
    override fun composeIcon() = Icons.Default.Percent

    override suspend fun doAction(): PumpEnactResult {
        val current = preferences.get(IntKey.ApsAutoIsfIobThPercent)
        return if (current != iobTHPercent) {
            uel.log(
                app.aaps.core.data.ue.Action.IOB_TH_SET,
                Sources.Automation,
                "$title: ${rh.gs(AutomationStrings.automate_set_iobTH_percent, iobTHPercent)}",
                app.aaps.core.data.ue.ValueWithUnit.Percent(iobTHPercent)
            )
            preferences.put(IntKey.ApsAutoIsfIobThPercent, iobTHPercent)
            pumpEnactResultProvider().success(true).comment(AutomationStrings.weight_new)
        } else {
            pumpEnactResultProvider().success(false).comment(AutomationStrings.weight_old)
        }
    }

    override fun isValid(): Boolean = iobTHPercent in 0..100

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionSetIobTH::class.simpleName)
            put("data", buildJsonObject { put("iobTHPercent", iobTHPercent) })
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        iobTHPercent = (o["iobTHPercent"]?.toString()?.toIntOrNull()) ?: 100
        return this
    }
}
