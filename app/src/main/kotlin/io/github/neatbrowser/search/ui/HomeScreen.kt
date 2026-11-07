package io.github.neatbrowser.search.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.neatbrowser.search.R

/**
 * Navy gradient with a soft blue glow, and the NEAT logo in the middle. In ghost mode the
 * gradient is near black, the logo is dimmed and an "Incognito" label shows.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier, ghost: Boolean = false) {
    val colors = if (ghost) {
        listOf(NeatColors.GhostTop, NeatColors.GhostBottom)
    } else {
        listOf(NeatColors.NavyDeep, NeatColors.Navy, NeatColors.NavyTeal)
    }
    Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
        if (!ghost) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(NeatColors.Blue.copy(alpha = 0.20f), Color.Transparent))),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.neat_logo),
                contentDescription = null,
                modifier = Modifier.size(150.dp).alpha(if (ghost) 0.35f else 1f),
            )
            if (ghost) {
                Spacer(Modifier.height(20.dp))
                EyesGlyph(color = NeatColors.Cyan, glyphSize = 30.dp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.incognito), color = NeatColors.Cyan)
            }
        }
    }
}
