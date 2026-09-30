package com.mangapill.app.data.opds

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI

/*
 * Protocol-level model of an OPDS catalog. Both OPDS 1.x (Atom XML) and OPDS 2.0 (JSON) are parsed
 * into the same shape, so the provider never has to care which one a server speaks.
 *
 * Specs: https://specs.opds.io/opds-1.2, https://drafts.opds.io/opds-2.0,
 * Page Streaming Extension: https://github.com/anansi-project/opds-pse
 */

data class OpdsLink(
    /** Always absolute. May be a template containing {pageNumber}, {maxWidth} or {searchTerms}. */
    val href: String,
    val rel: String = "",
    val type: String = "",
    val title: String = "",
    /** Page Streaming Extension: number of pages (pse:count). */
    val pageCount: Int? = null,
    /** OPDS 2.0 "templated": true. */
    val templated: Boolean = false
) {
    val isImage: Boolean get() = rel.startsWith(REL_IMAGE) || rel == "x-stanza-cover-image" || rel == "x-stanza-cover-image-thumbnail"
    val isThumbnail: Boolean get() = rel == REL_THUMBNAIL || rel == "x-stanza-cover-image-thumbnail"
    val isAcquisition: Boolean get() = rel.startsWith(REL_ACQUISITION)
    val isPageStream: Boolean get() = rel == REL_PSE && pageCount != null && pageCount > 0

    /** A Readium Web Publication / DiViNa manifest: a JSON list of page images. */
    val isManifest: Boolean
        get() = type.startsWith(TYPE_DIVINA) || type.startsWith(TYPE_WEBPUB)

    /** Points at another catalog feed (a series, a library, a category). */
    val isFeed: Boolean
        get() = !isImage && !isAcquisition && rel != REL_PSE && rel != "search" &&
            ((type.startsWith(TYPE_ATOM) && !type.contains("type=entry")) || type.startsWith(TYPE_OPDS2_FEED))

    companion object {
        const val REL_IMAGE = "http://opds-spec.org/image"
        const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"
        const val REL_ACQUISITION = "http://opds-spec.org/acquisition"
        const val REL_PSE = "http://vaemendis.net/opds-pse/stream"
        const val REL_SORT_NEW = "http://opds-spec.org/sort/new"
        const val REL_SORT_POPULAR = "http://opds-spec.org/sort/popular"

        const val TYPE_ATOM = "application/atom+xml"
        const val TYPE_OPDS2_FEED = "application/opds+json"
        const val TYPE_OPENSEARCH = "application/opensearchdescription+xml"
        const val TYPE_DIVINA = "application/divina+json"
        const val TYPE_WEBPUB = "application/webpub+json"
    }
}

data class OpdsEntry(
    val id: String,
    val title: String,
    val summary: String = "",
    val authors: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val links: List<OpdsLink> = emptyList()
) {
    val feedLink: OpdsLink? get() = links.firstOrNull { it.isFeed }
    val pageStream: OpdsLink? get() = links.firstOrNull { it.isPageStream }
    val manifest: OpdsLink? get() = links.firstOrNull { it.isManifest }

    /** The entry can be read page by page without downloading an archive. */
    val isReadable: Boolean get() = pageStream != null || manifest != null

    val cover: String
        get() = (links.firstOrNull { it.isThumbnail } ?: links.firstOrNull { it.isImage })?.href.orEmpty()
}

data class OpdsFeed(
    val title: String,
    val subtitle: String = "",
    val links: List<OpdsLink> = emptyList(),
    val entries: List<OpdsEntry> = emptyList()
) {
    fun link(rel: String): OpdsLink? = links.firstOrNull { it.rel == rel }

    val next: OpdsLink? get() = link("next")
}

object OpdsParser {

    /** Parses an OPDS 1.x (Atom) or OPDS 2.0 (JSON) feed fetched from [baseUrl]. */
    fun parseFeed(body: String, baseUrl: String): OpdsFeed =
        if (looksLikeJson(body)) parseJsonFeed(JSONObject(body), baseUrl) else parseAtomFeed(body, baseUrl)

    /**
     * Returns the Atom search template ({searchTerms}) of an OpenSearch description, or null.
     * Prefers a URL that returns an OPDS/Atom feed over one that returns HTML.
     */
    fun parseOpenSearch(body: String, baseUrl: String): String? {
        val doc = Jsoup.parse(body, baseUrl, Parser.xmlParser())
        val urls = doc.allElements.toList().filter { localName(it) == "url" && it.hasAttr("template") }
        val best = urls.firstOrNull { it.attr("type").startsWith(OpdsLink.TYPE_ATOM) }
            ?: urls.firstOrNull { it.attr("type").contains("opds") }
            ?: urls.firstOrNull()
            ?: return null
        return resolve(baseUrl, best.attr("template"))
    }

