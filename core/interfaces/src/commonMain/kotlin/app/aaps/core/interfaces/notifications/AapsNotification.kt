package app.aaps.core.interfaces.notifications

import kotlin.time.Clock

data class AapsNotification(
    val id: NotificationId,
    val instanceKey: Int,
    val text: String,
    val level: NotificationLevel,
    val date: Long = Clock.System.now().toEpochMilliseconds(),
    val validTo: Long = 0L,
    val sound: AlarmSound? = null,
    val actions: List<NotificationAction> = emptyList(),
    val validityCheck: (() -> Boolean)? = null,
    /**
     * [android.os.SystemClock.elapsedRealtime] at which an accompanying bypass-DND channel one-shot
     * of the same [soundRes] was posted (DND-override path), else 0. Passed to
     * [AlarmSoundPlayer.play] so the ramping MediaPlayer loop defers past the channel one-shot and
     * they don't overlap. 0 → no accompanying channel sound (play immediately).
     */
    val postedAtElapsedRealtime: Long = 0L
)
