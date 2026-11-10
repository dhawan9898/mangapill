package com.mangapill.app.data.repository

import com.mangapill.app.data.model.Manga

/** Lower-case letters and digits only, so "Solo Leveling!" and "solo-leveling" compare equal. */
internal fun normalizeTitle(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }

/**
 * Round-robin merge of the lists several sources returned, so every source is represented near the
 * top, dropping titles another source already provided (compared by [normalizeTitle]).
 */
internal fun interleaveSources(lists: List<List<Manga>>): List<Manga> {
    if (lists.size == 1) return lists[0]
    val out = ArrayList<Manga>()
    val seen = HashSet<String>()
    var i = 0
    while (lists.any { i < it.size }) {
        for (list in lists) {
            val m = list.getOrNull(i) ?: continue
            if (seen.add(normalizeTitle(m.title))) out.add(m)
        }
        i++
    }
    return out
}
