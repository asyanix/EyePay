package com.asyachz.eyepayapp.ui

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.asyachz.eyepayapp.EyePayApplication
import com.asyachz.eyepayapp.ml.EyePayAnalyzer
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = EyePayBlue,
            secondary = EyePayBlue,
            onPrimary = Color.White
        )
    ) {
        var currentScreen by remember { mutableStateOf("start") }

        when (currentScreen) {
            "camera" -> CameraScreen(onBackClick = { currentScreen = "start" })
            "saved_cards" -> SavedCardsScreen(onBackClick = { currentScreen = "start" })
            else -> StartScreen(
                onStartClick = { currentScreen = "camera" },
                onSavedCardsClick = { currentScreen = "saved_cards" }
            )
        }}
}

@Composable
fun StartScreen(onStartClick: () -> Unit, onSavedCardsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(4f)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "EyePay",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = EyePayBlue
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(7f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EyePayBlue)
        ) {
            Text("Открыть камеру", fontSize = 24.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSavedCardsClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(7f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EyePayBlue)
        ) {
            Text("Сохраненные карты", fontSize = 24.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { /* Логика не меняется, пока пусто */ },
            modifier = Modifier
                .fillMaxWidth()
                .weight(7f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EyePayBlue)
        ) {
            Text("Настройки", fontSize = 24.sp, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as EyePayApplication

    val viewModel: DetectionViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DetectionViewModel(app.ttsManager, app.cardRepository)
            }
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val isTtsReady by app.ttsManager.isReadyState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.saveEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(isTtsReady) {
        if (isTtsReady) {
            app.ttsManager.speak(
                text = "Поднесите банкноту или карту к камере телефона",
                ignoreCooldown = true,
                queueMode = TextToSpeech.QUEUE_FLUSH
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                viewModel.showBottomSheet()
            }
        )
    }) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(
                                cameraExecutor,
                                EyePayAnalyzer(
                                    context = ctx,
                                    app.ttsManager,
                                    onDetectionResult = { result -> viewModel.onDetection(result) },
                                    onOcrResult = { text -> viewModel.onOcrResult(text) }
                                )
                            )
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Вернуться в меню",
                tint = Color.White
            )
        }

        if (uiState.isVisible && uiState.ocrText.isNotEmpty() && uiState.foundCard == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Добавить в избранное?\nДважды коснитесь экрана",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (uiState.isVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .fillMaxWidth(0.85f)
                    .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = uiState.resultText,
                    fontSize = 28.sp,
                    color = Color.Green,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.foundCard != null) {
                    val card = uiState.foundCard!!
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = card.bankName,
                            fontSize = 20.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = viewModel.formatCardNumber(card.cardNumber),
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        if (card.note.isNotEmpty()) {
                            Text(
                                text = card.note,
                                fontSize = 16.sp,
                                color = Color.LightGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    if (uiState.ocrText.isNotEmpty()) {
                        Text(
                            text = uiState.ocrText,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (formState.isVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.hideBottomSheet() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .imePadding(),
            confirmButton = {
                TextButton(onClick = { viewModel.saveCard() }) {
                    Text("Отправить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideBottomSheet() }) {
                    Text("Закрыть")
                }
            },
            title = {
                Text(
                    text = "Добавить в избранное",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Приложите карту к задней крышке телефона",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = formState.bankName,
                        onValueChange = {},
                        label = { Text("Банк") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = formState.cardNumber,
                        onValueChange = { viewModel.updateCardNumber(it) },
                        label = { Text("Номер карты") },
                        isError = formState.errorMessage != null,
                        supportingText = {
                            if (formState.errorMessage != null) {
                                Text(text = formState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = formState.note,
                        onValueChange = { viewModel.updateNote(it) },
                        label = { Text("Заметка") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}