package io.github.neatbrowser.search.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neatbrowser.search.R
import io.github.neatbrowser.search.core.Tab
import io.github.neatbrowser.search.core.UrlUtils
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private const val CARD_ASPECT = 0.56f // width / height, close to a phone screen

/**
 * Every open tab of the current mode as a large card. Swipe sideways to move between cards, up to
 * close one; tap a card (or Done) to go to it. The eyes button switches ghost mode.
 */
@Composable
fun TabSwitcher(
    visible: Boolean,
    tabs: List<Tab>,
    selectedId: Long?,
    ghost: Boolean,
    thumbnails: Map<Long, ImageBitmap>,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onClearAll: () -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
    onToggleGhost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.94f),
        exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.94f),
    ) {
        val background = if (ghost) {
            Brush.verticalGradient(listOf(NeatColors.GhostTop, NeatColors.GhostBottom))
        } else {
            Brush.verticalGradient(listOf(NeatColors.NavyDeep, NeatColors.Navy, NeatColors.NavyTeal))
        }
        val pagerState = rememberPagerState(
            initialPage = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
            pageCount = { tabs.size },
        )
        Column(Modifier.fillMaxSize().background(background)) {
            TopRow(count = tabs.size, ghost = ghost, onToggleGhost = onToggleGhost)

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (tabs.isEmpty()) {
                    Text(
                        stringResource(R.string.tabs_empty),
                        modifier = Modifier.align(Alignment.Center),
                        color = NeatColors.OnPanelMuted,
                    )
                } else {
                    val verticalMargin = 12.dp
                    val cardHeightMax = maxHeight - verticalMargin * 2
                    val cardWidth = minOf(cardHeightMax * CARD_ASPECT, maxWidth * 0.74f)
                    val cardHeight = cardWidth / CARD_ASPECT
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                        pageSpacing = 14.dp,
                        key = { index -> tabs[index].id },
                    ) { page ->
                        val tab = tabs[page]
                        val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val scale = 1f - 0.10f * distance
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 1f - 0.35f * distance
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            TabCard(
                                tab = tab,
                                thumbnail = thumbnails[tab.id],
                                selected = tab.id == selectedId,
                                modifier = Modifier.width(cardWidth).height(cardHeight),
                                onClick = { onSelect(tab.id) },
                                onClose = { onClose(tab.id) },
                            )
                        }
                    }
                }
            }

            BottomRow(
                hasTabs = tabs.isNotEmpty(),
                onClearAll = onClearAll,
                onNew = onNew,
                onDone = {
                    val current = tabs.getOrNull(pagerState.currentPage)
                    if (current != null) onSelect(current.id) else onDismiss()
                },
            )
        }
    }
}

@Composable
private fun TopRow(count: Int, ghost: Boolean, onToggleGhost: () -> Unit) {
    val context = LocalContext.current
    val label = stringResource(R.string.cd_ghost_mode)
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            context.resources.getQuantityString(R.plurals.tabs_count, count, count),
            modifier = Modifier.weight(1f),
            color = NeatColors.OnPanel,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (ghost) NeatColors.Cyan else Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onToggleGhost)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            EyesGlyph(color = if (ghost) NeatColors.NavyDeep else NeatColors.OnPanel, glyphSize = 24.dp)
        }
    }
}

@Composable
private fun BottomRow(hasTabs: Boolean, onClearAll: () -> Unit, onNew: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (hasTabs) {
                TextAction(stringResource(R.string.clear_all_tabs), onClearAll)
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val label = stringResource(R.string.cd_new_tab)
            Box(
                Modifier
                    .size(width = 86.dp, height = 48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClick = onNew)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextAction(stringResource(R.string.done), onDone, bold = true)
        }
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit, bold: Boolean = false) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        color = NeatColors.Cyan,
        fontSize = 15.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/** One tab: its picture (or a badge while there is none), title and site; drag up to close. */
@Composable
private fun TabCard(
    tab: Tab,
    thumbnail: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offset = remember(tab.id) { Animatable(0f) }
    val density = LocalDensity.current
    val closeDistance = with(density) { 110.dp.toPx() }
    val flyAway = with(density) { 700.dp.toPx() }
    val dragState = rememberDraggableState { delta ->
        // Only upward drags count; the card never moves below where it started.
        scope.launch { offset.snapTo((offset.value + delta).coerceAtMost(0f)) }
    }
    val shape = RoundedCornerShape(28.dp)
    val closeLabel = stringResource(R.string.cd_close_tab)

    Box(
        modifier
            .graphicsLayer {
                translationY = offset.value
                alpha = 1f - (-offset.value / flyAway).coerceIn(0f, 1f)
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (offset.value < -closeDistance || velocity < -1800f) {
                        offset.animateTo(-flyAway, tween(180))
                        onClose()
                    } else {
                        offset.animateTo(0f, tween(160))
                    }
                },
            )
            .clip(shape)
            .background(if (tab.ghost) NeatColors.PanelGhost else NeatColors.Panel)
            .then(
                if (selected) Modifier.border(BorderStroke(2.dp, NeatColors.Cyan), shape) else Modifier,
            )
            .clickable(onClick = onClick),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        } else {
            SiteAvatar(tab.url, modifier = Modifier.align(Alignment.Center), avatarSize = 72.dp)
        }

        // Title strip at the bottom, readable over any picture.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                .padding(start = 14.dp, end = 14.dp, top = 36.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SiteAvatar(tab.url, avatarSize = 24.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    tab.title.ifBlank { UrlUtils.displayHost(tab.url) },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    UrlUtils.displayHost(tab.url),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClose)
                .semantics { contentDescription = closeLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
