package io.github.neatbrowser.search.core

data class HistoryEntry(
    val url: String,
    val title: String,
    val visits: Int,
    /** Milliseconds since the epoch. */
    val lastVisit: Long,
)

/** Visited pages, most recent first, one entry per address. Immutable. */
data class History(val entries: List<HistoryEntry> = emptyList()) {

    /** Notes a visit. A blank [title] keeps the title already known. */
    fun record(url: String, title: String, now: Long): History {
        if (!UrlUtils.isHttp(url)) return this
        val key = UrlUtils.normalize(url)
        val old = entries.firstOrNull { UrlUtils.normalize(it.url) == key }
        val entry = HistoryEntry(
            url = url.substringBefore('#'),
            title = title.trim().ifEmpty { old?.title.orEmpty() },
            visits = (old?.visits ?: 0) + 1,
            lastVisit = now,
        )
        val rest = if (old == null) entries else entries.filter { it !== old }
        return History((listOf(entry) + rest).take(MAX_ENTRIES))
    }

    /** The page's title became known after the visit was noted. */
    fun updateTitle(url: String, title: String): History {
        val key = UrlUtils.normalize(url)
        val clean = title.trim()
        if (clean.isEmpty()) return this
        var changed = false
        val updated = entries.map {
            if (!changed && UrlUtils.normalize(it.url) == key && it.title != clean) {
                changed = true
                it.copy(title = clean)
            } else {
                it
            }
        }
        return if (changed) History(updated) else this
    }

    fun remove(url: String): History {
        val key = UrlUtils.normalize(url)
        return History(entries.filter { UrlUtils.normalize(it.url) != key })
    }

    fun clear(): History = History()

    fun recent(limit: Int): List<HistoryEntry> = entries.take(limit)

    /**
     * Entries that contain every word of [query] in the title or the address (accents and case
     * ignored). A word at the start of the host ranks first, then one inside the host or at the
     * start of a title word, then the rest; ties go to the more visited, then the more recent.
     */
    fun search(query: String, limit: Int): List<HistoryEntry> {
        val words = TextFold.fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return recent(limit)
        return entries.withIndex()
            .mapNotNull { (index, entry) ->
                val score = score(entry, words) ?: return@mapNotNull null
                Triple(score, entry, index)
            }
            .sortedWith(
                compareByDescending<Triple<Int, HistoryEntry, Int>> { it.first }
                    .thenByDescending { it.second.visits }
                    .thenBy { it.third },
            )
            .take(limit)
            .map { it.second }
    }

    private fun score(entry: HistoryEntry, words: List<String>): Int? {
        val host = TextFold.fold(UrlUtils.displayHost(entry.url))
        val address = TextFold.fold(entry.url.substringAfter("://"))
        val title = TextFold.fold(entry.title)
        val titleWords = title.split(' ', '-', '|', '·', '–', '—').filter { it.isNotEmpty() }
        var total = 0
        for (word in words) {
            total += when {
                host.startsWith(word) -> 4
                host.contains(word) -> 3
                titleWords.any { it.startsWith(word) } -> 3
                title.contains(word) -> 2
                address.contains(word) -> 1
                else -> return null
            }
        }
        return total
    }

    companion object {
        const val MAX_ENTRIES = 2000
    }
}
