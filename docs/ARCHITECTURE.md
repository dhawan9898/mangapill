# Mangapill architecture

Native Android app: Kotlin, Jetpack Compose, Room, OkHttp + Jsoup, Coil. No backend: every source is
scraped on the device, so there is no server to host, and no shared IP to get blocked.

## Directory layout

```
app/src/main/java/com/mangapill/app/
├── MangapillApp.kt            Application: one repository, Coil image loader (512 MB disk cache)
├── MainActivity.kt            NavHost + spring/fade screen transitions
├── data/
│   ├── model/                 Unified schema: Manga, Chapter, PageImage, SearchFilters, Genres
│   ├── scraper/               One adapter per source (plugin pattern)
│   │   ├── BaseScraper.kt       Networking, throttling, retries, Cloudflare detection, parsing helpers
│   │   ├── SourceRegistry.kt    The list of adapters; add a source here
│   │   ├── MangapillScraper.kt  HTML (stable)
│   │   ├── MangaDexScraper.kt   Official JSON API (stable)
│   │   ├── ComickScraper.kt     JSON API (experimental)
│   │   ├── MangaKakalotScraper.kt  HTML (experimental)
│   │   └── MangaThemesiaScraper.kt WordPress Themesia/Madara sites: Asura, Reaper, Flame, ManhuaPlus (experimental)
│   ├── repository/            Aggregation, caching, recommendations, history, bookmarks
│   ├── local/                 Room database (offline metadata, history, library)
│   └── prefs/                 Enabled sources, recent searches
└── ui/
    ├── components/glass/      Liquid Glass design system (liquidGlass modifier, aurora, chips, buttons)
    ├── components/manga/      Interactive Intro Card (quick-peek sheet)
    ├── navigation/            Floating glass tab bar / tablet rail, routes
    └── screens/               home, details, reader, library, history, settings
```

## Data flow

```
 UI (HomeState / screens)
   │  searchStream(filters, source?)       ── Flow<SourceResults>, one emission per source that answers
   ▼
 MangaRepository
   │  • picks targets: explicit source, or every *enabled* source whose supports(filters) is true
   │  • skips sources that failed in the last 3 min (all-sources mode only)
   │  • per-source time budget (10 s all-sources / 25 s single) — cancels the HTTP call
   │  • round-robin merge, cross-source de-dupe by normalised title
   │  • in-memory TTL cache + Room persistence of every title seen
   ▼
 BaseScraper subclasses  ──►  site HTML / JSON  ──►  unified Manga / Chapter / PageImage
```

Images are loaded by Coil with the Referer each source's CDN expects (`BaseScraper.imageReferer`).

## Adding a source

1. Subclass `BaseScraper` (or `MangaThemesiaScraper` for WordPress sites).
2. Implement search / popular / latest / details / chapters / pages, and `supports(filters)` for the
   filters the site really honours.
3. Register it in `SourceRegistry`. Mark it `isExperimental = true` until it is verified on a device.

## Recommendations

* **Continue reading**: reading history, newest first.
* **Because you read X**: genre affinity weighted by recency (`1 / (1 + rank)`), then a genre search on
  the enabled sources, excluding titles already read.
* **Trending today** / **Latest updates**: each source's popular and latest lists, merged.
* **Recently updated favorites**: library titles that appear in the latest-updates feed.
* **Hidden gems**: best-rated recent titles (MangaDex `order[rating]` + `createdAtSince`, Comick `sort=rating`).

## Database schema (Room, `mangapill_db`)

| Table | Key | Columns |
|---|---|---|
| `mangas` | `id` = `"<sourceId>:<sourceMangaId>"` | title, slug, coverImage, synopsis, status, rating, genresCsv, sourceId, latestChapterNumber, cachedAt |
| `chapters` | `id` | mangaId, chapterNumber, title, releaseDate, url |
| `reading_history` | `mangaId` (same key as `mangas.id`) | chapterId, chapterNumber, lastReadPage, scrollPercentage, lastReadAt |
| `bookmarks` | `mangaId` | status, addedAt |

Source mapping is part of the key, so the same title from two sources is two rows.

## Reader

* Webtoon mode: zero-gap vertical `LazyColumn`. Paged mode: `HorizontalPager`.
* Progress (page + percentage) is written to Room on every page change and restored when reopening.
* The next 4 pages are pre-fetched; near the end of a chapter the whole next chapter is downloaded into
  Coil's disk cache so it opens instantly and survives a poor connection.