    /** Page image URLs of a Readium Web Publication / DiViNa manifest, in reading order. */
    fun parseManifestPages(body: String, baseUrl: String): List<String> {
        val json = JSONObject(body)
        val order = json.optJSONArray("readingOrder") ?: json.optJSONArray("spine") ?: return emptyList()
        return (0 until order.length()).mapNotNull { i ->
            val item = order.optJSONObject(i) ?: return@mapNotNull null
            item.optString("href").takeIf { it.isNotBlank() }?.let { resolve(baseUrl, it) }
        }
    }

    /** Expands a Page Streaming Extension template for the zero-based page [index]. */
    fun pageUrl(template: String, index: Int, maxWidth: Int = 2000): String =
        template.replace("{pageNumber}", index.toString()).replace("{maxWidth}", maxWidth.toString())

    // ---- OPDS 1.x ---------------------------------------------------------------------------------

    private fun parseAtomFeed(body: String, baseUrl: String): OpdsFeed {
        val doc = Jsoup.parse(body, baseUrl, Parser.xmlParser())
        val root = doc.children().firstOrNull { localName(it) == "feed" || localName(it) == "entry" }
            ?: throw IllegalArgumentException("Not an OPDS feed")

        // A lone <entry> document is a feed of one.
        if (localName(root) == "entry") {
            val entry = parseAtomEntry(root, baseUrl)
            return OpdsFeed(entry.title, entry.summary, emptyList(), listOf(entry))
        }

        return OpdsFeed(
            title = childText(root, "title"),
            subtitle = childText(root, "subtitle"),
            links = root.children().toList().filter { localName(it) == "link" }.mapNotNull { atomLink(it, baseUrl) },
            entries = root.children().toList().filter { localName(it) == "entry" }.map { parseAtomEntry(it, baseUrl) }
        )
    }

    private fun parseAtomEntry(el: Element, baseUrl: String): OpdsEntry {
        val summary = childText(el, "summary").ifBlank { childText(el, "content") }
        return OpdsEntry(
            id = childText(el, "id"),
            title = childText(el, "title").ifBlank { "Untitled" },
            summary = if (summary.contains('<')) Jsoup.parse(summary).text() else summary,
            authors = el.children().toList().filter { localName(it) == "author" }.map { childText(it, "name") }.filter { it.isNotBlank() },
            categories = el.children().toList().filter { localName(it) == "category" }
                .map { it.attr("label").ifBlank { it.attr("term") } }.filter { it.isNotBlank() },
            links = el.children().toList().filter { localName(it) == "link" }.mapNotNull { atomLink(it, baseUrl) }
        )
    }

    private fun atomLink(el: Element, baseUrl: String): OpdsLink? {
        val href = el.attr("href").trim()
        if (href.isEmpty()) return null
        // The PSE namespace prefix is conventionally "pse" but any prefix is legal.
        val count = el.attributes().firstOrNull { it.key == "count" || it.key.endsWith(":count") }?.value?.trim()?.toIntOrNull()
        return OpdsLink(
            href = resolve(baseUrl, href),
            rel = el.attr("rel").trim(),
            type = el.attr("type").trim(),
            title = el.attr("title").trim(),
            pageCount = count
        )
    }

    // ---- OPDS 2.0 ---------------------------------------------------------------------------------

    private fun parseJsonFeed(json: JSONObject, baseUrl: String): OpdsFeed {
        val entries = ArrayList<OpdsEntry>()
        collectJsonEntries(json, baseUrl, entries)
        json.optJSONArray("groups")?.let { groups ->
            for (i in 0 until groups.length()) groups.optJSONObject(i)?.let { collectJsonEntries(it, baseUrl, entries) }
        }
        val metadata = json.optJSONObject("metadata")

        // A bare publication (or manifest) is a feed of one.
        if (entries.isEmpty() && metadata != null && (json.has("readingOrder") || json.has("images"))) {
            entries += parseJsonPublication(json, baseUrl)
        }

        return OpdsFeed(
            title = metadata?.optString("title").orEmpty(),
            subtitle = metadata?.optString("subtitle").orEmpty(),
            links = jsonLinks(json.optJSONArray("links"), baseUrl),
            entries = entries.distinctBy { it.id }
        )
    }

