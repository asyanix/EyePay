package com.asyachz.eyepayapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.asyachz.eyepayapp.data.CardDao
import com.asyachz.eyepayapp.data.FavoriteCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

data class DetectionState(
    val resultText: String = "",
    val ocrText: String = "",
    val isVisible: Boolean = false
)

data class SaveCardFormState(
    val isVisible: Boolean = false,
    val bankName: String = "",
    val cardNumber: String = "",
    val note: String = "",
    val errorMessage: String? = null
)

class DetectionViewModel(private val cardDao: CardDao) : ViewModel() {
    private val _uiState = MutableStateFlow(DetectionState())
    val uiState: StateFlow<DetectionState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(SaveCardFormState())
    val formState: StateFlow<SaveCardFormState> = _formState.asStateFlow()

    private val _saveEvent = MutableSharedFlow<String>()
    val saveEvent: SharedFlow<String> = _saveEvent.asSharedFlow()

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

    fun showBottomSheet() {
        val currentBank = _uiState.value.ocrText
        if (currentBank.isNotEmpty() && currentBank != "Неизвестный банк") {
            _formState.update { it.copy(
                isVisible = true,
                bankName = currentBank,
                errorMessage = null
            ) }
        }
    }

    fun hideBottomSheet() {
        _formState.update { it.copy(isVisible = false, cardNumber = "", note = "", errorMessage = null) }
    }

    fun updateCardNumber(number: String) {
        _formState.update { it.copy(cardNumber = number, errorMessage = null) }
    }

    fun updateNote(note: String) {
        _formState.update { it.copy(note = note, errorMessage = null) }
    }

    fun saveCard() {
        val form = _formState.value
        android.util.Log.d("EyePay_DB", "Adding to the database started. Data: bank=${form.bankName}, card=${form.cardNumber}")

        if (form.bankName.isBlank() || form.cardNumber.isBlank() || form.note.isBlank()) {
            _formState.update { it.copy(errorMessage = "Заполните все поля") }
            return
        }

        val cardRegex = Regex("^[0-9]{16}$")
        if (!cardRegex.matches(form.cardNumber)) {
            _formState.update { it.copy(errorMessage = "Номер должен содержать строго 16 цифр") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val card = FavoriteCard(
                    bankName = form.bankName,
                    cardNumber = form.cardNumber,
                    expiryDate = "",
                    note = form.note
                )
                cardDao.insertCard(card)
                android.util.Log.d("EyePay_DB", "The recording was saved successfully. ID: ${card.id}")

                launch(Dispatchers.Main) {
                    hideBottomSheet()
                    _saveEvent.emit("Карта сохранена")
                }
            } catch (e: Exception) {
                android.util.Log.e("EyePay_DB", "Error when saving to the database: ${e.message}")
            }
        }
    }
}