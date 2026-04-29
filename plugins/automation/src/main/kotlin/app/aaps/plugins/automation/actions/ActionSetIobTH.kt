package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Percent
import androidx.compose.ui.graphics.vector.ImageVector
import dagger.android.HasAndroidInjector
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import org.json.JSONObject
import javax.inject.Inject

class ActionSetIobTH(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var sp: SP

    var iobTHPercent: Int = 100

    override fun friendlyName(): Int = R.string.autoisf_iobTH_percent
    override fun shortDescription(): String = rh.gs(R.string.automate_set_iobTH_percent, iobTHPercent)
    override fun composeIcon(): ImageVector = Icons.Default.Percent

    override suspend fun doAction(): PumpEnactResult {
        val current = sp.getInt(R.string.iob_threshold_percent, 100)
        return if (current != iobTHPercent) {
            uel.log(
                app.aaps.core.data.ue.Action.IOB_TH_SET,
                Sources.Automation,
                "$title: ${rh.gs(R.string.automate_set_iobTH_percent, iobTHPercent)}",
                ValueWithUnit.Percent(iobTHPercent)
            )
            sp.putInt(R.string.iob_threshold_percent, iobTHPercent)
            pumpEnactResultProvider.get().success(true).comment(R.string.weight_new)
        } else {
            pumpEnactResultProvider.get().success(false).comment(R.string.weight_old)
        }
    }

    override fun isValid(): Boolean = iobTHPercent in 10..200

    override fun toJSON(): String =
        JSONObject()
            .put("type", this.javaClass.name)
            .put("data", JSONObject().put("percentage", iobTHPercent))
            .toString()

    override fun fromJSON(data: String): Action {
        iobTHPercent = JsonHelper.safeGetInt(JSONObject(data), "percentage", 100)
        return this
    }
}