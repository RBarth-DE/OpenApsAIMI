package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputWeight
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerBgAcceWeight(
    deps: TriggerDeps
) : Trigger(deps) {

    var acceWeight = InputWeight()
    var comparator = Comparator(rh)

    constructor(deps: TriggerDeps, acceWeight: Double, compare: Comparator.Compare) : this(deps) {
        this.acceWeight = InputWeight(acceWeight)
        comparator = Comparator(rh, compare)
    }

    constructor(deps: TriggerDeps, triggerBgAcceWeight: TriggerBgAcceWeight) : this(deps) {
        this.acceWeight = InputWeight(triggerBgAcceWeight.acceWeight.value)
        comparator = Comparator(rh, triggerBgAcceWeight.comparator.value)
    }

    override suspend fun shouldRun(): Boolean {
        val actualWeight = preferences.get(DoubleKey.ApsAutoIsfBgAccelWeight)
        if (comparator.value.check(actualWeight, acceWeight.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "set bgAccel_ISF_weight ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "set bgAccel_ISF_weight NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON() =
        buildJsonObject {
            put("acce_weight", acceWeight.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val o = jsonOf(data)
        acceWeight.value = (o["acce_weight"]?.toString()?.toDoubleOrNull()) ?: 1.0
        comparator.value = Comparator.Compare.valueOf(o["comparator"]?.toString() ?: "IS_EQUAL")
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.autoisf_acce_weight

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.acceweightcompared, rh.gs(comparator.value.stringRes), acceWeight.value)

    override fun duplicate(): Trigger = TriggerBgAcceWeight(deps, this)
}
