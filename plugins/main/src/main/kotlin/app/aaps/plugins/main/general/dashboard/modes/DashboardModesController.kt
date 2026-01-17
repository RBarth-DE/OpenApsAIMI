package app.aaps.plugins.main.general.dashboard.modes

import android.content.Context
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.plugins.main.R
import androidx.fragment.app.FragmentActivity

class DashboardModesController(
    private val activity: FragmentActivity,
    private val automation: Automation,
    private val resourceHelper: ResourceHelper
) {

    fun runModeWithConfirmation(
        mode: DashboardModes,
        onConfirmed: (AutomationEvent) -> Unit
    ) {
        val event = mapModeToEvent(mode) ?: return

        OKDialog.showConfirmation(
            activity,
            resourceHelper.gs(
                app.aaps.core.ui.R.string.dashboard_run_question,
                event.title
            ),
            Runnable {
                onConfirmed(event)
            }
        )
    }

    fun runEventWithConfirmation(
        event: AutomationEvent,
        onConfirmed: () -> Unit
    ) {
        OKDialog.showConfirmation(
            activity,
            resourceHelper.gs(
                app.aaps.core.ui.R.string.dashboard_run_question,
                event.title
            ),
            Runnable {
                onConfirmed()
            }
        )
    }


    fun availableModes(): Set<DashboardModes> {
        return automation.userEvents()
            .filter { it.isEnabled && it.canRun() }
            .mapNotNull { mapEventToMode(it) }
            .toSet()
    }

    private fun mapModeToEvent(mode: DashboardModes): AutomationEvent? {
        return automation.userEvents().firstOrNull { event ->
            when (mode) {
                DashboardModes.BIG -> event.title.startsWith("Big")
                DashboardModes.MED -> event.title.startsWith("Med")
                DashboardModes.SMALL -> event.title.startsWith("Small")
                DashboardModes.SNACK1 -> event.title.startsWith("Snack1")
                DashboardModes.SNACK2 -> event.title.startsWith("Snack2")
                DashboardModes.SPORT -> event.title.startsWith("Sport")
                DashboardModes.HYPO -> event.title.startsWith("Hypo")
                DashboardModes.BEER -> event.title.startsWith("Bier")
                DashboardModes.STOP -> event.title.startsWith("Stop")
                DashboardModes.UNKNOWN -> false
            }
        }
    }


    private fun mapEventToMode(event: AutomationEvent): DashboardModes? =
        when {
            event.title.startsWith("Big") -> DashboardModes.BIG
            event.title.startsWith("Med") -> DashboardModes.MED
            event.title.startsWith("Small") -> DashboardModes.SMALL
            event.title.startsWith("Snack1") -> DashboardModes.SNACK1
            event.title.startsWith("Snack2") -> DashboardModes.SNACK2
            event.title.startsWith("Sport") -> DashboardModes.SPORT
            event.title.startsWith("Hypo") -> DashboardModes.HYPO
            event.title.startsWith("Bier") -> DashboardModes.BEER
            event.title.startsWith("Stop") -> DashboardModes.STOP
            else -> null
        }
}
