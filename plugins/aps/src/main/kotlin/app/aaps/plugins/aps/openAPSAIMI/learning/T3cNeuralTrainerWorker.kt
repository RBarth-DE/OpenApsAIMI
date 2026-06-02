package app.aaps.plugins.aps.openAPSAIMI.learning

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import kotlinx.coroutines.Dispatchers

/**
 * Legacy worker name — delegates to [BasalMlTrainingCoordinator].
 * Prefer [BasalMlTrainerWorker] scheduled by [AimiMlTrainingScheduler].
 */
class T3cNeuralTrainerWorker(
    appContext: Context,
    workerParams: WorkerParameters,aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy
) : LoggingWorker(appContext, workerParams, Dispatchers.IO , aapsLogger , fabricPrivacy) {

    override suspend fun doWorkAndLog(): Result {
        aapsLogger.debug(LTag.APS, "T3cNeuralTrainerWorker: delegating to BasalMlTrainingCoordinator")
        return runBasalMlTrainingJob()
    }
}
