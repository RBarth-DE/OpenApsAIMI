package app.aaps.plugins.main.general.dashboard

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity

import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.ActivityDashboardModesBinding
import app.aaps.plugins.main.general.dashboard.modes.DashboardModesController
import com.google.android.material.button.MaterialButton
import javax.inject.Inject


class DashboardModesActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var automation: Automation
    @Inject lateinit var resourceHelper: ResourceHelper

    private lateinit var binding: ActivityDashboardModesBinding
    private lateinit var modeController: DashboardModesController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardModesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = resourceHelper.gs(R.string.dashboard_nav_modes)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val settingsItem = binding.toolbar.menu.add(0, 1, 0, "Settings")
        settingsItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        settingsItem.setIcon(app.aaps.core.ui.R.drawable.ic_settings)
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
                    modeController.runEventWithConfirmation(event)
                    finish()
                }
            }
            binding.actionsContainer.addView(button)
        }
    }

}
