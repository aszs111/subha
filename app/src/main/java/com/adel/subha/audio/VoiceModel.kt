package com.adel.subha.audio

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** بصمة صوتية لذكر واحد: ثلاثة تسجيلات بصوت المستخدم. */
class VoiceModel(val templates: List<Array<FloatArray>>, val durMs: Float) {
    /** متوسط المسافة بين التسجيلات نفسها (الكلي، وأسوأ ربع): مرجع للحكم. */
    val mu: Float
    val muQ: Float

    init {
        var a = 0f; var b = 0f; var n = 0
        for (i in templates.indices) for (j in i + 1 until templates.size) {
            val d = Mfcc.dtw(templates[i], templates[j]); a += d[0]; b += d[1]; n++
        }
        mu = if (n > 0) a / n else 1f
        muQ = if (n > 0) b / n else 1f
    }

    /** نسبة البُعد (الكلي، أسوأ ربع) لأقرب تسجيل؛ ١ يعني بقدر اختلاف تسجيلاتك نفسها. */
    fun score(f: Array<FloatArray>): FloatArray {
        var best = Float.MAX_VALUE; var bestQ = Float.MAX_VALUE
        for (t in templates) {
            val d = Mfcc.dtw(f, t)
            if (d[0] < best) best = d[0]
            if (d[1] < bestQ) bestQ = d[1]
        }
        return floatArrayOf(best / mu, bestQ / muQ)
    }
}

/** حفظ البصمات في ملفات داخل التطبيق (لا تغادر الجهاز). */
class VoiceStore(context: Context) {
    private val dir = File(context.filesDir, "voice").apply { mkdirs() }
    private val cache = HashMap<Long, VoiceModel?>()

    private fun file(id: Long) = File(dir, "dhikr_$id.bin")

    fun has(id: Long) = file(id).exists()

    fun get(id: Long): VoiceModel? = synchronized(cache) {
        cache.getOrPut(id) { load(id) }
    }

    fun save(id: Long, templates: List<Array<FloatArray>>, durMs: Float) {
        DataOutputStream(file(id).outputStream().buffered()).use { o ->
            o.writeInt(1); o.writeFloat(durMs); o.writeInt(templates.size)
            for (t in templates) {
                o.writeInt(t.size); o.writeInt(Mfcc.NCEP)
                for (fr in t) for (v in fr) o.writeFloat(v)
            }
        }
        synchronized(cache) { cache[id] = VoiceModel(templates, durMs) }
    }

    fun delete(id: Long) { file(id).delete(); synchronized(cache) { cache.remove(id) } }

    private fun load(id: Long): VoiceModel? = runCatching {
        DataInputStream(file(id).inputStream().buffered()).use { i ->
            i.readInt()
            val dur = i.readFloat()
            val k = i.readInt()
            val ts = List(k) {
                val n = i.readInt(); val d = i.readInt()
                Array(n) { FloatArray(d) { i.readFloat() } }
            }
            VoiceModel(ts, dur)
        }
    }.getOrNull()
}
