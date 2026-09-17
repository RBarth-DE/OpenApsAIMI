package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionSetAcceWeight(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val uel: UserEntryLogger,
    private val preferences: Preferences
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var acceWeight: Double = 1.0

    override fun friendlyName(): TextRef = AutomationStrings.autoisf_acce_weight
    override fun shortDescription(): String = rh.gs(AutomationStrings.automate_set_acce_weight, acceWeight)
    override fun composeIcon() = Icons.Default.MonitorWeight

    override suspend fun doAction(): PumpEnactResult {
        val current = preferences.get(DoubleKey.ApsAutoIsfBgAccelWeight)
        return if (current != acceWeight) {
            uel.log(
                app.aaps.core.data.ue.Action.ACCE_WEIGHT_SET,
                Sources.Automation,
                "$title: ${rh.gs(AutomationStrings.automate_set_acce_weight, acceWeight)}"
            )
            preferences.put(DoubleKey.ApsAutoIsfBgAccelWeight, acceWeight)
            pumpEnactResultProvider().success(true).comment(AutomationStrings.weight_new)
        } else {
            pumpEnactResultProvider().success(false).comment(AutomationStrings.weight_old)
        }
    }

    override fun isValid(): Boolean = acceWeight > 0.0

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionSetAcceWeight::class.simpleName)
            put("data", buildJsonObject { put("weight", acceWeight) })
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        acceWeight = (o["weight"]?.toString()?.toDoubleOrNull()) ?: 1.0
        return this
    }
}
