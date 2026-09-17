package app.aaps.plugins.aps.openAPSAIMI.context.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.extensions.applySystemBarPadding
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSAIMI.context.ContextManager
import app.aaps.plugins.aps.openAPSAIMI.context.ContextPreset
import app.aaps.plugins.aps.openAPSAIMI.patient.PatientStatePresentationBuilder
import app.aaps.plugins.aps.openAPSAIMI.patient.PatientStateRuntimeRepository
import app.aaps.plugins.aps.openAPSAIMI.physio.HealthContextRepository
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * Context Activity - UI for Context Module.
 * 
 * Simplified version without ViewModel for quick implementation.
 */
class ContextActivity : TranslatedDaggerAppCompatActivity() {
    
    @Inject lateinit var contextManager: ContextManager
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var healthContextRepository: HealthContextRepository
    
    private lateinit var binding: ActivityContextViews
    private lateinit var adapter: ContextIntentAdapter

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private var patientStateRefreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityContextViews(layoutInflater.inflate(R.layout.activity_context, null))
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        
        // Toolbar
        title = rh.gs(R.string.context_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        setupUI()
        setupPresets()
        refreshUI()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
        refreshPhysioSnapshot()
        startPatientStateRefresh()
    }

    override fun onPause() {
        patientStateRefreshJob?.cancel()
        patientStateRefreshJob = null
        super.onPause()
    }

    override fun onDestroy() {
        patientStateRefreshJob?.cancel()
        activityScope.cancel()
        super.onDestroy()
    }
    
    private fun setupUI() {
        // RecyclerView
        adapter = ContextIntentAdapter(
            onRemove = { id -> 
                contextManager.removeIntent(id)
                refreshUI()
            },
            onExtend = { id -> showExtendDialog(id) },
            getTimeRemaining = { intent -> getTimeRemaining(intent) },
            getDisplayString = { intent -> getDisplayString(intent) }
        )
        
        binding.recyclerActiveIntents.layoutManager = LinearLayoutManager(this)
        binding.recyclerActiveIntents.adapter = adapter
        
        // Send button
        binding.btnSendChat.setOnClickListener {
            val text = binding.editChatInput.text.toString()
            if (text.isNotBlank()) {
                activityScope.launch {
                    binding.progressParsing.visibility = View.VISIBLE
                    binding.btnSendChat.isEnabled = false
                    
                    try {
                        val ids = contextManager.addIntent(text)
                        
                        if (ids.isNotEmpty()) {
                            binding.editChatInput.text?.clear()
                            Toast.makeText(this@ContextActivity, "${ids.size} context(s) added", Toast.LENGTH_SHORT).show()
                        } else {
                            // Feedback detailed on failure
                            val isLLMEnabled = sp.getBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextLLMEnabled.key, false)
                            val provider = sp.getString(app.aaps.core.keys.StringKey.AimiAdvisorProvider.key, "OPENAI")
                            
                            val msg = if (isLLMEnabled) {
                                "No context detected via AI (\$provider).\n" +
                                    "\n" +
                                    "Possible causes:\n" +
                                    "1. Missing or invalid API key (AIMI Preferences > Advisor)\n" +
                                    "2. Network timeout\n" +
                                    "3. Description too vague\n" +
                                    "\n" +
                                    "Fallback: Try simple keywords (e.g., ‘Sports 1h’, ‘Sick’)."
                            } else {
                                "No context detected based on keywords.\n" +
                                    "Try some simple commands:\n" +
                                    "- ‘1-hour cardio’\n" +
                                    "- ‘Sick’\n" +
                                    "- ‘Stress’\n" +
                                    "- 'Surprise meal'"
                            }
                            
                            MaterialAlertDialogBuilder(this@ContextActivity)
                                .setTitle("Analysis failed")
                                .setMessage(msg)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                        
                        refreshUI()
                    } catch (e: Exception) {
                        Toast.makeText(this@ContextActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        binding.progressParsing.visibility = View.GONE
                        binding.btnSendChat.isEnabled = true
                    }
                }
            }
        }
        
        // Clear button
        binding.btnClearInput.setOnClickListener {
            binding.editChatInput.text?.clear()
        }
        
        // Clear all button
        binding.btnClearAll.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete all contexts?")
                .setPositiveButton("Delete") { _, _ ->
                    contextManager.clearAll()
                    refreshUI()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // Module toggle
        binding.switchContextEnabled.setOnCheckedChangeListener { _, isChecked ->
            sp.putBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextEnabled.key, isChecked)
        }
        
        // LLM toggle
        binding.switchLLMEnabled.setOnCheckedChangeListener { _, isChecked ->
            sp.putBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextLLMEnabled.key, isChecked)
        }
    }
    
