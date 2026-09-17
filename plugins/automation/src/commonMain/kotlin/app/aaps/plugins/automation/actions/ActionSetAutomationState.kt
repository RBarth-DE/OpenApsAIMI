package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.automation.AutomationStrings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionSetAutomationState(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val automationStateService: AutomationStateInterface
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var stateName: String = ""
    var stateValue: String = ""

    override fun friendlyName(): TextRef = AutomationStrings.action_set_automation_state
    override fun shortDescription(): String = rh.gs(AutomationStrings.action_set_automation_state_short, stateName, stateValue)
    override fun composeIcon() = Icons.Default.Tune

    fun availableStateNames(): List<String> =
        automationStateService.getAllStates().map { it.first }

    fun availableStateValues(): List<String> =
        if (stateName.isNotEmpty() && automationStateService.hasStateValues(stateName))
            automationStateService.getStateValues(stateName)
        else emptyList()

    override suspend fun doAction(): PumpEnactResult {
        return try {
            automationStateService.setState(stateName, stateValue)
            pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
        } catch (e: IllegalStateException) {
            pumpEnactResultProvider().success(false).comment(e.message ?: rh.gs(CoreUiStrings.error))
        }
    }

    override fun isValid(): Boolean = stateName.isNotEmpty() && stateValue.isNotEmpty()

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionSetAutomationState::class.simpleName)
            put("data", buildJsonObject {
                put("stateName", stateName)
                put("stateValue", stateValue)
            })
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        stateName = o["stateName"]?.toString() ?: ""
        stateValue = o["stateValue"]?.toString() ?: ""
        return this
    }
}
