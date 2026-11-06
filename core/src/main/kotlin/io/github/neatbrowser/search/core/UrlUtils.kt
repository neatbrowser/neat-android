package io.github.neatbrowser.search.core

object UrlUtils {
    /** Lower-case host of an absolute URL (without port or userinfo), or null. */
    fun host(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        var authority = url.substring(schemeEnd + 3).takeWhile { it != '/' && it != '?' && it != '#' }
        authority = authority.substringAfterLast('@')
        val host = if (authority.startsWith("[")) {
            authority.substringBefore(']') + "]"
        } else {
            authority.substringBefore(':')
        }
        return host.lowercase().ifEmpty { null }
    }

    /** Key for per-site settings: the host without a leading "www.". */
    fun siteKey(url: String): String? = host(url)?.removePrefix("www.")

    /** What to show for a page address: the site key, or the text itself when it has no host. */
    fun displayHost(url: String): String = siteKey(url) ?: url

    fun isHttp(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
