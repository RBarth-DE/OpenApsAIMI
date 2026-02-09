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
import javax.inject.Provider


private val SafeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true // Often helpful when dealing with "messy" DB strings
    encodeDefaults = true
}

/**
 * Sanitize JSON strings to remove problematic Unicode characters
 * that can cause deserialization crashes (especially arrows and math symbols).
 * 
 * This is critical for backward compatibility with old database records
 * that may contain Unicode characters in consoleLog arrays.
 */
private fun sanitizeJson(json: String): String {
    return json
        // Replace all arrow characters with ASCII equivalents
        .replace("→", "->")      // U+2192 RIGHTWARDS ARROW
        .replace("←", "<-")      // U+2190 LEFTWARDS ARROW
        .replace("↑", "^")       // U+2191 UPWARDS ARROW
        .replace("↓", "v")       // U+2193 DOWNWARDS ARROW
        .replace("🠢", "->")      // U+1F822 NORTH EAST ARROW TO BAR (the culprit!)
        .replace("🠠", "->")      // U+1F820 LEFTWARDS TRIANGLE-HEADED ARROW
        .replace("🠡", "->")      // U+1F821 UPWARDS TRIANGLE-HEADED ARROW
        .replace("🠣", "->")      // U+1F823 DOWNWARDS TRIANGLE-HEADED ARROW
       // Replace math symbols
        .replace("×", "x")       // U+00D7 MULTIPLICATION SIGN
        .replace("÷", "/")       // U+00F7 DIVISION SIGN
        .replace("±", "+/-")     // U+00B1 PLUS-MINUS SIGN
        // Remove surrogate pairs (emojis, etc) which can break strict JSON parsers
        .replace(Regex("[\\ud800-\\udbff][\\udc00-\\udfff]"), "")
        // Note: We keep emojis like 🍱, ⚠️, ✅ as they are generally safe when at start of strings
}

fun app.aaps.database.entities.APSResult.fromDb(apsResultProvider: Provider<APSResult>): APSResult =
    when (algorithm) {
        app.aaps.database.entities.APSResult.Algorithm.AMA,
        app.aaps.database.entities.APSResult.Algorithm.SMB -> {
            apsResultProvider.get().with(SafeJson.decodeFromString(this.resultJson)).also { result ->
                result.date = this.timestamp
                result.glucoseStatus = try {
                    // Si AIMI a son propre GlucoseStatus, remplace GlucoseStatusSMB ci-dessous
                    this.glucoseStatusJson?.let { SafeJson.decodeFromString<GlucoseStatusSMB>(it) }
                } catch (_: Exception) {
                    null
                }
                result.currentTemp = this.currentTempJson?.let { SafeJson.decodeFromString(CurrentTemp.serializer(), it) }
                result.iobData = this.iobDataJson?.let { SafeJson.decodeFromString(ArraySerializer(IobTotal.serializer()), it) }
                result.oapsProfile = this.profileJson?.let { SafeJson.decodeFromString(OapsProfile.serializer(), it) }
                result.mealData = this.mealDataJson?.let { SafeJson.decodeFromString(MealData.serializer(), it) }
                result.autosensResult = this.autosensDataJson?.let { SafeJson.decodeFromString(AutosensResult.serializer(), it) }
            }
        }

        app.aaps.database.entities.APSResult.Algorithm.AUTO_ISF -> {
            apsResultProvider.get().with(SafeJson.decodeFromString(this.resultJson)).also { result ->
                result.date = this.timestamp
                result.glucoseStatus = try {
                    this.glucoseStatusJson?.let { SafeJson.decodeFromString<GlucoseStatusAutoIsf>(it) }
                } catch (_: Exception) {
                    null
                }
                result.currentTemp = this.currentTempJson?.let { SafeJson.decodeFromString(CurrentTemp.serializer(), it) }
                result.iobData = this.iobDataJson?.let { SafeJson.decodeFromString(ArraySerializer(IobTotal.serializer()), it) }
                result.oapsProfileAutoIsf = this.profileJson?.let { SafeJson.decodeFromString(OapsProfileAutoIsf.serializer(), it) }
                result.mealData = this.mealDataJson?.let { SafeJson.decodeFromString(MealData.serializer(), it) }
                result.autosensResult = this.autosensDataJson?.let { SafeJson.decodeFromString(AutosensResult.serializer(), it) }
            }
        }

        app.aaps.database.entities.APSResult.Algorithm.AIMI -> {
            apsResultProvider.get().with(SafeJson.decodeFromString(sanitizeJson(this.resultJson))).also { result ->
                result.date = this.timestamp
                result.glucoseStatus = try {
                    // Si AIMI a un GlucoseStatus spécifique, remplace par Json.decodeFromString<GlucoseStatusAimi>(it)
                    this.glucoseStatusJson?.let { SafeJson.decodeFromString<GlucoseStatusSMB>(it) }
                } catch (_: Exception) {
                    null
                }
                result.currentTemp = this.currentTempJson?.let { SafeJson.decodeFromString(CurrentTemp.serializer(), it) }
                result.iobData = this.iobDataJson?.let { SafeJson.decodeFromString(ArraySerializer(IobTotal.serializer()), it) }
                result.oapsProfileAimi = this.profileJson?.let { SafeJson.decodeFromString(OapsProfileAimi.serializer(), it) }
                result.mealData = this.mealDataJson?.let { SafeJson.decodeFromString(MealData.serializer(), it) }
                result.autosensResult = this.autosensDataJson?.let { SafeJson.decodeFromString(AutosensResult.serializer(), it) }
            }
        }

        else -> error("Unsupported")
    }

