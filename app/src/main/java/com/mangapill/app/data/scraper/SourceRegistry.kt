package com.mangapill.app.data.scraper

/** Every provider the app knows about. Add new sources here. */
object SourceRegistry {
    val all: List<BaseScraper> = listOf(
        MangapillScraper(),
        MangaDexScraper(),
        ComickScraper(),
        MangaKakalotScraper(),
        AsuraScansScraper(),
        ReaperScansScraper(),
        FlameComicsScraper(),
        ManhuaPlusScraper()
    )

    private val byId = all.associateBy { it.sourceId }

    operator fun get(id: String): BaseScraper? = byId[id]

    fun name(id: String): String = byId[id]?.sourceName ?: id

    fun imageReferer(id: String): String? = byId[id]?.imageReferer
}
