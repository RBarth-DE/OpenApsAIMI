package app.aaps.plugins.aps.autotune.data

import app.aaps.core.utils.lenientDouble
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DiaDeviation(var dia: Double = 0.0, var meanDeviation: Double = 0.0, var smrDeviation: Double = 0.0, var rmsDeviation: Double = 0.0) {

    /** A key that is absent leaves the field at its current value, as the old `has()` guard did. */
    constructor(json: JsonObject) : this() {
        dia = json.lenientDouble("dia", dia)
        meanDeviation = json.lenientDouble("meanDeviation", meanDeviation)
        smrDeviation = json.lenientDouble("SMRDeviation", smrDeviation)
        rmsDeviation = json.lenientDouble("RMSDeviation", rmsDeviation)
    }

    fun toJSON(): JsonObject = buildJsonObject {
        put("dia", dia)
        put("meanDeviation", meanDeviation.toInt())
        put("SMRDeviation", smrDeviation)
        put("RMSDeviation", rmsDeviation.toInt())
    }
}
