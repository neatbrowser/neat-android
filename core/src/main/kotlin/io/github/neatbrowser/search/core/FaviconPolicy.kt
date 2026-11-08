package io.github.neatbrowser.search.core

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The rules for fetching a site's icon. Icons are fetched by the app itself and only from the site
 * they belong to: no favicon service (Google, DuckDuckGo ...) ever sees which sites are visited.
 */
object FaviconPolicy {
    /** Icons are stored at about this many pixels on the longer side; smaller ones are kept as they are. */
    const val TARGET_SIZE = 64

    /** A picture with a shorter side than this is a tracking pixel or junk, not an icon. */
    const val MIN_SIDE = 8

    /** Pictures with a longer side than this are refused before they are decoded. */
    const val MAX_SOURCE_SIDE = 4096

    /** Only this much of a page is read; the icon links are at the top. */
    const val MAX_HTML_BYTES = 128 * 1024

    /** A bigger image file is refused. */
    const val MAX_IMAGE_BYTES = 256 * 1024

    const val MAX_REDIRECTS = 3

    const val TIMEOUT_MS = 5000

    /**
     * Whether [other] is on the same site as [site]: same host, `www.` ignored (the port is not
     * compared). A subdomain is another site, so an icon on a CDN host is not fetched.
     */
    fun sameSite(site: String, other: String): Boolean {
        if (!UrlUtils.isHttp(site) || !UrlUtils.isHttp(other)) return false
        val key = UrlUtils.siteKey(site) ?: return false
        return key == UrlUtils.siteKey(other)
    }

    /**
     * Where a redirect from [current] leads, or null when it must not be followed: no `Location`, or
     * a target that is not http(s) (data:, javascript:, file:, ftp: ...). The caller counts the hops
     * against [MAX_REDIRECTS].
     */
    fun redirectTarget(current: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        return UrlUtils.resolve(current, location)
    }

    /**
     * Name of the file that keeps the icon of [siteKey] in the cache folder: letters, digits, `-` and `.`
     * stay, anything else becomes `_` and four hex digits (so two keys never share a name), a leading
     * `.` is escaped (no hidden files) and very long names are cut and ended with a hash.
     */
    fun fileName(siteKey: String): String {
        val out = StringBuilder()
        for (c in siteKey.lowercase()) {
            val plain = c in 'a'..'z' || c in '0'..'9' || c == '-' || (c == '.' && out.isNotEmpty())
            if (plain) {
                out.append(c)
            } else {
                out.append('_').append(c.code.toString(16).padStart(4, '0'))
            }
        }
        var name = out.toString()
        if (name.length > MAX_NAME_LENGTH) {
            val hash = siteKey.hashCode().toUInt().toString(16)
            name = name.substring(0, CUT_NAME_LENGTH) + "_" + hash
        }
        return "$name.png"
    }

    /**
     * Power of two to decode a [width] x [height] picture with so that it stays at least [target]
     * pixels on its shorter side: decoding a 4000 px icon at full size would be a waste of memory.
     */
    fun sampleSize(width: Int, height: Int, target: Int = TARGET_SIZE): Int {
        val shorter = minOf(width, height)
        var sample = 1
        while (sample < MAX_SAMPLE && shorter / (sample * 2) >= target) sample *= 2
        return sample
    }

    /**
     * The size an icon of [width] x [height] is stored at: shrunk to [target] on the longer side with
     * the same proportions, and left alone when it is not bigger than that.
     */
    fun storedSize(width: Int, height: Int, target: Int = TARGET_SIZE): Pair<Int, Int> {
        val longer = max(width, height)
        if (longer <= target) return width to height
        val scale = target.toFloat() / longer
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    private const val MAX_NAME_LENGTH = 120
    private const val CUT_NAME_LENGTH = 100
    private const val MAX_SAMPLE = 64
}
