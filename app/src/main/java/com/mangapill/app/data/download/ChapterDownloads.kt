package com.mangapill.app.data.download

import android.net.Uri
import com.mangapill.app.data.model.Chapter
import com.mangapill.app.data.model.PageImage
import com.mangapill.app.data.scraper.BaseScraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

sealed interface DownloadState {
    /** [total] is 0 while the page list is still being looked up. */
    data class Running(val done: Int, val total: Int) : DownloadState
    object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Chapters saved for reading offline. Unlike the image cache (which is trimmed and can be cleared),
 * a downloaded chapter stays until it is removed: its pages are files under [root], one folder per
 * chapter, and a chapter counts as downloaded once its [INDEX] file is written (after the last page).
 * The title's chapter list is saved next to them, so the title still opens without a connection.
 */
class ChapterDownloads(private val root: File, private val client: OkHttpClient = BaseScraper.sharedClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    // Two chapters at a time: "download all" must not open hundreds of connections to one site.
    private val slots = Semaphore(2)

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    /** State of every chapter that is downloaded, downloading or failed, by [keyOf]. */
    val states: StateFlow<Map<String, DownloadState>> = _states

    init {
        scope.launch {
            val saved = HashMap<String, DownloadState>()
            root.listFiles()?.forEach { title ->
                title.listFiles()?.forEach { chapter ->
                    if (File(chapter, INDEX).isFile) saved["${title.name}/${chapter.name}"] = DownloadState.Done
                }
            }
            _states.update { saved + it }
        }
    }

    fun keyOf(sourceId: String, mangaId: String, chapterId: String): String =
        "${hash("$sourceId:$mangaId")}/${hash(chapterId)}"

    private fun titleDir(sourceId: String, mangaId: String) = File(root, hash("$sourceId:$mangaId"))

    private fun setState(key: String, state: DownloadState?) =
        _states.update { if (state == null) it - key else it + (key to state) }

    /** The saved pages of a chapter, or null when it is not (completely) downloaded. */
    fun localPages(sourceId: String, mangaId: String, chapterId: String): List<PageImage>? {
        val dir = File(root, keyOf(sourceId, mangaId, chapterId))
        val index = File(dir, INDEX)
        if (!index.isFile) return null
        val count = runCatching { index.readText().trim().toInt() }.getOrNull() ?: return null
        val files = (1..count).map { File(dir, pageName(it)) }
        if (files.isEmpty() || files.any { !it.isFile }) return null
        return files.mapIndexed { i, file -> PageImage(pageNumber = i + 1, imageUrl = Uri.fromFile(file).toString(), referer = null) }
    }

    /**
     * Saves [chapter] in the background. [chapters] is the title's chapter list (kept for offline use)
     * and [loadPages] looks up the chapter's pages. Does nothing when it is saved or on its way.
     */
    fun download(
        sourceId: String,
        mangaId: String,
        chapter: Chapter,
        chapters: List<Chapter>,
        loadPages: suspend () -> List<PageImage>
    ) {
        val key = keyOf(sourceId, mangaId, chapter.id)
        if (_states.value[key] is DownloadState.Done || jobs[key]?.isActive == true) return
        val dir = File(root, key)
        setState(key, DownloadState.Running(0, 0))
        jobs[key] = scope.launch {
            try {
                slots.withPermit {
                    val pages = loadPages()
                    if (pages.isEmpty()) throw IOException("This chapter has no pages")
                    dir.mkdirs()
                    saveChapterList(sourceId, mangaId, chapters)
                    setState(key, DownloadState.Running(0, pages.size))
                    pages.forEachIndexed { i, page ->
                        ensureActive()
                        fetch(page, File(dir, pageName(i + 1)))
                        setState(key, DownloadState.Running(i + 1, pages.size))
                    }
                    File(dir, INDEX).writeText(pages.size.toString())
                    setState(key, DownloadState.Done)
                }
            } catch (e: CancellationException) {
                dir.deleteRecursively()
                setState(key, null)
                throw e
            } catch (e: Exception) {
                dir.deleteRecursively()
                setState(key, DownloadState.Failed(e.message ?: "Download failed"))
                com.mangapill.app.data.ErrorReporter.report(
                    "download", "Chapter download failed: ${e.message ?: e.javaClass.simpleName}", e,
                    mapOf("source" to sourceId, "manga" to mangaId, "chapter" to chapter.chapterNumber)
                )
            }
        }
    }

    /** Removes a saved chapter, or stops it while it is downloading. */
    fun remove(sourceId: String, mangaId: String, chapterId: String) {
        val key = keyOf(sourceId, mangaId, chapterId)
        val running = jobs.remove(key)
        if (running?.isActive == true) {
            running.cancel() // the job deletes its own files
            return
        }
        scope.launch {
            File(root, key).deleteRecursively()
            setState(key, null)
        }
    }

    /** Bytes used by everything saved here. Walks the files: call it off the main thread. */
    fun sizeBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Stops every running download and removes every saved chapter. */
    fun removeAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.launch {
            root.deleteRecursively()
            _states.value = emptyMap()
        }
    }

    private fun fetch(page: PageImage, target: File) {
        val url = page.imageUrl.toHttpUrl()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BaseScraper.imageUserAgent(url.host))
            .apply { page.referer?.let { header("Referer", it) } }
            .build()
        var last: IOException? = null
        repeat(3) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Page ${page.pageNumber}: HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Page ${page.pageNumber} is empty")
                    val part = File(target.parentFile, target.name + ".part")
                    part.outputStream().use { out -> body.byteStream().copyTo(out) }
                    if (part.length() == 0L) throw IOException("Page ${page.pageNumber} is empty")
                    if (!part.renameTo(target)) throw IOException("Could not save page ${page.pageNumber}")
                }
                return
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("Download failed")
    }

    private fun saveChapterList(sourceId: String, mangaId: String, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        val dir = titleDir(sourceId, mangaId)
        dir.mkdirs()
        File(dir, CHAPTERS).writeText(encodeChapters(chapters))
    }

    /** The chapter list saved with this title's downloads, or null when it has none. */
    fun savedChapterList(sourceId: String, mangaId: String): List<Chapter>? {
        val file = File(titleDir(sourceId, mangaId), CHAPTERS)
        if (!file.isFile) return null
        return runCatching { decodeChapters(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val INDEX = "pages"
        private const val CHAPTERS = "chapters.json"

        private fun pageName(number: Int) = "%04d".format(number)

        private fun hash(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

        internal fun encodeChapters(chapters: List<Chapter>): String {
            val out = JSONArray()
            for (c in chapters) {
                val o = JSONObject()
                o.put("id", c.id)
                o.put("mangaId", c.mangaId)
                o.put("number", c.chapterNumber.toDouble())
                if (c.title != null) o.put("title", c.title)
                if (c.releaseDate != null) o.put("date", c.releaseDate)
                o.put("url", c.url)
                out.put(o)
            }
            return out.toString()
        }

        internal fun decodeChapters(json: String): List<Chapter> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Chapter(
                    id = o.getString("id"),
                    mangaId = o.getString("mangaId"),
                    chapterNumber = o.getDouble("number").toFloat(),
                    title = if (o.has("title")) o.getString("title") else null,
                    releaseDate = if (o.has("date")) o.getString("date") else null,
                    url = o.optString("url", "")
                )
            }
        }
    }
}
