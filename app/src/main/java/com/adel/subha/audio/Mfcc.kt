package com.adel.subha.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * بصمة الصوت: معاملات MFCC لكل ١٠ مللي ثانية، ومقارنة بصمتين بالمواءمة الزمنية (DTW)
 * حتى لا يضرّ اختلاف سرعة النطق.
 */
object Mfcc {
    const val SR = 16000
    private const val WIN = 400       // ٢٥ مللي ثانية
    private const val HOP = 160       // ١٠ مللي ثانية
    private const val NFFT = 512
    private const val NMEL = 26
    const val NCEP = 13

    private val window = FloatArray(WIN) { (0.54 - 0.46 * cos(2 * PI * it / (WIN - 1))).toFloat() }
    private val melFb: Array<FloatArray>
    private val dct: Array<FloatArray>

    init {
        fun hz2mel(h: Double) = 2595.0 * kotlin.math.log10(1 + h / 700.0)
        fun mel2hz(m: Double) = 700.0 * (Math.pow(10.0, m / 2595.0) - 1)
        val lo = hz2mel(80.0)
        val hi = hz2mel(7600.0)
        val pts = DoubleArray(NMEL + 2) { mel2hz(lo + (hi - lo) * it / (NMEL + 1)) }
        val bins = IntArray(NMEL + 2) { ((NFFT + 1) * pts[it] / SR).toInt() }
        melFb = Array(NMEL) { m ->
            FloatArray(NFFT / 2 + 1) { k ->
                val a = bins[m]; val b = bins[m + 1]; val c = bins[m + 2]
                when {
                    k in a until b && b > a -> (k - a).toFloat() / (b - a)
                    k in b..c && c > b -> (c - k).toFloat() / (c - b)
                    else -> 0f
                }
            }
        }
        dct = Array(NCEP) { i -> FloatArray(NMEL) { j -> cos(PI * i * (j + 0.5) / NMEL).toFloat() } }
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = kotlin.math.sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ar = re[i + k]; val ai = im[i + k]
                    val br = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val bi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ar + br; im[i + k] = ai + bi
                    re[i + k + len / 2] = ar - br; im[i + k + len / 2] = ai - bi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** بصمة مقطع صوتي: مصفوفة [إطار][١٣] بعد طرح المتوسط (لتجاهل اختلاف الميكروفون والمسافة). */
    fun features(x: ShortArray, n: Int): Array<FloatArray> {
        if (n < WIN) return emptyArray()
        val frames = 1 + (n - WIN) / HOP
        val out = Array(frames) { FloatArray(NCEP) }
        val re = FloatArray(NFFT)
        val im = FloatArray(NFFT)
        val mel = FloatArray(NMEL)
        for (f in 0 until frames) {
            val off = f * HOP
            re.fill(0f); im.fill(0f)
            for (i in 0 until WIN) {
                val cur = x[off + i].toFloat()
                val prev = if (off + i > 0) x[off + i - 1].toFloat() else 0f
                re[i] = (cur - 0.97f * prev) * window[i]
            }
            fft(re, im)
            for (m in 0 until NMEL) {
                var s = 0f
                val fb = melFb[m]
                for (k in 0..NFFT / 2) if (fb[k] != 0f) s += fb[k] * (re[k] * re[k] + im[k] * im[k])
                mel[m] = ln(max(s, 1e-3f))
            }
            for (c in 0 until NCEP) {
                var s = 0f
                for (m in 0 until NMEL) s += dct[c][m] * mel[m]
                out[f][c] = s
            }
        }
        // طرح المتوسط لكل معامل
        for (c in 0 until NCEP) {
            var mean = 0f
            for (f in 0 until frames) mean += out[f][c]
            mean /= frames
            for (f in 0 until frames) out[f][c] -= mean
        }
        // توحيد التباين لكل معامل (يقلّل أثر اختلاف الميكروفون وشدة الصوت)
        for (c in 0 until NCEP) {
            var v = 0f
            for (f in 0 until frames) v += out[f][c] * out[f][c]
            val sd = sqrt(v / frames) + 1e-3f
            for (f in 0 until frames) out[f][c] /= sd
        }
        return out
    }

    private fun dist(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) { val d = a[i] - b[i]; s += d * d }
        return sqrt(s)
    }

    /**
     * مسافة المواءمة الزمنية بين بصمتين: [المتوسط الكلي، أسوأ ربع]. أسوأ الربع يكشف
     * اللفظ الذي يشارك الذكرَ في نصفه فقط (مثل «الله» في «سبحان الله» و«الحمد لله»).
     */
    fun dtw(a: Array<FloatArray>, b: Array<FloatArray>): FloatArray {
        val n = a.size; val m = b.size
        val bad = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE)
        if (n == 0 || m == 0) return bad
        val band = max(abs(n - m), (max(n, m) * 0.3f).toInt()) + 2
        val inf = Float.MAX_VALUE / 4
        val D = Array(n + 1) { FloatArray(m + 1) { inf } }
        val P = Array(n + 1) { ByteArray(m + 1) }
        val C = Array(n + 1) { FloatArray(m + 1) }
        D[0][0] = 0f
        for (i in 1..n) {
            val center = (i.toFloat() * m / n).toInt()
            for (j in max(1, center - band)..min(m, center + band)) {
                val d = dist(a[i - 1], b[j - 1])
                C[i][j] = d
                var best = D[i - 1][j - 1]; var bp: Byte = 0
                if (D[i - 1][j] < best) { best = D[i - 1][j]; bp = 1 }
                if (D[i][j - 1] < best) { best = D[i][j - 1]; bp = 2 }
                if (best < inf) { D[i][j] = best + d; P[i][j] = bp }
            }
        }
        if (D[n][m] >= inf) return bad
        val q = FloatArray(4); val cnt = IntArray(4)
        var i = n; var j = m; var steps = 0; var tot = 0f
        while (i > 0 && j > 0) {
            val k = min(3, (((i.toFloat() / n) * 4f) - 1e-4f).toInt())
            q[k] += C[i][j]; cnt[k]++; tot += C[i][j]; steps++
            when (P[i][j].toInt()) { 0 -> { i--; j-- }; 1 -> i--; else -> j-- }
        }
        var worst = 0f
        for (k in 0 until 4) if (cnt[k] > 0) worst = max(worst, q[k] / cnt[k])
        return floatArrayOf(tot / max(1, steps), worst)
    }
}
