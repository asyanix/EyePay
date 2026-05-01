package com.asyachz.eyepayapp.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false

    private val speechCooldowns = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 6000L
    private val _isReadyState = MutableStateFlow(false)
    val isReadyState: StateFlow<Boolean> = _isReadyState.asStateFlow()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            tts?.setAudioAttributes(attrs)
            tts?.language = Locale("ru", "RU")
            isReady = true
            _isReadyState.value = true
        }
    }

    fun speak(text: String, ignoreCooldown: Boolean = false, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (!isReady || text.isBlank() || text == "Неизвестный банк") return

        val now = System.currentTimeMillis()
        val lastSpoken = speechCooldowns[text] ?: 0L

        if (ignoreCooldown || (now - lastSpoken > COOLDOWN_MS)) {
            tts?.speak(text, queueMode, null, text)
            speechCooldowns[text] = now
        }
    }

    fun resetCooldown(text: String) {
        speechCooldowns.remove(text)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}