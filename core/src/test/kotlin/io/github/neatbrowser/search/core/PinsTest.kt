package io.github.neatbrowser.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinsTest {
    @Test fun toggleAddsThenRemoves() {
        val p = Pins().toggle("https://a.com/x", "A")
        assertTrue(p.contains("https://a.com/x"))
        assertTrue(p.contains("https://A.com/x#frag"))
        assertFalse(p.toggle("https://a.com/x#frag", "A").contains("https://a.com/x"))
    }

    @Test fun pinsKeepTheirOrder() {
        val p = Pins().add("https://a.com", "A").add("https://b.com", "B").add("https://c.com", "C")
        assertEquals(listOf("A", "B", "C"), p.items.map { it.title })
    }

    @Test fun pinningTwiceKeepsOne() {
        val p = Pins().add("https://a.com", "A").add("https://a.com/", "Again")
        assertEquals(1, p.items.size)
        assertEquals("A", p.items[0].title)
    }

    @Test fun blankTitleShowsTheSite() {
        assertEquals("a.com", Pins().add("https://www.a.com/x", " ").items[0].title)
    }

    @Test fun onlyWebAddressesCanBePinned() {
        assertTrue(Pins().add("javascript:alert(1)", "x").items.isEmpty())
        assertTrue(Pins().add("about:blank", "x").items.isEmpty())
    }

    @Test fun searchFindsByTitleOrAddress() {
        val p = Pins().add("https://baomoi.com", "Đọc báo").add("https://github.com", "Code")
        assertEquals(listOf("Đọc báo"), p.search("doc bao").map { it.title })
        assertEquals(listOf("Code"), p.search("github").map { it.title })
        assertEquals(2, p.search("").size)
    }

    @Test fun sizeIsCapped() {
        var p = Pins()
        for (i in 1..Pins.MAX_PINS + 5) p = p.add("https://s$i.com", "S$i")
        assertEquals(Pins.MAX_PINS, p.items.size)
        assertEquals("S${Pins.MAX_PINS + 5}", p.items.last().title)
    }
}
