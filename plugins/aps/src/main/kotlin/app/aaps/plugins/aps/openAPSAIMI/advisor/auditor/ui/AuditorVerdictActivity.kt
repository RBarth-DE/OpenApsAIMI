package app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui

import kotlinx.coroutines.runBlocking
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import androidx.annotation.StringRes
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.android.support.DaggerAppCompatActivity
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorStatusTracker
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.AuditorVerdictCache
import app.aaps.plugins.aps.openAPSAIMI.model.VerdictType
import app.aaps.plugins.aps.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject


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
class AuditorVerdictActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var auditorStatusLiveData: AuditorStatusLiveData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auditor_verdict)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "🧠 AI Auditor Report"
        }

        // Cancel notification if opened via tap
        AuditorNotificationManager.cancelNotificationStatic(this)

        // Clear the icon badge/error state now that the user is reading the report
        auditorStatusLiveData.markAsRead()

        renderVerdict()
    }

    private fun renderVerdict() {
        if (!preferences.get(BooleanKey.AimiAuditorEnabled)) {
            showEmpty(R.string.aimi_auditor_verdict_disabled)
            return
        }

        val cached = AuditorVerdictCache.get()

        if (cached == null) {
            val (status, _) = AuditorStatusTracker.getStatus()
            val message: String = when {
                status == AuditorStatusTracker.Status.OFF ->
                    getString(R.string.aimi_auditor_verdict_enabled)
                status.isOffline() -> when (status) {
                    AuditorStatusTracker.Status.OFFLINE_NO_APIKEY ->
                        getString(R.string.aimi_auditor_verdict_offline_noapikey)
                    AuditorStatusTracker.Status.OFFLINE_NO_NETWORK ->
                        getString(R.string.aimi_auditor_verdict_offline_no_network)
                    AuditorStatusTracker.Status.OFFLINE_NO_ENDPOINT ->
                        getString(R.string.aimi_auditor_verdict_offline_no_endpoint)
                    AuditorStatusTracker.Status.OFFLINE_DNS_FAIL ->
                        getString(R.string.aimi_auditor_verdict_offline_dns_fail)
                    else -> getString(R.string.aimi_auditor_verdict_offline, status.message)
                }
                status.isError() -> when (status) {
                    AuditorStatusTracker.Status.ERROR_TIMEOUT ->
                        getString(R.string.aimi_auditor_verdict_error_timeout)
                    AuditorStatusTracker.Status.ERROR_PARSE ->
                        getString(R.string.aimi_auditor_verdict_error_parse)
                    AuditorStatusTracker.Status.ERROR_HTTP ->
                        getString(R.string.aimi_auditor_verdict_error_http)
                    AuditorStatusTracker.Status.ERROR_EXCEPTION ->
                        getString(R.string.aimi_auditor_verdict_error_exception)
                    else -> getString(R.string.aimi_auditor_verdict_error, status.message)
                }
                status.isSkipped() ->
                    getString(R.string.aimi_auditor_verdict_skipped)
                else ->
                    getString(R.string.aimi_auditor_verdict_noreport, status.message)
            }
            showEmpty(message)
            return
        }

        val verdict = cached.verdict
        val ageMs = System.currentTimeMillis() - cached.timestamp
        val ageMin = (ageMs / 60_000).toInt()

        // ── Timestamp ────────────────────────────────────────────────────────
        findViewById<TextView>(R.id.auditor_timestamp).text =
            getString(R.string.aimi_auditor_verdict_timestamp, formatTimestamp(cached.timestamp), ageMin)

        // ── Verdict Badge ─────────────────────────────────────────────────────
        val verdictView = findViewById<TextView>(R.id.auditor_verdict_badge)
        when (verdict.verdict) {
            is VerdictType.Confirm -> {
                verdictView.setText( R.string.aimi_auditor_verdict_confirm )
                verdictView.setBackgroundColor(ContextCompat.getColor(this, app.aaps.core.ui.R.color.inRange))
            }
            is VerdictType.Soften -> {
                verdictView.setText( R.string.aimi_auditor_verdict_soften )
                verdictView.setBackgroundColor(ContextCompat.getColor(this, app.aaps.core.ui.R.color.warning))
            }
            is VerdictType.ShiftToTbr -> {
                verdictView.setText( R.string.aimi_auditor_verdict_shifttotbr )
                verdictView.setBackgroundColor(ContextCompat.getColor(this, app.aaps.core.ui.R.color.examinedProfile))
            }
        }

        // ── Confidence ────────────────────────────────────────────────────────
        val confidencePct = (verdict.confidence * 100).toInt()
        findViewById<TextView>(R.id.auditor_confidence).text =
            if (verdict.degradedMode)
                getString(R.string.aimi_auditor_verdict_confidence, confidencePct)
            else
                getString(R.string.aimi_auditor_verdict_confidence_empty)

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

    private fun showEmpty(@StringRes reason: Int) {
        findViewById<View>(R.id.auditor_content).visibility = View.GONE
        val emptyView = findViewById<View>(R.id.auditor_empty_state)
        emptyView.visibility = View.VISIBLE
        emptyView.findViewById<TextView?>(R.id.auditor_empty_message)?.setText(reason)
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
