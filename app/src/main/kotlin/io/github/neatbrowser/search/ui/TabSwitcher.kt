package io.github.neatbrowser.search.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.neatbrowser.search.R
import io.github.neatbrowser.search.browser.LiveShot
import io.github.neatbrowser.search.core.SwitcherMath
import io.github.neatbrowser.search.core.Tab
import io.github.neatbrowser.search.core.UrlUtils
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How far above the navigation bar "Clear All Tabs" sits, centred. The Undo bar that replaces it after a
 * clear uses the same value, so it appears in exactly the same spot.
 */
internal val ActionSlotBottom = 88.dp

private val ActionHeight = 44.dp
private val BarHeight = 72.dp
private val HeaderHeight = 36.dp
private val HeaderGap = 8.dp
private val BlockMargin = 8.dp
private val CardGap = 14.dp
private val CardRadius = 28.dp
private val HeaderIconSize = 28.dp
private val BadgeSize = 72.dp

private const val MAX_CARD_WIDTH_FRACTION = 0.74f
private const val NEIGHBOUR_SCALE_LOSS = 0.10f // a tab one place away is 10% smaller: the middle one is only slightly bigger
private const val NEIGHBOUR_ALPHA_LOSS = 0.35f
private const val SLIDE_FRACTION = 0.35f // of a card's width, how far the others slide when the switcher opens or closes
private const val OPEN_MS = 320
private const val ZOOM_MS = 300
private const val FADE_MS = 220

/** A card that is let go after being dragged up this far is thrown away; a flick (see SwitcherMath) needs far less. */
private val CloseDragDistance = 64.dp
private const val THROW_MS = 220 // a thrown card leaves the screen this fast
private const val CLEAR_SLIDE_MS = 260 // every card of "Clear All Tabs" slides up this long ...
private const val GAP_MS = 260 // the other cards close the gap of a closed card this long

/** ... and the last card starts this much later than the first one, so this is how long the whole thing takes. */
private val ClearMs = CLEAR_SLIDE_MS + SwitcherMath.clearDelayMs(SwitcherMath.CLEAR_MAX_PLACES)

/** The live page may take this long, at least, to appear under the picture that has zoomed over it... */
private const val HANDOFF_MIN_MS = 120L

/** ... and at most this long before the picture fades out anyway (a page that never finishes loading). */
private const val HANDOFF_MAX_MS = 1200L

private enum class Phase {
    Hidden,

    /** Taking the picture of the page, then zooming it down into its card. Touches are blocked. */
    Opening,

    /** Ready to use. */
    Open,

    /** Scrolling back to the tab, then zooming it up over the page (or fading out). Touches are blocked. */
    Closing,

    /** The picture covers the page; the tab has been selected and the picture fades once the live page shows. */
    Handoff,

    /** "Clear All Tabs": the cards slide up and away, then the tabs are closed. Touches are blocked. */
    Clearing,
}

/** Everything that changes while the switcher animates. */
@Stable
private class SwitcherAnim {
    var phase by mutableStateOf(Phase.Hidden)

    /** The card that is zooming between its place and the page area, or null. */
    var heroId by mutableStateOf<Long?>(null)

    /** 1 = the switcher is fully open; 0 = the page shows, the card has zoomed to cover it. */
    val open = Animatable(0f)

    /** Opacity of the zoomed picture; it goes to 0 when the live page has taken over. */
    val heroAlpha = Animatable(1f)

    /** Counts openings: each one gets a fresh pager that starts on the tab that is open. */
    var session by mutableIntStateOf(0)

    /** True while "Clear All Tabs" slides the cards away. */
    var clearing by mutableStateOf(false)

    /** Where each tab stood (its index) when a card was closed last, and which one was in the middle then. */
    var closedFrom by mutableStateOf<Map<Long, Int>>(emptyMap())
    var closedMiddle by mutableIntStateOf(0)

    /** 1 right after a card was closed, then down to 0 while the others slide from where they stood into the gap. */
    val gap = Animatable(0f)

    var job: Job? = null
}

