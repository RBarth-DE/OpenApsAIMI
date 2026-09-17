package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputIobTH
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerIobTH(
    deps: TriggerDeps
) : Trigger(deps) {

    var IobTHpercent = InputIobTH()
    var comparator = Comparator(rh)

    constructor(deps: TriggerDeps, IobTHpercent: Double, compare: Comparator.Compare) : this(deps) {
        this.IobTHpercent = InputIobTH(IobTHpercent.toInt())
        comparator = Comparator(rh, compare)
    }

    constructor(deps: TriggerDeps, triggerIobTH: TriggerIobTH) : this(deps) {
        this.IobTHpercent = InputIobTH(triggerIobTH.IobTHpercent.value)
        comparator = Comparator(rh, triggerIobTH.comparator.value)
    }

    fun setValue(IobTHpercent: Int): TriggerIobTH {
        this.IobTHpercent.value = IobTHpercent
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerIobTH {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val actualPercent = preferences.get(IntKey.ApsAutoIsfIobThPercent)
        if (comparator.value.check(actualPercent, IobTHpercent.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "set iob_threshold_percent ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "set iob_threshold_percent NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON() =
        buildJsonObject {
            put("iobTH_percent", IobTHpercent.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val o = jsonOf(data)
        IobTHpercent.value = (o["iobTH_percent"]?.toString()?.toIntOrNull()) ?: 100
        comparator.value = Comparator.Compare.valueOf(o["comparator"]?.toString() ?: "IS_EQUAL")
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.autoisf_iobTH_percent

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.iobTHpercentcompared, rh.gs(comparator.value.stringRes), IobTHpercent.value.toInt())

    override fun duplicate(): Trigger = TriggerIobTH(deps, this)
}
