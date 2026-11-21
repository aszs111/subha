package com.adel.subha.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.adel.subha.audio.Mfcc
import com.adel.subha.audio.VoiceCounter
import com.adel.subha.data.AppState
import com.adel.subha.data.Dhikr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * تسجيل بصمة الذكر: يقول المستخدم الذكر ثلاث مرات منفصلة، فتُحفظ بصمته
 * ليستجيب التطبيق بعدها لهذا اللفظ بصوته دون غيره.
 */
@Composable
fun EnrollDialog(state: AppState, d: Dhikr, onDone: (saved: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val takes = remember { mutableStateListOf<ShortArray>() }
    var level by remember { mutableFloatStateOf(0f) }
    var msg by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val vc = remember {
        VoiceCounter(onUtterance = {}, onLevel = { level = it }).apply {
            sensitivity = state.voiceSensitivity
            enrollSink = { seg -> if (takes.size < 3) takes.add(seg) }
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) msg = "يلزم إذن الميكروفون لتسجيل البصمة"
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(granted) {
        if (granted) vc.start(scope)
        onDispose { vc.stop() }
    }

    LaunchedEffect(takes.size) {
        if (takes.size == 3 && !saved) {
            vc.stop()
            val durs = takes.map { it.size / 16f }
            val feats = withContext(Dispatchers.Default) { takes.map { Mfcc.features(it, it.size) } }
            val consistent = durs.max() / durs.min() < 1.9f && feats.all { it.size >= 8 }
            if (consistent) {
                state.saveVoice(d.id, feats, durs.average().toFloat())
                saved = true
                msg = "تمّ حفظ بصمة صوتك لهذا الذكر"
            } else {
                msg = "اختلفت المرات كثيرًا، فأعد التسجيل بنطق متقارب"
                takes.clear()
                vc.start(scope)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { onDone(saved) },
        title = { Text("بصمة صوتك") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (saved) "صار التطبيق يميّز نطقك لـ" else "قل الذكر بصوتك المعتاد، ثم اسكت لحظة، ثلاث مرات:",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(d.text, fontSize = 26.sp, lineHeight = 38.sp, color = Gold, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(16.dp))
                if (!saved) {
                    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(84.dp)) {
                            drawCircle(Gold.copy(alpha = 0.18f + 0.6f * level), radius = size.minDimension / 2 * (0.6f + 0.4f * level))
                            drawCircle(Gold.copy(alpha = 0.6f), radius = size.minDimension / 2 * 0.58f, style = Stroke(1.5.dp.toPx()))
                        }
                        Icon(Icons.Default.Mic, null, tint = Gold, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (k in 0 until 3) {
                        val done = k < takes.size || saved
                        Canvas(Modifier.size(18.dp)) {
                            if (done) drawCircle(Gold) else drawCircle(Color.White.copy(alpha = 0.4f), style = Stroke(1.5.dp.toPx()))
                        }
                    }
                }
                msg?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (saved) Gold else MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(saved) }) { Text(if (saved) "تمّ" else "إلغاء") }
        },
        dismissButton = {
            if (!saved && takes.isNotEmpty()) TextButton(onClick = { takes.clear(); msg = null }) { Text("إعادة") }
            else if (saved) TextButton(onClick = {
                saved = false; takes.clear(); msg = null; vc.start(scope)
            }) { Text("تسجيل من جديد") }
        },
    )
}
