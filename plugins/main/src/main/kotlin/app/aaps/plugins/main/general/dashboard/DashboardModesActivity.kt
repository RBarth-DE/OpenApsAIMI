package app.aaps.plugins.main.general.dashboard

import android.os.Bundle
import android.view.MenuItem
import androidx.core.view.isVisible
import android.content.Intent
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity

import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.ActivityDashboardModesBinding
import app.aaps.plugins.main.general.dashboard.modes.DashboardModesController
import com.google.android.material.button.MaterialButton
import javax.inject.Inject
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus

class DashboardModesActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var automation: Automation
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    private lateinit var binding: ActivityDashboardModesBinding
    private lateinit var modeController: DashboardModesController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modeController = DashboardModesController(
            this,
            automation,
            resourceHelper,
            rxBus,
            aapsSchedulers
        )

        binding = ActivityDashboardModesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = resourceHelper.gs(R.string.dashboard_nav_modes)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val settingsItem = binding.toolbar.menu.add(0, 1, 0, "Settings")
        settingsItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        settingsItem.setIcon(app.aaps.core.ui.R.drawable.ic_settings)

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                try {
                    val intent = Intent().setClassName(this, "app.aaps.plugins.aps.openAPSAIMI.advisor.AimiModeSettingsActivity")
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                true
            } else {
                false
            }
        }
        renderActions()
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
                        // In the Activity instead of directly using finish(), we give the system 150ms.
                        window.decorView.postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                finish()
                            }
                        }, 150) // A short delay of 150ms gives the launcher time to correctly detect the task status.
                    }
                }
            }
            binding.actionsContainer.addView(button)
        }
    }

    override fun finish() {
        // Prevent fragments from performing further operations
        supportFragmentManager.executePendingTransactions()
        super.finish()
    }
}
