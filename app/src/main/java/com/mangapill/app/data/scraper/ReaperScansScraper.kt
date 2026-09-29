package com.mangapill.app.data.scraper

class ReaperScansScraper : MangaThemesiaScraper() {
    override val sourceId = "reaperscans"
    override val sourceName = "Reaper Scans"
    override val baseUrl = "https://reaperscans.com"
    override val mangaListPath = "/series"
}
