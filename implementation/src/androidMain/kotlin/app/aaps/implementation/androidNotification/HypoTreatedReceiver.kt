package app.aaps.implementation.androidNotification

import android.content.BroadcastReceiver
import app.aaps.core.interfaces.di.injectMetroMembers
import dev.zacsweers.metro.Inject
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.alerts.LocalAlertUtils

/**
 * Handles the "Hypo treated" action on the low-glucose alarm notification: holds the alarm for the
 * time carbs need to work and clears the current alert, through
 * [LocalAlertUtils.snoozeHypoAfterTreatment].
 *
 * The action lives on the Android notification because that is the only surface the user sees during
 * a real hypo — the phone is usually locked and the app is not open.
 *
 * Fills its own fields with `injectMetroMembers` because a manifest [BroadcastReceiver] is not
 * constructed by a base class that injects (same pattern as [AlarmMuteReceiver]).
 */
class HypoTreatedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        context.injectMetroMembers(this)
        localAlertUtils.snoozeHypoAfterTreatment()
    }

    @Inject lateinit var localAlertUtils: LocalAlertUtils
}
