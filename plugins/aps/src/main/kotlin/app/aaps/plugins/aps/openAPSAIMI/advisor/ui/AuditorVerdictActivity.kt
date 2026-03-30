package app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorVerdictCache
import app.aaps.plugins.aps.openAPSAIMI.model.VerdictType
import app.aaps.plugins.aps.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AuditorVerdictActivity
 *
 * Displays the latest AI Auditor verdict from AuditorVerdictCache.
 * Opened via Notification tap, Dashboard indicator, or Overview indicator.
 *
 * Entry points:
 * - AuditorNotificationManager.createOpenReportIntent()
 * - DashboardFragment auditor indicator click
 * - OverviewFragment auditor indicator click
 */
class AuditorVerdictActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auditor_verdict)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "🧠 AI Auditor Report"
        }

        // Cancel notification if opened via tap
        AuditorNotificationManager.cancelNotificationStatic(this)

        renderVerdict()
    }

    private fun renderVerdict() {
        val cached = AuditorVerdictCache.get()

        if (cached == null) {
            showEmpty()
            return
        }

        val verdict = cached.verdict
        val ageMs = System.currentTimeMillis() - cached.timestamp
        val ageMin = (ageMs / 60_000).toInt()

        // ── Timestamp ────────────────────────────────────────────────────────
        findViewById<TextView>(R.id.auditor_timestamp).text =
            "Report from: ${formatTimestamp(cached.timestamp)} (${ageMin}m ago)"

        // ── Verdict Badge ─────────────────────────────────────────────────────
        val verdictView = findViewById<TextView>(R.id.auditor_verdict_badge)
        when (verdict.verdict) {
            is VerdictType.Confirm -> {
                verdictView.text = "✅ CONFIRM"
                verdictView.setBackgroundColor(ContextCompat.getColor(this, app.aaps.core.ui.R.color.inRange))
            }
            is VerdictType.Soften -> {
                verdictView.text = "⚠️ SOFTEN"
                verdictView.setBackgroundColor(ContextCompat.getColor(this, app.aaps.core.ui.R.color.warning))
            }
            is VerdictType.ShiftToTbr -> {
                verdictView.text = "🔄 SHIFT TO TBR"
                verdictView.setBackgroundColor(ContextCompat.getColor(this, app.aaps.core.ui.R.color.examinedProfile))
            }
        }

        // ── Confidence ────────────────────────────────────────────────────────
        val confidencePct = (verdict.confidence * 100).toInt()
        findViewById<TextView>(R.id.auditor_confidence).text =
            "Confidence: $confidencePct%${if (verdict.degradedMode) "  ⚠️ Degraded Mode" else ""}"

        // ── Bounded Adjustments ───────────────────────────────────────────────
        val adj = verdict.boundedAdjustments
        findViewById<TextView>(R.id.auditor_adjustments).text = buildString {
            appendLine("SMB factor:  ×${String.format("%.2f", adj.smbFactorClamp)}")
            appendLine("Interval:    +${adj.intervalAddMin} min")
            appendLine("TBR factor:  ×${String.format("%.2f", adj.tbrFactorClamp)}")
            append("Prefer TBR:  ${if (adj.preferTbr) "Yes" else "No"}")
        }

        // ── Risk Flags ────────────────────────────────────────────────────────
        val riskContainer = findViewById<LinearLayout>(R.id.auditor_risk_container)
        if (verdict.riskFlags.isEmpty()) {
            riskContainer.visibility = View.GONE
        } else {
            riskContainer.visibility = View.VISIBLE
            val riskView = findViewById<TextView>(R.id.auditor_risk_flags)
            riskView.text = verdict.riskFlags.joinToString("\n") { "⚠ $it" }
        }

        // ── Evidence ──────────────────────────────────────────────────────────
        val evidenceView = findViewById<TextView>(R.id.auditor_evidence)
        evidenceView.text = if (verdict.evidence.isEmpty()) {
            "No evidence provided"
        } else {
            verdict.evidence.joinToString("\n") { "• $it" }
        }

        // ── Debug Checks ──────────────────────────────────────────────────────
        val debugContainer = findViewById<LinearLayout>(R.id.auditor_debug_container)
        if (verdict.debugChecks.isEmpty()) {
            debugContainer.visibility = View.GONE
        } else {
            debugContainer.visibility = View.VISIBLE
            val debugView = findViewById<TextView>(R.id.auditor_debug_checks)
            debugView.text = verdict.debugChecks.joinToString("\n") { "· $it" }
        }

        // ── Hide empty state ───────────────────────────────────────────────────
        findViewById<View>(R.id.auditor_empty_state).visibility = View.GONE
        findViewById<View>(R.id.auditor_content).visibility = View.VISIBLE
    }

    private fun showEmpty() {
        findViewById<View>(R.id.auditor_empty_state).visibility = View.VISIBLE
        findViewById<View>(R.id.auditor_content).visibility = View.GONE
    }

    private fun formatTimestamp(ts: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
