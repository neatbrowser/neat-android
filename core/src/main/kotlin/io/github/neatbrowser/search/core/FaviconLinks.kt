package io.github.neatbrowser.search.core

import kotlin.math.abs
import kotlin.math.ln

/** One icon a page points to: an absolute http(s) address, its size when the page says so, and where it was found. */
data class FaviconCandidate(
    val url: String,
    /** Longer side in pixels from `sizes="..."` (the one closest to the target when several are listed), or null. */
    val size: Int?,
    /** Came from `rel="apple-touch-icon"`: a square picture without transparency. */
    val appleTouch: Boolean,
    /** Position of the `<link>` in the page; earlier wins a tie. */
    val order: Int,
)

/**
 * Finds the icon links in the top of a page and puts them in the order they should be tried.
 *
 * The scanner is forgiving, like a browser: tag and attribute names in any case, single, double or no
 * quotes, line breaks inside tags, `>` inside a quoted value, `&amp;` in addresses. Comments, `<script>`
 * and `<style>` are skipped, and so is everything from `<body>` on.
 */
object FaviconLinks {
    /** At most this many links from the page are tried, best first, before the `/favicon.ico` fallback. */
    const val MAX_CANDIDATES = 3

    /** What a link without `sizes` counts as: the usual size of a `favicon.ico`. */
    const val UNKNOWN_SIZE = 32

    /**
     * The usable icon links of [html], best first. [documentUrl] is the address the page was read from (it
     * resolves relative links, unless a `<base href>` says otherwise) and [siteUrl] the site the icon is
     * for: a link that leads to another host is dropped, and so are SVG icons (they cannot be decoded),
     * `mask-icon`, and anything that is not http(s).
     *
     * Best means the size closest to [FaviconPolicy.TARGET_SIZE] (twice as big and half as big are equally
     * far; the bigger wins), then a plain icon before an apple-touch-icon, then the one written first.
     */
    fun candidates(html: String, documentUrl: String, siteUrl: String = documentUrl): List<FaviconCandidate> {
        val tags = scan(html)
        val baseHref = tags.firstOrNull { it.name == "base" && !it.attrs["href"].isNullOrBlank() }?.attrs?.get("href")
        val base = baseHref?.let { UrlUtils.resolve(documentUrl, it) } ?: documentUrl

        val found = ArrayList<FaviconCandidate>()
        var order = 0
        for (tag in tags) {
            if (tag.name != "link") continue
            val kind = iconKind(tag.attrs["rel"]) ?: continue
            val href = tag.attrs["href"]
            if (href.isNullOrBlank()) continue
            val url = UrlUtils.resolve(base, href) ?: continue
            if (isSvg(url, tag.attrs["type"])) continue
            if (!FaviconPolicy.sameSite(siteUrl, url)) continue
            found.add(FaviconCandidate(url, bestSize(tag.attrs["sizes"]), kind == Kind.AppleTouch, order++))
        }
        return found
            .sortedWith(
                compareBy<FaviconCandidate> { distance(it.size) }
                    .thenByDescending { it.size ?: UNKNOWN_SIZE }
                    .thenBy { it.appleTouch }
                    .thenBy { it.order },
            )
            .distinctBy { it.url }
    }

    /**
     * The addresses to try for a site, in order: up to [MAX_CANDIDATES] icon links from the page, then
     * `/favicon.ico` at the root of the site. [html] and [documentUrl] are null when the page could not be
     * read; only the fallback is left then.
     */
    fun plan(html: String?, documentUrl: String?, siteUrl: String): List<String> {
        val urls = ArrayList<String>()
        if (html != null && documentUrl != null) {
            for (candidate in candidates(html, documentUrl, siteUrl)) {
                if (urls.size >= MAX_CANDIDATES) break
                urls.add(candidate.url)
            }
        }
        val fallback = fallbackUrl(siteUrl)
        if (fallback != null && fallback !in urls) urls.add(fallback)
        return urls
    }

    /** `/favicon.ico` at the root of the site's own host, or null for an address that is not http(s). */
    fun fallbackUrl(siteUrl: String): String? = UrlUtils.origin(siteUrl)?.let { "$it/favicon.ico" }

    // ---- ranking ----

    /** How far a size is from the target on a log scale, rounded so that equal distances really compare equal. */
    private fun distance(size: Int?): Long {
        val s = (size ?: UNKNOWN_SIZE).coerceAtLeast(1)
        return Math.round(abs(ln(s.toDouble() / FaviconPolicy.TARGET_SIZE)) * 1_000_000.0)
    }

    /** Of the sizes in a `sizes` attribute (`16x16 32x32`, `any`), the one that ranks best, or null. */
    private fun bestSize(sizes: String?): Int? {
        if (sizes.isNullOrBlank()) return null
        return sizes.lowercase()
            .split(' ', '\t', '\n', '\r', '\u000c')
            .mapNotNull { token ->
                val parts = token.split('x')
                if (parts.size != 2) return@mapNotNull null
                val w = parts[0].toIntOrNull() ?: return@mapNotNull null
                val h = parts[1].toIntOrNull() ?: return@mapNotNull null
                if (w <= 0 || h <= 0) null else maxOf(w, h)
            }
            .minWithOrNull(compareBy<Int> { distance(it) }.thenByDescending { it })
    }

