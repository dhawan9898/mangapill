package com.mangapill.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mangapill.app.data.scraper.BaseScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val apkUrl: String,
    val size: Long? = null,
    val publishedAt: String?
)

object AppUpdateService {

    /**
     * Checks GitHub releases for a newer version of the application.
     * Returns UpdateInfo if a newer release with an APK asset is found, or null otherwise.
     */
    suspend fun checkForUpdate(
        repo: String = "dhawan9898/mangapill",
        currentVersion: String
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "MangapillApp")
            .build()

        val response = BaseScraper.sharedClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("GitHub API returned HTTP ${response.code}")
        }

        val bodyStr = response.body?.string() ?: throw IOException("Empty response from GitHub")
        val json = JSONObject(bodyStr)

        val tagName = json.optString("tag_name", "").trim()
        val version = tagName.removePrefix("v").trim()
        val releaseNotes = json.optString("body", "").trim()
        val publishedAt = if (json.isNull("published_at")) null else json.optString("published_at")

        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        var apkSize: Long? = null

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    val s = asset.optLong("size", -1L)
                    if (s > 0) apkSize = s
                    break
                }
            }
        }

        if (apkUrl.isNullOrBlank()) {
            return@withContext null
        }

        if (isNewerVersion(version, currentVersion)) {
            UpdateInfo(
                version = version,
                releaseNotes = releaseNotes,
                apkUrl = apkUrl,
                size = apkSize,
                publishedAt = publishedAt
            )
        } else {
            null
        }
    }

    /** Updates are only ever installed from GitHub releases: https, and a GitHub host. */
    internal fun isTrustedApkUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val host = parsed.host.lowercase()
        return parsed.isHttps && (host == "github.com" || host.endsWith(".github.com") ||
            host == "githubusercontent.com" || host.endsWith(".githubusercontent.com"))
    }

    /**
     * Downloads the APK file to app cache and launches the Android Package Installer.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        expectedSize: Long? = null,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isTrustedApkUrl(apkUrl)) throw IOException("Updates can only be downloaded from GitHub over https.")
            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", BaseScraper.DEFAULT_USER_AGENT)
                .build()

            // The shared client gives a whole call 30 s, which is right for a page and far too short
            // for an APK: on a slow connection the update could never finish.
            val client = BaseScraper.sharedClient.newBuilder()
                .callTimeout(15, java.util.concurrent.TimeUnit.MINUTES)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw IOException("Failed to download APK: HTTP $code")
            }
            // Redirects are followed by the client: make sure the file really came from GitHub.
            if (!isTrustedApkUrl(response.request.url.toString())) {
                response.close()
                throw IOException("The update link redirected somewhere that is not GitHub. Will not install.")
            }

            val body = response.body ?: throw IOException("Empty APK download body")
            val totalBytes = body.contentLength()
            val apkFile = File(context.cacheDir, "mangapill-update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesCopied = 0L
                    var shownPercent = -1
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes > 0) {
                            val progress = (bytesCopied.toFloat() / totalBytes).coerceIn(0f, 1f)
                            // Once per percent: a hop to the main thread for every 8 KB slowed the download.
                            val percent = (progress * 100).toInt()
                            if (percent != shownPercent) {
                                shownPercent = percent
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                    output.flush()
                }
            }

            val expected = if (expectedSize != null && expectedSize > 0) expectedSize else totalBytes
            val received = apkFile.length()
            if (expected > 0 && received < expected) {
                apkFile.delete()
                throw IOException("The download stopped early ($received of $expected bytes).")
            }
            if (received < 1_000_000L) {
                apkFile.delete()
                throw IOException("The downloaded file is too small ($received bytes) to be the app.")
            }

            withContext(Dispatchers.Main) {
                onProgress(1f)
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            }
        }
    }

    /**
     * Compare semantic versions: returns true if latest is strictly greater than current.
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val lParts = latest.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }
        val cParts = current.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}

