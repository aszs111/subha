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
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * يستمع للميكروفون ويقطّع الكلام إلى تكرارات، ثم يقارن كل تكرار ببصمة الذكر المسجّلة
 * بصوت المستخدم، فلا يُسقط خرزة إلا إذا طابقها. يعمل على الجهاز بلا إنترنت،
 * ولا يحفظ الصوت ولا يرسله.
 *
 * وضع التسجيل (enrollSink): يلتقط كل نطقٍ يليه سكوت ويسلّمه كما هو.
 */
class VoiceCounter(
    private val onUtterance: () -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onReject: () -> Unit = {},
) {
    private var job: Job? = null
    private val main = Handler(Looper.getMainLooper())

    /** بصمة الذكر الحالي، وبصمات بقية الأذكار للمفاضلة بينها. */
    @Volatile var model: VoiceModel? = null
    @Volatile var others: List<VoiceModel> = emptyList()
    /** حدّ القبول: نسبة البُعد إلى اختلاف تسجيلاتك نفسها (أصغر = أشدّ). */
    @Volatile var strict = 1.22f
    /** ٠..١: كلما زادت التقط صوتًا أخفض. */
    @Volatile var sensitivity = 0.75f
    /** إن وُجد: وضع التسجيل. */
    @Volatile var enrollSink: ((ShortArray) -> Unit)? = null

    val running get() = job?.isActive == true

    /** عدد التكرارات المطابقة في المقطع (٠ = مرفوض). */
    private fun decide(seg: ShortArray): Int {
        val m = model ?: return 0
        val durMs = seg.size / 16f
        if (durMs < m.durMs * 0.45f) return 0
        val reps = if (durMs >= m.durMs * 1.7f) min(4, (durMs / m.durMs).roundToInt()) else 1
        var ok = 0
        val part = seg.size / reps
        for (r in 0 until reps) {
            val piece = seg.copyOfRange(r * part, if (r == reps - 1) seg.size else (r + 1) * part)
            val f = Mfcc.features(piece, piece.size)
            if (f.size < 8) continue
            val s = m.score(f)
            if (s[0] > strict || s[1] > strict + 0.12f) continue
            // إن كان أقرب إلى ذكر آخر مسجّل فليس هذا
            val rival = others.any { o -> o.score(f)[0] < s[0] * 0.95f }
            if (!rival) ok++
        }
        return ok
    }

    private fun handle(seg: ShortArray) {
        val sink = enrollSink
        if (sink != null) { main.post { sink(seg) }; return }
        val n = decide(seg)
        if (n == 0) main.post { onReject() }
        else repeat(n) { main.post { onUtterance() } }
    }

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (running) return
        job = scope.launch(Dispatchers.IO) {
            val rate = 16000
            val frame = rate / 50 // ٢٠ مللي ثانية
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, frame * 50))
            } catch (e: Exception) { return@launch }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@launch }

            val buf = ShortArray(frame)
            val pre = ArrayDeque<ShortArray>()       // ٢٠٠ مللي ثانية قبل بدء الكلام
            val seg = ArrayList<ShortArray>()
            var lastVoiced = 0
            var floor = -1f
            var n = 0
            var speaking = false
            var above = 0
            var speechMs = 0f
            var quietMs = 0f
            var peak = 0f

            fun reset() { seg.clear(); speaking = false; speechMs = 0f; quietMs = 0f }

            fun finish() {
                val keep = min(seg.size, lastVoiced + 4)
                var total = 0
                for (k in 0 until keep) total += seg[k].size
                val out = ShortArray(total)
                var o = 0
                for (k in 0 until keep) { seg[k].copyInto(out, o); o += seg[k].size }
                reset()
                handle(out)
            }

            try {
                rec.startRecording()
                while (isActive) {
                    val r = rec.read(buf, 0, frame)
                    if (r <= 0) continue
                    val chunk = buf.copyOf(r)
                    var sum = 0.0
                    for (i in 0 until r) { val v = chunk[i].toDouble(); sum += v * v }
                    val rms = sqrt(sum / r).toFloat()
                    n++

                    if (floor < 0f) floor = rms
                    else if (rms < floor) floor = floor * 0.85f + rms * 0.15f
                    else if (!speaking) floor = min(floor * 1.004f + 0.2f, rms)

                    val factor = 3.6f - sensitivity * 2.4f            // ١٫٢ .. ٣٫٦ أضعاف الضجيج
                    val thrOn = max(floor * factor, 60f + (1f - sensitivity) * 260f)
                    if (n % 3 == 0) {
                        val lv = (rms / (thrOn * 3f)).coerceIn(0f, 1f)
                        main.post { onLevel(lv) }
                    }
                    if (n < 10) continue

                    if (!speaking) {
                        pre.addLast(chunk); while (pre.size > 10) pre.removeFirst()
                        if (rms > thrOn) above++ else above = 0
                        if (above >= 2) {
                            speaking = true; above = 0
                            speechMs = 40f; quietMs = 0f; peak = rms
                            seg.clear(); seg.addAll(pre); pre.clear()
                            lastVoiced = seg.size
                        }
                        continue
                    }

                    seg.add(chunk)
                    peak = max(rms, peak * 0.996f)
                    val quiet = rms < max(thrOn * 0.6f, peak * 0.2f)
                    if (quiet) quietMs += 20f
                    else { speechMs += 20f + quietMs; quietMs = 0f; lastVoiced = seg.size }

                    if (enrollSink != null) {
                        if (quietMs >= 450f) { if (speechMs >= 250f) finish() else reset() }
                    } else {
                        val exp = model?.durMs ?: 900f
                        when {
                            quietMs >= 100f && speechMs >= exp * 0.6f -> finish()
                            quietMs >= 1300f -> if (speechMs >= 200f) finish() else reset()
                            speechMs >= exp * 4.5f -> finish() // ترديد متصل طويل بلا انخفاض
                        }
                    }
                    if (seg.size > 600) reset() // أكثر من ١٢ ثانية
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
