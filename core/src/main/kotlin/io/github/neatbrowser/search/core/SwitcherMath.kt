package io.github.neatbrowser.search.core

import kotlin.math.abs

/** The numbers behind the tab switcher that need no Android: timings, where to return to, and card size. */
object SwitcherMath {
    /** Scrolling back to the tab that was open takes this long for every tab it has to pass. */
    const val MS_PER_TAB = 350

    /** ... but never longer than this, however many tabs there are. */
    const val MAX_SCROLL_MS = 1000

    /** A swipe up faster than this (pixels per second) counts, however short it is: a short flick throws a card away. */
    const val SWIPE_VELOCITY = 800f

    /** "Clear All Tabs": each card starts sliding away this much later than the one before it, middle one first. */
    const val CLEAR_STAGGER_MS = 70

    /** Only the cards this many places from the middle are on screen, so they are the only ones that need a delay. */
    const val CLEAR_MAX_PLACES = 2

    /**
     * While a card zooms, the backdrop is solid until the zoom is this close to the page (0 = the card covers
     * the page), and only then fades. Until then the live page must not show through: it would be a second,
     * full-size copy of the page behind the zooming card.
     */
    const val BACKDROP_LEAD = 0.25f

    /** The shape of a page area is kept inside this range so a freak measurement cannot make a sliver or a plank. */
    const val MIN_ASPECT = 0.25f
    const val MAX_ASPECT = 3f

    /**
     * Time to scroll the switcher over [tabsSkipped] tabs (either direction): [msPerTab] each, at most
     * [capMs]. Zero tabs take no time.
     */
    fun scrollDurationMs(tabsSkipped: Int, msPerTab: Int = MS_PER_TAB, capMs: Int = MAX_SCROLL_MS): Int {
        val total = abs(tabsSkipped.toLong()) * msPerTab
        return total.coerceAtMost(capMs.toLong()).toInt()
    }

    /**
     * The card to go back to when the switcher is left without picking one: the tab selected right
     * now. [TabsState.close] has already moved the selection to the replacement when the tab that was
     * open got closed, so this is also where to go then. Null means the home screen (no tabs left).
     */
    fun returnIndex(tabs: List<Tab>, selectedId: Long?): Int? {
        if (selectedId == null) return null
        return tabs.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }
    }

    /** Width over height of the page area, or null while it has not been measured. */
    fun pageAspect(width: Int, height: Int): Float? {
        if (width <= 0 || height <= 0) return null
        return (width.toFloat() / height).coerceIn(MIN_ASPECT, MAX_ASPECT)
    }

    data class CardSize(val width: Float, val height: Float)

    /**
     * The biggest card with the proportions [aspect] (width over height) that fits in [maxWidth] x
     * [maxHeight]. The card has the shape of the page, so the picture of the page needs no cropping.
     */
    fun fitCard(maxWidth: Float, maxHeight: Float, aspect: Float): CardSize {
        val a = if (aspect > 0f) aspect.coerceIn(MIN_ASPECT, MAX_ASPECT) else 1f
        var height = maxHeight.coerceAtLeast(1f)
        var width = height * a
        val widthLimit = maxWidth.coerceAtLeast(1f)
        if (width > widthLimit) {
            width = widthLimit
            height = width / a
        }
        return CardSize(width, height)
    }

    /**
     * Opacity of the switcher's backdrop (the gradient) at [open] (1 = fully open, 0 = the page is showing).
     * With a card [zooming] between its place and the page the backdrop stays solid, so the live page is
     * never visible behind the card, and fades only over the last [BACKDROP_LEAD] of the way to the page.
     * Without a zoom (home screen, no page) it simply follows [open].
     */
    fun backdropAlpha(open: Float, zooming: Boolean): Float {
        val o = open.coerceIn(0f, 1f)
        return if (zooming) (o / BACKDROP_LEAD).coerceIn(0f, 1f) else o
    }

    /**
     * Whether an upward swipe closes what it was dragging (a card, or the whole switcher): it went further
     * than [minDistance], or it was flung faster than [minVelocity]. Distance and velocity are how much
     * upward, so negative means the finger went down.
     */
    fun swipeUpCloses(upDistance: Float, upVelocity: Float, minDistance: Float, minVelocity: Float = SWIPE_VELOCITY): Boolean =
        upDistance > minDistance || upVelocity > minVelocity

    /**
     * How long a card [places] away from the middle waits before it slides away when all tabs are cleared:
     * the middle one goes first, then the ones beside it, and so on, up to [CLEAR_MAX_PLACES] places.
     */
    fun clearDelayMs(places: Int, staggerMs: Int = CLEAR_STAGGER_MS): Int =
        minOf(abs(places.toLong()), CLEAR_MAX_PLACES.toLong()).toInt() * staggerMs
}
