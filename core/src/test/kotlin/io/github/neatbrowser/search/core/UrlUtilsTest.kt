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
}
