package app.aaps.database.persistence.converters

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatusAIMI
import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.core.interfaces.aps.GlucoseStatusSMB
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.OapsProfileAimi
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.aps.RT
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Provider

private const val TAG = "APSResultExtension"

/**
 * Sanitize JSON strings to remove problematic Unicode characters
 * that can cause deserialization crashes (especially arrows and math symbols).
 *
 * This is critical for backward compatibility with old database records
 * that may contain Unicode characters in consoleLog arrays.
 */
private fun sanitizeJson(json: String): String {
    return json
        .replace("→", "->")
        .replace("←", "<-")
        .replace("↑", "^")
        .replace("↓", "v")
        .replace("🠢", "->")
        .replace("🠠", "->")
        .replace("🠡", "->")
        .replace("🠣", "->")
        .replace("×", "x")
        .replace("÷", "/")
        .replace("±", "+/-")
        .replace(Regex("[\\ud800-\\udbff][\\udc00-\\udfff]"), "")
}

/**
 * Ensure the resultJson contains the correct algorithm value.
 * Old records may have algorithm=UNKNOWN (explicit) or no algorithm field at all
 * (kotlinx.serialization then uses the default UNKNOWN). Either way, force the
 * canonical value so DetermineBasalResult.with() does not throw.
 */
private fun ensureAlgorithmInJson(json: String, canonicalAlgorithm: String): String {
    return try {
        val jsonObj: JsonObject = Json.parseToJsonElement(json).jsonObject
        val current = jsonObj["algorithm"]?.toString()?.trim('"')
        //Log.d(TAG, "ensureAlgorithm: current=$current canonical=$canonicalAlgorithm snippet=${json.take(80)}")
        if (current == null || current == "UNKNOWN" || current.isBlank()) {
            // Rebuild the JSON object with the correct algorithm field
            val fixed = buildJsonObject {
                put("algorithm", canonicalAlgorithm)
                for ((k, v) in jsonObj) if (k != "algorithm") put(k, v)
            }
            Json.encodeToString(JsonObject.serializer(), fixed)
        } else {
            json
        }
    } catch (e: Exception) {
        //Log.e(TAG, "ensureAlgorithmInJson parse failed, falling back to regex: $e")
        // Fallback: regex-based replace (covers whitespace variants)
        Regex(""""algorithm"\s*:\s*"UNKNOWN"""", RegexOption.IGNORE_CASE)
            .replace(json, """"algorithm":"$canonicalAlgorithm"""")
    }
}

fun app.aaps.database.entities.APSResult.fromDb(apsResultProvider: Provider<APSResult>): APSResult =
    when (algorithm) {
        app.aaps.database.entities.APSResult.Algorithm.AMA,
        app.aaps.database.entities.APSResult.Algorithm.SMB -> {
            val json = ensureAlgorithmInJson(this.resultJson, algorithm.name)
            apsResultProvider.get().with(Json.decodeFromString(json)).also { result ->
                result.date = this.timestamp
                result.glucoseStatus = try {
                    this.glucoseStatusJson?.let { Json.decodeFromString<GlucoseStatusSMB>(it) }
                } catch (_: Exception) { null }
                result.currentTemp = this.currentTempJson?.let { Json.decodeFromString(CurrentTemp.serializer(), it) }
                result.iobData = this.iobDataJson?.let { Json.decodeFromString(ArraySerializer(IobTotal.serializer()), it) }
                result.oapsProfile = this.profileJson?.let { Json.decodeFromString(OapsProfile.serializer(), it) }
                result.mealData = this.mealDataJson?.let { Json.decodeFromString(MealData.serializer(), it) }
                result.autosensResult = this.autosensDataJson?.let { Json.decodeFromString(AutosensResult.serializer(), it) }
            }
        }

        app.aaps.database.entities.APSResult.Algorithm.AUTO_ISF -> {
            val json = ensureAlgorithmInJson(this.resultJson, "AUTO_ISF")
            apsResultProvider.get().with(Json.decodeFromString(json)).also { result ->
                result.date = this.timestamp
                result.glucoseStatus = try {
                    this.glucoseStatusJson?.let { Json.decodeFromString<GlucoseStatusAutoIsf>(it) }
                } catch (_: Exception) { null }
                result.currentTemp = this.currentTempJson?.let { Json.decodeFromString(CurrentTemp.serializer(), it) }
                result.iobData = this.iobDataJson?.let { Json.decodeFromString(ArraySerializer(IobTotal.serializer()), it) }
                result.oapsProfileAutoIsf = this.profileJson?.let { Json.decodeFromString(OapsProfileAutoIsf.serializer(), it) }
                result.mealData = this.mealDataJson?.let { Json.decodeFromString(MealData.serializer(), it) }
                result.autosensResult = this.autosensDataJson?.let { Json.decodeFromString(AutosensResult.serializer(), it) }
            }
        }

        app.aaps.database.entities.APSResult.Algorithm.AIMI -> {
            val json = ensureAlgorithmInJson(sanitizeJson(this.resultJson), "AIMI")
            apsResultProvider.get().with(Json.decodeFromString(json)).also { result ->
                result.date = this.timestamp
                result.glucoseStatus = try {
                    this.glucoseStatusJson?.let { Json.decodeFromString<GlucoseStatusSMB>(it) }
                } catch (_: Exception) { null }
                result.currentTemp = this.currentTempJson?.let { Json.decodeFromString(CurrentTemp.serializer(), it) }
                result.iobData = this.iobDataJson?.let { Json.decodeFromString(ArraySerializer(IobTotal.serializer()), it) }
                result.oapsProfileAimi = this.profileJson?.let { Json.decodeFromString(OapsProfileAimi.serializer(), it) }
                result.mealData = this.mealDataJson?.let { Json.decodeFromString(MealData.serializer(), it) }
                result.autosensResult = this.autosensDataJson?.let { Json.decodeFromString(AutosensResult.serializer(), it) }
            }
        }

        else -> apsResultProvider.get()  // DB record has algorithm=UNKNOWN at entity level – return empty result
    }

@OptIn(ExperimentalSerializationApi::class)
fun APSResult.toDb(): app.aaps.database.entities.APSResult =
    when (algorithm) {
        APSResult.Algorithm.AMA,
        APSResult.Algorithm.SMB -> {
            app.aaps.database.entities.APSResult(
                timestamp = this.date,
                algorithm = this.algorithm.toDb(),
                glucoseStatusJson = this.glucoseStatus?.let { Json.encodeToString(GlucoseStatusSMB.serializer(), it as GlucoseStatusSMB) },
                currentTempJson = this.currentTemp?.let { Json.encodeToString(CurrentTemp.serializer(), it) },
                iobDataJson = this.iobData?.let { Json.encodeToString(ArraySerializer(IobTotal.serializer()), it) },
                profileJson = this.oapsProfile?.let { Json.encodeToString(OapsProfile.serializer(), it) },
                mealDataJson = this.mealData?.let { Json.encodeToString(MealData.serializer(), it) },
                autosensDataJson = this.autosensResult?.let { Json.encodeToString(AutosensResult.serializer(), it) },
                resultJson = Json.encodeToString(RT.serializer(), this.rawData() as RT)
            )
        }

        APSResult.Algorithm.AUTO_ISF -> {
            app.aaps.database.entities.APSResult(
                timestamp = this.date,
                algorithm = this.algorithm.toDb(),
                glucoseStatusJson = this.glucoseStatus?.let { Json.encodeToString(GlucoseStatusAutoIsf.serializer(), it as GlucoseStatusAutoIsf) },
                currentTempJson = this.currentTemp?.let { Json.encodeToString(CurrentTemp.serializer(), it) },
                iobDataJson = this.iobData?.let { Json.encodeToString(ArraySerializer(IobTotal.serializer()), it) },
                profileJson = this.oapsProfileAutoIsf?.let { Json.encodeToString(OapsProfileAutoIsf.serializer(), it) },
                mealDataJson = this.mealData?.let { Json.encodeToString(MealData.serializer(), it) },
                autosensDataJson = this.autosensResult?.let { Json.encodeToString(AutosensResult.serializer(), it) },
                resultJson = Json.encodeToString(RT.serializer(), this.rawData() as RT)
            )
        }

        APSResult.Algorithm.AIMI -> {
            app.aaps.database.entities.APSResult(
                timestamp = this.date,
                algorithm = this.algorithm.toDb(),
                glucoseStatusJson = this.glucoseStatus?.let { Json.encodeToString(GlucoseStatusAIMI.serializer(), it as GlucoseStatusAIMI) },
                currentTempJson = this.currentTemp?.let { Json.encodeToString(CurrentTemp.serializer(), it) },
                iobDataJson = this.iobData?.let { Json.encodeToString(ArraySerializer(IobTotal.serializer()), it) },
                profileJson = this.oapsProfileAimi?.let { Json.encodeToString(OapsProfileAimi.serializer(), it) },
                mealDataJson = this.mealData?.let { Json.encodeToString(MealData.serializer(), it) },
                autosensDataJson = this.autosensResult?.let { Json.encodeToString(AutosensResult.serializer(), it) },
                resultJson = Json.encodeToString(RT.serializer(), this.rawData() as RT)
            )
        }

        else -> error("Unsupported")
    }

fun app.aaps.database.entities.APSResult.Algorithm.fromDb(): APSResult.Algorithm =
    when (this) {
        app.aaps.database.entities.APSResult.Algorithm.AMA      -> APSResult.Algorithm.AMA
        app.aaps.database.entities.APSResult.Algorithm.SMB      -> APSResult.Algorithm.SMB
        app.aaps.database.entities.APSResult.Algorithm.AUTO_ISF -> APSResult.Algorithm.AUTO_ISF
        app.aaps.database.entities.APSResult.Algorithm.AIMI     -> APSResult.Algorithm.AIMI
        else                                                    -> APSResult.Algorithm.UNKNOWN
    }

fun APSResult.Algorithm.toDb(): app.aaps.database.entities.APSResult.Algorithm =
    when (this) {
        APSResult.Algorithm.AMA      -> app.aaps.database.entities.APSResult.Algorithm.AMA
        APSResult.Algorithm.SMB      -> app.aaps.database.entities.APSResult.Algorithm.SMB
        APSResult.Algorithm.AUTO_ISF -> app.aaps.database.entities.APSResult.Algorithm.AUTO_ISF
        APSResult.Algorithm.AIMI     -> app.aaps.database.entities.APSResult.Algorithm.AIMI
        else                         -> error("Unsupported")
    }
