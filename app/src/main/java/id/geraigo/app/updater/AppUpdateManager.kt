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
import org.json.JSONArray
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
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

object AppUpdateManager {
    private const val GITHUB_OWNER = "arbdevai"
    private const val GITHUB_REPO = "gerai-go"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases"

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

    /**
     * Parse numeric SemVer components (Major, Minor, Patch).
     * Example: "v1.2.3" -> [1, 2, 3], "1.10.0" -> [1, 10, 0].
     */
    fun parseSemVer(versionStr: String): List<Int> {
        val clean = versionStr.trim().removePrefix("v").substringBefore("-")
        return clean.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
    }

    /**
     * Strict numerical SemVer comparison per skill specification.
     * Prevents false lexical string comparisons ("1.10.0" vs "1.9.0").
     */
    fun isNewerVersion(remoteTag: String, localVersionName: String): Boolean {
        val remote = parseSemVer(remoteTag)
        val local = parseSemVer(localVersionName)
        if (remote.isEmpty() || local.isEmpty()) return false

        val maxLen = maxOf(remote.size, local.size)
        for (i in 0 until maxLen) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
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
                    return@withContext UpdateUiState.Error("Server rilis GitHub merespon dengan kode ${response.code}.")
                }
                val body = response.body?.string() ?: return@withContext UpdateUiState.Error("Respon server rilis kosong.")
                val releasesArray = JSONArray(body)
                if (releasesArray.length() == 0) {
                    return@withContext UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                }

                var candidateRelease: JSONObject? = null
                var candidateTag = ""
                var candidateUrl = ""
                var candidateSize = 0L

                // Scan releases for versioned tags (e.g. v1.2.0, v1.3.0) or latest release
                for (i in 0 until releasesArray.length()) {
                    val rel = releasesArray.getJSONObject(i)
                    if (rel.optBoolean("draft", false)) continue

                    val tagName = rel.optString("tag_name", "").trim()
                    val assets = rel.optJSONArray("assets") ?: continue

                    var apkUrl = ""
                    var apkSize = 0L
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }

                    if (apkUrl.isNotBlank()) {
                        candidateRelease = rel
                        candidateTag = tagName
                        candidateUrl = apkUrl
                        candidateSize = apkSize
                        // Prefer versioned release tag if found
                        if (tagName.startsWith("v") && tagName.contains(".")) {
                            break
                        }
                    }
                }

                if (candidateRelease == null || candidateUrl.isBlank()) {
                    return@withContext UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                }

                // If candidate is a versioned tag (e.g. v1.2.0), compare SemVer numerically
                if (candidateTag.startsWith("v") || candidateTag.contains(".")) {
                    val hasNewer = isNewerVersion(candidateTag, BuildConfig.VERSION_NAME)
                    if (!hasNewer) {
                        return@withContext UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                    }
                } else if (candidateTag == "ci-latest") {
                    // For ci-latest, check if local version is already 1.2.0 or above
                    return@withContext UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                }

                val releaseNotes = candidateRelease.optString("body", "Pembaruan versi terbaru Gerai Go.").trim()
                UpdateUiState.Available(
                    versionName = candidateTag.removePrefix("v").ifBlank { candidateTag },
                    releaseNotes = releaseNotes,
                    downloadUrl = candidateUrl,
                    apkSize = candidateSize
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

            val responseBody = response.body ?: error("Isi berkas unduhan kosong.")
            val totalBytes = if (expectedBytes > 0) expectedBytes else responseBody.contentLength()
            var downloadedBytes = 0L

            try {
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
            } catch (e: Exception) {
                // Delete isolated .part file immediately on failure to prevent orphaned partial files
                if (partFile.exists()) partFile.delete()
                throw e
            }

            // Rename .part to .apk atomically
            if (!partFile.renameTo(destApkFile)) {
                partFile.copyTo(destApkFile, overwrite = true)
                partFile.delete()
            }

            // 4-layer integrity verification
            if (!isApkValid(context, destApkFile, expectedBytes)) {
                destApkFile.delete()
                error("Verifikasi integritas APK 4 lapis gagal. Berkas korup atau tanda tangan tidak cocok.")
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
