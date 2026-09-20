package app.aaps.plugins.aps

import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy

/**
 * A [FabricPrivacy] for `commonTest` that records instead of reporting.
 *
 * The APS algorithm reports its `determine_basal` failures through [logException], so a test that
 * cares whether the algorithm bailed out can read [exceptions] rather than mock the call.
 */
class FakeFabricPrivacy : FabricPrivacy {

    val exceptions = mutableListOf<Throwable>()
    val customEvents = mutableListOf<String>()
    val messages = mutableListOf<String>()

    override fun setUserProperty(key: String, value: String) = Unit

    override fun logCustom(event: String) {
        customEvents.add(event)
    }

    override fun logCustom(name: String, params: Map<String, Long>) {
        customEvents.add(name)
    }

    override fun logMessage(message: String) {
        messages.add(message)
    }

    override fun logException(throwable: Throwable) {
        exceptions.add(throwable)
    }

    /** False: this fake reports nothing, wherever it is used. */
    override fun fabricEnabled(): Boolean = false

    override fun logWearException(wearException: EventData.WearException) = Unit
}
