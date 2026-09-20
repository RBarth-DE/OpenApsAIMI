package app.aaps.plugins.aps.autotune

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

/**
 * Bridge between the two JSON libraries, for the autotune tests only.
 *
 * The autotune classes moved to shared code and now read and write kotlinx documents, while these
 * tests were written against the Android `org.json` library. A test that builds its input or reads
 * its result through the text crosses between the two, which is also what the app does with the
 * files it writes, so a value read this way is shown as the reader of the file will see it.
 */

/** The document in [text], as the shared code reads it. */
fun jsonObjectOf(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

/** The same document, with the Android getters these tests assert with. */
fun JsonObject.asOrgJson(): JSONObject = JSONObject(toString())
