package com.mangapill.app.data.scraper

import com.mangapill.app.data.opds.OpdsAuthInterceptor
import com.mangapill.app.data.opds.OpdsServer

/** Every provider the app knows about: the built-in sites plus the OPDS servers the user added. */
object SourceRegistry {
    /** Built-in providers. Add new sources here. */
    val builtIn: List<BaseScraper> = listOf(
        MangapillScraper(),
        MangaDexScraper(),
        ComickScraper(),
        MangaKakalotScraper(),
        AsuraScansScraper(),
        ReaperScansScraper(),
        FlameComicsScraper(),
        ManhuaPlusScraper()
    )

    @Volatile
    private var opds: List<OpdsScraper> = emptyList()

    @Volatile
    private var byId: Map<String, BaseScraper> = builtIn.associateBy { it.sourceId }

    /** User catalogs first: they are the most reliable sources the app can have. */
    val all: List<BaseScraper> get() = opds + builtIn

    /** Replaces the OPDS providers; called by [com.mangapill.app.data.prefs.AppPrefs] whenever servers change. */
    fun setOpdsServers(servers: List<OpdsServer>) {
        // Keep existing instances (and their cached root feed) for servers that did not change.
        val current = opds.associateBy { it.server }
        opds = servers.map { current[it] ?: OpdsScraper(it) }
        byId = all.associateBy { it.sourceId }
        OpdsAuthInterceptor.update(servers)
    }

    operator fun get(id: String): BaseScraper? = byId[id]

    fun name(id: String): String = byId[id]?.sourceName ?: id

    fun imageReferer(id: String): String? = byId[id]?.imageReferer
}
