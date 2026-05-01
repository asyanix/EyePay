package com.asyachz.eyepayapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asyachz.eyepayapp.data.CardDao
import com.asyachz.eyepayapp.data.FavoriteCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedCardsViewModel(private val cardDao: CardDao) : ViewModel() {
    val cards: StateFlow<List<FavoriteCard>> = cardDao.getAllCards()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _visibleCardIds = MutableStateFlow<Set<Int>>(emptySet())
    val visibleCardIds: StateFlow<Set<Int>> = _visibleCardIds

    fun toggleCardNumberVisibility(cardId: Int) {
        val currentSet = _visibleCardIds.value
        if (currentSet.contains(cardId)) {
            _visibleCardIds.value = currentSet - cardId
        } else {
            _visibleCardIds.value = currentSet + cardId
        }
    }

    fun deleteCard(card: FavoriteCard) {
        viewModelScope.launch(Dispatchers.IO) {
            cardDao.deleteCard(card)
        }
    }

    fun updateCard(card: FavoriteCard) {
        viewModelScope.launch(Dispatchers.IO) {
            cardDao.updateCard(card)
        }
    }

    fun addCard(bankName: String, cardNumber: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newCard = FavoriteCard(
                bankName = bankName,
                cardNumber = cardNumber,
                expiryDate = "",
                note = note
            )
            cardDao.insertCard(newCard)
        }
    }

    fun formatCardNumber(number: String, isVisible: Boolean): String {
        return if (isVisible || number.length < 4) {
            number
        } else {
            "**** ${number.takeLast(4)}"
        }
    }
}