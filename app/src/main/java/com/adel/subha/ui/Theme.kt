package com.adel.subha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.adel.subha.R
import kotlin.math.cos
import kotlin.math.sin

val RoyalGreen = Color(0xFF0A3F2A)
val RoyalGreenDeep = Color(0xFF062B1C)
val RoyalGreenLight = Color(0xFF13603F)
val Gold = Color(0xFFD9B95B)
val GoldPale = Color(0xFFF3E6BE)
val Cream = Color(0xFFF7EFDA)

val Markazi = FontFamily(
    Font(R.font.markazi_regular, FontWeight.Normal),
    Font(R.font.markazi_medium, FontWeight.Medium),
    Font(R.font.markazi_semibold, FontWeight.SemiBold),
    Font(R.font.markazi_bold, FontWeight.Bold),
)

private fun Typography.withFamily(f: FontFamily): Typography {
    fun TextStyle.m(scale: Float = 1.18f) = copy(fontFamily = f, fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return Typography(
        displayLarge = displayLarge.m(), displayMedium = displayMedium.m(), displaySmall = displaySmall.m(),
        headlineLarge = headlineLarge.m(), headlineMedium = headlineMedium.m(), headlineSmall = headlineSmall.m(),
        titleLarge = titleLarge.m(), titleMedium = titleMedium.m(), titleSmall = titleSmall.m(),
        bodyLarge = bodyLarge.m(), bodyMedium = bodyMedium.m(), bodySmall = bodySmall.m(),
        labelLarge = labelLarge.m(), labelMedium = labelMedium.m(), labelSmall = labelSmall.m(),
    )
}

private val Scheme = darkColorScheme(
    primary = Gold, onPrimary = RoyalGreenDeep,
    primaryContainer = Color(0xFF3B5A2E), onPrimaryContainer = GoldPale,
    secondary = GoldPale, onSecondary = RoyalGreenDeep,
    background = RoyalGreen, onBackground = Cream,
    surface = Color(0xFF0D4A32), onSurface = Cream,
    surfaceVariant = Color(0xFF145A3D), onSurfaceVariant = Color(0xFFD7CFB5),
    surfaceContainer = Color(0xFF0E4D34), surfaceContainerHigh = Color(0xFF125539),
    surfaceContainerHighest = Color(0xFF175F40), surfaceContainerLow = Color(0xFF0C4630),
    outline = Color(0xFF8C8461), outlineVariant = Color(0xFF3E6A50),
)

@Composable
fun SubhaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography().withFamily(Markazi)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

/** الخلفية الخضراء الملكية مع نقش نجمي ثماني خافت. */
@Composable
fun RoyalBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(RoyalGreenLight, RoyalGreen, RoyalGreenDeep), radius = 1900f))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = 96.dp.toPx()
            val stroke = Stroke(width = 1.dp.toPx())
            val col = Gold.copy(alpha = 0.07f)
            var y = 0f
            var row = 0
            while (y < size.height + cell) {
                var x = if (row % 2 == 0) 0f else cell / 2
                while (x < size.width + cell) {
                    drawPath(star(Offset(x, y), cell * 0.30f), col, style = stroke)
                    x += cell
                }
                y += cell * 0.87f; row++
            }
        }
        content()
    }
}

private fun star(c: Offset, r: Float): Path = Path().apply {
    // نجمة ثمانية: مربعان متراكبان
    for (k in 0 until 16) {
        val a = Math.PI / 8 * k - Math.PI / 2
        val rr = if (k % 2 == 0) r else r * 0.72f
        val p = Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat())
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}
