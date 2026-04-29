package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.ui.graphics.vector.ImageVector
import dagger.android.HasAndroidInjector
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import org.json.JSONObject
import javax.inject.Inject

class ActionSetAcceWeight(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var sp: SP

    var acceWeight: Double = 1.0

    override fun friendlyName(): Int = R.string.autoisf_acce_weight
    override fun shortDescription(): String = rh.gs(R.string.automate_set_acce_weight, acceWeight)
    override fun composeIcon(): ImageVector = Icons.Default.MonitorWeight

    override suspend fun doAction(): PumpEnactResult {
        val current = sp.getDouble(R.string.bgAccel_ISF_weight, 0.0)
        return if (current != acceWeight) {
            uel.log(
                app.aaps.core.data.ue.Action.ACCE_WEIGHT_SET,
                Sources.Automation,
                "$title: ${rh.gs(R.string.automate_set_acce_weight, acceWeight)}"
            )
            sp.putDouble(R.string.bgAccel_ISF_weight, acceWeight)
            pumpEnactResultProvider.get().success(true).comment(R.string.weight_new)
        } else {
            pumpEnactResultProvider.get().success(false).comment(R.string.weight_old)
        }
    }

    override fun isValid(): Boolean = acceWeight > 0.0

    override fun toJSON(): String =
        JSONObject()
            .put("type", this.javaClass.name)
            .put("data", JSONObject().put("weight", acceWeight))
            .toString()

    override fun fromJSON(data: String): Action {
        acceWeight = JsonHelper.safeGetDouble(JSONObject(data), "weight", 1.0)
        return this
    }
}