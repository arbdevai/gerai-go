package id.geraigo.app.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import id.geraigo.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(
        val versionName: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val apkSize: Long
    ) : UpdateUiState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState
    data class ReadyToInstall(
        val apkFile: File,
        val versionName: String
    ) : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

object AppUpdateManager {
    private const val GITHUB_OWNER = "arbdevai"
    private const val GITHUB_REPO = "gerai-go"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/tags/ci-latest"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun getUpdatesDir(context: Context): File {
        return File(context.cacheDir, "updates").apply { mkdirs() }
    }

    fun cleanOldUpdates(context: Context) {
        runCatching {
            val dir = getUpdatesDir(context)
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk") || file.name.endsWith(".part")) {
                    file.delete()
                }
            }
        }
    }

    suspend fun checkUpdate(context: Context): UpdateUiState = withContext(Dispatchers.IO) {
        cleanOldUpdates(context)
        val request = Request.Builder()
            .url(RELEASES_API_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "GeraiGo-App")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateUiState.Error("Server update mengembalikan kode ${response.code}.")
                }
                val body = response.body?.string() ?: return@withContext UpdateUiState.Error("Respon server kosong.")
                val json = JSONObject(body)

                val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                val releaseNotes = json.optString("body", "Pembaruan versi terbaru Gerai Go.").trim()
                val assets = json.optJSONArray("assets") ?: return@withContext UpdateUiState.Error("Aset APK tidak ditemukan.")

                var downloadUrl = ""
                var apkSize = 0L

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }

                if (downloadUrl.isBlank()) {
                    return@withContext UpdateUiState.Error("Berkas APK belum tersedia di rilis ini.")
                }

                UpdateUiState.Available(
                    versionName = tagName.ifBlank { "ci-latest" },
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    apkSize = apkSize
                )
            }
        }.getOrElse { exc ->
            UpdateUiState.Error(exc.message ?: "Gagal memeriksa pembaruan.")
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        expectedBytes: Long,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getUpdatesDir(context)
            val partFile = File(dir, "update.part")
            val destApkFile = File(dir, "gerai-go-update.apk")

            if (partFile.exists()) partFile.delete()
            if (destApkFile.exists()) destApkFile.delete()

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "GeraiGo-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("Gagal mengunduh: HTTP ${response.code}")
            }

            val responseBody = response.body ?: error("Isi berkas kosong.")
            val totalBytes = if (expectedBytes > 0) expectedBytes else responseBody.contentLength()
            var downloadedBytes = 0L

            responseBody.byteStream().use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(65536) // 64 KB buffer per skill guideline
                    var read: Int
                    var lastUpdate = System.currentTimeMillis()

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100 || downloadedBytes == totalBytes) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            // Rename .part to .apk atomically
            if (!partFile.renameTo(destApkFile)) {
                partFile.copyTo(destApkFile, overwrite = true)
                partFile.delete()
            }

            // 4-layer validation
            if (!isApkValid(context, destApkFile, expectedBytes)) {
                destApkFile.delete()
                error("Verifikasi integritas APK gagal. Berkas rusak atau tidak cocok.")
            }

            destApkFile
        }
    }

    /** 4-layer APK integrity validation as per skill specification. */
    fun isApkValid(context: Context, apkFile: File, expectedBytes: Long = 0L): Boolean {
        // Layer 1: Physical file size check (> 8 MB)
        if (!apkFile.exists() || !apkFile.isFile || apkFile.length() < 8_000_000L) {
            return false
        }
        if (expectedBytes > 0L && Math.abs(apkFile.length() - expectedBytes) > 100_000L) {
            return false
        }

        return try {
            // Layer 2: Deep archive & DEX parsing
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_ACTIVITIES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES or PackageManager.GET_ACTIVITIES
            }
            val pkg = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false

            // Layer 3: Strict package name match
            if (pkg.packageName != context.packageName) {
                return false
            }

            // Layer 4: Valid version presence
            val remoteVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }
            remoteVersionCode > 0
        } catch (_: Exception) {
            false
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() < 8_000_000L) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(settingsIntent)
            return
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}
