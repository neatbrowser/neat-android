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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.neatbrowser.search.R
import io.github.neatbrowser.search.core.Omnibox
import io.github.neatbrowser.search.core.OmniboxAction
import io.github.neatbrowser.search.core.SearchEngine

private val PanelShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/**
 * The panel that slides up from the bottom when "+" is pressed. The keyboard opens with it.
 * [onOpen] receives the address to load.
 */
@Composable
fun SearchSheet(
    visible: Boolean,
    engine: SearchEngine,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
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
            SearchPanel(engine = engine, onOpen = onOpen)
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
private fun SearchPanel(engine: SearchEngine, onOpen: (String) -> Unit) {
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

    val action = Omnibox.resolve(query, engine)

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .clip(PanelShape)
            .background(NeatColors.Panel)
            .padding(start = 14.dp, end = 14.dp, top = 18.dp),
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(32.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { action?.let { onOpen(it.url) } }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = NeatColors.Field,
                unfocusedContainerColor = NeatColors.Field,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = NeatColors.Blue,
                focusedTextColor = NeatColors.OnPanel,
                unfocusedTextColor = NeatColors.OnPanel,
                focusedPlaceholderColor = NeatColors.OnPanelMuted,
                unfocusedPlaceholderColor = NeatColors.OnPanelMuted,
            ),
        )

        // The list sits above the keyboard (or above the navigation bar when the keyboard is hidden).
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            Spacer(Modifier.height(10.dp))
            if (action != null) {
                if (action is OmniboxAction.Navigate) {
                    SuggestionRow(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        primary = action.url,
                        secondary = stringResource(R.string.go_to),
                        onClick = { onOpen(action.url) },
                    )
                }
                SuggestionRow(
                    icon = Icons.Filled.Search,
                    primary = query.trim(),
                    secondary = stringResource(R.string.search_with, engine.name),
                    onClick = { onOpen(engine.searchUrl(query.trim())) },
                )
            }
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
