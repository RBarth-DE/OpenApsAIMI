package app.aaps.plugins.aps.openAPSAIMI.learning
import kotlinx.coroutines.runBlocking

import android.content.Context
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.plugins.aps.openAPSAIMI.AimiNeuralNetwork
import app.aaps.plugins.aps.openAPSAIMI.TrainingConfig
import app.aaps.plugins.aps.openAPSAIMI.utils.AimiStorageHelper
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

/**
 * T3cNeuralTrainerWorker - Asynchronous background trainer for T3C Brittle Mode.
 * 
 * Goal: Learn the ideal "Aggressiveness Factor" for a given physiological context.
 * 
 * Labeling Logic:
 * If actualDelta > expectedDelta -> aggressiveness was too high.
 * If actualDelta < expectedDelta -> aggressiveness was too low.
 */
class T3cNeuralTrainerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : LoggingWorker(appContext, workerParams, Dispatchers.IO) {

    @Inject lateinit var storageHelper: AimiStorageHelper

    override suspend fun doWorkAndLog(): Result {
        aapsLogger.debug(LTag.APS, "🧠 T3C Neural Trainer: Starting training session")

        val externalDir = storageHelper.getAimiDirectory()
        val csvFile = File(externalDir, "basal_adaptive_records.csv")
        val weightsFile = File(externalDir, "t3c_brain_weights.json")

        if (!csvFile.exists()) {
            aapsLogger.debug(LTag.APS, "🧠 Basal Adaptive CSV not found. Aborting.")
            return Result.success()
        }

        val allLines = csvFile.readLines()
        if (allLines.size < 50) { // Minimum samples required
            aapsLogger.debug(LTag.APS, "🧠 Insufficient T3C data (${allLines.size} rows). Need 50.")
            return Result.success()
        }

        val header = allLines.first().split(",")
        val dataLines = allLines.drop(1)
        
        val inputs = mutableListOf<FloatArray>()
        val targets = mutableListOf<DoubleArray>()

        // Column indices
        val iBg = header.indexOf("bg")
        val iEventual = header.indexOf("eventualBg")
        val iBasal = header.indexOf("basal")
        val iTarget = header.indexOf("target")
        val iAccel = header.indexOf("accel")
        val iDuraMin = header.indexOf("duraMin")
        val iDuraAvg = header.indexOf("duraAvg")
        val iIob = header.indexOf("iob")
        val iCurrentAgg = header.indexOf("t3cAgg")

        var skippedRows = 0
        // Use index-based loop: next row's `bg` is the actual measured BG ~30 min later
        // (T3c runs every 30 min → consecutive rows are ~1 control period apart).
        // This is far better than using `eventualBg` which is a PKPD prediction —
        // training on own predictions creates a self-referential loop.
        // Safety filter: if |bgNext − bgCurrent| > 80 mg/dL, assume a gap in the log
        // (T3c was paused) and skip the pair rather than train on an invalid outcome.
        for (i in dataLines.indices) {
            try {
                val cols = dataLines[i].split(",")
                if (cols.size < header.size) { skippedRows++; continue }

                // Actual outcome: next row's BG (real measurement, not prediction)
                val nextCols = dataLines.getOrNull(i + 1)?.split(",")
                val bgBefore = cols[iBg].toDouble()
                val bgAfterActual = nextCols
                    ?.getOrNull(iBg)
                    ?.toDoubleOrNull()
                    ?.takeIf { kotlin.math.abs(it - bgBefore) <= 80.0 }  // gap filter
                if (bgAfterActual == null) { skippedRows++; continue }  // last row or gap
            
                // 1. Prepare Inputs
                val inputFeatures = floatArrayOf(
                    cols[iBg].toFloat(),
                    cols[iBasal].toFloat(),
                    cols[iAccel].toFloat(),
                    cols[iDuraMin].toFloat(),
                    cols[iDuraAvg].toFloat(),
                    cols[iIob].toFloat()
                )

                // 2. Labeling — use actual measured BG from next record as outcome
                val targetBg   = cols[iTarget].toDouble()
                val currentAgg = cols[iCurrentAgg].toDouble()

                val actualDelta = bgBefore - bgAfterActual
                val neededDelta = bgBefore - targetBg
            
                // Label: Ideal Aggressiveness Factor
                // Goal: scale currentAgg so that actualDelta ≈ neededDelta.
                //
                // Edge case: if actualDelta <= 0 (BG moved the wrong direction or stayed flat),
                // division is undefined / produces nonsense. coerceAtLeast(1.0) was the old
                // "fix" but it silently turned any adverse BG move into a fake 1 mg/dL drop,
                // then produced a wildly inflated weight (e.g. neededDelta=60 / 1 = 60).
                // The signal collapsed to always maxAgg with no magnitude information.
                //
                // Correct handling:
                //   BG went up when it should have dropped → aggressiveness clearly too low,
                //     but we can't compute a ratio → apply a capped boost.
                //   BG went up when it should have gone up (over-treatment in hypo region) →
                //     aggressiveness was too high → apply a capped reduction.
                val weight = when {
                    abs(neededDelta) < 5.0 -> 1.0   // already near target, keep current
                    actualDelta <= 0.0 -> {
                        // BG did not fall at all (or rose). Ratio formula meaningless.
                        if (neededDelta > 0.0) 1.5   // needed to fall, didn't → boost
                        else 0.7                      // needed to rise (hypo), overshot → reduce
                    }
                    else -> (neededDelta / actualDelta).coerceIn(0.5, 3.0)
                }
                val idealAgg = (currentAgg * weight).coerceIn(0.5, 2.0)

                inputs.add(inputFeatures)
                targets.add(doubleArrayOf(idealAgg))
            } catch (e: Exception) {
                skippedRows++
                aapsLogger.debug(LTag.APS, "🧠 T3C Trainer: Skipping malformed row (${e.message})")
            }
        }
        if (skippedRows > 0) {
            aapsLogger.debug(LTag.APS, "🧠 T3C Trainer: Skipped $skippedRows / ${dataLines.size} malformed rows")
        }

        if (inputs.isEmpty()) return Result.success()

        // 3. Training
        val net = AimiNeuralNetwork(
            inputSize = inputs.first().size,
            hiddenSize = 8,
            outputSize = 1,
            config = TrainingConfig(
                learningRate = 0.001,
                epochs = 300,
                patience = 20
            )
        )

        // Split 80/20
        val split = (inputs.size * 0.8).toInt()
        net.trainWithValidation(
            inputs.subList(0, split), targets.subList(0, split),
            inputs.subList(split, inputs.size), targets.subList(split, targets.size)
        )

        // 4. Save Weights
        net.saveToFile(weightsFile)
        aapsLogger.debug(LTag.APS, "🧠 T3C Neural Trainer: Training complete. Weights saved to t3c_brain_weights.json")

        return Result.success()
    }
}
