package io.github.neatbrowser.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    @Test fun recordPutsTheNewestFirst() {
        val h = History().record("https://a.com", "A", 1).record("https://b.com", "B", 2)
        assertEquals(listOf("https://b.com", "https://a.com"), h.entries.map { it.url })
    }

    @Test fun visitingAgainMovesToFrontAndCounts() {
        val h = History().record("https://a.com", "A", 1).record("https://b.com", "B", 2).record("https://a.com/", "A!", 3)
        assertEquals(listOf("https://a.com/", "https://b.com"), h.entries.map { it.url })
        assertEquals(2, h.entries[0].visits)
        assertEquals(3L, h.entries[0].lastVisit)
        assertEquals("A!", h.entries[0].title)
    }

    @Test fun fragmentsAndCaseAreTheSamePage() {
        val h = History().record("https://A.com/x#one", "X", 1).record("https://a.com/x#two", "X", 2)
        assertEquals(1, h.entries.size)
        assertEquals("https://a.com/x", h.entries[0].url)
    }

    @Test fun blankTitleKeepsTheKnownOne() {
        val h = History().record("https://a.com", "Alpha", 1).record("https://a.com", " ", 2)
        assertEquals("Alpha", h.entries[0].title)
    }

    @Test fun onlyWebAddressesAreRecorded() {
        val h = History().record("javascript:alert(1)", "x", 1).record("data:text/html,hi", "x", 1).record("about:blank", "x", 1)
        assertTrue(h.entries.isEmpty())
    }

    @Test fun updateTitleChangesTheEntry() {
        val h = History().record("https://a.com", "", 1).updateTitle("https://a.com/", "Alpha")
        assertEquals("Alpha", h.entries[0].title)
        assertEquals(h, h.updateTitle("https://zzz.com", "Z"))
        assertEquals(h, h.updateTitle("https://a.com", "  "))
    }

    @Test fun removeAndClear() {
        val h = History().record("https://a.com", "A", 1).record("https://b.com", "B", 2)
        assertEquals(listOf("https://b.com"), h.remove("https://a.com/#x").entries.map { it.url })
        assertTrue(h.clear().entries.isEmpty())
    }

    @Test fun sizeIsCapped() {
        var h = History()
        for (i in 1..History.MAX_ENTRIES + 50) h = h.record("https://site$i.com", "S$i", i.toLong())
        assertEquals(History.MAX_ENTRIES, h.entries.size)
        assertEquals("https://site${History.MAX_ENTRIES + 50}.com", h.entries[0].url)
    }

    @Test fun searchIgnoresCaseAndAccents() {
        val h = History().record("https://baomoi.com", "Đọc báo mới", 1)
        assertEquals(1, h.search("doc bao", 5).size)
        assertEquals(1, h.search("ĐỌC", 5).size)
        assertEquals(0, h.search("xyz", 5).size)
    }

    @Test fun searchNeedsEveryWord() {
        val h = History().record("https://a.com/news", "Daily news", 1)
        assertEquals(1, h.search("daily news", 5).size)
        assertEquals(0, h.search("daily sports", 5).size)
    }

    @Test fun hostStartRanksFirst() {
        val h = History()
            .record("https://other.com/x", "All about goo", 1)
            .record("https://google.com", "Search", 2)
            .record("https://notes.example.com/google", "Notes", 3)
        assertEquals("https://google.com", h.search("goo", 5).first().url)
    }

    @Test fun moreVisitedWinsATie() {
        val h = History()
            .record("https://a.com", "News", 1)
            .record("https://b.com", "News", 2)
            .record("https://a.com", "News", 3)
            .record("https://c.com", "News", 4)
        assertEquals("https://a.com", h.search("news", 5).first().url)
    }

    @Test fun emptyQueryGivesTheRecentOnes() {
        val h = History().record("https://a.com", "A", 1).record("https://b.com", "B", 2)
        assertEquals(listOf("https://b.com"), h.search("  ", 1).map { it.url })
    }

    @Test fun searchHonoursTheLimit() {
        var h = History()
        for (i in 1..10) h = h.record("https://news$i.com", "News", i.toLong())
        assertEquals(3, h.search("news", 3).size)
    }
}
