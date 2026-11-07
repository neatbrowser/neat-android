package io.github.neatbrowser.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistTest {
    @Test fun recordsRoundTripAwkwardText() {
        val rows = listOf(
            listOf("a", "tab\there", "line\nbreak", "back\\slash", "cr\r", "ünï Đọc 😀", ""),
            listOf("b", "\\n literally", "\\"),
        )
        assertEquals(rows, Records.decode(Records.encode(rows)))
    }

    @Test fun recordsSkipBlankLinesAndTolerateWindowsLineEnds() {
        assertEquals(listOf(listOf("a", "b"), listOf("c")), Records.decode("a\tb\r\n\n\nc\n"))
    }

    @Test fun tabsRoundTrip() {
        val s = TabsState().open("https://a.com").open("https://b.com/x?y=1").update(2, title = "Bee\tline").select(1)
        val back = Persist.decodeTabs(Persist.encodeTabs(s))
        assertEquals(s.tabs, back.tabs)
        assertEquals(1L, back.selectedNormal)
        assertEquals(3L, back.nextId)
        assertFalse(back.ghostMode)
    }

    @Test fun ghostTabsAreNeverWritten() {
        val s = TabsState().open("https://a.com").setGhostMode(true).open("https://secret.example/private")
        val text = Persist.encodeTabs(s)
        assertFalse(text.contains("secret"))
        val back = Persist.decodeTabs(text)
        assertEquals(1, back.tabs.size)
        assertNull(back.selectedGhost)
        assertFalse(back.ghostMode)
    }

    @Test fun tabsFromADamagedFileAreFilteredAndIdsStayUnique() {
        val text = Records.encode(
            listOf(
                listOf("v1"),
                listOf("next", "2"),
                listOf("sel", "7"),
                listOf("tab", "1", "https://a.com", "A"),
                listOf("tab", "1", "https://dup.com", "Dup"),
                listOf("tab", "5", "javascript:alert(1)", "Bad"),
                listOf("tab", "x", "https://nan.com", "NaN"),
                listOf("tab", "0", "https://zero.com", "Zero"),
                listOf("tab", "9", "file:///etc/passwd", "File"),
                listOf("tab", "4", "https://d.com"),
                listOf("what", "ever"),
            ),
        )
        val s = Persist.decodeTabs(text)
        assertEquals(listOf(1L, 4L), s.tabs.map { it.id })
        assertNull(s.selectedNormal) // 7 does not exist
        assertEquals(5L, s.nextId) // above the highest id, even though the file said 2
    }

    @Test fun unknownVersionOrGarbageGivesEmptyState() {
        assertEquals(TabsState(), Persist.decodeTabs("v2\ntab\t1\thttps://a.com\n"))
        assertEquals(TabsState(), Persist.decodeTabs(""))
        assertEquals(History(), Persist.decodeHistory("garbage"))
        assertEquals(Pins(), Persist.decodePins("\u0000\u0001"))
    }

    @Test fun historyRoundTrip() {
        val h = History().record("https://a.com", "A", 5).record("https://b.com", "B\tb", 6).record("https://a.com", "A", 7)
        assertEquals(h, Persist.decodeHistory(Persist.encodeHistory(h)))
    }

    @Test fun historyRejectsOtherSchemes() {
        val text = Records.encode(
            listOf(
                listOf("v1"),
                listOf("h", "javascript:1", "x", "1", "1"),
                listOf("h", "https://ok.com", "Ok", "0", "9"),
                listOf("h", "https://nonum.com", "N", "many", "later"),
            ),
        )
        val h = Persist.decodeHistory(text)
        assertEquals(listOf("https://ok.com", "https://nonum.com"), h.entries.map { it.url })
        assertEquals(1, h.entries[0].visits) // at least one visit
        assertEquals(0L, h.entries[1].lastVisit)
    }

    @Test fun pinsRoundTripAndFilter() {
        val p = Pins().add("https://a.com", "A").add("https://b.com", "B")
        assertEquals(p, Persist.decodePins(Persist.encodePins(p)))
        val text = Records.encode(listOf(listOf("v1"), listOf("p", "intent://x", "X"), listOf("p", "https://ok.com", "Ok")))
        assertEquals(listOf("Ok"), Persist.decodePins(text).items.map { it.title })
    }

    @Test fun overlongAddressesAreLeftOut() {
        val long = "https://a.com/" + "x".repeat(9000)
        val s = TabsState().open(long).open("https://b.com")
        assertEquals(listOf("https://b.com"), Persist.decodeTabs(Persist.encodeTabs(s)).tabs.map { it.url })
        assertTrue(Persist.decodeHistory(Persist.encodeHistory(History().record(long, "L", 1))).entries.isEmpty())
    }
}