/** Where the zooming card sits when the switcher is open: centre, scale and opacity. */
private class HeroRest(val centerX: Float, val centerY: Float, val scale: Float, val alpha: Float)

/**
 * Every open tab of the current mode as a card with the shape of the page, its icon and title above it.
 *
 * - Swipe sideways to move; swipe a card up (or use TalkBack's "Close tab" action) to close it.
 * - Tap a card: it zooms to fill the page, then that tab is selected.
 * - Tap the empty space, the tabs button or Back: scroll to the tab that was open, then zoom into it;
 *   with no tab left the screen just fades out.
 * - Opening takes a picture of the page (waiting at most [capture]'s own limit) and shrinks it into its card.
 * - Touches are blocked while it animates.
 *
 * [visible] is the switcher's own switch; it goes false when the zoom-in has finished and the tab is
 * selected ([onReturn]), while the picture may stay a moment longer over a page that is still drawing.
 */
@Composable
fun TabSwitcher(
    visible: Boolean,
    backSignal: Int,
    tabs: List<Tab>,
    selectedId: Long?,
    ghost: Boolean,
    thumbnails: Map<Long, ImageBitmap>,
    liveShot: LiveShot?,
    pageBounds: Rect?,
    pageSize: IntSize,
    isPageReady: (Long) -> Boolean,
    capture: suspend () -> Unit,
    onReturn: (Long?) -> Unit,
    onClose: (Long) -> Unit,
    onClearAll: () -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onHidden: () -> Unit,
    onBusyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anim = remember { SwitcherAnim() }
    val scope = rememberCoroutineScope()

    val tabsNow by rememberUpdatedState(tabs)
    val selectedNow by rememberUpdatedState(selectedId)
    val boundsNow by rememberUpdatedState(pageBounds)
    val captureNow by rememberUpdatedState(capture)
    val readyNow by rememberUpdatedState(isPageReady)
    val returnNow by rememberUpdatedState(onReturn)
    val hiddenNow by rememberUpdatedState(onHidden)
    val busyNow by rememberUpdatedState(onBusyChange)
    val clearNow by rememberUpdatedState(onClearAll)

    // A new pager for every opening, so that it starts on the tab that is open now.
    val pagerState = key(anim.session) {
        rememberPagerState(
            initialPage = SwitcherMath.returnIndex(tabs, selectedId) ?: 0,
            pageCount = { tabs.size },
        )
    }

    // Opening, and closing from outside (a new tab, a link from another app): that one needs no animation.
    LaunchedEffect(visible) {
        if (visible) {
            anim.job?.cancel()
            if (anim.phase != Phase.Hidden) {
                // Opened again while the last picture was still fading: start from nothing.
                anim.phase = Phase.Hidden
                anim.heroId = null
                withFrameNanos { }
            }
            anim.session++
            anim.open.snapTo(0f)
            anim.heroAlpha.snapTo(1f)
            anim.gap.snapTo(0f)
            anim.closedFrom = emptyMap()
            anim.clearing = false
            anim.heroId = null
            anim.phase = Phase.Opening

            captureNow() // the page is still on screen while the picture is taken
            val id = selectedNow
            anim.heroId = if (id != null && boundsNow != null && tabsNow.any { it.id == id }) id else null
            anim.open.animateTo(1f, tween(OPEN_MS, easing = FastOutSlowInEasing))
            anim.heroId = null
            anim.phase = Phase.Open
        } else if (anim.phase == Phase.Opening || anim.phase == Phase.Open) {
            anim.phase = Phase.Hidden
            anim.heroId = null
            hiddenNow()
        }
    }

    // Back is swallowed while the switcher animates or a picture still covers the page.
    val swallowBack = anim.phase != Phase.Hidden && anim.phase != Phase.Open
    LaunchedEffect(swallowBack) { busyNow(swallowBack) }

    /**
     * Leaves the switcher. [tappedId] is the card that was tapped; null means "go back to the tab that was
     * open" (empty space, tabs button, Back), which scrolls to it first.
     */
    fun startClose(tappedId: Long?) {
        if (anim.phase != Phase.Open) return
        anim.phase = Phase.Closing
        anim.job = scope.launch {
            val targetId = tappedId ?: selectedNow
            val index = tabsNow.indexOfFirst { it.id == targetId }
            val page = boundsNow
            if (targetId == null || index < 0 || page == null) {
                // Nothing to zoom into (home screen, no tabs left): the switcher fades away.
                anim.open.animateTo(0f, tween(FADE_MS))
                anim.phase = Phase.Hidden
                anim.heroId = null
                returnNow(targetId?.takeIf { index >= 0 })
                hiddenNow()
                return@launch
            }

            if (tappedId == null && pagerState.pageCount > 0) {
                val to = index.coerceIn(0, pagerState.pageCount - 1)
                val duration = SwitcherMath.scrollDurationMs(abs(to - pagerState.currentPage))
                if (duration > 0) {
                    pagerState.animateScrollToPage(to, animationSpec = tween(duration, easing = FastOutSlowInEasing))
                } else if (pagerState.currentPageOffsetFraction != 0f) {
                    pagerState.animateScrollToPage(to)
                }
            }

            anim.heroId = targetId
            anim.open.animateTo(0f, tween(ZOOM_MS, easing = FastOutSlowInEasing))

            // The picture now covers the page. Show the tab for real and let the picture go once the page is there.
            anim.phase = Phase.Handoff
            returnNow(targetId)
            val started = System.currentTimeMillis()
            delay(HANDOFF_MIN_MS)
            while (!readyNow(targetId) && System.currentTimeMillis() - started < HANDOFF_MAX_MS) delay(50)
            anim.heroAlpha.animateTo(0f, tween(FADE_MS))
            anim.phase = Phase.Hidden
            anim.heroId = null
            hiddenNow()
        }
    }

    val closeToOpenTab by rememberUpdatedState { startClose(null) }

    /** "Clear All Tabs": the cards slide up and away, the middle one first, and only then are the tabs closed. */
    fun startClear() {
        if (anim.phase != Phase.Open || tabsNow.isEmpty()) return
        anim.phase = Phase.Clearing
        anim.job = scope.launch {
            anim.clearing = true
            delay(ClearMs.toLong())
            clearNow() // the tabs are gone now; the Undo bar takes the place of the button
            anim.clearing = false
            anim.phase = Phase.Open
        }
    }

    /** A card has been thrown away: tell the others where they stood, so that they slide into the gap instead of jumping. */
    val closeCard: (Long) -> Unit = { id ->
        anim.closedFrom = tabs.mapIndexed { index, tab -> tab.id to index }.toMap()
        anim.closedMiddle = pagerState.currentPage
        onClose(id)
        scope.launch {
            anim.gap.snapTo(1f)
            anim.gap.animateTo(0f, tween(GAP_MS, easing = FastOutSlowInEasing))
        }
    }

    val lastBack = remember { mutableIntStateOf(backSignal) }
    LaunchedEffect(backSignal) {
        if (backSignal != lastBack.intValue) {
            lastBack.intValue = backSignal
            startClose(null)
        }
    }

    if (anim.phase != Phase.Hidden) {
        SwitcherBody(
            anim = anim,
            pagerState = pagerState,
            tabs = tabs,
            ghost = ghost,
            thumbnails = thumbnails,
            liveShot = liveShot,
            pageBounds = pageBounds,
            pageSize = pageSize,
            onCardClick = { id -> startClose(id) },
            onEmptyTap = { closeToOpenTab() },
            onClose = closeCard,
            onClearAll = { startClear() },
            onNew = onNew,
            onSettings = onSettings,
            modifier = modifier,
        )
    }
}

