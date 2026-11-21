package com.adel.subha.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun mod100(i: Int) = ((i % 100) + 100) % 100
private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)

/**
 * هندسة الخيط: كل «وحدة» خرزةٌ رقمها i، وفوقها فاصلة إن كانت الخرزة ٣٣ أو ٦٦،
 * والخرزة رقم ١٠٠ هي المئذنة (الشاهد).
 */
class StrandGeom(val art: BeadArt, val w: Float, val h: Float, beadW: Float, val topBase: Float, val botTop: Float, val px1: Float) {
    val s = beadW / art.beads[0].width
    val cx = w / 2
    private fun variant(i: Int) = (((i * 7 + i / 3) % 3) + 3) % 3
    fun item(i: Int): ImageBitmap = if (mod100(i) == 0) art.imam else art.beads[variant(i)]
    fun hasDisc(i: Int) = mod100(i) == 33 || mod100(i) == 66
    val discH get() = art.disc.height * s
    fun unitH(i: Int) = item(i).height * s + (if (hasDisc(i)) discH else 0f) + px1
}

private fun DrawScope.img(g: StrandGeom, im: ImageBitmap, top: Float) {
    val w = im.width * g.s
    val h = im.height * g.s
    drawImage(
        im,
        srcOffset = IntOffset.Zero, srcSize = IntSize(im.width, im.height),
        dstOffset = IntOffset((g.cx - w / 2).roundToInt(), top.roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

private fun DrawScope.unit(g: StrandGeom, i: Int, top: Float) {
    var y = top
    if (g.hasDisc(i)) { img(g, g.art.disc, y); y += g.discH }
    img(g, g.item(i), y)
}

/** خيط مفتول. */
private fun DrawScope.rope(g: StrandGeom, color: Color) {
    val tw = 2.6.dp.toPx()
    drawLine(color.copy(alpha = 0.9f), Offset(g.cx, 0f), Offset(g.cx, size.height), strokeWidth = tw)
    val step = 4.dp.toPx()
    var y = 0f
    val light = Color.White.copy(alpha = 0.35f)
    val dark = Color.Black.copy(alpha = 0.35f)
    while (y < size.height) {
        drawLine(light, Offset(g.cx - tw / 2, y), Offset(g.cx + tw / 2, y + step * 0.6f), strokeWidth = 0.9.dp.toPx())
        drawLine(dark, Offset(g.cx - tw / 2, y + step * 0.5f), Offset(g.cx + tw / 2, y + step * 1.1f), strokeWidth = 0.7.dp.toPx())
        y += step
    }
}

/**
 * يرسم الخيط كله: المجموعة العليا (ما لم يُعدّ بعد)، والسفلى (ما عُدّ)، والخرزات الساقطة.
 * pos: عدد ما مرّ من الخرز (بما فيه الساقط الآن). falling: (رقم الوحدة، أعلاها الآن).
 */
fun DrawScope.drawStrand(g: StrandGeom, pos: Int, falling: List<Pair<Int, Float>>, pending: Float, drag: Float, bg: Color) {
    rope(g, g.art.style.thread)

    // السفلى
    val base = pos - falling.size
    var push = 0f
    falling.forEach { (i, top) -> push += (top + g.unitH(i) - g.botTop).coerceIn(0f, g.unitH(i)) }
    var y = g.botTop + push
    var i = base
    var guard = 0
    while (y < size.height && guard++ < 60) {
        unit(g, i, y); y += g.unitH(i); i--
    }
    // العليا
    var bottom = g.topBase - pending
    i = pos + 1
    guard = 0
    while (bottom > 0 && guard++ < 60) {
        val hh = g.unitH(i)
        unit(g, i, bottom - hh + if (i == pos + 1) drag else 0f)
        bottom -= hh; i++
    }
    // الساقطة
    falling.forEach { (k, top) -> unit(g, k, top) }

    // الخيط يُرى عبر الخرز الشفاف
    if (g.art.style.translucent) {
        drawLine(g.art.style.thread.copy(alpha = 0.55f), Offset(g.cx, 0f), Offset(g.cx, size.height), strokeWidth = 1.6.dp.toPx())
    }
    // تلاشٍ أعلى الشاشة وأسفلها
    val f = size.height * 0.12f
    drawRect(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)), startY = 0f, endY = f))
    drawRect(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg), startY = size.height - f, endY = size.height), topLeft = Offset(0f, size.height - f))
}

