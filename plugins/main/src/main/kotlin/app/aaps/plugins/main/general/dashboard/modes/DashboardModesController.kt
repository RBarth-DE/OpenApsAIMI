package app.aaps.plugins.main.general.dashboard.modes
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.runBlocking

import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.resources.ResourceHelper
// OKDialog import removed

import androidx.fragment.app.FragmentActivity

import app.aaps.core.interfaces.rx.bus.RxBus
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

        aapsSchedulers.io.scheduleDirect {
                runBlocking { automation.processEvent(event) }

                aapsSchedulers.main.scheduleDirect {
                    rxBus.send(EventAPSCalculationFinished())

                    // 3. Optional: If you want the screen to close AFTER the event is processed,
                    // call activity.finish() here, inside the main scheduler block.
                    onComplete()
                }
            }
        }
    }
}