    private fun collectJsonEntries(json: JSONObject, baseUrl: String, out: MutableList<OpdsEntry>) {
        json.optJSONArray("navigation")?.let { nav ->
            for (i in 0 until nav.length()) {
                val obj = nav.optJSONObject(i) ?: continue
                val link = jsonLink(obj, baseUrl) ?: continue
                // Navigation links often omit the type; they always lead to another feed.
                val feedLink = if (link.type.isBlank()) link.copy(type = OpdsLink.TYPE_OPDS2_FEED) else link
                out += OpdsEntry(
                    id = feedLink.href,
                    title = link.title.ifBlank { "Untitled" },
                    summary = obj.optJSONObject("properties")?.optString("description").orEmpty(),
                    links = listOf(feedLink)
                )
            }
        }
        json.optJSONArray("publications")?.let { pubs ->
            for (i in 0 until pubs.length()) pubs.optJSONObject(i)?.let { out += parseJsonPublication(it, baseUrl) }
        }
    }

    private fun parseJsonPublication(pub: JSONObject, baseUrl: String): OpdsEntry {
        val metadata = pub.optJSONObject("metadata") ?: JSONObject()
        val links = jsonLinks(pub.optJSONArray("links"), baseUrl).toMutableList()
        pub.optJSONArray("images")?.let { images ->
            // Smallest image first as the thumbnail, largest as the full cover.
            val parsed = (0 until images.length()).mapNotNull { i ->
                val img = images.optJSONObject(i) ?: return@mapNotNull null
                jsonLink(img, baseUrl)?.let { it to img.optInt("width", Int.MAX_VALUE) }
            }.sortedBy { it.second }
            parsed.firstOrNull()?.let { links += it.first.copy(rel = OpdsLink.REL_THUMBNAIL) }
            parsed.lastOrNull()?.let { links += it.first.copy(rel = OpdsLink.REL_IMAGE) }
        }
        // A publication that carries its own readingOrder is its own manifest.
        if (pub.has("readingOrder") && links.none { it.isManifest }) {
            links.firstOrNull { it.rel == "self" }?.let { links += it.copy(type = OpdsLink.TYPE_DIVINA) }
        }
        val title = metadata.optString("title").ifBlank { "Untitled" }
        return OpdsEntry(
            id = metadata.optString("identifier").ifBlank { links.firstOrNull()?.href ?: title },
            title = title,
            summary = metadata.optString("description").let { if (it.contains('<')) Jsoup.parse(it).text() else it },
            authors = namesOf(metadata.opt("author")),
            categories = namesOf(metadata.opt("subject")),
            links = links
        )
    }

    private fun jsonLinks(array: JSONArray?, baseUrl: String): List<OpdsLink> =
        if (array == null) emptyList()
        else (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { jsonLink(it, baseUrl) } }

    private fun jsonLink(obj: JSONObject, baseUrl: String): OpdsLink? {
        val href = obj.optString("href").trim()
        if (href.isEmpty()) return null
        val rel = when (val r = obj.opt("rel")) {
            is JSONArray -> if (r.length() > 0) r.optString(0) else ""
            is String -> r
            else -> ""
        }
        val count = obj.optJSONObject("properties")?.optInt("numberOfItems", -1)?.takeIf { it > 0 }
        return OpdsLink(
            href = resolve(baseUrl, href),
            rel = rel,
            type = obj.optString("type"),
            title = obj.optString("title"),
            pageCount = if (rel == OpdsLink.REL_PSE) count else null,
            templated = obj.optBoolean("templated", false)
        )
    }

    /** OPDS 2.0 contributors/subjects: a string, an object with "name", or an array of either. */
    private fun namesOf(value: Any?): List<String> = when (value) {
        is String -> listOf(value)
        is JSONObject -> listOfNotNull(value.optString("name").takeIf { it.isNotBlank() })
        is JSONArray -> (0 until value.length()).flatMap { namesOf(value.opt(it)) }
        else -> emptyList()
    }.filter { it.isNotBlank() }

    // ---- Helpers ----------------------------------------------------------------------------------

    private fun looksLikeJson(body: String): Boolean = body.trimStart().startsWith("{")

    private fun localName(el: Element): String = el.tagName().substringAfter(':').lowercase()

    private fun childText(el: Element, name: String): String =
        el.children().firstOrNull { localName(it) == name }?.text()?.trim().orEmpty()

    /**
     * Resolves [href] against [base]. Braces of URI templates are not legal in a java.net.URI, so they
     * are protected during resolution and restored afterwards.
     */
    fun resolve(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return runCatching {
            val safe = href.replace("{", "%7B").replace("}", "%7D")
            URI(base).resolve(safe).toString().replace("%7B", "{").replace("%7D", "}")
        }.getOrDefault(href)
    }
}
