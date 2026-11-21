package com.adel.subha.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.adel.subha.audio.VoiceCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
    val controllers = remember { mutableMapOf<Long, StrandController>() }

    // ───── العدّ بالصوت ─────
    var listening by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    val reject = remember { Animatable(0f) }
    var enrollFor by remember { mutableStateOf<Dhikr?>(null) }
    val voice = remember {
        VoiceCounter(
            onUtterance = { controllers[state.currentId]?.tap() },
            onLevel = { level = it },
            onReject = { scope.launch { reject.snapTo(1f); reject.animateTo(0f, tween(600)) } },
        )
    }
    LaunchedEffect(state.voiceSensitivity) { voice.sensitivity = state.voiceSensitivity }
    LaunchedEffect(state.matchStrict) { voice.strict = state.strictValue }
    val context = LocalContext.current

    fun startListening() {
        if (!state.voices.has(state.currentId)) { enrollFor = state.current; return }
        voice.start(scope); listening = true
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("قل الذكر، وتسقط خرزة مع كل مرة يطابق فيها بصمتك") }
    }
    // بصمة الذكر الحالي وبصمات غيره (للمفاضلة)
    LaunchedEffect(state.currentId, state.voiceRev) {
        val id = state.currentId
        val ids = state.adhkar.map { it.id }
        val (cur, others) = withContext(Dispatchers.IO) {
            state.voices.get(id) to ids.filter { it != id }.mapNotNull { state.voices.get(it) }
        }
        voice.model = cur
        voice.others = others
        if (listening && cur == null) {
            voice.stop(); listening = false
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("لم تُسجَّل بصمة هذا الذكر بعد", actionLabel = "سجّلها")
            if (r == SnackbarResult.ActionPerformed) enrollFor = state.current
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startListening()
        else scope.launch { snackbar.showSnackbar("العدّ بالصوت يحتاج إذن الميكروفون") }
    }
    fun toggleVoice() {
        if (listening) { voice.stop(); listening = false; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
        else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    enrollFor?.let { d ->
        EnrollDialog(state, d) { saved ->
            enrollFor = null
            if (saved && d.id == state.currentId) {
                scope.launch {
                    voice.model = withContext(Dispatchers.IO) { state.voices.get(d.id) }
                    voice.start(scope); listening = true
                }
            }
        }
    }
    // يتوقف الاستماع إذا خرجتَ من الشاشة أو من التطبيق
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) { voice.stop(); listening = false } }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); voice.stop() }
    }

    RoyalBackground(state.background) {
        AnimatedContent(
            targetState = state.currentId,
            transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(300)) },
            label = "dhikr",
        ) { id ->
            val d = state.adhkar.firstOrNull { it.id == id } ?: return@AnimatedContent
            val art = rememberBeadArt(beadStyle(d.bead))
            val controller = remember(id) { controllers.getOrPut(id) { StrandController() } }
            Box(Modifier.fillMaxSize()) {
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
                    controller = controller,
                    sway = state.sway,
                )
                DhikrPanel(state, d, art.style.accent, onAdhkar, Modifier.weight(1f).fillMaxHeight())
            }
            // زر النقر الكبير بلون الخرز في الزاوية اليسرى السفلى
            TapButton(
                art.style.button,
                onTap = { controller.tap() },
                modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(end = 26.dp, bottom = 62.dp),
            )
            // زر الميكروفون بجوار زر النقر
            MicButton(
                on = listening, level = level, reject = reject.value, color = art.style.button,
                onClick = { toggleVoice() },
                modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(end = 152.dp, bottom = 90.dp),
            )
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
        // شريط الصوت الشفاف أسفل الشاشة
        Row(
            Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.60f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 6.dp, end = 14.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = Cream.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
            Slider(
                value = state.volume,
                onValueChange = { state.changeVolume(it) },
                onValueChangeFinished = { if (state.sound) fb.clack() },
                modifier = Modifier.weight(1f).height(36.dp).semantics { contentDescription = "مستوى الصوت" },
                colors = SliderDefaults.colors(
                    thumbColor = Cream.copy(alpha = 0.75f),
                    activeTrackColor = Cream.copy(alpha = 0.32f),
                    inactiveTrackColor = Cream.copy(alpha = 0.10f),
                ),
            )
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Cream.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
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
            d.text.length <= 16 -> 42.sp
            d.text.length <= 30 -> 35.sp
            d.text.length <= 70 -> 28.sp
            else -> 23.sp
        }
        Column(
            Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onAdhkar),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(d.text, color = Cream, fontSize = fs, lineHeight = fs * 1.5f, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text("الأذكار", color = Cream.copy(alpha = 0.45f), style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Cream.copy(alpha = 0.45f), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            d.count.ar(),
            color = if (done) accent else Cream,
            fontSize = if (d.count < 1000) 112.sp else 82.sp,
            lineHeight = 116.sp,
            modifier = Modifier.scale(pop.value),
        )
        Text(
            if (d.target > 0) "من ${d.target.ar()}" else "بلا حد",
            color = Cream.copy(alpha = 0.5f), style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp,
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
            style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(176.dp)) // مكان زر النقر وشريط الصوت
    }
}

@Composable
private fun Diamonds(filled: Int, color: Color) {
    Canvas(Modifier.size(width = 66.dp, height = 14.dp)) {
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

@Composable
private fun TapButton(color: Color, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val tapCb by rememberUpdatedState(onTap)
    Box(
        modifier
            .size(112.dp)
            .scale(press.value)
            .shadow(14.dp, CircleShape, ambientColor = color, spotColor = color)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    0f to lerp(color, Color.White, 0.45f),
                    0.45f to color,
                    1f to lerp(color, Color.Black, 0.55f),
                    center = Offset(110f, 90f), radius = 380f,
                )
            )
            .border(1.5.dp, lerp(color, Color.White, 0.5f).copy(alpha = 0.6f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scope.launch { press.animateTo(0.9f, tween(70)) }
                        tryAwaitRelease()
                        scope.launch { press.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                    },
                    onTap = { tapCb() },
                )
            }
            .semantics { contentDescription = "تسبيحة" },
    ) {
        // لمعة
        Canvas(Modifier.fillMaxSize()) {
            drawOval(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width * 0.34f, size.height * 0.26f), radius = size.width * 0.28f),
                topLeft = Offset(size.width * 0.12f, size.height * 0.10f), size = Size(size.width * 0.44f, size.height * 0.30f),
            )
        }
    }
}

@Composable
private fun MicButton(on: Boolean, level: Float, reject: Float, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (on) {
            // حلقة تنبض مع مستوى الصوت، وتومض رمادية إذا سُمع كلام لا يطابق الذكر
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color.copy(alpha = 0.25f + 0.5f * level), radius = size.minDimension / 2 * (0.78f + 0.22f * level))
                if (reject > 0f) drawCircle(Color(0xFF9E9E9E).copy(alpha = 0.8f * reject), radius = size.minDimension / 2, style = Stroke(3.dp.toPx()))
            }
        }
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(if (on) color else Color.White.copy(alpha = 0.07f))
                .border(1.dp, color.copy(alpha = if (on) 0.9f else 0.5f), CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = if (on) "إيقاف العدّ بالصوت" else "العدّ بالصوت" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (on) Icons.Default.Mic else Icons.Default.MicNone, contentDescription = null,
                tint = if (on) lerp(color, Color.Black, 0.65f) else Cream.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
