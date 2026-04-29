package app.aaps.plugins.automation.actions

import dagger.android.HasAndroidInjector
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.automation.R
import javax.inject.Inject

class ActionAutoisfDisable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var sp: SP

    override fun friendlyName(): Int = R.string.disableautoisf
    override fun shortDescription(): String = rh.gs(R.string.disableautoisf)

    override suspend fun doAction(): PumpEnactResult {
        val currentAutoisfStatus: Boolean = sp.getBoolean(R.string.enable_autoISF, false)
        return if (currentAutoisfStatus) {
            uel.log(app.aaps.core.data.ue.Action.AUTOISF_DISABLED, Sources.Automation, title)
            sp.putBoolean(R.string.enable_autoISF, false)
            pumpEnactResultProvider.get().success(true).comment(R.string.autoisf_disabled)
        } else {
            pumpEnactResultProvider.get().success(true).comment(R.string.autoisf_alreadydisabled)
        }
    }

    override fun isValid(): Boolean = true

    override fun hasDialog(): Boolean = false

}
