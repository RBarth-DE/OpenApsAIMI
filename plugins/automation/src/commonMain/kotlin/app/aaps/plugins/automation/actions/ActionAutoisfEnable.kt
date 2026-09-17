package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.data.ue.Sources
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings

class ActionAutoisfEnable(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val uel: UserEntryLogger,
    private val preferences: Preferences
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    override fun friendlyName(): TextRef = AutomationStrings.enableautoisf
    override fun shortDescription(): String = rh.gs(AutomationStrings.enableautoisf)

    override suspend fun doAction(): PumpEnactResult {
        val current = preferences.get(BooleanKey.ApsUseAutoIsfWeights)
        return if (!current) {
            uel.log(app.aaps.core.data.ue.Action.AUTOISF_ENABLED, Sources.Automation, title)
            preferences.put(BooleanKey.ApsUseAutoIsfWeights, true)
            pumpEnactResultProvider().success(true).comment(AutomationStrings.autoisf_enabled)
        } else {
            pumpEnactResultProvider().success(true).comment(AutomationStrings.autoisf_alreadyenabled)
        }
    }

    override fun isValid(): Boolean = true

    override fun hasDialog(): Boolean = false
}
