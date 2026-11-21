package com.adel.subha.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt

/**
 * يعدّ التكرارات المنطوقة: كل نطقٍ للذكر يليه سكوت قصير = خرزة واحدة.
 * يعمل على الجهاز بلا إنترنت، ولا يحفظ الصوت ولا يرسله. لا يتعرّف على الكلمات،
 * بل على «دفعات الكلام» وطولها المتوقع.
 */
class VoiceCounter(
    private val onUtterance: () -> Unit,
    private val onLevel: (Float) -> Unit,
) {
    private var job: Job? = null

    /** الطول المتوقع للذكر الواحد (مللي ثانية)؛ يتعلّم من نطقك. */
    @Volatile var expectedMs = 900f
    /** ٠..١: كلما زادت التقط صوتًا أخفض. */
    @Volatile var sensitivity = 0.5f

    val running get() = job?.isActive == true

    /** تقدير مبدئي من طول النص: نحو ٦٥ مللي ثانية للحرف. */
    fun expectFor(text: String) {
        val letters = text.count { it.isLetter() && it !in 'ً'..'ْ' && it != 'ٰ' }
        expectedMs = (letters * 65f).coerceIn(350f, 6000f)
    }

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (running) return
        job = scope.launch(Dispatchers.IO) {
            val rate = 16000
            val frame = rate / 50 // ٢٠ مللي ثانية
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, frame * 8))
            } catch (e: Exception) { return@launch }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@launch }
            val buf = ShortArray(frame)
            var floor = 0f
            var frames = 0
            var speaking = false
            var above = 0
            var silentMs = 0
            var speechMs = 0
            var acc = 0f          // كلام متراكم لم يبلغ طول ذكر كامل بعد (ذكر طويل فيه وقفات)
            var sinceSpeech = 0
            try {
                rec.startRecording()
                while (isActive) {
                    val n = rec.read(buf, 0, frame)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) { val v = buf[i].toDouble(); sum += v * v }
                    val rms = sqrt(sum / n).toFloat()
                    frames++
                    if (frames <= 15) { floor = if (frames == 1) rms else floor * 0.8f + rms * 0.2f; continue }

                    val factor = 4.6f - sensitivity * 2.8f          // ١٫٨ .. ٤٫٦ أضعاف الضجيج
                    val thr = max(floor * factor, 180f + (1f - sensitivity) * 420f)
                    val level = (rms / (thr * 2.5f)).coerceIn(0f, 1f)
                    withContext(Dispatchers.Main) { onLevel(level) }

                    if (!speaking) {
                        floor = floor * 0.97f + rms.coerceAtMost(floor * 3f + 1f) * 0.03f
                        if (rms > thr) above++ else above = 0
                        if (above >= 3) { speaking = true; speechMs = 60; silentMs = 0 }
                        sinceSpeech += 20
                        if (sinceSpeech > 2500) acc = 0f
                    } else {
                        if (rms > thr * 0.6f) { speechMs += 20 + silentMs; silentMs = 0 } else silentMs += 20
                        if (silentMs >= 420) {
                            speaking = false; above = 0; sinceSpeech = 0
                            acc += speechMs
                            if (acc >= expectedMs * 0.55f) {
                                // تعلّم الطول الحقيقي تدريجيًا
                                expectedMs = (expectedMs * 0.8f + acc * 0.2f).coerceIn(300f, 8000f)
                                acc = 0f
                                withContext(Dispatchers.Main) { onUtterance() }
                            }
                        }
                        if (speechMs > 12000) { speaking = false; acc = 0f } // كلام طويل متصل: ليس ذكرًا
                    }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) { onLevel(0f) }
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
