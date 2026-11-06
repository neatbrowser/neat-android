package io.github.neatbrowser.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.neatbrowser.search.R

enum class BarMode { Home, Page }

/**
 * Three equal columns: tabs, new tab, and settings (home) or the page menu (page).
 * On a page the bar is solid; on the home screen it floats over the gradient.
 */
@Composable
fun BottomBar(
    mode: BarMode,
    onTabs: () -> Unit,
    onNew: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (mode == BarMode.Page) NeatColors.BarSolid else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .navigationBarsPadding()
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onClick = onTabs) {
                TabsGlyph(color = Color.White.copy(alpha = 0.92f))
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = 86.dp, height = 48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClick = onNew),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_new_tab),
                    tint = Color.White,
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onClick = onRight) {
                if (mode == BarMode.Home) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = Color.White.copy(alpha = 0.92f),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.cd_page_menu),
                        tint = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}
