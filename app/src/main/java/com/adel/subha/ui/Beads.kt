package com.adel.subha.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class BeadShape(val w: Float, val h: Float) { ROUND(1f, 1f), OVAL(0.84f, 1.14f), BARREL(0.9f, 1.02f), FLAT(1.08f, 0.82f) }

enum class Finish { GLOSS, AMBER, PEARL, WOOD, BANDED, VEINS, SPECKS, CRYSTAL, METAL }

data class BeadStyle(
    val id: String,
    val name: String,
    val light: Color,
    val base: Color,
    val dark: Color,
    val shape: BeadShape,
    val finish: Finish,
    val accent: Color = Color.White,
    val thread: Color = Gold,
)

val BEAD_STYLES = listOf(
    BeadStyle("emerald", "زمرّد", Color(0xFF7FE0AE), Color(0xFF128A52), Color(0xFF033D22), BeadShape.ROUND, Finish.GLOSS, thread = Gold),
    BeadStyle("amber", "كهرمان", Color(0xFFFFE08A), Color(0xFFE08E1B), Color(0xFF7A3A05), BeadShape.OVAL, Finish.AMBER, accent = Color(0xFFFFD36B), thread = Color(0xFF5B3A1A)),
    BeadStyle("pearl", "لؤلؤ", Color(0xFFFFFFFF), Color(0xFFEDE6DA), Color(0xFFB5AA98), BeadShape.ROUND, Finish.PEARL, thread = Color(0xFFE8E0CC)),
    BeadStyle("yusr", "يُسر أسود", Color(0xFF6B6B6B), Color(0xFF1E1E1E), Color(0xFF000000), BeadShape.OVAL, Finish.GLOSS, thread = Color(0xFF9A8450)),
    BeadStyle("turquoise", "فيروز", Color(0xFFA6F0E6), Color(0xFF2FB5A6), Color(0xFF0E5E57), BeadShape.ROUND, Finish.VEINS, accent = Color(0xFF3D3424), thread = Color(0xFFC9A44E)),
    BeadStyle("sandal", "خشب الصندل", Color(0xFFE2B47C), Color(0xFFA8672F), Color(0xFF4F2A0E), BeadShape.BARREL, Finish.WOOD, accent = Color(0xFF5A2E10), thread = Color(0xFF3B2410)),
    BeadStyle("agate", "عقيق يماني", Color(0xFFFF8A73), Color(0xFFB3261E), Color(0xFF4A0705), BeadShape.OVAL, Finish.BANDED, accent = Color(0xFFFFC7B5), thread = Color(0xFFD9B95B)),
    BeadStyle("crystal", "كريستال", Color(0xFFFFFFFF), Color(0xFFBFE3F2), Color(0xFF5C8FA8), BeadShape.ROUND, Finish.CRYSTAL, thread = Color(0xFFDDE7EE)),
    BeadStyle("gold", "ذهبي", Color(0xFFFFF1B0), Color(0xFFD4A537), Color(0xFF6E4D0B), BeadShape.FLAT, Finish.METAL, accent = Color(0xFF7A5510), thread = Color(0xFF8B6A1E)),
    BeadStyle("silver", "فضّي", Color(0xFFFFFFFF), Color(0xFFB9BEC4), Color(0xFF4D545C), BeadShape.FLAT, Finish.METAL, accent = Color(0xFF5E656D), thread = Color(0xFFCFD3D8)),
    BeadStyle("lapis", "لازورد", Color(0xFF7D9BFF), Color(0xFF1F3FA8), Color(0xFF07154A), BeadShape.ROUND, Finish.SPECKS, accent = Color(0xFFF2CF63), thread = Color(0xFFD9B95B)),
    BeadStyle("rose", "مرجان", Color(0xFFFFD0D6), Color(0xFFEF7E8F), Color(0xFF8E2A3B), BeadShape.BARREL, Finish.GLOSS, thread = Color(0xFFF1E3C4)),
)

fun beadStyle(id: String) = BEAD_STYLES.firstOrNull { it.id == id } ?: BEAD_STYLES.first()

