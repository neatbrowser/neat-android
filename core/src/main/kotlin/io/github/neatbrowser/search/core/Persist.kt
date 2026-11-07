package io.github.neatbrowser.search.core

/**
 * What is written to disk and read back. Reading is forgiving (bad rows are skipped) and strict
 * about content: only http and https addresses are accepted, and sizes are capped, so a damaged
 * file can never put anything else in front of the engine. Ghost tabs are never written.
 */
object Persist {
    private const val VERSION = "v1"
    private const val MAX_URL = 8192
    private const val MAX_TITLE = 300
    private const val MAX_TABS = 500

    private fun usable(url: String) = url.length <= MAX_URL && UrlUtils.isHttp(url)

    // ---- tabs ----

    fun encodeTabs(state: TabsState): String {
        val tabs = state.tabs.filter { !it.ghost && usable(it.url) }.takeLast(MAX_TABS)
        val selected = state.selectedNormal?.takeIf { id -> tabs.any { it.id == id } }
        val rows = buildList {
            add(listOf(VERSION))
            add(listOf("next", state.nextId.toString()))
            if (selected != null) add(listOf("sel", selected.toString()))
            for (t in tabs) add(listOf("tab", t.id.toString(), t.url, t.title.take(MAX_TITLE)))
        }
        return Records.encode(rows)
    }

    fun decodeTabs(text: String): TabsState {
        val rows = Records.decode(text)
        if (rows.firstOrNull()?.firstOrNull() != VERSION) return TabsState()
        val tabs = ArrayList<Tab>()
        val seen = HashSet<Long>()
        var next = 1L
        var selected: Long? = null
        for (row in rows.drop(1)) {
            when (row[0]) {
                "next" -> row.getOrNull(1)?.toLongOrNull()?.let { next = it }
                "sel" -> selected = row.getOrNull(1)?.toLongOrNull()
                "tab" -> {
                    val id = row.getOrNull(1)?.toLongOrNull() ?: continue
                    val url = row.getOrNull(2) ?: continue
                    if (id <= 0 || !seen.add(id) || !usable(url) || tabs.size >= MAX_TABS) continue
                    tabs += Tab(id, url, row.getOrElse(3) { "" }.take(MAX_TITLE))
                }
            }
        }
        val highest = tabs.maxOfOrNull { it.id } ?: 0L
        return TabsState(
            tabs = tabs,
            selectedNormal = selected?.takeIf { id -> tabs.any { it.id == id } },
            nextId = maxOf(next, highest + 1, 1L),
        )
    }

    // ---- history ----

    fun encodeHistory(history: History): String {
        val rows = buildList {
            add(listOf(VERSION))
            for (e in history.entries) {
                if (!usable(e.url)) continue
                add(listOf("h", e.url, e.title.take(MAX_TITLE), e.visits.toString(), e.lastVisit.toString()))
            }
        }
        return Records.encode(rows)
    }

    fun decodeHistory(text: String): History {
        val rows = Records.decode(text)
        if (rows.firstOrNull()?.firstOrNull() != VERSION) return History()
        val entries = rows.drop(1).mapNotNull { row ->
            if (row[0] != "h") return@mapNotNull null
            val url = row.getOrNull(1)?.takeIf(::usable) ?: return@mapNotNull null
            HistoryEntry(
                url = url,
                title = row.getOrElse(2) { "" }.take(MAX_TITLE),
                visits = row.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                lastVisit = row.getOrNull(4)?.toLongOrNull() ?: 0L,
            )
        }
        return History(entries.take(History.MAX_ENTRIES))
    }

    // ---- pins ----

    fun encodePins(pins: Pins): String {
        val rows = buildList {
            add(listOf(VERSION))
            for (p in pins.items) if (usable(p.url)) add(listOf("p", p.url, p.title.take(MAX_TITLE)))
        }
        return Records.encode(rows)
    }

    fun decodePins(text: String): Pins {
        val rows = Records.decode(text)
        if (rows.firstOrNull()?.firstOrNull() != VERSION) return Pins()
        var pins = Pins()
        for (row in rows.drop(1)) {
            if (row[0] != "p") continue
            val url = row.getOrNull(1)?.takeIf(::usable) ?: continue
            pins = pins.add(url, row.getOrElse(2) { "" }.take(MAX_TITLE))
        }
        return pins
    }
}
