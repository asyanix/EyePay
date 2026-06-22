package com.asyachz.eyepayapp.ui

import android.speech.tts.TextToSpeech
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
import com.asyachz.eyepayapp.tts.HapticManager
import com.asyachz.eyepayapp.tts.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

data class DetectionState(
    val resultText: String = "",
    val ocrText: String = "",
    val isVisible: Boolean = false,
    val foundCard: FavoriteCard? = null,
    val totalSum: Long = 0L,
    val currentDetectedBillValue: Long = 0L,
    val isSummingUiVisible: Boolean = false
)

data class SaveCardFormState(
    val isVisible: Boolean = false,
    val bankName: String = "",
    val cardNumber: String = "",
    val note: String = "",
    val errorMessage: String? = null,
    val isBankError: Boolean = false,
    val isCardError: Boolean = false
)

class DetectionViewModel(
    private val ttsManager: TtsManager,
    private val cardDao: CardDao,
    private val hapticManager: HapticManager
    ) : ViewModel() {
    private val _uiState = MutableStateFlow(DetectionState())
    val uiState: StateFlow<DetectionState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(SaveCardFormState())
    val formState: StateFlow<SaveCardFormState> = _formState.asStateFlow()

    private val _saveEvent = MutableSharedFlow<String>()
    val saveEvent: SharedFlow<String> = _saveEvent.asSharedFlow()

    private var timeoutJob: Job? = null
    private var lastOcrUpdateTime = 0L
    private var lastDbCheckTime = 0L
    private val dbCheckInterval = 1500L
    private var lastAnnouncedBank: String? = null

    fun onDetection(result: String?) {
        if (result != null) {
            timeoutJob?.cancel()

            val billValue = if (result != "Карта") parseBillValue(result) else 0L

            _uiState.update { it.copy(
                resultText = result,
                isVisible = true,
                currentDetectedBillValue = billValue,
                isSummingUiVisible = billValue > 0L
            ) }

            if (result != "Карта") {
                ttsManager.speak(result)
            }

            timeoutJob = viewModelScope.launch {
                delay(4000)
                _uiState.update { it.copy(
                    isVisible = false,
                    foundCard = null,
                    isSummingUiVisible = false,
                    currentDetectedBillValue = 0L
                ) }
                lastAnnouncedBank = null
            }
        }
        else {
            lastAnnouncedBank = null
        }
    }

    private fun parseBillValue(className: String): Long {
        return className.filter { it.isDigit() }.toLongOrNull() ?: 0L
    }

    fun onSingleTapReceived() {
        val currentState = _uiState.value
        if (currentState.currentDetectedBillValue > 0) {
            val newTotal = currentState.totalSum + currentState.currentDetectedBillValue

            _uiState.update { it.copy(
                totalSum = newTotal,
                currentDetectedBillValue = 0L,
                isSummingUiVisible = false
            ) }

            hapticManager.vibrateSuccess()
            ttsManager.speak(
                "Добавлено, итого: $newTotal рублей",
                ignoreCooldown = true,
                queueMode = TextToSpeech.QUEUE_FLUSH
            )
        }
    }

    fun onDoubleTapReceived() {
        val currentState = _uiState.value

        if (currentState.totalSum > 0 || currentState.isSummingUiVisible) {
            val finalSum = currentState.totalSum

            _uiState.update { it.copy(
                totalSum = 0L,
                currentDetectedBillValue = 0L,
                isSummingUiVisible = false
            ) }

            hapticManager.vibrateDetection()
            ttsManager.speak(
                "Итоговая сумма: $finalSum рублей. Сумма сброшена.",
                ignoreCooldown = true,
                queueMode = TextToSpeech.QUEUE_FLUSH
            )
        } else {
            showBottomSheet()
        }
    }

    fun onOcrResult(text: String) {
        val currentTime = System.currentTimeMillis()

        if (text.isEmpty()) {
            lastAnnouncedBank = null
            _uiState.update { it.copy(ocrText = "") }
            return
        }

        if (currentTime - lastOcrUpdateTime >= 700) {
            lastOcrUpdateTime = currentTime
            _uiState.update { it.copy(ocrText = text) }
        }

        if (text.isNotEmpty() && text != "Неизвестный банк") {
            if (currentTime - lastDbCheckTime >= dbCheckInterval) {
                lastDbCheckTime = currentTime
                checkBankInDatabase(text)
            }
        }
    }

    private fun checkBankInDatabase(bankName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val card = cardDao.getCardByBank(bankName)
            _uiState.update { it.copy(foundCard = card) }

            if (lastAnnouncedBank != bankName) {
                lastAnnouncedBank = bankName

                val speechText = if (card != null && card.note.isNotBlank()) {
                    "$bankName, ${card.note}"
                } else {
                    bankName
                }

                ttsManager.speak(speechText, queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD)

                if (card == null) {
                    ttsManager.speak("Дважды тапните по экрану для добавления в избранное", queueMode = TextToSpeech.QUEUE_ADD)
                }
            }
        }
    }

    fun formatCardNumber(fullNumber: String): String {
        return if (fullNumber.length >= 4) {
            "**** ${fullNumber.takeLast(4)}"
        } else "****"
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

    fun updateBankName(name: String) {
        _formState.update { it.copy(bankName = name, errorMessage = null) }
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

        val isBankEmpty = form.bankName.isBlank()
        val isCardInvalid = form.cardNumber.length != 16

        if (isBankEmpty) {
            _formState.update { it.copy(isBankError = true) }
            ttsManager.speak("Заполните название банка", ignoreCooldown = true, queueMode = TextToSpeech.QUEUE_FLUSH)
            return
        } else {
            _formState.update { it.copy(isBankError = false) }
        }

        if (isCardInvalid) {
            _formState.update { it.copy(isCardError = true, errorMessage = "Неверный номер карты") }
            ttsManager.speak("Неверный номер карты", ignoreCooldown = true, queueMode = TextToSpeech.QUEUE_FLUSH)
            return
        } else {
            _formState.update { it.copy(isCardError = false, errorMessage = null) }
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
                    ttsManager.speak("Карта успешно добавлена", ignoreCooldown = true, queueMode = TextToSpeech.QUEUE_FLUSH)
                    hapticManager.vibrateSuccess()
                }
            } catch (e: Exception) {
                android.util.Log.e("EyePay_DB", "Error when saving to the database: ${e.message}")
            }
        }
    }

    fun updateCardNumberFromNfc(number: String) {
        _formState.update { it.copy(cardNumber = number, errorMessage = null) }
        ttsManager.speak("Карта отсканирована", ignoreCooldown = true, queueMode = TextToSpeech.QUEUE_FLUSH)
        hapticManager.vibrateSuccess()
    }

    fun setNfcError(message: String) {
        _formState.update { it.copy(errorMessage = message) }

        ttsManager.speak("Ошибка чтения NFC", ignoreCooldown = true, queueMode = TextToSpeech.QUEUE_FLUSH)
        hapticManager.vibrateDelete()
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}