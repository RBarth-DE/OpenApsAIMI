package app.aaps.plugins.aps.openAPSAIMI.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🛡️ Helper pour stockage robuste AIMI avec stratégie hybride.
 * 
 * Stratégie en 3 niveaux :
 * 1️⃣ PRÉFÉRÉ : Documents/AAPS (cohérence avec design AIMI, accessible utilisateur)
 * 2️⃣ FALLBACK : App-scoped external storage (pas de permissions requises)
 * 3️⃣ DERNIER RECOURS : Internal storage (toujours disponible)
 * 
 * Garantie : NE CRASH JAMAIS, même si permissions manquantes.
 * 
 * Utilisation :
 * ```kotlin
 * @Inject lateinit var storageHelper: AimiStorageHelper
 * 
 * private val file by lazy { storageHelper.getAimiFile("my_data.json") }
 * ```
 */
@Singleton
class AimiStorageHelper @Inject constructor(
    private val context: Context,
    private val log: AAPSLogger,
    private val preferences: Preferences
) {

    /**
     * État du stockage utilisé (pour logs de santé)
     */
    enum class StorageStatus {
        DOCUMENTS_AAPS,      // ✅ Documents/AAPS accessible
        APP_SCOPED_EXTERNAL, // ⚠️ Fallback app-scoped external
        INTERNAL_ONLY,       // ⚠️ Dernier recours internal
        ERROR                // ❌ Erreur (ne devrait jamais arriver)
    }
    
    private var currentStatus: StorageStatus = StorageStatus.ERROR
    private var currentDirectory: File? = null
    private var lastError: String? = null
    
    /**
     * Obtient le statut actuel du stockage (pour monitoring)
     */
    fun getStorageStatus(): Triple<StorageStatus, String?, String?> {
        return Triple(currentStatus, currentDirectory?.absolutePath, lastError)
    }
    
    /**
     * Détermine le meilleur répertoire de stockage disponible.
     * Appelé une seule fois au premier accès (lazy init).
     *
     * AIMI files always target Documents/AAPS/ (not the main AAPS app configured directory).
     */
    @Synchronized
    private fun determineStorageDirectory(): File {
        if (currentDirectory != null) {
            return currentDirectory!!
        }

        val storageManagerGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        // 1️⃣ Documents/AAPS (only attempted if MANAGE_EXTERNAL_STORAGE is granted; same permission needed)
        if (storageManagerGranted) {
            try {
                val docsDir = File(Environment.getExternalStorageDirectory(), "Documents/AAPS")
                val hasAccess = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    true  // storageManagerGranted == true in this branch
                } else {
                    docsDir.exists() && docsDir.canWrite()
                }
                if (hasAccess) {
                    if (!docsDir.exists()) docsDir.mkdirs()
                    currentStatus = StorageStatus.DOCUMENTS_AAPS
                    currentDirectory = docsDir
                    log.info(LTag.APS, "AimiStorageHelper: 📁 Using Documents/AAPS (preferred)")
                    log.info(LTag.APS, "  → Path: ${docsDir.absolutePath}")
                    return docsDir
                } else {
                    if (lastError == null) lastError = "Documents/AAPS not accessible (canWrite=false)"
                    log.warn(LTag.APS, "AimiStorageHelper: ⚠️ Documents/AAPS not accessible (canWrite=false)")
                }
            } catch (e: Exception) {
                if (lastError == null) lastError = "Cannot access Documents/AAPS: ${e.message}"
                log.warn(LTag.APS, "AimiStorageHelper: ⚠️ Cannot access Documents/AAPS: ${e.message}")
            }
        } else {
            log.debug(LTag.APS, "AimiStorageHelper: Skipping Documents/AAPS – MANAGE_EXTERNAL_STORAGE not granted")
        }
        
        // 2️⃣ Fallback vers app-scoped external storage
        try {
            val appDataDir = context.getExternalFilesDir(null)
            if (appDataDir != null && (appDataDir.exists() || appDataDir.mkdirs())) {
                currentStatus = StorageStatus.APP_SCOPED_EXTERNAL
                currentDirectory = appDataDir
                log.info(LTag.APS, "AimiStorageHelper: 📁 Using app-scoped external storage (fallback)")
                log.info(LTag.APS, "  → Path: ${appDataDir.absolutePath}")
                log.info(LTag.APS, "  → Reason: $lastError")
                return appDataDir
            }
        } catch (e: Exception) {
            lastError = "Cannot access external app storage: ${e.message}"
            log.warn(LTag.APS, "AimiStorageHelper: Cannot access external app storage: ${e.message}")
        }
        
        // 3️⃣ Dernier recours : stockage interne (toujours disponible)
        currentStatus = StorageStatus.INTERNAL_ONLY
        currentDirectory = context.filesDir
        log.warn(LTag.APS, "AimiStorageHelper: 📁 Using internal storage (last resort)")
        log.warn(LTag.APS, "  → Path: ${context.filesDir.absolutePath}")
        log.warn(LTag.APS, "  → Reason: $lastError")
        return context.filesDir
    }
    
    /**
     * Obtient le répertoire de stockage AIMI.
     */
    fun getAimiDirectory(): File {
        return determineStorageDirectory()
    }

    /**
     * Resets cached directory so it is re-evaluated on next access.
     * Call after MANAGE_EXTERNAL_STORAGE is granted at runtime.
     * Also clears the SAF bridge cache so it re-reads [StringKey.AapsDirectoryUri].
     */
    @Synchronized
    fun resetDirectory() {
        currentDirectory = null
        currentStatus = StorageStatus.ERROR
        lastError = null
        safRootCache = null
        safRootInitAttempted = false
        log.info(LTag.APS, "AimiStorageHelper: Directory cache reset (will re-evaluate on next access)")
    }
    
    /**
     * Obtient un fichier dans le répertoire AIMI.
     * 
     * @param filename Nom du fichier (ex: "basal_learning.json")
     * @return File dans le meilleur emplacement disponible
     */
    fun getAimiFile(filename: String): File {
        val dir = getAimiDirectory()
        val targetDir = if (filename.endsWith(".tflite")) {
            File(dir, "ml").also { if (!it.exists()) it.mkdirs() }
        } else {
            dir
        }
        return File(targetDir, filename).also {
            log.debug(LTag.APS, "AimiStorageHelper: File '$filename' → ${it.absolutePath}")
        }
    }

    /**
     * Liste tous les fichiers AIMI susceptibles d'être sauvegardés.
     * Scanne récursivement le répertoire AAPS pour les modèles (.json), datasets (.csv) et logs (.jsonl).
     * Files larger than [AimiBackupManager.MAX_BACKUP_FILE_BYTES] are omitted (OOM guard).
     */
    fun listBackupCandidates(): List<File> {
        val root = getAimiDirectory()
        val candidates = mutableListOf<File>()
        val maxBytes = AimiBackupManager.MAX_BACKUP_FILE_BYTES
        
        log.info(LTag.APS, "AimiStorageHelper: Scanning legacy directory for backup: ${root.absolutePath}")
        
        fun scan(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scan(file)
                } else {
                    val name = file.name.lowercase()
                    if (name.endsWith(".json") || name.endsWith(".csv") || name.endsWith(".jsonl")) {
                        // Exclure les fichiers temporaires ou backups automatiques si nécessaire
                        if (!name.contains(".tmp") && !name.contains(".pending")) {
                            val len = file.length()
                            if (len > maxBytes) {
                                log.warn(
                                    LTag.APS,
                                    "AimiStorageHelper: Skipping oversized backup candidate ${file.name} ($len bytes)",
                                )
                            } else {
                                candidates.add(file)
                            }
                        }
                    }
                }
            }
        }
        
        scan(root)
        log.info(LTag.APS, "AimiStorageHelper: Found ${candidates.size} backup candidates in ${root.absolutePath}")
        return candidates
    }
    
    /**
     * Obtient un fichier dans un sous-répertoire AIMI.
     * 
     * @param subdirectory Sous-répertoire (ex: "ml", "csv")
     * @param filename Nom du fichier
     * @return File dans le meilleur emplacement disponible
     */
    fun getAimiFile(subdirectory: String, filename: String): File {
        val dir = getAimiDirectory()
        val subdir = File(dir, subdirectory)
        if (!subdir.exists()) {
            subdir.mkdirs()
        }
        return File(subdir, filename).also {
            log.debug(LTag.APS, "AimiStorageHelper: File '$subdirectory/$filename' → ${it.absolutePath}")
        }
    }
    
    /**
     * Charge un fichier de manière robuste avec fallback.
     * 
     * @param file Fichier à charger
     * @param onSuccess Callback appelé avec le contenu si succès
     * @param onError Callback appelé en cas d'erreur (optionnel)
     * @return true si chargé avec succès
     */
    // ── SAF bridge for Documents/AAPS without MANAGE_EXTERNAL_STORAGE ──────────
    // The SAF tree URI is stored in SharedPreferences and survives reinstall via
    // SPBackupAgent, so learner state in Documents/AAPS remains reachable even
    // when MANAGE_EXTERNAL_STORAGE hasn't been re-granted after flash.
    private var safRootCache: DocumentFile? = null
    private var safRootInitAttempted = false

    private fun getSafRoot(): DocumentFile? {
        if (safRootInitAttempted) return safRootCache
        safRootInitAttempted = true
        val uriStr = preferences.getIfExists(StringKey.AapsDirectoryUri) ?: return null
        if (uriStr.isEmpty()) return null
        return try {
            val treeUri = Uri.parse(uriStr)
            val root = DocumentFile.fromTreeUri(context, treeUri)
            if (root != null && root.canRead() && root.canWrite()) {
                safRootCache = root
                log.debug(LTag.APS, "AimiStorageHelper: SAF bridge to Documents/AAPS ready")
                root
            } else {
                log.debug(LTag.APS, "AimiStorageHelper: SAF tree exists but is not read/write ready")
                null
            }
        } catch (e: Exception) {
            log.debug(LTag.APS, "AimiStorageHelper: SAF bridge init failed: ${e.message}")
            null
        }
    }

    /**
     * Try to load file content from Documents/AAPS via SAF.
     * Used as a transparent fallback when the local app-private copy doesn't exist yet
     * (e.g. after a reinstall that wiped app-private storage).
     */
    private fun tryLoadFromSaf(file: File): String? {
        val root = getSafRoot() ?: return null
        val name = file.name
        return try {
            val doc = root.findFile(name) ?: return null
            if (doc.length() <= 0L && doc.lastModified() <= 0L) return null // doesn't really exist
            context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                String(stream.readBytes()).takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            log.debug(LTag.APS, "AimiStorageHelper: SAF load failed for $name: ${e.message}")
            null
        }
    }

    /**
     * Mirror a save to Documents/AAPS via SAF so learner state persists
     * across reinstalls even when the current storage is app-private.
     * Always returns true (best-effort — failures are logged but not propagated).
     */
    private fun trySaveToSaf(file: File, content: String): Boolean {
        val root = getSafRoot() ?: return true // SAF not configured, not an error
        val name = file.name
        return try {
            // Delete existing file (SAF doesn't auto-overwrite)
            root.findFile(name)?.delete()
            // Infer MIME type
            val mime = when {
                name.endsWith(".json") -> "application/json"
                name.endsWith(".csv") -> "text/csv"
                name.endsWith(".jsonl") -> "application/x-jsonlines"
                else -> "application/octet-stream"
            }
            val doc = root.createFile(mime, name) ?: run {
                log.debug(LTag.APS, "AimiStorageHelper: SAF createFile returned null for $name")
                return true
            }
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { stream ->
                stream.write(content.toByteArray())
            }
            log.debug(LTag.APS, "AimiStorageHelper: ✅ SAF mirrored ${name} (${content.length} bytes)")
            true
        } catch (e: Exception) {
            log.debug(LTag.APS, "AimiStorageHelper: SAF mirror failed for $name: ${e.message}")
            true // don't propagate — local save already succeeded
        }
    }

    /**
     * Tells whether the current storage is a fallback (not Documents/AAPS),
     * i.e. whether SAF mirroring would be beneficial for persistence.
     */
    private fun isUsingFallbackStorage(): Boolean =
        currentStatus != StorageStatus.DOCUMENTS_AAPS

    fun loadFileSafe(
        file: File,
        onSuccess: (String) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ): Boolean {
        return runCatching {
            if (!file.exists()) {
                // ── SAF fallback: try loading from Documents/AAPS via SAF ──
                if (isUsingFallbackStorage()) {
                    val safContent = tryLoadFromSaf(file)
                    if (safContent != null) {
                        onSuccess(safContent)
                        log.info(LTag.APS, "AimiStorageHelper: ✅ Loaded ${file.name} via SAF bridge (${safContent.length} bytes)")
                        return true
                    }
                }
                log.debug(LTag.APS, "AimiStorageHelper: File ${file.name} does not exist (first run)")
                return false
            }
            
            if (!file.canRead()) {
                log.warn(LTag.APS, "AimiStorageHelper: File ${file.name} exists but cannot be read")
                return false
            }
            
            val content = file.readText()
            if (content.isEmpty()) {
                log.warn(LTag.APS, "AimiStorageHelper: File ${file.name} is empty")
                return false
            }
            
            onSuccess(content)
            log.debug(LTag.APS, "AimiStorageHelper: ✅ Loaded ${file.name} (${content.length} bytes)")
            true
            
        }.getOrElse { e ->
            log.error(LTag.APS, "AimiStorageHelper: Failed to load ${file.name}: ${e.message}", e)
            onError?.invoke(e)
            false
        }
    }
    
    /**
     * Sauvegarde un fichier de manière robuste.
     * 
     * @param file Fichier à sauvegarder
     * @param content Contenu à écrire
     * @return true si sauvegardé avec succès
     */
    fun saveFileSafe(file: File, content: String): Boolean {
        return runCatching {
            file.writeText(content)
            log.debug(LTag.APS, "AimiStorageHelper: ✅ Saved ${file.name} (${content.length} bytes)")
            // ── SAF mirror: persist to Documents/AAPS so state survives reinstall ──
            if (isUsingFallbackStorage()) {
                trySaveToSaf(file, content)
            }
            true
        }.getOrElse { e ->
            log.warn(LTag.APS, "AimiStorageHelper: ⚠️ Failed to save ${file.name}: ${e.message}")
            log.debug(LTag.APS, "  → Path: ${file.absolutePath}")
            log.debug(LTag.APS, "  → Data will be lost on restart but app continues normally")
            // Even if local save failed, try SAF in case the file system is broken but SAF works
            if (isUsingFallbackStorage()) {
                trySaveToSaf(file, content)
            }
            false
        }
    }
    
    /**
     * Génère un rapport de santé du stockage pour les logs Adjustments.
     */
    fun getHealthReport(): String {
        getAimiDirectory() // ensure lazy init is done before reading status
        val (status, path, error) = getStorageStatus()
        return when (status) {
            StorageStatus.DOCUMENTS_AAPS ->
                "✅ Storage: Documents/AAPS"
            StorageStatus.APP_SCOPED_EXTERNAL ->
                "⚠️ Storage: App-scoped (fallback) - Reason: ${error ?: "unknown"}"
            StorageStatus.INTERNAL_ONLY ->
                "⚠️ Storage: Internal only (degraded) - Reason: ${error ?: "unknown"}"
            StorageStatus.ERROR ->
                "❌ Storage: ERROR - ${error ?: "unknown"}"
        }
    }

}
