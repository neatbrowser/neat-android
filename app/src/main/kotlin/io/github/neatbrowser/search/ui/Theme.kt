package io.github.neatbrowser.search.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colours taken from the gradient of the NEAT logo, on a deep navy. */
object NeatColors {
    val Blue = Color(0xFF04AFED)
    val Cyan = Color(0xFF08FDED)
    val Green = Color(0xFF3BF59B)

    val NavyDeep = Color(0xFF060E18)
    val Navy = Color(0xFF0B1B2B)
    val NavyTeal = Color(0xFF0F3040)

    val PageBackground = Color(0xFF121212)
    val BarSolid = Color(0xFF1F2023)
    val Panel = Color(0xFF2C2D30)
    val Field = Color(0xFF4A4B4F)

    val OnPanel = Color(0xFFE8EAED)
    val OnPanelMuted = Color(0xFF9AA0A6)
}

@Composable
fun NeatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeatColors.Cyan,
            onPrimary = NeatColors.NavyDeep,
            background = NeatColors.Navy,
            surface = NeatColors.Panel,
            onSurface = NeatColors.OnPanel,
        ),
        content = content,
    )
}
