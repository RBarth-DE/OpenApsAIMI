package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.AuditorDisplayState
import app.aaps.core.interfaces.overview.AuditorStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Placeholder. The flow stays at [AuditorDisplayState.IDLE], so the AIMI auditor tile draws nothing
 * and no screen can act on a state that was never measured.
 *
 * Worth saying plainly: **the auditor is not an iOS problem.** `AuditorStateProviderImpl` reads
 * `AuditorStatusLiveData`, which is AIMI's own status, and the whole AIMI tree lives in
 * `:plugins:aps`'s androidMain by decision - the fork's algorithms are Android only for now. This
 * class exists so [app.aaps.ios.shell.di.IosAppGraph] can be built at all, because it collects every
 * view model and `GraphViewModel` reads this one. Delete it when AIMI is ported or when the graph
 * stops collecting that view model.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosAuditorStateProvider(
    private val aapsLogger: AAPSLogger
) : AuditorStateProvider {

    private val state = MutableStateFlow(AuditorDisplayState.IDLE)

    override val displayStateFlow: StateFlow<AuditorDisplayState>
        get() {
            aapsLogger.notOnIosYet("AuditorStateProvider.displayStateFlow")
            return state
        }
}
