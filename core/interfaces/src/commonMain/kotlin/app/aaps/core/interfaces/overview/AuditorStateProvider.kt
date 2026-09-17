package app.aaps.core.interfaces.overview

import kotlinx.coroutines.flow.StateFlow

enum class AuditorDisplayState { IDLE, PROCESSING, READY, WARNING, ERROR }

interface AuditorStateProvider {
    val displayStateFlow: StateFlow<AuditorDisplayState>
}
