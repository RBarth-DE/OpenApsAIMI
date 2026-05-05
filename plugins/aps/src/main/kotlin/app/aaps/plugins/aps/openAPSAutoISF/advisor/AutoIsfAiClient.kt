package app.aaps.plugins.aps.openAPSAutoISF.advisor

import android.content.Context
import app.aaps.core.keys.DoubleKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Self-contained LLM client for the AutoISF Advisor.
 * Supports Claude, Gemini, DeepSeek, and OpenAI.
 * No AIMI dependency — reuses only the shared StringKey API-key preference values.
 */
class AutoIsfAiClient {

    enum class Provider { OPENAI, GEMINI, DEEPSEEK, CLAUDE }

    companion object {
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val OPENAI_MODEL = "gpt-4o-mini"
        private const val DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val DEEPSEEK_MODEL = "deepseek-chat"
        private const val CLAUDE_URL = "https://api.anthropic.com/v1/messages"
        private const val CLAUDE_MODEL = "claude-haiku-4-5"
        private const val GEMINI_MODEL = "gemini-1.5-flash-latest"
        private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    suspend fun fetchAdvice(
        apiKey: String,
        provider: Provider,
        report: AutoIsfReport,
        prefs: AutoIsfPrefsSnapshot,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "API key missing. Please configure your ${provider.name} key in AutoISF settings."
        try {
            val prompt = buildPrompt(report, prefs, context)
            when (provider) {
                Provider.CLAUDE -> callClaude(apiKey, prompt)
                Provider.GEMINI -> callGemini(apiKey, prompt)
                Provider.DEEPSEEK -> callOpenAiCompatible(apiKey, prompt, DEEPSEEK_URL, DEEPSEEK_MODEL)
                Provider.OPENAI -> callOpenAiCompatible(apiKey, prompt, OPENAI_URL, OPENAI_MODEL)
            }
        } catch (e: Exception) {
            "Connection error (${provider.name}): ${e.localizedMessage}"
        }
    }

    private fun buildPrompt(report: AutoIsfReport, prefs: AutoIsfPrefsSnapshot, context: Context): String {
        val m = report.metrics
        val lang = Locale.getDefault().displayLanguage
        val sb = StringBuilder()

        sb.appendLine("You are an expert diabetes technologist specializing in AutoISF — a dynamic insulin sensitivity algorithm for closed-loop APS.")
        sb.appendLine("Analyze the patient's glycemic data and current AutoISF configuration, identify patterns, and suggest specific tuning directions.")
        sb.appendLine("Tone: Professional, precise, safety-first. Respond in '$lang'.")
        sb.appendLine()

        sb.appendLine("--- GLYCEMIC METRICS (${m.periodLabel}) ---")
        sb.appendLine("Score: ${"%.1f".format(Locale.US, report.overallScore)}/10")
        sb.appendLine("TIR 70-180: ${pct(m.tir70_180)}%  |  TIR 70-140: ${pct(m.tir70_140)}%")
        sb.appendLine("Hypo (<70): ${pct(m.timeBelow70)}%  |  Severe (<54): ${pct(m.timeBelow54)}%")
        sb.appendLine("Hyper (>180): ${pct(m.timeAbove180)}%  |  Very High (>250): ${pct(m.timeAbove250)}%")
        sb.appendLine("Mean BG: ${m.meanBg.toInt()} mg/dL  |  GMI: ${"%.1f".format(Locale.US, m.gmi)}%")
        sb.appendLine("Avg TDD: ${"%.1f".format(Locale.US, m.tdd)} U  |  Basal fraction: ${pct(m.basalPercent)}%")
        sb.appendLine()
        val test = DoubleKey.ApsAutoIsfLowBgWeight.min
        sb.appendLine("--- AUTOISF CONFIGURATION ---")
        sb.appendLine("Weights enabled: ${prefs.useAutoIsfWeights}")
        sb.appendLine("AutoISF bounds: min=${prefs.autoIsfMin} (${DoubleKey.ApsAutoIsfMin.min}/${DoubleKey.ApsAutoIsfMin.max}) max=${prefs.autoIsfMax} ((${DoubleKey.ApsAutoIsfMax.min}/${DoubleKey.ApsAutoIsfMax.max}))  autosensMax=${prefs.autosensMax} (defines how strong ISF can become)")
        sb.appendLine("ISF weights:")
        sb.appendLine("  Profile ISF=${prefs.profileISF}            (min: 2 / max: 1200 / default: 40)                                                                            (Profile ISF scales with its weights below)")
        sb.appendLine("  highBgWeight=${prefs.highBgWeight}         (min: ${DoubleKey.ApsAutoIsfHighBgWeight.min} / max: ${DoubleKey.ApsAutoIsfHighBgWeight.max})   (boosts ISF at high BG)")
        sb.appendLine("  lowBgWeight=${prefs.lowBgWeight}           (min: ${DoubleKey.ApsAutoIsfLowBgWeight.min} / max: ${DoubleKey.ApsAutoIsfLowBgWeight.max})     (lowers ISF when BG low → more insulin)")
        sb.appendLine("  bgAccelWeight=${prefs.bgAccelWeight}       (min: ${DoubleKey.ApsAutoIsfBgAccelWeight.min} / max: ${DoubleKey.ApsAutoIsfBgAccelWeight.max}) (responds to rising BG rate)")
        sb.appendLine("  bgBrakeWeight=${prefs.bgBrakeWeight}       (min: ${DoubleKey.ApsAutoIsfBgBrakeWeight.min} / max: ${DoubleKey.ApsAutoIsfBgBrakeWeight.max}) (reduces ISF when BG falling fast)")
        sb.appendLine("  ppWeight=${prefs.ppWeight}                 (min: ${DoubleKey.ApsAutoIsfPpWeight.min} / max: ${DoubleKey.ApsAutoIsfPpWeight.max})           (post-prandial ISF boost)")
        sb.appendLine("  duraWeight=${prefs.duraWeight}             (min: ${DoubleKey.ApsAutoIsfDuraWeight.min} / max: ${DoubleKey.ApsAutoIsfDuraWeight.max})       (extra ISF for prolonged highs)")
        sb.appendLine("SMB delivery ratio=${prefs.smbDeliveryRatio} (min: ${prefs.smbDeliveryRatioMin} / max: ${prefs.smbDeliveryRatioMax})                         (The linearly increasing SMB delivery ratio is mapped to the glucose range)")
        sb.appendLine("IOB threshold: ${prefs.iobThPercent}%        (min: ${DoubleKey.ApsAutoIsfHighBgWeight.min} / max: ${DoubleKey.ApsAutoIsfHighBgWeight.max})   (Raise IOB threshold to allow SMBs in high BG situations)")
        sb.appendLine()

        sb.appendLine("--- LOCAL HEURISTIC FLAGS ---")
        val actionRecs = report.recommendations.filter { it.action != null }
        if (actionRecs.isEmpty()) {
            sb.appendLine("No significant issues flagged locally.")
        } else {
            actionRecs.forEach { rec ->
                val act = rec.action as AutoIsfAction.PreferenceUpdate
                sb.appendLine("- [${rec.priority}] ${rec.title}: ${act.key.key} ${fmtValue(act.currentValue)} → ${fmtValue(act.newValue)}")
            }
        }
        sb.appendLine()

        sb.appendLine("--- TASK ---")
        sb.appendLine("Respond in '$lang'. Structure your answer as:")
        sb.appendLine("1. 🔍 Diagnostics: Key glycemic patterns observed.")
        sb.appendLine("2. ⚙️ Weight analysis: Which AutoISF weights are likely contributing to the patterns and why.")
        sb.appendLine("3. 🛠️ Tuning directions (2-4 steps): Specific, cautious parameter adjustments. Hypo safety first.")
        sb.appendLine("4. ⚠️ Safety note: Brief reminder to verify with a clinician before applying changes.")
        sb.appendLine("Constraint: Under 250 words. Only reference data above — do not invent metrics.")

        return sb.toString()
    }

    private fun callClaude(apiKey: String, prompt: String): String {
        val body = JSONObject().apply {
            put("model", CLAUDE_MODEL)
            put("max_tokens", 4096)
            put("temperature", 0.7)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val conn = URL(CLAUDE_URL).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 60000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        return if (conn.responseCode == 200) {
            val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            JSONObject(text).getJSONArray("content").getJSONObject(0).getString("text").trim()
        } else {
            val err = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            "Claude error (${conn.responseCode}): ${err.take(300)}"
        }
    }

    private fun callGemini(apiKey: String, prompt: String): String {
        val urlStr = "$GEMINI_BASE/$GEMINI_MODEL:generateContent?key=$apiKey"
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                put("role", "user")
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 4096)
            })
        }
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 60000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        return if (conn.responseCode == 200) {
            val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
        } else {
            val err = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            "Gemini error (${conn.responseCode}): ${err.take(300)}"
        }
    }

    private fun callOpenAiCompatible(apiKey: String, prompt: String, url: String, model: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        return if (conn.responseCode == 200) {
            val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            JSONObject(text).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        } else {
            val err = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            "Error ($model, ${conn.responseCode}): ${err.take(300)}"
        }
    }

    private fun pct(v: Double) = (v * 100).toInt()
    private fun fmtValue(v: Any) = if (v is Double) "%.2f".format(Locale.US, v) else v.toString()
}