private fun shapePath(c: Offset, rx: Float, ry: Float, shape: BeadShape): Path = Path().apply {
    val r = Rect(c.x - rx, c.y - ry, c.x + rx, c.y + ry)
    when (shape) {
        BeadShape.BARREL -> addRoundRect(RoundRect(r, CornerRadius(rx * 0.62f, ry * 0.5f)))
        else -> addOval(r)
    }
}

/** نصف القطر الأفقي/العمودي للخرزة بحجم أساسي r. */
fun BeadStyle.rx(r: Float) = r * shape.w
fun BeadStyle.ry(r: Float) = r * shape.h

// مواضع ثابتة للنقاط الذهبية في اللازورد (نسبية)
private val SPECKS = listOf(
    0.32f to -0.41f, -0.48f to 0.12f, 0.12f to 0.55f, 0.52f to 0.18f, -0.15f to -0.6f,
    -0.38f to 0.48f, 0.05f to 0.05f, 0.62f to -0.12f, -0.62f to -0.2f, 0.3f to 0.38f,
)

/** يرسم خرزة واحدة متلألئة في المركز c. */
fun DrawScope.drawBead(style: BeadStyle, c: Offset, r: Float, alpha: Float = 1f) {
    if (alpha <= 0.01f) return
    val rx = style.rx(r)
    val ry = style.ry(r)
    val path = shapePath(c, rx, ry, style.shape)
    val big = max(rx, ry)

    // ظل ناعم
    drawPath(shapePath(c + Offset(rx * 0.10f, ry * 0.16f), rx, ry, style.shape), Color.Black.copy(alpha = 0.32f), alpha = alpha)

    // الجسم
    val bodyAlpha = if (style.finish == Finish.CRYSTAL || style.finish == Finish.AMBER) 0.94f else 1f
    drawPath(
        path,
        Brush.radialGradient(
            0f to style.light, 0.45f to style.base, 1f to style.dark,
            center = c + Offset(-rx * 0.38f, -ry * 0.42f), radius = big * 1.55f,
        ),
        alpha = alpha * bodyAlpha,
    )

    clipPath(path) {
        when (style.finish) {
            Finish.AMBER -> {
                // ضوء يعبر الكهرمان فيتوهج أسفله
                drawCircle(
                    Brush.radialGradient(listOf(style.accent.copy(alpha = 0.75f), Color.Transparent), center = c + Offset(rx * 0.25f, ry * 0.42f), radius = big * 0.8f),
                    radius = big, center = c, alpha = alpha,
                )
                for (k in 0..2) {
                    val yy = c.y - ry * 0.3f + k * ry * 0.33f
                    drawLine(style.dark.copy(alpha = 0.18f), Offset(c.x - rx, yy), Offset(c.x + rx, yy + ry * 0.12f), strokeWidth = ry * 0.07f, alpha = alpha)
                }
            }
            Finish.PEARL -> {
                drawCircle(
                    Brush.sweepGradient(listOf(Color(0x33FFC4E1), Color(0x33B8E2FF), Color(0x33FFF3B0), Color(0x33C9FFD9), Color(0x33FFC4E1)), center = c),
                    radius = big, center = c, alpha = alpha,
                )
            }
            Finish.WOOD -> {
                for (k in -4..4) {
                    val yy = c.y + k * ry * 0.22f
                    val p = Path().apply {
                        moveTo(c.x - rx, yy)
                        cubicTo(c.x - rx * 0.3f, yy - ry * 0.12f, c.x + rx * 0.3f, yy + ry * 0.12f, c.x + rx, yy - ry * 0.04f)
                    }
                    drawPath(p, style.accent.copy(alpha = 0.28f), style = Stroke(width = ry * 0.035f), alpha = alpha)
                }
            }
            Finish.BANDED -> {
                for (k in -3..3) {
                    val yy = c.y + k * ry * 0.3f
                    val p = Path().apply {
                        moveTo(c.x - rx, yy + ry * 0.1f)
                        quadraticTo(c.x, yy - ry * 0.18f, c.x + rx, yy + ry * 0.1f)
                    }
                    drawPath(p, style.accent.copy(alpha = if (k % 2 == 0) 0.22f else 0.12f), style = Stroke(width = ry * 0.09f), alpha = alpha)
                }
            }
            Finish.VEINS -> {
                val s = Stroke(width = r * 0.045f)
                val v1 = Path().apply { moveTo(c.x - rx, c.y - ry * 0.2f); cubicTo(c.x - rx * 0.4f, c.y - ry * 0.5f, c.x, c.y + ry * 0.2f, c.x + rx * 0.7f, c.y - ry * 0.1f) }
                val v2 = Path().apply { moveTo(c.x - rx * 0.6f, c.y + ry * 0.8f); cubicTo(c.x - rx * 0.2f, c.y + ry * 0.3f, c.x + rx * 0.3f, c.y + ry * 0.6f, c.x + rx, c.y + ry * 0.3f) }
                val v3 = Path().apply { moveTo(c.x + rx * 0.1f, c.y - ry); quadraticTo(c.x + rx * 0.2f, c.y - ry * 0.5f, c.x + rx * 0.6f, c.y - ry * 0.45f) }
                listOf(v1, v2, v3).forEach { drawPath(it, style.accent.copy(alpha = 0.55f), style = s, alpha = alpha) }
            }
            Finish.SPECKS -> {
                SPECKS.forEachIndexed { i, (sx, sy) ->
                    drawCircle(style.accent.copy(alpha = 0.85f), radius = r * (0.035f + (i % 3) * 0.015f), center = c + Offset(sx * rx, sy * ry), alpha = alpha)
                }
            }
            Finish.CRYSTAL -> {
                // أوجه مقطوعة
                val n = 8
                for (k in 0 until n) {
                    val a1 = Math.PI * 2 * k / n
                    val a2 = Math.PI * 2 * (k + 1) / n
                    val p = Path().apply {
                        moveTo(c.x + rx * 0.45f * cos(a1).toFloat(), c.y + ry * 0.45f * sin(a1).toFloat())
                        lineTo(c.x + rx * 1.05f * cos(a1).toFloat(), c.y + ry * 1.05f * sin(a1).toFloat())
                        lineTo(c.x + rx * 1.05f * cos(a2).toFloat(), c.y + ry * 1.05f * sin(a2).toFloat())
                        lineTo(c.x + rx * 0.45f * cos(a2).toFloat(), c.y + ry * 0.45f * sin(a2).toFloat())
                        close()
                    }
                    drawPath(p, Color.White.copy(alpha = if (k % 2 == 0) 0.22f else 0.04f), alpha = alpha)
                    drawPath(p, Color.White.copy(alpha = 0.25f), style = Stroke(width = r * 0.02f), alpha = alpha)
                }
            }
            Finish.METAL -> {
                drawLine(style.accent.copy(alpha = 0.55f), Offset(c.x - rx, c.y), Offset(c.x + rx, c.y), strokeWidth = ry * 0.08f, alpha = alpha)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(c.x - rx, c.y - ry * 0.08f), Offset(c.x + rx, c.y - ry * 0.08f), strokeWidth = ry * 0.03f, alpha = alpha)
            }
            Finish.GLOSS -> Unit
        }
        // تعتيم الحافة
        drawCircle(
            Brush.radialGradient(0.65f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.28f), center = c, radius = big * 1.05f),
            radius = big * 1.1f, center = c, alpha = alpha,
        )
    }

    // ثقب الخيط أعلى الخرزة وأسفلها
    drawOval(style.dark.copy(alpha = 0.55f), topLeft = Offset(c.x - rx * 0.16f, c.y - ry - ry * 0.02f), size = Size(rx * 0.32f, ry * 0.12f), alpha = alpha)

    // اللمعة
    val hl = c + Offset(-rx * 0.36f, -ry * 0.46f)
    drawOval(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), center = hl, radius = rx * 0.32f),
        topLeft = Offset(hl.x - rx * 0.3f, hl.y - ry * 0.2f), size = Size(rx * 0.6f, ry * 0.4f), alpha = alpha,
    )
    drawCircle(Color.White.copy(alpha = 0.18f), radius = r * 0.09f, center = c + Offset(rx * 0.42f, ry * 0.5f), alpha = alpha)
}
