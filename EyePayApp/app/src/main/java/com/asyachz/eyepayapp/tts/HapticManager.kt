package com.asyachz.eyepayapp.tts

import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.MainThread

class HapticManager private constructor(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    @MainThread
    fun vibrateSuccess() {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    @MainThread
    fun vibrateDelete() {
        val timings = longArrayOf(0, 40, 60, 40)
        val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)

        if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    @MainThread
    fun vibrateDetection() {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: HapticManager? = null

        fun getInstance(context: Context): HapticManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HapticManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}