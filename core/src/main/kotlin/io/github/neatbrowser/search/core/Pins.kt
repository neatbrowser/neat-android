package io.github.neatbrowser.search.core

data class Pin(val url: String, val title: String)

/** Pinned pages in the order they were pinned. Immutable. */
data class Pins(val items: List<Pin> = emptyList()) {

    fun contains(url: String): Boolean {
        val key = UrlUtils.normalize(url)
        return items.any { UrlUtils.normalize(it.url) == key }
    }

    /** Pins the page, or unpins it when it already is. Only web addresses can be pinned. */
    fun toggle(url: String, title: String): Pins =
        if (contains(url)) remove(url) else add(url, title)

    fun add(url: String, title: String): Pins {
        if (!UrlUtils.isHttp(url) || contains(url)) return this
        val shown = title.trim().ifEmpty { UrlUtils.displayHost(url) }
        return Pins((items + Pin(url.substringBefore('#'), shown)).takeLast(MAX_PINS))
    }

    fun remove(url: String): Pins {
        val key = UrlUtils.normalize(url)
        return Pins(items.filter { UrlUtils.normalize(it.url) != key })
    }

    /** All pins when [query] is blank, otherwise those whose title or address contain every word. */
    fun search(query: String): List<Pin> {
        val words = TextFold.fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return items
        return items.filter { pin ->
            val haystack = TextFold.fold(pin.title) + " " + TextFold.fold(pin.url.substringAfter("://"))
            words.all { haystack.contains(it) }
        }
    }

    companion object {
        const val MAX_PINS = 100
    }
}
