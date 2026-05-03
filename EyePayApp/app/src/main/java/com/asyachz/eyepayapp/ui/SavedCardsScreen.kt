package com.asyachz.eyepayapp.ui

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.asyachz.eyepayapp.EyePayApplication
import com.asyachz.eyepayapp.data.FavoriteCard

val EyePayBlue = Color(0xFF2241A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedCardsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as EyePayApplication
    val viewModel: SavedCardsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SavedCardsViewModel(app.cardRepository) }
        }
    )

    val cards by viewModel.cards.collectAsState()
    val visibleCardIds by viewModel.visibleCardIds.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<FavoriteCard?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сохраненные карты") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить карту вручную")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cards) { card ->
                val isVisible = visibleCardIds.contains(card.id)
                CardItem(
                    card = card,
                    isVisible = isVisible,
                    onToggleVisibility = { viewModel.toggleCardNumberVisibility(card.id) },
                    onEditClick = { editingCard = card },
                    onDeleteClick = { viewModel.deleteCard(card) },
                    formatNumber = { viewModel.formatCardNumber(card.cardNumber, isVisible) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddEditCardDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { bank, number, note ->
                viewModel.addCard(bank, number, note)
                showAddDialog = false
            }
        )
    }

    editingCard?.let { card ->
        AddEditCardDialog(
            card = card,
            onDismiss = { editingCard = null },
            onConfirm = { bank, number, note ->
                viewModel.updateCard(card.copy(bankName = bank, cardNumber = number, note = note))
                editingCard = null
            }
        )
    }
}

@Composable
fun CardItem(
    card: FavoriteCard,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    formatNumber: () -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.bankName, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                TextButton(
                    onClick = onToggleVisibility,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.semantics {
                        (if (isVisible) "Номер показан" else "Номер скрыт").also { contentDescription = it }
                    }
                ) {
                    Text(formatNumber(), color = EyePayBlue, fontSize = 16.sp)
                }

                if (card.note.isNotEmpty()) {
                    Text(card.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать ${card.bankName}")
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить ${card.bankName}", tint = Color.Red)
            }
        }
    }
}

@Composable
fun AddEditCardDialog(
    card: FavoriteCard? = null,
    onDismiss: () -> Unit,
    onConfirm: (bank: String, number: String, note: String) -> Unit
) {
    var bankName by remember { mutableStateOf(card?.bankName ?: "") }
    var cardNumber by remember { mutableStateOf(card?.cardNumber ?: "") }
    var note by remember { mutableStateOf(card?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.9f).imePadding(),
    title = { Text(if (card == null) "Добавить карту" else "Редактировать") },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it; error = null },
                label = { Text("Название банка") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = cardNumber,
                onValueChange = { if (it.length <= 16) { cardNumber = it; error = null } },
                label = { Text("Номер карты (16 цифр)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = error != null,
            supportingText = { if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) },
            singleLine = true
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Заметка") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }
    },
    confirmButton = {
        Button(onClick = {
            if (bankName.isBlank() || cardNumber.length != 16) {
                error = "Введите корректные данные (16 цифр номера)"
            } else {
                onConfirm(bankName, cardNumber, note)
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = EyePayBlue)) {
            Text("Сохранить")
        }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) { Text("Отмена") }
    }
    )
}