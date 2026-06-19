package com.asyachz.eyepayapp.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("eyepay_settings", Context.MODE_PRIVATE)

    fun isTtsEnabled(): Boolean = prefs.getBoolean(KEY_TTS, true)
    fun setTtsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_TTS, enabled).apply()

    fun isHapticEnabled(): Boolean = prefs.getBoolean(KEY_HAPTIC, true)
    fun setHapticEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()

    companion object {
        private const val KEY_TTS = "tts_enabled"
        private const val KEY_HAPTIC = "haptic_enabled"
    }
}