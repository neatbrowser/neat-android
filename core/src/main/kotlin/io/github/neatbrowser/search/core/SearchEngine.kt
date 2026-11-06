package io.github.neatbrowser.search.core

import java.net.URLEncoder

/** A search engine described by a URL template; `%s` is replaced by the URL-encoded query. */
data class SearchEngine(val name: String, val template: String) {
    init {
        require(template.contains("%s")) { "template must contain %s" }
    }

    fun searchUrl(query: String): String =
        template.replace("%s", URLEncoder.encode(query, "UTF-8"))

    companion object {
        val Google = SearchEngine("Google", "https://www.google.com/search?q=%s")
    }
}
