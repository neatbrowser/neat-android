package io.github.neatbrowser.search.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.neatbrowser.search.R

/** Navy gradient with a soft blue glow, and the NEAT logo in the middle. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NeatColors.NavyDeep, NeatColors.Navy, NeatColors.NavyTeal))),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(NeatColors.Blue.copy(alpha = 0.20f), Color.Transparent))),
        )
        Image(
            painter = painterResource(R.drawable.neat_logo),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(150.dp),
        )
    }
}
