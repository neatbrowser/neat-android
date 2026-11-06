package io.github.neatbrowser.search.core

import java.text.Normalizer

/** What to do with the text typed into the search panel. */
sealed interface OmniboxAction {
    val url: String

    /** The text is a web address; open it. */
    data class Navigate(override val url: String) : OmniboxAction

    /** The text is a query; open the search engine's results. */
    data class Search(val query: String, override val url: String) : OmniboxAction
}

/**
 * Decides whether typed text is an address or a search.
 *
 * Only http and https can be opened from the box. Anything that looks like another scheme
 * (`javascript:`, `data:`, `file:`, `intent:`, `mailto:` ...) is searched for instead, so
 * pasted text can never run code or hand the page to another app.
 */
object Omnibox {
    private val labelRegex = Regex("^[\\p{L}\\p{N}]([\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?$")
    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val hostPortRegex = Regex("^[^/?#:]+:\\d{1,5}([/?#].*)?$")
    private val ipv4Regex = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val ipv6Regex = Regex("^\\[[0-9a-fA-F:.]+]$")

    /** `name.ext` with these endings is far more likely a file or a topic than a site. */
    private val fileLikeSuffixes = setOf(
        "js", "ts", "rb", "kt", "cs", "cpp", "java", "json", "xml", "txt", "exe", "png", "jpg",
        "jpeg", "gif", "pdf", "doc", "docx", "css", "html", "htm", "bat", "dll", "apk", "zip",
    )

    fun resolve(input: String, engine: SearchEngine = SearchEngine.Google): OmniboxAction? {
        val text = input.trim()
        if (text.isEmpty()) return null
        directUrl(text)?.let { return OmniboxAction.Navigate(it) }
        return OmniboxAction.Search(text, engine.searchUrl(text))
    }

    /** The address to open for [input], or null when it should be searched for. */
    fun directUrl(input: String): String? {
        val text = Normalizer.normalize(input.trim(), Normalizer.Form.NFC)
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null

        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            val rest = text.substring(text.indexOf("://") + 3)
            val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
            val parsed = parseAuthority(authority) ?: return null
            return if (isValidHost(parsed.first, requireDot = false, strictTld = false)) text else null
        }

        // Another scheme: not something to open from the box. "host:port" is not a scheme.
        if (schemeRegex.containsMatchIn(text) && !hostPortRegex.matches(text)) return null

        val authority = text.takeWhile { it != '/' && it != '?' && it != '#' }
        val hasPathOrQuery = authority.length != text.length
        val (host, port) = parseAuthority(authority) ?: return null
        if (!isValidHost(host, requireDot = true, strictTld = true)) return null

        val suffix = host.substringAfterLast('.').lowercase()
        if (!hasPathOrQuery && port == null && !isIp(host) && suffix in fileLikeSuffixes) return null

        val scheme = if (isLocalHost(host)) "http" else "https"
        return "$scheme://$text"
    }

    /** Splits `host[:port]`. Null when the port is not a number up to 65535 or userinfo is present. */
    private fun parseAuthority(authority: String): Pair<String, Int?>? {
        if (authority.isEmpty() || authority.contains('@')) return null
        val host: String
        val portText: String?
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            host = authority.substring(0, close + 1)
            val after = authority.substring(close + 1)
            portText = when {
                after.isEmpty() -> null
                after.startsWith(":") -> after.substring(1)
                else -> return null
            }
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon < 0) {
                host = authority
                portText = null
            } else {
                host = authority.substring(0, colon)
                portText = authority.substring(colon + 1)
            }
        }
        if (portText == null) return host to null
        if (portText.isEmpty() || portText.length > 5 || !portText.all { it in '0'..'9' }) return null
        val port = portText.toInt()
        if (port > 65535) return null
        return host to port
    }

    /** [strictTld]: for text typed without a scheme the last label must look like a real TLD. */
    private fun isValidHost(host: String, requireDot: Boolean, strictTld: Boolean): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        if (host.startsWith("[")) return ipv6Regex.matches(host)
        if (host.equals("localhost", ignoreCase = true)) return true
        if (isIp(host)) return true
        val labels = host.split('.')
        if (requireDot && labels.size < 2) return false
        if (!labels.all { labelRegex.matches(it) }) return false
        if (strictTld && labels.size >= 2) {
            val tld = labels.last().lowercase()
            val ok = tld.startsWith("xn--") || (tld.length >= 2 && tld.all { it.isLetter() })
            if (!ok) return false
        }
        return true
    }

    private fun isIp(host: String): Boolean {
        if (host.startsWith("[")) return ipv6Regex.matches(host)
        val m = ipv4Regex.matchEntire(host) ?: return false
        return m.groupValues.drop(1).all { it.toInt() in 0..255 }
    }

    private fun isLocalHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || isIp(h)
    }
}
