package com.mangapill.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mangapill.app.data.local.AppDatabase
import com.mangapill.app.data.prefs.AppPrefs
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.data.scraper.BaseScraper

class MangapillApp : Application(), ImageLoaderFactory {

    /** Single repository for the process so caches survive activity recreation. */
    val repository: MangaRepository by lazy {
        MangaRepository(AppDatabase.getInstance(this), AppPrefs(this))
    }

    /**
     * Image CDNs reject hot-linked requests. Every request carries a browser User-Agent; the
     * per-source Referer is added by the caller (covers and pages know their source).
     */
    override fun newImageLoader(): ImageLoader {
        val client = BaseScraper.sharedClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", BaseScraper.DEFAULT_USER_AGENT).build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            // Big disk cache: this is what makes prefetched chapters readable offline.
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(512L * 1024 * 1024).build() }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
