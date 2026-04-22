package app.aaps.plugins.aps.openAPSAIMI.pkpd

import android.util.Log
import app.aaps.plugins.aps.openAPSAIMI.utils.AimiStorageHelper
import javax.inject.Inject
import javax.inject.Singleton

data class PkPdLogRow(
    val dateStr: String,
    val epochMin: Long,
    val bg: Double,
    val delta5: Double,
    val iobU: Double,
    val carbsActiveG: Double,
    val windowMin: Int,
    val diaH: Double,
    val peakMin: Double,
    val fusedIsf: Double,
    val tddIsf: Double,
    val profileIsf: Double,
    val tailFrac: Double,
    val smbProposedU: Double,
    val smbFinalU: Double,
    // NEW – audit (nullable pour compat ascendante)
    val tailMult: Double? = null,
    val exerciseMult: Double? = null,
    val lateFatMult: Double? = null,
    val highBgOverride: Boolean? = null,
    val lateFatRise: Boolean? = null,
    val quantStepU: Double? = null,
    val activityStage: String? = null,
    val activityRelief: Double? = null,
    val activityFraction: Double? = null,
    val anticipation: Double? = null
)

@Singleton
class PkPdCsvLogger @Inject constructor(private val storageHelper: AimiStorageHelper) {
    private val path by lazy { storageHelper.getAimiFile("oapsaimi_pkpd_records.csv") }
    private val TAG = "PkPdCsvLogger"

    fun append(row: PkPdLogRow) {
        val appendResult = runCatching {
            val line = listOf(
                row.dateStr,
                row.epochMin,
                row.bg,
                row.delta5,
                row.iobU,
                row.carbsActiveG,
                row.windowMin,
                row.diaH,
                row.peakMin,
                row.fusedIsf,
                row.tddIsf,
                row.profileIsf,
                row.tailFrac,
                row.smbProposedU,
                row.smbFinalU,
                row.tailMult,
                row.exerciseMult,
                row.lateFatMult,
                row.highBgOverride,
                row.lateFatRise,
                row.quantStepU,
                row.activityStage,
                row.activityRelief,
                row.activityFraction,
                row.anticipation
            ).joinToString(",")

            path.appendText(line + "\n")
        }

        appendResult.exceptionOrNull()?.let { throwable ->
            Log.w(TAG, "Unable to append PK/PD log row to ${path.absolutePath}", throwable)
        }
    }
}
