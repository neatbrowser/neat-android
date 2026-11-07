package io.github.neatbrowser.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabsTest {
    private fun three(): TabsState =
        TabsState().open("https://a.com").open("https://b.com").open("https://c.com")

    @Test fun openAddsAndSelects() {
        val s = TabsState().open("https://a.com").open("https://b.com")
        assertEquals(listOf("https://a.com", "https://b.com"), s.visible.map { it.url })
        assertEquals("https://b.com", s.selected?.url)
        assertEquals(3L, s.nextId)
    }

    @Test fun nothingSelectedMeansHome() {
        assertNull(TabsState().selected)
    }

    @Test fun selectOnlyWorksInCurrentMode() {
        val s = three()
        assertEquals("https://a.com", s.select(1).selected?.url)
        assertEquals(s, s.select(99))
        val ghost = s.setGhostMode(true).open("https://g.com")
        val back = ghost.setGhostMode(false)
        assertEquals(back, back.select(ghost.selected!!.id)) // a ghost tab cannot be selected from normal mode
    }

    @Test fun closingSelectedTabSelectsTheNextOne() {
        val s = three().select(2).close(2)
        assertEquals("https://c.com", s.selected?.url)
    }

    @Test fun closingLastSelectedTabSelectsThePreviousOne() {
        val s = three().close(3)
        assertEquals("https://b.com", s.selected?.url)
    }

    @Test fun closingAnotherTabKeepsTheSelection() {
        val s = three().select(1).close(3)
        assertEquals("https://a.com", s.selected?.url)
        assertEquals(2, s.visible.size)
    }

    @Test fun closingTheLastTabShowsHome() {
        val s = TabsState().open("https://a.com").close(1)
        assertNull(s.selected)
        assertTrue(s.tabs.isEmpty())
    }

    @Test fun closingUnknownTabChangesNothing() {
        val s = three()
        assertEquals(s, s.close(42))
    }

    @Test fun modesHaveSeparateTabsAndSelections() {
        val normal = TabsState().open("https://a.com")
        val ghost = normal.setGhostMode(true)
        assertTrue(ghost.visible.isEmpty())
        assertNull(ghost.selected)
        val ghostWithTab = ghost.open("https://g.com")
        assertEquals(listOf("https://g.com"), ghostWithTab.visible.map { it.url })
        assertTrue(ghostWithTab.visible.all { it.ghost })
        val back = ghostWithTab.setGhostMode(false)
        assertEquals("https://a.com", back.selected?.url)
        assertEquals(2, back.tabs.size)
    }

    @Test fun closingInOneModeDoesNotTouchTheOther() {
        val s = TabsState().open("https://a.com").setGhostMode(true).open("https://g.com")
        val after = s.close(s.selected!!.id)
        assertEquals(listOf("https://a.com"), after.tabs.map { it.url })
        assertEquals(1L, after.selectedNormal)
        assertNull(after.selectedGhost)
    }

    @Test fun updateChangesAddressAndTitle() {
        val s = three().update(2, title = "Bee").update(2, url = "https://b.com/x")
        assertEquals(Tab(2, "https://b.com/x", "Bee"), s.tab(2))
        assertEquals(s, s.update(77, title = "x"))
    }

    @Test fun clearRemovesOnlyTheCurrentMode() {
        val s = three().setGhostMode(true).open("https://g.com").setGhostMode(false)
        val (after, cleared) = s.clearVisible()
        assertEquals(1, after.tabs.size)
        assertTrue(after.tabs.single().ghost)
        assertEquals(3, cleared.tabs.size)
        assertNull(after.selected)
    }

    @Test fun undoBringsTheTabsBackWithTheOldSelection() {
        val s = three().select(2)
        val (after, cleared) = s.clearVisible()
        val restored = after.restore(cleared)
        assertEquals(s.tabs, restored.tabs)
        assertEquals(2L, restored.selected?.id)
    }

    @Test fun undoKeepsTabsOpenedAfterTheClear() {
        val (after, cleared) = three().clearVisible()
        val fresh = after.open("https://new.com")
        val restored = fresh.restore(cleared)
        assertEquals(listOf("https://a.com", "https://b.com", "https://c.com", "https://new.com"), restored.visible.map { it.url })
        assertEquals("https://new.com", restored.selected?.url) // the tab opened since stays selected
    }

    @Test fun restoringNothingChangesNothing() {
        val (after, cleared) = TabsState().clearVisible()
        assertFalse(cleared.tabs.isNotEmpty())
        assertEquals(after, after.restore(cleared))
    }

    @Test fun idsNeverRepeat() {
        val s = three().close(3).open("https://d.com")
        assertEquals(listOf(1L, 2L, 4L), s.tabs.map { it.id })
    }
}
