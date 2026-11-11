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
│   ├── opds/                  OPDS protocol: Atom + JSON parser, server config, Basic-auth interceptor
│   ├── scraper/               One adapter per source (plugin pattern)
│   │   ├── BaseScraper.kt       Networking, throttling, retries, Cloudflare detection, parsing helpers
│   │   ├── SourceRegistry.kt    Built-in adapters + the user's OPDS servers; add a source here
│   │   ├── OpdsScraper.kt       Any OPDS 1.2 / 2.0 catalog: Komga, Kavita, Suwayomi, Stump, Codex (stable)
│   │   ├── MangapillScraper.kt  HTML (stable)
│   │   ├── MangaDexScraper.kt   Official JSON API (stable)
│   │   ├── ComickScraper.kt     JSON API (experimental)
│   │   ├── MangaKakalotScraper.kt  HTML (experimental)
│   │   └── MangaThemesiaScraper.kt WordPress Themesia/Madara sites: Asura, Reaper, Flame, ManhuaPlus (experimental)
│   ├── repository/            Aggregation, caching, recommendations, history, bookmarks
│   ├── local/                 Room database (offline metadata, history, library)
│   └── prefs/                 OPDS servers, enabled sources, recent searches
└── ui/
    ├── components/glass/      Liquid Glass design system (liquidGlass modifier, aurora, chips, buttons)
    ├── components/manga/      Interactive Intro Card (quick-peek sheet)
    ├── navigation/            Floating glass tab bar / tablet rail, routes
    └── screens/               home, search, details, reader, library (history + my list), settings
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

## OPDS servers (the stable provider)

Site scrapers break whenever a site changes its HTML or adds Cloudflare. OPDS is an open catalog
standard that comic servers people host themselves speak, so it is the one kind of source that stays
stable. The idea comes from [OpenComic](https://github.com/ollm/OpenComic), which reads comics from
OPDS catalogs. OpenComic is GPL-3.0, so this is an independent implementation written against the
specs, and it copies none of OpenComic's code.

* **Formats**: OPDS 1.2 (Atom XML) and OPDS 2.0 (JSON), parsed into one model (`OpdsFeed`/`OpdsEntry`/`OpdsLink`).
* **Mapping**: a feed of books (a series) is a `Manga`; its readable books are `Chapter`s in feed order
  (numbered 1..n, shown newest first). One nested level (series → volumes → books) is followed, as are
  `rel="next"` pages.
* **Pages**: the [Page Streaming Extension](https://github.com/anansi-project/opds-pse)
  (`pse:count` + a zero-based `{pageNumber}` template), or a Readium/DiViNa manifest's `readingOrder`.
  Books offered only as CBZ/PDF downloads are skipped with a clear error.
* **Lists**: popular/latest use `sort/popular` / `sort/new` links, or the root entries servers name
  "All series" / "Latest series"; search uses OpenSearch `{searchTerms}` or an OPDS 2 `{?query}` template.
* **Auth**: HTTP Basic, added by `OpdsAuthInterceptor` on the shared OkHttp client only when scheme, host
  and port match the server, so covers and pages loaded by Coil authenticate too and credentials never
  reach another host.
* **Ids**: manga `s:<feed url>` (or `b:<chapter id>` for a lone book); chapters `p:<count>:<template>` or `m:<manifest url>`.
* Servers are added in Settings (validated with a test request, removed again if it fails) and enabled
  automatically. Cleartext HTTP is allowed so LAN servers such as `http://192.168.1.10:25600` work.

## Home

Laid out like AniStream and CineStream: wordmark, Categories and Search; a billboard carousel; rails.
The catalogue rails come straight from the MangaDex API (`HomeRail`, `MangaDexScraper.list`), each
loading the first time it scrolls into view and cached for 20 minutes:

* **Billboard**: most followed (`order[followedCount]`); Read opens the title, Info the quick look.
* **Continue reading**: reading history, newest first.
* **Top 10 new this season**: most followed titles created in the last 120 days.
* **Latest updates**, **Top rated**, genre rails (tag ids), **Manhwa** (`originalLanguage[]=ko`),
  **Completed series**. "See all" opens the Search tab with the matching filter.
* **My List**, and **Because you read X** (genre affinity weighted by recency, searched on the sources).

MangaDex titles show their English name when the main title is romanised; the other names are kept
for matching. When a title's own source lists no chapters (MangaDex has none for licensed titles),
the chapter list comes from the same title on another source (ids `m|<source>|<chapter>`), and
those chapters open there.

## Database schema (Room, `mangapill_db`)

| Table | Key | Columns |
|---|---|---|
| `mangas` | `id` = `"<sourceId>:<sourceMangaId>"` | title, slug, coverImage, synopsis, status, rating, genresCsv, sourceId, latestChapterNumber, cachedAt |
| `chapters` | `id` | mangaId, chapterNumber, title, releaseDate, url |
| `reading_history` | `mangaId` (same key as `mangas.id`) | chapterId, chapterNumber, lastReadPage, scrollPercentage, lastReadAt |
| `bookmarks` | `mangaId` | status, addedAt |

Source mapping is part of the key, so the same title from two sources is two rows.

## Reader

* Webtoon mode: zero-gap vertical `LazyColumn`. Paged mode: `HorizontalPager`; right-to-left mode is the same
  pager mirrored. The chosen mode is saved (Settings → Reader) and every chapter opens in it. Paged modes end
  with a "next chapter" page, and tapping the outer thirds turns pages.
* Progress (page + percentage) is written to Room on every page change and restored when reopening.
* The next 4 pages are pre-fetched; near the end of a chapter the whole next chapter is downloaded into
  Coil's disk cache so it opens instantly and survives a poor connection.
