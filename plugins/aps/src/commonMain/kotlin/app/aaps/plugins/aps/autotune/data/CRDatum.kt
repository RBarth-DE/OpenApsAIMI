package app.aaps.plugins.aps.autotune.data

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientStringOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Created by Rumen Georgiev on 2/26/2018.
 */
class CRDatum {

    var crInitialIOB = 0.0
    var crInitialBG = 0.0
    var crInitialCarbTime = 0L
    var crEndIOB = 0.0
    var crEndBG = 0.0
    var crEndTime = 0L
    var crCarbs = 0.0
    var crInsulin = 0.0
    var crInsulinTotal = 0.0
    var dateUtil: DateUtil

    constructor(dateUtil: DateUtil) {
        this.dateUtil = dateUtil
    }

    /** A key that is absent leaves the field at its current value, as the old `has()` guard did. */
    constructor(json: JsonObject, dateUtil: DateUtil) {
        this.dateUtil = dateUtil
        crInitialIOB = json.lenientDouble("CRInitialIOB", crInitialIOB)
        crInitialBG = json.lenientDouble("CRInitialBG", crInitialBG)
        crInitialCarbTime = json.lenientStringOrNull("CRInitialCarbTime")?.let { dateUtil.fromISODateString(it) } ?: crInitialCarbTime
        crEndIOB = json.lenientDouble("CREndIOB", crEndIOB)
        crEndBG = json.lenientDouble("CREndBG", crEndBG)
        crEndTime = json.lenientStringOrNull("CREndTime")?.let { dateUtil.fromISODateString(it) } ?: crEndTime
        crCarbs = json.lenientDouble("CRCarbs", crCarbs)
        crInsulin = json.lenientDouble("CRInsulin", crInsulin)
    }

    fun toJSON(): JsonObject = buildJsonObject {
        put("CRInitialIOB", crInitialIOB)
        put("CRInitialBG", crInitialBG.toInt())
        put("CRInitialCarbTime", dateUtil.toISOString(crInitialCarbTime))
        put("CREndIOB", crEndIOB)
        put("CREndBG", crEndBG.toInt())
        put("CREndTime", dateUtil.toISOString(crEndTime))
        put("CRCarbs", crCarbs.toInt())
        put("CRInsulin", crInsulin)
    }

    fun equals(obj: CRDatum): Boolean {
        var isEqual = true
        if (crInitialIOB != obj.crInitialIOB) isEqual = false
        if (crInitialBG != obj.crInitialBG) isEqual = false
        if (crInitialCarbTime / 1000 != obj.crInitialCarbTime / 1000) isEqual = false
        if (crEndIOB != obj.crEndIOB) isEqual = false
        if (crEndBG != obj.crEndBG) isEqual = false
        if (crEndTime / 1000 != obj.crEndTime / 1000) isEqual = false
        if (crCarbs != obj.crCarbs) isEqual = false
        if (crInsulin != obj.crInsulin) isEqual = false
        return isEqual
    }
}