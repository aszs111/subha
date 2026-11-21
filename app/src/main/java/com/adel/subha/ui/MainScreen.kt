package com.adel.subha.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.subha.audio.Feedback
import com.adel.subha.data.AppState
import com.adel.subha.data.Dhikr
import com.adel.subha.data.Tick
import com.adel.subha.data.ar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MainScreen(state: AppState, fb: Feedback, onAdhkar: () -> Unit, onBeads: () -> Unit, onSettings: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val edge = edgeColor(state.background)

    RoyalBackground(state.background) {
        AnimatedContent(
            targetState = state.currentId,
            transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(300)) },
            label = "dhikr",
        ) { id ->
            val d = state.adhkar.firstOrNull { it.id == id } ?: return@AnimatedContent
            val art = rememberBeadArt(beadStyle(d.bead))
            Row(Modifier.fillMaxSize()) {
                // الخرز على اليمين (أول عنصر في الاتجاه من اليمين لليسار)
                BeadStrand(
                    art = art,
                    count = d.count,
                    bg = edge,
                    onDrop = {
                        val cur = state.current
                        if (state.autoNext && state.adhkar.size > 1 && cur.target > 0 && cur.count >= cur.target) {
                            // اكتمل الذكر واستمر المسبِّح: انتقل فورًا واحسبها للذكر التالي
                            state.goNext()
                            state.tick()
                        } else if (state.tick() == Tick.REACHED) {
                            val reachedId = state.currentId
                            scope.launch {
                                delay(380)
                                if (state.vibrate) fb.reached()
                                if (state.sound) fb.done()
                                if (state.autoNext && state.adhkar.size > 1) {
                                    delay(700)
                                    if (state.currentId == reachedId) state.goNext()
                                }
                            }
                        }
                    },
                    onLand = {
                        if (state.sound) fb.clack()
                        if (state.vibrate) fb.tick()
                    },
                    onTap = onBeads,
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.40f),
                )
                DhikrPanel(state, d, art.style.accent, onAdhkar, Modifier.weight(1f).fillMaxHeight())
            }
        }

        // أزرار صغيرة في الزاوية اليسرى العليا
        Row(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(4.dp)) {
            IconButton(onClick = {
                val old = state.reset()
                if (old > 0) scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val r = snackbar.showSnackbar("أُعيد العدّ إلى الصفر", actionLabel = "تراجع")
                    if (r == SnackbarResult.ActionPerformed) state.restoreCount(old)
                }
            }) { Icon(Icons.Default.Refresh, contentDescription = "إعادة", tint = Cream.copy(alpha = 0.5f)) }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "الإعدادات", tint = Cream.copy(alpha = 0.5f)) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

@Composable
private fun DhikrPanel(state: AppState, d: Dhikr, accent: Color, onAdhkar: () -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val done = d.target > 0 && d.count >= d.target
    val pop = remember { Animatable(1f) }
    LaunchedEffect(d.count) {
        if (d.count > 0) {
            pop.snapTo(1.06f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 4.dp, end = 20.dp)
            .pointerInput(d.id) {
                // سحب أفقي للتنقل بين الأذكار
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = {
                        val lim = with(density) { 70.dp.toPx() }
                        if (abs(acc) > lim) state.browse(if (acc < 0) 1 else -1)
                    },
                ) { ch, dx -> ch.consume(); acc += dx }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.7f))
        val fs = when {
            d.text.length <= 16 -> 36.sp
            d.text.length <= 30 -> 30.sp
            d.text.length <= 70 -> 24.sp
            else -> 20.sp
        }
        Column(
            Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onAdhkar),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(d.text, color = Cream, fontSize = fs, lineHeight = fs * 1.5f, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text("الأذكار", color = Cream.copy(alpha = 0.45f), style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Cream.copy(alpha = 0.45f), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            d.count.ar(),
            color = if (done) accent else Cream,
            fontSize = if (d.count < 1000) 96.sp else 70.sp,
            lineHeight = 100.sp,
            modifier = Modifier.scale(pop.value),
        )
        Text(
            if (d.target > 0) "من ${d.target.ar()}" else "بلا حد",
            color = Cream.copy(alpha = 0.5f), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(12.dp))
        Diamonds(
            filled = if (d.target > 0) (d.count * 3 / d.target).coerceIn(0, 3) else (d.count / 33) % 4,
            color = accent,
        )
        Spacer(Modifier.height(12.dp))
        val today = state.todayOf(d)
        Text(
            when {
                done && d.target == 100 -> "مئةٌ اليوم"
                done -> "اكتمل الورد"
                else -> "اليوم: ${today.ar()}"
            },
            color = if (done) accent else Cream.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Diamonds(filled: Int, color: Color) {
    Canvas(Modifier.size(width = 56.dp, height = 12.dp)) {
        val r = size.height / 2.2f
        for (k in 0 until 3) {
            // من اليمين إلى اليسار
            val cx = size.width - (size.width / 6f) * (2 * k + 1)
            val c = Offset(cx, size.height / 2)
            val p = Path().apply {
                moveTo(c.x, c.y - r); lineTo(c.x + r * 0.8f, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.8f, c.y); close()
            }
            if (k < filled) drawPath(p, color) else drawPath(p, color.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
        }
    }
}
