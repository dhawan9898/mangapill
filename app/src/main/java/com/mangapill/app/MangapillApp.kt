package com.mangapill.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.mangapill.app.data.scraper.BaseScraper
import okhttp3.OkHttpClient

class MangapillApp : Application(), ImageLoaderFactory {

    /**
     * Manga image CDNs reject hot-linked requests, so every image request carries a browser
     * User-Agent and a Referer (MangaDex's CDN needs none, the Mangapill CDN needs mangapill.com).
     */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("User-Agent", BaseScraper.DEFAULT_USER_AGENT)
                val host = original.url.host
                if (original.header("Referer") == null && !host.endsWith("mangadex.org") && !host.endsWith("mangadex.network")) {
                    builder.header("Referer", "https://mangapill.com/")
                }
                chain.proceed(builder.build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
