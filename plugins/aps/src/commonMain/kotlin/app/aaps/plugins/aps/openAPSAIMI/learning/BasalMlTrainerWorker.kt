package app.aaps.plugins.aps.openAPSAIMI.learning

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.objects.workflow.MetroWorkerCreator
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers

/**
 * Periodic worker (1h, no constraints) for basal / T3C neural weight training.
 */
class BasalMlTrainerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    // Injected (not @Assisted): forces Dagger to instantiate the @Singleton coordinator, so training
    // actually runs. The previous static-`instance` lookup was never populated (nobody injected the
    // coordinator) → the worker retried forever and no model was ever trained.
    private val coordinator: BasalMlTrainingCoordinator,
) : LoggingWorker(appContext, workerParams, Dispatchers.IO, aapsLogger, fabricPrivacy) {

    override suspend fun doWorkAndLog(): Result {
        aapsLogger.debug(LTag.APS, "BasalMlTrainerWorker: starting coordinated training")
        return runBasalMlTrainingJob(coordinator)
    }

    /**
     * WorkManager builds this worker from a class name, so the graph must be able to hand back a
     * creator. The same shape as [app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryWorker].
     */
    @AssistedFactory
    fun interface Factory : MetroWorkerCreator {

        override fun create(appContext: Context, workerParams: WorkerParameters): BasalMlTrainerWorker
    }
}
