package app.aaps.plugins.main.general.dashboard

import android.os.Bundle
import androidx.core.view.isVisible
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity

import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.ActivityDashboardModesBinding
import app.aaps.plugins.main.general.dashboard.modes.DashboardModesController
import com.google.android.material.button.MaterialButton
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class DashboardModesActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var automation: Automation
    @Inject lateinit var resourceHelper: ResourceHelper

    private lateinit var binding: ActivityDashboardModesBinding
    private lateinit var modeController: DashboardModesController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardModesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modeController = DashboardModesController( this, automation, resourceHelper)

    }

    override fun onResume() {
        super.onResume()
        renderActions()
    }
    private fun renderActions() {
        val actions = automation.userEvents().filter { it.isEnabled && it.canRun() }
        binding.modesEmpty.isVisible = actions.isEmpty()
        binding.actionsContainer.removeAllViews()

        actions.forEach { event ->
            val button = MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = event.title
                setOnClickListener {
                    modeController.runEventWithConfirmation(event) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            automation.processEvent(event)
                        }
                        finish()
                    }
                }
            }

            binding.actionsContainer.addView(button)
        }
    }

}
