package com.adel.subha.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.adel.subha.R
import kotlin.random.Random

/** صوت فرقعة الخرز والاهتزاز. */
class Feedback(context: Context, private val volume: () -> Float = { 1f }) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val clacks = listOf(R.raw.clack_1, R.raw.clack_2, R.raw.clack_3).map { pool.load(context, it, 1) }
    private val done = pool.load(context, R.raw.done, 1)
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    fun clack() {
        val v = (0.75f + Random.nextFloat() * 0.25f) * volume()
        pool.play(clacks.random(), v, v, 1, 0, 0.94f + Random.nextFloat() * 0.12f)
    }

    fun done() { pool.play(done, volume(), volume(), 2, 0, 1f) }

    fun tick() {
        val vb = vibrator ?: return
        if (!vb.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 29) vb.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        else vb.vibrate(VibrationEffect.createOneShot(12, 120))
    }

    fun reached() {
        val vb = vibrator ?: return
        if (!vb.hasVibrator()) return
        vb.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 90, 70, 90, 160), -1))
    }

    fun release() = pool.release()
}
