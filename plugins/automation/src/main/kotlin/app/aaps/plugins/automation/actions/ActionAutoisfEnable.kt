package app.aaps.plugins.automation.actions

import dagger.android.HasAndroidInjector
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.automation.R
import javax.inject.Inject

class ActionAutoisfEnable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var sp: SP

    override fun friendlyName(): Int = R.string.enableautoisf
    override fun shortDescription(): String = rh.gs(R.string.enableautoisf)

    override suspend fun doAction(): PumpEnactResult {
        val currentAutoisfStatus: Boolean = sp.getBoolean(R.string.enable_autoISF, true)
        return if (!currentAutoisfStatus) {
            uel.log(app.aaps.core.data.ue.Action.AUTOISF_ENABLED, Sources.Automation, title)
            sp.putBoolean(R.string.enable_autoISF, true)
            pumpEnactResultProvider.get().success(true).comment(R.string.autoisf_enabled)
        } else {
            pumpEnactResultProvider.get().success(true).comment(R.string.autoisf_alreadyenabled)
        }
    }

    override fun isValid(): Boolean = true

    override fun hasDialog(): Boolean = false
}
