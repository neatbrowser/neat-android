package io.github.neatbrowser.search.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neatbrowser.search.R
import io.github.neatbrowser.search.core.History
import io.github.neatbrowser.search.core.Omnibox
import io.github.neatbrowser.search.core.OmniboxAction
import io.github.neatbrowser.search.core.Pins
import io.github.neatbrowser.search.core.SearchEngine
import io.github.neatbrowser.search.core.UrlUtils

private val PanelShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
private const val MAX_PINS_SHOWN = 10
private const val MAX_HISTORY_EMPTY = 20
private const val MAX_HISTORY_QUERY = 8

/**
 * The panel that slides up from the bottom when "+" is pressed. The keyboard opens with it.
 * [onOpen] receives the address to open in a new tab. The eyes button switches ghost (incognito)
 * mode; in ghost mode the panel is darker and neither pins nor history are read or shown.
 */
@Composable
fun SearchSheet(
    visible: Boolean,
    ghost: Boolean,
    engine: SearchEngine,
    pins: Pins,
    history: History,
    onToggleGhost: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onRemovePin: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Scrim(onDismiss)
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            SearchPanel(
                ghost = ghost,
                engine = engine,
                pins = pins,
                history = history,
                onToggleGhost = onToggleGhost,
                onOpen = onOpen,
                onRemovePin = onRemovePin,
                onRemoveHistory = onRemoveHistory,
                onClearHistory = onClearHistory,
            )
        }
    }
}

@Composable
internal fun Scrim(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}

@Composable
private fun SearchPanel(
    ghost: Boolean,
    engine: SearchEngine,
    pins: Pins,
    history: History,
    onToggleGhost: () -> Unit,
    onOpen: (String) -> Unit,
    onRemovePin: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    DisposableEffect(Unit) {
        onDispose { keyboard?.hide() }
    }

    val trimmed = query.trim()
    val action = Omnibox.resolve(query, engine)

    // Ghost mode never reads pins or history, so nothing from the normal profile shows here.
    val pinned = remember(trimmed, pins, ghost) {
        if (ghost) emptyList() else pins.search(trimmed).take(MAX_PINS_SHOWN)
    }
    val visited = remember(trimmed, pins, history, ghost) {
        if (ghost) {
            emptyList()
        } else {
            val limit = if (trimmed.isEmpty()) MAX_HISTORY_EMPTY else MAX_HISTORY_QUERY
            history.search(trimmed, limit + pins.items.size).filter { !pins.contains(it.url) }.take(limit)
        }
    }

    val panelColor = if (ghost) NeatColors.PanelGhost else NeatColors.Panel
    val fieldColor = if (ghost) NeatColors.FieldGhost else NeatColors.Field

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .clip(PanelShape)
            .background(panelColor)
            .padding(start = 14.dp, end = 14.dp, top = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(32.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { action?.let { onOpen(it.url) } }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = fieldColor,
                    unfocusedContainerColor = fieldColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NeatColors.Blue,
                    focusedTextColor = NeatColors.OnPanel,
                    unfocusedTextColor = NeatColors.OnPanel,
                    focusedPlaceholderColor = NeatColors.OnPanelMuted,
                    unfocusedPlaceholderColor = NeatColors.OnPanelMuted,
                ),
            )
            GhostToggle(ghost = ghost, fieldColor = fieldColor, onClick = onToggleGhost)
        }

        // The list sits above the keyboard (or above the navigation bar when the keyboard is hidden).
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            item { Spacer(Modifier.height(10.dp)) }
            if (action != null) {
                if (action is OmniboxAction.Navigate) {
                    item(key = "go") {
                        SuggestionRow(
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            primary = action.url,
                            secondary = stringResource(R.string.go_to),
                            onClick = { onOpen(action.url) },
                        )
                    }
                }
                item(key = "search") {
                    SuggestionRow(
                        icon = Icons.Filled.Search,
                        primary = trimmed,
                        secondary = stringResource(R.string.search_with, engine.name),
                        onClick = { onOpen(engine.searchUrl(trimmed)) },
                    )
                }
            }
            if (ghost && trimmed.isEmpty()) {
                item(key = "ghost-notice") { GhostNotice() }
            }
            if (pinned.isNotEmpty()) {
                item(key = "pinned-header") { SectionHeader(stringResource(R.string.pinned)) }
                items(pinned, key = { "pin:" + it.url }) { pin ->
                    SiteRow(pin.url, pin.title, onClick = { onOpen(pin.url) }, onRemove = { onRemovePin(pin.url) })
                }
            }
            if (visited.isNotEmpty()) {
                item(key = "history-header") {
                    SectionHeader(
                        title = stringResource(R.string.history),
                        actionLabel = if (trimmed.isEmpty()) stringResource(R.string.clear) else null,
                        onAction = onClearHistory,
                    )
                }
                items(visited, key = { "history:" + it.url }) { entry ->
                    SiteRow(entry.url, entry.title, onClick = { onOpen(entry.url) }, onRemove = { onRemoveHistory(entry.url) })
                }
            }
        }
    }
}

@Composable
private fun GhostToggle(ghost: Boolean, fieldColor: Color, onClick: () -> Unit) {
    val label = stringResource(R.string.cd_ghost_mode)
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (ghost) NeatColors.Cyan else fieldColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        EyesGlyph(color = if (ghost) NeatColors.NavyDeep else NeatColors.OnPanel, glyphSize = 26.dp)
    }
}

@Composable
private fun GhostNotice() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EyesGlyph(color = NeatColors.Cyan, glyphSize = 40.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.incognito_title),
            color = NeatColors.OnPanel,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.incognito_hint),
            color = NeatColors.OnPanelMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = NeatColors.OnPanelMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (actionLabel != null) {
            Text(
                actionLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = NeatColors.Cyan,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SuggestionRow(icon: ImageVector, primary: String, secondary: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = NeatColors.OnPanelMuted, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(primary, color = NeatColors.OnPanel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(secondary, color = NeatColors.OnPanelMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A pinned or visited page: round site badge, title and host, and a button that removes it. */
@Composable
private fun SiteRow(url: String, title: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val removeLabel = stringResource(R.string.cd_remove)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SiteAvatar(url, avatarSize = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(
                title.ifBlank { UrlUtils.displayHost(url) },
                color = NeatColors.OnPanel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(UrlUtils.displayHost(url), color = NeatColors.OnPanelMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Close, contentDescription = removeLabel, tint = NeatColors.OnPanelMuted, modifier = Modifier.size(18.dp))
        }
    }
}
