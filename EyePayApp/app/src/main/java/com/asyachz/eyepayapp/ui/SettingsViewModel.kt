package com.asyachz.eyepayapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asyachz.eyepayapp.data.CardDao
import com.asyachz.eyepayapp.data.SettingsRepository
import com.asyachz.eyepayapp.tts.HapticManager
import com.asyachz.eyepayapp.tts.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val cardDao: CardDao,
    private val ttsManager: TtsManager,
    private val hapticManager: HapticManager
) : ViewModel() {

    private val _isTtsEnabled = MutableStateFlow(settingsRepository.isTtsEnabled())
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    private val _isHapticEnabled = MutableStateFlow(settingsRepository.isHapticEnabled())
    val isHapticEnabled: StateFlow<Boolean> = _isHapticEnabled.asStateFlow()

    fun toggleTts(enabled: Boolean) {
        settingsRepository.setTtsEnabled(enabled)
        _isTtsEnabled.value = enabled
        if (enabled) {
            ttsManager.speak("Синтез речи включен", ignoreCooldown = true)
        }
    }

    fun toggleHaptic(enabled: Boolean) {
        settingsRepository.setHapticEnabled(enabled)
        _isHapticEnabled.value = enabled
        if (enabled) {
            hapticManager.vibrateSuccess()
        }
    }

    fun deleteAllData(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            cardDao.deleteAllCards()

            launch(Dispatchers.Main) {
                ttsManager.speak("Все персональные данные успешно удалены", ignoreCooldown = true, queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH)
                hapticManager.vibrateDelete()
                onComplete()
            }
        }
    }
}