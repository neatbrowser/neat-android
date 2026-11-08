package io.github.neatbrowser.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    @Test fun hostIsLowerCaseWithoutPortOrUserinfo() {
        assertEquals("www.google.com", UrlUtils.host("https://www.Google.com:443/search?q=1"))
        assertEquals("evil.com", UrlUtils.host("https://user:pw@evil.com/"))
        assertEquals("[::1]", UrlUtils.host("http://[::1]:8080/x"))
        assertEquals("example.com", UrlUtils.host("https://example.com?x=1"))
    }

    @Test fun noHostForNonHierarchicalUrls() {
        assertNull(UrlUtils.host("about:blank"))
        assertNull(UrlUtils.host("mailto:a@b.c"))
        assertNull(UrlUtils.host("https://"))
        assertNull(UrlUtils.host("example.com"))
    }

    @Test fun siteKeyDropsWww() {
        assertEquals("google.com", UrlUtils.siteKey("https://www.google.com/x"))
        assertEquals("m.baomoi.com", UrlUtils.siteKey("https://m.baomoi.com/"))
    }

    @Test fun displayHostFallsBackToTheText() {
        assertEquals("google.com", UrlUtils.displayHost("https://www.google.com/x"))
        assertEquals("about:blank", UrlUtils.displayHost("about:blank"))
    }

    @Test fun isHttp() {
        assertTrue(UrlUtils.isHttp("https://a.b"))
        assertTrue(UrlUtils.isHttp("HTTP://a.b"))
        assertFalse(UrlUtils.isHttp("javascript:1"))
        assertFalse(UrlUtils.isHttp("intent://x"))
    }

    @Test fun normalizeDropsFragmentRootSlashAndCase() {
        assertEquals("https://a.com", UrlUtils.normalize("https://A.com/"))
        assertEquals("https://a.com", UrlUtils.normalize("HTTPS://a.com#top"))
        assertEquals("https://a.com/x?y=Z", UrlUtils.normalize("https://A.com/x?y=Z#f"))
        assertEquals("https://a.com?q=1", UrlUtils.normalize("https://a.com?q=1"))
        assertEquals("https://User:Pw@a.com/p", UrlUtils.normalize("https://User:Pw@A.com/p"))
        assertEquals("https://a.com/x/", UrlUtils.normalize("https://a.com/x/")) // only the root slash goes
        assertEquals("about:blank", UrlUtils.normalize("about:blank"))
    }

    // ---- origin ----

    @Test fun originIsSchemeAndHostWithPort() {
        assertEquals("https://example.com", UrlUtils.origin("https://example.com/a/b?c#d"))
        assertEquals("http://localhost:8080", UrlUtils.origin("HTTP://LocalHost:8080/x"))
        assertEquals("https://[::1]:3000", UrlUtils.origin("https://[::1]:3000/"))
        assertEquals("https://a.com", UrlUtils.origin("https://a.com?x=1"))
    }

    @Test fun originIsNullForWhatIsNotAPlainWebAddress() {
        assertNull(UrlUtils.origin("about:blank"))
        assertNull(UrlUtils.origin("ftp://a.com/"))
        assertNull(UrlUtils.origin("https://"))
        assertNull(UrlUtils.origin("https://user:pw@a.com/"))
        assertNull(UrlUtils.origin("https://a b.com/"))
    }

    // ---- resolve ----

    private val base = "https://example.com/dir/sub/page.html?q=1#frag"

    @Test fun resolveAbsoluteAddressesAreKeptWithoutFragment() {
        assertEquals("https://other.com/x.png", UrlUtils.resolve(base, "https://other.com/x.png#top"))
        assertEquals("http://other.com/x.png?a=1", UrlUtils.resolve(base, "HTTP://other.com/x.png?a=1"))
        assertEquals("https://other.com/", UrlUtils.resolve(base, "https://other.com"))
    }

    @Test fun resolveProtocolRelativeTakesTheBaseScheme() {
        assertEquals("https://cdn.test/i.ico", UrlUtils.resolve(base, "//cdn.test/i.ico"))
        assertEquals("http://cdn.test/i.ico", UrlUtils.resolve("http://example.com/", "//cdn.test/i.ico"))
        assertNull(UrlUtils.resolve(base, "///i.ico"))
    }

    @Test fun resolveRootRelativeAndRelativePaths() {
        assertEquals("https://example.com/i.png", UrlUtils.resolve(base, "/i.png"))
        assertEquals("https://example.com/dir/sub/i.png", UrlUtils.resolve(base, "i.png"))
        assertEquals("https://example.com/dir/sub/i.png", UrlUtils.resolve(base, "./i.png"))
        assertEquals("https://example.com/dir/i.png", UrlUtils.resolve(base, "../i.png"))
        assertEquals("https://example.com/i.png", UrlUtils.resolve(base, "../../i.png"))
        assertEquals("https://example.com/i.png", UrlUtils.resolve(base, "../../../../i.png")) // cannot climb above the root
        assertEquals("https://example.com/dir/x/i.png", UrlUtils.resolve(base, "../x/./y/../i.png"))
    }

    @Test fun resolveFromABaseWithoutPath() {
        assertEquals("https://example.com/i.png", UrlUtils.resolve("https://example.com", "i.png"))
        assertEquals("https://example.com/i.png", UrlUtils.resolve("https://example.com/", "i.png"))
        assertEquals("https://example.com/", UrlUtils.resolve("https://example.com", ""))
    }

    @Test fun resolveBaseDirectoryIsEverythingUpToTheLastSlash() {
        assertEquals("https://example.com/a/i.png", UrlUtils.resolve("https://example.com/a/b", "i.png"))
        assertEquals("https://example.com/a/b/i.png", UrlUtils.resolve("https://example.com/a/b/", "i.png"))
    }

    @Test fun resolveQueryOnlyAndFragmentOnly() {
        assertEquals("https://example.com/dir/sub/page.html?x=2", UrlUtils.resolve(base, "?x=2"))
        assertEquals("https://example.com/dir/sub/page.html?q=1", UrlUtils.resolve(base, "#other"))
        assertEquals("https://example.com/dir/sub/page.html?q=1", UrlUtils.resolve(base, "   "))
        assertEquals("https://example.com/i.png?v=3", UrlUtils.resolve(base, "/i.png?v=3#x"))
    }

    @Test fun resolveIgnoresTabsAndLineBreaksAndTrimsPadding() {
        assertEquals("https://example.com/ab.png", UrlUtils.resolve(base, "  /a\tb.png\n "))
        assertEquals("https://example.com/a/b.png", UrlUtils.resolve(base, "\n/a/\r\nb.png"))
    }

    @Test fun resolveBackslashesInThePathAreSlashesAndSpacesAreEscaped() {
        assertEquals("https://example.com/a/b.png", UrlUtils.resolve(base, "/a\\b.png"))
        assertEquals("https://example.com/my%20icon.png?a=b%20c", UrlUtils.resolve(base, "/my icon.png?a=b c"))
    }

    @Test fun resolveRefusesEverythingThatIsNotHttp() {
        for (ref in listOf("data:image/png;base64,AAAA", "javascript:alert(1)", "ftp://example.com/f", "file:///etc/passwd", "mailto:a@b.c", "about:blank", "c:\\icon.png", "blob:https://example.com/uuid")) {
            assertNull(ref, UrlUtils.resolve(base, ref))
        }
    }

    @Test fun resolveRefusesUserInfoAndBrokenHosts() {
        assertNull(UrlUtils.resolve(base, "https://user:pw@other.com/i.png"))
        assertNull(UrlUtils.resolve(base, "https://exa mple.com/i.png"))
        assertNull(UrlUtils.resolve(base, "https://:80/i.png"))
        assertNull(UrlUtils.resolve(base, "//"))
    }

    @Test fun resolveNeedsAWebBase() {
        assertNull(UrlUtils.resolve("about:blank", "/i.png"))
        assertNull(UrlUtils.resolve("ftp://example.com/", "/i.png"))
        assertNull(UrlUtils.resolve("example.com", "/i.png"))
        assertNull(UrlUtils.resolve("https://user:pw@example.com/", "/i.png"))
    }
}
