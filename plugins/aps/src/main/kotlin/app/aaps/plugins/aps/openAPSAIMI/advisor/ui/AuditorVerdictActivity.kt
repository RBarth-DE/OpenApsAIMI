package app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui
import kotlinx.coroutines.runBlocking

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorStatusTracker
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
            val (status, _) = AuditorStatusTracker.getStatus()
            val message = when {
                status == AuditorStatusTracker.Status.OFF ->
                    "AI Auditor is disabled. Enable it in AIMI settings."
                status.isOffline() -> when (status) {
                    AuditorStatusTracker.Status.OFFLINE_NO_APIKEY ->
                        "No API key configured.\nAdd your AI provider key in AIMI → Auditor settings."
                    AuditorStatusTracker.Status.OFFLINE_NO_NETWORK ->
                        "No network connection.\nCheck device connectivity."
                    AuditorStatusTracker.Status.OFFLINE_NO_ENDPOINT ->
                        "AI API endpoint unavailable."
                    AuditorStatusTracker.Status.OFFLINE_DNS_FAIL ->
                        "DNS resolution failed. Check network."
                    else -> "Auditor offline: ${status.message}"
                }
                status.isError() -> when (status) {
                    AuditorStatusTracker.Status.ERROR_TIMEOUT ->
                        "Last audit timed out.\nThe AI server did not respond in time."
                    AuditorStatusTracker.Status.ERROR_PARSE ->
                        "Last audit failed: could not parse AI response."
                    AuditorStatusTracker.Status.ERROR_HTTP ->
                        "Last audit failed: HTTP error from AI server."
                    AuditorStatusTracker.Status.ERROR_EXCEPTION ->
                        "Last audit failed with an unexpected error."
                    else -> "Auditor error: ${status.message}"
                }
                status.isSkipped() ->
                    "Sentinel monitoring active – situation normal, no AI review needed this cycle."
                else ->
                    "No audit report yet.\nStatus: ${status.message}"
            }
            showEmpty(message)
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

    private fun showEmpty(reason: String) {
        findViewById<View>(R.id.auditor_content).visibility = View.GONE
        val emptyView = findViewById<View>(R.id.auditor_empty_state)
        emptyView.visibility = View.VISIBLE
        emptyView.findViewById<TextView?>(R.id.auditor_empty_message)?.text = reason
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
