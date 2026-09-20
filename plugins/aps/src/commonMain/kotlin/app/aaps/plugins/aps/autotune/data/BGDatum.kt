package app.aaps.plugins.aps.autotune.data

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.data.time.systemUtcOffsetAt
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientInt
import app.aaps.core.utils.lenientLong
import app.aaps.core.utils.lenientStringOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Created by Rumen Georgiev on 2/24/2018.
 */
class BGDatum {

    //Added by Rumen for autotune
    var id: Long = 0
    var date = 0L
    var value = 0.0
    var direction: TrendArrow? = null
    var deviation = 0.0
    var bgi = 0.0
    var mealAbsorption = ""
    var mealCarbs = 0
    var uamAbsorption = ""
    var avgDelta = 0.0
    var bgReading: GV? = null
        private set
    var dateUtil: DateUtil

    constructor(dateUtil: DateUtil) {
        this.dateUtil = dateUtil
    }

    /** A key that is absent leaves the field at its current value, as the old `has()` guard did. */
    constructor(json: JsonObject, dateUtil: DateUtil) {
        this.dateUtil = dateUtil
        //if (json.has("_id")) id = json.getLong("_id")
        date = json.lenientLong("date", date)
        value = json.lenientDouble("sgv", value)
        json.lenientStringOrNull("direction")?.let { direction = TrendArrow.fromString(it) }
        deviation = json.lenientDouble("deviation", deviation)
        bgi = json.lenientDouble("BGI", bgi)
        avgDelta = json.lenientDouble("avgDelta", avgDelta)
        mealAbsorption = json.lenientStringOrNull("mealAbsorption") ?: mealAbsorption
        mealCarbs = json.lenientInt("mealCarbs", mealCarbs)
    }

    constructor(glucoseValue: GV, dateUtil: DateUtil) {
        this.dateUtil = dateUtil
        date = glucoseValue.timestamp
        value = glucoseValue.value
        direction = glucoseValue.trendArrow
        id = glucoseValue.id
        this.bgReading = glucoseValue
    }

    fun toJSON(mealData: Boolean): JsonObject = buildJsonObject {
        // `org.json` wrote the enum through `toString()`, which is its name - written as `.name` here
        // so the exported file keeps the exact bytes it has always had.
        val utcOffset = T.msecs(systemUtcOffsetAt(dateUtil.now())).hours()
        put("_id", id)
        put("date", date)
        put("dateString", dateUtil.toISOAsUTC(date))
        put("sgv", value)
        // Written only when there is one: `org.json` drops a key whose value is null, so an absent
        // direction produced no `direction` key at all. `.name` keeps the exact text it wrote.
        direction?.let { put("direction", it.name) }
        put("type", "sgv")
        put("sysTime", dateUtil.toISOAsUTC(date))
        put("utcOffset", utcOffset)
        put("glucose", value)
        put("avgDelta", avgDelta)
        put("BGI", bgi)
        put("deviation", deviation)
        if (mealData) {
            put("mealAbsorption", mealAbsorption)
            put("mealCarbs", mealCarbs)
        }
    }

    fun equals(obj: BGDatum): Boolean {
        var isEqual = true
        if (date / 1000 != obj.date / 1000) isEqual = false
        if (deviation != obj.deviation) isEqual = false
        if (avgDelta != obj.avgDelta) isEqual = false
        if (bgi != obj.bgi) isEqual = false
        if (mealAbsorption != obj.mealAbsorption) isEqual = false
        if (mealCarbs != obj.mealCarbs) isEqual = false
        return isEqual
    }
}
