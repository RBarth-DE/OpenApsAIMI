package app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.ui

import android.os.Handler
import android.os.Looper
import app.aaps.core.interfaces.overview.AuditorDisplayState
import app.aaps.core.interfaces.overview.AuditorStateProvider
import app.aaps.plugins.aps.openAPSAIMI.advisor.auditor.model.AuditorUIState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditorStateProviderImpl @Inject constructor(
    private val auditorStatusLiveData: AuditorStatusLiveData
) : AuditorStateProvider {

    private val _flow = MutableStateFlow(map(auditorStatusLiveData.uiState.value))
    override val displayStateFlow: StateFlow<AuditorDisplayState> = _flow.asStateFlow()

    init {
        Handler(Looper.getMainLooper()).post {
            auditorStatusLiveData.uiState.observeForever { state ->
                _flow.value = map(state)
            }
        }
    }

    private fun map(state: AuditorUIState?): AuditorDisplayState = when (state?.type) {
        AuditorUIState.StateType.PROCESSING -> AuditorDisplayState.PROCESSING
        AuditorUIState.StateType.READY -> AuditorDisplayState.READY
        AuditorUIState.StateType.WARNING -> AuditorDisplayState.WARNING
        AuditorUIState.StateType.ERROR -> AuditorDisplayState.ERROR
        else -> AuditorDisplayState.IDLE
    }
}
