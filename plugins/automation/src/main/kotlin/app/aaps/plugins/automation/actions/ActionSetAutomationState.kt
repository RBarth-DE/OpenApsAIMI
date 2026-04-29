package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionSetAutomationState(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var automationStateService: AutomationStateInterface

    var stateName: String = ""
    var stateValue: String = ""

    override fun friendlyName(): Int = R.string.action_set_automation_state
    override fun shortDescription(): String = rh.gs(R.string.action_set_automation_state_short, stateName, stateValue)
    override fun composeIcon(): ImageVector = Icons.Default.Tune

    fun availableStateNames(): List<String> =
        if (::automationStateService.isInitialized) automationStateService.getAllStates().map { it.first } else emptyList()

    fun availableStateValues(): List<String> =
        if (::automationStateService.isInitialized && stateName.isNotEmpty() && automationStateService.hasStateValues(stateName))
            automationStateService.getStateValues(stateName)
        else emptyList()

    override suspend fun doAction(): PumpEnactResult {
        return try {
            automationStateService.setState(stateName, stateValue)
            pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
        } catch (e: IllegalStateException) {
            pumpEnactResultProvider.get().success(false).comment(e.message ?: rh.gs(app.aaps.core.ui.R.string.error))
        }
    }

    override fun isValid(): Boolean = stateName.isNotEmpty() && stateValue.isNotEmpty()

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String =
        JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", JSONObject()
                .put("stateName", stateName)
                .put("stateValue", stateValue))
            .toString()

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        stateName = JsonHelper.safeGetString(o, "stateName", "")
        stateValue = JsonHelper.safeGetString(o, "stateValue", "")
        return this
    }
}