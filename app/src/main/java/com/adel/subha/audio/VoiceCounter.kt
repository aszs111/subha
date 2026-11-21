package com.adel.subha.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * يعدّ تكرارات الذكر المنطوقة: كل تكرار = خرزة.
 *
 * يقطّع الكلام عند «الانخفاضات» في شدة الصوت بين التكرارات (ولو كانت قصيرة كما في
 * الترديد المتصل)، ولا يقبل الحدّ إلا إذا بلغ الكلام قبله قرابة طول الذكر المتوقع،
 * فلا تُحسب الوقفات داخل الذكر الطويل. يتعلّم سرعة نطقك من التكرارات السابقة.
 * يعمل على الجهاز بلا إنترنت، ولا يحفظ الصوت ولا يرسله.
 */
class VoiceCounter(
    private val onUtterance: () -> Unit,
    private val onLevel: (Float) -> Unit,
) {
    private var job: Job? = null
    private val main = Handler(Looper.getMainLooper())

    /** الطول المتوقع المبدئي للذكر (من عدد حروفه). */
    @Volatile private var baseMs = 900f
    @Volatile private var expectedMs = 900f
    private val recent = ArrayDeque<Float>()

    /** ٠..١: كلما زادت التقط صوتًا أخفض. */
    @Volatile var sensitivity = 0.5f

    val running get() = job?.isActive == true

    /** نحو ٧٠ مللي ثانية للحرف في الترديد المعتاد. */
    fun expectFor(text: String) {
        val letters = text.count { it.isLetter() && it !in 'ً'..'ْ' && it != 'ٰ' }
        baseMs = (letters * 70f).coerceIn(380f, 6000f)
        expectedMs = baseMs
        synchronized(recent) { recent.clear() }
    }

    private fun learn(ms: Float) {
        synchronized(recent) {
            recent.addLast(ms)
            while (recent.size > 6) recent.removeFirst()
            val sorted = recent.sorted()
            val median = sorted[sorted.size / 2]
            expectedMs = median.coerceIn(baseMs * 0.45f, baseMs * 2f)
        }
    }

    private fun emit() { main.post { onUtterance() } }

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (running) return
        job = scope.launch(Dispatchers.IO) {
            val rate = 16000
            val frame = rate / 50 // ٢٠ مللي ثانية
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, frame * 10))
            } catch (e: Exception) { return@launch }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@launch }

            val buf = ShortArray(frame)
            var floor = -1f
            var n = 0
            var speaking = false
            var above = 0
            var speechMs = 0f      // زمن الكلام في التكرار الحالي (بما فيه الوقفات القصيرة داخله)
            var quietMs = 0f       // زمن الانخفاض الحالي
            var peak = 0f
            try {
                rec.startRecording()
                while (isActive) {
                    val r = rec.read(buf, 0, frame)
                    if (r <= 0) continue
                    var sum = 0.0
                    for (i in 0 until r) { val v = buf[i].toDouble(); sum += v * v }
                    val rms = sqrt(sum / r).toFloat()
                    n++

                    // ضجيج المكان: ينزل سريعًا ويصعد ببطء شديد، فلا يرتفع الحدّ مع الكلام
                    if (floor < 0f) floor = rms
                    else if (rms < floor) floor = floor * 0.85f + rms * 0.15f
                    else if (!speaking) floor = min(floor * 1.004f + 0.2f, rms)
                    if (n < 10) continue

                    val factor = 4.2f - sensitivity * 2.6f           // ١٫٦ .. ٤٫٢ أضعاف الضجيج
                    val thrOn = max(floor * factor, 120f + (1f - sensitivity) * 380f)
                    if (n % 3 == 0) {
                        val lv = (rms / (thrOn * 3f)).coerceIn(0f, 1f)
                        main.post { onLevel(lv) }
                    }

                    if (!speaking) {
                        if (rms > thrOn) above++ else above = 0
                        if (above >= 2) {
                            speaking = true; above = 0
                            speechMs = 40f; quietMs = 0f; peak = rms
                        }
                        continue
                    }

                    peak = max(rms, peak * 0.996f)
                    val quiet = rms < max(thrOn * 0.6f, peak * 0.2f)
                    if (quiet) {
                        quietMs += 20f
                    } else {
                        speechMs += 20f + quietMs   // وقفة قصيرة داخل الذكر تُضمّ إليه
                        quietMs = 0f
                    }

                    val exp = expectedMs
                    if (quietMs >= 100f && speechMs >= exp * 0.6f) {
                        // انخفاض قصير بعد كلام بطول الذكر تقريبًا = نهاية تكرار
                        learn(speechMs)
                        emit()
                        speaking = false; speechMs = 0f; quietMs = 0f
                    } else if (quietMs >= 1300f) {
                        // سكوت طويل: احسب ما سبقه إن كان كلامًا لا نقرة عابرة
                        if (speechMs >= 200f) { learn(speechMs); emit() }
                        speaking = false; speechMs = 0f; quietMs = 0f
                    }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                main.post { onLevel(0f) }
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
