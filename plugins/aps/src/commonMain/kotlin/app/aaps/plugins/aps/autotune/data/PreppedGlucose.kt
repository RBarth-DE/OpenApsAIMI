package app.aaps.plugins.aps.autotune.data

import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Suppress("SpellCheckingInspection") class PreppedGlucose {

    var crData: List<CRDatum> = ArrayList()
    var csfGlucoseData: List<BGDatum> = ArrayList()
    var isfGlucoseData: List<BGDatum> = ArrayList()
    var basalGlucoseData: List<BGDatum> = ArrayList()
    var diaDeviations: List<DiaDeviation> = ArrayList()
    var peakDeviations: List<PeakDeviation> = ArrayList()
    var from: Long = 0
    lateinit var dateUtil: DateUtil

    // to generate same king of json string than oref0-autotune-prep
    override fun toString(): String {
        return toString(0)
    }

    constructor(from: Long, crData: List<CRDatum>, csfGlucoseData: List<BGDatum>, isfGlucoseData: List<BGDatum>, basalGlucoseData: List<BGDatum>, dateUtil: DateUtil) {
        this.from = from
        this.crData = crData
        this.csfGlucoseData = csfGlucoseData
        this.isfGlucoseData = isfGlucoseData
        this.basalGlucoseData = basalGlucoseData
        this.dateUtil = dateUtil
    }

    constructor(json: JsonObject?, dateUtil: DateUtil) {
        if (json == null) return
        this.dateUtil = dateUtil
        crData = ArrayList()
        csfGlucoseData = ArrayList()
        isfGlucoseData = ArrayList()
        basalGlucoseData = ArrayList()
        crData = jsonCRDataToList(json["CRData"] as? JsonArray)
        csfGlucoseData = jsonGlucoseDataToList(json["CSFGlucoseData"] as? JsonArray)
        isfGlucoseData = jsonGlucoseDataToList(json["ISFGlucoseData"] as? JsonArray)
        basalGlucoseData = jsonGlucoseDataToList(json["basalGlucoseData"] as? JsonArray)
    }

    private fun jsonGlucoseDataToList(array: JsonArray?): List<BGDatum> {
        val bgData: MutableList<BGDatum> = ArrayList()
        array?.forEach { element ->
            (element as? JsonObject)?.let { bgData.add(BGDatum(it, dateUtil)) }
        }
        return bgData
    }

    private fun jsonCRDataToList(array: JsonArray?): List<CRDatum> {
        val crData: MutableList<CRDatum> = ArrayList()
        array?.forEach { element ->
            (element as? JsonObject)?.let { crData.add(CRDatum(it, dateUtil)) }
        }
        return crData
    }

    fun toString(indent: Int): String {
        val json = buildJsonObject {
            put("CRData", buildJsonArray { crData.forEach { add(it.toJSON()) } })
            put("CSFGlucoseData", buildJsonArray { csfGlucoseData.forEach { add(it.toJSON(true)) } })
            put("ISFGlucoseData", buildJsonArray { isfGlucoseData.forEach { add(it.toJSON(false)) } })
            put("basalGlucoseData", buildJsonArray { basalGlucoseData.forEach { add(it.toJSON(false)) } })
            if (diaDeviations.isNotEmpty() || peakDeviations.isNotEmpty()) {
                put("diaDeviations", buildJsonArray { diaDeviations.forEach { add(it.toJSON()) } })
                put("peakDeviations", buildJsonArray { peakDeviations.forEach { add(it.toJSON()) } })
            }
        }
        return if (indent != 0) Json { prettyPrint = true; prettyPrintIndent = " ".repeat(indent) }.encodeToString(JsonObject.serializer(), json)
        else json.toString()
    }
}
