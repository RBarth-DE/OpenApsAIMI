package app.aaps.plugins.main.general.overview.notifications

import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.plugins.main.general.overview.notifications.events.EventUpdateOverviewNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import dev.zacsweers.metro.Inject

/**
 * Helper used by both Overview and Dashboard to keep the notification list in sync with the
 * notification store. This centralises the subscription logic so both screens react to the same
 * update events without duplicating code.
 */
class NotificationUiBinder @Inject constructor(
    private val notificationStore: NotificationStore,
    private val aapsLogger: AAPSLogger,
) {

    fun bind(
        overviewBus: RxBus,
        notificationsView: RecyclerView,
        scope: CoroutineScope,
    ) {
        // Ensure the current content is visible immediately when the fragment is shown.
        notificationStore.updateNotifications(notificationsView)

        overviewBus.toFlow(EventUpdateOverviewNotification::class)
            .collectResilient(scope, aapsLogger, LTag.UI, start = CoroutineStart.UNDISPATCHED) {
                notificationStore.updateNotifications(notificationsView)
            }
    }

    fun bindCompose(
        overviewBus: RxBus,
        onSnapshot: (List<NotificationStore.NotificationComposeItem>) -> Unit,
        scope: CoroutineScope,
    ) {
        onSnapshot(notificationStore.snapshotForCompose())
        overviewBus.toFlow(EventUpdateOverviewNotification::class)
            .collectResilient(scope, aapsLogger, LTag.UI, start = CoroutineStart.UNDISPATCHED) {
                onSnapshot(notificationStore.snapshotForCompose())
            }
    }

    fun dismissCompose(id: Int): List<NotificationStore.NotificationComposeItem> {
        notificationStore.dismissFromCompose(id)
        return notificationStore.snapshotForCompose()
    }
}
