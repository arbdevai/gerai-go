package id.geraigo.app.backup

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.*
import id.geraigo.app.data.GeraiDatabase
import id.geraigo.app.data.StoreSettings
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object AutoBackup {
    private const val PREFS = "gerai_go_backup"
    private const val DRIVE_URI = "drive_backup_uri"
    private const val LAST_SYNC = "drive_last_sync"
    private const val DRIVE_STATUS = "drive_status"

    fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun driveUri(context: Context): Uri? = preferences(context).getString(DRIVE_URI, null)?.let(Uri::parse)
    fun setDriveUri(context: Context, uri: Uri?) {
        preferences(context).edit().putString(DRIVE_URI, uri?.toString()).apply()
        request(context)
    }
    fun lastSync(context: Context) = preferences(context).getLong(LAST_SYNC, 0L)
    fun driveStatus(context: Context) = preferences(context).getString(DRIVE_STATUS, "").orEmpty()

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("gerai-go-auto-backup", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun request(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("gerai-go-auto-backup-now", ExistingWorkPolicy.REPLACE, request)
    }

    internal fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    internal fun saveLocal(context: Context, data: ByteArray) {
        val directory = File(context.filesDir, "automatic-backups").apply { mkdirs() }
        writeAtomically(File(directory, "gerai-go-latest.json"), data)
        val dated = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        writeAtomically(File(directory, "gerai-go-$dated.json"), data)

        // Keep last 14 dated backups
        val backupPattern = Regex("""^gerai-go-\d{4}-\d{2}-\d{2}\.json$""")
        directory.listFiles()
            ?.filter { backupPattern.matches(it.name) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(14)
            ?.forEach(File::delete)

        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { saveToDocuments(context, data) }
        }
    }

    private fun saveToDocuments(context: Context, data: ByteArray) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val name = "gerai-go-backup.json"
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/Gerai Go/"

        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND (${MediaStore.MediaColumns.RELATIVE_PATH}=? OR ${MediaStore.MediaColumns.RELATIVE_PATH}=?)",
            arrayOf(name, relativePath, "${Environment.DIRECTORY_DOCUMENTS}/Gerai Go"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) Uri.withAppendedPath(collection, cursor.getLong(0).toString()) else null
        }

        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }) ?: error("File backup lokal tidak dapat dibuat")

        resolver.openOutputStream(uri, "wt")?.use { it.write(data) } ?: error("File backup lokal tidak dapat diperbarui")
    }

    private fun writeAtomically(destination: File, data: ByteArray) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.writeBytes(data)
        if (!temporary.renameTo(destination)) {
            // Fallback copy if rename fails
            destination.writeBytes(data)
            temporary.delete()
        }
    }
}

class AutoBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val db = GeraiDatabase.get(applicationContext)
        val sales = db.sales().observeAll().first()
        val settings = db.settings().get() ?: StoreSettings()
        val bytes = BackupCodec.encode(applicationContext, sales, settings).toByteArray(Charsets.UTF_8)
        AutoBackup.saveLocal(applicationContext, bytes)

        val uri = AutoBackup.driveUri(applicationContext) ?: return Result.success()
        if (!AutoBackup.hasNetwork(applicationContext)) {
            // Keep status pending if offline
            return Result.retry()
        }
        val resolver = applicationContext.contentResolver
        if (resolver.persistedUriPermissions.none { it.uri == uri && it.isWritePermission }) {
            error("Akses file Google Drive perlu disambungkan ulang.")
        }
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("File Google Drive tidak dapat dibuka untuk ditulis.")
        AutoBackup.preferences(applicationContext).edit()
            .putLong("drive_last_sync", System.currentTimeMillis())
            .putString("drive_status", "ok")
            .apply()
        Result.success()
    }.getOrElse { failure ->
        AutoBackup.preferences(applicationContext).edit()
            .putString("drive_status", failure.message ?: "Backup Drive gagal.")
            .apply()
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
