package com.mangapill.app.data.scraper

class ManhuaPlusScraper : MangaThemesiaScraper() {
    override val sourceId = "manhuaplus"
    override val sourceName = "ManhuaPlus"
    override val baseUrl = "https://manhuaplus.com"
    override val isMadara = true
}