@OptIn(ExperimentalSerializationApi::class)
fun APSResult.toDb(): app.aaps.database.entities.APSResult =
    when (algorithm) {
        APSResult.Algorithm.AMA,
        APSResult.Algorithm.SMB -> {
            app.aaps.database.entities.APSResult(
                timestamp = this.date,
                algorithm = this.algorithm.toDb(),
                // Si AIMI a son propre GlucoseStatus, ceci ne sera pas utilisé pour AIMI
                glucoseStatusJson = this.glucoseStatus?.let { SafeJson.encodeToString(GlucoseStatusSMB.serializer(), it as GlucoseStatusSMB) },
                currentTempJson = this.currentTemp?.let { SafeJson.encodeToString(CurrentTemp.serializer(), it) },
                iobDataJson = this.iobData?.let { SafeJson.encodeToString(ArraySerializer(IobTotal.serializer()), it) },
                profileJson = this.oapsProfile?.let { SafeJson.encodeToString(OapsProfile.serializer(), it) },
                mealDataJson = this.mealData?.let { SafeJson.encodeToString(MealData.serializer(), it) },
                autosensDataJson = this.autosensResult?.let { SafeJson.encodeToString(AutosensResult.serializer(), it) },
                resultJson = SafeJson.encodeToString(RT.serializer(), this.rawData() as RT)
            )
        }

        APSResult.Algorithm.AUTO_ISF -> {
            app.aaps.database.entities.APSResult(
                timestamp = this.date,
                algorithm = this.algorithm.toDb(),
                glucoseStatusJson = this.glucoseStatus?.let { SafeJson.encodeToString(GlucoseStatusAutoIsf.serializer(), it as GlucoseStatusAutoIsf) },
                currentTempJson = this.currentTemp?.let { SafeJson.encodeToString(CurrentTemp.serializer(), it) },
                iobDataJson = this.iobData?.let { SafeJson.encodeToString(ArraySerializer(IobTotal.serializer()), it) },
                profileJson = this.oapsProfileAutoIsf?.let { SafeJson.encodeToString(OapsProfileAutoIsf.serializer(), it) },
                mealDataJson = this.mealData?.let { SafeJson.encodeToString(MealData.serializer(), it) },
                autosensDataJson = this.autosensResult?.let { SafeJson.encodeToString(AutosensResult.serializer(), it) },
                resultJson = SafeJson.encodeToString(RT.serializer(), this.rawData() as RT)
            )
        }

        APSResult.Algorithm.AIMI -> {
            app.aaps.database.entities.APSResult(
                timestamp = this.date,
                algorithm = this.algorithm.toDb(),
                // Remplacer GlucoseStatusSMB par GlucoseStatusAimi si tu en as un
                glucoseStatusJson = this.glucoseStatus?.let { SafeJson.encodeToString(GlucoseStatusAIMI.serializer(), it as GlucoseStatusAIMI) },
                currentTempJson = this.currentTemp?.let { SafeJson.encodeToString(CurrentTemp.serializer(), it) },
                iobDataJson = this.iobData?.let { SafeJson.encodeToString(ArraySerializer(IobTotal.serializer()), it) },
                profileJson = this.oapsProfileAimi?.let { SafeJson.encodeToString(OapsProfileAimi.serializer(), it) },
                mealDataJson = this.mealData?.let { SafeJson.encodeToString(MealData.serializer(), it) },
                autosensDataJson = this.autosensResult?.let { SafeJson.encodeToString(AutosensResult.serializer(), it) },
                resultJson = SafeJson.encodeToString(RT.serializer(), this.rawData() as RT)
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
        else                                                    -> error("Unsupported")
    }

fun APSResult.Algorithm.toDb(): app.aaps.database.entities.APSResult.Algorithm =
    when (this) {
        APSResult.Algorithm.AMA      -> app.aaps.database.entities.APSResult.Algorithm.AMA
        APSResult.Algorithm.SMB      -> app.aaps.database.entities.APSResult.Algorithm.SMB
        APSResult.Algorithm.AUTO_ISF -> app.aaps.database.entities.APSResult.Algorithm.AUTO_ISF
        APSResult.Algorithm.AIMI     -> app.aaps.database.entities.APSResult.Algorithm.AIMI
        else                         -> error("Unsupported")
    }
