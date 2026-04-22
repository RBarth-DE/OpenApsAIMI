package app.aaps.plugins.aps.openAPSAIMI.wcycle

import android.util.Log
import app.aaps.plugins.aps.openAPSAIMI.utils.AimiStorageHelper
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

class WCycleCsvLogger(private val storageHelper: AimiStorageHelper) {
    private val TAG = "WCycleCsvLogger"

    private val file by lazy { storageHelper.getAimiFile("oapsaimi_wcycle.csv") }
    private val dir = storageHelper.getAimiDirectory()
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun append(row: Map<String, Any?>): Boolean {
        val headerNeeded = !file.exists()
        val line = build(row, headerNeeded)

        return runCatching {
            ensureDir(dir)
            file.appendText(line)
        }.onFailure { t ->
            Log.w(TAG, "Write failed at ${file.absolutePath}", t)
        }.isSuccess
    }

    private fun ensureDir(dir: File) {
        if (!dir.exists() && !dir.mkdirs()) {
            error("Unable to create directory ${dir.absolutePath}")
        }
    }

    private fun build(row: Map<String, Any?>, header: Boolean): String {
        val keys = listOf(
            "ts","trackingMode","cycleDay","phase","contraceptive","thyroid","verneuil",
            "bg","delta5","iob","tdd24h","isfProfile","dynIsf",
            "basalBase","smbBase","basalLearn","smbLearn",
            "basalApplied","smbApplied",
            "needBasalScale","needSmbScale",   // ← colonnes utiles pour l'apprentissage offline
            "applied","reason"
        )
        val sb = StringBuilder()
        if (header) sb.append(keys.joinToString(",")).append("\n")
        val map = row.toMutableMap(); map["ts"] = sdf.format(Date())
        sb.append(keys.joinToString(",") { (map[it] ?: "").toString() }).append("\n")
        return sb.toString()
    }
}
