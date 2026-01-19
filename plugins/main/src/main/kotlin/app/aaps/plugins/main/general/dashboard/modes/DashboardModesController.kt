package app.aaps.plugins.main.general.dashboard.modes

import android.content.Context
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog

import androidx.fragment.app.FragmentActivity

import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.AapsSchedulers

class DashboardModesController(
    private val activity: FragmentActivity,
    private val automation: Automation,
    private val resourceHelper: ResourceHelper,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers
) {
    fun availableModes(): Set<DashboardModes> =
        automation.userEvents()
            .filter { it.isEnabled && it.canRun() }
            .mapNotNull { mapEventToMode(it) }
            .toSet()

    fun runModeWithConfirmation(mode: DashboardModes) {
        val event = mapModeToEvent(mode) ?: return

        OKDialog.showConfirmation(
            activity,
            resourceHelper.gs(
                app.aaps.core.ui.R.string.dashboard_run_question,
                event.title
            )
        ) {
            aapsSchedulers.io.scheduleDirect {
                automation.processEvent(event)

                aapsSchedulers.main.scheduleDirect {
                    rxBus.send(EventNewHistoryData(0L, false))
                }
            }
        }
    }

    fun runEventWithConfirmation(event: AutomationEvent) {
        OKDialog.showConfirmation(
            activity,
            resourceHelper.gs(
                app.aaps.core.ui.R.string.dashboard_run_question,
                event.title
            )
        ) {
            aapsSchedulers.io.scheduleDirect {
                automation.processEvent(event)

                aapsSchedulers.main.scheduleDirect {
                    rxBus.send(EventNewHistoryData(0L, false))
                }
            }
        }

    }

    fun mapEventToMode(event: AutomationEvent): DashboardModes? {
        val title = event.title.lowercase()
        return when {
            title.startsWith("big") -> DashboardModes.BIG
            title.startsWith("med") -> DashboardModes.MED
            title.startsWith("small") -> DashboardModes.SMALL
            title.startsWith("snack1") -> DashboardModes.SNACK1
            title.startsWith("snack2") -> DashboardModes.SNACK2
            title.startsWith("sport") -> DashboardModes.SPORT
            title.startsWith("hypo") -> DashboardModes.HYPO
            title.startsWith("bier") -> DashboardModes.BEER
            title.startsWith("stop") -> DashboardModes.STOP
            else -> null
        }
    }

    fun mapModeToEvent(mode: DashboardModes): AutomationEvent? =
        automation.userEvents().firstOrNull {
            mapEventToMode(it) == mode
        }
}

