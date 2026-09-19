package app.aaps.plugins.aps.openAPSAIMI.context

import app.aaps.core.interfaces.aps.AimiContextIntentInjector
import app.aaps.core.interfaces.logging.AAPSLogger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * [AimiContextIntentInjector] for Android, where AIMI itself is built.
 *
 * It is the two calls the NS client used to make directly: read the JSON, then store the intent.
 * This lives in `androidMain` with the rest of AIMI, so other targets do not link it and get the
 * client binding instead, which accepts nothing.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AimiContextIntentInjector>())
class AimiContextIntentInjectorImpl(
    private val contextManager: ContextManager,
    private val aapsLogger: AAPSLogger,
) : AimiContextIntentInjector {

    override fun injectFromNs(contextId: String, intentJson: String, pin: String?): Boolean {
        val intent = ContextIntentDeserializer.deserialize(intentJson, aapsLogger) ?: return false
        contextManager.injectContextFromNS(contextId, intent, pin)
        return true
    }
}