    // ---- links ----

    private enum class Kind { Icon, AppleTouch }

    /** What the `rel` value says: an icon, an apple-touch-icon, or nothing we use (`mask-icon`, `stylesheet` ...). */
    private fun iconKind(rel: String?): Kind? {
        if (rel == null) return null
        val tokens = rel.lowercase().split(' ', '\t', '\n', '\r', '\u000c').filter { it.isNotEmpty() }
        if ("mask-icon" in tokens) return null
        return when {
            "icon" in tokens -> Kind.Icon // also "shortcut icon" and "alternate icon"
            "apple-touch-icon" in tokens || "apple-touch-icon-precomposed" in tokens -> Kind.AppleTouch
            else -> null
        }
    }

    private fun isSvg(url: String, type: String?): Boolean {
        if (type != null && type.trim().lowercase().startsWith("image/svg")) return true
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return path.endsWith(".svg") || path.endsWith(".svgz")
    }

    // ---- scanning ----

    private class Tag(val name: String, val attrs: Map<String, String>)

    /** Elements whose content is text, not markup: `<link>` written inside them is not a link. */
    private val RAW_TEXT = setOf("script", "style", "textarea", "title", "template")

    /** The `<link>` and `<base>` tags before `<body>`, in order. */
    private fun scan(html: String): List<Tag> {
        val tags = ArrayList<Tag>()
        val n = html.length
        var i = 0
        while (i < n) {
            val lt = html.indexOf('<', i)
            if (lt < 0 || lt + 1 >= n) break
            val c = html[lt + 1]
            when {
                html.startsWith("<!--", lt) -> {
                    val end = html.indexOf("-->", lt + 4)
                    if (end < 0) break
                    i = end + 3
                }
                c == '!' || c == '?' || c == '/' -> { // doctype, processing instruction, end tag
                    val end = html.indexOf('>', lt + 2)
                    if (end < 0) break
                    i = end + 1
                }
                c.isLetter() -> {
                    var j = lt + 1
                    while (j < n && !html[j].isWhitespace() && html[j] != '>' && html[j] != '/') j++
                    val name = html.substring(lt + 1, j).lowercase()
                    if (name == "body") break // the head is over
                    val read = readAttributes(html, j) ?: break
                    if (name == "link" || name == "base") tags.add(Tag(name, read.first))
                    i = read.second
                    if (name in RAW_TEXT) {
                        val close = html.indexOf("</$name", i, ignoreCase = true)
                        if (close < 0) break
                        i = close
                    }
                }
                else -> i = lt + 1
            }
        }
        return tags
    }

    /**
     * Reads the attributes of a tag from [start] (just after its name) to its `>`. Returns them with the
     * position after the `>`, or null when the text ends first (a tag cut off at the size limit is not a tag).
     * Names are lower case; when a name repeats the first one counts, as in HTML.
     */
    private fun readAttributes(s: String, start: Int): Pair<Map<String, String>, Int>? {
        val attrs = LinkedHashMap<String, String>()
        val n = s.length
        var i = start
        while (i < n) {
            val c = s[i]
            if (c == '>') return attrs to (i + 1)
            if (c.isWhitespace() || c == '/') {
                i++
                continue
            }
            val nameStart = i
            while (i < n && !s[i].isWhitespace() && s[i] != '=' && s[i] != '>' && s[i] != '/') i++
            val name = s.substring(nameStart, i).lowercase()
            while (i < n && s[i].isWhitespace()) i++
            var value = ""
            if (i < n && s[i] == '=') {
                i++
                while (i < n && s[i].isWhitespace()) i++
                if (i < n && (s[i] == '"' || s[i] == '\'')) {
                    val end = s.indexOf(s[i], i + 1)
                    if (end < 0) return null
                    value = s.substring(i + 1, end)
                    i = end + 1
                } else {
                    val valueStart = i
                    while (i < n && !s[i].isWhitespace() && s[i] != '>') i++
                    value = s.substring(valueStart, i)
                }
            }
            if (name.isNotEmpty() && name !in attrs) attrs[name] = unescape(value)
        }
        return null
    }

    /** `&amp;` `&lt;` `&gt;` `&quot;` `&apos;` and numeric references; anything else is left as written. */
    private fun unescape(value: String): String {
        if (!value.contains('&')) return value
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '&') {
                val semicolon = value.indexOf(';', i + 1)
                if (semicolon in (i + 2)..(i + 10)) {
                    val replacement = entity(value.substring(i + 1, semicolon))
                    if (replacement != null) {
                        out.append(replacement)
                        i = semicolon + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun entity(name: String): String? = when {
        name == "amp" -> "&"
        name == "lt" -> "<"
        name == "gt" -> ">"
        name == "quot" -> "\""
        name == "apos" -> "'"
        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let(::codePoint)
        name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::codePoint)
        else -> null
    }

    private fun codePoint(value: Int): String? =
        if (value in 1..0x10FFFF && value !in 0xD800..0xDFFF) String(Character.toChars(value)) else null
}
