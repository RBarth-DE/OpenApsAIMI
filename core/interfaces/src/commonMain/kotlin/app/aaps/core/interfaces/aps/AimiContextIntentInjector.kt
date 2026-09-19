package app.aaps.core.interfaces.aps

/**
 * Takes one AIMI context intent that arrived from Nightscout and hands it to the AIMI context
 * manager.
 *
 * The NS client runs on every target, but AIMI itself is Android only: the intent is parsed with the
 * platform JSON reader and stored by code that the other targets do not link. So the sync side knows
 * only this interface, and the plugin that can honour it implements it.
 *
 * The caller keeps the note format (`AIMI_CONTEXT:<id>[:PIN:<pin>]:<json>`) and its logging, because
 * that is part of the NS protocol rather than of AIMI. Only the "parse and store" part is here.
 */
fun interface AimiContextIntentInjector {

    /**
     * Parses [intentJson] and injects it under [contextId].
     *
     * @return true when the intent was parsed and injected, false when it was rejected as malformed.
     */
    fun injectFromNs(contextId: String, intentJson: String, pin: String?): Boolean
}