@Composable
private fun SwitcherBody(
    anim: SwitcherAnim,
    pagerState: PagerState,
    tabs: List<Tab>,
    ghost: Boolean,
    thumbnails: Map<Long, ImageBitmap>,
    liveShot: LiveShot?,
    pageBounds: Rect?,
    pageSize: IntSize,
    onCardClick: (Long) -> Unit,
    onEmptyTap: () -> Unit,
    onClose: (Long) -> Unit,
    onClearAll: () -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val emptyTap by rememberUpdatedState(onEmptyTap)
    val handoff = anim.phase == Phase.Handoff
    val blocking = anim.phase == Phase.Opening || anim.phase == Phase.Closing || anim.phase == Phase.Clearing

    val background = if (ghost) {
        Brush.verticalGradient(listOf(NeatColors.GhostTop, NeatColors.GhostBottom))
    } else {
        Brush.verticalGradient(listOf(NeatColors.NavyDeep, NeatColors.Navy, NeatColors.NavyTeal))
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // ---- layout, in pixels ----
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
        val barPx = with(density) { BarHeight.toPx() }
        val slotPx = with(density) { (ActionSlotBottom + ActionHeight).toPx() }
        val marginPx = with(density) { BlockMargin.toPx() }
        val headerPx = with(density) { (HeaderHeight + HeaderGap).toPx() }
        val spacingPx = with(density) { CardGap.toPx() }

        // The card has the shape of the page area. Until that is measured, the screen minus bars stands in.
        val aspect = SwitcherMath.pageAspect(pageSize.width, pageSize.height)
            ?: SwitcherMath.pageAspect(width.toInt(), (height - statusTop - navBottom - barPx).toInt())
            ?: 1f

        // Room for the header above the card and for "Clear All Tabs" below it.
        val regionTop = statusTop + marginPx
        val regionBottom = height - navBottom - slotPx - marginPx
        val card = SwitcherMath.fitCard(width * MAX_CARD_WIDTH_FRACTION, regionBottom - regionTop - headerPx, aspect)
        val blockHeight = headerPx + card.height
        val blockTop = regionTop + (regionBottom - regionTop - blockHeight) / 2f

        // ---- background ----
        // While a card zooms to or from the page the backdrop stays solid: if it faded away with the zoom, the
        // live page would show behind the card, full size and already in place, and the zoom would look fake.
        if (!handoff) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = SwitcherMath.backdropAlpha(anim.open.value, zooming = anim.heroId != null) }
                    .background(background),
            )
        }

        // ---- cards; a tap on the empty space around them goes back to the tab that was open ----
        if (!handoff) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { emptyTap() }) },
            ) {
                if (tabs.isEmpty()) {
                    Text(
                        stringResource(R.string.tabs_empty),
                        modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = anim.open.value },
                        color = NeatColors.OnPanelMuted,
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = with(density) { ((width - card.width) / 2f).toDp() }),
                        pageSpacing = CardGap,
                        userScrollEnabled = anim.phase == Phase.Open,
                        key = { index -> tabs[index].id },
                    ) { page ->
                        val tab = tabs[page]
                        TabBlock(
                            tab = tab,
                            thumbnail = thumbnails[tab.id],
                            page = page,
                            pagerState = pagerState,
                            anim = anim,
                            isHero = anim.heroId == tab.id,
                            cardWidth = with(density) { card.width.toDp() },
                            cardHeight = with(density) { card.height.toDp() },
                            blockTop = with(density) { blockTop.toDp() },
                            slidePx = card.width * SLIDE_FRACTION * direction,
                            slotPx = (card.width + spacingPx) * direction,
                            onClick = { onCardClick(tab.id) },
                            onClose = { onClose(tab.id) },
                        )
                    }
                }
            }
        }

        // ---- Clear All Tabs, directly above the "+" button ----
        if (!handoff) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = ActionSlotBottom)
                    .graphicsLayer { alpha = anim.open.value },
            ) {
                if (tabs.isNotEmpty()) ClearAllButton(onClick = onClearAll)
            }
        }

        // ---- bottom bar: the home bar, over the gradient; the tabs button leaves like a tap on the empty space ----
        if (!handoff) {
            BottomBar(
                mode = BarMode.Home,
                onTabs = onEmptyTap,
                onNew = onNew,
                onRight = onSettings,
                modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = anim.open.value },
            )
        }

        // ---- the card that zooms between its place and the page ----
        val heroId = anim.heroId
        val heroIndex = if (heroId == null) -1 else tabs.indexOfFirst { it.id == heroId }
        if (heroId != null && heroIndex >= 0 && pageBounds != null && pageBounds.width > 0f && pageBounds.height > 0f) {
            val rest = remember(heroId, width, card.width, card.height, blockTop) {
                val offsetPages = heroIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
                val distance = abs(offsetPages).coerceIn(0f, 1f)
                val scale = 1f - NEIGHBOUR_SCALE_LOSS * distance
                HeroRest(
                    centerX = width / 2f + direction * offsetPages * (card.width + spacingPx),
                    centerY = blockTop + blockHeight / 2f + scale * headerPx / 2f,
                    scale = scale,
                    alpha = 1f - NEIGHBOUR_ALPHA_LOSS * distance,
                )
            }
            HeroCard(
                tab = tabs[heroIndex],
                shot = liveShot?.takeIf { it.tabId == heroId }?.image ?: thumbnails[heroId],
                page = pageBounds,
                rest = rest,
                cardWidth = card.width,
                cardHeight = card.height,
                anim = anim,
            )
        }

        // ---- no touch gets through while it moves ----
        if (blocking) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
            )
        }
    }
}

