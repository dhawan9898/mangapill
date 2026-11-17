package com.mangapill.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mangapill.app.data.CrashLog
import com.mangapill.app.data.download.ChapterDownloads
import com.mangapill.app.data.local.AppDatabase
import com.mangapill.app.data.prefs.AppPrefs
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.data.scraper.BaseScraper
import com.mangapill.app.data.sync.CloudSync
import com.mangapill.app.data.sync.LibrarySync

class MangapillApp : Application(), ImageLoaderFactory {

    /** Single repository for the process so caches survive activity recreation. */
    val repository: MangaRepository by lazy {
        // Downloads live in filesDir, not the cache: clearing the image cache must not remove them.
        MangaRepository(AppDatabase.getInstance(this), AppPrefs(this), ChapterDownloads(filesDir.resolve("downloads")))
    }

    /** The user's account: syncs reading progress, My List and settings between devices. */
    val cloud: CloudSync by lazy {
        LibrarySync.create(this, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, AppDatabase.getInstance(this), repository.prefs)
            .also { sync -> repository.onLibraryChanged = { sync.schedule() } }
    }

    override fun onCreate() {
        super.onCreate()
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        CrashLog.install(filesDir, version)
        startErrorReporting(version)
    }

    /** Failures go to the app_errors table (see data/ErrorReporter.kt). */
    private fun startErrorReporting(version: String) {
        val sp = getSharedPreferences("mangapill_errors", MODE_PRIVATE)
        val store = object : com.mangapill.app.data.sync.KeyValueStore {
            override fun get(key: String): String? = sp.getString(key, null)
            override fun set(key: String, value: String?) {
                sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            }
        }
        com.mangapill.app.data.ErrorReporter.configure(
            BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, version,
            "Android ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})",
            store, { runCatching { cloud.accessToken() }.getOrNull() }, BaseScraper.sharedClient
        )
        // The crash handler saved the last crash to a file: send it once (the file stays for Settings).
        CrashLog.read(filesDir)?.let { report ->
            val id = report.hashCode().toString()
            if (store.get("crash_reported") != id) {
                val lines = report.lines()
                val trace = lines.drop(3).joinToString("\n")
                val first = lines.drop(3).firstOrNull { it.isNotBlank() }?.trim() ?: "Crash"
                com.mangapill.app.data.ErrorReporter.report(
                    "crash", first, NativeCrash(first, trace),
                    mapOf("device" to lines.getOrNull(0), "when" to lines.getOrNull(1))
                )
                store.set("crash_reported", id)
            }
        }
    }

    /** A crash read back from the saved report: carries its original stack trace. */
    private class NativeCrash(message: String, private val trace: String) : Throwable(message) {
        override fun printStackTrace(s: java.io.PrintWriter) { s.print(trace) }
    }

    /**
     * Image CDNs reject hot-linked requests. Every request carries the User-Agent its host expects
     * (see [BaseScraper.imageUserAgent]); the per-source Referer is added by the caller (covers and
     * pages know their source).
     */
    override fun newImageLoader(): ImageLoader {
        val client = BaseScraper.sharedClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(request.newBuilder().header("User-Agent", BaseScraper.imageUserAgent(request.url.host)).build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            // Big disk cache: this is what makes prefetched chapters readable offline.
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(512L * 1024 * 1024).build() }
            .crossfade(true)
            // Covers and pages that fail to load (host, reason) go to the error reports.
            .eventListener(object : coil.EventListener {
                override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) {
                    val data = request.data.toString()
                    if (!data.startsWith("http")) return
                    val host = runCatching { java.net.URI(data).host }.getOrNull() ?: "?"
                    val reason = result.throwable.message ?: result.throwable.javaClass.simpleName
                    com.mangapill.app.data.ErrorReporter.report("image", "Image failed from $host: $reason", result.throwable, mapOf("host" to host))
                }
            })
            .respectCacheHeaders(false)
            .build()
    }
}