    private fun setupPresets() {
        if (ContextPreset.ALL_PRESETS.size >= 10) {
            binding.chipCardio.setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[0]) }
            binding.chipStrength.setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[1]) }
            binding.chipYoga.setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[2]) }
            binding.chipSport.setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[3]) }
            binding.chipWalking.setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[4]) }
            binding.chipSick.setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[5]) }
            binding.chipStress.setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[6]) }
            binding.chipMealRisk.setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[7]) }
            binding.chipAlcohol.setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[8]) }
            binding.chipTravel.setOnClickListener { addPreset(ContextPreset.ALL_PRESETS[9]) }
        }
    }
    
    private fun refreshUI() {
        // Get intents
        val intents = contextManager.getAllIntents().toList()
        adapter.submitList(intents)
        
        // Show/hide empty state
        if (intents.isEmpty()) {
            binding.recyclerActiveIntents.visibility = View.GONE
            binding.textEmptyState.visibility = View.VISIBLE
        } else {
            binding.recyclerActiveIntents.visibility = View.VISIBLE
            binding.textEmptyState.visibility = View.GONE
        }
        
        // Settings
        binding.switchContextEnabled.isChecked = sp.getBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextEnabled.key, false)
        binding.switchLLMEnabled.isChecked = sp.getBoolean(app.aaps.core.keys.BooleanKey.OApsAIMIContextLLMEnabled.key, false)
        refreshPatientRuntimeUi()
    }

    private fun refreshPhysioSnapshot() {
        activityScope.launch(Dispatchers.IO) {
            try {
                healthContextRepository.fetchSnapshot()
            } catch (e: Exception) {
                aapsLogger.error(LTag.APS, "ContextActivity physio snapshot refresh failed", e)
            }
        }
    }

    private fun startPatientStateRefresh() {
        patientStateRefreshJob?.cancel()
        patientStateRefreshJob = activityScope.launch {
            launch {
                PatientStateRuntimeRepository.updates.collectLatest {
                    refreshPatientRuntimeUi()
                }
            }
            while (isActive) {
                refreshPatientRuntimeUi()
                delay(60_000L)
            }
        }
    }

    private fun refreshPatientRuntimeUi() {
        val runtimeSnapshot = PatientStateRuntimeRepository.getLatest()
        val presentation = runtimeSnapshot?.let {
            PatientStatePresentationBuilder.build(
                snapshot = it,
                nowMs = System.currentTimeMillis(),
            )
        }
        if (presentation == null) {
            binding.layoutPatientStateDetails.visibility = View.GONE
            binding.textPatientStateEmpty.visibility = View.VISIBLE
            binding.textPatientStateEmpty.text = rh.gs(R.string.context_patient_state_empty)
            return
        }

        binding.layoutPatientStateDetails.visibility = View.VISIBLE
        binding.textPatientStateEmpty.visibility = View.GONE
        binding.textPatientStateUpdated.text = presentation.updatedSummary
        binding.textPatientStateMode.text = presentation.modeHeadline
        binding.textPatientStateNarrative.text = presentation.narrative
        binding.textPatientStateLiveBody.text = presentation.physioLiveSummary
        binding.textPatientStateThermal.text = presentation.thermalSummary
        binding.textPatientStatePhaseValue.text = presentation.physiologySummary
        binding.textPatientStateIntentValue.text = presentation.intentSummary
        binding.textPatientStateSignalsValue.text = presentation.signalSummary
        PatientSignalGaugeBinder.bindAll(
            mealContainer = binding.layoutPatientSignalGaugeMeal,
            endogenousContainer = binding.layoutPatientSignalGaugeEndogenous,
            resistanceContainer = binding.layoutPatientSignalGaugeResistance,
            thermalContainer = binding.layoutPatientSignalGaugeThermal,
            sensorContainer = binding.layoutPatientSignalGaugeSensor,
            gauges = presentation.signalGauges,
        )
        binding.textPatientStateDeliveryValue.text = presentation.deliverySummary
        binding.textPatientStateReasonsValue.text = presentation.reasonSummary
    }
    
    private fun addPreset(preset: ContextPreset) {
        activityScope.launch {
            try {
                contextManager.addPreset(preset)
                Toast.makeText(this@ContextActivity, "Context added", Toast.LENGTH_SHORT).show()
                refreshUI()
            } catch (e: Exception) {
                Toast.makeText(this@ContextActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showExtendDialog(intentId: String) {
        val options = arrayOf("15 min", "30 min", "1 hour", "2 hours")
        val durations = arrayOf(15, 30, 60, 120)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Expand the context")
            .setItems(options) { _, which ->
                contextManager.extendDuration(intentId, durations[which].minutes)
                refreshUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun getTimeRemaining(intent: app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent): String {
        val now = System.currentTimeMillis()
        val remaining = (intent.endTimeMs - now) / 1000 / 60 // minutes
        
        return when {
            remaining <= 0 -> "Expired"
            remaining < 60 -> "${remaining}minutes remaining"
            else -> "${remaining / 60}h ${remaining % 60}min"
        }
    }
    
    private fun getDisplayString(intent: app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent): String {
        return when (intent) {
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Activity -> 
                "🏃 Activity: ${intent.activityType.name} ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Illness -> 
                "🤒 Illness: ${intent.symptomType.name} ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Stress -> 
                "😰 Stress: ${intent.stressType.name} ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.UnannouncedMealRisk -> 
                "🍕 Meal Risk: ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Alcohol -> 
                "🍷 Alcohol: ${intent.units}U ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Travel -> 
                "✈️ Travel: ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.MenstrualCycle ->
                "🔄 Cycle: ${intent.phase.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.SlowCarbMeal ->
                "🍕 Repas lent: ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.HypoRecovery ->
                "🍬 Hypo récup: ${intent.intensity.name}"
            is app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent.Custom ->
                "📝 ${intent.description}"
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

/**
 * View ports of `activity_context.xml`.
 *
 * Written by hand, because a Kotlin Multiplatform module never gets generated view binding classes
 * - AGP hard codes `viewBinding = false` and `dataBinding = false` for it, and there is no build
 * flag that changes that. So `ActivityContextBinding` does not exist here, and this class takes its
 * place: each property is one `android:id` of the layout, found once when the layout is inflated.
 *
 * The five `layoutPatientSignalGauge*` entries are `<include>` tags, so each one is the root view of
 * `item_patient_signal_gauge.xml` - which is what `PatientSignalGaugeBinder` wants.
 */
private class ActivityContextViews(root: View) {
    val root: View = root

    val recyclerActiveIntents: RecyclerView = root.findViewById(R.id.recyclerActiveIntents)
    val textEmptyState: TextView = root.findViewById(R.id.textEmptyState)

    val editChatInput: TextInputEditText = root.findViewById(R.id.editChatInput)
    val btnSendChat: Button = root.findViewById(R.id.btnSendChat)
    val btnClearInput: Button = root.findViewById(R.id.btnClearInput)
    val progressParsing: ProgressBar = root.findViewById(R.id.progressParsing)

    val chipCardio: Chip = root.findViewById(R.id.chipCardio)
    val chipStrength: Chip = root.findViewById(R.id.chipStrength)
    val chipYoga: Chip = root.findViewById(R.id.chipYoga)
    val chipSport: Chip = root.findViewById(R.id.chipSport)
    val chipWalking: Chip = root.findViewById(R.id.chipWalking)
    val chipSick: Chip = root.findViewById(R.id.chipSick)
    val chipStress: Chip = root.findViewById(R.id.chipStress)
    val chipMealRisk: Chip = root.findViewById(R.id.chipMealRisk)
    val chipAlcohol: Chip = root.findViewById(R.id.chipAlcohol)
    val chipTravel: Chip = root.findViewById(R.id.chipTravel)

    val btnClearAll: Button = root.findViewById(R.id.btnClearAll)

    val switchContextEnabled: SwitchMaterial = root.findViewById(R.id.switchContextEnabled)
    val switchLLMEnabled: SwitchMaterial = root.findViewById(R.id.switchLLMEnabled)

    val layoutPatientStateDetails: LinearLayout = root.findViewById(R.id.layoutPatientStateDetails)
    val textPatientStateUpdated: TextView = root.findViewById(R.id.textPatientStateUpdated)
    val textPatientStateEmpty: TextView = root.findViewById(R.id.textPatientStateEmpty)
    val textPatientStateMode: TextView = root.findViewById(R.id.textPatientStateMode)
    val textPatientStateNarrative: TextView = root.findViewById(R.id.textPatientStateNarrative)
    val textPatientStateLiveBody: TextView = root.findViewById(R.id.textPatientStateLiveBody)
    val textPatientStateThermal: TextView = root.findViewById(R.id.textPatientStateThermal)
    val textPatientStatePhaseValue: TextView = root.findViewById(R.id.textPatientStatePhaseValue)
    val textPatientStateIntentValue: TextView = root.findViewById(R.id.textPatientStateIntentValue)
    val textPatientStateSignalsValue: TextView = root.findViewById(R.id.textPatientStateSignalsValue)
    val textPatientStateDeliveryValue: TextView = root.findViewById(R.id.textPatientStateDeliveryValue)
    val textPatientStateReasonsValue: TextView = root.findViewById(R.id.textPatientStateReasonsValue)

    val layoutPatientSignalGaugeMeal: View = root.findViewById(R.id.layoutPatientSignalGaugeMeal)
    val layoutPatientSignalGaugeEndogenous: View = root.findViewById(R.id.layoutPatientSignalGaugeEndogenous)
    val layoutPatientSignalGaugeResistance: View = root.findViewById(R.id.layoutPatientSignalGaugeResistance)
    val layoutPatientSignalGaugeThermal: View = root.findViewById(R.id.layoutPatientSignalGaugeThermal)
    val layoutPatientSignalGaugeSensor: View = root.findViewById(R.id.layoutPatientSignalGaugeSensor)
}
