package app.aaps.plugins.aps.openAPSAIMI.advisor.pulse
import kotlinx.coroutines.runBlocking

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

/**
 * Full-screen detail view for the last AIMI loop rationale ("pulse").
 * Shows the complete unclipped reason text, key decision numbers, and timing.
 */
class AimiPulseDetailActivity : DaggerAppCompatActivity() {

    @Inject lateinit var loop: Loop
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make system bars transparent so the dark background extends fully
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        // Enable edge-to-edge so content draws behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Pad around system bars so content is fully readable
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val result = loop.lastRun?.request

        // ── Title + timestamp ──────────────────────────────────────────────
        val runTime = loop.lastRun?.lastAPSRun?.takeIf { it > 0 }
        val titleText = if (runTime != null) {
            "Last AIMI run · ${dateUtil.timeString(runTime)}"
        } else {
            "Last AIMI run"
        }
        container.addView(label(titleText, 18f, bold = true))
        container.addView(spacer(8))

        if (result == null) {
            container.addView(body("No APS result available. Run the loop or wait for the next glucose reading."))
            scroll.addView(container)
            setContentView(scroll)
            return
        }

        // ── Key numbers row ────────────────────────────────────────────────
        val smb = decimalFormatter.to2Decimal(result.smb)
        val basalDisplay = if (result.rate == -1.0) "—" else "${decimalFormatter.to2Decimal(result.rate)} U/h"
        val sens = decimalFormatter.to0Decimal((result.autosensResult?.ratio ?: 1.0) * 100.0)
        val targetBg = decimalFormatter.to0Decimal(result.targetBG)
        val iob = result.iob?.iob?.let { decimalFormatter.to2Decimal(it) } ?: "—"
        val cob = result.mealData?.mealCOB?.let { decimalFormatter.to0Decimal(it) } ?: "—"

        container.addView(keyValueRow("SMB", "$smb U"))
        container.addView(keyValueRow("Scheduled basal", basalDisplay))
        container.addView(keyValueRow("AutoSens", "$sens%"))
        container.addView(keyValueRow("Target BG", "$targetBg mg/dl"))
        container.addView(keyValueRow("IOB", "$iob U"))
        container.addView(keyValueRow("COB", "$cob g"))

        if ((result as? app.aaps.core.interfaces.aps.RT)?.isHypoRisk ?: false) {
            container.addView(spacer(8))
            container.addView(label("⚠ Hypo risk detected", 13f, color = Color.parseColor("#FF6D00")))
        }

        // ── Divider ────────────────────────────────────────────────────────
        container.addView(spacer(12))
        container.addView(divider())
        container.addView(spacer(12))

        // ── Full reason text ───────────────────────────────────────────────
        container.addView(label("Rationale", 14f, bold = true))
        container.addView(spacer(6))

        val rawReason = result.reason
        val plainReason = rawReason
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        container.addView(body(plainReason.ifBlank { "No reason text available." }))

        // ── Script debug lines (if any) ────────────────────────────────────
        val debugLines = result.scriptDebug
        if (!debugLines.isNullOrEmpty()) {
            container.addView(spacer(12))
            container.addView(divider())
            container.addView(spacer(12))
            container.addView(label("Debug", 14f, bold = true))
            container.addView(spacer(6))
            container.addView(body(debugLines.joinToString("\n")))
        }

        scroll.addView(container)
        setContentView(scroll)
    }

    // ── View helpers ───────────────────────────────────────────────────────

    private fun label(text: String, sizeSp: Float, bold: Boolean = false, color: Int = Color.WHITE) =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#CCCCCC"))
        lineHeight = (textSize * 1.5).toInt()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun keyValueRow(key: String, value: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(4) }
        }
        row.addView(TextView(this).apply {
            text = key
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        return row
    }

    private fun divider() = android.view.View(this).apply {
        setBackgroundColor(Color.parseColor("#33FFFFFF"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun spacer(heightDp: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
