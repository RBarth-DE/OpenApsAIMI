package app.aaps.plugins.main.general.overview.notifications.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import dev.zacsweers.metro.Inject

/**
 * Answers the delete intent of a notification posted by
 * [app.aaps.plugins.main.general.overview.notifications.NotificationStore]: the user swiped the
 * notification away, and the notification with the carried id is dismissed in the app as well.
 *
 * Android builds a receiver itself, so it cannot take the bus in a constructor - it asks the graph
 * for its fields, the same way the other receivers in the tree do.
 */
class DismissNotificationReceiver : BroadcastReceiver() {

    companion object {

        const val ACTION = "app.aaps.plugins.main.general.overview.notifications.receivers.DismissNotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        context.injectMetroMembers(this)
        rxBus.send(EventDismissNotification(intent.getIntExtra("alertID", -1)))
    }

    @Inject lateinit var rxBus: RxBus
}
