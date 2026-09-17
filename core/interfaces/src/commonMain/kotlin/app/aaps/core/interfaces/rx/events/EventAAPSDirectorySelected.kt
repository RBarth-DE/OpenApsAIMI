package app.aaps.core.interfaces.rx.events

import app.aaps.core.keys.interfaces.TextRef

/** Fired when the user selects the AAPS directory (SAF tree). */
class EventAAPSDirectorySelected(val status: String) : EventStatus() {

    override fun getStatus(): TextRef = TextRef.Literal(status)
}
