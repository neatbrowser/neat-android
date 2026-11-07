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

    /**
     * Address used to tell "the same page" apart: no fragment, lower-case scheme and host, and no
     * lone "/" path, so `https://A.com/` and `https://a.com#top` are one page.
     */
    fun normalize(url: String): String {
        val noFragment = url.substringBefore('#')
        val schemeEnd = noFragment.indexOf("://")
        if (schemeEnd <= 0) return noFragment
        val scheme = noFragment.substring(0, schemeEnd).lowercase()
        val rest = noFragment.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        val tail = rest.substring(authorityEnd)
        val at = authority.lastIndexOf('@')
        val hostPart = authority.substring(at + 1).lowercase()
        val normalizedAuthority = if (at >= 0) authority.substring(0, at + 1) + hostPart else hostPart
        return "$scheme://$normalizedAuthority${if (tail == "/") "" else tail}"
    }
}
