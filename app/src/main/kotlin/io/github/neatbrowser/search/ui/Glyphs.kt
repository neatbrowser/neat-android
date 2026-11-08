package io.github.neatbrowser.search.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neatbrowser.search.browser.Favicons
import io.github.neatbrowser.search.core.UrlUtils

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

/** Two eyes looking down-left: ghost (incognito) mode. */
@Composable
fun EyesGlyph(modifier: Modifier = Modifier, color: Color = Color.White, glyphSize: Dp = 24.dp) {
    Canvas(modifier.size(glyphSize)) {
        val s = size.minDimension
        val eyeW = s * 0.40f
        val eyeH = s * 0.58f
        val stroke = Stroke(width = s * 0.085f)
        for (cx in listOf(s * 0.27f, s * 0.73f)) {
            drawOval(color, Offset(cx - eyeW / 2, s * 0.5f - eyeH / 2), Size(eyeW, eyeH), style = stroke)
            drawCircle(color, radius = s * 0.075f, center = Offset(cx - s * 0.02f, s * 0.5f + s * 0.09f))
        }
    }
}

/** A thumbtack: pin the page. */
@Composable
fun PinGlyph(modifier: Modifier = Modifier, color: Color = Color.White, glyphSize: Dp = 24.dp) {
    Canvas(modifier.size(glyphSize)) {
        val s = size.minDimension
        rotate(degrees = 35f, pivot = Offset(s / 2, s / 2)) {
            // head, body, and the needle
            drawRoundRect(color, Offset(s * 0.30f, s * 0.08f), Size(s * 0.40f, s * 0.13f), CornerRadius(s * 0.05f))
            drawRoundRect(color, Offset(s * 0.38f, s * 0.19f), Size(s * 0.24f, s * 0.33f), CornerRadius(s * 0.04f))
            drawRoundRect(color, Offset(s * 0.24f, s * 0.50f), Size(s * 0.52f, s * 0.11f), CornerRadius(s * 0.05f))
            drawLine(color, Offset(s * 0.5f, s * 0.60f), Offset(s * 0.5f, s * 0.92f), strokeWidth = s * 0.07f)
        }
    }
}

/** An open tray with an arrow pointing down into it: clear all tabs. */
@Composable
fun TrayDownGlyph(modifier: Modifier = Modifier, color: Color = Color.White, glyphSize: Dp = 22.dp) {
    Canvas(modifier.size(glyphSize)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // the tray: a shallow "U"
        val tray = Path().apply {
            moveTo(s * 0.12f, s * 0.58f)
            lineTo(s * 0.12f, s * 0.84f)
            lineTo(s * 0.88f, s * 0.84f)
            lineTo(s * 0.88f, s * 0.58f)
        }
        drawPath(tray, color, style = stroke)

        // the arrow: a stem and a head, pointing down into the tray
        drawLine(color, Offset(s * 0.5f, s * 0.10f), Offset(s * 0.5f, s * 0.62f), strokeWidth = s * 0.10f, cap = StrokeCap.Round)
        val head = Path().apply {
            moveTo(s * 0.30f, s * 0.44f)
            lineTo(s * 0.50f, s * 0.64f)
            lineTo(s * 0.70f, s * 0.44f)
        }
        drawPath(head, color, style = stroke)
    }
}

/** Where [SiteAvatar] finds the site pictures. Null (previews, tests) means the letter badge only. */
val LocalFavicons = staticCompositionLocalOf<Favicons?> { null }

/**
 * The picture of a site: its own icon once it is known, otherwise the letter badge. Showing it never
 * starts a download; icons are fetched when a page has loaded (see [Favicons]). [ghost] picks the
 * memory-only cache of incognito tabs, which is separate from the stored one.
 */
@Composable
fun SiteAvatar(
    url: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 36.dp,
    ghost: Boolean = false,
    shape: Shape = CircleShape,
) {
    val key = UrlUtils.siteKey(url).orEmpty()
    val favicons = LocalFavicons.current
    if (favicons != null && key.isNotEmpty()) {
        LaunchedEffect(key, ghost) { favicons.load(key, ghost) }
    }
    val icon = if (favicons != null && key.isNotEmpty()) favicons.cached(key, ghost) else null
    if (icon != null) {
        // A light plate under the icon: dark logos would vanish on the dark panels.
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier
                .size(avatarSize)
                .clip(shape)
                .background(Color.White)
                .padding(avatarSize * 0.1f),
            contentScale = ContentScale.Fit,
        )
    } else {
        LetterBadge(url, modifier, avatarSize, shape)
    }
}

/** A badge with the first letter of the site, in a colour taken from its name. */
@Composable
fun LetterBadge(
    url: String,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 36.dp,
    shape: Shape = CircleShape,
) {
    val key = UrlUtils.siteKey(url).orEmpty()
    val color = remember(key) {
        val hue = (key.hashCode().toLong().mod(360L)).toFloat()
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.62f)))
    }
    val letter = key.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    Box(
        modifier.size(badgeSize).clip(shape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White, fontSize = (badgeSize.value * 0.46f).sp, fontWeight = FontWeight.SemiBold)
    }
}
