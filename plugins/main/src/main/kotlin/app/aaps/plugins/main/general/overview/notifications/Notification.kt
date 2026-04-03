package app.aaps.plugins.main.general.overview.notifications

import androidx.annotation.RawRes

// AAPS4 compatibility shim - replaces AAPS3 Notification base class
abstract class Notification {
    var id: Int = 0
    var date: Long = System.currentTimeMillis()
    var validTo: Long = 0L
    var text: String = ""
    var level: Int = 0
    @RawRes var soundId: Int? = null
    var buttonText: Int = 0
    var action: Runnable? = null

    companion object {
        const val ANNOUNCEMENT = 0
        const val NORMAL = 1
        const val URGENT = 2
        const val NS_ANNOUNCEMENT = 1001
        const val NS_ALARM = 1002
        const val NS_URGENT_ALARM = 1003
    }
}
