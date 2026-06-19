package com.asyachz.eyepayapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.asyachz.eyepayapp.EyePayApplication
import com.asyachz.eyepayapp.data.SettingsRepository
import com.asyachz.eyepayapp.tts.HapticManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as EyePayApplication
    val hapticManager = remember { HapticManager.getInstance(context) }

    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = SettingsRepository(context),
                    cardDao = app.cardRepository,
                    ttsManager = app.ttsManager,
                    hapticManager = hapticManager
                )
            }
        }
    )

    val isTtsEnabled by viewModel.isTtsEnabled.collectAsState()
    val isHapticEnabled by viewModel.isHapticEnabled.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Синтез речи", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text("Голосовое сопровождение действий", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isTtsEnabled,
                    onCheckedChange = { viewModel.toggleTts(it) },
                    modifier = Modifier.semantics {
                        contentDescription = "Синтез речи"
                        stateDescription = if (isTtsEnabled) "Включено" else "Выключено"
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Тактильный отклик", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text("Вибрация при сканировании и ошибках", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isHapticEnabled,
                    onCheckedChange = { viewModel.toggleHaptic(it) },
                    modifier = Modifier.semantics {
                        contentDescription = "Тактильный отклик"
                        stateDescription = if (isHapticEnabled) "Включено" else "Выключено"
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .semantics {
                        contentDescription = "Безвозвратное удаление всех сохраненных карт. Это действие необратимо."
                    },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Удалить сохраненные карты", fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Очистить всё?") },
            text = { Text("Вы уверены, что хотите безвозвратно удалить все сохраненные карты? Это действие нельзя отменить.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllData(
                            onComplete = {
                                Toast.makeText(context, "Данные удалены", Toast.LENGTH_SHORT).show()
                            }
                        )
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Да, удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}