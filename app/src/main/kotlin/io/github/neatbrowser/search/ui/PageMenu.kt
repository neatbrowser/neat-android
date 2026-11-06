package io.github.neatbrowser.search.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.neatbrowser.search.R
import io.github.neatbrowser.search.browser.PageState
import io.github.neatbrowser.search.core.UrlUtils

private val PanelShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/**
 * The menu behind the up-arrow. Step 1 has only the address row (back, forward, site, copy
 * link, reload); Pin, Find, Share, Zoom, Desktop Site and Site Settings come with step 3.
 */
@Composable
fun PageMenu(
    visible: Boolean,
    page: PageState,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onCopyLink: () -> Unit,
    onReload: () -> Unit,
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(NeatColors.Panel)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 18.dp),
            ) {
                AddressRow(page, onBack, onForward, onCopyLink, onReload)
            }
        }
    }
}

@Composable
private fun AddressRow(
    page: PageState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onCopyLink: () -> Unit,
    onReload: () -> Unit,
) {
    fun tint(enabled: Boolean) = if (enabled) NeatColors.OnPanel else NeatColors.OnPanelMuted.copy(alpha = 0.45f)

    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(NeatColors.Field)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = page.canGoBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = tint(page.canGoBack))
        }
        IconButton(onClick = onForward, enabled = page.canGoForward) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.cd_forward), tint = tint(page.canGoForward))
        }
        Text(
            text = page.url?.let { UrlUtils.displayHost(it) }.orEmpty(),
            modifier = Modifier.weight(1f),
            color = NeatColors.OnPanel,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onCopyLink) {
            LinkGlyph(color = NeatColors.OnPanel)
        }
        IconButton(onClick = onReload) {
            Icon(Icons.Filled.Refresh, stringResource(R.string.cd_reload), tint = NeatColors.OnPanel)
        }
    }
}
