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

    /**
     * `scheme://host[:port]` of an http(s) address, host in lower case, or null. An address with a user name or
     * password gives null: nothing is ever fetched from it on the user's behalf.
     */
    fun origin(url: String): String? {
        if (!isHttp(url)) return null
        val parts = split(url) ?: return null
        return parts.scheme + "://" + parts.authority.lowercase()
    }

    /**
     * Resolves [ref], an `href` as written in a page, against [base] the way a browser does: absolute addresses,
     * `//host/path`, `/path`, `?query`, `path`, `./` and `../` are all understood, tabs and line breaks inside
     * the text are ignored, `\` counts as `/` in the path, spaces become `%20` and the fragment is dropped.
     *
     * Only http and https come out. A [ref] of any other scheme (data:, javascript:, ftp: ...), an address with a
     * user name or password, a malformed host, or a [base] that is not http(s) all give null.
     */
    fun resolve(base: String, ref: String): String? {
        val baseParts = split(base) ?: return null
        if (!isHttp(base)) return null
        val text = ref.trim { it <= ' ' }.filter { it != '\t' && it != '\n' && it != '\r' }
        if (text.isEmpty()) return build(baseParts, baseParts.path, baseParts.query)

        val absolute = text.startsWith("//") || SCHEME.containsMatchIn(text)
        if (absolute) {
            val full = if (text.startsWith("//")) baseParts.scheme + ":" + text else text
            if (!isHttp(full)) return null
            val parts = split(fixSlashes(full)) ?: return null
            return build(parts, removeDots(parts.path), parts.query)
        }

        val noFragment = text.substringBefore('#')
        if (noFragment.isEmpty()) return build(baseParts, baseParts.path, baseParts.query) // "#top" alone
        val queryAt = noFragment.indexOf('?')
        val refPath = fixSlashes(if (queryAt < 0) noFragment else noFragment.substring(0, queryAt))
        val refQuery = if (queryAt < 0) null else noFragment.substring(queryAt + 1)

        return when {
            refPath.isEmpty() -> build(baseParts, baseParts.path, refQuery) // "?x=1" alone
            refPath.startsWith("/") -> build(baseParts, removeDots(refPath), refQuery)
            else -> {
                val directory = baseParts.path.substringBeforeLast('/', "")
                build(baseParts, removeDots("$directory/$refPath"), refQuery)
            }
        }
    }

    private class Parts(val scheme: String, val authority: String, val path: String, val query: String?)

    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    /** Splits an absolute address without its fragment; null when the host part is not usable. */
    private fun split(url: String): Parts? {
        val noFragment = url.substringBefore('#')
        val schemeEnd = noFragment.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = noFragment.substring(0, schemeEnd).lowercase()
        val rest = noFragment.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        if (authority.contains('@')) return null
        if (authority.any { it <= ' ' || it == '\\' || it in "\"<>^`{|}" }) return null
        if (host("$scheme://$authority") == null) return null
        val tail = rest.substring(authorityEnd)
        val queryAt = tail.indexOf('?')
        val path = if (queryAt < 0) tail else tail.substring(0, queryAt)
        val query = if (queryAt < 0) null else tail.substring(queryAt + 1)
        return Parts(scheme, authority, path, query)
    }

    private fun build(parts: Parts, path: String, query: String?): String {
        val safePath = path.ifEmpty { "/" }.replace(" ", "%20")
        val safeQuery = query?.replace(" ", "%20")
        return parts.scheme + "://" + parts.authority + safePath + (if (safeQuery != null) "?$safeQuery" else "")
    }

    /** Backslashes in the path part (before any `?`) count as slashes, as in browsers. */
    private fun fixSlashes(text: String): String {
        val end = text.indexOf('?').let { if (it < 0) text.length else it }
        return text.substring(0, end).replace('\\', '/') + text.substring(end)
    }

    /** RFC 3986 5.2.4 for a path that starts with `/`: `.` and `..` segments are folded away. */
    private fun removeDots(path: String): String {
        if (!path.contains('.')) return path
        val kept = ArrayList<String>()
        val segments = path.split('/')
        for ((index, segment) in segments.withIndex()) {
            val last = index == segments.lastIndex
            when (segment) {
                "." -> if (last) kept.add("")
                ".." -> {
                    if (kept.size > 1) kept.removeAt(kept.lastIndex)
                    if (last) kept.add("")
                }
                else -> kept.add(segment)
            }
        }
        return kept.joinToString("/")
    }
}
