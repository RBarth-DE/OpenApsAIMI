package app.aaps.plugins.aps.openAPSAutoISF.advisor

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.plugins.aps.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

class AutoIsfProfileAdvisorActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    @Inject lateinit var aapsLogger: AAPSLogger

    private lateinit var advisorService: AutoIsfAdvisorService
    private lateinit var aiClient: AutoIsfAiClient
    private lateinit var cooldownPrefs: SharedPreferences

    private var currentReport: AutoIsfReport? = null
    private var currentPrefs: AutoIsfPrefsSnapshot? = null

    private val bgColor = Color.parseColor("#10141C")
    private val cardColor = Color.parseColor("#1E293B")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        advisorService = AutoIsfAdvisorService(
            profileFunction = profileFunction,
            persistenceLayer = persistenceLayer,
            preferences = preferences,
            tddCalculator = tddCalculator,
            tirCalculator = tirCalculator
        )
        aiClient = AutoIsfAiClient()
        cooldownPrefs = getSharedPreferences("autoisf_advisor_cooldown", MODE_PRIVATE)
        title = rh.gs(R.string.autoisf_advisor_title)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(bgColor)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val scroll = ScrollView(this).apply {
            addView(root)
            setBackgroundColor(bgColor)
        }
        setContentView(scroll)

        val loadingText = TextView(this).apply {
            text = rh.gs(R.string.autoisf_adv_loading)
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 64, 0, 0)
        }
        root.addView(loadingText)

        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = advisorService.collectPrefs()
            val report = runCatching { advisorService.generateReport(periodDays = 10) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (isFinishing) return@withContext
                root.removeView(loadingText)
                if (report == null) {
                    root.addView(errorText("Failed to generate report."))
                    return@withContext
                }
                currentReport = report
                currentPrefs = prefs

                root.addView(buildHeader(report))
                root.addView(buildMetricsGrid(report.metrics))

                val weightRecs = report.recommendations.filter {
                    it.action is AutoIsfAction.PreferenceUpdate &&
                        isWeightKey((it.action as AutoIsfAction.PreferenceUpdate).key)
                }
                val smbRecs = report.recommendations.filter {
                    it.action is AutoIsfAction.PreferenceUpdate &&
                        !isWeightKey((it.action as AutoIsfAction.PreferenceUpdate).key)
                }
                val infoRecs = report.recommendations.filter { it.action == null }

                if (weightRecs.isNotEmpty()) {
                    root.addView(sectionHeader(rh.gs(R.string.autoisf_adv_section_isf_weights)))
                    weightRecs.forEach { root.addView(buildRecCard(it)) }
                }
                if (smbRecs.isNotEmpty()) {
                    root.addView(sectionHeader(rh.gs(R.string.autoisf_adv_section_smb)))
                    smbRecs.forEach { root.addView(buildRecCard(it)) }
                }
                if (infoRecs.isNotEmpty()) {
                    root.addView(sectionHeader(rh.gs(R.string.autoisf_adv_section_observations)))
                    infoRecs.forEach { root.addView(buildRecCard(it)) }
                }

                root.addView(buildAiSection(root))
            }
        }
    }

    // ── AI section ────────────────────────────────────────────────────────────

    private fun buildAiSection(root: LinearLayout): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        container.addView(sectionHeader(rh.gs(R.string.autoisf_adv_section_ai)))

        val providerStr = runCatching { preferences.get(StringKey.AimiAdvisorProvider) }.getOrDefault("OPENAI")
        val provider = runCatching { AutoIsfAiClient.Provider.valueOf(providerStr.uppercase()) }
            .getOrDefault(AutoIsfAiClient.Provider.OPENAI)

        val apiKey = runCatching {
            when (provider) {
                AutoIsfAiClient.Provider.CLAUDE -> preferences.get(StringKey.AimiAdvisorClaudeKey)
                AutoIsfAiClient.Provider.GEMINI -> preferences.get(StringKey.AimiAdvisorGeminiKey)
                AutoIsfAiClient.Provider.DEEPSEEK -> preferences.get(StringKey.AimiAdvisorDeepSeekKey)
                AutoIsfAiClient.Provider.OPENAI -> preferences.get(StringKey.AimiAdvisorOpenAIKey)
            }
        }.getOrDefault("")

        val responseCard = CardView(this).apply {
            radius = 12f
            setCardBackgroundColor(cardColor)
            cardElevation = 4f
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }
        }
        val responseText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(28, 24, 28, 24)
        }
        responseCard.addView(responseText)

        val askBtn = Button(this).apply {
            text = rh.gs(R.string.autoisf_adv_ask_ai_btn, provider.name)
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6366F1"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 16) }
        }

        if (apiKey.isBlank()) {
            askBtn.isEnabled = false
            askBtn.alpha = 0.4f
            askBtn.text = rh.gs(R.string.autoisf_adv_ask_ai_no_key, provider.name)
        }

        askBtn.setOnClickListener {
            val report = currentReport ?: return@setOnClickListener
            val prefs = currentPrefs ?: return@setOnClickListener
            askBtn.isEnabled = false
            askBtn.text = rh.gs(R.string.autoisf_adv_ai_loading)
            responseCard.visibility = View.GONE

            lifecycleScope.launch(Dispatchers.IO) {
                val result = aiClient.fetchAdvice(apiKey, provider, report, prefs, applicationContext)
                withContext(Dispatchers.Main) {
                    if (isFinishing) return@withContext
                    responseText.text = result
                    responseCard.visibility = View.VISIBLE
                    askBtn.isEnabled = true
                    askBtn.text = rh.gs(R.string.autoisf_adv_ask_ai_btn, provider.name)
                }
            }
        }

        container.addView(askBtn)
        container.addView(responseCard)
        return container
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun buildHeader(report: AutoIsfReport): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }

        val titleView = TextView(this).apply {
            text = rh.gs(R.string.autoisf_advisor_title)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val scoreColor = when (report.overallSeverity) {
            AutoIsfSeverity.Good -> Color.parseColor("#22C55E")
            AutoIsfSeverity.Warning -> Color.parseColor("#F59E0B")
            AutoIsfSeverity.Critical -> Color.parseColor("#EF4444")
        }

        val pill = CardView(this).apply {
            radius = 48f
            setCardBackgroundColor(scoreColor)
            cardElevation = 0f
            addView(TextView(this@AutoIsfProfileAdvisorActivity).apply {
                text = String.format(Locale.US, "%.1f/10", report.overallScore)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(24, 8, 24, 8)
            })
        }

        row.addView(titleView)
        row.addView(pill)
        return row
    }

    // ── Metrics grid ──────────────────────────────────────────────────────────

    private fun buildMetricsGrid(m: AutoIsfMetrics): View {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }

        row1.addView(metricTile("TIR 70-180", "${pct(m.tir70_180)}%", tirColor(m.tir70_180)))
        row1.addView(metricTile("Below 70", "${pct(m.timeBelow70)}%", hypoColor(m.timeBelow70)))
        row2.addView(metricTile("Above 180", "${pct(m.timeAbove180)}%", hyperColor(m.timeAbove180)))
        row2.addView(metricTile("Mean BG", "${m.meanBg.roundToInt()} mg/dL", Color.WHITE))

        grid.addView(row1)
        grid.addView(row2)

        m.todayTir?.let {
            val todayRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 0)
            }
            todayRow.addView(metricTile("Today TIR", "${pct(it)}%", tirColor(it)))
            m.todayTdd?.let { tdd ->
                todayRow.addView(metricTile("Today TDD", String.format(Locale.US, "%.1f U", tdd), Color.WHITE))
            }
            grid.addView(todayRow)
        }
        return grid
    }

    private fun metricTile(label: String, value: String, valueColor: Int): View {
        val card = CardView(this).apply {
            radius = 12f
            setCardBackgroundColor(cardColor)
            cardElevation = 4f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 0, 4, 0)
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 20)
        }
        col.addView(TextView(this).apply {
            text = value
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(valueColor)
            gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        })
        card.addView(col)
        return card
    }

    // ── Section header ────────────────────────────────────────────────────────

    private fun sectionHeader(text: String): View =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            setPadding(4, 24, 4, 8)
            letterSpacing = 0.1f
        }

    // ── Recommendation card ───────────────────────────────────────────────────

    private fun buildRecCard(rec: AutoIsfRecommendation): View {
        val card = CardView(this).apply {
            radius = 12f
            setCardBackgroundColor(cardColor)
            cardElevation = 4f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 20)
        }

        val priorityColor = when (rec.priority) {
            AutoIsfPriority.Critical -> Color.parseColor("#EF4444")
            AutoIsfPriority.High -> Color.parseColor("#F97316")
            AutoIsfPriority.Medium -> Color.parseColor("#F59E0B")
            AutoIsfPriority.Low -> Color.parseColor("#22C55E")
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(View(this).apply {
            setBackgroundColor(priorityColor)
            layoutParams = LinearLayout.LayoutParams(6, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(0, 0, 16, 0)
            }
        })
        titleRow.addView(TextView(this).apply {
            text = rec.title
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        col.addView(titleRow)

        col.addView(TextView(this).apply {
            text = rec.description
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(22, 8, 0, 0)
        })

        val action = rec.action
        if (action is AutoIsfAction.PreferenceUpdate && !isCooledDown(action.key.key)) {
            col.addView(TextView(this).apply {
                text = "${action.key.key}: ${fmtValue(action.currentValue)} → ${fmtValue(action.newValue)}"
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(22, 8, 0, 0)
                setTypeface(null, Typeface.ITALIC)
            })
            col.addView(Button(this).apply {
                text = rh.gs(R.string.autoisf_adv_apply_btn)
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundColor(priorityColor)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(18, 12, 0, 0) }
                setOnClickListener { showApplyDialog(rec.title, action, card) }
            })
        }

        card.addView(col)
        return card
    }

    private fun showApplyDialog(title: String, action: AutoIsfAction.PreferenceUpdate, cardView: View) {
        val msg = "$title\n${action.key.key}: ${fmtValue(action.currentValue)} → ${fmtValue(action.newValue)}"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(rh.gs(R.string.autoisf_adv_apply_dialog_title))
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok) { _, _ -> applyAction(action, cardView) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyAction(action: AutoIsfAction.PreferenceUpdate, cardView: View) {
        try {
            val key = action.key
            val value = action.newValue
            var applied = false
            when {
                value is Double && key is DoublePreferenceKey -> { preferences.put(key, value); applied = true }
                value is Int && key is IntPreferenceKey -> { preferences.put(key, value); applied = true }
                value is Boolean && key is BooleanPreferenceKey -> { preferences.put(key, value); applied = true }
                value is String && key is StringPreferenceKey -> { preferences.put(key, value); applied = true }
            }
            if (applied) {
                markCooldown(key.key)
                Toast.makeText(this, rh.gs(R.string.autoisf_adv_success_msg, 1), Toast.LENGTH_SHORT).show()
                cardView.visibility = View.GONE
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // ── 48-hour cooldown ──────────────────────────────────────────────────────

    private fun isCooledDown(keyStr: String): Boolean {
        val applied = cooldownPrefs.getLong(keyStr, 0L)
        return applied > 0L && System.currentTimeMillis() - applied < 48 * 3600 * 1000L
    }

    private fun markCooldown(keyStr: String) {
        cooldownPrefs.edit().putLong(keyStr, System.currentTimeMillis()).apply()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isWeightKey(key: app.aaps.core.keys.interfaces.PreferenceKey): Boolean {
        val k = key.key
        return k.contains("autoisf", ignoreCase = true) &&
            (k.contains("weight", ignoreCase = true) ||
                k.contains("min", ignoreCase = true) ||
                k.contains("max", ignoreCase = true))
    }

    private fun errorText(msg: String) = TextView(this).apply {
        text = msg
        textSize = 14f
        setTextColor(Color.parseColor("#EF4444"))
        gravity = Gravity.CENTER
        setPadding(0, 32, 0, 0)
    }

    private fun fmtValue(v: Any): String = when (v) {
        is Double -> String.format(Locale.US, "%.2f", v)
        else -> v.toString()
    }

    private fun pct(v: Double) = (v * 100).roundToInt()

    private fun tirColor(v: Double) = when {
        v >= 0.70 -> Color.parseColor("#22C55E")
        v >= 0.50 -> Color.parseColor("#F59E0B")
        else -> Color.parseColor("#EF4444")
    }

    private fun hypoColor(v: Double) = when {
        v < 0.04 -> Color.parseColor("#22C55E")
        v < 0.07 -> Color.parseColor("#F59E0B")
        else -> Color.parseColor("#EF4444")
    }

    private fun hyperColor(v: Double) = when {
        v < 0.20 -> Color.parseColor("#22C55E")
        v < 0.35 -> Color.parseColor("#F59E0B")
        else -> Color.parseColor("#EF4444")
    }
}
