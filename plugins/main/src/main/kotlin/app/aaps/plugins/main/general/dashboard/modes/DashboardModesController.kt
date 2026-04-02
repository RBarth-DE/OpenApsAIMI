package app.aaps.plugins.main.general.dashboard.modes

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
      fun runEventWithConfirmation(event: AutomationEvent, onComplete: () -> Unit = {}) {
        // 1. Safety Check: Ensure the activity is still alive and valid
        if (activity.isFinishing || activity.isDestroyed) return

        OKDialog.showConfirmation(
            activity,
            resourceHelper.gs(
                app.aaps.core.ui.R.string.dashboard_run_question,
                event.title
            )
        ) {
            // 2. The logic inside the curly braces runs ONLY after the user clicks "OK"
            aapsSchedulers.io.scheduleDirect {
                automation.processEvent(event)

                aapsSchedulers.main.scheduleDirect {
                    rxBus.send(EventNewHistoryData(0L, false))

                    // 3. Optional: If you want the screen to close AFTER the event is processed,
                    // call activity.finish() here, inside the main scheduler block.
                    onComplete()
                }
            }
        }
    }
}