private class Drop(val index: Int, val y0: Float, val t: Animatable<Float, AnimationVector1D>)

/**
 * الخيط التفاعلي: اسحب الخرزة السفلى من المجموعة العليا فتتبع إصبعك ثم تسقط،
 * ويمسك الإصبع التي بعدها، فتتوالى مع التمرير. اللمسة بلا سحب تفتح شاشة الأشكال.
 */
@Composable
fun BeadStrand(
    art: BeadArt,
    count: Int,
    bg: Color,
    onDrop: () -> Unit,
    onLand: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var pos by remember { mutableIntStateOf(count) }
    val drops = remember { mutableStateListOf<Drop>() }
    var drag by remember { mutableFloatStateOf(0f) }
    val dropCb by rememberUpdatedState(onDrop)
    val landCb by rememberUpdatedState(onLand)
    val tapCb by rememberUpdatedState(onTap)

    LaunchedEffect(count) {
        if (count != pos) { drops.clear(); drag = 0f; pos = count }
    }

    BoxWithConstraints(modifier.clipToBounds().semantics { contentDescription = "الخرزات" }) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val beadW = min(w * 0.62f, with(density) { 118.dp.toPx() })
        val g = remember(art, w, h) {
            StrandGeom(art, w, h, beadW, topBase = h * 0.36f, botTop = h * 0.64f, px1 = with(density) { 0.5.dp.toPx() })
        }
        val releaseT = min(g.unitH(1) * 0.45f, with(density) { 52.dp.toPx() })
        val flingV = with(density) { 700.dp.toPx() }

        fun pending(): Float = drops.sumOf { d -> ((1f - easeOut(clamp01(d.t.value / 0.55f))) * g.unitH(d.index)).toDouble() }.toFloat()

        fun release() {
            val i = pos + 1
            val top = g.topBase - pending() - g.unitH(i) + drag
            val d = Drop(i, top, Animatable(0f))
            drops.add(d)
            drag = 0f
            pos = i
            dropCb()
            val dur = (sqrt(((g.botTop - top) / h).coerceAtLeast(0.01f)) * 520).toInt().coerceIn(140, 420)
            scope.launch {
                try {
                    d.t.animateTo(1f, tween(dur, easing = LinearEasing))
                    landCb()
                } finally {
                    drops.remove(d)
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(g) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val vt = VelocityTracker()
                        vt.addPosition(down.uptimeMillis, down.position)
                        var dragging = false
                        var lastY = down.position.y
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            vt.addPosition(ch.uptimeMillis, ch.position)
                            if (!ch.pressed) {
                                if (!dragging) tapCb()
                                else if (drag > 0f) {
                                    if (vt.calculateVelocity().y > flingV) release()
                                    else {
                                        val p0 = drag
                                        scope.launch { animate(p0, 0f, animationSpec = tween(180)) { v, _ -> drag = v } }
                                    }
                                }
                                break
                            }
                            if (!dragging && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                lastY = ch.position.y
                            }
                            if (dragging) {
                                ch.consume()
                                val dy = ch.position.y - lastY
                                lastY = ch.position.y
                                drag = (drag + dy).coerceAtLeast(0f)
                                var n = 0
                                while (drag >= releaseT && n < 3) {
                                    val extra = drag - releaseT
                                    drag = releaseT
                                    release()
                                    drag = extra.coerceAtMost(releaseT * 0.9f)
                                    n++
                                }
                            }
                        }
                    }
                }
        ) {
            val dest = g.botTop
            val falling = drops.map { d -> val t = d.t.value; d.index to d.y0 + (dest - d.y0) * t * t }
            drawStrand(g, pos, falling, pending(), drag, bg)
        }
    }
}

/** خيط ثابت للمعاينة في شاشة الأشكال. */
@Composable
fun StrandPreview(art: BeadArt, bg: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Canvas(modifier.clipToBounds()) {
        val beadW = min(size.width * 0.66f, 90.dp.toPx())
        val g = StrandGeom(art, size.width, size.height, beadW, size.height * 0.40f, size.height * 0.62f, with(density) { 0.5.dp.toPx() })
        drawStrand(g, 32, emptyList(), 0f, 0f, bg)
    }
}
