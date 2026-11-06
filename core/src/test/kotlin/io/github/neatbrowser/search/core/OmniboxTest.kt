package io.github.neatbrowser.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniboxTest {
    private fun navigate(input: String) = (Omnibox.resolve(input) as? OmniboxAction.Navigate)?.url
    private fun searched(input: String) = (Omnibox.resolve(input) as? OmniboxAction.Search)

    @Test fun emptyInputDoesNothing() {
        assertNull(Omnibox.resolve(""))
        assertNull(Omnibox.resolve("   "))
    }

    @Test fun bareDomainGetsHttps() {
        assertEquals("https://example.com", navigate("example.com"))
        assertEquals("https://baomoi.com/tin-nong", navigate("  baomoi.com/tin-nong "))
        assertEquals("https://x.y.co.uk/path?q=1#top", navigate("x.y.co.uk/path?q=1#top"))
    }

    @Test fun explicitHttpAndHttpsAreKept() {
        assertEquals("https://a.b/c?d=1", navigate("https://a.b/c?d=1"))
        assertEquals("http://intranet/wiki", navigate("http://intranet/wiki"))
        assertEquals("HTTP://Example.com", navigate("HTTP://Example.com"))
    }

    @Test fun localAddressesUseHttp() {
        assertEquals("http://localhost:3000", navigate("localhost:3000"))
        assertEquals("http://localhost", navigate("localhost"))
        assertEquals("http://192.168.1.1/admin", navigate("192.168.1.1/admin"))
        assertEquals("http://[::1]:8080", navigate("[::1]:8080"))
        assertEquals("http://printer.local", navigate("printer.local"))
    }

    @Test fun internationalDomainsAreAddresses() {
        assertEquals("https://báo.vn", navigate("báo.vn"))
        // decomposed (NFD) input is normalised
        assertEquals("https://báo.vn", navigate("báo.vn"))
    }

    @Test fun plainWordsAreSearches() {
        assertTrue(searched("chrome") != null)
        val s = searched("báo mới")!!
        assertEquals("báo mới", s.query)
        assertEquals("https://www.google.com/search?q=b%C3%A1o+m%E1%BB%9Bi", s.url)
    }

    @Test fun queryIsEncoded() {
        assertEquals("https://www.google.com/search?q=c%2B%2B+%26+kotlin", searched("c++ & kotlin")!!.url)
    }

    @Test fun fileLikeNamesAreSearches() {
        assertTrue(searched("node.js") != null)
        assertTrue(searched("report.pdf") != null)
        assertTrue(searched("node.js tutorial") != null)
        // but with a path or port it is an address
        assertEquals("https://node.js/docs", navigate("node.js/docs"))
    }

    @Test fun malformedHostsAreSearches() {
        assertTrue(searched("a..b.com") != null)
        assertTrue(searched("-bad.com") != null)
        assertTrue(searched("bad-.com") != null)
        assertTrue(searched("exa mple.com") != null)
        assertTrue(searched("192.168.1.300") != null)
        assertTrue(searched("v1.2") != null)
        assertTrue(searched("example.com:99999") != null)
        assertTrue(searched("example.com:") != null)
        assertTrue(searched("http://") != null)
        assertTrue(searched("e.g.") != null)
    }

    @Test fun otherSchemesAreNeverOpenedFromTheBox() {
        for (text in listOf(
            "javascript:alert(1)", "javascript:1", "data:text/html,<b>x</b>", "file:///etc/passwd",
            "intent://scan/#Intent;scheme=zxing;end", "mailto:a@b.com", "content://x/y", "about:blank",
        )) {
            val a = Omnibox.resolve(text)
            assertTrue("$text must be a search", a is OmniboxAction.Search)
        }
    }

    @Test fun userinfoIsNotTreatedAsAnAddress() {
        assertTrue(searched("user@example.com") != null)
        assertTrue(searched("https://google.com@evil.com/") != null)
    }

    @Test fun customEngine() {
        val e = SearchEngine("DDG", "https://duckduckgo.com/?q=%s")
        val a = Omnibox.resolve("neat browser", e) as OmniboxAction.Search
        assertEquals("https://duckduckgo.com/?q=neat+browser", a.url)
    }

    @Test(expected = IllegalArgumentException::class)
    fun engineTemplateNeedsPlaceholder() {
        SearchEngine("bad", "https://example.com/")
    }
}
