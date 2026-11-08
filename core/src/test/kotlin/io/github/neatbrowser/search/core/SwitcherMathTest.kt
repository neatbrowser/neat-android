package io.github.neatbrowser.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitcherMathTest {
    // ---- scroll duration ----

    @Test fun noTabsSkippedNoTime() {
        assertEquals(0, SwitcherMath.scrollDurationMs(0))
    }

    @Test fun threeHundredFiftyMillisecondsPerTabSkipped() {
        assertEquals(350, SwitcherMath.scrollDurationMs(1))
        assertEquals(700, SwitcherMath.scrollDurationMs(2))
    }

    @Test fun neverMoreThanOneSecond() {
        assertEquals(1000, SwitcherMath.scrollDurationMs(3)) // 1050 capped
        assertEquals(1000, SwitcherMath.scrollDurationMs(50))
        assertEquals(1000, SwitcherMath.scrollDurationMs(Int.MAX_VALUE))
    }

    @Test fun directionDoesNotMatter() {
        assertEquals(SwitcherMath.scrollDurationMs(2), SwitcherMath.scrollDurationMs(-2))
        assertEquals(1000, SwitcherMath.scrollDurationMs(Int.MIN_VALUE))
    }

    @Test fun timingsCanBeTuned() {
        assertEquals(300, SwitcherMath.scrollDurationMs(3, msPerTab = 100))
        assertEquals(250, SwitcherMath.scrollDurationMs(3, msPerTab = 100, capMs = 250))
    }

    // ---- where to return to ----

    private fun three(): TabsState =
        TabsState().open("https://a.com").open("https://b.com").open("https://c.com")

    @Test fun returnsToTheTabThatWasOpen() {
        val state = three().select(2)
        assertEquals(1, SwitcherMath.returnIndex(state.visible, state.selected?.id))
    }

    @Test fun whenTheOpenTabWasClosedItsReplacementIsTheTarget() {
        val state = three().select(2).close(2) // c.com takes its place
        assertEquals("https://c.com", state.selected?.url)
        assertEquals(1, SwitcherMath.returnIndex(state.visible, state.selected?.id))

        val last = three().close(3) // the last one closed: the one before it
        assertEquals(1, SwitcherMath.returnIndex(last.visible, last.selected?.id))
    }

    @Test fun closingAnotherTabKeepsTheTarget() {
        val state = three().select(3).close(1)
        assertEquals(1, SwitcherMath.returnIndex(state.visible, state.selected?.id))
    }

    @Test fun noTabsLeftMeansHome() {
        val state = three().clearVisible().first
        assertNull(SwitcherMath.returnIndex(state.visible, state.selected?.id))
        assertNull(SwitcherMath.returnIndex(emptyList(), null))
    }

    @Test fun anIdThatIsNotListedMeansHome() {
        val state = three()
        assertNull(SwitcherMath.returnIndex(state.visible, 99L))
    }

    @Test fun undoingClearAllBringsTheTargetBack() {
        val state = three().select(2)
        val (cleared, removed) = state.clearVisible()
        val back = cleared.restore(removed)
        assertEquals(1, SwitcherMath.returnIndex(back.visible, back.selected?.id))
    }

    // ---- card size ----

    @Test fun pageAspectIsWidthOverHeight() {
        assertEquals(0.5f, SwitcherMath.pageAspect(540, 1080)!!, 0.0001f)
        assertEquals(2f, SwitcherMath.pageAspect(2000, 1000)!!, 0.0001f)
    }

    @Test fun pageAspectIsNullUntilMeasured() {
        assertNull(SwitcherMath.pageAspect(0, 0))
        assertNull(SwitcherMath.pageAspect(100, 0))
        assertNull(SwitcherMath.pageAspect(0, 100))
        assertNull(SwitcherMath.pageAspect(-5, 100))
    }

    @Test fun pageAspectStaysInSaneBounds() {
        assertEquals(SwitcherMath.MIN_ASPECT, SwitcherMath.pageAspect(1, 1000)!!, 0.0001f)
        assertEquals(SwitcherMath.MAX_ASPECT, SwitcherMath.pageAspect(1000, 1)!!, 0.0001f)
    }

    @Test fun cardHasExactlyTheShapeOfThePage() {
        val aspect = SwitcherMath.pageAspect(1080, 1920)!!
        val card = SwitcherMath.fitCard(maxWidth = 800f, maxHeight = 1200f, aspect = aspect)
        assertEquals(aspect, card.width / card.height, 0.0001f)
    }

    @Test fun tallCardIsLimitedByHeight() {
        val card = SwitcherMath.fitCard(maxWidth = 1000f, maxHeight = 600f, aspect = 0.5f)
        assertEquals(600f, card.height, 0.001f)
        assertEquals(300f, card.width, 0.001f)
    }

    @Test fun wideCardIsLimitedByWidth() {
        val card = SwitcherMath.fitCard(maxWidth = 300f, maxHeight = 1000f, aspect = 0.5f)
        assertEquals(300f, card.width, 0.001f)
        assertEquals(600f, card.height, 0.001f)
    }

    @Test fun landscapePageGivesAWideCard() {
        val card = SwitcherMath.fitCard(maxWidth = 800f, maxHeight = 300f, aspect = 2f)
        assertEquals(600f, card.width, 0.001f)
        assertEquals(300f, card.height, 0.001f)
    }

    @Test fun neverShrinksToNothingOrBlowsUp() {
        val tiny = SwitcherMath.fitCard(maxWidth = -10f, maxHeight = 0f, aspect = 0.5f)
        assertTrue(tiny.width > 0f && tiny.height > 0f)
        val bad = SwitcherMath.fitCard(maxWidth = 100f, maxHeight = 100f, aspect = Float.NaN)
        assertTrue(bad.width > 0f && bad.height > 0f)
        val zero = SwitcherMath.fitCard(maxWidth = 100f, maxHeight = 100f, aspect = 0f)
        assertTrue(zero.width > 0f && zero.height > 0f)
    }
}
