package com.asyachz.eyepayapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetectionState(
    val resultText: String = "",
    val ocrText: String = "",
    val isVisible: Boolean = false
)

class DetectionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DetectionState())
    val uiState: StateFlow<DetectionState> = _uiState.asStateFlow()

    private var timeoutJob: Job? = null
    private var lastOcrUpdateTime = 0L

    fun onDetection(result: String?) {
        if (result != null) {
            timeoutJob?.cancel()
            _uiState.value = _uiState.value.copy(
                resultText = result,
                isVisible = true
            )

            timeoutJob = viewModelScope.launch {
                delay(4000)
                _uiState.value = DetectionState(isVisible = false)
            }
        }
    }

    fun onOcrResult(text: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOcrUpdateTime >= 700) {
            lastOcrUpdateTime = currentTime
            _uiState.value = _uiState.value.copy(ocrText = text)
        }
    }
}