package io.github.neatbrowser.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconLinksTest {
    private val page = "https://example.com/blog/post.html"

    private fun urls(html: String, document: String = page, site: String = document): List<String> =
        FaviconLinks.candidates(html, document, site).map { it.url }

    private fun head(vararg links: String) = "<!doctype html><html><head>${links.joinToString("")}</head><body></body></html>"

    // ---- which links count ----

    @Test fun plainIconLink() {
        assertEquals(listOf("https://example.com/favicon.png"), urls(head("<link rel=\"icon\" href=\"/favicon.png\">")))
    }

    @Test fun shortcutIconAlternateIconAndAppleTouchVariantsAreAccepted() {
        val html = head(
            "<link rel=\"shortcut icon\" href=\"/a.ico\">",
            "<link rel=\"alternate icon\" href=\"/b.ico\">",
            "<link rel=\"apple-touch-icon\" href=\"/c.png\">",
            "<link rel=\"apple-touch-icon-precomposed\" href=\"/d.png\">",
        )
        assertEquals(
            setOf("a.ico", "b.ico", "c.png", "d.png"),
            urls(html).map { it.substringAfterLast('/') }.toSet(),
        )
    }

    @Test fun maskIconAndOtherRelsAreIgnored() {
        val html = head(
            "<link rel=\"mask-icon\" href=\"/pin.svg\" color=\"#000\">",
            "<link rel=\"icon mask-icon\" href=\"/both.png\">",
            "<link rel=\"stylesheet\" href=\"/site.css\">",
            "<link rel=\"manifest\" href=\"/site.webmanifest\">",
            "<link href=\"/no-rel.png\">",
        )
        assertTrue(urls(html).isEmpty())
    }

    @Test fun relIsCaseInsensitiveAndMayHaveOddWhitespace() {
        val html = head("<LINK REL=\"Shortcut   ICON\" HREF=\"/x.png\">", "<link rel=\"\n  icon\t\" href=\"/y.png\">")
        assertEquals(setOf("x.png", "y.png"), urls(html).map { it.substringAfterLast('/') }.toSet())
    }

    @Test fun svgIconsAreIgnoredByExtensionByTypeAndWithAQuery() {
        val html = head(
            "<link rel=\"icon\" href=\"/logo.svg\">",
            "<link rel=\"icon\" href=\"/LOGO.SVG?v=2\">",
            "<link rel=\"icon\" href=\"/dyn\" type=\"image/svg+xml\">",
            "<link rel=\"icon\" href=\"/pack.svgz\">",
            "<link rel=\"icon\" href=\"/ok.png\">",
        )
        assertEquals(listOf("https://example.com/ok.png"), urls(html))
    }

    @Test fun linkWithoutHrefOrWithEmptyHrefIsIgnored() {
        assertTrue(urls(head("<link rel=\"icon\">", "<link rel=\"icon\" href=\"\">", "<link rel=\"icon\" href=\"  \">")).isEmpty())
    }

    @Test fun nonHttpSchemesAreIgnored() {
        val html = head(
            "<link rel=\"icon\" href=\"data:image/png;base64,AAAA\">",
            "<link rel=\"icon\" href=\"javascript:alert(1)\">",
            "<link rel=\"icon\" href=\"ftp://example.com/f.ico\">",
            "<link rel=\"icon\" href=\"file:///etc/passwd\">",
        )
        assertTrue(urls(html).isEmpty())
    }

    // ---- quotes, case, odd markup ----

    @Test fun singleDoubleAndNoQuotes() {
        val html = head(
            "<link rel='icon' href='/single.png' sizes='64x64'>",
            "<link rel=\"icon\" href=\"/double.png\" sizes=\"48x48\">",
            "<link rel=icon href=/bare.png sizes=32x32>",
        )
        assertEquals(
            listOf("single.png", "double.png", "bare.png"),
            urls(html).map { it.substringAfterLast('/') },
        )
    }

    @Test fun attributeOrderSelfClosingAndLineBreaksInsideTheTag() {
        val html = head("<link\n  href=\"/x.png\"\n  type=\"image/png\"\n  rel=\"icon\"\n/>")
        assertEquals(listOf("https://example.com/x.png"), urls(html))
    }

    @Test fun greaterThanInsideAQuotedValueDoesNotEndTheTag() {
        val html = head("<link rel=\"icon\" title=\"a > b\" href=\"/after.png\">")
        assertEquals(listOf("https://example.com/after.png"), urls(html))
    }

    @Test fun entitiesInHrefAreDecoded() {
        val html = head("<link rel=\"icon\" href=\"/i.png?a=1&amp;b=2&#38;c=3&#x26;d=4\">")
        assertEquals(listOf("https://example.com/i.png?a=1&b=2&c=3&d=4"), urls(html))
    }

    @Test fun firstOfARepeatedAttributeWins() {
        val html = head("<link rel=\"icon\" href=\"/first.png\" href=\"/second.png\">")
        assertEquals(listOf("https://example.com/first.png"), urls(html))
    }

    @Test fun commentsScriptsStylesAndTitlesAreNotMarkup() {
        val html = """
            <html><head>
            <!-- <link rel="icon" href="/commented.png"> -->
            <script>var s = '<link rel="icon" href="/in-script.png">';</script>
            <style>/* <link rel="icon" href="/in-style.png"> */</style>
            <title>a <link rel="icon" href="/in-title.png"></title>
            <link rel="icon" href="/real.png">
            </head><body></body></html>
        """.trimIndent()
        assertEquals(listOf("https://example.com/real.png"), urls(html))
    }

    @Test fun linksAfterBodyStartAreIgnored() {
        val html = "<html><head><link rel=\"icon\" href=\"/head.png\"></head><body><link rel=\"icon\" href=\"/body.png\"></body></html>"
        assertEquals(listOf("https://example.com/head.png"), urls(html))
    }

    @Test fun pageWithoutHeadOrBodyIsScannedWhole() {
        assertEquals(listOf("https://example.com/f.png"), urls("<link rel=\"icon\" href=\"/f.png\">"))
    }

    @Test fun truncatedInputNeverThrowsAndDropsTheCutTag() {
        val html = "<html><head><link rel=\"icon\" href=\"/ok.png\"><link rel=\"icon\" href=\"/cut.p"
        assertEquals(listOf("https://example.com/ok.png"), urls(html))
        assertTrue(urls("<").isEmpty())
        assertTrue(urls("<link").isEmpty())
        assertTrue(urls("<!-- never closed <link rel=icon href=/x.png>").isEmpty())
        assertTrue(urls("<script>never closed <link rel=icon href=/x.png>").isEmpty())
        assertTrue(urls("").isEmpty())
    }

    // ---- addresses ----

    @Test fun relativeHrefsResolveAgainstThePageDirectory() {
        val html = head(
            "<link rel=\"icon\" href=\"favicon.png\" sizes=\"64x64\">",
            "<link rel=\"icon\" href=\"./dot.png\" sizes=\"48x48\">",
            "<link rel=\"icon\" href=\"../up.png\" sizes=\"40x40\">",
            "<link rel=\"icon\" href=\"/root.png\" sizes=\"32x32\">",
        )
        assertEquals(
            listOf(
                "https://example.com/blog/favicon.png",
                "https://example.com/blog/dot.png",
                "https://example.com/up.png",
                "https://example.com/root.png",
            ),
            urls(html),
        )
    }

    @Test fun protocolRelativeHrefTakesThePageScheme() {
        assertEquals(
            listOf("https://example.com/p.png"),
            urls(head("<link rel=\"icon\" href=\"//example.com/p.png\">")),
        )
        assertEquals(
            listOf("http://example.com/p.png"),
            urls(head("<link rel=\"icon\" href=\"//example.com/p.png\">"), document = "http://example.com/"),
        )
    }

    @Test fun absoluteHrefOnTheSameHostIsKept() {
        assertEquals(
            listOf("https://example.com/abs.png"),
            urls(head("<link rel=\"icon\" href=\"https://example.com/abs.png\">")),
        )
    }

    @Test fun baseHrefChangesWhatRelativeLinksResolveAgainst() {
        val absolute = head("<base href=\"https://example.com/static/\">", "<link rel=\"icon\" href=\"i.png\">")
        assertEquals(listOf("https://example.com/static/i.png"), urls(absolute))

        val relative = head("<link rel=\"icon\" href=\"i.png\">", "<base href=\"/assets/\">") // a base after the link still counts
        assertEquals(listOf("https://example.com/assets/i.png"), urls(relative))

        val protocolRelative = head("<base href=\"//example.com/cdn/\">", "<link rel=\"icon\" href=\"i.png\">")
        assertEquals(listOf("https://example.com/cdn/i.png"), urls(protocolRelative))
    }

    @Test fun baseHrefOnAnotherHostPushesTheLinkOutOfTheSite() {
        val html = head("<base href=\"https://evil.test/\">", "<link rel=\"icon\" href=\"i.png\">")
        assertTrue(urls(html).isEmpty())
    }

    @Test fun nonHttpBaseIsIgnored() {
        val html = head("<base href=\"data:text/plain,x\">", "<link rel=\"icon\" href=\"i.png\">")
        assertEquals(listOf("https://example.com/blog/i.png"), urls(html))
    }

    // ---- only the site's own host ----

    @Test fun otherHostsAreDropped() {
        val html = head(
            "<link rel=\"icon\" href=\"https://cdn.example.net/i.png\">",
            "<link rel=\"icon\" href=\"//static.example.com/i.png\">", // a subdomain is another host
            "<link rel=\"icon\" href=\"https://www.google.com/s2/favicons?domain=example.com\">",
            "<link rel=\"icon\" href=\"https://example.com.evil.test/i.png\">",
            "<link rel=\"icon\" href=\"https://user:pw@example.com/i.png\">",
        )
        assertTrue(urls(html).isEmpty())
    }

    @Test fun wwwAndPortAreSeenAsTheSameSite() {
        val html = head("<link rel=\"icon\" href=\"https://example.com/a.png\">", "<link rel=\"icon\" href=\"https://example.com:8443/b.png\">")
        assertEquals(
            setOf("https://example.com/a.png", "https://example.com:8443/b.png"),
            urls(html, document = "https://www.example.com/", site = "https://www.example.com/").toSet(),
        )
    }

    // ---- ranking ----

    @Test fun closestSizeToSixtyFourComesFirst() {
        val html = head(
            "<link rel=\"icon\" href=\"/16.png\" sizes=\"16x16\">",
            "<link rel=\"icon\" href=\"/32.png\" sizes=\"32x32\">",
            "<link rel=\"icon\" href=\"/64.png\" sizes=\"64x64\">",
            "<link rel=\"icon\" href=\"/128.png\" sizes=\"128x128\">",
            "<link rel=\"apple-touch-icon\" href=\"/180.png\" sizes=\"180x180\">",
        )
        assertEquals(
            listOf("64", "128", "32", "180", "16"),
            urls(html).map { it.substringAfterLast('/').substringBefore('.') },
        )
    }

    @Test fun halfAndDoubleAreEquallyFarAndTheBiggerWins() {
        val html = head("<link rel=\"icon\" href=\"/32.png\" sizes=\"32x32\">", "<link rel=\"icon\" href=\"/128.png\" sizes=\"128x128\">")
        assertEquals("https://example.com/128.png", urls(html).first())
    }

    @Test fun sizesListPicksTheBestOfThem() {
        val html = head("<link rel=\"icon\" href=\"/multi.ico\" sizes=\"16x16 32x32 48x48 256X256\">")
        val candidate = FaviconLinks.candidates(html, page).single()
        assertEquals(48, candidate.size)
    }

    @Test fun anyAndBrokenSizesCountAsUnknown() {
        for (sizes in listOf("any", "", "x", "0x0", "16", "axb", "16x", "-4x-4")) {
            val html = head("<link rel=\"icon\" href=\"/i.png\" sizes=\"$sizes\">")
            assertNull("sizes=\"$sizes\"", FaviconLinks.candidates(html, page).single().size)
        }
    }

    @Test fun unknownSizeCountsAsTheUsualFaviconSize() {
        val html = head(
            "<link rel=\"icon\" href=\"/unknown.ico\">",
            "<link rel=\"icon\" href=\"/16.png\" sizes=\"16x16\">",
            "<link rel=\"apple-touch-icon\" href=\"/180.png\" sizes=\"180x180\">",
        )
        assertEquals(
            listOf("unknown.ico", "180.png", "16.png"), // 180 is 2.8x too big, 16 is 4x too small
            urls(html).map { it.substringAfterLast('/') },
        )
    }

    @Test fun plainIconBeatsAppleTouchOfTheSameSizeAndEarlierBeatsLater() {
        val html = head(
            "<link rel=\"apple-touch-icon\" href=\"/apple.png\" sizes=\"64x64\">",
            "<link rel=\"icon\" href=\"/second.png\" sizes=\"64x64\">",
            "<link rel=\"icon\" href=\"/third.png\" sizes=\"64x64\">",
        )
        assertEquals(listOf("second.png", "third.png", "apple.png"), urls(html).map { it.substringAfterLast('/') })
    }

    @Test fun sameAddressIsListedOnce() {
        val html = head("<link rel=\"icon\" href=\"/a.png\">", "<link rel=\"shortcut icon\" href=\"/a.png\">")
        assertEquals(listOf("https://example.com/a.png"), urls(html))
    }

    // ---- the plan ----

    @Test fun planIsTheBestThreeThenTheRootFavicon() {
        val html = head(
            "<link rel=\"icon\" href=\"/a.png\" sizes=\"64x64\">",
            "<link rel=\"icon\" href=\"/b.png\" sizes=\"48x48\">",
            "<link rel=\"icon\" href=\"/c.png\" sizes=\"32x32\">",
            "<link rel=\"icon\" href=\"/d.png\" sizes=\"16x16\">",
        )
        assertEquals(
            listOf(
                "https://example.com/a.png",
                "https://example.com/b.png",
                "https://example.com/c.png",
                "https://example.com/favicon.ico",
            ),
            FaviconLinks.plan(html, page, page),
        )
    }

    @Test fun planDoesNotListTheRootFaviconTwice() {
        val html = head("<link rel=\"icon\" href=\"/favicon.ico\">")
        assertEquals(listOf("https://example.com/favicon.ico"), FaviconLinks.plan(html, page, page))
    }

    @Test fun planWithoutAPageIsOnlyTheRootFavicon() {
        assertEquals(listOf("https://example.com/favicon.ico"), FaviconLinks.plan(null, null, "https://example.com/deep/page?x=1"))
        assertEquals(listOf("https://example.com/favicon.ico"), FaviconLinks.plan("<html></html>", page, page))
    }

    @Test fun planKeepsThePortAndSchemeOfTheSite() {
        assertEquals(listOf("http://localhost:8080/favicon.ico"), FaviconLinks.plan(null, null, "http://LocalHost:8080/x"))
    }

    @Test fun fallbackOnlyForWebAddresses() {
        assertEquals("https://a.com/favicon.ico", FaviconLinks.fallbackUrl("https://a.com/x/y"))
        assertNull(FaviconLinks.fallbackUrl("about:blank"))
        assertNull(FaviconLinks.fallbackUrl("ftp://a.com/"))
        assertNull(FaviconLinks.fallbackUrl("https://user:pw@a.com/"))
        assertTrue(FaviconLinks.plan(null, null, "about:blank").isEmpty())
    }

    @Test fun hugeJunkIsHandledWithoutTrouble() {
        val junk = "<p class=x>text &amp; <b>more</b></p>\n".repeat(20_000) + "<link rel=icon href=/ok.png>"
        assertEquals(listOf("https://example.com/ok.png"), urls(junk))
        assertFalse(urls("<".repeat(100_000)).isNotEmpty())
        assertFalse(urls("<a ".repeat(50_000)).isNotEmpty()) // one endless tag, never closed
        assertFalse(urls("<link rel=icon href=".repeat(10_000)).isNotEmpty())
    }
}
