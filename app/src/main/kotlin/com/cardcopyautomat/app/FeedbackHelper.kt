package com.cardcopyautomat.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Plays a short beep and vibrates the phone twice in quick, short bursts.
 * This is the "safe to remove the card now" signal, fired once the app has
 * finished copying/uploading/deleting and has released its access to the
 * card reader volume.
 */
object FeedbackHelper {

    fun playEjectSignal(context: Context) {
        playBeep()
        vibrateTwice(context)
    }

    private fun playBeep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            // ToneGenerator plays asynchronously; release shortly after.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tg.release()
            }, 300)
        } catch (_: RuntimeException) {
            // No audio output available (e.g. silent hardware) — ignore, vibration still fires.
        }
    }

    private fun vibrateTwice(context: Context) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        // Pattern: wait 0, vibrate 100ms, pause 120ms, vibrate 100ms.
        // (Two short bursts, as requested.)
        val timings = longArrayOf(0, 100, 120, 100)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }
}
