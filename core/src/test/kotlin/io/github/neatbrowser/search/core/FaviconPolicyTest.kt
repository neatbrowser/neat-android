package io.github.neatbrowser.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconPolicyTest {
    // ---- same site ----

    @Test fun sameHostWithOrWithoutWwwAndPortIsTheSameSite() {
        assertTrue(FaviconPolicy.sameSite("https://example.com/a", "https://example.com/b.ico"))
        assertTrue(FaviconPolicy.sameSite("https://www.example.com/", "https://example.com/f.ico"))
        assertTrue(FaviconPolicy.sameSite("https://example.com/", "http://EXAMPLE.com:8080/f.ico"))
    }

    @Test fun subdomainsAndLookAlikesAreOtherSites() {
        assertFalse(FaviconPolicy.sameSite("https://example.com/", "https://cdn.example.com/f.ico"))
        assertFalse(FaviconPolicy.sameSite("https://example.com/", "https://example.com.evil.test/f.ico"))
        assertFalse(FaviconPolicy.sameSite("https://example.com/", "https://notexample.com/f.ico"))
        assertFalse(FaviconPolicy.sameSite("https://example.com/", "https://www.google.com/s2/favicons?domain=example.com"))
    }

    @Test fun onlyWebAddressesCanBeTheSameSite() {
        assertFalse(FaviconPolicy.sameSite("https://example.com/", "ftp://example.com/f.ico"))
        assertFalse(FaviconPolicy.sameSite("about:blank", "about:blank"))
        assertFalse(FaviconPolicy.sameSite("https://example.com/", "data:image/png;base64,AA"))
    }

    // ---- redirects ----

    @Test fun redirectsToWebAddressesAreFollowed() {
        assertEquals("https://example.com/new.ico", FaviconPolicy.redirectTarget("http://example.com/favicon.ico", "https://example.com/new.ico"))
        assertEquals("https://example.com/b/new.ico", FaviconPolicy.redirectTarget("https://example.com/a/old.ico", "/b/new.ico"))
        assertEquals("https://example.com/a/new.ico", FaviconPolicy.redirectTarget("https://example.com/a/old.ico", "new.ico"))
        assertEquals("https://other.test/f.ico", FaviconPolicy.redirectTarget("https://example.com/f.ico", "//other.test/f.ico"))
    }

    @Test fun redirectsToAnythingElseAreRefused() {
        for (target in listOf("ftp://example.com/f.ico", "file:///etc/passwd", "javascript:alert(1)", "data:image/png;base64,AA", "intent://x#Intent;end", "")) {
            assertNull(target, FaviconPolicy.redirectTarget("https://example.com/f.ico", target))
        }
        assertNull(FaviconPolicy.redirectTarget("https://example.com/f.ico", null))
        assertNull(FaviconPolicy.redirectTarget("https://example.com/f.ico", "   "))
    }

    // ---- cache file names ----

    @Test fun ordinaryHostsKeepTheirNameAndGetPng() {
        assertEquals("example.com.png", FaviconPolicy.fileName("example.com"))
        assertEquals("m.baomoi-1.com.png", FaviconPolicy.fileName("m.baomoi-1.com"))
        assertEquals("example.com.png", FaviconPolicy.fileName("EXAMPLE.com"))
    }

    @Test fun nothingThatCouldLeaveTheFolderSurvives() {
        for (key in listOf("../../etc/passwd", "a/b", "a\\b", "..", ".", ".hidden", "[::1]", "a b", "evil\u0000.com", "tên-miền.vn")) {
            val name = FaviconPolicy.fileName(key)
            assertTrue(key, name.endsWith(".png"))
            assertFalse(key, name.startsWith("."))
            assertTrue(key, name.none { it == '/' || it == '\\' || it == ':' || it == '\u0000' || it == ' ' })
            assertTrue(key, name.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '.' || it == '_' })
        }
    }

    @Test fun differentKeysNeverShareAFile() {
        val keys = listOf("a.b", "a_b", "a-b", "a:b", "[::1]", "[::2]", "ab", "a.b.", ".a.b", "a_002eb")
        assertEquals(keys.size, keys.map { FaviconPolicy.fileName(it) }.toSet().size)
    }

    @Test fun veryLongHostsGetAShortNameThatStillTellsThemApart() {
        val a = "a".repeat(250) + ".com"
        val b = "a".repeat(250) + ".org"
        assertTrue(FaviconPolicy.fileName(a).length < 140)
        assertNotEquals(FaviconPolicy.fileName(a), FaviconPolicy.fileName(b))
    }

    // ---- sizes ----

    @Test fun sampleSizeKeepsAtLeastTheTargetOnTheShorterSide() {
        assertEquals(1, FaviconPolicy.sampleSize(16, 16))
        assertEquals(1, FaviconPolicy.sampleSize(64, 64))
        assertEquals(1, FaviconPolicy.sampleSize(127, 127))
        assertEquals(2, FaviconPolicy.sampleSize(128, 128))
        assertEquals(2, FaviconPolicy.sampleSize(180, 180))
        assertEquals(4, FaviconPolicy.sampleSize(256, 256))
        assertEquals(64, FaviconPolicy.sampleSize(4096, 4096))
        assertEquals(1, FaviconPolicy.sampleSize(4096, 40)) // the shorter side decides
    }

    @Test fun storedSizeShrinksOnlyBigPicturesAndKeepsTheShape() {
        assertEquals(16 to 16, FaviconPolicy.storedSize(16, 16))
        assertEquals(64 to 64, FaviconPolicy.storedSize(64, 64))
        assertEquals(64 to 64, FaviconPolicy.storedSize(180, 180))
        assertEquals(64 to 32, FaviconPolicy.storedSize(256, 128))
        assertEquals(16 to 64, FaviconPolicy.storedSize(32, 128))
        assertEquals(64 to 1, FaviconPolicy.storedSize(4096, 4)) // never zero
    }
}
