package io.github.neatbrowser.search.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Two tilted, overlapping rounded squares: the tab switcher. */
@Composable
fun TabsGlyph(modifier: Modifier = Modifier, color: Color = Color.White, glyphSize: Dp = 28.dp) {
    Canvas(modifier.size(glyphSize)) {
        val s = size.minDimension
        val side = s * 0.56f
        val corner = CornerRadius(s * 0.16f)
        val box = Size(side, side)

        val backTopLeft = Offset(s * 0.36f, s * 0.06f)
        rotate(degrees = 14f, pivot = backTopLeft + Offset(side / 2, side / 2)) {
            drawRoundRect(color.copy(alpha = color.alpha * 0.55f), backTopLeft, box, corner)
        }
        val frontTopLeft = Offset(s * 0.08f, s * 0.38f)
        rotate(degrees = -14f, pivot = frontTopLeft + Offset(side / 2, side / 2)) {
            drawRoundRect(color, frontTopLeft, box, corner)
        }
    }
}

/** Two linked rings: copy link. */
@Composable
fun LinkGlyph(modifier: Modifier = Modifier, color: Color = Color.White, glyphSize: Dp = 24.dp) {
    Canvas(modifier.size(glyphSize)) {
        val s = size.minDimension
        val w = s * 0.50f
        val h = s * 0.28f
        val stroke = Stroke(width = s * 0.09f)
        val corner = CornerRadius(h / 2)
        rotate(degrees = -45f) {
            drawRoundRect(color, Offset(s * 0.5f - w * 0.78f, s * 0.5f - h / 2), Size(w, h), corner, stroke)
            drawRoundRect(color, Offset(s * 0.5f - w * 0.22f, s * 0.5f - h / 2), Size(w, h), corner, stroke)
        }
    }
}
