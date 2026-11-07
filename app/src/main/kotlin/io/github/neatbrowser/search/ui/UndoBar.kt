package io.github.neatbrowser.search.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.neatbrowser.search.R
import kotlinx.coroutines.delay

private const val UNDO_WINDOW_MS = 6000L

/**
 * "Tabs closed · Undo". [token] is any value that changes with every clear; the bar hides itself and
 * calls [onExpire] when the time for Undo has passed.
 */
@Composable
fun UndoBar(
    visible: Boolean,
    token: Any?,
    onUndo: () -> Unit,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(token, visible) {
        if (visible) {
            delay(UNDO_WINDOW_MS)
            onExpire()
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(NeatColors.Panel)
                .padding(start = 22.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.tabs_cleared), color = NeatColors.OnPanel)
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Text(stringResource(R.string.undo), color = NeatColors.Cyan, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
