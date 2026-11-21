package com.adel.subha.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.subha.audio.Feedback
import com.adel.subha.data.AppState
import com.adel.subha.data.Tick
import com.adel.subha.data.ar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(state: AppState, fb: Feedback, onAdhkar: () -> Unit, onBeads: () -> Unit, onSettings: () -> Unit) {
    val d = state.current
    val style = beadStyle(state.beadStyle)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    RoyalBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("سُبحة", color = Gold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "الإعدادات", tint = GoldPale) }
            }
            // في الاتجاه من اليمين لليسار: العنصر الأول (الخرز) على اليمين، والذكر على اليسار
            Row(Modifier.weight(1f).fillMaxWidth()) {
                BeadStrand(
                    style = style,
                    onDrop = {
                        val t = state.tick()
                        if (state.vibrate) fb.tick()
                        if (t == Tick.REACHED) {
                            if (state.vibrate) fb.reached()
                            scope.launch {
                                delay(330)
                                if (state.sound) fb.done()
                                if (state.autoNext && state.adhkar.size > 1) {
                                    delay(500)
                                    state.goNext()
                                    snackbar.showSnackbar("أتممتَ الذكر، وهذا التالي: ${state.current.text}")
                                }
                            }
                        }
                    },
                    onLand = { if (state.sound) fb.clack() },
                    onTap = onBeads,
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.36f),
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp, end = 18.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        onClick = onAdhkar,
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val fs = when {
                                d.text.length <= 12 -> 36.sp
                                d.text.length <= 24 -> 30.sp
                                d.text.length <= 60 -> 25.sp
                                else -> 21.sp
                            }
                            Text(d.text, color = Cream, fontSize = fs, lineHeight = fs * 1.35f, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text("الأذكار ‹", color = Gold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                    CounterRing(d.count, d.target)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (d.target > 0) "من ${d.target.ar()}" else "بلا حد",
                        color = GoldPale.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium,
                    )
                    if (d.target > 0 && d.count >= d.target) {
                        Text("✓ اكتمل الورد", color = Gold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(22.dp))
                    OutlinedButton(
                        onClick = {
                            val old = state.reset()
                            if (old > 0) scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val r = snackbar.showSnackbar("أُعيد العدّ إلى الصفر", actionLabel = "تراجع")
                                if (r == SnackbarResult.ActionPerformed) state.restoreCount(old)
                            }
                        },
                        border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPale),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إعادة", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("المجموع الكلي: ${d.total.ar()}", color = Cream.copy(alpha = 0.55f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

@Composable
private fun CounterRing(count: Int, target: Int) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count > 0) {
            pop.snapTo(1.07f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val prog = remember { Animatable(0f) }
    val goal = if (target > 0) (count.toFloat() / target).coerceAtMost(1f) else 0f
    LaunchedEffect(goal) { prog.animateTo(goal, tween(220)) }

    Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 7.dp.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(Color.White.copy(alpha = 0.10f), 0f, 360f, false, topLeft = tl, size = sz, style = Stroke(sw))
            if (target > 0) drawArc(Gold, -90f, -360f * prog.value, false, topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(
            count.ar(),
            color = Gold,
            fontSize = if (count < 1000) 66.sp else 50.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.scale(pop.value),
        )
    }
}
