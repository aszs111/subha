package com.adel.subha.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.min

private class Drop(val y0: Float, val t: Animatable<Float, AnimationVector1D>)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)

/**
 * خيط عمودي عليه ثلاث خرزات في الأعلى وثلاث في الأسفل وبينهما فراغ.
 * السحب إلى الأسفل يُسقط الخرزات واحدة تلو الأخرى، والضغط يفتح شاشة شكل الخرز.
 */
@Composable
fun BeadStrand(
    style: BeadStyle,
    onDrop: () -> Unit,
    onLand: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val drops = remember { mutableStateListOf<Drop>() }   // الخرزات التي في الهواء الآن
    var pull by remember { mutableFloatStateOf(0f) }       // سحب الخرزة السفلى من المجموعة العليا بالإصبع
    val dropCb by rememberUpdatedState(onDrop)
    val landCb by rememberUpdatedState(onLand)
    val tapCb by rememberUpdatedState(onTap)

    BoxWithConstraints(modifier.clipToBounds().semantics { contentDescription = "الخرزات" }) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val margin = with(density) { 22.dp.toPx() }
        val gapPx = with(density) { 2.dp.toPx() }
        // حجم الخرزة: يتسع للعرض ويترك فراغًا يعادل ثلاث خرزات تقريبًا
        val r = min(min(w * 0.30f / style.shape.w, with(density) { 40.dp.toPx() }), (h - 2 * margin) / (9.5f * 2 * style.shape.h))
        val ry = style.ry(r)
        val pitch = 2 * ry + gapPx
        fun topY(i: Float) = margin + ry + i * pitch
        fun botY(j: Float) = h - margin - ry - (2f - j) * pitch
        val fall = botY(0f) - topY(2f)
        val threshold = min(with(density) { 26.dp.toPx() }, fall * 0.3f)
        val cx = w / 2

        // كم خانة لم تنزلها المجموعة العليا بعد
        fun pending(): Float = drops.sumOf { (1f - easeOut(clamp01(it.t.value / 0.7f))).toDouble() }.toFloat()

        fun startDrop() {
            dropCb()
            val d = Drop(topY(2f - pending()) + pull, Animatable(0f))
            drops.add(d)
            scope.launch {
                try {
                    d.t.animateTo(1f, tween(300, easing = LinearEasing))
                    landCb()
                } finally {
                    drops.remove(d)
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(style, fall, threshold) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val p0 = pull
                            scope.launch { animate(p0, 0f, animationSpec = tween(160)) { v, _ -> pull = v } }
                        },
                        onDragCancel = { pull = 0f },
                    ) { change, dy ->
                        change.consume()
                        var acc = (pull + dy).coerceAtLeast(0f)
                        var n = 0
                        while (acc >= threshold && n < 3) {
                            pull = 0f
                            startDrop()
                            acc -= threshold
                            n++
                        }
                        pull = acc.coerceAtMost(threshold * 0.95f)
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { tapCb() }) }
        ) {
            // الخيط
            drawLine(
                Brush.verticalGradient(listOf(Color.Transparent, style.thread, style.thread, Color.Transparent)),
                Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.6.dp.toPx(),
            )
            drawLine(Color.White.copy(alpha = 0.18f), Offset(cx - 0.5.dp.toPx(), 0f), Offset(cx - 0.5.dp.toPx(), size.height), strokeWidth = 0.6.dp.toPx())

            fun alphaFor(y: Float) = when {
                y < topY(0f) -> clamp01(1f - (topY(0f) - y) / pitch)
                y > botY(2f) -> clamp01(1f - (y - botY(2f)) / pitch)
                else -> 1f
            }
            val dest = botY(0f)
            val falling = drops.map { d -> val t = d.t.value; d.y0 + (dest - d.y0) * t * t }
            val push = falling.sumOf { y -> (y + pitch - dest).coerceIn(0f, pitch).toDouble() }.toFloat()
            val pend = pending()

            // المجموعة السفلى (تُدفع بالخرزة الساقطة)
            for (j in 3 downTo 0) {
                val y = botY(j.toFloat()) + push
                drawBead(style, Offset(cx, y), r, alphaFor(y))
            }
            // المجموعة العليا (تنزل خانة، وتدخل خرزة جديدة من الأعلى)
            var m = 0
            while (m < 12) {
                val y = topY(2f - m - pend) + if (m == 0) pull else 0f
                if (y < topY(-1.05f)) break
                drawBead(style, Offset(cx, y), r, alphaFor(y))
                m++
            }
            // الخرزات الساقطة
            falling.forEach { y -> drawBead(style, Offset(cx, y), r, 1f) }
        }
    }
}