/**
 * One tab: the icon and title above, the card below, as one block that scales and fades with its distance
 * from the middle. Drag the block up to close it.
 */
@Composable
private fun TabBlock(
    tab: Tab,
    thumbnail: ImageBitmap?,
    page: Int,
    pagerState: PagerState,
    anim: SwitcherAnim,
    isHero: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    blockTop: Dp,
    slidePx: Float,
    slotPx: Float,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember(tab.id) { Animatable(0f) }
    val density = LocalDensity.current
    val closeDistance = with(density) { CloseDragDistance.toPx() }
    val flyAway = with(density) { 700.dp.toPx() }
    val dragState = rememberDraggableState { delta ->
        // Only upward drags count; the block never moves below where it started.
        scope.launch { offset.snapTo((offset.value + delta).coerceAtMost(0f)) }
    }
    val title = tab.title.ifBlank { UrlUtils.displayHost(tab.url) }
    val closeLabel = stringResource(R.string.cd_close_tab)
    val shape = RoundedCornerShape(CardRadius)

    // "Clear All Tabs": slide off the top, the middle card first and the ones beside it a moment later.
    val clearing = anim.clearing
    LaunchedEffect(clearing) {
        if (clearing) {
            delay(SwitcherMath.clearDelayMs(page - pagerState.currentPage).toLong())
            offset.animateTo(-flyAway, tween(CLEAR_SLIDE_MS, easing = FastOutLinearInEasing))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .padding(top = blockTop)
                .width(cardWidth)
                .graphicsLayer {
                    // How far this block is from the middle, 0 (in it) to 1 (a whole place away or more).
                    val offsetPages = page - pagerState.currentPage - pagerState.currentPageOffsetFraction
                    val distance = abs(offsetPages).coerceIn(0f, 1f)
                    val open = anim.open.value
                    val scale = 1f - NEIGHBOUR_SCALE_LOSS * distance
                    scaleX = scale
                    scaleY = scale
                    translationY = offset.value
                    // The other tabs slide in from the sides when the switcher opens, and out again when it closes.
                    translationX = (1f - open) * distance * sign(offsetPages) * slidePx
                    // After a card was closed, this one slides from the place it stood in into its new place.
                    val stoodAt = anim.closedFrom[tab.id]
                    if (stoodAt != null) {
                        val before = stoodAt - anim.closedMiddle
                        val now = page - pagerState.currentPage
                        translationX += anim.gap.value * (before - now) * slotPx
                    }
                    alpha = (1f - NEIGHBOUR_ALPHA_LOSS * distance) *
                        open *
                        (1f - (-offset.value / flyAway).coerceIn(0f, 1f))
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (SwitcherMath.swipeUpCloses(upDistance = -offset.value, upVelocity = -velocity, minDistance = closeDistance)) {
                            // Let go after a short drag or a flick: the card throws itself off the top, as in Android's recents.
                            offset.animateTo(-flyAway, tween(THROW_MS, easing = FastOutLinearInEasing))
                            onClose()
                        } else {
                            offset.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                        }
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TabHeader(tab = tab, title = title, maxWidth = cardWidth)
            Spacer(Modifier.height(HeaderGap))
            Box(
                Modifier
                    .size(cardWidth, cardHeight)
                    // While this card zooms, the zooming copy is the one on screen.
                    .alpha(if (isHero) 0f else 1f)
                    .clip(shape)
                    .background(if (tab.ghost) NeatColors.PanelGhost else NeatColors.Panel)
                    .semantics {
                        contentDescription = title
                        customActions = listOf(
                            CustomAccessibilityAction(closeLabel) {
                                scope.launch {
                                    offset.animateTo(-flyAway, tween(THROW_MS, easing = FastOutLinearInEasing))
                                    onClose()
                                }
                                true
                            },
                        )
                    }
                    .clickable(onClick = onClick),
            ) {
                if (thumbnail != null) {
                    // The card has the shape of the page, so the picture fits without cropping; Crop only
                    // guards against a picture taken when the page area had another shape.
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                } else {
                    LetterBadge(tab.url, Modifier.align(Alignment.Center), badgeSize = BadgeSize)
                }
            }
        }
    }
}

/** The page's icon (rounded corners, 28 dp) and a one-line title, centred above its card. */
@Composable
private fun TabHeader(tab: Tab, title: String, maxWidth: Dp) {
    Row(
        Modifier
            .height(HeaderHeight)
            .widthIn(max = maxWidth)
            .clearAndSetSemantics { }, // the card says the title for TalkBack
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SiteAvatar(
            url = tab.url,
            avatarSize = HeaderIconSize,
            ghost = tab.ghost,
            shape = RoundedCornerShape(8.dp),
        )
        Text(
            title,
            modifier = Modifier.weight(1f, fill = false),
            color = NeatColors.OnPanel,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The tab's picture, as big as the page area, moved and scaled to where its card is: one transform
 * driven by [SwitcherAnim.open]. The corner radius grows as it shrinks, so it is always [CardRadius] on screen
 * when it sits in its place and square when it covers the page.
 */
@Composable
private fun HeroCard(
    tab: Tab,
    shot: ImageBitmap?,
    page: Rect,
    rest: HeroRest,
    cardWidth: Float,
    cardHeight: Float,
    anim: SwitcherAnim,
) {
    val density = LocalDensity.current
    val restScaleX = rest.scale * cardWidth / page.width
    val restScaleY = rest.scale * cardHeight / page.height
    val radiusPx = with(density) { CardRadius.toPx() }
    val center = page.center

    Box(
        Modifier
            .offset { IntOffset(page.left.roundToInt(), page.top.roundToInt()) }
            .size(with(density) { page.width.toDp() }, with(density) { page.height.toDp() })
            .graphicsLayer {
                val open = anim.open.value
                val zoom = 1f - open // 0 in its place, 1 over the page
                val scaleNowX = lerp(restScaleX, 1f, zoom)
                scaleX = scaleNowX
                scaleY = lerp(restScaleY, 1f, zoom)
                translationX = lerp(rest.centerX - center.x, 0f, zoom)
                translationY = lerp(rest.centerY - center.y, 0f, zoom)
                alpha = lerp(rest.alpha, 1f, zoom) * anim.heroAlpha.value
                clip = true
                // The layer is scaled after it is clipped: divide so that the radius on screen is what we want.
                shape = RoundedCornerShape(radiusPx * open / scaleNowX.coerceAtLeast(0.01f))
            }
            .background(if (tab.ghost) NeatColors.PanelGhost else NeatColors.Panel),
    ) {
        if (shot != null) {
            Image(
                bitmap = shot,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        } else {
            // Drawn as big as the page so that, scaled down, it is the size of the badge on the card.
            LetterBadge(tab.url, Modifier.align(Alignment.Center), badgeSize = BadgeSize / restScaleX)
        }
    }
}

@Composable
private fun ClearAllButton(onClick: () -> Unit) {
    Row(
        Modifier
            .height(ActionHeight)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrayDownGlyph(color = NeatColors.OnPanel, glyphSize = 20.dp)
        Text(
            stringResource(R.string.clear_all_tabs),
            color = NeatColors.OnPanel,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
