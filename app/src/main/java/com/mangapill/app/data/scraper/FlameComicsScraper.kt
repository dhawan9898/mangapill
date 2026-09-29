package com.mangapill.app.data.scraper

class FlameComicsScraper : MangaThemesiaScraper() {
    override val sourceId = "flamecomics"
    override val sourceName = "Flame Comics"
    override val baseUrl = "https://flamecomics.com"
    override val mangaListPath = "/series"
}